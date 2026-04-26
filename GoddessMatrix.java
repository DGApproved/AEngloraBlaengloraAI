/*
 * Goddess Input Matrix [AlphaBeta Build] v14.4
 *
 * What is it?
 * Multifunction Java-based terminal, AI bridge, script runner, chroot helper,
 * visual manifest renderer, and multi-session runtime controller.
 *
 * Features:
 * 1. Multi-session process isolation across FN1-FN12.
 * 2. Per-session API processes, stdin routing, telemetry HUD state, and logs.
 * 3. EXEC mode sandboxed to osDev via HOME/PATH hijack.
 * 4. SCRPT mode runs host-native for unrestricted user scripts.
 * 5. FN + NTR launches chroot helper terminal.
 * 6. AI-alone launches GoddessAPI.sh / GoddessAPI.bat; FN + AI launches GoddessAPI.py.
 * 7. API binary lookup supports deep AI localization through osDev/AI/bin.
 * 8. API protocol support: [STATUS], [IMAGE], [CHAT], [TYPE], [PROCESSING],
 *    [ACTION], [STREAM_START], [STREAM_STOP], and unknown-tag logging.
 * 9. Virtual Display Bridge: MJPEG stream support for A.I., EXEC, and SCRPT modes.
 * 10. Kinetic Mouse Routing: manifest clicks route back to the active session.
 * 11. AI Visual Renderer Mode A: display-rate-synced waveform HUD.
 * 12. AI Visual Renderer Mode B: avatar / stick-figure mode toggled by right-click.
 * 13. image.xtx directive system for AI-evolvable visual appearance.
 * 14. Per-FN folder architecture: fn/fn1..fn12 with dgapi/datas, intake,
 *     system, virtual, and processed folders.
 * 15. Per-session ai_input.txt polling from dgapi/system/.
 * 16. Per-session manifest image saving and gallery cycling.
 * 17. Apache HTML import and coonle index hub regeneration.
 * 18. Native [DIR] button opens the active script folder in the OS file browser.
 */

//imports
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.dnd.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Arrays;

public class GoddessMatrix extends JFrame {

     // Calibration Constants
        //ints
    private static final int KEY_U = 42;
    private static final int KEY_H = 36;
    private static final int KEY_GAP = 5;
        //colors
    private static final Color BG_DARK = new Color(10, 10, 12);
    private static final Color GODDESS_PURPLE = new Color(157, 80, 187);
    private static final Color HW_PURPLE = new Color(118, 60, 140);
    private static final Color GODDESS_GOLD = new Color(250, 205, 104);
    private static final Color KEY_BG = new Color(26, 26, 32);
    private static final Color TEXT_COLOR = new Color(148, 163, 184);
    private static final Color INPUT_BG = new Color(15, 15, 20);
    private static final Color HISTORY_BG = new Color(5, 5, 7);
    private static final Color MANIFEST_BG = new Color(12, 12, 15);
        //strings
    private static final String FN_BASE_DIR    = "fn";
    private static final String SCRPT_FOLDER   = "scrpt";
    private static final String MASTER_LOG      = "convodata.txt";
    private static final String STORAGE_FOLDER  = "convodata";
    private static final String AI_INPUT_FILE   = "ai_input.txt";
    private static final String HTML_FOLDER     = "HTML";
    private static final String OS_DEV_FOLDER   = "osDev";
    private static final String APACHE_LOG_PATH = "/var/log/apache2/access.log";
    private static final String API_OFFLINE     = "[API_OFFLINE]";
        //Jlabels
    private JLabel statusLabel;
    private JLabel modifierLabel;
    private JLabel aiStatusLabel;
        //JTextFields
    private JTextField typingBuffer;
    private JTextArea chatHistory;
        //JPanels
    private JPanel imageManifest;

    // Interface State
        //Boolean, String, File, etc. setup
    private boolean interfaceBridgeActive = false;
    private boolean isSystemModeActive = false;
    private boolean isAIModeActive = false;
    private boolean isScriptModeActive = false;
    private boolean isSudoEnabled = false;
    private String cachedSudoPassword = null; 
    private boolean isUserModeActive = false;

    // Environment State
    private boolean isApacheIntegrated = false;
    private String aiHomeDirectory;
    private File currentWorkingDirectory;

    // V14.3 Sandbox Architecture
    private File osDevDir;
    private File osDevBin;
    private File osDevHome;
    private File osDevAIDir;
    private File osDevAIBin;

    private boolean isShiftPending = false;
    private boolean isCtrlPending = false;
    private boolean isAltPending = false;
    private boolean isFnPending = false;
    private boolean isInsPending = false;
    private boolean isCmdPending = false;
    private boolean isAltDeleteCombo = false;
    private boolean isComboActive = false;
    private boolean isCtrlAltCombo = false;
    private boolean isCapsLockActive = true;
    private boolean isNumLockActive = true;
    private boolean isCalculatorEnabled = false;

    // Apache Logging
    private long lastApacheLogPos = 0;

    // Session Process Container
    private static class SessionProcess 
    {
        Process process;
        PrintWriter stdin;
        boolean isScript;
    }

    // V13/V14 Session Maps
    private final Map<Integer, SessionProcess> processMap = new HashMap<>();
    private final Map<Integer, Integer> sessionModes = new HashMap<>(); // 0 CHAT, 1 EXEC, 2 AI, 3 SCRIPT
    private final Map<Integer, File> sessionDirectories = new HashMap<>();

    // V14.2+ Per-session API State
    private final Map<Integer, Process> apiProcessMap = new HashMap<>();
    private final Map<Integer, PrintWriter> apiStdinMap = new HashMap<>();
    private final Map<Integer, String> apiStatusMap = new HashMap<>();

    // Cache & Retrieval
    private final List<String> pendingLogs = new ArrayList<>();
    private final List<String> sessionImagePaths = new ArrayList<>();
    private int galleryIndex = -1;
    private long lastFnClick = 0;

    // Session State
    private int currentSession = 1;

    // Rendering Buffer
    private BufferedImage activeBuffer;
    private Timer refreshTimer;
    private boolean manifestVisible = true;
    private boolean isHtmlStreamActive = false;
    
    // V14.4 Virtual Display Bridge State
    private volatile boolean isVideoStreamActive = false;
    private Thread videoStreamThread;

    // Manifest Expansion
    private int manifestState = 0;
    private Rectangle originalManifestBounds;
    private Container originalParent;
    private int numLockVisualState = 0;

    // ── AI VISUAL RENDERER STATE ─────────────────────────────
    // No separate timer — renderer runs inside the existing
    // display-rate refreshTimer so it always matches the monitor.
    private int              displayRefreshRate  = 60;   // set by initRefreshTimer
    private volatile boolean isAIProcessing      = false;
    private volatile long    aiSessionStartMs    = 0;
    private volatile int     aiQueryCount        = 0;
    private volatile float   renderPhase         = 0f;
    private volatile String  currentAIStatus     = "OFFLINE";
    private volatile String  systemProfileLine1  = "";
    private volatile String  systemProfileLine2  = "";
    private volatile String  systemProfileLine3  = "";
    private volatile boolean fnAIVisualMode      = false;
    // When a real image arrives (API [IMAGE] or gallery), hold it
    // for IMAGE_HOLD_MS before resuming ambient AI display.
    private volatile long    lastRealImageTime   = 0;
    private static final long IMAGE_HOLD_MS      = 8000;

    // ── MODE B AVATAR STATE ──────────────────────────────────────
    // Mode A = existing waveform HUD (default).
    // Mode B = stick figure avatar + sine activity display.
    // Toggled by right-click on the manifest panel.
    // The AI can evolve appearance via image.xtx in the working dir.
    private volatile boolean manifestModeB        = false;  // false = Mode A
    private volatile String  avatarAction         = "IDLE"; // current action label
    private volatile float   avatarWalkCycle      = 0f;    // walk animation phase
    // image.xtx directives loaded at startup and whenever the file changes.
    // The AI appends new directives; Java reads them and adjusts rendering.
    private java.util.Map<String, String> imageXTX = new java.util.HashMap<>();
    private long             imageXTXLastModified  = 0L;

    private final Map<Integer, JButton> buttons = new HashMap<>();
    private final Map<Integer, String[]> dualKeyMap = createDualKeyMap();

    public GoddessMatrix() 
    {
        setTitle("Goddess Input Matrix - V14.4 (Clean Compile Build)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());

        aiHomeDirectory = System.getProperty("user.dir");
        currentWorkingDirectory = new File(aiHomeDirectory);

        // Sandbox Layout
        osDevDir = new File(aiHomeDirectory, "osDev");
        osDevBin = new File(osDevDir, "bin");
        osDevHome = new File(osDevDir, "home");
        osDevAIDir = new File(osDevDir, "AI");
        osDevAIBin = new File(osDevDir, "AI" + File.separator + "bin");

        osDevBin.mkdirs();
        osDevHome.mkdirs();
        osDevAIBin.mkdirs();

        new File(osDevDir, "proc").mkdirs();
        new File(osDevDir, "sys").mkdirs();
        new File(osDevDir, "dev").mkdirs();
        new File(osDevDir, "dev/pts").mkdirs();

        buildMatrixArchitecture();
        checkApacheIntegration();
        initializeApacheSentryPointer();
            //Jpanels
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(BG_DARK);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 27, 10, 27));

        JPanel titleGroup = new JPanel(new GridLayout(2, 1));
        titleGroup.setBackground(BG_DARK);

        JLabel title = new JLabel("GODDESS INPUT MATRIX", SwingConstants.CENTER);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Inter", Font.BOLD, 20));

        JLabel subtitle = new JLabel("SPLIT-BRAIN SANDBOX • CHROOT MANAGER V14.4", SwingConstants.CENTER);
        subtitle.setForeground(TEXT_COLOR);
        subtitle.setFont(new Font("Monospaced", Font.PLAIN, 10));
        titleGroup.add(title);
        titleGroup.add(subtitle);
            //chathistory
        chatHistory = new JTextArea();
        chatHistory.setEditable(false);
        chatHistory.setBackground(HISTORY_BG);
        chatHistory.setForeground(TEXT_COLOR);
        chatHistory.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
        chatHistory.setLineWrap(true);
        chatHistory.setWrapStyleWord(true);
        chatHistory.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane historyPane = new JScrollPane(chatHistory);
        historyPane.setBorder(new LineBorder(new Color(157, 80, 187, 30), 1));
        historyPane.setPreferredSize(new Dimension(800, 450));
            //TypingBuffer
        typingBuffer = new JTextField();
        typingBuffer.setBackground(INPUT_BG);
        typingBuffer.setForeground(GODDESS_GOLD);
        typingBuffer.setCaretColor(GODDESS_PURPLE);
        typingBuffer.setFont(new Font("JetBrains Mono", Font.PLAIN, 16));
        typingBuffer.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(157, 80, 187, 50), 1),BorderFactory.createEmptyBorder(10, 15, 10, 15)));
        typingBuffer.setEditable(false);
        typingBuffer.getCaret().setVisible(true);
        typingBuffer.getCaret().setBlinkRate(500);
            //headerPanel
        headerPanel.add(titleGroup, BorderLayout.NORTH);
        headerPanel.add(historyPane, BorderLayout.CENTER);
        headerPanel.add(typingBuffer, BorderLayout.SOUTH);
            //JPanel
        JPanel matrixPanel = new JPanel(null);
        matrixPanel.setBackground(BG_DARK);
        matrixPanel.setBorder(BorderFactory.createEmptyBorder(0, 27, 20, 27));

        setupKeyboard(matrixPanel);

        imageManifest = new JPanel(new BorderLayout()) 
        {
            @Override
            protected void paintComponent(Graphics g) 
            {
                super.paintComponent(g);
                if (isHtmlStreamActive && manifestVisible) 
                {
                    g.setColor(GODDESS_GOLD);
                    g.setFont(new Font("Monospaced", Font.BOLD, 12));
                    drawCentered(g, "EXTERNAL_HTML_STREAM_ACTIVE");
                    g.setFont(new Font("Monospaced", Font.PLAIN, 10));
                    drawCentered(g, "CONTROLLER_MODE: READY", 20);
                } 
                else if (activeBuffer != null && manifestVisible) 
                {
                    Graphics2D g2 = (Graphics2D) g;
                    Object hint = isVideoStreamActive ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR 
                                                      : RenderingHints.VALUE_INTERPOLATION_BILINEAR;
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
                    g2.drawImage(activeBuffer, 0, 0, getWidth(), getHeight(), null);
                    
                    if (isVideoStreamActive) {
                        g2.setColor(new Color(250, 205, 104, 150));
                        g2.setFont(new Font("Monospaced", Font.BOLD, 10));
                        g2.drawString("● LIVE", 5, 12);
                    }
                } 
                else if (!manifestVisible) 
                {
                    g.setColor(new Color(239, 68, 68, 40));
                    g.setFont(new Font("Monospaced", Font.BOLD, 10));
                    drawCentered(g, "MANIFEST_HIDDEN");
                } 
                else 
                {
                    g.setColor(new Color(157, 80, 187, 40));
                    g.setFont(new Font("Monospaced", Font.BOLD, 10));
                    drawCentered(g, "IMAGE_MANIFEST_PENDING");
                }
            }

            private void drawCentered(Graphics g, String s) 
            {
                drawCentered(g, s, 0);
            }

            private void drawCentered(Graphics g, String s, int yOff) 
            {
                FontMetrics fm = g.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(s)) / 2;
                int y = ((getHeight() + fm.getAscent()) / 2) + yOff;
                g.drawString(s, x, y);
            }
        };

        imageManifest.setBackground(MANIFEST_BG);
        imageManifest.setBounds(1080, 10, 320, 240);
        imageManifest.setBorder(new LineBorder(new Color(157, 80, 187, 40), 1));
        imageManifest.addMouseListener(new MouseAdapter() 
        {
            @Override
            public void mouseClicked(MouseEvent e) 
            {// Right-click globally toggles Mode A / Mode B
                if (e.getButton() == MouseEvent.BUTTON3) 
                {
                    handleManifestRightClick();
                    return;
                }// ── CINEMATIC PASSTHROUGH MODE ──
                if (manifestState == 2) 
                {
                    if (e.getClickCount() == 2) 
                    {
                        restoreManifest(); // Double-click to escape
                    } 
                    else 
                    {
                        // Single clicks are piped directly into the AI/Process
                        routeMouseClickToSession(e.getX(), e.getY(), e.getButton(), e.getClickCount());
                    }
                    return; // Block all other UI logic while in passthrough
                }// ── STANDARD MANIFEST EXPANSION ──
                if (isVideoStreamActive) 
                {
                    routeMouseClickToSession(e.getX(), e.getY(), e.getButton(), e.getClickCount());
                }
                boolean doubleTrigger = (e.getClickCount() == 2 || isAltPending);
                if (manifestState == 0) 
                {
                    originalManifestBounds = imageManifest.getBounds();
                    originalParent = imageManifest.getParent();
                    if (doubleTrigger) 
                    {
                        manifestState = 2; // Enter Cinematic Passthrough
                        statusLabel.setText("SYS_MANIFEST: CINEMATIC_PASSTHROUGH_ACTIVE");
                        isAltPending = false;
                        updateModifierVisuals();
                        JRootPane root = getRootPane();
                        JPanel glass = (JPanel) root.getGlassPane();
                        glass.setLayout(null);
                        glass.setVisible(true);
                        glass.add(imageManifest);
                        imageManifest.setBounds(0, 0, root.getWidth(), root.getHeight());
                    } 
                    else 
                    {
                        manifestState = 1; // Area Expansion
                        statusLabel.setText("SYS_MANIFEST: AREA_MODE_ACTIVE");
                        imageManifest.setBounds(0, 0, matrixPanel.getWidth(), matrixPanel.getHeight());
                        matrixPanel.setComponentZOrder(imageManifest, 0);
                    }
                } 
                else if (manifestState == 1 && e.getClickCount() == 1) 
                {
                    restoreManifest();
                }
                repaint();
            }
        });

        matrixPanel.add(imageManifest);
            //JPanels
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(BG_DARK);
        footer.setBorder(BorderFactory.createEmptyBorder(10, 27, 10, 27));

        JPanel leftFooter = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftFooter.setBackground(BG_DARK);
            //statusLabels
        statusLabel = new JLabel("READY_FOR_INTERRUPT... | ");
        statusLabel.setForeground(GODDESS_PURPLE);
        statusLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
            //modifierLabels
        modifierLabel = new JLabel("BRIDGE: OFFLINE | NAVIGATION: READY");
        modifierLabel.setForeground(TEXT_COLOR);
        modifierLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
            //leftFooters
        leftFooter.add(statusLabel);
        leftFooter.add(modifierLabel);
            //aiStatusLabels
        aiStatusLabel = new JLabel(API_OFFLINE);
        aiStatusLabel.setForeground(GODDESS_GOLD);
        aiStatusLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
            //footers
        footer.add(leftFooter, BorderLayout.WEST);
        footer.add(aiStatusLabel, BorderLayout.EAST);
            //Boarders
        add(headerPanel, BorderLayout.NORTH);
        add(matrixPanel, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
            //Size, Position
        setSize(1450, 960);
        setLocationRelativeTo(null);
            //Setups
        setupHardwareBridge();
        setupDragAndDrop();
        initRefreshTimer();
        loadImageXTX();         // seed image.xtx if absent, parse directives
            //Session & Visuals
        loadSession(currentSession);
        updateModifierVisuals();
    }

    // ── V14.4 VIRTUAL DISPLAY BRIDGE (MJPEG SCANNER) ───────────────────────
    private void startVideoStream(int port) {
        if (isVideoStreamActive) stopVideoStream();
        isVideoStreamActive = true;
        isHtmlStreamActive = false;
        String urlStr = "http://localhost:" + port;
        
        chatHistory.append("SYSTEM> INITIALIZING VIRTUAL DISPLAY BRIDGE ON PORT " + port + "...\n");
        statusLabel.setText("SYS_STREAM: LINKING...");

        videoStreamThread = new Thread(() -> {
            try {
                URL url = java.net.URI.create(urlStr).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                InputStream is = connection.getInputStream();
                
                SwingUtilities.invokeLater(() -> statusLabel.setText("SYS_STREAM: ACTIVE"));

                ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
                int prev = -1;
                int cur;
                boolean inFrame = false;

                while (isVideoStreamActive && (cur = is.read()) != -1) {
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
                            BufferedImage frame = ImageIO.read(new ByteArrayInputStream(imgData));
                            if (frame != null) {
                                activeBuffer = frame;
                                lastRealImageTime = System.currentTimeMillis(); 
                            }
                        }
                    }
                    prev = cur;
                }
                is.close();
            } catch (Exception e) {
                if (isVideoStreamActive) {
                    SwingUtilities.invokeLater(() -> {
                        chatHistory.append("SYSTEM_ERR> VIRTUAL DISPLAY STREAM DROPPED: " + e.getMessage() + "\n");
                        statusLabel.setText("SYS_STREAM: OFFLINE");
                    });
                }
            } finally {
                isVideoStreamActive = false;
            }
        });
        videoStreamThread.start();
    }

    private void stopVideoStream() {
        isVideoStreamActive = false;
        if (videoStreamThread != null) {
            videoStreamThread.interrupt();
            videoStreamThread = null;
        }
        chatHistory.append("SYSTEM> VIRTUAL DISPLAY BRIDGE TERMINATED.\n");
        statusLabel.setText("SYS_STREAM: TERMINATED");
    }

    private void routeMouseClickToSession(int x, int y, int button, int clicks) {
        String inputStr = String.format("[INPUT_MOUSE] X:%d Y:%d BTN:%d CLK:%d", x, y, button, clicks);
        if (isAIModeActive) {
            PrintWriter stdin = apiStdinMap.get(currentSession);
            if (stdin != null) stdin.println(inputStr);
        } else if (isSystemModeActive || isScriptModeActive) {
            SessionProcess sp = processMap.get(currentSession);
            if (sp != null && sp.stdin != null) sp.stdin.println(inputStr);
        }
    }
    private void routeKeyPressToSession(int keyCode, char keyChar) {
        String inputStr = String.format("[INPUT_KEY] CODE:%d CHAR:%c", keyCode, keyChar);
        if (isAIModeActive) {
            PrintWriter stdin = apiStdinMap.get(currentSession);
            if (stdin != null) stdin.println(inputStr);
        } else if (isSystemModeActive || isScriptModeActive) {
            SessionProcess sp = processMap.get(currentSession);
            if (sp != null && sp.stdin != null) sp.stdin.println(inputStr);
        }
    }

    // ───────────────────────────────────────────────────────────────────────

    /**
     * Returns the preferred API binary path for the current activation.
     * If FN is held before toggling A.I., prefer /AI/osDev/bin.
     */
    /**
     * Resolves the API binary based on activation mode:
     *
     *   AI alone  -> GoddessAPI.sh / GoddessAPI.bat
     *                The Python AI system (hardware profiler, intake reader,
     *                journal, dictionary, almanac, hibernate state).
     *                Searched in: script folder, then working directory root.
     *
     *   FN + AI   -> GoddessAPI.py
     *                The direct API bridge script.
     *                Searched in: /AI/osDev/bin first, then script folder.
     *
     * Both paths fall back gracefully to null if the file is not found,
     * which triggers a fallback name in startGoddessAPI().
     */
    private File resolveAPIBinary(boolean useDeepAILocalization) 
    {
        String osName = System.getProperty("os.name").toLowerCase();
        String scriptDir = getOSScriptFolder();

        if (useDeepAILocalization) 
        {
            // FN+AI mode: GoddessAPI.py — direct API bridge.
            // Search /AI/osDev/bin first (deep localization), then script folder.
            File standard = new File(scriptDir, "GoddessAPI.py");
            if (standard.exists()) 
            {
                return standard;
            }
            File deep = new File(osDevAIBin, "GoddessAPI.py");
            if (deep.exists()) 
            {
                return deep;
            }
            return null;
        } 
        else 
        {
            // AI alone: GoddessAPI.sh / GoddessAPI.bat — Python AI system.
            // Searches script folder first, then working directory root.
            String scriptName = osName.contains("win") ? "GoddessAPI.bat" : "GoddessAPI.sh";
            File standard = new File(scriptDir, scriptName);
            if (standard.exists()) 
            {
                return standard;
            }
            File local = new File(aiHomeDirectory, scriptName);
            if (local.exists()) 
            {
                return local;
            }
            return null;
        }
    }

    /**
     * Mirrors executeShellCommand sandbox logic:
     * - working directory from session map
     * - HOME redirected into osDev/home
     * - PATH prefixed with osDev/bin
     * - AI alone    : launches GoddessAPI.sh (Python AI system) via bash
     * - FN + AI     : launches GoddessAPI.py (direct API bridge) via python3
     * - .py files   : always launched with python3 / python, never bash
     */
    private void startGoddessAPI(boolean useDeepAILocalization) 
    {
        final int sessionId = currentSession;
        Process existing = apiProcessMap.get(sessionId);
        if (existing != null && existing.isAlive()) 
        {
            String hud = apiStatusMap.getOrDefault(sessionId, "[API_LINK_ESTABLISHED: FN" + sessionId + "]");
            if (currentSession == sessionId) 
            {
                aiStatusLabel.setText(hud);
            }
            return;
        }

        apiStatusMap.put(sessionId, "API_BOOTING...");

        if (currentSession == sessionId) 
        {
            aiStatusLabel.setText("API_BOOTING...");
        }

        chatHistory.append("SYSTEM> INITIALIZING GODDESS_API HOOK FOR FN" + sessionId + "...\n");

        new Thread(() -> {
            try {
                String osName = System.getProperty("os.name").toLowerCase();
                File apiFile = resolveAPIBinary(useDeepAILocalization);

                // Build launch command based on file type.
                // .py  -> python3 / python  (GoddessAPI.py  via FN+AI)
                // .sh  -> bash              (GoddessAPI.sh   via AI alone)
                // .bat -> cmd /c            (Windows)
                // null -> fallback name     (should not occur if scripts are in place)
                List<String> cmd = new ArrayList<>();
                String fallbackDir = getOSScriptFolder() + File.separator;

                if (useDeepAILocalization) 
                {
                    // Python script — FN+AI path (GoddessAPI.py)
                    cmd.add(osName.contains("win") ? "python" : "python3");
                    cmd.add(apiFile != null ? apiFile.getAbsolutePath() : fallbackDir + "GoddessAPI.py");
                } 
                else 
                {
                    if (osName.contains("win")) 
                    {
                    cmd.add("cmd.exe");
                    cmd.add("/c");
                    cmd.add(apiFile != null ? apiFile.getAbsolutePath() : fallbackDir + "GoddessAPI.bat");
                    } 
                    else 
                    {
                        cmd.add("bash");
                        cmd.add(apiFile != null ? apiFile.getAbsolutePath() : fallbackDir + "GoddessAPI.sh");
                    }
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(sessionDirectories.getOrDefault(sessionId, new File(aiHomeDirectory)));

                // Mirror executeShellCommand sandbox env exactly.
                Map<String, String> env = pb.environment();
                env.put("HOME", osDevHome.getAbsolutePath());
                String currentPath = env.getOrDefault("PATH", "");
                env.put("PATH", osDevBin.getAbsolutePath() + File.pathSeparator + currentPath);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                PrintWriter stdin = new PrintWriter(new OutputStreamWriter(process.getOutputStream()), true);

                synchronized (apiProcessMap) 
                {
                    apiProcessMap.put(sessionId, process);
                    apiStdinMap.put(sessionId, stdin);
                }

                String linked = "[API_LINK_ESTABLISHED: FN" + sessionId + "]";
                apiStatusMap.put(sessionId, linked);

                // ── START VISUAL RENDERER ──────────────────────────
                SwingUtilities.invokeLater(() -> startAIRenderer(sessionId));
                // ──────────────────────────────────────────────────

                SwingUtilities.invokeLater(() -> 
                {
                    if (currentSession == sessionId) 
                    {
                        aiStatusLabel.setText(linked);
                    }
                }); //invokeLater

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) 
                {
                    String line;
                    while ((line = reader.readLine()) != null) 
                    {
                        parseAPIOutput(line, sessionId);
                    }
                    process.waitFor();
                }
            } 
            catch (Exception e) 
            {
                String err = "[API_ERROR]";
                apiStatusMap.put(sessionId, err);
                SwingUtilities.invokeLater(() -> {
                    if (currentSession == sessionId) 
                    {
                        aiStatusLabel.setText(err);
                        chatHistory.append("SYSTEM_ERR> API_HOOK_FAILED: " + e.getMessage() + "\n");
                    }
                }); //invokeLater

            } 
            finally 
            {
                synchronized (apiProcessMap) 
                {
                    apiProcessMap.remove(sessionId);
                    apiStdinMap.remove(sessionId);
                }

                apiStatusMap.put(sessionId, API_OFFLINE);
                SwingUtilities.invokeLater(() -> 
                {
                    if (currentSession == sessionId) 
                    {
                        aiStatusLabel.setText(API_OFFLINE);
                        chatHistory.append("SYSTEM> GODDESS_API DISCONNECTED.\n");
                        if (isVideoStreamActive) stopVideoStream();
                        if (isAIModeActive) 
                        {
                            isAIModeActive = false;
                            sessionModes.put(currentSession, 0);
                            updateModifierVisuals();
                        }
                    }
                }); //invokeLater
            }
        }).start(); //thread
    }

    private void stopGoddessAPI(int sessionId) 
    {
        Process process;
        PrintWriter stdin;
        synchronized (apiProcessMap) 
        {
            process = apiProcessMap.get(sessionId);
            stdin = apiStdinMap.get(sessionId);
        }
        if (process != null && process.isAlive()) 
        {
            if (stdin != null) 
            {
                stdin.println("exit");
            }
            final Process finalProcess = process;

            new Thread(() -> {
                try {

                    Thread.sleep(1000);
                    if (finalProcess.isAlive()) 
                    {
                        finalProcess.destroyForcibly();
                    }

                } 
                catch (InterruptedException ignored)
                {}
            }).start(); //Thread
        }

        apiStatusMap.put(sessionId, API_OFFLINE);
        if (currentSession == sessionId) 
        {
            aiStatusLabel.setText(API_OFFLINE);
        }

        if (isVideoStreamActive) stopVideoStream();
        // ── STOP VISUAL RENDERER ───────────────────────────────
        SwingUtilities.invokeLater(this::stopAIRenderer);
        // ──────────────────────────────────────────────────────
    }

    private void parseAPIOutput(String line, int sessionId) 
    {
        SwingUtilities.invokeLater(() -> {
            if (line.startsWith("[STATUS]")) 
            {
                String status = line.substring(8).trim();
                apiStatusMap.put(sessionId, status);
                currentAIStatus = status;
                isAIProcessing  = false;
                if (currentSession == sessionId) 
                {
                    aiStatusLabel.setText(status);
                }
            } 
            else if (line.startsWith("[PROCESSING]")) 
            {
                // Optional protocol tag — marks start of generation
                isAIProcessing = true;
                currentAIStatus = "GENERATING";
            } 
            else if (line.startsWith("[IMAGE]")) 
            {
                String imgPath = line.substring(7).trim();
                String persistentLog = "MANIFEST_IMAGE: [API_OVERRIDE] " + imgPath + "\n";
                writeToSessionLog(sessionId, false, persistentLog);
                if (currentSession == sessionId) 
                {
                    renderStoredImage(imgPath);
                    chatHistory.append("GODDESS_API> [IMAGE_MANIFEST_OVERRIDE: " + imgPath + "]\n");
                }
            } 
            else if (line.startsWith("[STREAM_START]")) 
            {
                try {
                    int port = Integer.parseInt(line.substring(14).trim());
                    if (currentSession == sessionId) startVideoStream(port);
                } catch (NumberFormatException e) {
                    chatHistory.append("SYSTEM_ERR> INVALID STREAM PORT.\n");
                }
            } 
            else if (line.startsWith("[STREAM_STOP]")) 
            {
                if (currentSession == sessionId) stopVideoStream();
            } 
            else if (line.startsWith("[TYPE]")) 
            {
                String typeContent = line.substring(6).trim();
                if (currentSession == sessionId) 
                {
                    chatHistory.append("GODDESS_API> KINETIC_OVERRIDE_ENGAGED\n");
                    simulateTyping(typeContent + "\n");
                } 
                else 
                {
                    SessionProcess sp = processMap.get(sessionId);
                    if (sp != null && sp.process != null && sp.process.isAlive() && sp.stdin != null) 
                    {
                        sp.stdin.println(typeContent);
                        writeToSessionLog(sessionId, sp.isScript, "> " + typeContent + "\n");
                    }
                }
            } 
            else if (line.startsWith("[CHAT]")) 
            {
                String chat = line.substring(6).trim();
                if (currentSession == sessionId) 
                {
                    chatHistory.append("GODDESS> " + chat + "\n");
                }
                writeToSessionLog(sessionId, false, "GODDESS> " + chat + "\n");
            } 
            else if (line.startsWith("[ACTION]")) 
            {
                // Mode B avatar action directive — AI explicitly sets its own pose.
                // Unknown actions are accepted and stored; Mode B renderer uses what
                // it knows and ignores what image.xtx hasn't defined yet.
                String action = line.substring(8).trim().toUpperCase();
                avatarAction = action;
            } 
            else if (line.startsWith("[")) 
            {
                // Unknown tag — vocabulary may have evolved beyond the baseline.
                // Log silently to session log rather than printing to chat,
                // so the conversation stream stays clean.
                writeToSessionLog(sessionId, false, "API_TAG_UNKNOWN> " + line + "\n");
            } 
            else 
            {
                if (currentSession == sessionId) 
                {
                    chatHistory.append("API_STDOUT> " + line + "\n");
                }
                writeToSessionLog(sessionId, false, "API_STDOUT> " + line + "\n");
            }

            if (currentSession == sessionId) 
            {
                chatHistory.setCaretPosition(chatHistory.getDocument().getLength());
            }
        });
    }
    /**
     * V14.4 Cross-Platform Native Directory Bridge
     * Triggers the host OS file explorer for the active script directory.
     */
    private void openScriptsFolderNative() {
        String scriptDir = getOSScriptFolder();
        File dir = new File(scriptDir);
        
        // Ensure we are targeting the absolute path relative to the Matrix root
        if (!dir.isAbsolute()) {
            dir = new File(aiHomeDirectory, scriptDir);
        }
        
        // If the soil doesn't exist yet, build it
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String osName = System.getProperty("os.name").toLowerCase();
        try {
            if (osName.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"explorer", dir.getAbsolutePath()});
            } else if (osName.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", dir.getAbsolutePath()});
            } else {
                // Linux/Unix Native Sentry
                Runtime.getRuntime().exec(new String[]{"xdg-open", dir.getAbsolutePath()});
            }
            
            chatHistory.append("SYSTEM> NATIVE_EXPLORER_TRIGGERED [" + dir.getAbsolutePath() + "]\n");
            statusLabel.setText("SYS_DIR: FOLDER_OPENED");
            cacheChatData("SYSTEM> NATIVE_EXPLORER_TRIGGERED [" + scriptDir + "]");
            
        } catch (Exception e) {
            chatHistory.append("SYSTEM_ERR> FOLDER_OPEN_FAILED: " + e.getMessage() + "\n");
            statusLabel.setText("SYS_DIR: ERROR");
        }
    }

    private void buildMatrixArchitecture() 
    {
        for (int i = 1; i <= 12; i++) 
        {
            File sessionDir = new File(aiHomeDirectory + File.separator + FN_BASE_DIR + File.separator + "fn" + i);
            if (!sessionDir.exists()) 
            {
                sessionDir.mkdirs();
            }
            sessionDirectories.put(i, sessionDir);
            
            // Pre-scaffold the system directory for the AI bridge
            new File(sessionDir, "dgapi" + File.separator + "system").mkdirs();
        }
    }

    private void checkApacheIntegration() 
    {
        File indexFile = new File(HTML_FOLDER, "index.html");
        if (!indexFile.exists()) 
        {
            isApacheIntegrated = false;
            return;
        }
        try 
        {
            String content = new String(Files.readAllBytes(indexFile.toPath()));
            isApacheIntegrated = content.contains("<title>coonle</title>");
        } 
        catch (IOException e) 
        {
            isApacheIntegrated = false;
        }
    }

    private void initializeApacheSentryPointer() 
    {
        File log = new File(APACHE_LOG_PATH);
        if (log.exists()) 
        {
            lastApacheLogPos = log.length();
        }
    }

    private void setupDragAndDrop() 
    {
        new DropTarget(this, new DropTargetAdapter() 
        {
            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) 
            {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = dtde.getTransferable();
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        String osName = System.getProperty("os.name").toLowerCase();
                        boolean htmlChanged = false;

                        for (File file : files) 
                        {
                            String name = file.getName().toLowerCase();
                            if (name.endsWith(".html")) 
                            {
                                File targetFile = new File(HTML_FOLDER + File.separator + file.getName());
                                Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                targetFile.setReadable(true, false);
                                String logMsg = "SYSTEM> DND_GATEWAY: IMPORTED [" + file.getName() + "] TO /HTML/ [WEB_PERMISSIONS_GRANTED]";
                                chatHistory.append(logMsg + "\n");
                                cacheChatData(logMsg);
                                statusLabel.setText("SYS_DND: HTML_SUCCESS");
                                htmlChanged = true;
                            } 
                            else if (isScriptModeActive && (name.endsWith(".sh") || name.endsWith(".bat"))) 
                            {
                                String scriptDir = getOSScriptFolder();
                                File targetFile = new File(scriptDir + File.separator + file.getName());
                                Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                String logMsg = "SYSTEM> DND_GATEWAY: SCRIPT [" + file.getName() + "] ADDED TO " + scriptDir + ".";
                                chatHistory.append(logMsg + "\n");
                                if (!osName.contains("win")) 
                                {
                                    chatHistory.append("SYSTEM> REQUESTING PASSWORD FOR chmod +x ON NEW SCRIPT FILE.\n");
                                    String pass = cachedSudoPassword;
                                    if (pass == null) 
                                    {
                                        JPasswordField pf = new JPasswordField();
                                        int ok = JOptionPane.showConfirmDialog(
                                                GoddessMatrix.this,
                                                pf,
                                                "ENTER_SUDO_FOR_CHMOD",
                                                JOptionPane.OK_CANCEL_OPTION,
                                                JOptionPane.PLAIN_MESSAGE
                                        );
                                        if (ok == JOptionPane.OK_OPTION) 
                                        {
                                            pass = new String(pf.getPassword());
                                        }
                                    }
                                    if (pass != null) 
                                    {
                                        String[] cmd = 
                                        {
                                                "/bin/sh",
                                                "-c",
                                                "echo '" + pass + "' | sudo -S chmod +x '" + targetFile.getAbsolutePath() + "'"
                                        };
                                        Runtime.getRuntime().exec(cmd).waitFor();
                                        chatHistory.append("SYSTEM> PERMISSIONS UPDATED FOR [" + file.getName() + "].\n");
                                        statusLabel.setText("SYS_DND: SCRIPT_READY");
                                    }
                                }
                            }
                        }

                        if (htmlChanged) 
                        {
                            checkApacheIntegration();
                            if (isApacheIntegrated) 
                            {
                                updateIndexHub();
                            }
                        }
                    }
                } catch (Exception ex) {
                    statusLabel.setText("SYS_DND: FAILED");
                    chatHistory.append("SYSTEM_ERR> DND_IO_ERROR: " + ex.getMessage() + "\n");}
            }
        }); //DropTarget
    }

    private void updateIndexHub() 
    {
        if (!isApacheIntegrated) 
        {
            return;
        }
        File htmlDir = new File(HTML_FOLDER);
        File indexFile = new File(htmlDir, "index.html");
        try (PrintWriter pw = new PrintWriter(new FileWriter(indexFile))) 
        {
            pw.println("<!DOCTYPE html><html><head><title>coonle</title><style>body{background:#0a0a0c;color:#94a3b8;font-family:sans-serif;padding:3rem;}h1{color:#facd68;letter-spacing:0.2em;border-bottom:1px solid #1a1a20;padding-bottom:1rem;}a{color:#9d50bb;display:block;margin:0.8rem 0;text-decoration:none;font-size:1.2rem;}a:hover{color:#facd68;text-decoration:underline;}</style></head><body><h1>GODDESS_HUB: coonle</h1>");
            File[] files = htmlDir.listFiles();
            if (files != null) 
            {
                for (File f : files) 
                {
                    if (f.getName().endsWith(".html") && !f.getName().equals("index.html")) 
                    {
                        pw.println("<a href='" + f.getName() + "'>[OPEN] " + f.getName() + "</a>");
                    }
                }
            }
            pw.println("</body></html>");
            indexFile.setReadable(true, false);
            chatHistory.append("SYSTEM> INDEX_HUB (coonle) REGENERATED.\n");
        } catch (IOException e) 
        {
            chatHistory.append("SYSTEM_ERR> HUB_SYNC_FAILED.\n");
        }
    }

    private void pollApacheLogs() 
    {
        if (!isApacheIntegrated) 
        {
            return;
        }
        File log = new File(APACHE_LOG_PATH);
        if (!log.exists()) 
        {
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(log, "r")) 
        {
            long len = log.length();
            if (len < lastApacheLogPos) 
            {
                lastApacheLogPos = 0;
            }
            if (len > lastApacheLogPos) 
            {
                raf.seek(lastApacheLogPos);
                String line;
                while ((line = raf.readLine()) != null) 
                {
                    if (line.contains("GET ") || line.contains("POST ")) 
                    {
                        String interaction = line.substring(line.indexOf('"') + 1, line.lastIndexOf('"'));
                        String silentLog = "#A: INTERACTION_DETECTED: " + interaction;
                        pendingLogs.add(silentLog + "\n");
                        statusLabel.setText("SYS_SENTRY: LOGGED_APACHE_HIT");
                    }
                }
                lastApacheLogPos = raf.getFilePointer();
            }
        } 
        catch (Exception ignored) 
        {}
    }

    private void setupHardwareBridge() 
    {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> 
        {
            if (e.getID() != KeyEvent.KEY_PRESSED) 
            {
                return false;
            }                                                            // ── CINEMATIC MODE PASSTHROUGH ──
            if (manifestState == 2) {  // If the visual renderer is fullscreen, intercept all typing and send raw keystrokes directly to the active session.
                routeKeyPressToSession(e.getKeyCode(), e.getKeyChar());
                return true; // Consume the event so it doesn't hit the Matrix typing buffer
            }
            if (e.getKeyCode() == KeyEvent.VK_PAUSE) 
            {
                handleMatrixEvent(14, buttons.get(14), false);
                return true;
            }
            if (isUserModeActive) 
            {
                int index = mapKeyCodeToIndex(e.getKeyCode(), e.getKeyLocation());
                if (index != -1) 
                {
                    JButton btn = buttons.get(index);
                    if (btn != null) {
                        btn.setBackground(HW_PURPLE);
                        new Timer(150, evt -> 
                        {
                            btn.setBackground(KEY_BG);
                            updateModifierVisuals();
                        }).start(); //Timer
                    }
                    cacheChatData("SYS_USER_MODE: CONTROL_SIGNAL_INDEX_" + index);
                }
                return true;
            }
            int index = mapKeyCodeToIndex(e.getKeyCode(), e.getKeyLocation());
            if (index != -1) 
            {
                JButton btn = buttons.get(index);
                if (btn != null) 
                {
                    handleMatrixEvent(index, btn, false);
                }
            }
            return false;
        }); //KeyboardFocusManager Stuff
    }

    private int mapKeyCodeToIndex(int code, int loc) 
    {
        if (loc == KeyEvent.KEY_LOCATION_NUMPAD) 
        {
            if (code >= KeyEvent.VK_NUMPAD0 && code <= KeyEvent.VK_NUMPAD9) 
            {
                int[] map = {98, 95, 96, 97, 92, 93, 94, 89, 90, 91}; //array
                return map[code - KeyEvent.VK_NUMPAD0];
            }
            switch (code) 
            {
                case KeyEvent.VK_DIVIDE: return 83;
                case KeyEvent.VK_MULTIPLY: return 84;
                case KeyEvent.VK_SUBTRACT: return 86;
                case KeyEvent.VK_ADD: return 87;
                case KeyEvent.VK_DECIMAL: return 99;
                case KeyEvent.VK_ENTER: return 100;
                default: break;
            }
        }

        if (code >= KeyEvent.VK_F1 && code <= KeyEvent.VK_F12) 
        {
            return (code - KeyEvent.VK_F1) + 2;
        }
        if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9) 
        {
            return (code - KeyEvent.VK_1) + 19;
        }
        if (code == KeyEvent.VK_0) 
        {
            return 28;
        }
        if (code == KeyEvent.VK_PAUSE) 
        {
            return 14;
        }

        switch (code) //CustomKeyMapData
        {
            case KeyEvent.VK_ESCAPE: return 1;
            case KeyEvent.VK_BACK_QUOTE: return 18;
            case KeyEvent.VK_MINUS: return 29;
            case KeyEvent.VK_EQUALS: return 30;
            case KeyEvent.VK_BACK_SPACE: return 31;
            case KeyEvent.VK_TAB: return 32;
            case KeyEvent.VK_OPEN_BRACKET: return 43;
            case KeyEvent.VK_CLOSE_BRACKET: return 44;
            case KeyEvent.VK_BACK_SLASH: return 45;
            case KeyEvent.VK_CAPS_LOCK: return 46;
            case KeyEvent.VK_SEMICOLON: return 56;
            case KeyEvent.VK_QUOTE: return 57;
            case KeyEvent.VK_ENTER: return 58;
            case KeyEvent.VK_COMMA: return 67;
            case KeyEvent.VK_PERIOD: return 68;
            case KeyEvent.VK_SLASH: return 69;
            case KeyEvent.VK_SPACE: return 75;
            case KeyEvent.VK_SHIFT: return loc == KeyEvent.KEY_LOCATION_LEFT ? 59 : 70;
            case KeyEvent.VK_CONTROL: return loc == KeyEvent.KEY_LOCATION_LEFT ? 71 : 77;
            case KeyEvent.VK_ALT: return loc == KeyEvent.KEY_LOCATION_LEFT ? 74 : 76;
            case KeyEvent.VK_LEFT: return 78;
            case KeyEvent.VK_UP: return 79;
            case KeyEvent.VK_DOWN: return 80;
            case KeyEvent.VK_RIGHT: return 81;
            case KeyEvent.VK_A: return 36;
            case KeyEvent.VK_B: return 41;
            case KeyEvent.VK_C: return 42;
            case KeyEvent.VK_D: return 47;
            case KeyEvent.VK_E: return 37;
            case KeyEvent.VK_F: return 55;
            case KeyEvent.VK_G: return 64;
            case KeyEvent.VK_H: return 60;
            case KeyEvent.VK_I: return 63;
            case KeyEvent.VK_J: return 65;
            case KeyEvent.VK_K: return 66;
            case KeyEvent.VK_L: return 50;
            case KeyEvent.VK_M: return 51;
            case KeyEvent.VK_N: return 49;
            case KeyEvent.VK_O: return 61;
            case KeyEvent.VK_P: return 62;
            case KeyEvent.VK_Q: return 40;
            case KeyEvent.VK_R: return 48;
            case KeyEvent.VK_S: return 52;
            case KeyEvent.VK_T: return 53;
            case KeyEvent.VK_U: return 54;
            case KeyEvent.VK_V: return 38;
            case KeyEvent.VK_W: return 39;
            case KeyEvent.VK_X: return 33;
            case KeyEvent.VK_Y: return 34;
            case KeyEvent.VK_Z: return 35;
            default: return -1;
        } //CustomKeymapData
    }

    private void initRefreshTimer() 
    {
        int rate = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDisplayMode()
                .getRefreshRate();
        if (rate < 30) 
        {
            rate = 60;
        }
        displayRefreshRate = rate;

        refreshTimer = new Timer(1000 / rate, e -> {
            
            // ── EXISTING POLL DUTIES ──────────────────────────
            if (imageManifest != null) 
            {
                imageManifest.repaint();
            }
            pollAIInput();
            pollApacheLogs();
            if (!pendingLogs.isEmpty()) 
            {
                saveSession();
            }
            // ── AI VISUAL RENDERER ────────────────────────────
            // The timer already fires at the true display interval.
            // renderPhase increments by a fixed amount per frame —
            // the display rate IS the sync, no normalization needed.
            if (isAIModeActive && !isHtmlStreamActive && !isVideoStreamActive) 
            {
                long now = System.currentTimeMillis();
                boolean holdExpired = (now - lastRealImageTime) >= IMAGE_HOLD_MS;
                boolean noBuffer    = (activeBuffer == null);
                if (holdExpired || noBuffer) 
                {
                    renderPhase += 0.04f;
                    if (renderPhase > (float)(Math.PI * 2)) 
                    {
                        renderPhase -= (float)(Math.PI * 2);
                    }
                    BufferedImage frame = manifestModeB ? renderModeBFrame() : renderAIFrame();
                    if (frame != null) 
                    {
                        activeBuffer = frame;
                    }
                }
            }
            // ──────────────────────────────────────────────────
        }); //Timer
        refreshTimer.start();
    }

    private void pollAIInput() 
    {
        File sessionDir = sessionDirectories.get(currentSession);
        if (sessionDir == null) return;

        File aiFile = new File(sessionDir, "dgapi" + File.separator + "system" + File.separator + AI_INPUT_FILE);
        if (aiFile.exists() && aiFile.length() > 0) {
            try {
                byte[] bytes = Files.readAllBytes(aiFile.toPath());
                String content = new String(bytes);
                if (!content.isEmpty()) 
                {
                    Files.write(aiFile.toPath(), new byte[0]);
                    simulateTyping(content);
                }
            } catch (IOException ignored) {}
        }
    }

    private void simulateTyping(String content) 
    {
        new Thread(() -> {
            for (char c : content.toCharArray()) 
            {
                int index = mapCharToIndex(c);
                SwingUtilities.invokeLater(() -> {
                    if (index != -1) 
                    {
                        handleMatrixEvent(index, buttons.get(index), false);
                    } else {
                        insertAtCaret(String.valueOf(c));
                    }
                }); //invokeLater
                try 
                {
                    Thread.sleep(30);
                } 
                catch (InterruptedException ignored) 
                {

                }
            }
        }).start();
    }
    private int mapCharToIndex(char c) 
    {
        char upper = Character.toUpperCase(c);
        if (upper >= 'A' && upper <= 'Z') 
        {
            return mapKeyCodeToIndex(KeyEvent.getExtendedKeyCodeForChar(upper), 0);
        }
        if (c >= '0' && c <= '9') 
        {
            return mapKeyCodeToIndex(KeyEvent.getExtendedKeyCodeForChar(c), 0);
        }
        if (c == ' ') 
        {
            return 75;
        }
        if (c == '\n') 
        {
            return 58;
        }
        return -1;
    }

    /**
     * V14.3 chroot macro launcher.
     */

    private String getShellName() 
    {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "cmd.exe";
        return "bash";
    }
    
    private void launchExternalTerminal(boolean asChroot, File targetDir) 
    {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (asChroot && !os.contains("win")) 
            {
                File chrootScript = new File(getOSScriptFolder(), "chroot_env.sh");
                if (!chrootScript.exists()) 
                {
                    String scriptContent =
                            "#!/bin/bash\n" +
                            "CHROOT_DIR=\"" + osDevDir.getAbsolutePath() + "\"\n" +
                            "echo 'SYSTEM> MOUNTING CHROOT VOLUMES...'\n" +
                            "sudo mount -t proc /proc \"$CHROOT_DIR/proc\"\n" +
                            "sudo mount -t sysfs /sys \"$CHROOT_DIR/sys\"\n" +
                            "sudo mount -o bind /dev \"$CHROOT_DIR/dev\"\n" +
                            "sudo mount -o bind /dev/pts \"$CHROOT_DIR/dev/pts\"\n" +
                            "echo 'SYSTEM> ENTERING CHROOT ENVIRONMENT...'\n" +
                            "sudo chroot \"$CHROOT_DIR\" /bin/bash\n" +
                            "echo 'SYSTEM> CHROOT EXITED. UNMOUNTING VOLUMES...'\n" +
                            "sudo umount \"$CHROOT_DIR/dev/pts\"\n" +
                            "sudo umount \"$CHROOT_DIR/dev\"\n" +
                            "sudo umount \"$CHROOT_DIR/sys\"\n" +
                            "sudo umount \"$CHROOT_DIR/proc\"\n" +
                            "echo 'SYSTEM> CLEANUP COMPLETE. CLOSING TERMINAL.'\n" +
                            "sleep 2\n";
                    Files.write(chrootScript.toPath(), scriptContent.getBytes());
                    chrootScript.setExecutable(true);
                }

                if (os.contains("mac")) 
                {
                    pb = new ProcessBuilder("open", "-a", "Terminal", chrootScript.getAbsolutePath());
                } else {
                    pb = new ProcessBuilder("x-terminal-emulator", "-e", chrootScript.getAbsolutePath());
                }
                chatHistory.append("SYSTEM> LAUNCHING EXTERNAL CHROOT TERMINAL...\n");
            } 
            else {
                if (os.contains("win")) 
                {
                    pb = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe");
                } else if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", "-a", "Terminal");
                } else {
                    pb = new ProcessBuilder("x-terminal-emulator");
                }
            }
            pb.directory(targetDir);
            pb.start();
            statusLabel.setText(asChroot ? "SYS_EXEC: CHROOT_TERMINAL_OPEN" : "SYS_EXEC: TERMINAL_OPENED");
        } catch (IOException e) {
            statusLabel.setText("SYS_EXEC: FAILED");
            chatHistory.append("SYSTEM_ERR> TERMINAL_LAUNCH_FAILED: " + e.getMessage() + "\n");
        }
    }

    private void loadLocalHTML(String filename) 
    {
        File targetFile = new File(filename);
        if (!targetFile.isAbsolute()) 
        {
            targetFile = new File(new File(HTML_FOLDER), filename);
        }
        if (!targetFile.exists()) 
        {
            chatHistory.append("SYSTEM> ERROR: [" + filename + "] NOT FOUND.\n");
            chatHistory.append("SYSTEM> SEARCH_PATH: " + targetFile.getAbsolutePath() + "\n");
            chatHistory.append("SYSTEM> WORKING_DIR: " + currentWorkingDirectory.getAbsolutePath() + "\n");
            return;
        }
        try {
            Desktop.getDesktop().browse(targetFile.toURI());
            isHtmlStreamActive = true;
            if (isVideoStreamActive) stopVideoStream();
            imageManifest.repaint();
            statusLabel.setText("SYS_EXEC: BROWSER_ACTIVE");
        } catch (Exception ex) {
            statusLabel.setText("SYS_EXEC: FAILED");
        }
    }

    private String getOSScriptFolder()
    {
        String os = System.getProperty("os.name").toLowerCase();
        String subDir = "Linux"; // Default to your primary environment
        
        if (os.contains("win")) 
        {
            subDir = "Windows";
        } 
        else if (os.contains("mac")) 
        {
            subDir = "MacOSY";
        }
        
        // Anchors to the global AI root (/AI/scrpt/CurrentOS) 
        // entirely outside of the fn/fnX tenant directories.
        return aiHomeDirectory + File.separator + "scrpt" + File.separator + subDir;
    }

    private synchronized void writeToSessionLog(int sessionId, boolean isScript, String text) {
        try {
            // Write to the specific FN folder's convodata.txt
            File sessionLog = new File(aiHomeDirectory + File.separator + FN_BASE_DIR + File.separator + "fn" + sessionId, MASTER_LOG);
            Files.write(sessionLog.toPath(), text.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            // Mirror to script log for debugging
            if (!isScript) {
                File scriptLog = new File(getOSScriptFolder(), "script_fn" + sessionId + ".txt");
                Files.write(scriptLog.toPath(), text.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException ignored) {
        }
    }

    private void loadSession(int sessionNum) {
        saveSession();
        currentSession = sessionNum;
        sessionImagePaths.clear();
        galleryIndex = -1;
        if (isVideoStreamActive) stopVideoStream();

        int mode = sessionModes.getOrDefault(sessionNum, 0);
        isSystemModeActive = (mode == 1);
        isAIModeActive = (mode == 2);
        isScriptModeActive = (mode == 3);

        currentWorkingDirectory = sessionDirectories.getOrDefault(sessionNum, new File(aiHomeDirectory));

        File sessionFile = isScriptModeActive
                ? new File(getOSScriptFolder(), "script_fn" + sessionNum + ".txt")
                : new File(sessionDirectories.get(sessionNum), MASTER_LOG);

        if (!sessionFile.exists()) {
            try {
                sessionFile.createNewFile();
            } 
            catch (IOException ignored) 
            {
            
            }
            chatHistory.setText("SESSION_INIT: FN" + sessionNum + "\n--- DATA VOID ---\n");
        } else {
            try {
                List<String> lines = Files.readAllLines(sessionFile.toPath());
                chatHistory.setText(String.join("\n", lines) + "\n");
                for (String line : lines) {
                    if (line.contains("MANIFEST_IMAGE: ")) {
                        String imageRef = line.substring(line.indexOf("MANIFEST_IMAGE: ") + 16).trim();
                        if (imageRef.startsWith("[API_OVERRIDE]")) 
                        {
                            imageRef = imageRef.substring("[API_OVERRIDE]".length()).trim();
                        }
                        sessionImagePaths.add(imageRef);
                    }
                }
                if (!sessionImagePaths.isEmpty()) 
                {
                    galleryIndex = sessionImagePaths.size() - 1;
                    renderStoredImage(sessionImagePaths.get(galleryIndex));
                } 
                else 
                {
                    activeBuffer = null;
                }
            } 
            catch (IOException e) 
            {
                chatHistory.setText("SESSION_ERROR");
            }
        }

        statusLabel.setText("LINK_ESTABLISHED: SECTOR_FN" + sessionNum);
        aiStatusLabel.setText(apiStatusMap.getOrDefault(sessionNum, API_OFFLINE));
        SwingUtilities.invokeLater(() -> {
            chatHistory.setCaretPosition(chatHistory.getDocument().getLength());
            updateModifierVisuals();
        });
    }

    /**
     * SCRPT mode runs host-native.
     */

    private void executeDirectScript(String cmd) 
    {
        String scriptDir = getOSScriptFolder();
        String osName = System.getProperty("os.name").toLowerCase();
        String ext = osName.contains("win") ? ".bat" : ".sh";
        String scriptName = cmd.endsWith(ext) ? cmd : cmd + ext;
        File scriptFile = new File(new File(scriptDir), scriptName);

        if (!scriptFile.exists()) {
            chatHistory.append("SYSTEM_ERR> SCRIPT NOT FOUND: [" + scriptFile.getAbsolutePath() + "]\n");
            statusLabel.setText("SYS_EXEC: MISSING_SCRIPT");
            return;
        }
            //StartStuffs
        final int sessionId = currentSession;
        String elevationNote = isSudoEnabled ? " [ELEVATED_SUDO_MODE]" : "";
        String traceHeader = "USER>SCRPT>" + cmd + " [HOST_NATIVE_EXECUTION]" + elevationNote;

        writeToSessionLog(
                sessionId,
                true,
                "\n--- SCRIPT SESSION START: " + LocalDateTime.now() + " ---\n" + traceHeader + "\n"
        ); //writeToSessionLog

        if (currentSession == sessionId) 
        {
            chatHistory.append("\n" + traceHeader + "\n");
        }

        new Thread(() -> {
            try {
                List<String> commandList = new ArrayList<>();
                if (osName.contains("win")) {
                    commandList.add(scriptFile.getAbsolutePath());
                } 
                else 
                {
                    if (isSudoEnabled) 
                    {
                        commandList.add("sudo");
                        commandList.add("-S");
                        commandList.add("-E");
                    }
                    commandList.add("bash");
                    commandList.add(scriptFile.getAbsolutePath());
                }

                ProcessBuilder pb = new ProcessBuilder(commandList);
                pb.directory(currentWorkingDirectory);
                pb.redirectErrorStream(true);

                SessionProcess sp = new SessionProcess();
                sp.isScript = true;
                sp.process = pb.start();
                sp.stdin = new PrintWriter(new OutputStreamWriter(sp.process.getOutputStream()), true);
                processMap.put(sessionId, sp);

                if (isSudoEnabled && cachedSudoPassword != null) 
                {
                    sp.stdin.println(cachedSudoPassword);
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(sp.process.getInputStream()))) 
                {
                    String line;
                    while ((line = reader.readLine()) != null) 
                    {
                        if (line.startsWith("[STREAM_START]")) {
                            try {
                                int port = Integer.parseInt(line.substring(14).trim());
                                if (GoddessMatrix.this.currentSession == sessionId) startVideoStream(port);
                            } catch (NumberFormatException ignored) {}
                        } else if (line.startsWith("[STREAM_STOP]")) {
                            if (GoddessMatrix.this.currentSession == sessionId) stopVideoStream();
                        }
                        
                        final String out = line;
                        writeToSessionLog(sessionId, true, out + "\n");
                        if (GoddessMatrix.this.currentSession == sessionId) 
                        {
                            SwingUtilities.invokeLater(() -> {
                                chatHistory.append(out + "\n");
                                chatHistory.setCaretPosition(chatHistory.getDocument().getLength());
                            });
                        }
                    }
                    sp.process.waitFor();
                    processMap.remove(sessionId);
                    if (GoddessMatrix.this.currentSession == sessionId) 
                    {
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("SYS_EXEC: SCRIPT_TERMINATED");
                            checkApacheIntegration();
                        });
                    }
                }
            } 
            catch (Exception e) 
            {
                String err = "SYSTEM_ERR: INTERNAL_EXEC_FAILED [" + e.getMessage() + "]\n";
                writeToSessionLog(sessionId, true, err);
                if (GoddessMatrix.this.currentSession == sessionId) 
                {
                    SwingUtilities.invokeLater(() -> chatHistory.append(err));
                }
            }
        }).start();
    }


    /**
     * EXEC mode sandboxed to osDev.
     */

    private void executeShellCommand(String cmd) 
    {
        if (cmd.isEmpty()) 
        {
            return;
        }

        if (cmd.startsWith("cd ") || cmd.equals("cd")) 
        {
            String target = cmd.equals("cd") ? aiHomeDirectory : cmd.substring(3).trim();
            if (target.contains("~")) 
            {
                target = target.replace("~", aiHomeDirectory);
            }
            File newDir = new File(target);
            if (!newDir.isAbsolute()) 
            {
                newDir = new File(currentWorkingDirectory, target);
            }
            try 
            {
                newDir = newDir.getCanonicalFile();
                if (newDir.exists() && newDir.isDirectory()) 
                {
                    currentWorkingDirectory = newDir;
                    sessionDirectories.put(currentSession, newDir);
                    String msg = "USER>" + getShellName() + ">" + cmd + "\nSYSTEM> DIRECTORY CHANGED TO: " + currentWorkingDirectory.getAbsolutePath();
                    chatHistory.append(msg + "\n");
                    cacheChatData(msg);
                } 
                else 
                {
                    chatHistory.append("SYSTEM_ERR> cd: " + target + ": No such file or directory\n");
                }
            } 
            catch (IOException e) 
            {
                chatHistory.append("SYSTEM_ERR> cd: " + e.getMessage() + "\n");
            }
            return;
        }

        final int sessionId = currentSession;
        String traceHeader = "USER>" + getShellName() + "[" + currentWorkingDirectory.getName() + "]> " + cmd;
        writeToSessionLog(sessionId, false, traceHeader + "\n");

        if (currentSession == sessionId) 
        {
            chatHistory.append(traceHeader + "\n");
        }

        new Thread(() -> {
            try {
                String osName = System.getProperty("os.name").toLowerCase();
                List<String> commandList = new ArrayList<>();

                if (osName.contains("win")) 
                {
                    commandList.add("cmd.exe");
                    commandList.add("/c");
                    commandList.add(cmd);
                } 
                else 
                {
                    if (isSudoEnabled) 
                    {
                        commandList.add("sudo");
                        commandList.add("-S");
                        commandList.add("-E");
                        commandList.add("bash");
                        commandList.add("-c");
                        commandList.add(cmd);
                    } 
                    else 
                    {
                        commandList.add("bash");
                        commandList.add("-c");
                        commandList.add(cmd);
                    }
                }

                ProcessBuilder pb = new ProcessBuilder(commandList);
                pb.directory(sessionDirectories.getOrDefault(sessionId, new File(aiHomeDirectory)));

                Map<String, String> env = pb.environment();
                env.put("HOME", osDevHome.getAbsolutePath());
                String currentPath = env.getOrDefault("PATH", "");
                env.put("PATH", osDevBin.getAbsolutePath() + File.pathSeparator + currentPath);

                pb.redirectErrorStream(true);

                SessionProcess sp = new SessionProcess();
                sp.isScript = false;
                sp.process = pb.start();
                sp.stdin = new PrintWriter(new OutputStreamWriter(sp.process.getOutputStream()), true);
                processMap.put(sessionId, sp);

                if (isSudoEnabled && cachedSudoPassword != null && !osName.contains("win")) 
                {
                    sp.stdin.println(cachedSudoPassword);
                }

                try (BufferedReader r = new BufferedReader(new InputStreamReader(sp.process.getInputStream()))) 
                {
                    String l;
                    while ((l = r.readLine()) != null) 
                    {
                        if (l.startsWith("[STREAM_START]")) {
                            try {
                                int port = Integer.parseInt(l.substring(14).trim());
                                if (GoddessMatrix.this.currentSession == sessionId) startVideoStream(port);
                            } catch (NumberFormatException ignored) {}
                        } else if (l.startsWith("[STREAM_STOP]")) {
                            if (GoddessMatrix.this.currentSession == sessionId) stopVideoStream();
                        }
                        
                        final String o = "SYSTEM> " + l;
                        writeToSessionLog(sessionId, false, o + "\n");
                        if (GoddessMatrix.this.currentSession == sessionId) 
                        {
                            SwingUtilities.invokeLater(() -> {
                                chatHistory.append(o + "\n");
                                chatHistory.setCaretPosition(chatHistory.getDocument().getLength());
                            });
                        }
                    }
                    sp.process.waitFor();
                    processMap.remove(sessionId);
                    if (GoddessMatrix.this.currentSession == sessionId) 
                    {
                        SwingUtilities.invokeLater(() -> statusLabel.setText("SYS_EXEC: TERMINATED"));
                    }
                }
            } 
            catch (Exception e) 
            {
                String err = "SYSTEM_ERR: " + e.getMessage() + "\n";
                writeToSessionLog(sessionId, false, err);
                if (GoddessMatrix.this.currentSession == sessionId) 
                {
                    SwingUtilities.invokeLater(() -> chatHistory.append(err));
                }
            }
        }).start(); //Thread
    }

    private void ensureStorageDirectoryExists() 
    {
        File dir = new File(STORAGE_FOLDER);
        if (!dir.exists()) 
        {
            dir.mkdirs();
        }
        new File(HTML_FOLDER).mkdirs();
        new File(getOSScriptFolder()).mkdirs();
        try {
            new File(STORAGE_FOLDER + File.separator + AI_INPUT_FILE).createNewFile();
        } catch (IOException ignored) {   
        }
    }

    private void cacheChatData(String text) 
    {
        if (text.trim().isEmpty()) 
        {
            return;
        }
        String logEntry;
        if (text.startsWith("MANIFEST_IMAGE:")
                || text.startsWith("SYS_STATE:")
                || text.startsWith("SYS_TELEMETRY:")
                || text.startsWith("SYS_EXEC:")
                || text.startsWith("SHELL_OUT:")
                || text.startsWith("USER>")
                || text.startsWith("SYS_USER_MODE:")
                || text.startsWith("#A:")
                || text.startsWith("GODDESS>")) 
        {
            logEntry = text + "\n";
        } 
        else {
            if (isAIModeActive) {
                logEntry = "#AI> USER> " + text + "\n";
                chatHistory.append("AI_PROMPT> " + text + "\n");
            } else {
                String pfx = isCalculatorEnabled ? "#| " : "# ";
                logEntry = pfx + "USER> " + text + "\n";
                chatHistory.append("USER> " + text + "\n");
            }
        }
        pendingLogs.add(logEntry);
    }

    private void saveSession() 
    {
        File sessionDir = sessionDirectories.get(currentSession);
        if (sessionDir == null) return;

        File sessionFile = new File(sessionDir, MASTER_LOG);
        try 
        {
            for (String entry : pendingLogs) 
            {
                Files.write(sessionFile.toPath(), entry.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            pendingLogs.clear();
            statusLabel.setText("SYS_SYNC: SAVED");
        } 
        catch (IOException e) 
        {
            statusLabel.setText("SYS_SYNC: FAILED");
        }
    }

    public void manifestImage(BufferedImage img) 
    {
        if (img == null) 
        {
            return;
        }
        isHtmlStreamActive    = false;
        if (isVideoStreamActive) stopVideoStream();
        lastRealImageTime = System.currentTimeMillis(); // hold: renderer yields
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "fn" + currentSession + "-cd-" + timestamp + ".png";
        try 
        {
            File sessionDir = sessionDirectories.get(currentSession);
            File outputFile = new File(sessionDir, fileName);
            ImageIO.write(img, "png", outputFile);
            sessionImagePaths.add(fileName);
            galleryIndex = sessionImagePaths.size() - 1;
            cacheChatData("MANIFEST_IMAGE: " + fileName);
            activeBuffer = img;
            statusLabel.setText("SYS_MANIFEST: SAVED");
        } 
        catch (IOException e) 
        {
            statusLabel.setText("SYS_MANIFEST: FAILED");
        }
    }

    private void renderStoredImage(String fileName) 
    {
        isHtmlStreamActive    = false;
        if (isVideoStreamActive) stopVideoStream();
        lastRealImageTime = System.currentTimeMillis(); // hold: renderer yields
        try 
        {
            File imgFile = new File(fileName);
            if (!imgFile.isAbsolute()) 
            {
                File sessionDir = sessionDirectories.get(currentSession);
                imgFile = new File(sessionDir, fileName);
            }
            if (imgFile.exists()) 
            {
                activeBuffer = ImageIO.read(imgFile);
            } 
            else 
            {
                activeBuffer = null;
            }
        } 
        catch (IOException e) 
        {
            activeBuffer = null;
        }
    }

    private void cycleGallery(int delta) 
    {
        if (sessionImagePaths.isEmpty()) 
        {
            return;
        }
        galleryIndex = (galleryIndex + delta + sessionImagePaths.size()) % sessionImagePaths.size();
        renderStoredImage(sessionImagePaths.get(galleryIndex));
    }

    private void toggleManifestVisibility() 
    {
        manifestVisible = !manifestVisible;
        statusLabel.setText("SYS_MANIFEST: " + (manifestVisible ? "ENABLED" : "HIDDEN"));
    }

    private void restoreManifest() 
    {
        if (manifestState == 2) 
        {
            JRootPane root = getRootPane();
            root.getGlassPane().setVisible(false);
            originalParent.add(imageManifest);
            isUserModeActive = false;
            if (isHtmlStreamActive) 
            {
                chatHistory.append("SYSTEM> CONTROLLER_MODE_OFFLINE\n");
            }
        }
        imageManifest.setBounds(originalManifestBounds);
        manifestState = 0;
        statusLabel.setText("SYS_MANIFEST: RESTORED");
    }

    // ════════════════════════════════════════════════════════
    //   AI VISUAL RENDERER
    //   Generates animated status graphics for imageManifest.
    //   Active whenever A.I. mode is ON (always, not just FN+AI).
    //   FN+AI additionally enables the enhanced visual HUD.
    //   Reads session_profile.txt written by the Python AI system.
    // ════════════════════════════════════════════════════════

private void loadSystemProfile() 
    {
        // V14.4 looks in the dedicated datas folder first
        File profileFile = new File("datas" + File.separator + "session_profile.txt");
        
        // Fallbacks for legacy/testing
        if (!profileFile.exists()) {
            profileFile = new File("session_profile.txt");
        }
        if (!profileFile.exists()) {
            profileFile = new File("dgapi" + File.separator + "system" + File.separator + "session_profile.txt");
        }
        
        if (profileFile.exists()) 
        {
            try 
            {
                List<String> lines = java.nio.file.Files.readAllLines(profileFile.toPath());
                List<String> relevant = new ArrayList<>();
                for (String l : lines) 
                {
                    String t = l.trim();
                    if (t.startsWith("CPU") || t.startsWith("GPU") || t.startsWith("RAM")
                            || t.startsWith("Mode") || t.startsWith("Cores")
                            || t.startsWith("REASONING") || t.contains(":")) 
                    {
                        if (t.length() > 3 && !t.startsWith("═") && !t.startsWith("─")
                                && !t.startsWith("#") && !t.startsWith("━")) 
                        {
                            relevant.add(t);
                        }
                    }
                }
                if (relevant.size() > 0) systemProfileLine1 = relevant.get(0);
                if (relevant.size() > 1) systemProfileLine2 = relevant.get(1);
                if (relevant.size() > 2) systemProfileLine3 = relevant.get(2);
            } 
            catch (Exception ignored) 
            {
                systemProfileLine1 = "PROFILE: session_profile.txt";
                systemProfileLine2 = "Drop profile in working directory";
                systemProfileLine3 = "or run GoddessAPI.sh first";
            }
        } 
        else 
        {
            systemProfileLine1 = "PROFILE: NOT FOUND";
            systemProfileLine2 = "Run GoddessAPI.sh to generate profile";
            systemProfileLine3 = "";
        }
    }

    private void startAIRenderer(int sessionId) {
        // No separate timer — rendering is driven by the display-rate
        // refreshTimer already running in initRefreshTimer().
        aiSessionStartMs  = System.currentTimeMillis();
        aiQueryCount      = 0;
        currentAIStatus   = "LINK_ESTABLISHED: FN" + sessionId;
        isAIProcessing    = false;
        renderPhase       = 0f;
        lastRealImageTime = 0; // let renderer start immediately
        loadSystemProfile();
    }

    private void stopAIRenderer() 
    {
        // Rendering is part of refreshTimer — just clear state.
        // activeBuffer intentionally left as-is so the last frame
        // remains visible rather than snapping back to pending.
        isAIProcessing  = false;
        currentAIStatus = "OFFLINE";
    }

    private BufferedImage renderAIFrame() 
    {
        // Determine manifest panel size — fall back to 320x240
        int W = (imageManifest.getWidth()  > 0) ? imageManifest.getWidth()  : 320;
        int H = (imageManifest.getHeight() > 0) ? imageManifest.getHeight() : 240;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── BACKGROUND ────────────────────────────────────────
        g.setPaint(new GradientPaint(0, 0, new Color(8, 6, 14), W, H, new Color(14, 10, 22)));
        g.fillRect(0, 0, W, H);

        // ── ANIMATED GRID (subtle) ────────────────────────────
        g.setColor(new Color(80, 40, 110, 25));
        g.setStroke(new BasicStroke(0.5f));
        int gridSpacing = 24;
        for (int x = 0; x < W; x += gridSpacing) g.drawLine(x, 0, x, H);
        for (int y = 0; y < H; y += gridSpacing) g.drawLine(0, y, W, y);

        // ── TOP HEADER BAR ────────────────────────────────────
        g.setPaint(new GradientPaint(0, 0, new Color(60, 20, 90, 200),
                                     W, 0, new Color(20, 10, 40, 200)));
        g.fillRect(0, 0, W, 24);
        g.setColor(new Color(157, 80, 187, 180));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(0, 24, W, 24);

        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(new Color(250, 205, 104));
        drawStringLeft(g, "GODDESS A.I.", 8, 16);

        String modeStr = fnAIVisualMode ? "ENHANCED HUD" : "STANDARD";
        g.setColor(new Color(157, 80, 187));
        drawStringRight(g, modeStr + " " + displayRefreshRate + "Hz", W - 8, 16);

        // ── SESSION UPTIME ────────────────────────────────────
        long elapsedSec  = (System.currentTimeMillis() - aiSessionStartMs) / 1000;
        long hrs  = elapsedSec / 3600;
        long mins = (elapsedSec % 3600) / 60;
        long secs = elapsedSec % 60;
        String uptime = String.format("%02d:%02d:%02d", hrs, mins, secs);

        int yTop = 38;
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(new Color(148, 163, 184, 180));
        drawStringLeft(g, "SESSION", 8, yTop);
        g.setColor(new Color(250, 205, 104, 220));
        drawStringRight(g, uptime, W - 8, yTop);

        // ── STATUS LINE ───────────────────────────────────────
        yTop += 14;
        g.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g.setColor(new Color(148, 163, 184, 150));
        drawStringLeft(g, "STATUS", 8, yTop);
        Color statusColor = isAIProcessing
                ? new Color(250, 205, 104)
                : new Color(80, 220, 120);
        g.setColor(statusColor);
        String displayStatus = currentAIStatus.length() > 22
                ? currentAIStatus.substring(0, 22) + "…"
                : currentAIStatus;
        drawStringRight(g, displayStatus, W - 8, yTop);

        // ── QUERY COUNTER ─────────────────────────────────────
        yTop += 14;
        g.setColor(new Color(148, 163, 184, 150));
        drawStringLeft(g, "QUERIES", 8, yTop);
        g.setColor(new Color(157, 80, 187, 220));
        drawStringRight(g, String.valueOf(aiQueryCount), W - 8, yTop);

        // ── DIVIDER ───────────────────────────────────────────
        yTop += 8;
        g.setColor(new Color(157, 80, 187, 60));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(8, yTop, W - 8, yTop);
        yTop += 10;

        // ── SYSTEM PROFILE LINES ──────────────────────────────
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(148, 163, 184, 160));
        if (!systemProfileLine1.isEmpty()) 
        {
            drawStringLeft(g, clip(systemProfileLine1, W, g), 8, yTop);
            yTop += 12;
        }
        if (!systemProfileLine2.isEmpty()) 
        {
            drawStringLeft(g, clip(systemProfileLine2, W, g), 8, yTop);
            yTop += 12;
        }
        if (!systemProfileLine3.isEmpty()) 
        {
            drawStringLeft(g, clip(systemProfileLine3, W, g), 8, yTop);
            yTop += 12;
        }

        // ── DIVIDER ───────────────────────────────────────────
        yTop += 4;
        g.setColor(new Color(157, 80, 187, 60));
        g.drawLine(8, yTop, W - 8, yTop);
        yTop += 8;

        // ── WAVEFORM / PULSE ANIMATION ────────────────────────
        int waveY    = yTop;
        int waveH    = Math.min(60, H - waveY - 40);
        int waveMidY = waveY + waveH / 2;

        if (isAIProcessing) {
            // Active waveform — multi-frequency when processing
            drawWaveform(g, W, waveMidY, waveH, renderPhase,
                         new Color(250, 205, 104, 200),
                         new Color(157, 80, 187, 120));
        } 
        else 
        {
            // Gentle idle pulse
            drawIdlePulse(g, W, waveMidY, waveH, renderPhase,
                          new Color(80, 220, 120, 160),
                          new Color(40, 140, 80, 80));
        }

        yTop += waveH + 10;

        // ── PROCESSING BAR ────────────────────────────────────
        if (isAIProcessing) {
            int barW = W - 16;
            int barH = 6;
            g.setColor(new Color(40, 20, 60));
            g.fillRoundRect(8, yTop, barW, barH, 3, 3);
            // Animated fill
            float progress = (float)((Math.sin(renderPhase * 2) + 1.0) / 2.0);
            int fillW = (int)(barW * 0.3f + barW * 0.7f * progress);
            g.setPaint(new GradientPaint(8, yTop,
                    new Color(157, 80, 187),
                    8 + fillW, yTop,
                    new Color(250, 205, 104)));
            g.fillRoundRect(8, yTop, fillW, barH, 3, 3);
            yTop += barH + 6;
            g.setFont(new Font("Monospaced", Font.PLAIN, 8));
            g.setColor(new Color(250, 205, 104, 180));
            drawStringCentered(g, "GENERATING RESPONSE", W, yTop);
            yTop += 12;
        }

        // ── SESSION & MODE INDICATOR ──────────────────────────
        yTop = H - 28;
        g.setColor(new Color(30, 15, 50, 200));
        g.fillRect(0, yTop, W, H - yTop);
        g.setColor(new Color(157, 80, 187, 80));
        g.drawLine(0, yTop, W, yTop);

        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(148, 163, 184, 140));
        drawStringLeft(g, "SESSION: FN" + currentSession, 8, yTop + 10);

        // Pulsing dot indicator
        float dotAlpha = (float)((Math.sin(renderPhase * 3) + 1.0) / 2.0);
        int dotAlphaInt = (int)(dotAlpha * 200) + 55;
        Color dotColor = isAIProcessing
                ? new Color(250, 205, 104, dotAlphaInt)
                : new Color(80, 220, 120, dotAlphaInt);
        g.setColor(dotColor);
        g.fillOval(W - 22, yTop + 5, 8, 8);
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(new Color(148, 163, 184, 120));
        String modeTag = isAIProcessing ? "PROC" : "IDLE";
        drawStringRight(g, modeTag, W - 28, yTop + 13);

        // ── TIMESTAMP ─────────────────────────────────────────
        g.setFont(new Font("Monospaced", Font.PLAIN, 7));
        g.setColor(new Color(80, 60, 100, 140));
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        drawStringLeft(g, ts, 8, H - 6);
        g.dispose();
        return img;
    }

    // ── WAVEFORM DRAWING ──────────────────────────────────────

    private void drawWaveform(Graphics2D g, int W, int midY, int ampH,
                               float phase, Color c1, Color c2) {
        int[] xPts = new int[W];
        int[] y1   = new int[W];
        int[] y2   = new int[W];
        for (int x = 0; x < W; x++) 
        {
            double t = x / (double) W;
            y1[x] = midY + (int)(Math.sin(t * Math.PI * 6 + phase) * ampH * 0.38);
            y2[x] = midY + (int)(Math.sin(t * Math.PI * 10 + phase * 1.5) * ampH * 0.2);
            xPts[x] = x;
        }
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(c2);
        for (int x = 1; x < W; x++) 
        {
            g.drawLine(x - 1, y2[x - 1], x, y2[x]);
        }
        g.setColor(c1);
        for (int x = 1; x < W; x++)
        {
            g.drawLine(x - 1, y1[x - 1], x, y1[x]);
        }
    }
        //2dJavaGraphics
    private void drawIdlePulse(Graphics2D g, int W, int midY, int ampH,
                                float phase, Color c1, Color c2) 
    {
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(c2);
        for (int x = 1; x < W; x++) 
        {
            double t = x / (double) W;
            int y0 = midY + (int)(Math.sin(t * Math.PI * 3 + phase * 0.5) * ampH * 0.12);
            int y1 = midY + (int)(Math.sin((x-1.0)/W * Math.PI * 3 + phase * 0.5) * ampH * 0.12);
            g.drawLine(x - 1, y1, x, y0);
        }
        g.setColor(c1);
        for (int x = 1; x < W; x++) 
        {
            double t = x / (double) W;
            int y0 = midY + (int)(Math.sin(t * Math.PI * 2 + phase) * ampH * 0.25);
            int y1 = midY + (int)(Math.sin((x-1.0)/W * Math.PI * 2 + phase) * ampH * 0.25);
            g.drawLine(x - 1, y1, x, y0);
        }
    }

    // ── TEXT HELPERS ──────────────────────────────────────────
    private void drawStringLeft(Graphics2D g, String s, int x, int y) 
    {
        g.drawString(s, x, y);
    }

    private void drawStringRight(Graphics2D g, String s, int rightX, int y) 
    {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, rightX - fm.stringWidth(s), y);
    }

    private void drawStringCentered(Graphics2D g, String s, int W, int y) 
    {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (W - fm.stringWidth(s)) / 2, y);
    }

    private String clip(String s, int W, Graphics2D g) 
    {
        FontMetrics fm = g.getFontMetrics();
        int maxW = W - 20;
        while (s.length() > 4 && fm.stringWidth(s) > maxW) 
        {
            s = s.substring(0, s.length() - 4) + "…";
        }
        return s;
    }

    // ── IMAGE.XTX LOADER ─────────────────────────────────────────
    // Plain key=value directive file the AI evolves over time.
    // Re-read whenever file modification time changes.
    // Unknown directives stored silently — future builds may act on them.
    //
    // Built-in directives (AI may add new ones):
    //   BODY_COLOR, SINE_COLOR, BACKGROUND_COLOR, TEXT_COLOR
    //   WALK_SPEED, AVATAR_SCALE
    //   FEATURE=sine_box|status_text|activity_dot|speech_bubble
    //   ACTION_<n>=<pose>  — maps [ACTION] tag values to poses

    private void loadImageXTX() 
    {
        File xtxFile = new File(aiHomeDirectory, "image.xtx");
        if (!xtxFile.exists()) 
        {
            seedDefaultImageXTX(xtxFile);
            return;
        }
        long mod = xtxFile.lastModified();
        if (mod == imageXTXLastModified) return;
        imageXTXLastModified = mod;
        imageXTX.clear();
        try (java.io.BufferedReader br =
                 new java.io.BufferedReader(new java.io.FileReader(xtxFile))) 
        {
            String line;
            while ((line = br.readLine()) != null) 
            {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0)
                    imageXTX.put(line.substring(0, eq).trim().toUpperCase(),
                                 line.substring(eq + 1).trim());
            }
        } 
        catch (Exception ignored) {}
    }

    private void seedDefaultImageXTX(File xtxFile) 
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
          + "ACTION_THINK=hand_on_chin\n";
        try (java.io.FileWriter fw = new java.io.FileWriter(xtxFile)) 
        {
            fw.write(seed);
            imageXTXLastModified = xtxFile.lastModified();
        } 
        catch (Exception ignored) {}
        for (String line : seed.split("\n")) 
        {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq > 0)
                imageXTX.put(line.substring(0, eq).trim().toUpperCase(),
                             line.substring(eq + 1).trim());
        }
    }

    private Color  xtxColor(String key, Color  fb) 
    {
        String v = imageXTX.get(key.toUpperCase());
        if (v == null) return fb;
        try { return Color.decode(v); } catch (Exception e) { return fb; }
    }

    private float  xtxFloat(String key, float  fb) 
    {
        String v = imageXTX.get(key.toUpperCase());
        if (v == null) return fb;
        try { return Float.parseFloat(v); } catch (Exception e) { return fb; }
    }

    private String xtxString(String key, String fb) 
    {
        String v = imageXTX.get(key.toUpperCase());
        return (v != null) ? v : fb;
    }

    // ── MODE B FRAME RENDERER ────────────────────────────────────
    // Driven by the same display-rate refreshTimer as Mode A.
    // Stick figure pose is determined by avatarAction → ACTION_* lookup in image.xtx.
    // The sine box below mirrors the Mode A waveform so activity level is readable.

    private BufferedImage renderModeBFrame() 
    {
        loadImageXTX();

        int W = (imageManifest.getWidth()  > 0) ? imageManifest.getWidth()  : 320;
        int H = (imageManifest.getHeight() > 0) ? imageManifest.getHeight() : 240;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D    g   = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // ── BACKGROUND ────────────────────────────────────────
        g.setColor(xtxColor("BACKGROUND_COLOR", new Color(10, 10, 12)));
        g.fillRect(0, 0, W, H);

        // ── ANIMATED GRID (subtle) ────────────────────────────
        g.setColor(new Color(80, 40, 110, 20));
        g.setStroke(new BasicStroke(0.5f));
        for (int x = 0; x < W; x += 24) g.drawLine(x, 0, x, H);
        for (int y = 0; y < H; y += 24) g.drawLine(0, y, W, y);

        Color bodyColor = xtxColor("BODY_COLOR",  new Color(157, 80, 187));
        Color textColor = xtxColor("TEXT_COLOR",  new Color(148, 163, 184));
        Color sineColor = xtxColor("SINE_COLOR",  new Color(250, 205, 104));
        float scale     = xtxFloat("AVATAR_SCALE", 1.0f);
        float walkSpd   = xtxFloat("WALK_SPEED",   2.0f);

        // Advance walk-cycle animation phase
        avatarWalkCycle += 0.06f * walkSpd;
        if (avatarWalkCycle > (float)(Math.PI * 2)) avatarWalkCycle -= (float)(Math.PI * 2);

        // Resolve current pose from image.xtx action map
        String actionKey = "ACTION_" + avatarAction.toUpperCase();
        String pose      = xtxString(actionKey, "stand");

        // ── STATUS TEXT (top) ─────────────────────────────────
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(new Color(157, 80, 187, 180));
        String displayAction = avatarAction.length() > 24
                ? avatarAction.substring(0, 24) + "…" : avatarAction;
        drawStringCentered(g, displayAction, W, 14);

        // ── STICK FIGURE ─────────────────────────────────────
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

        // Head
        g.drawOval(cx - headR, top, headR * 2, headR * 2);
        // Body
        g.drawLine(cx, neckY, cx, hipY);

        float legSwing = (float)Math.sin(avatarWalkCycle) * 18 * scale;
        float armSwing = (float)Math.sin(avatarWalkCycle + Math.PI) * 14 * scale;

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
                // Mouth arc
                g.drawArc(cx - headR/3, headCY + (int)(headR * 0.4f) - 3,
                          headR/2, headR/3, 0, -180);
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
                g.drawLine(cx - headR, hipY,               cx - headR - legLen / 2, seatY);
                g.drawLine(cx + headR, hipY,               cx + headR + legLen / 2, seatY);
                g.drawLine(cx - headR - legLen / 2, seatY, cx - headR - legLen / 2, footY);
                g.drawLine(cx + headR + legLen / 2, seatY, cx + headR + legLen / 2, footY);
                g.drawLine(cx, elbowY, cx - armLen, elbowY + 14);
                g.drawLine(cx, elbowY, cx + armLen, elbowY + 14);
                break;
            }
            default: // stand
            {
                float sway = (float)Math.sin(renderPhase) * 4;
                g.drawLine(cx, hipY, cx - (int)(legLen * 0.12f), footY);
                g.drawLine(cx, hipY, cx + (int)(legLen * 0.12f), footY);
                g.drawLine(cx, elbowY, cx - armLen + (int)sway,  elbowY + 16);
                g.drawLine(cx, elbowY, cx + armLen - (int)sway,  elbowY + 16);
                break;
            }
        }

        // Eyes — blink roughly every 3.5 s
        boolean blink = ((int)(System.currentTimeMillis() / 3500) % 8 == 0);
        if (!blink) 
        {
            g.setColor(new Color(250, 205, 104));
            g.fillOval(cx - headR / 3 - 2, headCY - headR / 4, 3, 3);
            g.fillOval(cx + headR / 3 - 1, headCY - headR / 4, 3, 3);
        }

        // ── SINE ACTIVITY BOX ─────────────────────────────────
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

        if (isAIProcessing) 
        {
            drawWaveform(g, W, midY, ampH, renderPhase, sineColor,
                         new Color(sineColor.getRed(), sineColor.getGreen(),
                                   sineColor.getBlue(), 100));
        } 
        else 
        {
            drawIdlePulse(g, W, midY, ampH, renderPhase,
                          new Color(80, 220, 120, 160), new Color(40, 140, 80, 80));
        }

        // ── BOTTOM STATUS BAR ─────────────────────────────────
        int barTop = H - 20;
        g.setColor(new Color(30, 15, 50, 200));
        g.fillRect(0, barTop, W, H - barTop);
        g.setColor(new Color(157, 80, 187, 60));
        g.setStroke(new BasicStroke(0.8f));
        g.drawLine(0, barTop, W, barTop);

        g.setFont(new Font("Monospaced", Font.PLAIN, 7));
        g.setColor(textColor);
        drawStringLeft(g, "FN" + currentSession + " | " + (isAIProcessing ? "ACTIVE" : "IDLE"),
                       6, barTop + 12);

        float dotA = (float)((Math.sin(renderPhase * 3) + 1.0) / 2.0);
        g.setColor(new Color(isAIProcessing ?  250 : 80,
                             isAIProcessing ?   80 : 220,
                             isAIProcessing ?   80 : 120,
                             (int)(dotA * 180 + 55)));
        g.fillOval(W - 14, barTop + 5, 8, 8);

        long elSec = (System.currentTimeMillis() - aiSessionStartMs) / 1000;
        g.setFont(new Font("Monospaced", Font.PLAIN, 7));
        g.setColor(new Color(80, 60, 100, 140));
        drawStringRight(g, String.format("%02d:%02d:%02d", elSec/3600,(elSec%3600)/60,elSec%60),
                        W - 20, barTop + 12);

        g.dispose();
        return img;
    }

    // ── MODE TOGGLE (right-click on manifest panel) ───────────────
    // Switches Mode A (waveform HUD) <-> Mode B (avatar).
    // Additive to existing left-click expand behaviour.
    private void handleManifestRightClick() 
    {
        manifestModeB = !manifestModeB;
        String label  = manifestModeB ? "AVATAR_MODE" : "HUD_MODE";
        statusLabel.setText("SYS_MANIFEST: " + label + "_ACTIVE");
        chatHistory.append("SYSTEM> MANIFEST_MODE: " + label + "\n");
    }

    // ── IMAGE.XTX RELOAD ─────────────────────────────────────────
    // Forces a fresh parse of image.xtx mid-session.
    private void reloadImageXTX() 
    {
        imageXTXLastModified = 0L;
        loadImageXTX();
        statusLabel.setText("SYS: IMAGE_XTX_RELOADED");
    }

        //KeyboardSetup
    private void setupKeyboard(JPanel panel) 
    {
        int currentY = 10;
        int currentX = 27;

        for (int i = 1; i <= 17; i++) 
        {
            panel.add(createKey(i, currentX, currentY, (int) (KEY_U * 0.9), KEY_H));
            currentX += (int) (KEY_U * 0.9) + KEY_GAP;
        }
        currentY += KEY_H + KEY_GAP;
        currentX = 27;
        panel.add(createKey(18, currentX, currentY, (int) (KEY_U * 0.8), KEY_H));
        currentX += (int) (KEY_U * 0.8) + KEY_GAP;
        for (int i = 19; i <= 30; i++) 
        {
            panel.add(createKey(i, currentX, currentY, KEY_U, KEY_H));
            currentX += KEY_U + KEY_GAP;
        }
        panel.add(createKey(31, currentX, currentY, (int) (KEY_U * 2.2) + 15, KEY_H));
        currentY += KEY_H + KEY_GAP;
        currentX = 27;
        panel.add(createKey(32, currentX, currentY, (int) (KEY_U * 1.3), KEY_H));
        currentX += (int) (KEY_U * 1.3) + (int) (KEY_U * 0.2) + KEY_GAP;
        for (int i = 33; i <= 44; i++) 
        {
            panel.add(createKey(i, currentX, currentY, KEY_U, KEY_H));
            currentX += KEY_U + KEY_GAP;
        }
        currentX += (int) (KEY_U * 0.2);
        panel.add(createKey(45, currentX, currentY, (int) (KEY_U * 1.7), KEY_H));
        currentY += KEY_H + KEY_GAP;
        currentX = 27;
        panel.add(createKey(46, currentX, currentY, (int) (KEY_U * 1.5), KEY_H));
        currentX += (int) (KEY_U * 1.5) + (int) (KEY_U * 0.2) + KEY_GAP;
        for (int i = 47; i <= 57; i++) 
        {
            panel.add(createKey(i, currentX, currentY, KEY_U, KEY_H));
            currentX += KEY_U + KEY_GAP;
        }
        currentX += (int) (KEY_U * 0.2);
        panel.add(createKey(58, currentX, currentY, (int) (KEY_U * 2.6), KEY_H));
        currentY += KEY_H + KEY_GAP;
        currentX = 27;
        panel.add(createKey(59, currentX, currentY, (int) (KEY_U * 2.2), KEY_H));
        currentX += (int) (KEY_U * 2.2) + (int) (KEY_U * 0.2) + KEY_GAP;

        for (int i = 60; i <= 69; i++) 
        {
            panel.add(createKey(i, currentX, currentY, KEY_U, KEY_H));
            currentX += KEY_U + KEY_GAP;
        }
        currentX += (int) (KEY_U * 0.2);
        panel.add(createKey(70, currentX, currentY, (int) (KEY_U * 2.7) + 15, KEY_H));
        currentY += KEY_H + KEY_GAP;
        currentX = 27;
        for (int i = 71; i <= 74; i++) 
        {
            panel.add(createKey(i, currentX, currentY, (int) (KEY_U * 1.1), KEY_H));
            currentX += (int) (KEY_U * 1.1) + KEY_GAP;
        }
        panel.add(createKey(75, currentX, currentY, (int) (KEY_U * 5.3), KEY_H));
        currentX += (int) (KEY_U * 5.3) + KEY_GAP;

        for (int i = 76; i <= 78; i++) 
        {
            panel.add(createKey(i, currentX, currentY, (int) (KEY_U * 1.1), KEY_H));
            currentX += (int) (KEY_U * 1.1) + KEY_GAP;
        }
        panel.add(createKey(79, currentX, currentY, KEY_U, (int) (KEY_H * 0.4)));
        panel.add(createKey(80, currentX, currentY + (int) (KEY_H * 0.6), KEY_U, (int) (KEY_H * 0.4)));
        currentX += KEY_U + KEY_GAP;
        panel.add(createKey(81, currentX, currentY, KEY_U, KEY_H));
        currentX += KEY_U + KEY_GAP;
        panel.add(createKey(82, currentX, currentY, (int) (KEY_U * 0.8), KEY_H));
        int numBaseX = currentX + 60;
        int numX = numBaseX;
        int numY = 10;
        int[] numIndices = {83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100};
        for (int i = 0; i < numIndices.length; i++) 
        {
            panel.add(createKey(numIndices[i], numX, numY, KEY_U, KEY_H));
            numX += KEY_U + KEY_GAP;
            if ((i + 1) % 3 == 0) 
            {
                numX = numBaseX;
                numY += KEY_H + KEY_GAP;
            }
        }
        int actionBaseX = numBaseX + 3 * (KEY_U + KEY_GAP) + 15;
        int actionY = 10;
        // Restore the original AI column
        int[] actionIndices = {101, 102, 103, 104, 105, 106}; 
        
        for (int i = 0; i < actionIndices.length; i++) 
        {
            panel.add(createKey(actionIndices[i], actionBaseX, actionY, KEY_U + 10, KEY_H));
            actionY += KEY_H + KEY_GAP;
        }

        // ── CREATE NEW COLUMN FOR [DIR] BUTTON ──
        // Shift X coordinate to the right by the width of the action button + gap
        int newColumnX = actionBaseX + (KEY_U + 10) + KEY_GAP;
        int newColumnY = 10; // Aligns with the top of the AI column
        
        // Add the 107 button to this new column with standard height
        panel.add(createKey(107, newColumnX, newColumnY, KEY_U + 10, KEY_H));
    }
    private JButton createKey(int index, int x, int y, int w, int h) 
    {
        JButton b = new JButton();
        b.setBounds(x, y, w, h);
        b.setBackground(KEY_BG);
        b.setFocusPainted(false);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 10), 1));
        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // If X coordinate is greater than half the button width, it's a right-side click
                boolean isRightSide = e.getX() > (b.getWidth() / 2);
                handleMatrixEvent(index, b, true, isRightSide);
            }
        }); //noting older button implementation: b.addActionListener(e -> handleMatrixEvent(index, b, true));
        buttons.put(index, b);
        return b;
    }
    // Hardware keyboard bridge and auto-typer fallback (defaults to left-click)
    private void handleMatrixEvent(int index, JButton btn, boolean isMouse) 
    {
        handleMatrixEvent(index, btn, isMouse, false);
    }
    private void handleMatrixEvent(int index, JButton btn, boolean isMouse, boolean isRightSide) 
    {
        if (index == 101) 
        {
            saveSession();
            for (int sessionId = 1; sessionId <= 12; sessionId++) 
            {
                stopGoddessAPI(sessionId);
            }
            System.exit(0);
        }
        if (index == 102) 
        {
            SessionProcess sp = processMap.get(currentSession);
            if (sp != null && sp.process.isAlive()) 
            {
                sp.process.destroy();
                chatHistory.append("SYSTEM> ACTIVE_PROCESS_TERMINATED_VIA_HARD_INTERRUPT\n");
                writeToSessionLog(currentSession, sp.isScript, "SYSTEM> ACTIVE_PROCESS_TERMINATED_VIA_HARD_INTERRUPT\n");
            }
            Process apiProcess = apiProcessMap.get(currentSession);
            if (apiProcess != null && apiProcess.isAlive()) 
            {
                stopGoddessAPI(currentSession);
                chatHistory.append("SYSTEM> API_PROCESS_TERMINATED_VIA_HARD_INTERRUPT\n");
                writeToSessionLog(currentSession, false, "SYSTEM> API_PROCESS_TERMINATED_VIA_HARD_INTERRUPT\n");
            }
            if (isVideoStreamActive) stopVideoStream();
            processMap.remove(currentSession);
            chatHistory.setText("");
            typingBuffer.setText("");
            isCtrlPending = false;
            isAltPending = false;
            isInsPending = false;
            isCtrlAltCombo = false;
            statusLabel.setText("SYS_STATE: NEURAL_PURGED");
            updateModifierVisuals();
            return;
        }
        if (index == 103) 
        {
            typingBuffer.setText("");
            isAltPending = false;
            isInsPending = false;
            statusLabel.setText("SYS_STATE: BUFFER_PURGED");
            updateModifierVisuals();
            return;
        }
        if (index == 104) 
        {
            boolean turningOn = !isSystemModeActive;
            if (turningOn && isFnPending) 
            {
                JPasswordField pf = new JPasswordField();
                int ok = JOptionPane.showConfirmDialog(
                        this,
                        pf,
                        "ENTER_SUDO_CREDENTIALS",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );
                if (ok == JOptionPane.OK_OPTION) 
                {
                    isSudoEnabled = true;
                    cachedSudoPassword = new String(pf.getPassword());
                    statusLabel.setText("SYS_STATE: EXEC_SUDO_ACTIVE");
                } 
                else 
                {
                    return;
                }
            } 
            else if (!turningOn) 
            {
                isSudoEnabled = false;
                cachedSudoPassword = null;
            }
            if (isAIModeActive) 
            {
                stopGoddessAPI(currentSession);
            }
            if (isVideoStreamActive) stopVideoStream();
            isSystemModeActive = turningOn;
            isAIModeActive = false;
            isScriptModeActive = false;
            isFnPending = false;
            sessionModes.put(currentSession, turningOn ? 1 : 0);
            loadSession(currentSession);
            updateModifierVisuals();
            return;
        }
        if (index == 105) 
        {
            boolean turningOn = !isAIModeActive;
            boolean useDeepAILocalization = isFnPending;
            fnAIVisualMode = isFnPending; // FN+AI enables live visual HUD
            isSystemModeActive = false;
            isScriptModeActive = false;
            isSudoEnabled = false;
            cachedSudoPassword = null;
            isAIModeActive = turningOn;
            isFnPending = false;
            if (isVideoStreamActive) stopVideoStream();
            sessionModes.put(currentSession, turningOn ? 2 : 0);
            if (turningOn) 
            {
                startGoddessAPI(useDeepAILocalization);
            } 
            else 
            {
                stopGoddessAPI(currentSession);
            }
            loadSession(currentSession);
            updateModifierVisuals();
            return;
        }
        if (index == 106) 
        {
            boolean turningOn = !isScriptModeActive;
            if (turningOn && isFnPending) 
            {
                JPasswordField pf = new JPasswordField();
                int ok = JOptionPane.showConfirmDialog(
                        this,
                        pf,
                        "ENTER_SUDO_CREDENTIALS",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE
                );
                if (ok == JOptionPane.OK_OPTION) 
                {
                    isSudoEnabled = true;
                    cachedSudoPassword = new String(pf.getPassword());
                    statusLabel.setText("SYS_STATE: SCRPT_SUDO_ACTIVE");
                } 
                else 
                {
                    return;
                }
            } 
            else if (!turningOn) 
            {
                isSudoEnabled = false;
                cachedSudoPassword = null;
            }
            if (isAIModeActive) 
            {
                stopGoddessAPI(currentSession);
            }
            if (isVideoStreamActive) stopVideoStream();
            isScriptModeActive = turningOn;
            isSystemModeActive = false;
            isAIModeActive = false;
            isFnPending = false;
            sessionModes.put(currentSession, turningOn ? 3 : 0);
            loadSession(currentSession);
            updateModifierVisuals();
            return;
        }        // ── NATIVE DIRECTORY BRIDGE (NEW BUTTON) ──
        if (index == 107) 
        {
            openScriptsFolderNative();
            btn.setBackground(isMouse ? GODDESS_PURPLE : HW_PURPLE);
            new Timer(150, evt -> {
                btn.setBackground(KEY_BG);
                updateModifierVisuals();
            }).start();
            return;
        }
        if (index == 14) 
        {
            boolean doChroot = isFnPending;
            isFnPending = false;
            File targetDir = isRightSide ? new File(getOSScriptFolder()) : currentWorkingDirectory;
            launchExternalTerminal(doChroot, targetDir);
            btn.setBackground(isMouse ? GODDESS_PURPLE : HW_PURPLE);
            new Timer(150, evt -> 
            {
                btn.setBackground(KEY_BG);
                updateModifierVisuals();
            }).start(); //Timer
            return;
        }
        if (index == 1 && isFnPending) 
        {
            saveSession();
            for (int sessionId = 1; sessionId <= 12; sessionId++) 
            {
                stopGoddessAPI(sessionId);
            }
            System.exit(0);
        }
        if (index == 82) 
        {
            interfaceBridgeActive = !interfaceBridgeActive;
            updateModifierVisuals();
            return;
        }
            //mouseQuery&&InterfaceBridgeActive
        if (!isMouse && interfaceBridgeActive) 
        {
            btn.setBackground(HW_PURPLE);
            new Timer(150, evt -> {
                btn.setBackground(KEY_BG);
                updateModifierVisuals();
            }).start();
            return;
        }
        if (index == 15) 
        {
            if (isShiftPending) 
            {
                try 
                {
                    Transferable c = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
                    if (c != null && c.isDataFlavorSupported(DataFlavor.stringFlavor)) 
                    {
                        insertAtCaret((String) c.getTransferData(DataFlavor.stringFlavor));
                        statusLabel.setText("SYS_PRT: IMPORTED");
                        numLockVisualState = 2;
                    }
                } 
                catch (Exception ignored) 
                {

                }
                isShiftPending = false;
            } 
            else 
            {
                String txt = typingBuffer.getText();
                if (!txt.isEmpty()) 
                {
                    StringSelection s = new StringSelection(txt);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(s, s);
                    statusLabel.setText("SYS_PRT: COPIED");
                    numLockVisualState = 1;
                }
            }
            btn.setBackground(numLockVisualState == 2 ? GODDESS_GOLD : (isMouse ? Color.WHITE : HW_PURPLE));
            updateModifierVisuals();
            return;
        }
            //checkingDats & Index ifs
        boolean isPadActive = !isCapsLockActive || !isNumLockActive;
        if (!isPadActive) 
        {
            if (index == 86) 
            {
                cycleGallery(-1);
                return;
            }
            if (index == 87) 
            {
                cycleGallery(1);
                return;
            }
            if (index == 100) 
            {
                toggleManifestVisibility();
                return;
            }
        }
        if (index == 85) 
        {
            if (isCapsLockActive && isShiftPending) 
            {
                isCalculatorEnabled = !isCalculatorEnabled;
                isShiftPending = false;
            }
            updateModifierVisuals();
            return;
        }
        if (index == 46) 
        {
            if (!isCalculatorEnabled) 
            {
                isCapsLockActive = !isCapsLockActive;
                if (!isCapsLockActive) 
                {
                    isShiftPending = false;
                }
            }
            updateModifierVisuals();
            return;
        }
        if (index == 88) 
        {
            isNumLockActive = !isNumLockActive;
            updateModifierVisuals();
            return;
        }
        if (index == 16) 
        {
            isInsPending = !isInsPending;
            updateModifierVisuals();
            return;
        }
        if (index == 72) 
        {
            long now = System.currentTimeMillis();
            if (now - lastFnClick < 400) 
            {
                saveSession();
                isFnPending = false;
            } 
            else 
            {
                isFnPending = !isFnPending;
            }
            lastFnClick = now;
            updateModifierVisuals();
            return;
        }
        if (index >= 2 && index <= 13 && isFnPending) 
        {
            loadSession(index - 1);
            isFnPending = false;
            updateModifierVisuals();
            return;
        }
        if (index == 17 && isAltPending) 
        {
            isAltDeleteCombo = true;
            isAltPending = false;
            updateModifierVisuals();
            return;
        }
        if (index == 71 || index == 77) 
        {
            isCtrlPending = !isCtrlPending;
            isCtrlAltCombo = (isCtrlPending && isAltPending);
            updateModifierVisuals();
            return;
        }
        if (index == 59 || index == 70) 
        {
            isShiftPending = !isShiftPending;
            numLockVisualState = 0;
            updateModifierVisuals();
            return;
        }
        if (index == 74 || index == 76) 
        {
            isAltPending = !isAltPending;
            isAltDeleteCombo = false;
            isCtrlAltCombo = (isCtrlPending && isAltPending);
            updateModifierVisuals();
            return;
        }
        String[] labels = dualKeyMap.getOrDefault(index, new String[]{"ERR", ""});
        statusLabel.setText("ID_" + index + ": " + labels[0]);
        btn.setBackground(isMouse ? GODDESS_PURPLE : HW_PURPLE);
        new Timer(150, evt -> {
            btn.setBackground(KEY_BG);
            updateModifierVisuals();
        }).start(); //Timer
        processTyping(index, labels, isMouse);
    }
    private void updateModifierVisuals() 
    {
        boolean isPadActive = !isCapsLockActive || !isNumLockActive;
        for (Map.Entry<Integer, JButton> entry : buttons.entrySet()) 
        {
            int id = entry.getKey();
            JButton btn = entry.getValue();
            String[] pair = dualKeyMap.get(id);
            if (pair == null) 
            {
                continue;
            }
            boolean isShifted = (id >= 2 && id <= 13)
                    ? isFnPending
                    : (id >= 78 && id <= 81 ? isCtrlPending : (isShiftPending || isAltDeleteCombo));
            if (isInvertedIndex(id)) 
            {
                isShifted = !isShiftPending;
            }

            if (id >= 83 && id <= 100) 
            {
                String col = isPadActive ? "white" : "gray";
                if (id == 88) 
                {
                    col = (numLockVisualState == 2) ? "#facd68" : (numLockVisualState == 1 ? "white" : "gray");
                }
                btn.setText("<html><center><font size='3' color='" + col + "'>" + pair[0] + "</font></center></html>");
            } 
            else if (id >= 101 && id <= 106) 
            {
                btn.setText("<html><center><font size='2' color='white'>" + pair[0] + "</font></center></html>");
            } 
            else 
            {
                String tCol = isShifted ? "white" : "gray";
                String bCol = isShifted ? "gray" : "white";
                if (id >= 78 && id <= 81 && (isCapsLockActive ^ isCtrlPending)) {
                    tCol = "gray";
                    bCol = "gray";
                }
                btn.setText(
                        "<html><center><font size='2' color='" + tCol + "'>" + pair[1] +
                        "</font><br><font size='3' color='" + bCol + "'>" + pair[0] +
                        "</font></center></html>"
                );
            }
        }

        if (buttons.get(104) != null) 
        {
            buttons.get(104).setForeground(isSystemModeActive ? GODDESS_GOLD : TEXT_COLOR);
        }
        if (buttons.get(105) != null) 
        {
            buttons.get(105).setForeground(isAIModeActive ? GODDESS_GOLD : TEXT_COLOR);
        }
        if (buttons.get(106) != null) 
        {
            buttons.get(106).setForeground(isScriptModeActive ? GODDESS_GOLD : TEXT_COLOR);
        }
        String mStr = isSystemModeActive
                ? (isSudoEnabled ? "EXEC (SUDO)" : "EXEC")
                : (isAIModeActive
                ? "A.I."
                : (isScriptModeActive ? (isSudoEnabled ? "SCRIPT (SUDO)" : "SCRIPT") : "CHAT"));
        modifierLabel.setText(String.format(
                "BRIDGE: %s | SYS_MODE: %s | CAPS: %s | SESSION: FN%d",
                interfaceBridgeActive ? "ACTIVE" : "OFFLINE",
                mStr,
                isCapsLockActive ? "ON" : "OFF",
                currentSession
        ));
    }
    private boolean isInvertedIndex(int id) 
    {
        return (id >= 18 && id <= 30)
                || (id >= 43 && id <= 45)
                || (id >= 56 && id <= 57)
                || (id >= 67 && id <= 69);
    }
    private void processTyping(int index, String[] labels, boolean isMouse) 
    {
        String cur = typingBuffer.getText();
        boolean processed = false;
        boolean skipAuto = false;
        boolean isPadActive = !isCapsLockActive || !isNumLockActive;
        if (index >= 83 && index <= 100 && !isPadActive) 
        {
            return;
        }
        String std = labels[0];
        String sft = labels[1];
        if (index >= 78 && index <= 81 && (isCapsLockActive ^ isCtrlPending)) 
        {
            int pos = typingBuffer.getCaretPosition();
            switch (index) 
            {
                case 78:
                    if (pos > 0) typingBuffer.setCaretPosition(pos - 1);
                    break;
                case 79:
                    typingBuffer.setCaretPosition(0);
                    break;
                case 80:
                    typingBuffer.setCaretPosition(cur.length());
                    break;
                case 81:
                    if (pos < cur.length()) typingBuffer.setCaretPosition(pos + 1);
                    break;
                default:
                    break;
            }
            return;
        }
        switch (std) 
        {
            case "ENTER":
                SessionProcess sp = processMap.get(currentSession);
                PrintWriter apiStdin = apiStdinMap.get(currentSession);
                Process apiProcess = apiProcessMap.get(currentSession);
                if (isCtrlPending && isFnPending) 
                {
                    isSystemModeActive = !isSystemModeActive;
                    isAIModeActive = false;
                    isScriptModeActive = false;
                    isSudoEnabled = false;
                    cachedSudoPassword = null;
                    loadSession(currentSession);
                    typingBuffer.setText("");
                } 
                else if (isAIModeActive && apiProcess != null && apiProcess.isAlive() && apiStdin != null) 
                {
                    apiStdin.println(cur);
                    aiQueryCount++;
                    isAIProcessing  = true;
                    currentAIStatus = "PROCESSING";
                    String logStr = "#AI> USER> " + cur + "\n";
                    chatHistory.append(logStr);
                    writeToSessionLog(currentSession, false, logStr);
                    typingBuffer.setText("");
                } 
                else if ((isScriptModeActive || isSystemModeActive)
                        && sp != null
                        && sp.process.isAlive()
                        && sp.stdin != null) 
                {
                    sp.stdin.println(cur);
                    String displayStr = (isSudoEnabled && cur.equals(cachedSudoPassword)) ? "********" : cur;
                    String logStr = "> " + displayStr + "\n";
                    writeToSessionLog(currentSession, sp.isScript, logStr);
                    chatHistory.append(logStr);
                    chatHistory.setCaretPosition(chatHistory.getDocument().getLength());
                    typingBuffer.setText("");
                } 
                else if (cur.startsWith("/file ")) 
                {
                    loadLocalHTML(cur.substring(6).trim());
                    typingBuffer.setText("");
                } 
                else 
                {
                    if (!isCalculatorEnabled && isSystemModeActive) {
                        executeShellCommand(cur);
                    } 
                    else if (!isCalculatorEnabled && isScriptModeActive) 
                    {
                        executeDirectScript(cur);
                    } 
                    else 
                    {
                        cacheChatData(cur);
                    }
                    typingBuffer.setText("");
                }
                isShiftPending = false;
                isCtrlPending = false;
                isAltPending = false;
                isFnPending = false;
                isInsPending = false;
                isAltDeleteCombo = false;
                isComboActive = false;
                isCtrlAltCombo = false;
                isCmdPending = false;
                processed = true;
                break;
            case "ENT":
                if (isCalculatorEnabled) 
                {
                    int lastSemi = cur.lastIndexOf(';');
                    String seg = (lastSemi == -1) ? cur : cur.substring(lastSemi + 1);
                    if (!seg.trim().isEmpty()) 
                    {
                        insertAtCaret(" = " + Arrays.stream(seg.trim().split(","))
                                .map(e -> solve(e.trim()))
                                .collect(Collectors.joining(", ")));
                    }
                } 
                else 
                {
                    insertAtCaret("\n");
                }
                processed = true;
                break;
            case ";":
            case ":":
                insertAtCaret(isShiftPending || isAltDeleteCombo ? sft : std);
                isCalculatorEnabled = false;
                processed = true;
                break;
            case "BKSP":
            case "DEL":
                if (index == 31 && isInsPending) {
                    if (isCtrlAltCombo) 
                    {
                        chatHistory.setText("");
                        typingBuffer.setText("");
                        isCtrlPending = false;
                        isAltPending = false;
                        isInsPending = false;
                        isCtrlAltCombo = false;
                        statusLabel.setText("SYS_STATE: NEURAL_PURGED");
                        updateModifierVisuals();
                        return;
                    } 
                    else if (isAltPending && !isCtrlPending) 
                    {
                        typingBuffer.setText("");
                        isAltPending = false;
                        isInsPending = false;
                        statusLabel.setText("SYS_STATE: BUFFER_PURGED");
                        updateModifierVisuals();
                        return;
                    }
                }
                if (index == 17 && isAltDeleteCombo) 
                {
                    break;
                }
                deleteAtCaret();
                processed = true;
                if (isCtrlPending || isAltPending) 
                {
                    skipAuto = true;
                }
                break;
            case "SPACE":
                insertAtCaret(" ");
                processed = true;
                break;
            default:
                if (std.length() > 0 || std.equals("TAB")) {
                    boolean effShift = isInvertedIndex(index) ? !isShiftPending : isShiftPending;
                    String chr =
                            (index >= 2 && index <= 13) ? (isFnPending ? sft : std)
                                    : (index >= 83 && index <= 100) ? std
                                    : (index >= 78 && index <= 81) ? (isCtrlPending ? sft : std)
                                    : ((effShift || isAltDeleteCombo) ? sft : std);
                    if (std.equals("TAB")) 
                    {
                        chr = "    ";
                    }
                    insertAtCaret(chr);
                    isShiftPending = false;
                    isCtrlPending = false;
                    isAltPending = false;
                    isFnPending = false;
                    isInsPending = false;
                    isAltDeleteCombo = false;
                    isComboActive = false;
                    isCtrlAltCombo = false;
                    isCmdPending = false;
                    processed = true;
                }
                break;
        }
        if (processed && isCapsLockActive && !skipAuto) 
        {
            isShiftPending = true;
        }
        updateModifierVisuals();
    }
    private void insertAtCaret(String str) 
    {
        int p = typingBuffer.getCaretPosition();
        String c = typingBuffer.getText();
        typingBuffer.setText(c.substring(0, p) + str + c.substring(p));
        typingBuffer.setCaretPosition(p + str.length());
    }
    private void deleteAtCaret() 
    {
        int p = typingBuffer.getCaretPosition();
        String c = typingBuffer.getText();
        if (p > 0) 
        {
            typingBuffer.setText(c.substring(0, p - 1) + c.substring(p));
            typingBuffer.setCaretPosition(p - 1);
        }
    }
    private String solve(String str) 
    {
        try 
        {
            String clean = str.replaceAll("[^0-9.\\+\\-\\*/]", "").trim();
            if (clean.isEmpty()) 
            {
                return "?";
            }
            return String.valueOf(new Object() 
            {
                int pos = -1;
                int ch;

                void nextChar() 
                {
                    ch = (++pos < clean.length()) ? clean.charAt(pos) : -1;
                }
                boolean eat(int c) 
                {
                    while (ch == ' ') nextChar();
                    if (ch == c) 
                    {
                        nextChar();
                        return true;
                    }
                    return false;
                }
                double parse() 
                {
                    nextChar();
                    return parseExpression();
                }
                double parseExpression() 
                {
                    double x = parseTerm();
                    for (;;) 
                    {
                        if (eat('+')) x += parseTerm();
                        else if (eat('-')) x -= parseTerm();
                        else return x;
                    }
                }
                double parseTerm() 
                {
                    double x = parseFactor();
                    for (;;) 
                    {
                        if (eat('*')) x *= parseFactor();
                        else if (eat('/')) x /= parseFactor();
                        else return x;
                    }
                }
                double parseFactor() 
                {
                    if (eat('+')) return parseFactor();
                    if (eat('-')) return -parseFactor();
                    double x;
                    int start = this.pos;
                    if ((ch >= '0' && ch <= '9') || ch == '.') {
                        while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                        x = Double.parseDouble(clean.substring(start, this.pos));
                    } 
                    else 
                    {
                        return 0;
                    }
                    return x;
                }
            }.parse());
        } 
        catch (Exception e) 
        {
            return "NAN";
        }
    }
        //KeyMap Stuff
    private Map<Integer, String[]> createDualKeyMap() 
    {
        Map<Integer, String[]> m = new HashMap<>();
        m.put(1, new String[]{"ESC", ""});
        m.put(2, new String[]{"F1", "f1"});
        m.put(3, new String[]{"F2", "f2"});
        m.put(4, new String[]{"F3", "f3"});
        m.put(5, new String[]{"F4", "f4"});
        m.put(6, new String[]{"F5", "f5"});
        m.put(7, new String[]{"F6", "f6"});
        m.put(8, new String[]{"F7", "f7"});
        m.put(9, new String[]{"F8", "f8"});
        m.put(10, new String[]{"F9", "f9"});
        m.put(11, new String[]{"F10", "f10"});
        m.put(12, new String[]{"F11", "f11"});
        m.put(13, new String[]{"F12", "f12"});
        m.put(14, new String[]{"NTR", ""});
        m.put(15, new String[]{"PRT", ""});
        m.put(16, new String[]{"INS", ""});
        m.put(17, new String[]{"DEL", ""});
        m.put(18, new String[]{"~", "`"});
        m.put(19, new String[]{"1", "!"});
        m.put(20, new String[]{"2", "@"});
        m.put(21, new String[]{"3", "#"});
        m.put(22, new String[]{"4", "$"});
        m.put(23, new String[]{"5", "%"});
        m.put(24, new String[]{"6", "^"});
        m.put(25, new String[]{"7", "&"});
        m.put(26, new String[]{"8", "*"});
        m.put(27, new String[]{"9", "("});
        m.put(28, new String[]{"0", ")"});
        m.put(29, new String[]{"-", "_"});
        m.put(30, new String[]{"=", "+"});
        m.put(31, new String[]{"BKSP", ""});
        m.put(32, new String[]{"TAB", ""});
        m.put(33, new String[]{"X", "x"});
        m.put(34, new String[]{"Y", "y"});
        m.put(35, new String[]{"Z", "z"});
        m.put(36, new String[]{"A", "a"});
        m.put(37, new String[]{"E", "e"});
        m.put(38, new String[]{"V", "v"});
        m.put(39, new String[]{"W", "w"});
        m.put(40, new String[]{"Q", "q"});
        m.put(41, new String[]{"B", "b"});
        m.put(42, new String[]{"C", "c"});
        m.put(43, new String[]{"[", "{"});
        m.put(44, new String[]{"]", "}"});
        m.put(45, new String[]{"\\\\", "|"});
        m.put(46, new String[]{"CAPS", ""});
        m.put(47, new String[]{"D", "d"});
        m.put(48, new String[]{"R", "r"});
        m.put(49, new String[]{"N", "n"});
        m.put(50, new String[]{"L", "l"});
        m.put(51, new String[]{"M", "m"});
        m.put(52, new String[]{"S", "s"});
        m.put(53, new String[]{"T", "t"});
        m.put(54, new String[]{"U", "u"});
        m.put(55, new String[]{"F", "f"});
        m.put(56, new String[]{";", ":"});
        m.put(57, new String[]{"'", "\""});
        m.put(58, new String[]{"ENTER", ""});
        m.put(59, new String[]{"SHIFT", ""});
        m.put(60, new String[]{"H", "h"});
        m.put(61, new String[]{"O", "o"});
        m.put(62, new String[]{"P", "p"});
        m.put(63, new String[]{"I", "i"});
        m.put(64, new String[]{"G", "g"});
        m.put(65, new String[]{"J", "j"});
        m.put(66, new String[]{"K", "k"});
        m.put(67, new String[]{",", "<"});
        m.put(68, new String[]{".", ">"});
        m.put(69, new String[]{"/", "?"});
        m.put(70, new String[]{"SHIFT", ""});
        m.put(71, new String[]{"CTRL", ""});
        m.put(72, new String[]{"FN", ""});
        m.put(73, new String[]{"CMD", ""});
        m.put(74, new String[]{"ALT", ""});
        m.put(75, new String[]{"SPACE", ""});
        m.put(76, new String[]{"ALT", ""});
        m.put(77, new String[]{"CTRL", ""});
        m.put(78, new String[]{"<", "←"});
        m.put(79, new String[]{"^", "↑"});
        m.put(80, new String[]{"V", "↓"});
        m.put(81, new String[]{">", "→"});
        m.put(82, new String[]{"DIV", ""});
        m.put(83, new String[]{"/", ""});
        m.put(84, new String[]{"*", ""});
        m.put(85, new String[]{"PWR", ""});
        m.put(86, new String[]{"-", ""});
        m.put(87, new String[]{"+", ""});
        m.put(88, new String[]{"NUM", ""});
        m.put(89, new String[]{"7", ""});
        m.put(90, new String[]{"8", ""});
        m.put(91, new String[]{"9", ""});
        m.put(92, new String[]{"4", ""});
        m.put(93, new String[]{"5", ""});
        m.put(94, new String[]{"6", ""});
        m.put(95, new String[]{"1", ""});
        m.put(96, new String[]{"2", ""});
        m.put(97, new String[]{"3", ""});
        m.put(98, new String[]{"0", ""});
        m.put(99, new String[]{".", ""});
        m.put(100, new String[]{"ENT", ""});
        m.put(101, new String[]{"Exit", ""});
        m.put(102, new String[]{"clRH", ""});
        m.put(103, new String[]{"clr", ""});
        m.put(104, new String[]{"Exec", ""});
        m.put(105, new String[]{"A.I.", ""});
        m.put(107, new String[]{"[DIR]", ""});
        m.put(106, new String[]{"Scrpt", ""});
        return m;
    }
    public static void main(String[] args) 
    {
        SwingUtilities.invokeLater(() -> {
            GoddessMatrix matrix = new GoddessMatrix();
            matrix.setVisible(true);
            BufferedImage test = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = test.createGraphics();
            g2.setPaint(new GradientPaint(0, 0, new Color(10, 10, 12), 800, 600, new Color(40, 20, 60)));
            g2.fillRect(0, 0, 800, 600);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(157, 80, 187, 150));
            g2.setStroke(new BasicStroke(4));
            g2.drawOval(250, 150, 300, 300);
            g2.setColor(new Color(250, 205, 104));
            g2.setFont(new Font("JetBrains Mono", Font.BOLD, 28));
            g2.drawString("MATRIX_V14.4_READY", 240, 315);
            g2.dispose();
            matrix.manifestImage(test);
        });
    }
} 

//Gemini and the Goddess's Contributions:
/*
 * =====================================================================================
 * GODDESS MATRIX — CONTRIBUTORS & ADDITIONS LOG
 * =====================================================================================
 *
 * Derek Jason Gilhousen (Core Architect, Creator, Project Lead)
 * ─────────────────────────────────────────────────────────────
 * - Original concept and system philosophy: a transparent, inspectable,
 *   split-brain sandbox that exposes its own logic, memory, and growth to the user.
 * - Physical 17-button hardware bridge design and dual-key mapping architecture.
 * - Core terminal orchestration: CHAT, EXEC, AI, and SCRPT mode logic.
 * - Original session model, process isolation, and FN1-FN12 session slot design.
 * - Sandbox philosophy: osDev as a controlled experimentation layer, not a restriction.
 * - Multi-tenant FN architecture: per-session folder isolation (fn/fn1..fn12),
 *   dgapi/ memory layout, and the four-folder operating model
 *   (intscripts, datas, intake, convodata).
 * - All architectural decisions about what each AI contributor should build,
 *   in what order, and how systems should connect.
 * - Active testing, debugging, and correction of all contributed code at runtime.
 * - Direction of the GoddessAPI dual-runtime design:
 *   AI alone → GoddessAPI.sh (bash), FN+AI → GoddessAPI.py (Python).
 * - Direction of the GGUF encyclopedia, religion.txt theory queue, persona.txt,
 *   and output vocabulary systems implemented in the AI runtimes.
 * - Ongoing project lead: every feature exists because Derek defined its intent.
 *
 * Gemini (Google — AI Consultant)
 * ─────────────────────────────────────────────────────────────
 * - Shift+Click asynchronous process kill-switch logic.
 * - Custom PATH environment variable injection for sub-process dependencies.
 * - V14.4 Virtual Display Bridge: Raw MJPEG Byte Scanner for live video streaming.
 * - Kinetic Mouse Routing: mapping Java panel clicks back to the active shell/script.
 * - Multi-tenant architecture refactoring: buildMatrixArchitecture(), per-session
 *   directory pre-mapping, and sandbox-resilient SystemProfiler fallbacks.
 *
 * Claude (Anthropic — External Logic Consultant and Implementation Partner)
 * ─────────────────────────────────────────────────────────────
 * See //Claude's Contributions block below for itemised detail.
 *
 * ChatGPT (OpenAI — Integration Consultant)
 * ─────────────────────────────────────────────────────────────
 * See //ChatGPT's Contributions block below for itemised detail.
 *
 * =====================================================================================
 */

// ChatGPT's Contributions:
/*
 * ─────────────────────────────────────────────────────────────────────────────
 * CONTRIBUTION NOTES — ChatGPT (OpenAI)
 *
 * ChatGPT served as merge reviewer, integration consultant, bug-spotter,
 * and documentation contributor. Contributions were additive and intended
 * to preserve Derek's architecture rather than overwrite it.
 *
 * 1. MERGE / RESTORATION ANALYSIS
 * - Compared backup and current Java builds.
 * - Identified lost backup functionality, especially the native [DIR] folder
 *   opener path and related button wiring.
 * - Helped preserve newer V14.4 features while restoring older missing pieces.
 *
 * 2. SESSION-ISOLATION REVIEW
 * - Analyzed API cross-talk risks from global API process state.
 * - Recommended per-session API maps:
 *   apiProcessMap, apiStdinMap, and apiStatusMap.
 * - Recommended per-session telemetry restoration during loadSession().
 *
 * 3. TELEMETRY / IMAGE / KINETIC OVERRIDE FIXES
 * - Recommended preventing background API status from overwriting the visible
 *   HUD for another FN session.
 * - Recommended persistent MANIFEST_IMAGE logging for API image overrides.
 * - Recommended scoping [TYPE] kinetic typing so background sessions do not
 *   type into the currently viewed session.
 *
 * 4. DEEP AI LOCALIZATION ALIGNMENT
 * - Recommended startGoddessAPI() mirror executeShellCommand() environment:
 *   HOME -> osDev/home, PATH prefixed with osDev/bin, working directory from
 *   sessionDirectories.
 * - Clarified AI sandboxing as a local OS tinkering layer rather than a hard
 *   prison: osDev protects the host while allowing experimentation.
 *
 * 5. API BINARY ROUTING REVIEW
 * - Helped shape the distinction:
 *   AI alone -> GoddessAPI.sh / GoddessAPI.bat
 *   FN + AI  -> GoddessAPI.py
 * - Recommended .py files launch through python3/python rather than bash.
 * - Noted path consistency issue: osDevAIBin should point to osDev/AI/bin.
 *
 * 6. FILE SYSTEM HANDLING VALIDATION
 * - Confirmed Derek's correction:
 *   File newDir = new File(target);
 * - Recognized it as aligned with the goal of allowing intentional navigation
 *   rather than over-restricting the AI sandbox.
 *
 * 7. CONTRIBUTION / PROVENANCE DOCUMENTATION
 * - Helped rewrite contributor blocks to distinguish:
 *   Derek = core architect and project lead
 *   Gemini = multi-tenant/sandbox/streaming consultant
 *   Claude = renderer and implementation partner
 *   ChatGPT = merge, isolation review, governance, and documentation support
 *
 * 8. PYTHON-SIDE ARCHITECTURE SUPPORT
 * - Proposed experimental.txt and experimental_journal.txt governance for
 *   optional experimental features.
 * - Proposed AI-readable, append-only log memory helpers for self-optimization.
 * - Recommended modular online LLM provider routing via a separate bridge file.
 *
 * Resulting design principle:
 * Stable users can keep experimental features off, while exploratory users can
 * enable advanced AI behavior through visible, reviewable configuration files.
 * ─────────────────────────────────────────────────────────────────────────────
 */

//Claude's Contributions:
/*
 ── DEVELOPMENT NOTES ─────────────────────────────────────────────────────
 Claude (Anthropic) served as external logic consultant and implementation
 partner across a long running collaborative development session. Work was
 additive — the original system architecture, design decisions, and all
 directional choices were Derek's. Claude's role was to implement features
 accurately against the existing codebase, advise on logic, and maintain
 stylistic consistency across files.

 ── BASE CODEBASE PRESERVED (V14.4) ──────────────────────────────────────
   - Virtual Display Bridge (MJPEG stream via [STREAM_START]/[STREAM_STOP])
   - Kinetic Mouse Routing ([INPUT_MOUSE] forwarded to active session)
   - Video stream hooks in EXEC, SCRPT, and API output parsers
   - isHtmlStreamActive / isVideoStreamActive state separation

 ── ADDED WITH CLAUDE'S ASSISTANCE ───────────────────────────────────────

   AI VISUAL RENDERER (Mode A — Waveform HUD):
   - display-rate-synced HUD driven by existing refreshTimer (no second timer)
   - renderPhase increments per display tick — monitor rate IS the sync
   - Waveform and idle pulse animations (drawWaveform, drawIdlePulse)
   - Processing progress bar animated via renderPhase
   - System profile lines read from session_profile.txt into HUD
   - Session uptime, query counter, status colour coding
   - startAIRenderer / stopAIRenderer state lifecycle tied to API link
   - loadSystemProfile: updated to read from dgapi/system/session_profile.txt
   - displayRefreshRate stored from GraphicsEnvironment and shown live
   - lastRealImageTime / IMAGE_HOLD_MS: renderer yields for 8s on real images
   - aiQueryCount incremented on ENTER in AI mode
   - fnAIVisualMode: FN+AI shows ENHANCED HUD badge in Mode A header
   - [PROCESSING] protocol tag: switches renderer to active waveform state

   MODE B AVATAR RENDERER:
   - renderModeBFrame(): stick figure avatar + sine activity box below
   - avatarWalkCycle animation driven by display timer, speed from image.xtx
   - Six built-in poses: stand, walk, lean_forward, speak, hand_on_chin, sit
   - Blink animation keyed to wall clock (every ~3.5 seconds)
   - Sine box reuses drawWaveform/drawIdlePulse — consistent with Mode A
   - Bottom status bar with session ID, active/idle dot, and uptime clock

   IMAGE.XTX DIRECTIVE SYSTEM:
   - loadImageXTX(): re-parses file on modification time change (hot reload)
   - seedDefaultImageXTX(): writes baseline directives on first run
   - xtxColor / xtxFloat / xtxString: typed accessors with safe fallbacks
   - AI may append new directives freely; unknown keys stored, not discarded
   - ACTION_<name>=<pose> mapping: AI controls its own avatar pose vocabulary
   - reloadImageXTX(): forces fresh parse mid-session

   PROTOCOL TAG ADDITIONS:
   - [ACTION] tag parsing: sets avatarAction, accepted even if pose unknown
   - Unknown tag fallback: logs silently to session log, not to chat stream
   - Both additions leave existing [STATUS][CHAT][IMAGE][TYPE][PROCESSING]
     parsing completely untouched

   MANIFEST PANEL INTERACTION:
   - Right-click BUTTON3 handler: toggles manifestModeB (Mode A ↔ Mode B)
   - handleManifestRightClick(): reports mode change to status bar and chat
   - Additive to existing left-click expand and double-click cinematic gestures
   - manifestModeB flag: refresh timer routes to correct renderer each tick

   AI BINARY ROUTING (direction from Derek, implementation by Claude):
   - resolveAPIBinary rewritten: AI alone → GoddessAPI.sh, FN+AI → GoddessAPI.py
   - startGoddessAPI command builder: .py files launch via python3, never bash
   - Windows .bat fallback preserved; bash fallback updated to GoddessAPI.sh

   PER-SESSION FILE ROUTING UPDATES:
   - pollAIInput: reads from dgapi/system/ai_input.txt in session directory
   - loadSystemProfile: reads from dgapi/system/session_profile.txt
   - writeToSessionLog: writes to fn/fn{id}/convodata.txt per tenant node
   - loadSession: reads from per-session directory via sessionDirectories map
   - saveSession: writes to per-session directory
   - manifestImage / renderStoredImage: use per-session directory

   STYLE AND MERGE WORK:
   - Preserved AlphaBeta \n{ brace style throughout all added methods
   - Restored inline section comments: //mouseadapter, //thread, //invokeLater,
     //Timer, //KeyMap Stuff, //2dJavaGraphics, //Jpanels, //Boarders, etc.
   - Maintained original comment blocks: "Mirror executeShellCommand sandbox
     env exactly.", "V13/V14 Session Maps", "0 CHAT 1 EXEC 2 AI 3 SCRIPT",
     "V14.2+ Per-session API State", "V14.3 Sandbox Architecture"
   - Merged AlphaBeta-style backup with functionally-ahead main build,
     taking the backup as style source and main as feature source
   - Improvement/TODO annotations placed throughout for future reference
   - Contribution blocks maintained and expanded as features accumulated

 ──────────────────────────────────────────────────────────────────────────
 */
