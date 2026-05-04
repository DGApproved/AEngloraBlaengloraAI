package modular.game;

/*
 * IceSandbox.java
 *
 * Main game panel for the Goddess Matrix game engine.
 *
 * The player stands on a small sphere floating in a void.
 * The sphere size is set by the total file size in modular/game/.
 * Empty folder = 3ft diameter (player is ~1.8x taller than the planet).
 *
 * LAYER OWNERSHIP:
 *   Void background (far)   → Java geometry + C/ASM image hook
 *   Space between           → Java (particles, lighting, depth)
 *   Sphere geometry         → Java
 *   Sphere texture          → Python (GGUF or rule-based), Java fallback
 *   Player figure           → Java (stick figure V1, sprite future)
 *   In-game text content    → Python (via [GAME_NARRATE:] tags)
 *   In-game text rendering  → Java
 *   UI panels (E key)       → Java frame, Python content
 *   Timing                  → ASM script (fallback: Java nanoTime loop)
 *
 * LAUNCH CONTEXT DETECTION:
 *   Launched via Game.java (Matrix) → matrixContext = true
 *     MatrixState reference available
 *     Protocol tags wired through ApiBridge
 *     Exit handled by [Game] button in Matrix UI
 *     ESC click does not exit
 *   Launched via main() (standalone terminal) → matrixContext = false
 *     No MatrixState
 *     Standalone exit: ESC overlay click OR E panel exit button
 *     Full game operational without any external systems
 *
 * KEY BINDINGS:
 *   WASD  — walk on sphere surface
 *   SPACE — jump (radial outward, gravity returns player)
 *   ESC   — toggle refresh/timing overlay (MODE_OVERLAY)
 *   E     — toggle side panels (MODE_PANELS)
 *
 * Contributors:
 *   Gemini (Google)        — original IceSandbox physics and renderer concept
 *   Derek Jason Gilhousen — planet-as-world design, layer ownership philosophy,
 *                           timing mode system, panel design, context detection
 *   Claude (Anthropic)    — IceSandbox rewrite integrating all subsystems,
 *                           spherical physics, key handling, render pipeline,
 *                           context detection, timing bridge wiring
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class IceSandbox extends JPanel implements Runnable, KeyListener, MouseListener, MouseMotionListener 
{
    // ── SUBSYSTEMS ────────────────────────────────────────────────────────────
    private final WorldState     world;
    private final TimingBridge   timing;
    private final PlayerPhysics  physics;
    private final PlanetScaler   scaler;
    private final SphereRenderer sphereRenderer;
    private final VoidRenderer   voidRenderer;
    private final RefreshOverlay refreshOverlay;
    private final SidePanels     sidePanels;
    private final GameProtocol   protocol;

    // ── ENGINE ────────────────────────────────────────────────────────────────
    private Thread renderThread;
    private float  renderPhase = 0.0f;

    // ── CONTEXT ───────────────────────────────────────────────────────────────
    private final boolean isStandalone;    // true = no GoddessMatrix present
    private final File    gameDir;         // modular/game/
    private final File    dgapiSystemDir;  // fn/fn{N}/dgapi/system/ or null

    // Jump counter for event writing
    private int jumpCount = 0;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Matrix-context constructor. Called by Game.java.
     * MatrixState reference used for dgapi path resolution.
     *
     * @param gameDir        absolute path to modular/game/
     * @param dgapiSystemDir absolute path to active session's dgapi/system/
     * @param isMatrix       always true when called from Game.java
     */
    public IceSandbox(File gameDir, File dgapiSystemDir, boolean isMatrix) 
    {
        this.gameDir        = gameDir;
        this.dgapiSystemDir = dgapiSystemDir;
        this.isStandalone   = !isMatrix;

        // ── INIT SUBSYSTEMS ───────────────────────────────────────────────────
        world          = new WorldState();
        world.launchedFromMatrix = isMatrix;

        timing         = new TimingBridge(gameDir);
        physics        = new PlayerPhysics();
        scaler         = new PlanetScaler(gameDir);
        sphereRenderer = new SphereRenderer();
        voidRenderer   = new VoidRenderer(new File(gameDir, "images"));
        refreshOverlay = new RefreshOverlay();
        sidePanels     = new SidePanels();

        File dgapiDir = dgapiSystemDir != null ? dgapiSystemDir.getParentFile() : null;
        protocol = new GameProtocol(world, sidePanels, gameDir, dgapiDir, isMatrix);

        // Write session enter event
        // [BASH AI HOOK: session start logged to game_events.txt]
        protocol.writeSessionEnterEvent(1); // TODO: pass actual FN node from MatrixState

        // Set initial planet scale
        world.planetDiameterFeet = scaler.getDiameter();

        // Standalone exit callbacks
        if (isStandalone) 
        {
            Runnable exitAction = () -> {
                stopEngine();
                Window w = SwingUtilities.getWindowAncestor(this);
                if (w != null) w.dispose();
            };
            refreshOverlay.setStandaloneExitCallback(exitAction);
            sidePanels.setStandaloneExitCallback(exitAction);
        }

        // Set void images dir for hot-reload
        world.voidImagesDir = new File(gameDir, "images");

        // ── SWING SETUP ───────────────────────────────────────────────────────
        setBackground(new Color(2, 4, 10));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    /**
     * Standalone constructor. Called by main() for terminal launches.
     * No MatrixState available. Full game operational without AI systems.
     */
    public IceSandbox() 
    {
        this(resolveStandaloneGameDir(), null, false);
    }

    private static File resolveStandaloneGameDir() 
    {
        // When run standalone, game dir is relative to the class location
        File f = new File("modular/game");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    // ── ENGINE CONTROL ────────────────────────────────────────────────────────

    public void startEngine() 
    {
        if (renderThread == null) 
        {
            renderThread = new Thread(this, "IceSandbox-RenderLoop");
            renderThread.setDaemon(true);
            renderThread.start();
        }
    }

    public void stopEngine() 
    {
        protocol.writeSessionExitEvent(1); // TODO: pass actual FN node
        timing.shutdown();
        renderThread = null;
    }

    // ── GAME LOOP ─────────────────────────────────────────────────────────────

    @Override
    public void run() 
    {
        long lastTime = System.nanoTime();
        long logicAccum = 0;
        final long LOGIC_TICK_NS = 81_000_000L; // 81ms — matches Matrix LOGIC_TICK_MS

        while (renderThread != null) 
        {
            long now   = System.nanoTime();
            long delta = now - lastTime;
            lastTime   = now;

            // ── TIMING TICK ───────────────────────────────────────────────────
            // [ASM HOOK: TimingBridge.getTickNanos() returns ASM value when present]
            long tickNs = timing.getTickNanos();

            // Logic tick — physics and game state, decoupled from render rate
            logicAccum += delta;
            if (logicAccum >= LOGIC_TICK_NS) 
            {
                updateLogic();
                logicAccum -= LOGIC_TICK_NS;
            }

            // Render phase advance — drives waveform and animation
            renderPhase += 0.04f;
            if (renderPhase > (float)(Math.PI * 2)) renderPhase -= (float)(Math.PI * 2);

            repaint();

            // Sleep to match current timing mode
            long elapsed = System.nanoTime() - now;
            long sleepNs = tickNs - elapsed;
            if (sleepNs > 0) 
            {
                try { Thread.sleep(sleepNs / 1_000_000, (int)(sleepNs % 1_000_000)); }
                catch (InterruptedException ignored) {}
            }
        }
    }

    // ── LOGIC UPDATE ──────────────────────────────────────────────────────────

    private void updateLogic() 
    {
        // Update planet scale periodically
        world.planetDiameterFeet = scaler.getDiameter();

        // Advance physics
        physics.tick(world.planetDiameterFeet / 2.0f);
        physics.writeToWorld(world);

        // Panel slide animation
        if (world.panelSlideProgress != world.panelSlideTarget) 
        {
            float diff = world.panelSlideTarget - world.panelSlideProgress;
            if (Math.abs(diff) < WorldState.PANEL_SLIDE_SPEED) 
            {
                world.panelSlideProgress = world.panelSlideTarget;
            } 
            else 
            {
                world.panelSlideProgress += Math.signum(diff) * WorldState.PANEL_SLIDE_SPEED;
            }
        }

        // Poll hardware telemetry from Python's live file
        // [PYTHON HOOK: hardware_live.txt — atmosphere driven by CPU/GPU load]
        protocol.pollHardwareTelemetry(dgapiSystemDir);

        // Narrate text expiry
        if (!world.narrateText.isEmpty()
                && System.currentTimeMillis() > world.narrateExpireMs) 
        {
            world.narrateText = "";
        }
    }

    // ── PAINT ─────────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) 
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        int W = getWidth();
        int H = getHeight();

        // ── 1. VOID BACKGROUND ────────────────────────────────────────────────
        // [C/ASM HOOK: void images composited here — see VoidRenderer]
        voidRenderer.render(g2, world, W, H, renderPhase);

        // ── 2. PLANET SPHERE ──────────────────────────────────────────────────
        // Sphere positioned at lower-center — player appears to stand on top
        float sphereCX   = W / 2.0f;
        float sphereCY   = H * 0.62f;                   // slightly below center
        float feetToPixels = Math.min(W, H) / 12.0f;    // scale: ~12ft fills screen
        float screenRadius = (world.planetDiameterFeet / 2.0f) * feetToPixels;

        // [PYTHON HOOK: sphere texture loaded in SphereRenderer if present]
        sphereRenderer.render(g2, world, sphereCX, sphereCY, screenRadius, feetToPixels);

        // ── 3. SIDE PANELS (E key) ────────────────────────────────────────────
        // [PYTHON HOOK: panel content set via [PANEL_UPDATE:] tags — see SidePanels]
        sidePanels.render(g2, world, W, H, isStandalone);

        // ── 4. NARRATE TEXT ───────────────────────────────────────────────────
        // [PYTHON HOOK: text content from [GAME_NARRATE:] tags]
        if (!world.narrateText.isEmpty()
                && System.currentTimeMillis() < world.narrateExpireMs) 
        {
            renderNarrateText(g2, W, H);
        }

        // ── 5. REFRESH OVERLAY (ESC) ──────────────────────────────────────────
        if (world.overlayVisible) 
        {
            refreshOverlay.render(g2, W, H, timing, scaler, isStandalone);
        }

        // ── 6. MODE INDICATOR (always visible, bottom-right corner) ───────────
        renderModeIndicator(g2, W, H);
    }

    // ── NARRATE TEXT ──────────────────────────────────────────────────────────

    private void renderNarrateText(Graphics2D g, int W, int H) 
    {
        // Narrate text appears above the sphere, centered
        // Content: Python-owned. Rendering position/style: Java-owned.
        long remaining  = world.narrateExpireMs - System.currentTimeMillis();
        float fadeAlpha = Math.min(1.0f, remaining / 1000.0f);
        int alpha       = (int)(fadeAlpha * 200);

        g.setFont(new Font("Monospaced", Font.ITALIC, 12));
        FontMetrics fm = g.getFontMetrics();
        String text    = world.narrateText;
        int tx         = (W - fm.stringWidth(text)) / 2;
        int ty         = (int)(H * 0.32f);

        // Shadow
        g.setColor(new Color(0, 0, 0, alpha / 2));
        g.drawString(text, tx + 1, ty + 1);

        // Text
        g.setColor(new Color(220, 235, 255, alpha));
        g.drawString(text, tx, ty);
    }

    // ── MODE INDICATOR ────────────────────────────────────────────────────────

    private void renderModeIndicator(Graphics2D g, int W, int H) 
    {
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(80, 60, 110, 140));

        String asmStr = timing.isASMPresent() ? "ASM" : "JAVA";
        String modeStr;
        switch (world.timingMode) 
        {
            case TimingBridge.MODE_OVERLAY: modeStr = "ESC"; break;
            case TimingBridge.MODE_PANELS:  modeStr = "E";   break;
            default:                        modeStr = "PLAY"; break;
        }

        String label = asmStr + " | " + timing.getDisplayRate() + " | " + modeStr;
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, W - fm.stringWidth(label) - 8, H - 8);
    }

    // ── KEY HANDLER ───────────────────────────────────────────────────────────

    @Override
    public void keyPressed(KeyEvent e) 
    {
        int key = e.getKeyCode();

        // Movement → physics
        if (key == KeyEvent.VK_A) physics.setMoveLeft(true);
        if (key == KeyEvent.VK_D) physics.setMoveRight(true);
        if (key == KeyEvent.VK_W) physics.setMoveForward(true);
        if (key == KeyEvent.VK_S) physics.setMoveBack(true);

        if (key == KeyEvent.VK_SPACE) 
        {
            physics.requestJump();
            jumpCount++;
            // [BASH AI HOOK: jump events accumulated, written on session exit]
        }

        // ESC — toggle refresh overlay, switch to MODE_OVERLAY
        if (key == KeyEvent.VK_ESCAPE) 
        {
            world.overlayVisible = !world.overlayVisible;
            world.timingMode = world.overlayVisible
                ? TimingBridge.MODE_OVERLAY
                : (world.panelsVisible ? TimingBridge.MODE_PANELS : TimingBridge.MODE_GAMEPLAY);
            timing.setMode(world.timingMode);
        }

        // E — toggle side panels, switch to MODE_PANELS
        if (key == KeyEvent.VK_E) 
        {
            world.panelsVisible   = !world.panelsVisible;
            world.panelSlideTarget = world.panelsVisible ? 1.0f : 0.0f;
            world.timingMode = world.panelsVisible
                ? TimingBridge.MODE_PANELS
                : (world.overlayVisible ? TimingBridge.MODE_OVERLAY : TimingBridge.MODE_GAMEPLAY);
            timing.setMode(world.timingMode);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) 
    {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_A) physics.setMoveLeft(false);
        if (key == KeyEvent.VK_D) physics.setMoveRight(false);
        if (key == KeyEvent.VK_W) physics.setMoveForward(false);
        if (key == KeyEvent.VK_S) physics.setMoveBack(false);
    }

    @Override public void keyTyped(KeyEvent e) {}

    // ── MOUSE HANDLERS ────────────────────────────────────────────────────────

    @Override
    public void mouseMoved(MouseEvent e) 
    {
        if (world.overlayVisible) 
        {
            refreshOverlay.onMouseMove(e.getX(), e.getY(), getWidth());
        }
        if (world.panelsVisible) 
        {
            sidePanels.onMouseMove(e.getX(), e.getY(), getWidth(), getHeight(),
                                   world.panelSlideProgress);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) 
    {
        if (world.overlayVisible) 
        {
            refreshOverlay.onMouseClick(e.getX(), e.getY(), getWidth(), isStandalone);
        }
        if (world.panelsVisible) 
        {
            sidePanels.onMouseClick(e.getX(), e.getY(), getWidth(), getHeight(),
                                    world.panelSlideProgress, isStandalone);
        }
    }

    @Override public void mouseDragged(MouseEvent e) {}
    @Override public void mousePressed(MouseEvent e)  {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e)  {}
    @Override public void mouseExited(MouseEvent e)   {}

    // ── GAME PROTOCOL ACCESSOR ────────────────────────────────────────────────
    // Called by Game.java to wire this into ApiBridge.
    // [HOOK: Game.java sets state.gameProtocol = sandbox.getProtocol()]

    public GameProtocol getProtocol() { return protocol; }

    /**
     * Sets the callback fired when the user clicks the refresh rate display
     * in the ESC overlay while in Matrix context.
     * Game.java sets this to imageViewer.restoreManifest() so clicking the
     * rate display exits fullscreen cinematic mode.
     * In standalone context this is set to the game exit action instead.
     */
    public void setFullscreenExitCallback(Runnable cb)
    {
        refreshOverlay.setRateClickCallback(cb);
    }

    // ── STANDALONE TEST WRAPPER ───────────────────────────────────────────────
    // Full game functional without GoddessMatrix, GoddessAPI.py, or ASM present.
    // Run: java -cp . modular.game.IceSandbox
    // (or uncomment and compile directly)

    public static void main(String[] args) 
    {
        SwingUtilities.invokeLater(() -> 
        {
            JFrame frame = new JFrame("ICE SANDBOX — Standalone Mode");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            frame.setLocationRelativeTo(null);
            frame.getContentPane().setBackground(new Color(2, 4, 10));

            IceSandbox sandbox = new IceSandbox(); // standalone constructor
            frame.add(sandbox);
            frame.setVisible(true);
            sandbox.requestFocusInWindow();
            sandbox.startEngine();
        });
    }
}
