package modular.game;

/*
 * TimingBridge.java
 *
 * Timing authority for the Goddess Matrix game engine.
 *
 * Three modes governed by ASM timing script when present.
 * Falls back to Java defaults when ASM is absent.
 *
 * MODE_GAMEPLAY  81ms tick   — biological/analog cadence
 *                              matches the Matrix core LOGIC_TICK_MS
 *                              intentional: game idle and AI idle
 *                              run at the same rhythm
 *
 * MODE_OVERLAY   60 FPS      — ESC active, refresh display visible
 *                              standard display rate, UI-friendly
 *
 * MODE_PANELS    75 FPS      — E key active, side panels open
 *                              ASM combines MODE_OVERLAY + MODE_GAMEPLAY
 *                              Java fallback: 75 FPS
 *
 * ASM INTEGRATION HOOK:
 *   The ASM timing script lives at: modular/game/timing_asm (compiled binary)
 *   or modular/game/timing.asm (source, requires assembler).
 *
 *   Expected ASM interface (to be confirmed with Derek's script):
 *   - ASM binary receives mode signal via stdin line: "MODE 0|1|2"
 *   - ASM responds with tick interval in nanoseconds via stdout
 *   - Java reads that value and uses it as the loop interval
 *   - OR: ASM writes a tick counter to a shared memory-mapped file
 *     that Java polls — implementation depends on Derek's script design
 *
 *   When ASM is confirmed, replace the [ASM_HOOK] stub sections below.
 *
 * Contributors:
 *   Derek Jason Gilhousen — timing philosophy, three-mode design,
 *                           ASM script (external, to be integrated)
 *   Claude (Anthropic)    — TimingBridge implementation, fallback logic,
 *                           hook stubs, mode management
 */

import java.io.File;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class TimingBridge 
{
    // ── MODE CONSTANTS ────────────────────────────────────────────────────────
    public static final int MODE_GAMEPLAY = 0;   // 81ms — default, no UI overlay
    public static final int MODE_OVERLAY  = 1;   // 60 FPS — ESC active
    public static final int MODE_PANELS   = 2;   // 75 FPS fallback — E panels active

    // ── FALLBACK TICK INTERVALS (nanoseconds) ─────────────────────────────────
    // Used when ASM timing script is not present or not detected.
    // These match the three ASM modes by design.
    private static final long TICK_GAMEPLAY = 81_000_000L;           // 81ms
    private static final long TICK_OVERLAY  = 1_000_000_000L / 60;   // ~16.67ms
    private static final long TICK_PANELS   = 1_000_000_000L / 75;   // ~13.33ms

    // ── ASM STATE ─────────────────────────────────────────────────────────────
    private boolean asmPresent    = false;
    private Process asmProcess    = null;
    private PrintWriter asmStdin  = null;
    private BufferedReader asmOut = null;
    private long asmTickNanos     = TICK_GAMEPLAY; // last value received from ASM

    // ── CURRENT STATE ─────────────────────────────────────────────────────────
    private int  currentMode      = MODE_GAMEPLAY;
    private long lastModeChange   = System.nanoTime();

    // ─────────────────────────────────────────────────────────────────────────

    public TimingBridge(File gameDir) 
    {
        detectAndLoadASM(gameDir);
        // Start hardware governor — adjusts render quality to hit target FPS.
        // SphereRenderer reads OptimizeRender.Governor.getQualityMultiplier().
        OptimizeRender.Governor.start(30.0); // 30 FPS default target
    }

    // ── ASM DETECTION ─────────────────────────────────────────────────────────

    private void detectAndLoadASM(File gameDir) 
    {
        if (gameDir == null || !gameDir.exists()) return;

        // [ASM_HOOK: DETECTION]
        // Look for compiled ASM timing binary in the game directory.
        // Priority: compiled binary first, then source (if assembler present).
        File asmBin = new File(gameDir, "timing_asm");
        File asmSrc = new File(gameDir, "timing.asm");

        File asmTarget = null;
        if (asmBin.exists() && asmBin.canExecute()) 
        {
            asmTarget = asmBin;
        }
        // Source-only detection — assembler integration TBD
        // else if (asmSrc.exists()) { assemble + load }

        if (asmTarget != null) 
        {
            try 
            {
                // [ASM_HOOK: PROCESS LAUNCH]
                // Launch ASM timing binary as a subprocess.
                // Contract: binary reads "MODE 0|1|2\n" on stdin,
                // responds with tick interval in nanoseconds on stdout.
                // This interface is a placeholder — confirm with Derek's script.
                ProcessBuilder pb = new ProcessBuilder(asmTarget.getAbsolutePath());
                pb.redirectErrorStream(false);
                asmProcess = pb.start();
                asmStdin   = new PrintWriter(asmProcess.getOutputStream(), true);
                asmOut     = new BufferedReader(
                                 new InputStreamReader(asmProcess.getInputStream()));
                asmPresent = true;

                // Signal initial mode
                sendModeToASM(currentMode);

            } 
            catch (Exception e) 
            {
                // ASM failed to launch — fall back to Java defaults silently
                asmPresent = false;
                asmProcess = null;
            }
        }
    }

    // ── MODE SWITCHING ────────────────────────────────────────────────────────

    public void setMode(int mode) 
    {
        if (mode == currentMode) return;
        currentMode   = mode;
        lastModeChange = System.nanoTime();

        if (asmPresent) 
        {
            sendModeToASM(mode);
            readTickFromASM();
        }
    }

    private void sendModeToASM(int mode) 
    {
        // [ASM_HOOK: MODE SIGNAL]
        // Sends the new mode to the ASM process via stdin.
        // Format: "MODE 0\n", "MODE 1\n", or "MODE 2\n"
        if (asmStdin != null) 
        {
            try { asmStdin.println("MODE " + mode); }
            catch (Exception ignored) {}
        }
    }

    private void readTickFromASM() 
    {
        // [ASM_HOOK: TICK READ]
        // Reads the tick interval in nanoseconds from the ASM process stdout.
        // If read fails, falls back to Java default for the current mode.
        if (asmOut != null) 
        {
            try 
            {
                String line = asmOut.readLine();
                if (line != null) 
                {
                    asmTickNanos = Long.parseLong(line.trim());
                }
            } 
            catch (Exception e) 
            {
                asmTickNanos = getFallbackTick(currentMode);
            }
        }
    }

    // ── TICK QUERY ────────────────────────────────────────────────────────────

    /**
     * Returns the current tick interval in nanoseconds.
     * Game loop calls this each iteration to determine sleep duration.
     * When ASM is present, returns the ASM-provided value.
     * When ASM is absent, returns the Java fallback for the current mode.
     */
    public long getTickNanos() 
    {
        if (asmPresent) return asmTickNanos;
        return getFallbackTick(currentMode);
    }

    private long getFallbackTick(int mode) 
    {
        switch (mode) 
        {
            case MODE_OVERLAY:  return TICK_OVERLAY;
            case MODE_PANELS:   return TICK_PANELS;
            default:            return TICK_GAMEPLAY;
        }
    }

    // ── STATE ACCESSORS ───────────────────────────────────────────────────────

    public int     getCurrentMode()  { return currentMode; }
    public boolean isASMPresent()    { return asmPresent; }
    public long    getLastModeChange() { return lastModeChange; }

    /** Human-readable FPS equivalent of current tick for display. */
    public String getDisplayRate() 
    {
        long tick = getTickNanos();
        if (tick <= 0) return "??";
        long fps = 1_000_000_000L / tick;

        // MODE_GAMEPLAY shows as "81ms" not FPS — matches biological framing
        if (currentMode == MODE_GAMEPLAY) return "81ms";
        return fps + "Hz";
    }

    // ── CLEANUP ───────────────────────────────────────────────────────────────

    public void shutdown() 
    {
        if (asmProcess != null && asmProcess.isAlive()) 
        {
            try 
            {
                if (asmStdin != null) asmStdin.println("EXIT");
                Thread.sleep(200);
                if (asmProcess.isAlive()) asmProcess.destroyForcibly();
            } 
            catch (Exception ignored) {}
        }
        asmProcess = null;
        asmStdin   = null;
        asmOut     = null;
        asmPresent = false;
    }
}
