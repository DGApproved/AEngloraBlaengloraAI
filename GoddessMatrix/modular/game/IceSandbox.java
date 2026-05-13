package modular.game;

/*
 * IceSandbox.java
 *
 * Merged main game panel for the Goddess Matrix game engine.
 *
 * Contributors:
 *   Gemini (Google)        — original engine concept
 *   Derek Jason Gilhousen — world design, layer ownership, camera design intent,
 *                           fault-isolated modular game architecture
 *   Claude (Anthropic)    — prior IceSandbox implementation passes
 *   ChatGPT (OpenAI)      — four-version merge stabilization pass,
 *                           compile-safe camera/minigame/key integration
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class IceSandbox extends JPanel implements Runnable, KeyListener, MouseListener, MouseMotionListener, MouseWheelListener
{
    private final WorldState world;
    private final TimingBridge timing;
    private final PlayerPhysics physics;
    private final PlanetScaler scaler;
    private final SphereRenderer sphereRenderer;
    private final VoidRenderer voidRenderer;
    private final RefreshOverlay refreshOverlay;
    private final SidePanels sidePanels;
    private final GameProtocol protocol;
    private final GameHiberfile hiberfile;
    private final MiniGameEngine miniGameEngine;
    private final KeyState keys;

    private Thread renderThread;
    private float renderPhase = 0f;
    private final boolean isStandalone;
    private final File gameDir;
    private final File dgapiSystemDir;

    private float camYawTarget = 180f, camPitchTarget = 25f, camYawSmooth = 180f, camPitchSmooth = 25f;
    private float camDistTarget = 6.0f, camDistSmooth = 6.0f;
    private static final float CAM_DIST_MIN = 1.5f, CAM_DIST_MAX = 18.0f, CAM_SMOOTH = 0.18f, MOUSE_SENS = 0.20f, ARROW_SENS = 2.5f;
    private boolean rightMouseDown = false, playFocused = false;
    private java.awt.Robot mouseRobot;
    private final StringBuilder chatBuffer = new StringBuilder();
    private static final int MAX_CHAT = 200;
    private int jumpCount = 0;

    public IceSandbox(File gameDir, File dgapiSystemDir, boolean isMatrix)
    {
        this.gameDir = gameDir; this.dgapiSystemDir = dgapiSystemDir; this.isStandalone = !isMatrix;
        world = new WorldState(); world.launchedFromMatrix = isMatrix; world.cameraYaw = camYawSmooth; world.cameraPitch = camPitchSmooth;
        timing = new TimingBridge(gameDir); physics = new PlayerPhysics(); scaler = new PlanetScaler(gameDir); sphereRenderer = new SphereRenderer();
        voidRenderer = new VoidRenderer(new File(gameDir, "images")); refreshOverlay = new RefreshOverlay(); sidePanels = new SidePanels();
        hiberfile = new GameHiberfile(gameDir); miniGameEngine = new MiniGameEngine(); keys = new KeyState(gameDir);
        File dgapiDir = (dgapiSystemDir != null) ? dgapiSystemDir.getParentFile() : null;
        protocol = new GameProtocol(world, sidePanels, gameDir, dgapiDir, isMatrix);
        world.voidImagesDir = new File(gameDir, "images");

        if (isStandalone)
        {
            Runnable exit = () -> { stopEngine(); Window w = SwingUtilities.getWindowAncestor(this); if (w != null) w.dispose(); };
            refreshOverlay.setStandaloneExitCallback(exit); sidePanels.setStandaloneExitCallback(exit);
        }

        try { mouseRobot = new java.awt.Robot(); } catch (AWTException ignored) { mouseRobot = null; }
        setBackground(new Color(2, 4, 10)); setFocusable(true);
        addKeyListener(this); addMouseListener(this); addMouseMotionListener(this); addMouseWheelListener(this);
    }

    public IceSandbox() { this(resolveStandaloneGameDir(), null, false); }
    private static File resolveStandaloneGameDir() { File f = new File("modular/game"); f.mkdirs(); return f; }

    public void startEngine()
    {
        if (renderThread != null) return;
        hiberfile.load(world); scaler.applyToWorld(world);
        
        // 1. Ensure default boot is Earth if hiberfile is empty
        if (world.activePlanet < 1 || world.activePlanet > 12) world.activePlanet = WorldState.FN_EARTH;
        world.activeNode = world.activePlanet;

        // 2. Pre-build all 12 planets so they exist in the VoidRenderer skybox
        for (int i = 1; i <= 12; i++) {
            world.planets[i] = buildPlanet(i);
        }

        world.gameNodes = HtmlGameScanner.scan(resolveHtmlDir());
        protocol.writeSessionEnterEvent(world.activePlanet);
        renderThread = new Thread(this, "IceSandbox-RenderLoop"); 
        renderThread.setDaemon(true); 
        renderThread.start();
        SwingUtilities.invokeLater(this::captureMouse);
    }

    public void stopEngine()
    {
        releaseMouse(); protocol.writeJumpEvent(world.playerAltitudeFeet, jumpCount); protocol.writeSessionExitEvent(world.activePlanet);
        hiberfile.save(world); timing.shutdown(); renderThread = null;
    }

    private File resolveHtmlDir()
    {
        File modularDir = gameDir.getParentFile(); File rootDir = (modularDir != null) ? modularDir.getParentFile() : null;
        return (rootDir != null) ? new File(rootDir, "html") : new File("html");
    }

    public void setPlayerOrientation(float latitude, float longitude, float yaw, float pitch)
    {
        physics.setSpawnPosition(latitude, longitude); camYawTarget = yaw; camYawSmooth = yaw; camPitchTarget = pitch; camPitchSmooth = pitch;
        world.cameraYaw = yaw; world.cameraPitch = pitch;
    }

    private void captureMouse()
    {
        if (playFocused || !isShowing()) return; playFocused = true;
        setCursor(getToolkit().createCustomCursor(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB), new java.awt.Point(), "blank"));
        centerMouse();
    }

    private void releaseMouse() { playFocused = false; setCursor(Cursor.getDefaultCursor()); }

    // Only recapture from key events if standalone OR user has already clicked in.
    // In matrix mode, initial capture MUST come from clicking the ImageViewer panel.
    private void recaptureFromKey()
    {
        if (isStandalone || playFocused) captureMouse();
    }
    private void centerMouse()
    {
        if (mouseRobot == null || !isShowing()) return;
        try { Point screen = getLocationOnScreen(); mouseRobot.mouseMove(screen.x + getWidth() / 2, screen.y + getHeight() / 2); } catch (Exception ignored) {}
    }

    private int countFilesFast(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        return files == null ? 0 : files.length;
    }

    private WorldState.SystemPlanet buildPlanet(int fn)
    {
        WorldState.SystemPlanet p = new WorldState.SystemPlanet();
        p.fnNode = fn; p.celestialName = WorldState.celestialName(fn); 
        p.orbitRadius = WorldState.orbitalRadius(fn); p.gravity = WorldState.baseGravity(fn); p.axialTiltDeg = WorldState.axialTilt(fn);
        p.isSun = (fn == WorldState.FN_SUN); p.isAsteroidBelt = (fn == WorldState.FN_ASTEROID_BELT); p.hasRings = (fn == WorldState.FN_SATURN); p.isUserNamed = (fn == WorldState.FN_KUIPER);
        
        int fileCount = 1;

        if (dgapiSystemDir != null)
        {
            File root = dgapiSystemDir.getParentFile().getParentFile().getParentFile().getParentFile(); 
            File fnDir = new File(root, "fn/fn" + fn);
            if (fnDir.exists())
            {
                p.fnDirectory = fnDir; p.encyclopediaFile = new File(fnDir, "dgapi/virtual/encyclopedia.txt"); p.dictionaryFile = new File(fnDir, "dgapi/datas/dictionary.txt");
                p.almanacFile = new File(fnDir, "dgapi/datas/almanac.txt"); p.religionFile = new File(fnDir, "dgapi/virtual/religion.txt"); p.personaFile = new File(fnDir, "dgapi/virtual/persona.txt"); p.journalFile = new File(fnDir, "dgapi/datas/journal.txt");
                
                // Count data to scale this planet in the skybox
                fileCount = countFilesFast(new File(fnDir, "dgapi/datas")) + 
                            countFilesFast(new File(fnDir, "dgapi/virtual"));
            }
        }
        
        // The active planet uses the strict PlanetScaler. Skybox planets use their local file count.
        if (fn == world.activeNode) {
            p.bedrockCount = Math.max(fileCount, scaler.getBedrockCount());
            p.coreRadius = scaler.getPlanetCoreRadius();
        } else {
            p.bedrockCount = Math.max(1, fileCount);
            p.coreRadius = WorldState.BASE_DIAMETER_FT / 2f * (1.0f + (float)Math.log10(p.bedrockCount));
        }
        return p;
    }

    @Override public void run()
    {
        long lastTime = System.nanoTime(), logicAccum = 0; final long LOGIC_TICK_NS = 81_000_000L;
        while (renderThread != null)
        {
            long now = System.nanoTime(), delta = now - lastTime; lastTime = now; logicAccum += delta;
            if (logicAccum >= LOGIC_TICK_NS) { updateLogic(); logicAccum -= LOGIC_TICK_NS; }
            renderPhase += 0.03f; if (renderPhase > (float)(Math.PI * 2)) renderPhase -= (float)(Math.PI * 2);
            try { OptimizeRender.Temporal.advance(); long frameMs = Math.max(1L, (System.nanoTime() - now) / 1_000_000L); OptimizeRender.Governor.reportFPS(1000.0 / frameMs); } catch (Throwable ignored) {}
            repaint(); long tickNs = timing.getTickNanos(); long elapsed = System.nanoTime() - now; long sleepNs = tickNs - elapsed;
            if (sleepNs > 0) { try { Thread.sleep(sleepNs / 1_000_000, (int)(sleepNs % 1_000_000)); } catch (InterruptedException ignored) {} }
        }
    }

    private void updateLogic()
    {
        scaler.applyToWorld(world); world.clock.update();
        if (sidePanels.calendarModeTapped) { world.clock.calCycleMode(); sidePanels.calendarModeTapped = false; }
        updateCameraSmooth(); updateAstridLimbControls();
        
        WorldState.SystemPlanet planet = world.planets[world.activePlanet]; 
        float gravity = (planet != null) ? planet.gravity : 1.0f;
        float coreRadius = (planet != null) ? planet.coreRadius : world.coreRadius;
        
        // Use the new raycaster to find the true height of the data-terrain
        float terrainRadius = physics.calculateTerrainRadius(world, coreRadius);
        
        physics.setCameraYaw(camYawSmooth); 
        keys.applyToPhysics(physics); 
        if (keys.jump) physics.requestJump(); 
        
        // Pass the calculated terrain floor radius into the physics loop
        physics.tick(terrainRadius, gravity); 
        physics.writeToWorld(world, terrainRadius);
        
        updatePanels(); protocol.pollHardwareTelemetry(dgapiSystemDir); 
        updateOrbits(); expireNarration(); updateMiniGames();
    }

    private void updateCameraSmooth()
    {
        // Arrow keys route to head/arm control in updateAstridLimbControls().
        // Camera is mouse-only. Do not move camYawTarget from arrow keys here.
        camPitchTarget = Math.max(-85f, Math.min(85f, camPitchTarget)); float yawDiff = camYawTarget - camYawSmooth; if (yawDiff > 180f) yawDiff -= 360f; if (yawDiff < -180f) yawDiff += 360f;
        camYawSmooth += yawDiff * CAM_SMOOTH; camPitchSmooth += (camPitchTarget - camPitchSmooth) * CAM_SMOOTH; camDistSmooth += (camDistTarget - camDistSmooth) * CAM_SMOOTH;
        while (camYawSmooth < 0f) camYawSmooth += 360f; while (camYawSmooth >= 360f) camYawSmooth -= 360f;
        // View composition:
        //   camYawSmooth = absolute world camera/head yaw (mouse, free rotation)
        //   world.eyeYaw = fine eye offset (arrow keys, ±30°)
        //   world.cameraYaw = what the player actually sees
        //
        // Physics receives camYawSmooth for camera-relative WASD movement.
        // AstridHeadYaw = head angle relative to body facing — for avatar
        // rendering only, computed as the difference between camera and body.
        world.cameraYaw   = camYawSmooth + world.eyeYaw;
        world.cameraPitch = Math.max(-85f, Math.min(85f, camPitchSmooth + world.eyePitch));
        // Avatar head angle relative to body (wrap to ±180°)
        float relHead = camYawSmooth - world.playerAngleDeg;
        while (relHead >  180f) relHead -= 360f;
        while (relHead < -180f) relHead += 360f;
        world.AstridHeadYaw   = relHead;
        world.AstridHeadPitch = camPitchSmooth;
    }

    private void updateAstridLimbControls()
    {
        float s = 4.0f;
        if (keys.key_CTRL) { if (keys.camLeft) world.rightArmYaw -= s; if (keys.camRight) world.rightArmYaw += s; if (keys.camUp) world.rightArmPitch = Math.min(90f, world.rightArmPitch + s); if (keys.camDown) world.rightArmPitch = Math.max(-90f, world.rightArmPitch - s); }
        else if (keys.key_ALT) { if (keys.camLeft) world.leftArmYaw -= s; if (keys.camRight) world.leftArmYaw += s; if (keys.camUp) world.leftArmPitch = Math.min(90f, world.leftArmPitch + s); if (keys.camDown) world.leftArmPitch = Math.max(-90f, world.leftArmPitch - s); }
        else
        {
            // Arrow keys = eye movement within head constraints.
            // Eyes can shift ±30° relative to wherever the head is pointing.
            // This allows fine focus — reading a book, checking a shelf label —
            // without rotating the head.
            float eyeS = s * 0.6f; // eyes move slower and finer than head
            if (keys.camLeft)  world.eyeYaw -= eyeS;
            if (keys.camRight) world.eyeYaw += eyeS;
            world.eyeYaw   = Math.max(-30f, Math.min(30f, world.eyeYaw));
            if (keys.camUp)    world.eyePitch += eyeS;
            if (keys.camDown)  world.eyePitch -= eyeS;
            world.eyePitch = Math.max(-30f, Math.min(30f, world.eyePitch));
            // headYaw/headPitch still reflect where the head is pointing (mouse)
            world.headYaw   = world.AstridHeadYaw;
            world.headPitch = world.AstridHeadPitch;
        }
    }

    private void updatePanels() { float diff = world.panelSlideTarget - world.panelSlideProgress; if (Math.abs(diff) < WorldState.PANEL_SLIDE_SPEED) world.panelSlideProgress = world.panelSlideTarget; else world.panelSlideProgress += Math.signum(diff) * WorldState.PANEL_SLIDE_SPEED; }
    private void updateOrbits() { for (int i = 1; i <= 12; i++) { world.orbitAngles[i] += 0.01f; if (world.orbitAngles[i] >= 360f) world.orbitAngles[i] -= 360f; } }
    private void expireNarration() { if (!world.narrateText.isEmpty() && System.currentTimeMillis() > world.narrateExpireMs) world.narrateText = ""; }
    private void updateMiniGames() { HtmlGameScanner.updateUnlocks(world.gameNodes, world); world.activeMiniGame = HtmlGameScanner.findNearestActive(world.gameNodes, world.playerAngleDeg, world.playerLatDeg, 15f); if (world.activeMiniGame != null) { keys.applyToMiniGame(miniGameEngine); miniGameEngine.tick(world.activeMiniGame, world); } }

    @Override protected void paintComponent(Graphics g)
    {
        super.paintComponent(g); Graphics2D g2 = (Graphics2D)g; g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int W = getWidth(), H = getHeight(); voidRenderer.render(g2, world, W, H, renderPhase); sphereRenderer.render(g2, world, W, H);
        if (world.activeMiniGame != null) miniGameEngine.render(g2, world.activeMiniGame, world, W, H); sidePanels.render(g2, world, W, H, isStandalone);
        if (!world.narrateText.isEmpty() && System.currentTimeMillis() < world.narrateExpireMs) renderNarrate(g2, W, H); if (world.avatarChatOpen) renderChat(g2, W, H); if (world.overlayVisible) refreshOverlay.render(g2, W, H, timing, scaler, isStandalone); renderModeBar(g2, W, H);
        if (!playFocused && !world.overlayVisible && !world.panelsVisible && !world.avatarChatOpen) renderFocusHint(g2, W, H);
    }

    private void renderNarrate(Graphics2D g, int W, int H) { long rem = world.narrateExpireMs - System.currentTimeMillis(); int a = (int)(Math.min(1f, rem / 1000f) * 200); g.setFont(new Font("Monospaced", Font.ITALIC, 12)); FontMetrics fm = g.getFontMetrics(); String text = world.narrateText; int tx = (W - fm.stringWidth(text)) / 2; int ty = (int)(H * 0.28f); g.setColor(new Color(0, 0, 0, a / 2)); g.drawString(text, tx + 1, ty + 1); g.setColor(new Color(220, 200, 255, a)); g.drawString(text, tx, ty); }
    private void renderChat(Graphics2D g, int W, int H)
    {
        // Chat box grows upward from bottom to show history + input line
        int lineH    = 18;
        int padX     = 10;
        int maxLines = Math.min(world.chatMessages.size(), 8);
        int boxH     = lineH * (maxLines + 2) + 10; // history + input + hints
        int boxW     = (int)(W * 0.75f);
        int boxX     = (W - boxW) / 2;
        int boxY     = H - boxH - 40;

        // Background
        g.setColor(new Color(6, 8, 16, 180));
        g.fillRoundRect(boxX, boxY, boxW, boxH, 8, 8);
        g.setColor(new Color(157, 80, 187, 100));
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(boxX, boxY, boxW, boxH, 8, 8);

        // Chat history — most recent at bottom, oldest at top
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        java.util.List<WorldState.ChatMessage> msgs =
                new java.util.ArrayList<>(world.chatMessages);
        int startIdx = Math.max(0, msgs.size() - maxLines);
        int ty = boxY + lineH;
        for (int i = startIdx; i < msgs.size(); i++)
        {
            WorldState.ChatMessage m = msgs.get(i);
            boolean isAI = "AI".equals(m.speaker);
            g.setColor(isAI
                ? new Color(180, 140, 255, 200)   // AI response — purple
                : new Color(120, 210, 180, 200));  // Player — teal
            String line = (isAI ? "AI: " : "YOU: ") + m.text;
            // Truncate to fit box width
            FontMetrics fm = g.getFontMetrics();
            while (line.length() > 4 && fm.stringWidth(line) > boxW - padX * 2)
                line = line.substring(0, line.length() - 1);
            g.drawString(line, boxX + padX, ty);
            ty += lineH;
        }

        // Divider above input
        g.setColor(new Color(157, 80, 187, 60));
        g.drawLine(boxX + padX, ty, boxX + boxW - padX, ty);
        ty += 4;

        // Input line
        String prompt = "> " + chatBuffer + "_";
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(220, 200, 255, 220));
        g.drawString(prompt, boxX + padX, ty + 13);

        // Hints
        g.setFont(new Font("Monospaced", Font.PLAIN, 7));
        g.setColor(new Color(148, 163, 184, 100));
        g.drawString("ENTER=send  ESC=close", boxX + boxW - 115, ty + 13);
    }
    private int[] chatBoxBounds(int W, int H) { int boxW = Math.min(W - 40, 500), boxX = (W - boxW) / 2, boxY = (int)(H * 0.75f); return new int[]{boxX, boxY, boxW, 28}; }
    private void renderModeBar(Graphics2D g, int W, int H) { g.setFont(new Font("Monospaced", Font.PLAIN, 8)); g.setColor(new Color(80, 60, 110, 140)); String asmStr = timing.isASMPresent() ? "ASM" : "JAVA"; String mode; switch (world.timingMode) { case TimingBridge.MODE_OVERLAY: mode = "ESC"; break; case TimingBridge.MODE_PANELS: mode = "E"; break; default: mode = "PLAY"; break; } String label = asmStr + " | " + timing.getDisplayRate() + " | " + mode + " | " + WorldState.celestialName(world.activePlanet) + " | " + (world.isFirstPerson ? "FP" : "3P") + " | A:" + String.format("%.0f", world.playerAngleDeg) + " L:" + String.format("%.0f", world.playerLatDeg) + " +" + String.format("%.1f", world.playerAltitudeFeet) + "ft"; FontMetrics fm = g.getFontMetrics(); g.drawString(label, W - fm.stringWidth(label) - 8, H - 8); }
    private void renderFocusHint(Graphics2D g, int W, int H) { String s = "click to recapture"; g.setFont(new Font("Monospaced", Font.PLAIN, 10)); FontMetrics fm = g.getFontMetrics(); int x = (W - fm.stringWidth(s)) / 2, y = H - 30; g.setColor(new Color(0,0,0,120)); g.drawString(s, x+1, y+1); g.setColor(new Color(220,200,255,180)); g.drawString(s, x, y); }

    @Override public void keyPressed(KeyEvent e)
    {
        int key = e.getKeyCode(); keys.onKeyPressed(key);
        if (world.avatarChatOpen)
        {
            if (key == KeyEvent.VK_ESCAPE) { world.avatarChatOpen = false; world.chatOpen = false; chatBuffer.setLength(0); recaptureFromKey(); }
            else if (key == KeyEvent.VK_ENTER) { String text = chatBuffer.toString().trim(); if (!text.isEmpty()) protocol.sendToPython(text); chatBuffer.setLength(0); }
            else if (key == KeyEvent.VK_BACK_SPACE && chatBuffer.length() > 0) chatBuffer.deleteCharAt(chatBuffer.length() - 1);
            return;
        }
        if (key == KeyEvent.VK_ESCAPE) { world.overlayVisible = !world.overlayVisible; if (world.overlayVisible) releaseMouse(); else if (!world.panelsVisible) recaptureFromKey(); world.timingMode = world.overlayVisible ? TimingBridge.MODE_OVERLAY : (world.panelsVisible ? TimingBridge.MODE_PANELS : TimingBridge.MODE_GAMEPLAY); timing.setMode(world.timingMode); }
        if (key == KeyEvent.VK_E) { world.panelsVisible = !world.panelsVisible; world.panelSlideTarget = world.panelsVisible ? 1f : 0f; if (world.panelsVisible) releaseMouse(); else if (!world.overlayVisible) recaptureFromKey(); world.timingMode = world.panelsVisible ? TimingBridge.MODE_PANELS : (world.overlayVisible ? TimingBridge.MODE_OVERLAY : TimingBridge.MODE_GAMEPLAY); timing.setMode(world.timingMode); }
        if (key == KeyEvent.VK_C) { world.avatarChatOpen = !world.avatarChatOpen; world.chatOpen = world.avatarChatOpen; chatBuffer.setLength(0); if (world.avatarChatOpen) releaseMouse(); else recaptureFromKey(); }
        if (key == KeyEvent.VK_P) world.isFirstPerson = !world.isFirstPerson;
        if (key == KeyEvent.VK_SPACE) { physics.requestJump(); jumpCount++; }
    }
    @Override public void keyTyped(KeyEvent e) { if (world.avatarChatOpen) { char c = e.getKeyChar(); if (c >= 32 && c < 127 && chatBuffer.length() < MAX_CHAT) chatBuffer.append(c); } }
    @Override public void keyReleased(KeyEvent e) { keys.onKeyReleased(e.getKeyCode()); }
    @Override public void mouseMoved(MouseEvent e)
    {
        if (playFocused && mouseRobot != null && !world.avatarChatOpen && !world.overlayVisible && !world.panelsVisible && isShowing())
        { int cx = getWidth()/2, cy = getHeight()/2, dx = e.getX() - cx, dy = e.getY() - cy;
          if (dx != 0 || dy != 0) {
              // Mouse drives absolute world camera yaw — free rotation.
              // Body facing is implicitly the camera direction since WASD is camera-relative.
              camYawTarget  += dx * MOUSE_SENS;
              camPitchTarget = Math.max(-85f, Math.min(85f, camPitchTarget - dy * MOUSE_SENS));
              centerMouse();
          } }
        else { if (world.overlayVisible) refreshOverlay.onMouseMove(e.getX(), e.getY(), getWidth()); if (world.panelsVisible) sidePanels.onMouseMove(e.getX(), e.getY(), getWidth(), getHeight(), world.panelSlideProgress); }
    }
    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }
    @Override public void mouseClicked(MouseEvent e)
    {
        if (world.avatarChatOpen)
        { int[] b = chatBoxBounds(getWidth(), getHeight()); boolean inside = e.getX() >= b[0] && e.getX() <= b[0] + b[2] && e.getY() >= b[1] && e.getY() <= b[1] + b[3]; if (!inside) { world.avatarChatOpen = false; world.chatOpen = false; chatBuffer.setLength(0); captureMouse(); } return; }
        if (world.overlayVisible) refreshOverlay.onMouseClick(e.getX(), e.getY(), getWidth(), isStandalone); if (world.panelsVisible) sidePanels.onMouseClick(e.getX(), e.getY(), getWidth(), getHeight(), world.panelSlideProgress, isStandalone);
        if (!world.overlayVisible && !world.panelsVisible) { requestFocusInWindow(); captureMouse(); }
    }
    @Override public void mousePressed(MouseEvent e) { if (SwingUtilities.isRightMouseButton(e)) rightMouseDown = true; }
    @Override public void mouseReleased(MouseEvent e) { if (SwingUtilities.isRightMouseButton(e)) rightMouseDown = false; }
    @Override public void mouseWheelMoved(MouseWheelEvent e) { camDistTarget += e.getWheelRotation() * 0.5f; camDistTarget = Math.max(CAM_DIST_MIN, Math.min(CAM_DIST_MAX, camDistTarget)); camDistSmooth = camDistTarget; }
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    /** Called by Keyboard passthrough to decide if ENTER/BACKSPACE go to game. */
    public boolean isChatOpen() { return world != null && world.avatarChatOpen; }

    /** Returns current player position for debugging movement. Format: "A:%.0f L:%.0f Alt:%.1f" */
    public String getPlayerPosString()
    {
        if (world == null) return "";
        return String.format("A:%.0f L:%.0f Alt:%.1fft %s",
            world.playerAngleDeg, world.playerLatDeg, world.playerAltitudeFeet,
            world.isFirstPerson ? "FP" : "3P");
    }

    public GameProtocol getProtocol() { return protocol; }
    public void setFullscreenExitCallback(Runnable cb) { refreshOverlay.setRateClickCallback(cb); }
    public static void main(String[] args) { SwingUtilities.invokeLater(() -> { JFrame frame = new JFrame("ICE SANDBOX — Standalone"); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); frame.setSize(800, 600); frame.setLocationRelativeTo(null); frame.getContentPane().setBackground(new Color(2, 4, 10)); IceSandbox sandbox = new IceSandbox(); frame.add(sandbox); frame.setVisible(true); sandbox.requestFocusInWindow(); sandbox.startEngine(); }); }
}
