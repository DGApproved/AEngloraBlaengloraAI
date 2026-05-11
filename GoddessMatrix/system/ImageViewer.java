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

        state.manifestState   = 0;
        state.isUserModeActive = false;
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

        // Re-focus game canvas so keyboard input still reaches it
        if (state.isGameModeActive)
        {
            java.awt.Component[] children = imageManifest.getComponents();
            for (java.awt.Component c : children)
            {
                if (c instanceof modular.game.IceSandbox)
                {
                    c.requestFocusInWindow();
                    break;
                }
            }
        }

        imageManifest.repaint();
    }

    private void handleManifestRightClick() 
    {
        state.manifestModeB      = !state.manifestModeB;
        state.lastRealImageTime  = 0; // let renderer start immediately

        setStatus(state.manifestModeB
                ? "SYS_MANIFEST: MODE_B_AVATAR"
                : "SYS_MANIFEST: MODE_A_WAVEFORM");

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
                        if (prev == 0xFF && cur == 0xD9) {
                            inFrame = false;
                            byte[] imgData = frameBuffer.toByteArray();
                            BufferedImage frame =
                                    ImageIO.read(new ByteArrayInputStream(imgData));
                            if (frame != null) {
                                state.activeBuffer       = frame;
                                state.lastRealImageTime  = System.currentTimeMillis();
                                if (imageManifest != null) imageManifest.repaint();
                            }
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

        // AI visual renderer
        if (state.isAIModeActive
                && !state.isHtmlStreamActive
                && !state.isVideoStreamActive) {

            boolean holdExpired = (now - state.lastRealImageTime) >= MatrixConfig.IMAGE_HOLD_MS;
            boolean noBuffer    = state.activeBuffer == null;

            if (holdExpired || noBuffer) {
                state.renderPhase += 0.04f;
                if (state.renderPhase > (float)(Math.PI * 2)) {
                    state.renderPhase -= (float)(Math.PI * 2);
                }

                BufferedImage frame = state.manifestModeB
                        ? renderModeBFrame()
                        : renderAIFrame();

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
        imageManifest.revalidate();
        imageManifest.repaint();

        // Give the game canvas keyboard focus
        gameCanvas.requestFocusInWindow();

        // Flag so Keyboard dispatcher passes game keys through
        state.isGameModeActive = true;

        setStatus("SYS_GAME: MANIFEST_ACTIVE — DBL-CLICK TO EXPAND");
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
