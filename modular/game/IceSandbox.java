package modular.game;

/*
 * IceSandbox.java
 *
 * Main game panel for the Goddess Matrix game engine.
 * Player stands on a sphere whose size is determined by the
 * total file count in modular/game/. The world is built from
 * SDF ray marching over spheres derived from knowledge files.
 *
 * LAYER OWNERSHIP:
 *   Void background       → VoidRenderer (C/ASM images + starfield)
 *   3D scene / planet     → SphereRenderer (SDF ray march)
 *   Side panels (E key)   → SidePanels (Java frame, Python content)
 *   Refresh overlay (ESC) → RefreshOverlay
 *   In-game text content  → Python via [GAME_NARRATE:] tags
 *   In-game text render   → Java (IceSandbox)
 *   Timing                → TimingBridge (ASM binary or Java fallback)
 *   Physics               → PlayerPhysics
 *   AI events             → GameProtocol → game_events.txt → GoddessAPI.sh
 *   C key conduit         → GameProtocol.sendToPython() → direct Python stdin
 *
 * LAUNCH DETECTION:
 *   Matrix context  → IceSandbox(gameDir, dgapiSysDir, true)
 *     Embedded in ImageViewer manifest panel via Game.java
 *     Python stdin set by Game.java after construction
 *     ESC overlay click → restoreManifest (set by Game.java callback)
 *     Right-click [Game] → cinematic fullscreen (Keyboard.java)
 *   Standalone      → IceSandbox() — no-arg constructor
 *     Runs in its own JFrame via main()
 *     ESC overlay click → exit game
 *     E panel shows EXIT button
 *
 * KEY BINDINGS:
 *   WASD   — walk on sphere surface
 *   SPACE  — jump
 *   ESC    — toggle timing/rate overlay (MODE_OVERLAY)
 *   E      — toggle side panels (MODE_PANELS)
 *   C      — toggle AI chat conduit (direct Python stdin)
 *            When open: typed text → avatar speech prompt
 *            ENTER sends, ESC closes
 *
 * HIBERFILE:
 *   Loaded on startEngine, saved on stopEngine.
 *   Cache hash validated — fast resume if knowledge files unchanged.
 *
 * Contributors:
 *   Gemini (Google)        — original engine concept and physics
 *   Derek Jason Gilhousen — world design, layer ownership, C key conduit,
 *                           material philosophy, scale tier system
 *   Claude (Anthropic)    — IceSandbox integration of all subsystems
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class IceSandbox extends JPanel
        implements Runnable, KeyListener, MouseListener, MouseMotionListener
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

    // ── ENGINE ────────────────────────────────────────────────────────────────
    private Thread  renderThread;
    private float   renderPhase = 0f;

    // ── CONTEXT ───────────────────────────────────────────────────────────────
    private final boolean isStandalone;
    private final File    gameDir;
    private final File    dgapiSystemDir;

    // ── C KEY CHAT STATE ──────────────────────────────────────────────────────
    private final StringBuilder chatInputBuffer = new StringBuilder();
    private static final int    MAX_CHAT_INPUT  = 200;

    // ── COUNTERS ──────────────────────────────────────────────────────────────
    private int jumpCount = 0;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Matrix-context constructor. Called by Game.java.
     * After construction, Game.java should call:
     *   protocol.setPythonStdin(state.apiStdinMap.get(state.currentSession))
     */
    public IceSandbox(File gameDir, File dgapiSystemDir, boolean isMatrix)
    {
        this.gameDir        = gameDir;
        this.dgapiSystemDir = dgapiSystemDir;
        this.isStandalone   = !isMatrix;

        world          = new WorldState();
        world.launchedFromMatrix = isMatrix;

        timing         = new TimingBridge(gameDir);
        physics        = new PlayerPhysics();
        scaler         = new PlanetScaler(gameDir);
        sphereRenderer = new SphereRenderer();
        voidRenderer   = new VoidRenderer(new File(gameDir, "images"));
        refreshOverlay = new RefreshOverlay();
        sidePanels     = new SidePanels();
        hiberfile      = new GameHiberfile(gameDir);

        File dgapiDir = (dgapiSystemDir != null)
                      ? dgapiSystemDir.getParentFile() : null;
        protocol = new GameProtocol(world, sidePanels, gameDir, dgapiDir, isMatrix);

        world.voidImagesDir = new File(gameDir, "images");

        // Standalone exit callbacks
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

        setBackground(new Color(2, 4, 10));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    /** Standalone constructor — no Matrix context. */
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

        // Load hiberfile — fast resume if cache valid
        boolean cacheValid = hiberfile.load(world);
        scaler.applyToWorld(world);

        // Apply planet gravity to active session
        int fn = world.activePlanet;
        world.planets[fn] = buildPlanet(fn);

        protocol.writeSessionEnterEvent(fn);

        renderThread = new Thread(this, "IceSandbox-RenderLoop");
        renderThread.setDaemon(true);
        renderThread.start();
    }

    public void stopEngine()
    {
        protocol.writeJumpEvent(world.playerAltitudeFeet, jumpCount);
        protocol.writeSessionExitEvent(world.activePlanet);
        hiberfile.save(world);
        timing.shutdown();
        renderThread = null;
    }

    // ── PLANET BUILDER ────────────────────────────────────────────────────────
    // Constructs a SystemPlanet record for the given fn node.
    // In a full implementation this would scan the fn directory and
    // build TerrainSpheres from txt file contents.
    // For V1: sets orbital properties and gravity, terrain populated later.

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

        // Resolve fn directory if Matrix context
        if (dgapiSystemDir != null)
        {
            File fnBase = dgapiSystemDir.getParentFile()  // dgapi/
                                        .getParentFile()  // fn{N}/
                                        .getParentFile()  // fn base
                                        .getParentFile(); // root
            File fnDir  = new File(fnBase, "fn/fn" + fn);
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
        long lastTime  = System.nanoTime();
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
            if (renderPhase > (float)(Math.PI * 2)) renderPhase -= (float)(Math.PI * 2);

            repaint();

            long tickNs  = timing.getTickNanos();
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
        scaler.applyToWorld(world);

        // Gravity from active planet
        WorldState.SystemPlanet planet = world.planets[world.activePlanet];
        float gravity = (planet != null) ? planet.gravity : 1.0f;
        float radius  = (planet != null) ? planet.coreRadius : scaler.getPlanetCoreRadius();

        physics.tick(radius, gravity);
        physics.writeToWorld(world, radius);

        // Panel slide animation
        float diff = world.panelSlideTarget - world.panelSlideProgress;
        if (Math.abs(diff) < WorldState.PANEL_SLIDE_SPEED)
            world.panelSlideProgress = world.panelSlideTarget;
        else
            world.panelSlideProgress += Math.signum(diff) * WorldState.PANEL_SLIDE_SPEED;

        // Hardware telemetry → atmosphere + avatar glow
        protocol.pollHardwareTelemetry(dgapiSystemDir);

        // Advance orbital angles
        for (int i = 1; i <= 12; i++)
        {
            world.orbitAngles[i] += 0.01f; // slow orbit, visual only
            if (world.orbitAngles[i] >= 360f) world.orbitAngles[i] -= 360f;
        }

        // Narrate text expiry
        if (!world.narrateText.isEmpty()
                && System.currentTimeMillis() > world.narrateExpireMs)
            world.narrateText = "";
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

        int W = getWidth();
        int H = getHeight();

        // 1. Void background (C/ASM images + starfield + aurora)
        voidRenderer.render(g2, world, W, H, renderPhase);

        // 2. SDF ray march (planet, terrain, player, avatar)
        //    SphereRenderer should use TYPE_INT_ARGB with transparent sky pixels
        //    so background shows through void space. See SphereRenderer note.
        sphereRenderer.render(g2, world, W, H);

        // 3. Side panels (E key)
        sidePanels.render(g2, world, W, H, isStandalone);

        // 4. Narrate text (content from Python, position from Java)
        if (!world.narrateText.isEmpty()
                && System.currentTimeMillis() < world.narrateExpireMs)
            renderNarrateText(g2, W, H);

        // 5. C key chat input prompt
        if (world.avatarChatOpen) renderChatInput(g2, W, H);

        // 6. ESC overlay
        if (world.overlayVisible)
            refreshOverlay.render(g2, W, H, timing, scaler, isStandalone);

        // 7. Mode indicator (always visible, bottom-right)
        renderModeIndicator(g2, W, H);
    }

    // ── TEXT RENDERS ──────────────────────────────────────────────────────────

    private void renderNarrateText(Graphics2D g, int W, int H)
    {
        long  rem   = world.narrateExpireMs - System.currentTimeMillis();
        float alpha = Math.min(1f, rem / 1000f);
        int   a     = (int)(alpha * 200);

        g.setFont(new Font("Monospaced", Font.ITALIC, 12));
        FontMetrics fm = g.getFontMetrics();
        String text    = world.narrateText;
        int    tx      = (W - fm.stringWidth(text)) / 2;
        int    ty      = (int)(H * 0.28f);

        g.setColor(new Color(0, 0, 0, a / 2));
        g.drawString(text, tx + 1, ty + 1);
        g.setColor(new Color(220, 200, 255, a));
        g.drawString(text, tx, ty);
    }

    private void renderChatInput(Graphics2D g, int W, int H)
    {
        // C key chat conduit input prompt
        // Content the user types routes directly to Python stdin
        String prompt  = "> " + chatInputBuffer + "_";
        int    boxW    = Math.min(W - 40, 500);
        int    boxX    = (W - boxW) / 2;
        int    boxY    = (int)(H * 0.75f);

        g.setColor(new Color(8, 10, 20, 210));
        g.fillRoundRect(boxX, boxY, boxW, 28, 8, 8);
        g.setColor(new Color(157, 80, 187, 180));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(boxX, boxY, boxW, 28, 8, 8);

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(220, 200, 255, 220));
        g.drawString(prompt, boxX + 10, boxY + 18);

        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(148, 163, 184, 140));
        g.drawString("ENTER=send  ESC=close", boxX + boxW - 130, boxY + 18);
    }

    private void renderModeIndicator(Graphics2D g, int W, int H)
    {
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(80, 60, 110, 140));
        String asmStr  = timing.isASMPresent() ? "ASM" : "JAVA";
        String modeStr;
        switch (world.timingMode)
        {
            case TimingBridge.MODE_OVERLAY: modeStr = "ESC";  break;
            case TimingBridge.MODE_PANELS:  modeStr = "E";    break;
            default:                        modeStr = "PLAY"; break;
        }
        String label = asmStr + " | " + timing.getDisplayRate() + " | " + modeStr
                     + " | " + WorldState.celestialName(world.activePlanet);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, W - fm.stringWidth(label) - 8, H - 8);
    }

    // ── KEY HANDLER ───────────────────────────────────────────────────────────

    @Override
    public void keyPressed(KeyEvent e)
    {
        int key = e.getKeyCode();

        // C key chat conduit — ESC to close, ENTER to send
        if (world.avatarChatOpen)
        {
            if (key == KeyEvent.VK_ESCAPE)
            {
                world.avatarChatOpen = false;
                chatInputBuffer.setLength(0);
            }
            else if (key == KeyEvent.VK_ENTER)
            {
                String text = chatInputBuffer.toString().trim();
                if (!text.isEmpty()) protocol.sendToPython(text);
                chatInputBuffer.setLength(0);
            }
            else if (key == KeyEvent.VK_BACK_SPACE && chatInputBuffer.length() > 0)
            {
                chatInputBuffer.deleteCharAt(chatInputBuffer.length() - 1);
            }
            return; // all other keys consumed while chat is open
        }

        // Movement
        if (key == KeyEvent.VK_A) physics.setMoveLeft(true);
        if (key == KeyEvent.VK_D) physics.setMoveRight(true);
        if (key == KeyEvent.VK_W) world.cameraPitchDeg = Math.max(-60f, world.cameraPitchDeg - 2f);
        if (key == KeyEvent.VK_S) world.cameraPitchDeg = Math.min(60f, world.cameraPitchDeg + 2f);

        if (key == KeyEvent.VK_SPACE)
        {
            physics.requestJump();
            jumpCount++;
        }

        // ESC — refresh/timing overlay
        if (key == KeyEvent.VK_ESCAPE)
        {
            world.overlayVisible = !world.overlayVisible;
            world.timingMode = world.overlayVisible
                ? TimingBridge.MODE_OVERLAY
                : (world.panelsVisible ? TimingBridge.MODE_PANELS
                                       : TimingBridge.MODE_GAMEPLAY);
            timing.setMode(world.timingMode);
        }

        // E — side panels
        if (key == KeyEvent.VK_E)
        {
            world.panelsVisible    = !world.panelsVisible;
            world.panelSlideTarget = world.panelsVisible ? 1f : 0f;
            world.timingMode = world.panelsVisible
                ? TimingBridge.MODE_PANELS
                : (world.overlayVisible ? TimingBridge.MODE_OVERLAY
                                        : TimingBridge.MODE_GAMEPLAY);
            timing.setMode(world.timingMode);
        }

        // C — chat conduit toggle
        if (key == KeyEvent.VK_C)
        {
            world.avatarChatOpen = !world.avatarChatOpen;
            chatInputBuffer.setLength(0);
            if (!world.avatarChatOpen) world.avatarSpeechText = "";
        }
    }

    @Override
    public void keyTyped(KeyEvent e)
    {
        // Capture printable characters when C key chat is open
        if (world.avatarChatOpen)
        {
            char c = e.getKeyChar();
            if (c >= 32 && c < 127 && chatInputBuffer.length() < MAX_CHAT_INPUT)
            {
                chatInputBuffer.append(c);
            }
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

    // ── MOUSE HANDLERS ────────────────────────────────────────────────────────

    @Override
    public void mouseMoved(MouseEvent e)
    {
        if (world.overlayVisible)
            refreshOverlay.onMouseMove(e.getX(), e.getY(), getWidth());
        if (world.panelsVisible)
            sidePanels.onMouseMove(e.getX(), e.getY(), getWidth(), getHeight(),
                                   world.panelSlideProgress);
    }

    @Override
    public void mouseClicked(MouseEvent e)
    {
        if (world.overlayVisible)
            refreshOverlay.onMouseClick(e.getX(), e.getY(), getWidth(), isStandalone);
        if (world.panelsVisible)
            sidePanels.onMouseClick(e.getX(), e.getY(), getWidth(), getHeight(),
                                    world.panelSlideProgress, isStandalone);
    }

    @Override public void mouseDragged(MouseEvent e)  {}
    @Override public void mousePressed(MouseEvent e)  {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e)  {}
    @Override public void mouseExited(MouseEvent e)   {}

    // ── ACCESSORS ─────────────────────────────────────────────────────────────

    public GameProtocol getProtocol() { return protocol; }

    public void setFullscreenExitCallback(Runnable cb)
    {
        refreshOverlay.setRateClickCallback(cb);
    }

    // ── STANDALONE ENTRY POINT ────────────────────────────────────────────────

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
