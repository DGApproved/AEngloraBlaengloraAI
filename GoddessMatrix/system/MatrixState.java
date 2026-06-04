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
 * HOOK ARCHITECTURE:
 *   Every Matrix capability is exposed as a @FunctionalInterface hook field.
 *   Hooks are wired at Matrix startup (GoddessMatrix.java) to the actual
 *   implementations. Modular features read the hooks they need from MatrixState
 *   and call them — they never reference system/* or assets/* classes directly.
 *
 *   This means:
 *   - The Matrix never changes to support a new modular feature.
 *   - A modular feature uses only what it needs, ignores the rest.
 *   - Any Java program dropped into the modular/ folder can be a full citizen.
 *
 * HOOK CATEGORIES:
 *   KeyEventHook      — receive all keyboard events before Matrix processes them
 *   ErrorHook         — log a crash as a clickable [rawdat_crash_log] in chat
 *   CalculatorHook    — ask the calculator to evaluate an expression
 *   ChatHook          — append text to the Matrix chat display
 *   AISendHook        — send text to the active AI session's Python stdin
 *   AIResponseHook    — receive AI stdout lines (set to receive narration etc.)
 *   StatusHook        — write to the Matrix status bar
 *   ShellHook         — execute a shell command via TerminalRunner
 *   ManifestHook      — inject a rendered frame into the ImageViewer manifest
 *   SessionInfoHook   — query current session number and directory
 *
 * Design Rules:
 * - NO direct dependency on modular/*
 * - ONLY references system/ and assets/
 * - everything optional is nullable
 *
 * Contributors:
 *   Derek Jason Gilhousen — architecture, hook philosophy, all design decisions
 *   Claude (Anthropic)    — hook interface definitions, wiring pattern
 */

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class MatrixState {

    // ─────────────────────────────────────────────
    // OS / ENVIRONMENT
    // Detected once at construction from System.getProperty("os.name").
    // All downstream code reads these flags — no inline os.name calls needed.
    // ApiBridge.getOSScriptFolderFile() uses isWindows/isMac to select
    // scripts/Windows, scripts/MacOSY, or scripts/Linux automatically.
    // ─────────────────────────────────────────────
    public String  osName;
    public boolean isWindows;
    public boolean isMac;
    public boolean isLinux;
    public String  shellName;

    public MatrixState() {
        String raw = System.getProperty("os.name", "").toLowerCase();
        isWindows = raw.contains("win");
        isMac     = raw.contains("mac") || raw.contains("darwin");
        isLinux   = !isWindows && !isMac;
        osName    = raw;
        shellName = isWindows ? "cmd" : "bash";
        // Call wireHooks() after UI components are initialized.
    }

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

    public boolean imageViewerMaximized = false;
    public modular.game.IceSandbox sandboxInstance = null;

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

    public volatile int manifestViewMode = 0; // 0=Waveform, 1=Avatar, 2=Calculator, 3=Game
    public boolean isCalculatorUnlocked = false;

    public volatile String avatarAction = "IDLE";
    public volatile float avatarWalkCycle = 0f;
    public Map<String, String> imageXTX = new HashMap<>();
    public long imageXTXLastModified = 0L;

    // ─────────────────────────────────────────────
    // LOGIC LOOP
    // ─────────────────────────────────────────────
    public long lastLogicTick = 0;

    // ─────────────────────────────────────────────
    // GAME MODE
    // ─────────────────────────────────────────────
    public boolean isGameModeActive = false;

    // ─────────────────────────────────────────────
    // OPTIONAL ASSETS (safe nullable)
    // ─────────────────────────────────────────────
    public assets.TerminalRunner terminalRunner;
    public assets.ApiBridge apiBridge;

    // ─────────────────────────────────────────────
    // MODULAR BRIDGES (nullable, set by modular layer at runtime)
    // ─────────────────────────────────────────────
    public Object llmBridge    = null;
    public Object gameProtocol = null;

    // ═════════════════════════════════════════════
    // MODULAR CAPABILITY HOOKS
    //
    // Wired at Matrix startup by GoddessMatrix.java.
    // Modular features read what they need and call it.
    // Null = capability not available (feature gracefully skips it).
    // Never throw from a hook — the Matrix must stay running.
    // ═════════════════════════════════════════════

    // ─────────────────────────────────────────────
    // KEY EVENT HOOK
    // Called first for every KeyEvent before Matrix processes it.
    // Return true  → consumed, Matrix sees nothing.
    // Return false → Matrix processes normally.
    // Clear to null on module stop.
    // ─────────────────────────────────────────────
    @FunctionalInterface
    public interface KeyEventHook {
        boolean onKeyEvent(java.awt.event.KeyEvent e);
    }
    public volatile KeyEventHook keyHook = null;

    // ─────────────────────────────────────────────
    // ERROR HOOK
    // Logs an exception as a clickable [rawdat_crash_log] in the chat display.
    // Wired to: chatHistory.logModularCrash(moduleName, e)
    // Use for any non-fatal modular fault.
    //
    // Example:
    //   state.errorHook.log("Game Physics", e);
    // ─────────────────────────────────────────────
    @FunctionalInterface
    public interface ErrorHook {
        void log(String moduleName, Exception e);
    }
    public volatile ErrorHook errorHook = null;

    // ─────────────────────────────────────────────
    // CALCULATOR HOOK
    // Evaluates a mathematical expression using the Matrix calculator engine.
    // Returns the result string. Returns "SYS_OFFLINE" if calculator is off.
    // Wired to: CalculatorEngine.solve(expression)
    //
    // Example:
    //   String result = state.calculatorHook.calculate("sqrt(2) * pi");
    // ─────────────────────────────────────────────
    @FunctionalInterface
    public interface CalculatorHook {
        String calculate(String expression);
    }
    public volatile CalculatorHook calculatorHook = null;

    // ─────────────────────────────────────────────
    // CHAT HOOKS
    // Append text to the Matrix chat display.
    // Wired to chatHistory methods.
    //
    // chatSystemHook  → chatHistory.appendSystem(text)  — SYSTEM> prefix, purple
    // chatErrorHook   → chatHistory.appendError(text)   — ERROR> prefix, red
    // chatRawHook     → chatHistory.appendRaw(text)     — no prefix, plain
    //
    // Example:
    //   state.chatSystemHook.append("GAME: Player picked up book");
    // ─────────────────────────────────────────────
    @FunctionalInterface
    public interface ChatAppendHook {
        void append(String text);
    }
    public volatile ChatAppendHook chatSystemHook = null;
    public volatile ChatAppendHook chatErrorHook  = null;
    public volatile ChatAppendHook chatRawHook    = null;

    // ─────────────────────────────────────────────
    // AI SEND HOOK
    // Sends text directly to the active AI session's Python stdin.
    // This is the C key conduit — bypasses the chat log roundtrip.
    // Wired to: apiStdinMap.get(currentSession).println(text)
    //
    // Example:
    //   state.aiSendHook.send("What is on this page?");
    // ─────────────────────────────────────────────
    @FunctionalInterface
    public interface AISendHook {
        void send(String text);
    }
    public volatile AISendHook aiSendHook = null;

    // ─────────────────────────────────────────────
    // AI RESPONSE HOOK
    // Called by ApiBridge when a line arrives from GoddessAPI stdout
    // that starts with [GAME_NARRATE:] or any tag the module registered for.
    // The module sets this to receive narration, world updates, etc.
    // Wired in ApiBridge.parseAPIOutput() alongside existing tag routing.
    //
    // Example:
    //   state.aiResponseHook = line -> {
    //       if (line.startsWith("[GAME_NARRATE:")) { ... }
    //   };
    // ─────────────────────────────────────────────
    @FunctionalInterface
    public interface AIResponseHook {
        void onLine(String line);
    }
    public volatile AIResponseHook aiResponseHook = null;

    // ─────────────────────────────────────────────
    // STATUS HOOK
    // Writes to the Matrix status bar (bottom-left label).
    // Wired to: statusLabel.setText(text)
    //
    // Example:
    //   state.statusHook.set("GAME: Walking north");
    // ─────────────────────────────────────────────
    @FunctionalInterface
    public interface StatusHook {
        void set(String text);
    }
    public volatile StatusHook statusHook = null;

    // ─────────────────────────────────────────────
    // SHELL HOOK
    // Executes a shell command via TerminalRunner.
    // Wired to: terminalRunner.executeShellCommand(command)
    //
    // Example:
    //   state.shellHook.execute("python3 myscript.py");
    // ─────────────────────────────────────────────
    @FunctionalInterface
    public interface ShellHook {
        void execute(String command);
    }
    public volatile ShellHook shellHook = null;

    // ─────────────────────────────────────────────
    // MANIFEST RENDER HOOK
    // Called by ImageViewer each render tick when manifestViewMode == 3 (Game).
    // The module returns a BufferedImage to display in the manifest panel.
    // Return null to let the ImageViewer render its default content.
    // Wired in ImageViewer.refreshTick() alongside existing view modes.
    //
    // Example:
    //   state.manifestRenderHook = (w, h) -> myGameFrame.renderToImage(w, h);
    // ─────────────────────────────────────────────
    @FunctionalInterface
    public interface ManifestRenderHook {
        BufferedImage render(int width, int height);
    }
    public volatile ManifestRenderHook manifestRenderHook = null;

    // ─────────────────────────────────────────────
    // SESSION INFO HOOK
    // Provides the current session number and its directory to the module.
    // The module uses this to know where to read/write its own data files
    // without needing to reference MatrixState's session maps directly.
    // Wired at startup.
    //
    // Example:
    //   int fn = state.sessionInfoHook.currentSession();
    //   File dir = state.sessionInfoHook.sessionDirectory();
    // ─────────────────────────────────────────────
    public interface SessionInfoHook {
        int  currentSession();
        File sessionDirectory();
    }
    public volatile SessionInfoHook sessionInfoHook = null;

    // ─────────────────────────────────────────────
    // HOOK WIRING HELPER
    // Called once by GoddessMatrix at startup to wire all hooks.
    // Keeps the wiring in one place. Each hook is null-safe — if the
    // backing object isn't ready, the hook stays null.
    // ─────────────────────────────────────────────
    public void wireHooks()
    {
        // Error reporting
        if (chatHistory != null)
            errorHook = (name, e) -> chatHistory.logModularCrash(name, e);

        // Calculator
        calculatorHook = expression -> assets.CalculatorEngine.solve(expression);

        // Chat display
        if (chatHistory != null) {
            chatSystemHook = text -> chatHistory.appendSystem(text);
            chatErrorHook  = text -> chatHistory.appendError(text);
            chatRawHook    = text -> chatHistory.appendRaw(text);
        }

        // AI send — always routes to current session's stdin
        aiSendHook = text -> {
            java.io.PrintWriter stdin = apiStdinMap.get(currentSession);
            if (stdin != null) { stdin.println(text); stdin.flush(); }
        };

        // Status bar
        if (statusLabel != null)
            statusHook = text ->
                javax.swing.SwingUtilities.invokeLater(() -> statusLabel.setText(text));

        // Shell execution
        if (terminalRunner != null)
            shellHook = command -> terminalRunner.executeShellCommand(command);

        // Session info
        sessionInfoHook = new SessionInfoHook() {
            public int  currentSession()   { return MatrixState.this.currentSession; }
            public File sessionDirectory() {
                return sessionDirectories.getOrDefault(currentSession, aiHomeDirectory);
            }
        };

        // manifestRenderHook and aiResponseHook are set by the modular feature,
        // not by the Matrix — the Matrix calls them, doesn't provide them.
    }
}
