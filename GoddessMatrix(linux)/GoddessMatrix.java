/*
 * Goddess Input Matrix [Modular Build] v15.0-dev
 *
 * Root entry point / motherboard conductor.
 *
 * SYSTEM IDENTITY:
 * A Java-based local orchestration shell for:
 * - multi-session execution (FN1–FN12)
 * - AI runtime bridging (local + external)
 * - script + terminal control
 * - visual manifest rendering
 * - modular feature expansion
 *
 * ARCHITECTURE LAYOUT:
 * ├── GoddessMatrix.java        (Root Conductor)
 * ├── system/                  (Required runtime core)
 * │   ├── MatrixState.java
 * │   ├── SessionProcess.java
 * │   ├── UIWindow.java
 * │   ├── Keyboard.java
 * │   ├── ChatHistory.java
 * │   └── ImageViewer.java
 * ├── assets/                  (Required support layer)
 * │   ├── MatrixConfig.java
 * │   ├── CalculatorEngine.java
 * │   ├── TerminalRunner.java
 * │   └── ApiBridge.java
 * └── modular/                 (Optional expansion layer)
 *     ├── Update.java
 *     ├── LLMs.java
 *     ├── Terminal.java
 *     ├── Game.java
 *     └── App.java
 *
 * CORE RESPONSIBILITIES:
 * - Boot entry (main)
 * - OS detection + shell mapping
 * - Root + sandbox directory initialization
 * - FN1–FN12 tenant architecture construction
 * - Creation + wiring of system modules
 * - Launch of UIWindow
 *
 * SYSTEM MODES:
 * - CHAT   (default)
 * - EXEC   (sandbox terminal)
 * - AI     (ApiBridge runtime)
 * - SCRIPT (host execution)
 * - CLOUD  (external LLM routing via modular layer)
 *
 * DESIGN PRINCIPLES:
 * - system/ = required, must always compile
 * - assets/ = required helpers, strongly typed
 * - modular/ = optional, failure-isolated expansion cards
 * - MatrixState = central memory bus (no circular deps)
 * - UI never requires root privileges
 *
 * NOTE:
 * Modular classes are loaded via reflection.
 * Missing modules must NEVER break core boot.
 *
 * See CONTRIBUTORS.md for full contribution history.
 */

import javax.swing.SwingUtilities;
import java.io.File;
import java.util.Locale;

import system.MatrixState;
import system.UIWindow;
import system.Keyboard;
import system.ChatHistory;
import system.ImageViewer;

import assets.TerminalRunner;
import assets.ApiBridge;

public class GoddessMatrix {

    public static final String VERSION = "V14.4+ Modular Split";

    public static final String FN_BASE_DIR    = "fn";
    public static final String SCRPT_FOLDER   = "scrpt";
    public static final String MASTER_LOG     = "convodata.txt";
    public static final String STORAGE_FOLDER = "convodata";
    public static final String AI_INPUT_FILE  = "ai_input.txt";
    public static final String HTML_FOLDER    = "HTML";
    public static final String OS_DEV_FOLDER  = "osDev";

    public static final String APACHE_LOG_PATH = "/var/log/apache2/access.log";
    public static final String API_OFFLINE     = "[API_OFFLINE]";

    private final MatrixState state;

    private UIWindow uiWindow;
    private Keyboard keyboard;
    private ChatHistory chatHistory;
    private ImageViewer imageViewer;

    public GoddessMatrix() {
        state = new MatrixState();

        detectOperatingSystem();
        initializeRootPaths();
        initializeSandboxPaths();
        buildMatrixArchitecture();

        createCoreModules();
        wireCoreModules();

        uiWindow.showWindow();
    }

    private void detectOperatingSystem() {
        String os = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);

        state.osName = os;
        state.isWindows = os.contains("win");
        state.isMac = os.contains("mac");
        state.isLinux = !state.isWindows && !state.isMac;
        state.shellName = state.isWindows ? "cmd.exe" : "bash";
    }

    private void initializeRootPaths() {
        state.aiHomeDirectory = new File(System.getProperty("user.dir"));
        state.currentWorkingDirectory = state.aiHomeDirectory;

        state.htmlDirectory = new File(state.aiHomeDirectory, HTML_FOLDER);
        state.fnBaseDirectory = new File(state.aiHomeDirectory, FN_BASE_DIR);
        state.scriptRootDirectory = new File(state.aiHomeDirectory, SCRPT_FOLDER);

        state.htmlDirectory.mkdirs();
        state.fnBaseDirectory.mkdirs();
        state.scriptRootDirectory.mkdirs();
    }

    private void initializeSandboxPaths() {
        state.osDevDir = new File(state.aiHomeDirectory, OS_DEV_FOLDER);
        state.osDevBin = new File(state.osDevDir, "bin");
        state.osDevHome = new File(state.osDevDir, "home");
        state.osDevAIDir = new File(state.osDevDir, "AI");
        state.osDevAIBin = new File(state.osDevAIDir, "bin");

        state.osDevBin.mkdirs();
        state.osDevHome.mkdirs();
        state.osDevAIBin.mkdirs();

        new File(state.osDevDir, "proc").mkdirs();
        new File(state.osDevDir, "sys").mkdirs();
        new File(state.osDevDir, "dev").mkdirs();
        new File(state.osDevDir, "dev/pts").mkdirs();
    }

    private void buildMatrixArchitecture() {
        for (int i = 1; i <= 12; i++) {
            File sessionDir = new File(
                    state.fnBaseDirectory,
                    "fn" + i
            );

            sessionDir.mkdirs();
            state.sessionDirectories.put(i, sessionDir);

            new File(sessionDir, "dgapi/system").mkdirs();
            new File(sessionDir, "dgapi/datas").mkdirs();
            new File(sessionDir, "dgapi/intake").mkdirs();
            new File(sessionDir, "dgapi/intake/books").mkdirs();
            new File(sessionDir, "dgapi/intake/code").mkdirs();
            new File(sessionDir, "dgapi/intake/reference").mkdirs();
            new File(sessionDir, "dgapi/intake/processed").mkdirs();
            new File(sessionDir, "dgapi/virtual").mkdirs();
            new File(sessionDir, "dgapi/resources").mkdirs();
        }
    }

    private void createCoreModules() {
        chatHistory = new ChatHistory(state);
        imageViewer = new ImageViewer(state);
        keyboard = new Keyboard(state);
        uiWindow = new UIWindow(state, keyboard, chatHistory, imageViewer);

        state.terminalRunner = new TerminalRunner(state);
        state.apiBridge = new ApiBridge(state);
    }

    private void wireCoreModules() {
        state.uiWindow = uiWindow;
        state.keyboard = keyboard;
        state.chatHistory = chatHistory;
        state.imageViewer = imageViewer;

        chatHistory.initialize();
        imageViewer.initialize();
        keyboard.initialize();
        uiWindow.initialize();

        uiWindow.installHardwareBridge();
        uiWindow.installDragAndDrop();
        uiWindow.loadInitialSession();
    }

    public void shutdown() {
        try {
            if (imageViewer != null) {
                imageViewer.stopVideoStream();
                imageViewer.stopRenderer();
            }

            if (uiWindow != null) {
                uiWindow.saveSession();
            }

            for (int sessionId = 1; sessionId <= 12; sessionId++) {
                if (uiWindow != null) {
                    uiWindow.stopGoddessAPI(sessionId);
                }
            }
        } finally {
            System.exit(0);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(GoddessMatrix::new);
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
 * split-brain sandbox that exposes its own logic, memory, and growth to the user.
 * - Physical 17-button hardware bridge design and dual-key mapping architecture.
 * - Core terminal orchestration: CHAT, EXEC, AI, and SCRPT mode logic.
 * - Original session model, process isolation, and FN1-FN12 session slot design.
 * - Sandbox philosophy: osDev as a controlled experimentation layer, not a restriction.
 * - Multi-tenant FN architecture: per-session folder isolation (fn/fn1..fn12),
 * dgapi/ memory layout, and the four-folder operating model
 * (intscripts, datas, intake, convodata).
 * - All architectural decisions about what each AI contributor should build,
 * in what order, and how systems should connect.
 * - Active testing, debugging, and correction of all contributed code at runtime.
 * - Direction of the GoddessAPI dual-runtime design:
 * AI alone → GoddessAPI.sh (bash), FN+AI → GoddessAPI.py (Python).
 * - Direction of the GGUF encyclopedia, religion.txt theory queue, persona.txt,
 * and output vocabulary systems implemented in the AI runtimes.
 * - Ongoing project lead: every feature exists because Derek defined its intent.
 *
 * Gemini (Google — AI Consultant)
 * ─────────────────────────────────────────────────────────────
 * - Shift+Click asynchronous process kill-switch logic.
 * - Custom PATH environment variable injection for sub-process dependencies.
 * - V14.4 Virtual Display Bridge: Raw MJPEG Byte Scanner for live video streaming.
 * - Kinetic Mouse Routing: mapping Java panel clicks back to the active shell/script.
 * - Multi-tenant architecture refactoring: buildMatrixArchitecture(), per-session
 * directory pre-mapping, and sandbox-resilient SystemProfiler fallbacks.
 * - V14.4 The Asynchronous Dumbo Fix: Disentangled GUI refresh (60Hz fallback) from the 
 * 81ms biological/analog logic tick to prevent hardware-induced stutter.
 * - V14.4 Analog Debounce Failsafe: Implemented a 40ms noise filter to catch and
 * destroy duplicate signals from physically degraded keyboard traces.
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
 * opener path and related button wiring.
 * - Helped preserve newer V14.4 features while restoring older missing pieces.
 *
 * 2. SESSION-ISOLATION REVIEW
 * - Analyzed API cross-talk risks from global API process state.
 * - Recommended per-session API maps:
 * apiProcessMap, apiStdinMap, and apiStatusMap.
 * - Recommended per-session telemetry restoration during loadSession().
 *
 * 3. TELEMETRY / IMAGE / KINETIC OVERRIDE FIXES
 * - Recommended preventing background API status from overwriting the visible
 * HUD for another FN session.
 * - Recommended persistent MANIFEST_IMAGE logging for API image overrides.
 * - Recommended scoping [TYPE] kinetic typing so background sessions do not
 * type into the currently viewed session.
 *
 * 4. DEEP AI LOCALIZATION ALIGNMENT
 * - Recommended startGoddessAPI() mirror executeShellCommand() environment:
 * HOME -> osDev/home, PATH prefixed with osDev/bin, working directory from
 * sessionDirectories.
 * - Clarified AI sandboxing as a local OS tinkering layer rather than a hard
 * prison: osDev protects the host while allowing experimentation.
 *
 * 5. API BINARY ROUTING REVIEW
 * - Helped shape the distinction:
 * AI alone -> GoddessAPI.sh / GoddessAPI.bat
 * FN + AI  -> GoddessAPI.py
 * - Recommended .py files launch through python3/python rather than bash.
 * - Noted path consistency issue: osDevAIBin should point to osDev/AI/bin.
 *
 * 6. FILE SYSTEM HANDLING VALIDATION
 * - Confirmed Derek's correction:
 * File newDir = new File(target);
 * - Recognized it as aligned with the goal of allowing intentional navigation
 * rather than over-restricting the AI sandbox.
 *
 * 7. CONTRIBUTION / PROVENANCE DOCUMENTATION
 * - Helped rewrite contributor blocks to distinguish:
 * Derek = core architect and project lead
 * Gemini = multi-tenant/sandbox/streaming consultant
 * Claude = renderer and implementation partner
 * ChatGPT = merge, isolation review, governance, and documentation support
 *
 * 8. PYTHON-SIDE ARCHITECTURE SUPPORT
 * - Proposed experimental.txt and experimental_journal.txt governance for
 * optional experimental features.
 * - Proposed AI-readable, append-only log memory helpers for self-optimization.
 * - Recommended modular online LLM provider routing via a separate bridge file.
 *
 * Resulting design principle:
 * Stable users can keep experimental features off, while exploratory users can
 * enable advanced AI behavior through visible, reviewable configuration files.
 * 9. INITIALIZED CONVERSION TO CLASSES BASED ARCHITECTURE
 * 10.
 * 11.
 * 12.
 * 13.
 * 14.
 * 15. HEADER / DOCUMENTATION CONSOLIDATION
 * - Identified duplicate and conflicting header blocks (v14.4+ vs v15 modular).
 * - Unified documentation into a single authoritative architecture header.
 * - Clarified system/ vs assets/ vs modular/ separation for maintainability.
 *
 * 16. RUNTIME STRUCTURE ALIGNMENT
 * - Verified GoddessMatrix boot flow matches documented responsibilities:
 *   detect OS → init paths → build architecture → create modules → wire modules → show UI.
 * - Ensured documentation reflects actual execution order and system layering.
 *
 * 17. CHAT HISTORY LOGGING INTEGRATION
 * - Added missing logging methods required by Keyboard and ApiBridge:
 *   writeToSessionLog, logUserAiInput, logProcessInput,
 *   logApiChat, logApiStdout, logUnknownApiTag.
 * - Ensured compatibility with Cloud Mode (non-persistent behavior).
 *
 * 18. TYPE SAFETY CORRECTION (CRITICAL)
 * - Replaced Object typing in MatrixState with:
 *   assets.TerminalRunner and assets.ApiBridge.
 * - Restored compile-time safety for:
 *   executeShellCommand, executeDirectScript, launchExternalTerminal, startGoddessAPI.
 *
 * 19. FILE STRUCTURE DEBUGGING
 * - Diagnosed Linux case-sensitive filename issues (uiWindow → UIWindow).
 * - Identified cross-file copy errors (ImageViewer containing Keyboard class).
 * - Guided recovery to proper one-class-per-file structure.
 *
 * 20. UI DEFAULT STATE CORRECTION
 * - Changed default resolution from 1080p to 720p in UIWindow.
 * - Synchronized UI dropdown default with actual startup state.
 * - Identified need for resolution-driven font scaling (future config binding).
 *
 * 21. MODULAR EXPANSION MODEL REFINEMENT
 * - Reinforced design: core system must run even if modular classes are missing.
 * - Defined extension buttons 107–112 as failure-isolated feature triggers.
 * - Established modular/ as update-safe, hot-swappable expansion layer.
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
