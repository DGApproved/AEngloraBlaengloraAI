package modular.game;

/*
 * MiniGameEngine.java
 *
 * Runs discovered HTML mechanics natively in the 3D game viewport.
 * No popup windows. Mechanics render as overlays over the game world.
 *
 * MECHANICS:
 *   SIGNAL    — Frequency tuning. Arrow keys adjust playerFreq toward
 *               targetFreq. Lock gains raw noise. Target drifts over time.
 *
 *   DECRYPT   — Hold F while SIGNAL is locked. Spends raw noise,
 *               fills decrypt bar, yields blue + gold threads on completion.
 *
 *   WEAVE     — Press Z (blue) or X (gold) to add threads to the loom.
 *               Match the current recipe to craft an artifact.
 *               Recipes: Pancake 1B+3G, Logic Gate 3B+1G,
 *                        Dream Catcher 2B+2G, Happy Mac 2B+4G
 *
 *   SANCTUARY — Entropy rises passively. F = Hammer (entropy -25, strain +5).
 *               G = Headdress (entropy -5, insight +2). Strain > 100 = fail.
 *               Insight >= 100 = victory.
 *
 *   CLOCK     — Two rings orbit at different speeds (matches CelestialClock
 *               dual-rhythm). Press A (logic ring) or L (love ring) when
 *               the runner reaches the target zone.
 *
 * RENDERING:
 *   Each mechanic draws a compact overlay in the lower portion of the viewport.
 *   The 3D world remains visible behind it.
 *   ESC closes the active mechanic (returns to world exploration).
 *
 * Contributors:
 *   Derek Jason Gilhousen — mechanic designs (HTML originals),
 *                           Aengloria resource loop
 *   Claude (Anthropic)    — MiniGameEngine native implementation
 */

import java.awt.*;
import java.awt.geom.*;

public class MiniGameEngine
{
    // ── OVERLAY GEOMETRY ──────────────────────────────────────────────────────
    private static final int OVERLAY_H    = 180;
    private static final int CORNER_R     = 10;
    private static final Color OVERLAY_BG = new Color(5, 8, 18, 220);
    private static final Color BORDER     = new Color(157, 80, 187, 160);

    // ── SIGNAL STATE ──────────────────────────────────────────────────────────
    private float targetFreq  = 50f;
    private float playerFreq  = 20f;
    private float signalLock  = 0f;
    private float signalPulse = 0f;

    // ── DECRYPT STATE ─────────────────────────────────────────────────────────
    private float decryptProgress = 0f;

    // ── WEAVE STATE ───────────────────────────────────────────────────────────
    private static final int[][] RECIPES = {
        {1, 3},  // Pancake
        {3, 1},  // Logic Gate
        {2, 2},  // Dream Catcher
        {2, 4}   // Happy Mac
    };
    private static final String[] RECIPE_NAMES = {
        "PANCAKE", "LOGIC GATE", "DREAM CATCHER", "HAPPY MAC"
    };
    private int   currentRecipe = 0;
    private int   wovenBlue     = 0;
    private int   wovenGold     = 0;
    private String weaveMsg     = "WEAVING...";

    // ── SANCTUARY STATE ───────────────────────────────────────────────────────
    private float sanctuary_entropy = 30f;
    private float sanctuary_insight = 0f;
    private float sanctuary_strain  = 0f;
    private String sanctuary_status = "";

    // ── CLOCK STATE ───────────────────────────────────────────────────────────
    private float clockLogicAngle = 0f;
    private float clockLoveAngle  = 0f;
    private float clockHarmony    = 0f;
    private static final float TARGET_ZONE    = (float)(Math.PI * 1.5);
    private static final float ZONE_WIDTH     = 0.4f;
    private static final float LOGIC_SPEED    = 0.02f;
    private static final float LOVE_SPEED     = 0.035f;

    // ── INPUT FLAGS (set by IceSandbox key handler) ───────────────────────────
    public boolean keyLeft   = false;
    public boolean keyRight  = false;
    public boolean keyF      = false;  // interact / hammer
    public boolean keyG      = false;  // headdress
    public boolean keyZ      = false;  // add blue thread
    public boolean keyX      = false;  // add gold thread
    public boolean keyA      = false;  // logic ring (clock)
    public boolean keyL      = false;  // love ring (clock)

    // ── TICK COUNTER for drift ────────────────────────────────────────────────
    private int ticks = 0;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called each logic tick (81ms) when a minigame node is active.
     * Updates mechanic state and applies resource changes to WorldState.
     */
    public void tick(WorldState.GameNode node, WorldState world)
    {
        if (node == null) return;
        ticks++;

        switch (node.mechanic)
        {
            case HtmlGameScanner.MECH_SIGNAL:    tickSignal(world);    break;
            case HtmlGameScanner.MECH_DECRYPT:   tickDecrypt(world);   break;
            case HtmlGameScanner.MECH_WEAVE:     tickWeave(world);     break;
            case HtmlGameScanner.MECH_SANCTUARY: tickSanctuary(world); break;
            case HtmlGameScanner.MECH_CLOCK:     tickClock(world);     break;
        }

        // Consume single-press keys
        keyZ = false; keyX = false; keyA = false; keyL = false;
    }

    // ── SIGNAL CATCHER ────────────────────────────────────────────────────────

    private void tickSignal(WorldState world)
    {
        if (keyLeft)  playerFreq = Math.max(10f, playerFreq - 0.8f);
        if (keyRight) playerFreq = Math.min(90f, playerFreq + 0.8f);

        float diff = Math.abs(targetFreq - playerFreq);
        if (diff < 5f)
        {
            signalLock    = Math.min(1f, signalLock + 0.025f);
            world.rawNoise += 0.12f;
        }
        else
        {
            signalLock = Math.max(0f, signalLock - 0.04f);
        }

        // Target drifts occasionally
        if (ticks % 80 == 0)
            targetFreq = 20f + (float)(Math.random() * 60f);

        signalPulse += 0.15f;
    }

    // ── FORENSIC LENS / DECRYPT ───────────────────────────────────────────────

    private void tickDecrypt(WorldState world)
    {
        if (world.rawNoise >= 1f && keyF)
        {
            decryptProgress += 1.5f;
            world.rawNoise  -= 0.2f;

            if (decryptProgress >= 100f)
            {
                decryptProgress  = 0f;
                world.blueThreads += 1 + (int)(Math.random() * 3);
                world.goldThreads += 1 + (int)(Math.random() * 3);
            }
        }
        else
        {
            decryptProgress = Math.max(0f, decryptProgress - 0.8f);
        }
    }

    // ── GARDEN WEAVER ─────────────────────────────────────────────────────────

    private void tickWeave(WorldState world)
    {
        int needBlue = RECIPES[currentRecipe][0];
        int needGold = RECIPES[currentRecipe][1];

        if (keyZ && world.blueThreads > 0)
        {
            world.blueThreads--;
            wovenBlue++;
        }
        if (keyX && world.goldThreads > 0)
        {
            world.goldThreads--;
            wovenGold++;
        }

        if (wovenBlue == needBlue && wovenGold == needGold)
        {
            weaveMsg = "CREATED: " + RECIPE_NAMES[currentRecipe] + "!";
            world.artifacts++;
            // Reset after brief display pause
            if (ticks % 20 == 0) resetWeave();
        }
        else if (wovenBlue > needBlue || wovenGold > needGold)
        {
            weaveMsg = "FAILED — LOOM RESET";
            resetWeave();
        }
        else
        {
            weaveMsg = "WEAVING...";
        }
    }

    private void resetWeave()
    {
        wovenBlue = 0; wovenGold = 0;
        currentRecipe = (int)(Math.random() * RECIPES.length);
    }

    // ── SANCTUARY ─────────────────────────────────────────────────────────────

    private void tickSanctuary(WorldState world)
    {
        // Natural entropy rise
        sanctuary_entropy += 0.15f;

        // F = Hammer
        if (keyF)
        {
            sanctuary_entropy = Math.max(0f, sanctuary_entropy - 25f);
            sanctuary_strain  += 5f;
            sanctuary_status  = "STRIKE!";
        }
        // G = Headdress
        else if (keyG)
        {
            sanctuary_entropy = Math.max(0f, sanctuary_entropy - 5f);
            sanctuary_insight += 2f;
            sanctuary_status  = "OBSERVING...";
        }
        else sanctuary_status = "";

        sanctuary_entropy = Math.min(100f, sanctuary_entropy);
        sanctuary_insight = Math.min(100f, sanctuary_insight);

        // Feed results back to world resources
        world.rawNoise    = Math.max(0f, world.rawNoise - 0.01f); // entropy costs noise
        if (sanctuary_insight >= 10f)
        {
            world.blueThreads += (int)(sanctuary_insight / 10f);
            sanctuary_insight  = sanctuary_insight % 10f;
        }
    }

    // ── HARMONY ENGINE / CLOCK ────────────────────────────────────────────────

    private void tickClock(WorldState world)
    {
        clockLogicAngle += LOGIC_SPEED;
        clockLoveAngle  += LOVE_SPEED;
        if (clockLogicAngle > Math.PI * 2) clockLogicAngle -= (float)(Math.PI * 2);
        if (clockLoveAngle  > Math.PI * 2) clockLoveAngle  -= (float)(Math.PI * 2);

        if (keyA) // Logic ring
        {
            if (isInZone(clockLogicAngle))
            {
                clockHarmony   += 10f;
                world.blueThreads++;
            }
            else clockHarmony -= 5f;
        }
        if (keyL) // Love ring
        {
            if (isInZone(clockLoveAngle))
            {
                clockHarmony   += 15f;
                world.goldThreads++;
            }
            else clockHarmony -= 5f;
        }

        clockHarmony = Math.max(0f, Math.min(100f, clockHarmony));
        if (clockHarmony >= 100f)
        {
            world.artifacts++;
            clockHarmony = 0f;
        }
    }

    private boolean isInZone(float angle)
    {
        return Math.abs(angle - TARGET_ZONE) < ZONE_WIDTH;
    }

    // ── RENDER ────────────────────────────────────────────────────────────────

    /**
     * Renders the active mechanic overlay in the lower portion of the viewport.
     * Call from IceSandbox.paintComponent() when world.activeMiniGame != null.
     */
    public void render(Graphics2D g, WorldState.GameNode node,
                       WorldState world, int W, int H)
    {
        if (node == null) return;

        int oy = H - OVERLAY_H - 10;

        // Overlay background
        RoundRectangle2D bg = new RoundRectangle2D.Float(10, oy, W - 20, OVERLAY_H,
                                                          CORNER_R, CORNER_R);
        g.setColor(OVERLAY_BG);
        g.fill(bg);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(bg);

        // Title bar
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(250, 205, 104));
        g.drawString(HtmlGameScanner.displayName(node.mechanic)
                     + "  [ESC=close]", 20, oy + 14);

        // Mechanic-specific content
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        switch (node.mechanic)
        {
            case HtmlGameScanner.MECH_SIGNAL:    renderSignal(g,    world, W, oy); break;
            case HtmlGameScanner.MECH_DECRYPT:   renderDecrypt(g,   world, W, oy); break;
            case HtmlGameScanner.MECH_WEAVE:     renderWeave(g,     world, W, oy); break;
            case HtmlGameScanner.MECH_SANCTUARY: renderSanctuary(g, world, W, oy); break;
            case HtmlGameScanner.MECH_CLOCK:     renderClock(g,     world, W, oy); break;
        }

        // Resource summary — bottom of overlay
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(148, 163, 184, 180));
        g.drawString(String.format("NOISE:%.0f  BLUE:%d  GOLD:%d  ARTIFACTS:%d",
            world.rawNoise, world.blueThreads, world.goldThreads, world.artifacts),
            20, oy + OVERLAY_H - 8);
    }

    // ── SIGNAL RENDER ─────────────────────────────────────────────────────────

    private void renderSignal(Graphics2D g, WorldState world, int W, int oy)
    {
        int cy = oy + 80;
        int x0 = 40, x1 = W - 40;

        // Target wave
        g.setColor(new Color(255, 0, 255, 100));
        g.setStroke(new BasicStroke(2f));
        drawWave(g, targetFreq, cy - 20, x0, x1);

        // Player wave
        Color waveCol = (signalLock > 0.8f)
            ? new Color(255, 255, 255)
            : new Color(255, 0, 255, 220);
        g.setColor(waveCol);
        g.setStroke(new BasicStroke(3f));
        drawWave(g, playerFreq, cy + 10, x0, x1);

        // Lock bar
        int barX = 40, barY = oy + 120, barW = W - 80, barH = 10;
        g.setColor(new Color(40, 30, 60));
        g.fillRoundRect(barX, barY, barW, barH, 4, 4);
        g.setColor(signalLock > 0.8f ? COL_SYNC : new Color(157, 80, 187));
        g.fillRoundRect(barX, barY, (int)(barW * signalLock), barH, 4, 4);

        // Labels
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(new Color(220, 200, 255));
        g.drawString("◄ ► TUNE   LOCK: " + (int)(signalLock * 100) + "%"
                     + (signalLock > 0.8f ? "  SIGNAL LOCKED!" : ""), 40, oy + 148);
        g.drawString(String.format("FREQ %.0f → TARGET %.0f", playerFreq, targetFreq),
                     40, oy + 160);
    }

    private void drawWave(Graphics2D g, float freq, int cy, int x0, int x1)
    {
        GeneralPath path = new GeneralPath();
        boolean first = true;
        for (int x = x0; x <= x1; x += 3)
        {
            float y = cy + (float)(Math.sin((x + signalPulse * 15) * (freq / 1500.0)) * 20);
            if (first) { path.moveTo(x, y); first = false; }
            else path.lineTo(x, y);
        }
        g.draw(path);
    }

    // ── DECRYPT RENDER ────────────────────────────────────────────────────────

    private void renderDecrypt(Graphics2D g, WorldState world, int W, int oy)
    {
        // Decrypt bar
        int barX = 40, barY = oy + 70, barW = W - 80, barH = 20;
        g.setColor(new Color(20, 20, 40));
        g.fillRoundRect(barX, barY, barW, barH, 6, 6);
        g.setColor(new Color(80, 150, 255));
        g.fillRoundRect(barX, barY, (int)(barW * decryptProgress / 100f), barH, 6, 6);
        g.setColor(new Color(148, 163, 184));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(barX, barY, barW, barH, 6, 6);

        // Labels
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(80, 150, 255));
        g.drawString("DECRYPT", 40, oy + 65);

        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(world.rawNoise < 1f
            ? new Color(239, 68, 68)
            : new Color(220, 235, 255));
        g.drawString(world.rawNoise < 1f
            ? "Need raw noise (Signal Catcher first)"
            : "Hold F to decrypt  —  " + (int)decryptProgress + "%",
            40, oy + 105);

        g.setColor(new Color(148, 163, 184));
        g.drawString(String.format("Raw Noise: %.1f  |  Yields: +Blue +Gold threads",
            world.rawNoise), 40, oy + 120);
    }

    // ── WEAVE RENDER ──────────────────────────────────────────────────────────

    private void renderWeave(Graphics2D g, WorldState world, int W, int oy)
    {
        int needBlue = RECIPES[currentRecipe][0];
        int needGold = RECIPES[currentRecipe][1];
        int loomX = 40, loomY = oy + 30;
        int threadW = 10, threadH = 60, gap = 5;

        // Blue threads (vertical)
        for (int i = 0; i < wovenBlue; i++)
        {
            g.setColor(new Color(80, 150, 255, 200));
            g.fillRect(loomX + i * (threadW + gap), loomY, threadW, threadH);
        }
        // Target blue (dim)
        for (int i = wovenBlue; i < needBlue; i++)
        {
            g.setColor(new Color(80, 150, 255, 50));
            g.fillRect(loomX + i * (threadW + gap), loomY, threadW, threadH);
        }

        // Gold threads (horizontal)
        for (int i = 0; i < wovenGold; i++)
        {
            g.setColor(new Color(255, 215, 0, 200));
            g.fillRect(loomX, loomY + i * (threadW + gap),
                       needBlue * (threadW + gap), threadW);
        }
        for (int i = wovenGold; i < needGold; i++)
        {
            g.setColor(new Color(255, 215, 0, 50));
            g.fillRect(loomX, loomY + i * (threadW + gap),
                       needBlue * (threadW + gap), threadW);
        }

        // Recipe info
        int rx = loomX + needBlue * (threadW + gap) + 20;
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(250, 205, 104));
        g.drawString(RECIPE_NAMES[currentRecipe], rx, oy + 42);

        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(new Color(148, 163, 184));
        g.drawString(String.format("Need: %dB + %dG", needBlue, needGold), rx, oy + 56);
        g.drawString(String.format("Have: %dB + %dG",
            world.blueThreads, world.goldThreads), rx, oy + 70);

        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(weaveMsg.startsWith("CREATED")
            ? COL_SYNC : new Color(220, 200, 255));
        g.drawString(weaveMsg, rx, oy + 90);

        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(148, 163, 184));
        g.drawString("[Z] add blue thread  [X] add gold thread", 40, oy + 110);
    }

    // ── SANCTUARY RENDER ──────────────────────────────────────────────────────

    private void renderSanctuary(Graphics2D g, WorldState world, int W, int oy)
    {
        int barW = (W - 80) / 2 - 10;
        int barH = 16;
        int barY = oy + 60;

        // Entropy bar
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(239, 68, 68));
        g.drawString("ENTROPY", 40, barY - 2);
        g.setColor(new Color(40, 20, 20));
        g.fillRoundRect(40, barY, barW, barH, 4, 4);
        g.setColor(sanctuary_entropy > 75f
            ? new Color(255, 50, 50) : new Color(239, 68, 68));
        g.fillRoundRect(40, barY, (int)(barW * sanctuary_entropy / 100f), barH, 4, 4);

        // Insight bar
        int ix = 40 + barW + 20;
        g.setColor(COL_SYNC);
        g.drawString("INSIGHT", ix, barY - 2);
        g.setColor(new Color(20, 40, 20));
        g.fillRoundRect(ix, barY, barW, barH, 4, 4);
        g.setColor(COL_SYNC);
        g.fillRoundRect(ix, barY, (int)(barW * sanctuary_insight / 100f), barH, 4, 4);

        // Strain indicator
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(sanctuary_strain > 50f
            ? new Color(239, 68, 68) : new Color(148, 163, 184));
        g.drawString(String.format("Strain: %.0f/100", sanctuary_strain),
                     40, oy + 96);

        // Status
        if (!sanctuary_status.isEmpty())
        {
            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            g.setColor(new Color(250, 205, 104));
            g.drawString(sanctuary_status, ix, oy + 96);
        }

        // Controls
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(148, 163, 184));
        g.drawString("[F] Hammer (−entropy, +strain)   [G] Headdress (−entropy, +insight)",
                     40, oy + 118);
    }

    // ── CLOCK RENDER ──────────────────────────────────────────────────────────

    private void renderClock(Graphics2D g, WorldState world, int W, int oy)
    {
        int cx = W / 2, cy = oy + 85;
        int r1 = 45, r2 = 68; // logic and love ring radii

        // Target zone arcs
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(cx - r1, cy - r1, r1*2, r1*2,
                  (int)Math.toDegrees(-TARGET_ZONE - ZONE_WIDTH/2) - 90,
                  (int)Math.toDegrees(ZONE_WIDTH));
        g.drawArc(cx - r2, cy - r2, r2*2, r2*2,
                  (int)Math.toDegrees(-TARGET_ZONE - ZONE_WIDTH/2) - 90,
                  (int)Math.toDegrees(ZONE_WIDTH));

        // Ring outlines
        g.setColor(new Color(80, 150, 255, 40));
        g.setStroke(new BasicStroke(4f));
        g.drawOval(cx - r1, cy - r1, r1*2, r1*2);
        g.setColor(new Color(255, 215, 0, 40));
        g.drawOval(cx - r2, cy - r2, r2*2, r2*2);

        // Logic runner
        float lx = cx + (float)(Math.cos(clockLogicAngle) * r1);
        float ly = cy + (float)(Math.sin(clockLogicAngle) * r1);
        g.setColor(isInZone(clockLogicAngle)
            ? new Color(200, 230, 255) : new Color(80, 150, 255));
        g.fillOval((int)lx - 6, (int)ly - 6, 12, 12);

        // Love runner
        float mx = cx + (float)(Math.cos(clockLoveAngle) * r2);
        float my = cy + (float)(Math.sin(clockLoveAngle) * r2);
        g.setColor(isInZone(clockLoveAngle)
            ? new Color(255, 240, 180) : new Color(255, 215, 0));
        g.fillOval((int)mx - 6, (int)my - 6, 12, 12);

        // Harmony bar
        int hbX = W - 120, hbY = oy + 40, hbW = 80, hbH = 10;
        g.setColor(new Color(40, 30, 60));
        g.fillRoundRect(hbX, hbY, hbW, hbH, 4, 4);
        g.setColor(COL_SYNC);
        g.fillRoundRect(hbX, hbY, (int)(hbW * clockHarmony / 100f), hbH, 4, 4);
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(148, 163, 184));
        g.drawString("HARMONY", hbX, hbY - 2);

        // Controls
        g.drawString("[A] logic ring  [L] love ring  — hit target zone",
                     40, oy + 162);
    }

    // ── COLORS ────────────────────────────────────────────────────────────────
    private static final Color COL_SYNC = new Color(80, 220, 120);
}
