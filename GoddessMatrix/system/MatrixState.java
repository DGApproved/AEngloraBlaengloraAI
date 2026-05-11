package system;

/*
 * MatrixState.java
 *
 * Central shared state container for Goddess Matrix.
 *
 * Purpose:
 * - holds all runtime state shared across system classes
 * - avoids circular dependencies
 * - acts as "motherboard memory bus"
 *
 * Design Rules:
 * - NO direct dependency on modular/*
 * - ONLY references system/ and assets/
 * - everything optional is nullable
 */

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class MatrixState {

    // ─────────────────────────────────────────────
    // OS / ENVIRONMENT
    // ─────────────────────────────────────────────
    public String osName = "unknown";
    public boolean isWindows = false;
    public boolean isMac = false;
    public boolean isLinux = true;
    public String shellName = "bash";

    // ─────────────────────────────────────────────
    // CORE PATHS
    // ─────────────────────────────────────────────
    public File aiHomeDirectory;
    public File currentWorkingDirectory;

    public File htmlDirectory;
    public File fnBaseDirectory;
    public File scriptRootDirectory;

    // ─────────────────────────────────────────────
    // SANDBOX (osDev)
    // ─────────────────────────────────────────────
    public File osDevDir;
    public File osDevBin;
    public File osDevHome;
    public File osDevAIDir;
    public File osDevAIBin;

    // ─────────────────────────────────────────────
    // UI REFERENCES (system layer)
    // ─────────────────────────────────────────────
    public UIWindow uiWindow;
    public Keyboard keyboard;
    public ChatHistory chatHistory;
    public ImageViewer imageViewer;

    // ─────────────────────────────────────────────
    // SWING COMPONENT REFERENCES
    // ─────────────────────────────────────────────
    public JPanel matrixPanel;
    public JPanel imageManifest;

    public JLabel statusLabel;
    public JLabel modifierLabel;
    public JLabel aiStatusLabel;

    public JEditorPane chatTextArea;
    public JTextPane typingBuffer;

    // ─────────────────────────────────────────────
    // MODE STATE
    // ─────────────────────────────────────────────
    public boolean interfaceBridgeActive = false;

    public boolean isSystemModeActive = false; // EXEC
    public boolean isAIModeActive = false;     // AI
    public boolean isScriptModeActive = false; // SCRIPT
    public boolean isCloudModeActive = false;  // CLOUD

    public boolean isUserModeActive = false;
    public boolean isSudoEnabled = false;
    public String cachedSudoPassword = null;

    // ─────────────────────────────────────────────
    // MODIFIER STATE
    // ─────────────────────────────────────────────
    public boolean isShiftPending = false;
    public boolean isCtrlPending = false;
    public boolean isAltPending = false;
    public boolean isFnPending = false;
    public boolean isInsPending = false;
    public boolean isCmdPending = false;

    public boolean isAltDeleteCombo = false;
    public boolean isComboActive = false;
    public boolean isCtrlAltCombo = false;

    public boolean isCapsLockActive = true;
    public boolean isNumLockActive = true;
    public boolean isCalculatorEnabled = false;

    public int numLockVisualState = 0;

    // ─────────────────────────────────────────────
    // TIMING / INPUT
    // ─────────────────────────────────────────────
    public long lastKeyPressTime = 0;
    public int lastKeyCode = -1;
    public long lastFnClick = 0;
    public int debounceMs = 25;
    public boolean debounceEnabled = true;

    // ─────────────────────────────────────────────
    // SESSION STATE
    // ─────────────────────────────────────────────
    public int currentSession = 1;

    // 0 CHAT, 1 EXEC, 2 AI, 3 SCRIPT, 4 CLOUD
    public final Map<Integer, Integer> sessionModes = new HashMap<>();
    public final Map<Integer, File> sessionDirectories = new HashMap<>();

    // ─────────────────────────────────────────────
    // PROCESS / API STATE
    // ─────────────────────────────────────────────
    public final Map<Integer, SessionProcess> processMap = new HashMap<>();

    public final Map<Integer, Process> apiProcessMap = new HashMap<>();
    public final Map<Integer, PrintWriter> apiStdinMap = new HashMap<>();
    public final Map<Integer, String> apiStatusMap = new HashMap<>();

    // ─────────────────────────────────────────────
    // KEYBOARD MAPPING
    // ─────────────────────────────────────────────
    public Map<Integer, JButton> buttons = new HashMap<>();
    public Map<Integer, String[]> dualKeyMap = new HashMap<>();

    // ─────────────────────────────────────────────
    // IMAGE / MANIFEST STATE
    // ─────────────────────────────────────────────
    public final List<String> sessionImagePaths = new ArrayList<>();
    public int galleryIndex = -1;

    public BufferedImage activeBuffer;
    public boolean manifestVisible = true;
    public boolean isHtmlStreamActive = false;
    public volatile boolean isVideoStreamActive = false;

    public int manifestState = 0;

    // ─────────────────────────────────────────────
    // AI VISUAL STATE
    // ─────────────────────────────────────────────
    public int displayRefreshRate = 60;

    public volatile boolean isAIProcessing = false;
    public volatile long aiSessionStartMs = 0;
    public volatile int aiQueryCount = 0;
    public volatile float renderPhase = 0f;
    public volatile String currentAIStatus = "OFFLINE";

    public volatile boolean fnAIVisualMode = false;
    public volatile long lastRealImageTime = 0;

    // Mode B Avatar
    public volatile boolean manifestModeB = false;
    public volatile String avatarAction = "IDLE";
    public volatile float avatarWalkCycle = 0f;

    // image.xtx directives
    public Map<String, String> imageXTX = new HashMap<>();
    public long imageXTXLastModified = 0L;

    // ─────────────────────────────────────────────
    // LOGIC LOOP
    // ─────────────────────────────────────────────
    public long lastLogicTick = 0;

    // ─────────────────────────────────────────────
    // GAME MODE
    // ─────────────────────────────────────────────
    // Set true by modular.Game when IceSandbox is embedded in the manifest panel.
    // Keyboard.installHardwareBridge uses this to pass game keys (WASD/SPACE/ESC/E)
    // through to the game canvas rather than consuming them into Matrix buttons.
    public boolean isGameModeActive = false;

    // ─────────────────────────────────────────────
    // OPTIONAL ASSETS (safe nullable)
    // ─────────────────────────────────────────────
    public assets.TerminalRunner terminalRunner;
    public assets.ApiBridge apiBridge;

    // ─────────────────────────────────────────────
    // MODULAR BRIDGES (nullable, set by modular layer at runtime)
    // ─────────────────────────────────────────────
    public Object llmBridge    = null;   // set by modular.LLMs on launch
    public Object gameProtocol = null;   // set by modular.Game on launch
                                         // cast to modular.game.GameProtocol
                                         // by ApiBridge when forwarding game tags
}
