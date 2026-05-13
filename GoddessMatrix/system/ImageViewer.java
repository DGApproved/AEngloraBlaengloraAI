package system;

/*
 * ImageViewer.java
 *
 * Manifest / visual chamber for Goddess Matrix.
 *
 * Responsibilities:
 * - visual manifest panel
 * - image gallery
 * - local HTML view trigger
 * - MJPEG virtual display bridge
 * - cinematic passthrough mode
 * - mouse/key routing back to active session
 * - Mode A waveform HUD renderer
 * - Mode B avatar renderer (stick figure + sine activity box)
 * - image.xtx directive parsing (AI-evolvable visual layer)
 * - session_profile.txt loading for hardware telemetry display
 */

import assets.MatrixConfig;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ImageViewer 
{
    private final MatrixState state;

    private JPanel    imageManifest;
    private Rectangle originalManifestBounds;
    private Container originalParent;

    private Thread videoStreamThread;
    private Timer  refreshTimer;

    // System profile lines for HUD — loaded from dgapi/system/session_profile.txt
    private String profileLine1 = "";
    private String profileLine2 = "";
    private String profileLine3 = "";

    // ── MODE C CALCULATOR CLICK AREAS ─────────────────────────────────────────
    // Populated each time renderModeCGraphingFrame() runs.
    // Checked by mouse click handler for function button insertion.
    private static class CalcButton {
        final java.awt.Rectangle bounds;
        final String insert;
        CalcButton(int x, int y, int w, int h, String ins)
        { bounds = new java.awt.Rectangle(x, y, w, h); insert = ins; }
    }
    private final java.util.List<CalcButton> calcButtons = new java.util.ArrayList<>();
    private java.awt.Rectangle graphExpandBounds  = null;
    private java.awt.Rectangle graphRestoreBounds = null;
    private boolean graphExpanded = false;

    private float currentScale = 1.0f;

    public void applyScale(float scale) {
        this.currentScale = scale;
        // Trigger a fake resize event to force the canvas to recalculate its new anchor points
        if (imageManifest != null && imageManifest.getParent() != null) {
            for (java.awt.event.ComponentListener cl : imageManifest.getParent().getComponentListeners()) {
                cl.componentResized(null);
            }
        }
    }

    public ImageViewer(MatrixState state) 
    {
        this.state = state;
        state.imageViewer = this;
    }

    public void initialize() 
    {
        imageManifest = new JPanel(new BorderLayout()) 
        {
            @Override
            protected void paintComponent(Graphics g) 
            {
                super.paintComponent(g);
                paintManifest(g);
            }
        };

        imageManifest.setBackground(MatrixConfig.MANIFEST_BG);
        imageManifest.setBounds(740, 10, 320, 240);
        imageManifest.setBorder(new LineBorder(new Color(157, 80, 187, 40), 1));

        installMouseLogic();
        loadImageXTX();

        state.imageManifest = imageManifest;
    }

    public void attachToPanel(JPanel panel) 
    {
        if (imageManifest == null) initialize();
        panel.add(imageManifest);

        // ── DYNAMIC UI RESIZING HOOK ──────────────────────────────────────────
        // Actively listens to the main window and recalculates UI boundaries
        panel.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int pW = panel.getWidth();
                int pH = panel.getHeight();

                //Scale the base manifest dimensions
                int manifestW = (int)(320 * currentScale);
                int manifestH = (int)(240 * currentScale);

                if (state.manifestState == 1) { 
                    // Area Mode: Snaps to fill the entire window
                    imageManifest.setBounds(0, 0, pW, pH);
                } 
                else if (graphExpanded) {
                    // Expanded Graph Mode: Maintains the 35% / 65% split dynamically
                    imageManifest.setBounds(0, (int)(pH * 0.35), pW, (int)(pH * 0.65));
                } 
                else if (state.manifestState == 0) {
                    // Standard Manifest: Anchors securely to the top-right corner
                    int anchorX = pW - manifestW - 20; // 320px width + 20px padding
                    if (anchorX < 0) anchorX = 0; // Prevents clipping on tiny windows
                    
                    imageManifest.setBounds(anchorX, 10, manifestW, manifestH);
                    originalManifestBounds = imageManifest.getBounds();
                }
                
                // Force the renderer to redraw the traces and grids at the new scale
                imageManifest.revalidate();
                imageManifest.repaint();
            }
        });
    }

    // ── MANIFEST PAINTING ─────────────────────────────────────────────────────

    private void paintManifest(Graphics g) 
    {
        if (state.isHtmlStreamActive && state.manifestVisible) {
            g.setColor(MatrixConfig.GODDESS_GOLD);
            g.setFont(new Font("Monospaced", Font.BOLD, 12));
            drawCentered(g, "EXTERNAL_HTML_STREAM_ACTIVE", 0);
            g.setFont(new Font("Monospaced", Font.PLAIN, 10));
            drawCentered(g, "CONTROLLER_MODE: READY", 20);
            return;
        }

        if (state.activeBuffer != null && state.manifestVisible) {
            Graphics2D g2 = (Graphics2D) g;
            Object hint = state.isVideoStreamActive
                    ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                    : RenderingHints.VALUE_INTERPOLATION_BILINEAR;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
            g2.drawImage(state.activeBuffer, 0, 0,
                    imageManifest.getWidth(), imageManifest.getHeight(), null);
            if (state.isVideoStreamActive) {
                g2.setColor(new Color(250, 205, 104, 150));
                g2.setFont(new Font("Monospaced", Font.BOLD, 10));
                g2.drawString("● LIVE", 5, 12);
            }
            return;
        }

        if (!state.manifestVisible) {
            g.setColor(new Color(239, 68, 68, 40));
            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            drawCentered(g, "MANIFEST_HIDDEN", 0);
            return;
        }

        g.setColor(new Color(157, 80, 187, 40));
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        drawCentered(g, "IMAGE_MANIFEST_PENDING", 0);
    }

    private void drawCentered(Graphics g, String text, int yOffset) 
    {
        if (text == null || imageManifest == null) return;
        FontMetrics fm = g.getFontMetrics();
        int x = (imageManifest.getWidth()  - fm.stringWidth(text)) / 2;
        int y = ((imageManifest.getHeight() + fm.getAscent()) / 2) + yOffset;
        g.drawString(text, x, y);
    }

    // ── MOUSE LOGIC ───────────────────────────────────────────────────────────

    private void installMouseLogic() 
    {
        imageManifest.addMouseListener(new MouseAdapter() 
        {
            @Override
            public void mouseClicked(MouseEvent e) 
            {
                // Right-click: toggle Mode A ↔ Mode B
                if (e.getButton() == MouseEvent.BUTTON3) {
                    handleManifestRightClick();
                    return;
                }

                // Cinematic passthrough: all clicks route to session
                if (state.manifestState == 2) {
                    if (e.getClickCount() == 2) {
                        restoreManifest();
                    } else {
                        routeMouseClickToSession(
                                e.getX(), e.getY(), e.getButton(), e.getClickCount());
                    }
                    return;
                }

                if (state.isVideoStreamActive) {
                    routeMouseClickToSession(
                            e.getX(), e.getY(), e.getButton(), e.getClickCount());
                }

                // ── MODE C CALCULATOR CLICK DETECTION ────────────────────────
                if (state.isCalculatorEnabled && state.manifestViewMode == 2
                        && e.getButton() == java.awt.event.MouseEvent.BUTTON1)
                {
                    int mx = e.getX(), my = e.getY();

                    // RESTORE button when graph is expanded
                    if (graphExpanded)
                    {
                        if (graphRestoreBounds != null
                                && graphRestoreBounds.contains(mx, my))
                        {
                            collapseGraphMode();
                            return;
                        }
                        return; // no other clicks in graph mode
                    }

                    // GRAPH expand button
                    if (graphExpandBounds != null
                            && graphExpandBounds.contains(mx, my))
                    {
                        expandGraphMode();
                        return;
                    }

                    // Function buttons — insert into typing buffer
                    for (CalcButton cb : calcButtons)
                    {
                        if (cb.bounds.contains(mx, my))
                        {
                            if (state.chatHistory != null)
                                state.chatHistory.insertAtCaret(cb.insert);
                            setStatus("SYS_CALC: " + cb.insert);
                            if (imageManifest != null) imageManifest.repaint();
                            return;
                        }
                    }
                }

                boolean doubleTrigger = e.getClickCount() == 2 || state.isAltPending;

                if (state.manifestState == 0) {
                    originalManifestBounds = imageManifest.getBounds();
                    originalParent         = imageManifest.getParent();

                    if (doubleTrigger) {
                        state.manifestState = 2;
                        setStatus("SYS_MANIFEST: CINEMATIC_PASSTHROUGH_ACTIVE");
                        state.isAltPending = false;
                        if (state.keyboard != null) state.keyboard.updateModifierVisuals();

                        JRootPane root = SwingUtilities.getRootPane(imageManifest);
                        JPanel glass   = (JPanel) root.getGlassPane();
                        glass.setLayout(null);
                        glass.setVisible(true);
                        glass.add(imageManifest);
                        imageManifest.setBounds(0, 0, root.getWidth(), root.getHeight());

                    } else {
                        state.manifestState = 1;
                        setStatus("SYS_MANIFEST: AREA_MODE_ACTIVE");
                        if (state.matrixPanel != null) {
                            imageManifest.setBounds(0, 0,
                                    state.matrixPanel.getWidth(),
                                    state.matrixPanel.getHeight());
                            state.matrixPanel.setComponentZOrder(imageManifest, 0);
                        }
                    }

                } else if (state.manifestState == 1 && e.getClickCount() == 1) {
                    restoreManifest();
                }

                imageManifest.repaint();
            }
        }); //mouseadapter
    }

    public boolean isCinematicPassthrough() 
    {
        return state.manifestState == 2;
    }

    public void restoreManifest() 
    {
        if (state.manifestState == 2) {
            JRootPane root = SwingUtilities.getRootPane(imageManifest);
            if (root != null) root.getGlassPane().setVisible(false);
            if (originalParent != null) originalParent.add(imageManifest);
        }

        if (originalManifestBounds != null) {
            imageManifest.setBounds(originalManifestBounds);
        } else {
            imageManifest.setBounds(740, 10, 320, 240);
        }

        state.manifestState        = 0;
        state.isUserModeActive     = false;
        state.imageViewerMaximized = false;
        // Hide game canvas on restore — user cycles back via right-click
        if (state.isGameModeActive && state.sandboxInstance != null)
        {
            state.sandboxInstance.setVisible(false);
            state.manifestViewMode = 0;
        }
        setStatus("SYS_MANIFEST: RESTORED");
        imageManifest.repaint();
    }

    /**
     * Programmatically enter cinematic passthrough (fullscreen glass pane).
     * Called by Keyboard when user right-clicks the [Game] button while
     * game mode is active. Equivalent to double-clicking the manifest panel.
     * No-op if already in cinematic mode.
     */
    public void enterCinematicPassthrough()
    {
        if (imageManifest == null || state.manifestState == 2) return;

        originalManifestBounds = imageManifest.getBounds();
        originalParent         = imageManifest.getParent();

        state.manifestState = 2;
        setStatus("SYS_MANIFEST: CINEMATIC_PASSTHROUGH_ACTIVE");

        if (state.keyboard != null) state.keyboard.updateModifierVisuals();

        JRootPane root = SwingUtilities.getRootPane(imageManifest);
        if (root != null)
        {
            JPanel glass = (JPanel) root.getGlassPane();
            glass.setLayout(null);
            glass.setVisible(true);
            glass.add(imageManifest);
            imageManifest.setBounds(0, 0, root.getWidth(), root.getHeight());
        }

        // Game cinematic: show canvas, set imageViewerMaximized, capture mouse
        if (state.isGameModeActive)
        {
            state.imageViewerMaximized = true;
            state.manifestViewMode     = 3;
            java.awt.Component[] children = imageManifest.getComponents();
            for (java.awt.Component c : children)
            {
                if (c instanceof modular.game.IceSandbox)
                {
                    c.setVisible(true);
                    c.requestFocusInWindow();
                    try { c.getClass().getMethod("captureMouse").invoke(c); }
                    catch (Exception ignored) {}
                    break;
                }
            }
        }

        imageManifest.repaint();
    }

    private void handleManifestRightClick() 
    {
        // View modes:
        //   0 = MODE_A_WAVEFORM   — AI HUD (always available)
        //   1 = MODE_B_AVATAR     — AI stick figure (always available)
        //   2 = MODE_C_GRAPHING   — calculator (when calculator enabled)
        //   3 = MODE_D_GAME       — game canvas (when game active)
        //
        // The game module is optional — only included in the cycle when running.
        // Right-click [Game] button for fullscreen cinematic with mouse capture.
        int maxViews = 2;
        if (state.isCalculatorEnabled)  maxViews = 3;
        if (state.isGameModeActive)      maxViews = 4;

        state.manifestViewMode = (state.manifestViewMode + 1) % maxViews;
        state.lastRealImageTime = 0;

        String modeName;
        switch (state.manifestViewMode)
        {
            case 1:  modeName = "MODE_B_AVATAR";   break;
            case 2:  modeName = "MODE_C_GRAPHING";  break;
            case 3:  modeName = "MODE_D_GAME";      break;
            default: modeName = "MODE_A_WAVEFORM";  break;
        }

        // Show or hide the game canvas based on whether game view is selected.
        // The game keeps running either way — canvas visibility is presentation only.
        if (state.sandboxInstance != null)
        {
            boolean showGame = (state.manifestViewMode == 3);
            state.sandboxInstance.setVisible(showGame);
            if (showGame) state.sandboxInstance.requestFocusInWindow();
        }

        setStatus("SYS_MANIFEST: " + modeName);
        imageManifest.repaint();
    }

    // ── MJPEG VIRTUAL DISPLAY BRIDGE ─────────────────────────────────────────

    public void startVideoStream(int port) 
    {
        if (state.isVideoStreamActive) stopVideoStream();

        state.isVideoStreamActive = true;
        state.isHtmlStreamActive  = false;

        if (state.chatHistory != null) {
            state.chatHistory.appendSystem(
                    "INITIALIZING VIRTUAL DISPLAY BRIDGE ON PORT " + port);
        }
        setStatus("SYS_STREAM: LINKING...");

        videoStreamThread = new Thread(() -> {
            try {
                URL url = java.net.URI.create("http://localhost:" + port).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                InputStream is = connection.getInputStream();
                SwingUtilities.invokeLater(() -> setStatus("SYS_STREAM: ACTIVE"));

                ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
                int prev = -1;
                int cur;
                boolean inFrame = false;

                while (state.isVideoStreamActive && (cur = is.read()) != -1) {
                    if (!inFrame && prev == 0xFF && cur == 0xD8) {
                        inFrame = true;
                        frameBuffer.reset();
                        frameBuffer.write(0xFF);
                        frameBuffer.write(0xD8);
                    } else if (inFrame) {
                        frameBuffer.write(cur);
                        if (prev == 0xFF && cur == 0xD9) 
                        {
                            inFrame = false;
                            byte[] imgData = frameBuffer.toByteArray();
                            BufferedImage frame = null;
                            if (state.manifestViewMode == 1) {
                                frame = renderModeBFrame();
                            } else if (state.manifestViewMode == 2) {
                                frame = renderModeCGraphingFrame();
                            } else {
                                frame = renderAIFrame();
                            }

                            if (frame != null) state.activeBuffer = frame;
                        }
                    }
                    prev = cur;
                }
                is.close();

            } catch (Exception e) {
                if (state.isVideoStreamActive) {
                    SwingUtilities.invokeLater(() -> {
                        if (state.chatHistory != null) {
                            state.chatHistory.appendError(
                                    "VIRTUAL DISPLAY STREAM DROPPED: " + e.getMessage());
                        }
                        setStatus("SYS_STREAM: OFFLINE");
                    });
                }
            } finally {
                state.isVideoStreamActive = false;
            }
        });

        videoStreamThread.setDaemon(true);
        videoStreamThread.start();
    }

    public void stopVideoStream() 
    {
        state.isVideoStreamActive = false;

        if (videoStreamThread != null) {
            videoStreamThread.interrupt();
            videoStreamThread = null;
        }

        if (state.chatHistory != null) {
            state.chatHistory.appendSystem("VIRTUAL DISPLAY BRIDGE TERMINATED");
        }
        setStatus("SYS_STREAM: TERMINATED");
    }

    // ── SESSION ROUTING ───────────────────────────────────────────────────────

    public void routeMouseClickToSession(int x, int y, int button, int clicks) 
    {
        String inputStr = String.format(
                "[INPUT_MOUSE] X:%d Y:%d BTN:%d CLK:%d", x, y, button, clicks);

        if (state.isAIModeActive) {
            PrintWriter stdin = state.apiStdinMap.get(state.currentSession);
            if (stdin != null) stdin.println(inputStr);
        } else if (state.isSystemModeActive || state.isScriptModeActive) {
            SessionProcess sp = state.processMap.get(state.currentSession);
            if (sp != null && sp.stdin != null) sp.stdin.println(inputStr);
        }
    }

    public void routeKeyPressToSession(int keyCode, char keyChar) 
    {
        String inputStr = String.format(
                "[INPUT_KEY] CODE:%d CHAR:%c", keyCode, keyChar);

        if (state.isAIModeActive) {
            PrintWriter stdin = state.apiStdinMap.get(state.currentSession);
            if (stdin != null) stdin.println(inputStr);
        } else if (state.isSystemModeActive || state.isScriptModeActive) {
            SessionProcess sp = state.processMap.get(state.currentSession);
            if (sp != null && sp.stdin != null) sp.stdin.println(inputStr);
        }
    }

    // ── REFRESH TIMER ─────────────────────────────────────────────────────────

    public void updateRefreshRate(String selection) 
    {
        if (refreshTimer != null) refreshTimer.stop();

        int rate = 60;
        String sel = selection == null ? "Auto" : selection;

        if ("Auto".equals(sel)) {
            try {
                rate = GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice()
                        .getDisplayMode()
                        .getRefreshRate();
                if (rate < 30 || rate > 300) rate = 60;
            } catch (Exception ignored) { rate = 60; }
        } else {
            try {
                rate = Integer.parseInt(sel.replace("Hz", "").trim());
            } catch (Exception ignored) { rate = 60; }
        }

        state.displayRefreshRate = rate;
        int delay = Math.max(1, 1000 / Math.max(1, rate));

        refreshTimer = new Timer(delay, e -> refreshTick());
        refreshTimer.start();

        if (state.chatHistory != null) {
            state.chatHistory.appendSystem(
                    "REFRESH_RATE_SYNCED: " + sel + " (Actual: " + rate + "Hz)");
        }
    }

    private void refreshTick() 
    {
        if (imageManifest != null) imageManifest.repaint();

        long now = System.currentTimeMillis();

        // Logic tick — decoupled from display rate (Dumbo Fix)
        if (now - state.lastLogicTick >= MatrixConfig.LOGIC_TICK_MS) {
            pollAIInput();
            if (state.chatHistory != null) state.chatHistory.flushPendingLogsIfNeeded();
            state.lastLogicTick = now;
        }

        // AI & Calculator visual renderer
        boolean shouldRender = (state.isAIModeActive || state.isCalculatorEnabled) 
                               && !state.isHtmlStreamActive 
                               && !state.isVideoStreamActive;

        if (shouldRender) {

            boolean holdExpired = (now - state.lastRealImageTime) >= MatrixConfig.IMAGE_HOLD_MS;
            boolean noBuffer    = state.activeBuffer == null;

            if (holdExpired || noBuffer) {
                state.renderPhase += 0.04f;
                if (state.renderPhase > (float)(Math.PI * 2)) {
                    state.renderPhase -= (float)(Math.PI * 2);
                }

                BufferedImage frame = null;
                if (state.manifestViewMode == 1) {
                    frame = renderModeBFrame();
                } else if (state.manifestViewMode == 2) {
                    frame = renderModeCGraphingFrame();
                } else if (state.manifestViewMode == 3 && state.manifestRenderHook != null) {
                    // Mode 3: modular feature renders its own frame
                    int fw = Math.max(1, imageManifest.getWidth());
                    int fh = Math.max(1, imageManifest.getHeight());
                    try { frame = state.manifestRenderHook.render(fw, fh); }
                    catch (Exception e) {
                        state.manifestRenderHook = null; // clear broken hook
                        if (state.chatHistory != null)
                            state.chatHistory.logModularCrash("ManifestRender", e);
                    }
                } else {
                    frame = renderAIFrame();
                }

                if (frame != null) state.activeBuffer = frame;
            }
        }
    }

    public void stopRenderer() 
    {
        if (refreshTimer != null) {
            refreshTimer.stop();
            refreshTimer = null;
        }
    }

    // ── GAME MODE ─────────────────────────────────────────────────────────────
    // Embeds the IceSandbox panel directly inside the manifest panel.
    // The manifest panel becomes the game viewport.
    // Cinematic passthrough (double-click to fullscreen) still works —
    // it moves imageManifest to the glass pane, taking the game with it.
    // [Game] button press again calls stopGameMode() to restore normal rendering.

    public void startGameMode(modular.game.IceSandbox gameCanvas) 
    {
        if (imageManifest == null) return;

        // Stop AI renderer — game takes over the visual chamber
        stopAIRenderer();

        // Clear any active image buffer so the game surface is unobstructed
        state.activeBuffer = null;

        // Add game canvas as full-size child of the manifest panel
        imageManifest.setLayout(new java.awt.BorderLayout());
        imageManifest.add(gameCanvas, java.awt.BorderLayout.CENTER);

        // Start hidden — game view is mode 3 in the right-click cycle.
        // Right-clicking [Game] button enters cinematic (fullscreen + mouse capture).
        // Right-clicking ImageViewer cycles to MODE_D_GAME to see game in manifest.
        gameCanvas.setVisible(false);

        imageManifest.revalidate();
        imageManifest.repaint();

        // Flag so Keyboard dispatcher is aware game is running
        state.isGameModeActive = true;

        setStatus("SYS_GAME: ACTIVE — R-CLICK [Game] = fullscreen | R-CLICK manifest = cycle views");
    }

    public void stopGameMode() 
    {
        if (imageManifest == null) return;

        // Remove all children (the game canvas)
        imageManifest.removeAll();

        // Restore null layout that ImageViewer normally uses
        imageManifest.setLayout(new java.awt.BorderLayout());

        imageManifest.revalidate();
        imageManifest.repaint();

        state.isGameModeActive = false;
        state.activeBuffer     = null;
        // Reset to waveform view — game slot (mode 3) is gone
        if (state.manifestViewMode == 3) state.manifestViewMode = 0;

        setStatus("SYS_GAME: ENGINE_OFFLINE");
    }

    // ── AI INPUT POLL ─────────────────────────────────────────────────────────

    private void pollAIInput() 
    {
        File sessionDir = state.sessionDirectories.get(state.currentSession);
        if (sessionDir == null) return;

        File aiFile = new File(sessionDir,
                "dgapi" + File.separator + "system" + File.separator + "ai_input.txt");

        if (aiFile.exists() && aiFile.length() > 0) {
            try {
                byte[] bytes   = Files.readAllBytes(aiFile.toPath());
                String content = new String(bytes);
                if (!content.isEmpty()) {
                    Files.write(aiFile.toPath(), new byte[0]);
                    simulateTyping(content);
                }
            } catch (IOException ignored) {}
        }
    }

    private void simulateTyping(String content) 
    {
        new Thread(() -> {
            for (char c : content.toCharArray()) {
                int index = state.keyboard != null
                        ? state.keyboard.mapCharToIndex(c) : -1;

                SwingUtilities.invokeLater(() -> {
                    if (index != -1 && state.buttons != null && state.keyboard != null) {
                        JButton btn = state.buttons.get(index);
                        state.keyboard.handleMatrixEvent(index, btn, false);
                    } else if (state.chatHistory != null) {
                        state.chatHistory.insertAtCaret(String.valueOf(c));
                    }
                });

                try { Thread.sleep(30); } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    // ── SESSION ───────────────────────────────────────────────────────────────

    public void loadSession(int sessionId) 
    {
        if (state.isVideoStreamActive) stopVideoStream();

        if (!state.sessionImagePaths.isEmpty()) {
            state.galleryIndex = state.sessionImagePaths.size() - 1;
            renderStoredImage(state.sessionImagePaths.get(state.galleryIndex));
        } else {
            clearImageBuffer();
        }

        loadSystemProfile();
        if (imageManifest != null) imageManifest.repaint();
    }

    public void renderStoredImage(String imageRef) 
    {
        state.isHtmlStreamActive = false;

        if (imageRef == null || imageRef.isBlank()) { clearImageBuffer(); return; }

        try {
            File imgFile = new File(imageRef);

            if (!imgFile.isAbsolute()) {
                File sessionDir = state.sessionDirectories.get(state.currentSession);
                if (sessionDir != null) imgFile = new File(sessionDir, imageRef);
            }

            if (!imgFile.exists()) {
                File fallback = new File(state.aiHomeDirectory, imageRef);
                if (fallback.exists()) imgFile = fallback;
            }

            if (imgFile.exists()) {
                state.activeBuffer      = ImageIO.read(imgFile);
                state.lastRealImageTime = System.currentTimeMillis();
            } else {
                clearImageBuffer();
            }

        } catch (IOException e) {
            clearImageBuffer();
        }

        if (imageManifest != null) imageManifest.repaint();
    }

    public void manifestImage(BufferedImage img) 
    {
        if (img == null) return;

        state.isHtmlStreamActive = false;

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "fn" + state.currentSession + "-cd-" + timestamp + ".png";

        try {
            File sessionDir = state.sessionDirectories.get(state.currentSession);
            if (sessionDir == null) sessionDir = state.aiHomeDirectory;

            File outputFile = new File(sessionDir, fileName);
            ImageIO.write(img, "png", outputFile);

            state.sessionImagePaths.add(fileName);
            state.galleryIndex      = state.sessionImagePaths.size() - 1;
            state.activeBuffer      = img;
            state.lastRealImageTime = System.currentTimeMillis();

            if (state.chatHistory != null) state.chatHistory.logManifestImage(fileName);
            setStatus("SYS_MANIFEST: SAVED");

        } catch (IOException e) {
            setStatus("SYS_MANIFEST: FAILED");
        }

        if (imageManifest != null) imageManifest.repaint();
    }

    public void clearImageBuffer() 
    {
        state.activeBuffer = null;
        if (imageManifest != null) imageManifest.repaint();
    }

    public void cycleGallery(int delta) 
    {
        if (state.sessionImagePaths.isEmpty()) return;
        state.galleryIndex = (state.galleryIndex + delta + state.sessionImagePaths.size())
                % state.sessionImagePaths.size();
        renderStoredImage(state.sessionImagePaths.get(state.galleryIndex));
    }

    public void toggleManifestVisibility() 
    {
        state.manifestVisible = !state.manifestVisible;
        setStatus("SYS_MANIFEST: " + (state.manifestVisible ? "ENABLED" : "HIDDEN"));
        if (imageManifest != null) imageManifest.repaint();
    }

    public void loadLocalHTML(String filename) 
    {
        File targetFile = new File(filename);
        if (!targetFile.isAbsolute()) targetFile = new File(state.htmlDirectory, filename);

        if (!targetFile.exists()) {
            if (state.chatHistory != null) {
                state.chatHistory.appendRaw("SYSTEM> ERROR: [" + filename + "] NOT FOUND.\n");
                state.chatHistory.appendRaw("SYSTEM> SEARCH_PATH: "
                        + targetFile.getAbsolutePath() + "\n");
            }
            return;
        }

        try {
            Desktop.getDesktop().browse(targetFile.toURI());
            state.isHtmlStreamActive = true;
            if (state.isVideoStreamActive) stopVideoStream();
            setStatus("SYS_EXEC: BROWSER_ACTIVE");
        } catch (Exception e) {
            setStatus("SYS_EXEC: FAILED");
        }

        if (imageManifest != null) imageManifest.repaint();
    }

    // ── RENDERER LIFECYCLE ────────────────────────────────────────────────────

    public void startAIRenderer(int sessionId) 
    {
        state.aiSessionStartMs  = System.currentTimeMillis();
        state.aiQueryCount      = 0;          // reset — not increment
        state.currentAIStatus   = "LINK_ESTABLISHED: FN" + sessionId;
        state.isAIProcessing    = false;
        state.fnAIVisualMode    = true;
        state.lastRealImageTime = 0;          // let renderer start immediately
        loadSystemProfile();
    }

    public void stopAIRenderer() 
    {
        state.isAIProcessing = false;
        state.currentAIStatus = "OFFLINE";
        state.fnAIVisualMode  = false;
    }

    // ── SYSTEM PROFILE LOADER ─────────────────────────────────────────────────
    // Reads dgapi/system/session_profile.txt for hardware telemetry HUD lines.
    // Falls back gracefully — HUD still renders, just without profile data.

    public void loadSystemProfile() 
    {
        profileLine1 = "";
        profileLine2 = "";
        profileLine3 = "";

        try {
            File sessionDir = state.sessionDirectories.get(state.currentSession);
            if (sessionDir == null) return;

            File profileFile = new File(sessionDir,
                    "dgapi" + File.separator + "system"
                    + File.separator + "session_profile.txt");

            if (!profileFile.exists()) {
                profileLine1 = "PROFILE: NOT FOUND";
                profileLine2 = "Run GoddessAPI.sh to generate profile";
                return;
            }

            List<String> lines     = Files.readAllLines(profileFile.toPath());
            List<String> relevant  = new ArrayList<>();

            for (String l : lines) {
                String t = l.trim();
                if (t.length() <= 3) continue;
                if (t.startsWith("═") || t.startsWith("─")
                        || t.startsWith("#") || t.startsWith("━")) continue;

                if (t.startsWith("CPU") || t.startsWith("GPU") || t.startsWith("RAM")
                        || t.startsWith("Mode") || t.startsWith("Cores")
                        || t.startsWith("REASONING") || t.contains(":")) {
                    relevant.add(t);
                }
            }

            if (relevant.size() > 0) profileLine1 = relevant.get(0);
            if (relevant.size() > 1) profileLine2 = relevant.get(1);
            if (relevant.size() > 2) profileLine3 = relevant.get(2);

        } catch (Exception ignored) {
            profileLine1 = "PROFILE: READ ERROR";
        }
    }

    // ── MODE C: VIRTUAL GRAPHING ENGINE ───────────────────────────────────────
    // Small area mode: shows all scientific function buttons (clickable).
    // Expanded graph mode: shows live Cartesian plotting grid with curves.

    private BufferedImage renderModeCGraphingFrame()
    {
        int W = Math.max(1, imageManifest != null ? imageManifest.getWidth()  : 320);
        int H = Math.max(1, imageManifest != null ? imageManifest.getHeight() : 240);

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D    g   = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background
        g.setPaint(new GradientPaint(0, 0, new Color(5, 10, 15),
                                     W, H, new Color(12, 18, 28)));
        g.fillRect(0, 0, W, H);

        // Cyberpunk Cartesian grid
        g.setColor(new Color(157, 80, 187, 25));
        g.setStroke(new BasicStroke(0.5f));
        int gs = graphExpanded ? 40 : 20;
        for (int x = W/2 % gs; x < W; x += gs) g.drawLine(x, 0, x, H);
        for (int y = H/2 % gs; y < H; y += gs) g.drawLine(0, y, W, y);

        // Axes
        g.setColor(new Color(157, 80, 187, 80));
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(W/2, 0, W/2, H);
        g.drawLine(0, H/2, W, H/2);

        // ── HEADER ────────────────────────────────────────────────────────────
        g.setPaint(new GradientPaint(0, 0, new Color(40, 15, 65, 220),
                                     W, 0, new Color(15, 8, 30, 220)));
        g.fillRect(0, 0, W, 22);
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(MatrixConfig.GODDESS_GOLD);
        drawLeft(g, graphExpanded ? "GODDESS PLOTTER" : "EXTENDED CALC", 8, 15);
        g.setColor(MatrixConfig.GODDESS_PURPLE);
        drawRight(g, "SEED:29", W - 8, 15);

        // ── EXPRESSION DISPLAY ────────────────────────────────────────────────
        String curExpr = state.chatHistory != null
                ? state.chatHistory.getTypingText() : "";
        if (curExpr.length() > 30) curExpr = "…" + curExpr.substring(curExpr.length() - 30);
        g.setColor(new Color(30, 15, 50, 200));
        g.fillRoundRect(6, 24, W - 12, 16, 4, 4);
        g.setColor(new Color(80, 220, 120, 200));
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        drawLeft(g, curExpr.isEmpty() ? "enter expression..." : curExpr, 10, 35);

        if (graphExpanded)
        {
            // ── GRAPHING MODE ─────────────────────────────────────────────────
            int plotTop = 44;
            int plotBot = H - 52;
            boolean hasCustomExpr = !curExpr.isEmpty() && !curExpr.equals("enter expression...");

            if (hasCustomExpr) 
            {
                // Expression trace (cyan) - Active Graph Focus
                g.setColor(new Color(80, 210, 255, 180));
                for (int x = 1; x < W; x++)
                {
                    int y0 = assets.CalculatorEngine.Graphing.evaluateExpressionY(
                            curExpr, x - 1, W, plotBot - plotTop);
                    int y1 = assets.CalculatorEngine.Graphing.evaluateExpressionY(
                            curExpr, x, W, plotBot - plotTop);
                    if (y0 == Integer.MIN_VALUE || y1 == Integer.MIN_VALUE) continue;
                    y0 += plotTop; y1 += plotTop;
                    if (y0 >= plotTop && y1 >= plotTop && y0 < plotBot && y1 < plotBot)
                        g.drawLine(x - 1, y0, x, y1);
                }
            } 
            else 
            {
                // Sine trace (gold) - Default Idle
                g.setColor(MatrixConfig.GODDESS_GOLD);
                g.setStroke(new BasicStroke(2f));
                for (int x = 1; x < W; x++)
                {
                    int y0 = assets.CalculatorEngine.Graphing.calculateSineY(
                            x - 1, W, plotBot - plotTop, state.renderPhase) + plotTop;
                    int y1 = assets.CalculatorEngine.Graphing.calculateSineY(
                            x, W, plotBot - plotTop, state.renderPhase) + plotTop;
                    if (y0 >= plotTop && y1 >= plotTop && y0 < plotBot && y1 < plotBot)
                        g.drawLine(x - 1, y0, x, y1);
                }

                // Parabola trace (green) - Default Idle
                g.setColor(new Color(80, 220, 120, 160));
                for (int x = 1; x < W; x++)
                {
                    int y0 = assets.CalculatorEngine.Graphing.calculateParabolaY(
                            x - 1, W, plotBot - plotTop, state.renderPhase) + plotTop;
                    int y1 = assets.CalculatorEngine.Graphing.calculateParabolaY(
                            x, W, plotBot - plotTop, state.renderPhase) + plotTop;
                    if (y0 >= plotTop && y1 >= plotTop && y0 < plotBot && y1 < plotBot)
                        g.drawLine(x - 1, y0, x, y1);
                }
            }

            // ── DYNAMIC LEGEND ────────────────────────────────────────────────
            g.setFont(new Font("Monospaced", Font.PLAIN, 8));
            int ly = plotBot + 10;
            
            if (hasCustomExpr) 
            {
                g.setColor(new Color(80, 210, 255));
                String legStr = curExpr.length() > 25 ? curExpr.substring(0, 25) + "..." : curExpr;
                drawLeft(g, "── y = " + legStr, 8, ly);
            } 
            else 
            {
                g.setColor(MatrixConfig.GODDESS_GOLD);
                drawLeft(g, "── sin(x+θ)", 8, ly);
                g.setColor(new Color(80, 220, 120));
                drawLeft(g, "── x²", 90, ly);
            }

            // RESTORE button
            int rbW = 80, rbH = 18;
            int rbX = W - rbW - 6, rbY = H - rbH - 4;
            g.setColor(new Color(40, 15, 65, 200));
            g.fillRoundRect(rbX, rbY, rbW, rbH, 4, 4);
            g.setColor(new Color(157, 80, 187, 160));
            g.setStroke(new BasicStroke(0.8f));
            g.drawRoundRect(rbX, rbY, rbW, rbH, 4, 4);
            g.setFont(new Font("Monospaced", Font.BOLD, 8));
            g.setColor(new Color(200, 160, 255));
            drawCenteredXY(g, "▲ RESTORE", rbX + rbW/2, rbY + 12);
            graphRestoreBounds = new java.awt.Rectangle(rbX, rbY, rbW, rbH);
        }
        else
        {
            // ── FUNCTION BUTTON GRID ──────────────────────────────────────────
            // All Java Math functions available. Click inserts into typing buffer.
            calcButtons.clear();

            // Row 1: Power / exponential
            // Row 2: Trig
            // Row 3: Hyperbolic
            // Row 4: Log / misc
            // Each button: label shown, insert string appended to typing buffer
            Object[][] funcs = {
                // label,       insert string,       row, col
                {"√x",    "sqrt(",   0, 0},
                {"cbrt",  "cbrt(",   0, 1},
                {"xⁿ",   "pow(",    0, 2},
                {"exp",   "exp(",    0, 3},
                {"x²",   "x^2",     0, 4},
                {"π",    "pi",      0, 5},
                {"e",    "e",       0, 6},

                {"sin",  "sin(",    1, 0},
                {"cos",  "cos(",    1, 1},
                {"tan",  "tan(",    1, 2},
                {"asin", "asin(",   1, 3},
                {"acos", "acos(",   1, 4},
                {"atan", "atan(",   1, 5},

                {"sinh", "sinh(",   2, 0},
                {"cosh", "cosh(",   2, 1},
                {"tanh", "tanh(",   2, 2},

                {"log",  "log(",    3, 0},
                {"ln",   "ln(",     3, 1},
                {"abs",  "abs(",    3, 2},
                {"ceil", "ceil(",   3, 3},
                {"⌊x⌋", "floor(",  3, 4},
                {"rnd",  "round(",  3, 5},
            };

            int startY = 46;
            int btnW   = 38, btnH = 20, padX = 6, padY = 5;
            int cols   = 7;

            // Calculate layout based on panel width
            int totalRowW = cols * (btnW + padX) - padX;
            int marginX   = Math.max(4, (W - totalRowW) / 2);

            for (Object[] f : funcs)
            {
                String label  = (String) f[0];
                String insert = (String) f[1];
                int    row    = (Integer) f[2];
                int    col    = (Integer) f[3];

                int bx = marginX + col * (btnW + padX);
                int by = startY  + row * (btnH + padY);

                // Background
                g.setColor(new Color(25, 10, 45, 210));
                g.fillRoundRect(bx, by, btnW, btnH, 4, 4);

                // Border — highlight if mouse hovers (approximated by gold tint)
                g.setColor(new Color(157, 80, 187, 90));
                g.setStroke(new BasicStroke(0.8f));
                g.drawRoundRect(bx, by, btnW, btnH, 4, 4);

                // Label
                g.setFont(new Font("Monospaced", Font.BOLD, 8));
                g.setColor(new Color(200, 185, 255));
                // center label in button
                FontMetrics fm = g.getFontMetrics();
                int lx = bx + (btnW - fm.stringWidth(label)) / 2;
                int ly = by + btnH/2 + fm.getAscent()/2 - 1;
                g.drawString(label, lx, ly);

                calcButtons.add(new CalcButton(bx, by, btnW, btnH, insert));
            }

            // GRAPH EXPAND button (bottom-left)
            int gbW = 80, gbH = 18;
            int gbX = 6, gbY = H - gbH - 22;
            g.setColor(new Color(10, 40, 20, 210));
            g.fillRoundRect(gbX, gbY, gbW, gbH, 4, 4);
            g.setColor(new Color(80, 220, 120, 120));
            g.setStroke(new BasicStroke(0.8f));
            g.drawRoundRect(gbX, gbY, gbW, gbH, 4, 4);
            g.setFont(new Font("Monospaced", Font.BOLD, 8));
            g.setColor(new Color(80, 220, 120));
            drawCenteredXY(g, "▼ GRAPH", gbX + gbW/2, gbY + 12);
            graphExpandBounds = new java.awt.Rectangle(gbX, gbY, gbW, gbH);
        }

        // ── STATUS BAR ────────────────────────────────────────────────────────
        int barTop = H - 18;
        g.setColor(new Color(20, 8, 35, 220));
        g.fillRect(0, barTop, W, 18);
        g.setColor(new Color(157, 80, 187, 40));
        g.setStroke(new BasicStroke(0.5f));
        g.drawLine(0, barTop, W, barTop);

        g.setFont(new Font("Monospaced", Font.BOLD, 8));
        g.setColor(state.isCalculatorUnlocked
                ? new Color(80, 220, 120) : new Color(239, 68, 68));
        drawLeft(g, state.isCalculatorUnlocked ? "SEC:OPEN" : "SEC:LOCK", 6, barTop + 12);

        g.setColor(MatrixConfig.GODDESS_GOLD);
        drawRight(g, graphExpanded ? "GRAPH ACTIVE" : "CALC ACTIVE", W - 6, barTop + 12);

        g.dispose();
        return img;
    }

    /** Expands the manifest panel to cover the bottom half of the matrix panel. */
    public void expandGraphMode()
    {
        if (graphExpanded || imageManifest == null) return;
        java.awt.Container parent = imageManifest.getParent();
        if (parent == null) return;

        graphExpanded = true;
        // Save current bounds for restore
        originalManifestBounds = imageManifest.getBounds();

        int pW = parent.getWidth();
        int pH = parent.getHeight();
        // Start the graph at 35% down the screen, giving it a massive 65% of the total height.
        imageManifest.setBounds(0, (int)(pH * 0.35), pW, (int)(pH * 0.65));
        parent.setComponentZOrder(imageManifest, 0);
        imageManifest.repaint();
        setStatus("SYS_CALC: GRAPH EXPANDED");
    }

    /** Restores manifest to its original size after graph expand. */
    public void collapseGraphMode()
    {
        if (!graphExpanded || imageManifest == null) return;
        graphExpanded = false;
        if (originalManifestBounds != null)
            imageManifest.setBounds(originalManifestBounds);
        else
            imageManifest.setBounds(740, 10, 320, 240);
        imageManifest.repaint();
        setStatus("SYS_CALC: GRAPH RESTORED");
    }

    // ── MODE A WAVEFORM HUD ───────────────────────────────────────────────────
    // Full hardware telemetry HUD with waveform animation.
    // FN+AI shows ENHANCED HUD badge. Processing shows progress bar.

    private BufferedImage renderAIFrame() 
    {
        int W = Math.max(1, imageManifest != null ? imageManifest.getWidth()  : 320);
        int H = Math.max(1, imageManifest != null ? imageManifest.getHeight() : 240);

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D    g   = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── BACKGROUND ────────────────────────────────────────────────────────
        g.setPaint(new GradientPaint(0, 0, new Color(8, 6, 14), W, H, new Color(14, 10, 22)));
        g.fillRect(0, 0, W, H);

        // ── GRID ──────────────────────────────────────────────────────────────
        g.setColor(new Color(80, 40, 110, 25));
        g.setStroke(new BasicStroke(0.5f));
        for (int x = 0; x < W; x += 24) g.drawLine(x, 0, x, H);
        for (int y = 0; y < H; y += 24) g.drawLine(0, y, W, y);

        // ── HEADER BAR ────────────────────────────────────────────────────────
        g.setPaint(new GradientPaint(0, 0, new Color(60, 20, 90, 200),
                                     W, 0, new Color(20, 10, 40, 200)));
        g.fillRect(0, 0, W, 24);
        g.setColor(new Color(157, 80, 187, 180));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(0, 24, W, 24);

        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(MatrixConfig.GODDESS_GOLD);
        drawLeft(g, "GODDESS A.I.", 8, 16);

        String modeStr = state.fnAIVisualMode ? "ENHANCED HUD" : "STANDARD";
        g.setColor(MatrixConfig.GODDESS_PURPLE);
        drawRight(g, modeStr + " " + state.displayRefreshRate + "Hz", W - 8, 16);

        // ── UPTIME ────────────────────────────────────────────────────────────
        long elapsedSec = (System.currentTimeMillis() - state.aiSessionStartMs) / 1000;
        String uptime = String.format("%02d:%02d:%02d",
                elapsedSec / 3600, (elapsedSec % 3600) / 60, elapsedSec % 60);

        int yTop = 38;
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(new Color(148, 163, 184, 180));
        drawLeft(g, "SESSION", 8, yTop);
        g.setColor(new Color(250, 205, 104, 220));
        drawRight(g, uptime, W - 8, yTop);

        // ── STATUS ────────────────────────────────────────────────────────────
        yTop += 14;
        g.setColor(new Color(148, 163, 184, 150));
        drawLeft(g, "STATUS", 8, yTop);

        Color statusColor = state.isAIProcessing
                ? new Color(250, 205, 104)
                : new Color(80, 220, 120);
        g.setColor(statusColor);

        String displayStatus = state.currentAIStatus != null
                && state.currentAIStatus.length() > 22
                ? state.currentAIStatus.substring(0, 22) + "…"
                : state.currentAIStatus;
        drawRight(g, displayStatus, W - 8, yTop);

        // ── QUERY COUNTER ─────────────────────────────────────────────────────
        yTop += 14;
        g.setColor(new Color(148, 163, 184, 150));
        drawLeft(g, "QUERIES", 8, yTop);
        g.setColor(new Color(157, 80, 187, 220));
        drawRight(g, String.valueOf(state.aiQueryCount), W - 8, yTop);

        // ── DIVIDER ───────────────────────────────────────────────────────────
        yTop += 8;
        g.setColor(new Color(157, 80, 187, 60));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(8, yTop, W - 8, yTop);
        yTop += 10;

        // ── SYSTEM PROFILE LINES ──────────────────────────────────────────────
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(148, 163, 184, 160));
        if (!profileLine1.isEmpty()) { drawLeft(g, clip(profileLine1, W, g), 8, yTop); yTop += 12; }
        if (!profileLine2.isEmpty()) { drawLeft(g, clip(profileLine2, W, g), 8, yTop); yTop += 12; }
        if (!profileLine3.isEmpty()) { drawLeft(g, clip(profileLine3, W, g), 8, yTop); yTop += 12; }

        // ── DIVIDER ───────────────────────────────────────────────────────────
        yTop += 4;
        g.setColor(new Color(157, 80, 187, 60));
        g.drawLine(8, yTop, W - 8, yTop);
        yTop += 8;

        // ── WAVEFORM ──────────────────────────────────────────────────────────
        int waveH   = Math.min(60, H - yTop - 40);
        int waveMidY = yTop + waveH / 2;

        if (state.isAIProcessing) {
            drawWaveform(g, W, waveMidY, waveH, state.renderPhase,
                    new Color(250, 205, 104, 200), new Color(157, 80, 187, 120));
        } else {
            drawIdlePulse(g, W, waveMidY, waveH, state.renderPhase,
                    new Color(80, 220, 120, 160), new Color(40, 140, 80, 80));
        }

        yTop += waveH + 10;

        // ── PROCESSING BAR ────────────────────────────────────────────────────
        if (state.isAIProcessing) {
            int barW = W - 16;
            int barH = 6;
            g.setColor(new Color(40, 20, 60));
            g.fillRoundRect(8, yTop, barW, barH, 3, 3);

            float progress = (float)((Math.sin(state.renderPhase * 2) + 1.0) / 2.0);
            int fillW = (int)(barW * 0.3f + barW * 0.7f * progress);

            g.setPaint(new GradientPaint(8, yTop, MatrixConfig.GODDESS_PURPLE,
                    8 + fillW, yTop, MatrixConfig.GODDESS_GOLD));
            g.fillRoundRect(8, yTop, fillW, barH, 3, 3);

            yTop += barH + 6;
            g.setFont(new Font("Monospaced", Font.PLAIN, 8));
            g.setColor(new Color(250, 205, 104, 180));
            drawCenter(g, "GENERATING RESPONSE", W, yTop);
        }

        // ── BOTTOM BAR ────────────────────────────────────────────────────────
        int barTop = H - 28;
        g.setColor(new Color(30, 15, 50, 200));
        g.fillRect(0, barTop, W, H - barTop);
        g.setColor(new Color(157, 80, 187, 80));
        g.drawLine(0, barTop, W, barTop);

        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(148, 163, 184, 140));
        drawLeft(g, "SESSION: FN" + state.currentSession, 8, barTop + 10);

        float dotAlpha   = (float)((Math.sin(state.renderPhase * 3) + 1.0) / 2.0);
        int   dotAlphaI  = (int)(dotAlpha * 200) + 55;
        Color dotColor   = state.isAIProcessing
                ? new Color(250, 205, 104, dotAlphaI)
                : new Color(80,  220, 120,  dotAlphaI);
        g.setColor(dotColor);
        g.fillOval(W - 22, barTop + 5, 8, 8);

        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(148, 163, 184, 120));
        drawRight(g, state.isAIProcessing ? "PROC" : "IDLE", W - 28, barTop + 13);

        // ── TIMESTAMP ─────────────────────────────────────────────────────────
        g.setFont(new Font("Monospaced", Font.PLAIN, 7));
        g.setColor(new Color(80, 60, 100, 140));
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        drawLeft(g, ts, 8, H - 6);

        g.dispose();
        return img;
    }

    // ── MODE B AVATAR RENDERER ────────────────────────────────────────────────
    // Stick figure + sine activity box below.
    // Pose determined by state.avatarAction → ACTION_* lookup in image.xtx.
    // Appearance driven by image.xtx directives — AI may evolve these.

    private BufferedImage renderModeBFrame() 
    {
        loadImageXTX();

        int W = Math.max(1, imageManifest != null ? imageManifest.getWidth()  : 320);
        int H = Math.max(1, imageManifest != null ? imageManifest.getHeight() : 240);

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D    g   = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── BACKGROUND ────────────────────────────────────────────────────────
        Color bgColor = xtxColor("BACKGROUND_COLOR", new Color(10, 10, 12));
        g.setColor(bgColor);
        g.fillRect(0, 0, W, H);

        // ── GRID ──────────────────────────────────────────────────────────────
        g.setColor(new Color(80, 40, 110, 20));
        g.setStroke(new BasicStroke(0.5f));
        for (int x = 0; x < W; x += 24) g.drawLine(x, 0, x, H);
        for (int y = 0; y < H; y += 24) g.drawLine(0, y, W, y);

        Color bodyColor = xtxColor("BODY_COLOR",  MatrixConfig.GODDESS_PURPLE);
        Color textColor = xtxColor("TEXT_COLOR",  new Color(148, 163, 184));
        Color sineColor = xtxColor("SINE_COLOR",  MatrixConfig.GODDESS_GOLD);
        float scale     = xtxFloat("AVATAR_SCALE", 1.0f);
        float walkSpd   = xtxFloat("WALK_SPEED",   2.0f);

        // Advance walk cycle
        state.avatarWalkCycle += 0.06f * walkSpd;
        if (state.avatarWalkCycle > (float)(Math.PI * 2))
            state.avatarWalkCycle -= (float)(Math.PI * 2);

        // Resolve pose from image.xtx ACTION_* map
        String action   = state.avatarAction != null
                ? state.avatarAction.toUpperCase() : "IDLE";
        String actionKey = "ACTION_" + action;
        String pose     = xtxString(actionKey, "stand");

        // ── STATUS LABEL (top) ────────────────────────────────────────────────
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(new Color(157, 80, 187, 180));
        String displayAction = action.length() > 24 ? action.substring(0, 24) + "…" : action;
        drawCenter(g, displayAction, W, 14);

        // ── STICK FIGURE ──────────────────────────────────────────────────────
        int cx      = W / 2;
        int top     = 26;
        int bot     = (int)(H * 0.56f);
        int fH      = bot - top;
        int headR   = Math.max(8, (int)(fH * 0.14f * scale));
        int headCY  = top + headR;
        int neckY   = headCY + headR;
        int bodyLen = (int)(fH * 0.32f * scale);
        int hipY    = neckY + bodyLen;
        int legLen  = (int)(fH * 0.36f * scale);
        int footY   = hipY + legLen;
        int armLen  = (int)(fH * 0.28f * scale);
        int elbowY  = neckY + (int)(bodyLen * 0.4f);

        g.setStroke(new BasicStroke(Math.max(1.5f, 2.0f * scale),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(bodyColor);

        g.drawOval(cx - headR, top, headR * 2, headR * 2); // head
        g.drawLine(cx, neckY, cx, hipY);                   // body

        float legSwing = (float)Math.sin(state.avatarWalkCycle) * 18 * scale;
        float armSwing = (float)Math.sin(state.avatarWalkCycle + Math.PI) * 14 * scale;

        switch (pose) 
        {
            case "walk": 
            {
                g.drawLine(cx, hipY, cx - (int)legSwing, footY);
                g.drawLine(cx, hipY, cx + (int)legSwing, footY);
                g.drawLine(cx, elbowY, cx - armLen + (int)armSwing, elbowY + (int)(armLen * 0.5f));
                g.drawLine(cx, elbowY, cx + armLen - (int)armSwing, elbowY + (int)(armLen * 0.5f));
                break;
            }
            case "lean_forward": 
            {
                g.drawLine(cx - (int)(legLen * 0.25f), hipY, cx - (int)(legLen * 0.1f), footY);
                g.drawLine(cx + (int)(legLen * 0.25f), hipY, cx + (int)(legLen * 0.1f), footY);
                g.drawLine(cx, elbowY, cx - armLen, elbowY + 4);
                g.drawLine(cx, elbowY, cx + armLen, elbowY + 4);
                break;
            }
            case "speak": 
            {
                g.drawLine(cx, hipY, cx - (int)(legLen * 0.15f), footY);
                g.drawLine(cx, hipY, cx + (int)(legLen * 0.15f), footY);
                g.drawLine(cx, elbowY, cx - (int)(armLen * 0.6f), neckY - 4);
                g.drawLine(cx, elbowY, cx + armLen, elbowY + 8);
                // mouth arc
                g.drawArc(cx - headR / 3, headCY + (int)(headR * 0.4f) - 3,
                        headR / 2, headR / 3, 0, -180);
                break;
            }
            case "hand_on_chin": 
            {
                g.drawLine(cx, hipY, cx - (int)(legLen * 0.1f), footY);
                g.drawLine(cx, hipY, cx + (int)(legLen * 0.1f), footY);
                g.drawLine(cx, elbowY, cx + (int)(armLen * 0.7f), neckY + headR);
                g.drawLine(cx, elbowY, cx - armLen, elbowY + 12);
                break;
            }
            case "sit": 
            {
                int seatY = hipY + (int)(legLen * 0.4f);
                g.drawLine(cx - headR, hipY, cx - headR - legLen / 2, seatY);
                g.drawLine(cx + headR, hipY, cx + headR + legLen / 2, seatY);
                g.drawLine(cx - headR - legLen / 2, seatY, cx - headR - legLen / 2, footY);
                g.drawLine(cx + headR + legLen / 2, seatY, cx + headR + legLen / 2, footY);
                g.drawLine(cx, elbowY, cx - armLen, elbowY + 14);
                g.drawLine(cx, elbowY, cx + armLen, elbowY + 14);
                break;
            }
            default: // stand
            {
                float sway = (float)Math.sin(state.renderPhase) * 4;
                g.drawLine(cx, hipY, cx - (int)(legLen * 0.12f), footY);
                g.drawLine(cx, hipY, cx + (int)(legLen * 0.12f), footY);
                g.drawLine(cx, elbowY, cx - armLen + (int)sway, elbowY + 16);
                g.drawLine(cx, elbowY, cx + armLen - (int)sway, elbowY + 16);
                break;
            }
        }

        // ── EYE BLINK (wall-clock keyed, ~every 3.5s) ─────────────────────────
        boolean blink = ((int)(System.currentTimeMillis() / 3500) % 8 == 0);
        if (!blink) {
            g.setColor(MatrixConfig.GODDESS_GOLD);
            g.fillOval(cx - headR / 3 - 2, headCY - headR / 4, 3, 3);
            g.fillOval(cx + headR / 3 - 1, headCY - headR / 4, 3, 3);
        }

        // ── SINE ACTIVITY BOX ─────────────────────────────────────────────────
        int boxTop = (int)(H * 0.62f);
        int boxH   = H - boxTop - 22;
        int boxX   = 10;
        int boxW   = W - 20;
        int midY   = boxTop + boxH / 2;
        int ampH   = (boxH / 2) - 4;

        g.setColor(new Color(20, 10, 35, 180));
        g.fillRoundRect(boxX, boxTop, boxW, boxH, 6, 6);
        g.setColor(new Color(157, 80, 187, 60));
        g.setStroke(new BasicStroke(0.8f));
        g.drawRoundRect(boxX, boxTop, boxW, boxH, 6, 6);

        if (state.isAIProcessing) {
            drawWaveform(g, W, midY, ampH, state.renderPhase,
                    sineColor,
                    new Color(sineColor.getRed(), sineColor.getGreen(),
                              sineColor.getBlue(), 100));
        } else {
            drawIdlePulse(g, W, midY, ampH, state.renderPhase,
                    new Color(80, 220, 120, 160), new Color(40, 140, 80, 80));
        }

        // ── BOTTOM STATUS BAR ─────────────────────────────────────────────────
        int barTop = H - 20;
        g.setColor(new Color(30, 15, 50, 200));
        g.fillRect(0, barTop, W, H - barTop);
        g.setColor(new Color(157, 80, 187, 60));
        g.setStroke(new BasicStroke(0.8f));
        g.drawLine(0, barTop, W, barTop);

        g.setFont(new Font("Monospaced", Font.PLAIN, 7));
        g.setColor(textColor);
        drawLeft(g, "FN" + state.currentSession
                + " | " + (state.isAIProcessing ? "ACTIVE" : "IDLE"),
                6, barTop + 12);

        float dotA = (float)((Math.sin(state.renderPhase * 3) + 1.0) / 2.0);
        g.setColor(new Color(
                state.isAIProcessing ? 250 : 80,
                state.isAIProcessing ?  80 : 220,
                state.isAIProcessing ?  80 : 120,
                (int)(dotA * 180 + 55)));
        g.fillOval(W - 14, barTop + 5, 8, 8);

        long elSec = (System.currentTimeMillis() - state.aiSessionStartMs) / 1000;
        g.setFont(new Font("Monospaced", Font.PLAIN, 7));
        g.setColor(new Color(80, 60, 100, 140));
        drawRight(g,
                String.format("%02d:%02d:%02d",
                        elSec / 3600, (elSec % 3600) / 60, elSec % 60),
                W - 20, barTop + 12);

        g.dispose();
        return img;
    }

    // ── WAVEFORM HELPERS ──────────────────────────────────────────────────────

    private void drawWaveform(Graphics2D g, int W, int midY, int ampH,
                              float phase, Color c1, Color c2) 
    {
        int[] y1 = new int[W];
        int[] y2 = new int[W];
        for (int x = 0; x < W; x++) {
            double t = x / (double) W;
            y1[x] = midY + (int)(Math.sin(t * Math.PI * 6  + phase)       * ampH * 0.38);
            y2[x] = midY + (int)(Math.sin(t * Math.PI * 10 + phase * 1.5) * ampH * 0.20);
        }
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(c2);
        for (int x = 1; x < W; x++) g.drawLine(x - 1, y2[x - 1], x, y2[x]);
        g.setColor(c1);
        for (int x = 1; x < W; x++) g.drawLine(x - 1, y1[x - 1], x, y1[x]);
    }

    private void drawIdlePulse(Graphics2D g, int W, int midY, int ampH,
                               float phase, Color c1, Color c2) 
    {
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(c2);
        for (int x = 1; x < W; x++) {
            int y0 = midY + (int)(Math.sin(x       / (double)W * Math.PI * 3 + phase * 0.5) * ampH * 0.12);
            int y1 = midY + (int)(Math.sin((x-1.0) / W        * Math.PI * 3 + phase * 0.5) * ampH * 0.12);
            g.drawLine(x - 1, y1, x, y0);
        }
        g.setColor(c1);
        for (int x = 1; x < W; x++) {
            int y0 = midY + (int)(Math.sin(x       / (double)W * Math.PI * 2 + phase) * ampH * 0.25);
            int y1 = midY + (int)(Math.sin((x-1.0) / W        * Math.PI * 2 + phase) * ampH * 0.25);
            g.drawLine(x - 1, y1, x, y0);
        }
    }

    // ── TEXT HELPERS ──────────────────────────────────────────────────────────

    private void drawCenteredXY(Graphics2D g, String s, int cx, int y) 
    {
        if (s == null) return;
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }

    private void drawLeft(Graphics2D g, String s, int x, int y) 
    {
        if (s == null) return;
        g.drawString(s, x, y);
    }

    private void drawRight(Graphics2D g, String s, int rightX, int y) 
    {
        if (s == null) return;
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, rightX - fm.stringWidth(s), y);
    }

    private void drawCenter(Graphics2D g, String s, int W, int y) 
    {
        if (s == null) return;
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (W - fm.stringWidth(s)) / 2, y);
    }

    private String clip(String s, int W, Graphics2D g) 
    {
        if (s == null) return "";
        FontMetrics fm  = g.getFontMetrics();
        int maxW        = W - 20;
        while (s.length() > 4 && fm.stringWidth(s) > maxW) {
            s = s.substring(0, s.length() - 4) + "…";
        }
        return s;
    }

    // ── IMAGE.XTX DIRECTIVES ──────────────────────────────────────────────────
    // Hot-reload: re-parses on modification time change.
    // AI may freely add new directives. Unknown keys stored silently.
    // Built-in: BODY_COLOR, SINE_COLOR, BACKGROUND_COLOR, TEXT_COLOR,
    //           WALK_SPEED, AVATAR_SCALE, FEATURE=*, ACTION_<name>=<pose>

    public void loadImageXTX() 
    {
        try {
            File xtx = new File(state.aiHomeDirectory, "image.xtx");

            if (!xtx.exists()) {
                seedDefaultImageXTX(xtx);
                return;
            }

            long mod = xtx.lastModified();
            if (mod == state.imageXTXLastModified) return;

            state.imageXTX.clear();

            for (String line : Files.readAllLines(xtx.toPath())) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) continue;
                int eq = t.indexOf('=');
                state.imageXTX.put(
                        t.substring(0, eq).trim().toUpperCase(),
                        t.substring(eq + 1).trim());
            }

            state.imageXTXLastModified = mod;

        } catch (Exception ignored) {}
    }

    private void seedDefaultImageXTX(File xtx) 
    {
        String seed =
            "# image.xtx — Mode B visual directive file\n"
          + "# This file belongs to the AI. Modify and extend freely.\n"
          + "# Java reads known directives; unknown ones are stored for future builds.\n"
          + "\n"
          + "BODY_COLOR=#9D50BB\n"
          + "SINE_COLOR=#FACD68\n"
          + "BACKGROUND_COLOR=#0A0A0C\n"
          + "TEXT_COLOR=#94A3B8\n"
          + "WALK_SPEED=2\n"
          + "AVATAR_SCALE=1.0\n"
          + "FEATURE=sine_box\n"
          + "FEATURE=status_text\n"
          + "FEATURE=activity_dot\n"
          + "\n"
          + "# Action-to-pose mapping — AI may add new entries\n"
          + "ACTION_IDLE=stand\n"
          + "ACTION_PROCESSING=walk\n"
          + "ACTION_READING=lean_forward\n"
          + "ACTION_SHUTDOWN=sit\n"
          + "ACTION_CHAT=speak\n"
          + "ACTION_THINKING=hand_on_chin\n"
          + "ACTION_THINK=hand_on_chin\n";

        try {
            Files.writeString(xtx.toPath(), seed);
            state.imageXTXLastModified = xtx.lastModified();
        } catch (Exception ignored) {}

        for (String line : seed.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) continue;
            int eq = t.indexOf('=');
            state.imageXTX.put(
                    t.substring(0, eq).trim().toUpperCase(),
                    t.substring(eq + 1).trim());
        }
    }

    private Color  xtxColor(String key, Color fallback) 
    {
        String v = state.imageXTX.get(key.toUpperCase());
        if (v == null) return fallback;
        try { return Color.decode(v); } catch (Exception e) { return fallback; }
    }

    private float  xtxFloat(String key, float fallback) 
    {
        String v = state.imageXTX.get(key.toUpperCase());
        if (v == null) return fallback;
        try { return Float.parseFloat(v); } catch (Exception e) { return fallback; }
    }

    private String xtxString(String key, String fallback) 
    {
        String v = state.imageXTX.get(key.toUpperCase());
        return v != null ? v : fallback;
    }

    // ── STATUS HELPER ─────────────────────────────────────────────────────────

    private void setStatus(String text) 
    {
        if (state.statusLabel != null) state.statusLabel.setText(text);
    }

    public JPanel getPanel() 
    {
        return imageManifest;
    }
}
