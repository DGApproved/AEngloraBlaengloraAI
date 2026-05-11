package modular.game;

/*
 * IceSandbox.java
 *
 * Main game panel for the Goddess Matrix game engine.
 *
 * CAMERA MODEL (Skyrim/Morrowind hybrid):
 *   Mouse always controls camera look when play focus is active.
 *   No toggle needed — move mouse to look around.
 *   Right mouse held = free camera rotate without changing movement direction.
 *   Scroll wheel = zoom in/out (increases/decreases camera distance).
 *   Camera smoothly follows player movement direction via lerp.
 *   WASD movement is camera-relative — W walks toward where you're looking.
 *
 *   Pitch=25 (default): camera elevated, planet visible below.
 *   Pitch=85: almost overhead — full view of the small planet.
 *   Pitch=-85: horizon level — starscape dominant.
 *
 * FOCUS MODEL:
 *   Play focus (default): mouse captured, camera active.
 *   ESC: releases mouse focus, opens timing overlay.
 *   E panel open: mouse released for panel interaction.
 *   Click game area: recaptures mouse, resumes play focus.
 *
 * KEY BINDINGS:
 *   WASD        — walk on sphere (camera-relative direction)
 *   SPACE       — jump
 *   Mouse       — look (always when focused)
 *   Right Mouse — free camera rotate (Skyrim style)
 *   Scroll      — zoom in/out
 *   Arrow Keys  — camera look (backup when mouse unavailable)
 *   ESC         — overlay toggle / release mouse
 *   E           — side panels
 *   C           — AI chat conduit
 *   F           — interact / primary action (minigames)
 *   G           — secondary action (minigames)
 *   Z           — add blue thread (Garden Weaver)
 *   X           — add gold thread (Garden Weaver)
 *   Q           — logic ring (Harmony Engine clock)
 *   L           — love ring (Harmony Engine clock)
 *   (R T Y U I O P H J K N B V 1-0) — unassigned, routed via KeyState
 *
 * CONTRIBUTORS:
 *   Gemini (Google)        — original engine concept
 *   Derek Jason Gilhousen — world design, layer ownership, camera design intent
 *   Claude (Anthropic)    — IceSandbox implementation
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class IceSandbox extends JPanel
        implements Runnable, KeyListener,
                   MouseListener, MouseMotionListener, MouseWheelListener
{
    // ── SUBSYSTEMS ────────────────────────────────────────────────────────────
    private final WorldState      world;
    private final TimingBridge    timing;
    private final PlayerPhysics   physics;
    private final PlanetScaler    scaler;
    private final SphereRenderer  sphereRenderer;
    private final VoidRenderer    voidRenderer;
    private final RefreshOverlay  refreshOverlay;
    private final SidePanels      sidePanels;
    private final GameProtocol    protocol;
    private final GameHiberfile   hiberfile;
    private final MiniGameEngine  miniGameEngine;
    private final KeyState        keys;

    // ── ENGINE ────────────────────────────────────────────────────────────────
    private Thread renderThread;
    private float  renderPhase = 0f;

    // ── CONTEXT ───────────────────────────────────────────────────────────────
    private final boolean isStandalone;
    private final File    gameDir;
    private final File    dgapiSystemDir;

    // ── CAMERA STATE ──────────────────────────────────────────────────────────
    // Targets are set instantly by input.
    // Smooth values lerp toward targets each logic tick.
    private float camYawTarget    = 180f;   // 0=north, 90=east — start behind player
    private float camPitchTarget  = 25f;    // degrees, 0=horizon, 85=overhead
    private float camYawSmooth    = 180f;
    private float camPitchSmooth  = 25f;

    private float camDistTarget   = 6.0f;   // feet from player
    private float camDistSmooth   = 6.0f;
    private static final float CAM_DIST_MIN  = 1.5f;
    private static final float CAM_DIST_MAX  = 18.0f;
    private static final float CAM_SMOOTH    = 0.18f; // lerp factor per tick
    private static final float MOUSE_SENS    = 0.20f; // degrees per pixel
    private static final float ARROW_SENS    = 2.5f;  // degrees per key tick

    // Right mouse = free camera (Skyrim: orbit without turning character)
    private boolean rightMouseDown   = false;
    private boolean playFocused      = false;  // mouse captured for look
    private java.awt.Robot mouseRobot;

    // Auto-follow: when player is walking, camera slowly aligns behind them
    private static final float FOLLOW_SPEED  = 0.04f; // fraction per tick

    // ── CHAT STATE ────────────────────────────────────────────────────────────
    private final StringBuilder chatBuffer  = new StringBuilder();
    private static final int    MAX_CHAT    = 200;

    // ── COUNTERS ──────────────────────────────────────────────────────────────
    private int jumpCount = 0;

    // ─────────────────────────────────────────────────────────────────────────

    public IceSandbox(File gameDir, File dgapiSystemDir, boolean isMatrix)
    {
        this.gameDir        = gameDir;
        this.dgapiSystemDir = dgapiSystemDir;
        this.isStandalone   = !isMatrix;

        world          = new WorldState();
        world.launchedFromMatrix = isMatrix;
        world.cameraYaw   = camYawSmooth;
        world.cameraPitch = camPitchSmooth;

        timing         = new TimingBridge(gameDir);
        physics        = new PlayerPhysics();
        scaler         = new PlanetScaler(gameDir);
        sphereRenderer = new SphereRenderer();
        voidRenderer   = new VoidRenderer(new File(gameDir, "images"));
        refreshOverlay = new RefreshOverlay();
        sidePanels     = new SidePanels();
        hiberfile      = new GameHiberfile(gameDir);
        miniGameEngine = new MiniGameEngine();
        keys           = new KeyState();

        File dgapiDir = (dgapiSystemDir != null)
                      ? dgapiSystemDir.getParentFile() : null;
        protocol = new GameProtocol(world, sidePanels, gameDir, dgapiDir, isMatrix);

        world.voidImagesDir = new File(gameDir, "images");

        if (isStandalone)
        {
            Runnable exit = () -> {
                stopEngine();
                Window w = SwingUtilities.getWindowAncestor(this);
                if (w != null) w.dispose();
            };
            refreshOverlay.setStandaloneExitCallback(exit);
            sidePanels.setStandaloneExitCallback(exit);
        }

        try { mouseRobot = new java.awt.Robot(); }
        catch (AWTException ignored) {}

        setBackground(new Color(2, 4, 10));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
    }

    public IceSandbox()
    {
        this(resolveStandaloneGameDir(), null, false);
    }

    private static File resolveStandaloneGameDir()
    {
        File f = new File("modular/game");
        f.mkdirs();
        return f;
    }

    // ── ENGINE CONTROL ────────────────────────────────────────────────────────

    public void startEngine()
    {
        if (renderThread != null) return;

        hiberfile.load(world);
        scaler.applyToWorld(world);

        int fn = world.activePlanet;
        world.planets[fn] = buildPlanet(fn);
        protocol.writeSessionEnterEvent(fn);

        // Scan HTML folder for minigame nodes
        File htmlDir = new File(gameDir.getParentFile().getParentFile(), "html");
        world.gameNodes = HtmlGameScanner.scan(htmlDir);

        // Start render thread
        renderThread = new Thread(this, "IceSandbox-RenderLoop");
        renderThread.setDaemon(true);
        renderThread.start();

        // Auto-capture mouse on start (Morrowind style — always looking)
        SwingUtilities.invokeLater(this::captureMouse);
    }

    public void stopEngine()
    {
        releaseMouse();
        protocol.writeJumpEvent(world.playerAltitudeFeet, jumpCount);
        protocol.writeSessionExitEvent(world.activePlanet);
        hiberfile.save(world);
        timing.shutdown();
        renderThread = null;
    }

    // ── MOUSE FOCUS ───────────────────────────────────────────────────────────

    private void captureMouse()
    {
        if (playFocused || !isShowing()) return;
        playFocused = true;
        setCursor(getToolkit().createCustomCursor(
            new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_ARGB),
            new java.awt.Point(), "blank"));
    }

    private void releaseMouse()
    {
        playFocused = false;
        setCursor(Cursor.getDefaultCursor());
    }

    private void centerMouse()
    {
        if (mouseRobot == null || !isShowing()) return;
        try
        {
            Point screen = getLocationOnScreen();
            mouseRobot.mouseMove(screen.x + getWidth()  / 2,
                                 screen.y + getHeight() / 2);
        }
        catch (Exception ignored) {}
    }

    // ── PLANET BUILDER ────────────────────────────────────────────────────────

    private WorldState.SystemPlanet buildPlanet(int fn)
    {
        WorldState.SystemPlanet p = new WorldState.SystemPlanet();
        p.fnNode         = fn;
        p.celestialName  = WorldState.celestialName(fn);
        p.orbitRadius    = WorldState.orbitalRadius(fn);
        p.gravity        = WorldState.baseGravity(fn);
        p.axialTiltDeg   = WorldState.axialTilt(fn);
        p.isSun          = (fn == WorldState.FN_SUN);
        p.isAsteroidBelt = (fn == WorldState.FN_ASTEROID_BELT);
        p.hasRings       = (fn == WorldState.FN_SATURN);
        p.isUserNamed    = (fn == WorldState.FN_KUIPER);
        p.bedrockCount   = scaler.getBedrockCount();
        p.coreRadius     = scaler.getPlanetCoreRadius();

        if (dgapiSystemDir != null)
        {
            File root  = dgapiSystemDir.getParentFile().getParentFile()
                                       .getParentFile().getParentFile();
            File fnDir = new File(root, "fn/fn" + fn);
            if (fnDir.exists())
            {
                p.fnDirectory      = fnDir;
                p.encyclopediaFile = new File(fnDir, "dgapi/virtual/encyclopedia.txt");
                p.dictionaryFile   = new File(fnDir, "dgapi/datas/dictionary.txt");
                p.almanacFile      = new File(fnDir, "dgapi/datas/almanac.txt");
                p.religionFile     = new File(fnDir, "dgapi/virtual/religion.txt");
                p.personaFile      = new File(fnDir, "dgapi/virtual/persona.txt");
                p.journalFile      = new File(fnDir, "dgapi/datas/journal.txt");
            }
        }
        return p;
    }

    // ── GAME LOOP ─────────────────────────────────────────────────────────────

    @Override
    public void run()
    {
        long lastTime   = System.nanoTime();
        long logicAccum = 0;
        final long LOGIC_TICK_NS = 81_000_000L;

        while (renderThread != null)
        {
            long now   = System.nanoTime();
            long delta = now - lastTime;
            lastTime   = now;

            logicAccum += delta;
            if (logicAccum >= LOGIC_TICK_NS)
            {
                updateLogic();
                logicAccum -= LOGIC_TICK_NS;
            }

            renderPhase += 0.03f;
            if (renderPhase > (float)(Math.PI * 2))
                renderPhase -= (float)(Math.PI * 2);

            // Report FPS and advance temporal frame counter to OptimizeRender
            long frameMs = (System.nanoTime() - now) / 1_000_000L;
            if (frameMs > 0)
                OptimizeRender.Governor.reportFPS(1000.0 / frameMs);
            OptimizeRender.Temporal.advance();

            repaint();

            long tickNs  = timing.getTickNanos();
            long elapsed = System.nanoTime() - now;
            long sleepNs = tickNs - elapsed;
            if (sleepNs > 0)
            {
                try
                {
                    Thread.sleep(sleepNs / 1_000_000,
                                 (int)(sleepNs % 1_000_000));
                }
                catch (InterruptedException ignored) {}
            }
        }
    }

    // ── LOGIC UPDATE ──────────────────────────────────────────────────────────

    private void updateLogic()
    {
        scaler.applyToWorld(world);
        world.clock.update();

        if (sidePanels.calendarModeTapped)
        {
            world.clock.calCycleMode();
            sidePanels.calendarModeTapped = false;
        }

        // ── CAMERA SMOOTH (Skyrim-style lerp) ─────────────────────────────────
        // Wrap yaw difference correctly across 360 boundary
        float yawDiff = camYawTarget - camYawSmooth;
        if (yawDiff >  180) yawDiff -= 360;
        if (yawDiff < -180) yawDiff += 360;
        camYawSmooth    += yawDiff   * CAM_SMOOTH;
        camPitchSmooth  += (camPitchTarget  - camPitchSmooth)  * CAM_SMOOTH;
        camDistSmooth   += (camDistTarget   - camDistSmooth)   * CAM_SMOOTH;

        // Wrap smooth yaw
        if (camYawSmooth <    0) camYawSmooth += 360f;
        if (camYawSmooth >= 360) camYawSmooth -= 360f;

        // ── CAMERA AUTO-FOLLOW (Skyrim: camera swings behind when walking) ────
        boolean walking = keys.moveLeft || keys.moveRight
                       || keys.moveForward || keys.moveBack;
        if (walking && !rightMouseDown && world.activeMiniGame == null)
        {
            // Gently pull camera yaw toward the movement direction
            // (0 = directly behind). Only pitch, not horizontal yaw.
            // This means the camera follows laterally but lets player
            // free-look vertically without snapping.
            float targetPitch = 25f; // comfortable walking pitch
            camPitchTarget += (targetPitch - camPitchTarget) * FOLLOW_SPEED;
        }

        // Write smoothed values to WorldState for renderer
        world.cameraYaw   = camYawSmooth;
        world.cameraPitch = camPitchSmooth;

        // ── ARROW KEY CAMERA (backup — active when play not focused) ──────────
        if (!playFocused || world.overlayVisible)
        {
            if (keys.camLeft)
            {
                camYawTarget -= ARROW_SENS;
                if (camYawTarget < 0) camYawTarget += 360f;
            }
            if (keys.camRight)
            {
                camYawTarget += ARROW_SENS;
                if (camYawTarget >= 360) camYawTarget -= 360f;
            }
            if (keys.camUp)
                camPitchTarget = Math.min(85f, camPitchTarget + ARROW_SENS);
            if (keys.camDown)
                camPitchTarget = Math.max(-85f, camPitchTarget - ARROW_SENS);
        }

        // ── PHYSICS ───────────────────────────────────────────────────────────
        WorldState.SystemPlanet planet = world.planets[world.activePlanet];
        float gravity = (planet != null) ? planet.gravity : 1.0f;
        float radius  = (planet != null) ? planet.coreRadius
                                         : scaler.getPlanetCoreRadius();

        // Camera-relative movement — set yaw before tick
        physics.setCameraYaw(camYawSmooth);
        keys.applyToPhysics(physics);

        if (keys.jump) physics.requestJump();
        physics.tick(radius, gravity);
        physics.writeToWorld(world, radius);

        // ── PANEL SLIDE ───────────────────────────────────────────────────────
        float diff = world.panelSlideTarget - world.panelSlideProgress;
        if (Math.abs(diff) < WorldState.PANEL_SLIDE_SPEED)
            world.panelSlideProgress = world.panelSlideTarget;
        else
            world.panelSlideProgress +=
                Math.signum(diff) * WorldState.PANEL_SLIDE_SPEED;

        // ── TELEMETRY ─────────────────────────────────────────────────────────
        protocol.pollHardwareTelemetry(dgapiSystemDir);

        // ── ORBITAL MECHANICS ─────────────────────────────────────────────────
        for (int i = 1; i <= 12; i++)
        {
            world.orbitAngles[i] += 0.01f;
            if (world.orbitAngles[i] >= 360f) world.orbitAngles[i] -= 360f;
        }

        // ── NARRATE EXPIRY ────────────────────────────────────────────────────
        if (!world.narrateText.isEmpty()
                && System.currentTimeMillis() > world.narrateExpireMs)
            world.narrateText = "";

        // ── MINIGAME ──────────────────────────────────────────────────────────
        HtmlGameScanner.updateUnlocks(world.gameNodes, world);
        world.activeMiniGame = HtmlGameScanner.findNearestActive(
            world.gameNodes, world.playerAngleDeg, world.playerLatDeg, 15f);
        if (world.activeMiniGame != null)
        {
            keys.applyToMiniGame(miniGameEngine);
            miniGameEngine.tick(world.activeMiniGame, world);
        }
    }

    // ── PAINT ─────────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int W = getWidth(), H = getHeight();

        // 1. Void background
        voidRenderer.render(g2, world, W, H, renderPhase);

        // 2. SDF scene (planet, terrain, player, avatar)
        sphereRenderer.render(g2, world, W, H);

        // 3. Minigame overlay
        if (world.activeMiniGame != null)
            miniGameEngine.render(g2, world.activeMiniGame, world, W, H);

        // 4. Side panels
        sidePanels.render(g2, world, W, H, isStandalone);

        // 5. Narrate text
        if (!world.narrateText.isEmpty()
                && System.currentTimeMillis() < world.narrateExpireMs)
            renderNarrate(g2, W, H);

        // 6. Chat input
        if (world.avatarChatOpen) renderChat(g2, W, H);

        // 7. ESC overlay
        if (world.overlayVisible)
            refreshOverlay.render(g2, W, H, timing, scaler, isStandalone);

        // 8. Mode indicator
        renderModeBar(g2, W, H);

        // 9. Focus hint (shows briefly when mouse is released)
        if (!playFocused && !world.overlayVisible && !world.panelsVisible)
            renderFocusHint(g2, W, H);
    }

    // ── TEXT RENDERS ──────────────────────────────────────────────────────────

    private void renderNarrate(Graphics2D g, int W, int H)
    {
        long  rem = world.narrateExpireMs - System.currentTimeMillis();
        int   a   = (int)(Math.min(1f, rem / 1000f) * 200);
        g.setFont(new Font("Monospaced", Font.ITALIC, 12));
        FontMetrics fm = g.getFontMetrics();
        String text = world.narrateText;
        int tx = (W - fm.stringWidth(text)) / 2;
        int ty = (int)(H * 0.28f);
        g.setColor(new Color(0, 0, 0, a / 2));
        g.drawString(text, tx + 1, ty + 1);
        g.setColor(new Color(220, 200, 255, a));
        g.drawString(text, tx, ty);
    }

    private void renderChat(Graphics2D g, int W, int H)
    {
        int[] b = chatBoxBounds(W, H);
        String prompt = "> " + chatBuffer + "_";

        // Semi-transparent background — visible but doesn't block world view
        g.setColor(new Color(8, 10, 20, 140));
        g.fillRoundRect(b[0], b[1], b[2], b[3], 8, 8);

        // Subtle border
        g.setColor(new Color(157, 80, 187, 120));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(b[0], b[1], b[2], b[3], 8, 8);

        // Input text
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(220, 200, 255, 210));
        g.drawString(prompt, b[0] + 10, b[1] + 18);

        // Hint
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(148, 163, 184, 120));
        g.drawString("ENTER=send  ESC=close", b[0] + b[2] - 130, b[1] + 18);
    }

    /** Chatbox bounds as [x, y, w, h]. Used by both renderChat and mouseClicked. */
    private int[] chatBoxBounds(int W, int H)
    {
        int boxW = Math.min(W - 40, 500);
        int boxX = (W - boxW) / 2;
        int boxY = (int)(H * 0.75f);
        return new int[]{ boxX, boxY, boxW, 28 };
    }

    private void renderModeBar(Graphics2D g, int W, int H)
    {
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(80, 60, 110, 140));
        String asmStr = timing.isASMPresent() ? "ASM" : "JAVA";
        String mode;
        switch (world.timingMode)
        {
            case TimingBridge.MODE_OVERLAY: mode = "ESC";  break;
            case TimingBridge.MODE_PANELS:  mode = "E";    break;
            default:                        mode = "PLAY"; break;
        }
        String label = asmStr + " | " + timing.getDisplayRate()
                     + " | " + mode
                     + " | " + WorldState.celestialName(world.activePlanet)
                     + (playFocused ? "" : " | [CLICK TO FOCUS]");
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, W - fm.stringWidth(label) - 8, H - 8);
    }

    private void renderFocusHint(Graphics2D g, int W, int H)
    {
        g.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g.setColor(new Color(220, 200, 255, 140));
        String hint = "Click to resume play focus";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(hint, (W - fm.stringWidth(hint)) / 2, H / 2);
    }

    // ── KEY HANDLERS ──────────────────────────────────────────────────────────

    @Override
    public void keyPressed(KeyEvent e)
    {
        int key = e.getKeyCode();

        // Route to KeyState first (covers movement + all mapped keys)
        keys.onKeyPressed(key);

        // ── CHAT CONDUIT intercepts everything when open ───────────────────────
        if (world.avatarChatOpen)
        {
            if (key == KeyEvent.VK_ESCAPE)
            {
                world.avatarChatOpen = false;
                chatBuffer.setLength(0);
            }
            else if (key == KeyEvent.VK_ENTER)
            {
                String t = chatBuffer.toString().trim();
                if (!t.isEmpty()) protocol.sendToPython(t);
                chatBuffer.setLength(0);
            }
            else if (key == KeyEvent.VK_BACK_SPACE && chatBuffer.length() > 0)
            {
                chatBuffer.deleteCharAt(chatBuffer.length() - 1);
            }
            return;
        }

        // ── MINIGAME intercepts ESC to close ──────────────────────────────────
        if (world.activeMiniGame != null && key == KeyEvent.VK_ESCAPE)
        {
            world.activeMiniGame = null;
            return;
        }

        // ── SYSTEM KEYS ───────────────────────────────────────────────────────
        if (key == KeyEvent.VK_ESCAPE)
        {
            world.overlayVisible = !world.overlayVisible;
            world.timingMode = world.overlayVisible
                ? TimingBridge.MODE_OVERLAY
                : (world.panelsVisible ? TimingBridge.MODE_PANELS
                                       : TimingBridge.MODE_GAMEPLAY);
            timing.setMode(world.timingMode);

            if (world.overlayVisible) releaseMouse();
            else                      captureMouse();
        }

        if (key == KeyEvent.VK_E)
        {
            world.panelsVisible    = !world.panelsVisible;
            world.panelSlideTarget = world.panelsVisible ? 1f : 0f;
            world.timingMode = world.panelsVisible
                ? TimingBridge.MODE_PANELS
                : (world.overlayVisible ? TimingBridge.MODE_OVERLAY
                                        : TimingBridge.MODE_GAMEPLAY);
            timing.setMode(world.timingMode);

            // Release mouse when panels open so player can click panel content
            if (world.panelsVisible) releaseMouse();
            else                     captureMouse();
        }

        if (key == KeyEvent.VK_C)
        {
            world.avatarChatOpen = !world.avatarChatOpen;
            chatBuffer.setLength(0);
            if (world.avatarChatOpen)
            {
                // Release mouse so player can use cursor for text editing
                releaseMouse();
            }
            else
            {
                world.avatarSpeechText = "";
                // Don't auto-recapture — let click do it
            }
        }

        if (key == KeyEvent.VK_SPACE) jumpCount++;
    }

    @Override
    public void keyTyped(KeyEvent e)
    {
        if (world.avatarChatOpen)
        {
            char c = e.getKeyChar();
            if (c >= 32 && c < 127 && chatBuffer.length() < MAX_CHAT)
                chatBuffer.append(c);
        }
    }

    @Override
    public void keyReleased(KeyEvent e)
    {
        keys.onKeyReleased(e.getKeyCode());
    }

    // ── MOUSE LOOK ────────────────────────────────────────────────────────────

    @Override
    public void mouseMoved(MouseEvent e)
    {
        // Camera look — active when play focused and overlays closed
        if (playFocused && !world.overlayVisible && !world.panelsVisible
                && !world.avatarChatOpen && isShowing())
        {
            int cx = getWidth()  / 2;
            int cy = getHeight() / 2;
            int dx = e.getX() - cx;
            int dy = e.getY() - cy;

            if (dx != 0 || dy != 0)
            {
                // Horizontal mouse = yaw (look left/right)
                camYawTarget += dx * MOUSE_SENS;
                if (camYawTarget <    0) camYawTarget += 360f;
                if (camYawTarget >= 360) camYawTarget -= 360f;

                // Vertical mouse = pitch (look up/down)
                // Inverted Y: mouse up = look up (pitch decreases = camera lower)
                camPitchTarget = Math.max(-85f,
                                 Math.min( 85f, camPitchTarget - dy * MOUSE_SENS));

                centerMouse();
            }
        }
        else
        {
            // When not play-focused: hover for overlay/panel interaction
            if (world.overlayVisible)
                refreshOverlay.onMouseMove(e.getX(), e.getY(), getWidth());
            if (world.panelsVisible)
                sidePanels.onMouseMove(e.getX(), e.getY(), getWidth(),
                                       getHeight(), world.panelSlideProgress);
        }
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
        // Right mouse drag = free camera (Skyrim orbit)
        if (rightMouseDown && isShowing())
        {
            int cx = getWidth()  / 2;
            int cy = getHeight() / 2;
            int dx = e.getX() - cx;
            int dy = e.getY() - cy;

            if (dx != 0 || dy != 0)
            {
                camYawTarget += dx * MOUSE_SENS;
                if (camYawTarget <    0) camYawTarget += 360f;
                if (camYawTarget >= 360) camYawTarget -= 360f;

                camPitchTarget = Math.max(-85f,
                                 Math.min( 85f, camPitchTarget - dy * MOUSE_SENS));
                centerMouse();
            }
        }
        // Also feed hover state for panels during drag
        else if (world.overlayVisible)
            refreshOverlay.onMouseMove(e.getX(), e.getY(), getWidth());
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
        if (e.getButton() == MouseEvent.BUTTON3)
        {
            // Right mouse — free camera mode (Skyrim orbit)
            rightMouseDown = true;
            if (!playFocused) captureMouse();
            centerMouse();
        }
        else if (e.getButton() == MouseEvent.BUTTON1)
        {
            // Left click — use KeyState to decide whether to recapture
            if (!playFocused)
            {
                int[] b = chatBoxBounds(getWidth(), getHeight());
                boolean shouldCapture = keys.clickShouldCapture(
                    e.getX(), e.getY(), getWidth(), getHeight(),
                    world.overlayVisible, world.panelsVisible,
                    world.avatarChatOpen,
                    b[0], b[1], b[2], b[3]);
                if (shouldCapture) captureMouse();
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
        if (e.getButton() == MouseEvent.BUTTON3)
            rightMouseDown = false;
    }

    @Override
    public void mouseClicked(MouseEvent e)
    {
        int mx = e.getX(), my = e.getY();

        if (world.overlayVisible)
            refreshOverlay.onMouseClick(mx, my, getWidth(), isStandalone);

        if (world.panelsVisible)
            sidePanels.onMouseClick(mx, my, getWidth(), getHeight(),
                                    world.panelSlideProgress, isStandalone);

        // If chat is open and user clicks outside the chatbox — close it
        // and recapture mouse to resume play
        if (world.avatarChatOpen && e.getButton() == MouseEvent.BUTTON1)
        {
            int[] b = chatBoxBounds(getWidth(), getHeight());
            boolean insideChat = mx >= b[0] && mx <= b[0] + b[2]
                              && my >= b[1] && my <= b[1] + b[3];
            if (!insideChat)
            {
                world.avatarChatOpen = false;
                world.avatarSpeechText = "";
                chatBuffer.setLength(0);
                captureMouse();
            }
            // Inside the chatbox: leave mouse free so cursor is usable for selection
        }
    }

    // ── SCROLL WHEEL — zoom ───────────────────────────────────────────────────

    @Override
    public void mouseWheelMoved(MouseWheelEvent e)
    {
        // Scroll toward = zoom in (Skyrim), scroll away = zoom out
        camDistTarget += e.getPreciseWheelRotation() * 0.8f;
        camDistTarget  = Math.max(CAM_DIST_MIN, Math.min(CAM_DIST_MAX, camDistTarget));

        // At minimum zoom: effectively first-person feel
        // At maximum: wide orbital view of the whole planet
    }

    @Override public void mouseEntered(MouseEvent e)  {}
    @Override public void mouseExited(MouseEvent e)   {}

    // ── ACCESSORS ─────────────────────────────────────────────────────────────

    public GameProtocol getProtocol() { return protocol; }

    public void setFullscreenExitCallback(Runnable cb)
    {
        refreshOverlay.setRateClickCallback(cb);
    }

    // ── STANDALONE ENTRY ──────────────────────────────────────────────────────

    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("ICE SANDBOX — Standalone");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            frame.setLocationRelativeTo(null);
            frame.getContentPane().setBackground(new Color(2, 4, 10));
            IceSandbox sandbox = new IceSandbox();
            frame.add(sandbox);
            frame.setVisible(true);
            sandbox.requestFocusInWindow();
            sandbox.startEngine();
        });
    }
}
