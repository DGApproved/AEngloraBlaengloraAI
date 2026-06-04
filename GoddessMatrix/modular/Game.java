package modular;

/*
 * Game.java
 *
 * MODULE: Game Engine Launcher (modular.Game)
 * Button: 111 [Game] -> modular.Game
 *
 * The game renders inside the GoddessMatrix ImageViewer manifest panel —
 * the same visual chamber used by Mode A (waveform HUD) and Mode B (avatar).
 * No separate JFrame is created.
 *
 * DISPLAY ARCHITECTURE:
 *   [Game] pressed → IceSandbox embedded in imageManifest panel
 *   Single-click manifest → expand to area mode (fills matrix panel)
 *   Double-click manifest → cinematic passthrough (fullscreen glass pane)
 *   Double-click again  → restore manifest size
 *   [Game] pressed again → stopGameMode(), restore normal manifest rendering
 *
 * EXIT IN MATRIX CONTEXT:
 *   [Game] button again — calls stopGameMode()
 *   Hard interrupt (clRH button) — also calls stopGameMode() via UIWindow
 *   No in-game exit button needed.
 *
 * EXIT IN STANDALONE CONTEXT:
 *   ESC overlay click — exits
 *   E panel exit button — exits
 *   Window close button — exits
 *
 * KEY INPUT:
 *   Keyboard.installHardwareBridge passes WASD/SPACE/ESC/E through to the
 *   embedded IceSandbox panel when state.isGameModeActive is true.
 *   All other Matrix keys (FN, modifiers, session switching) still work.
 *
 * APIBRIGE HOOK (uncomment after MatrixState.gameProtocol confirmed):
 *   state.gameProtocol = sandboxInstance.getProtocol();
 *   state.gameProtocol = null; // on stop
 *
 * Contributors:
 *   Gemini (Google)        — original IceSandbox engine
 *   Derek Jason Gilhousen — manifest-as-display design, context detection
 *   Claude (Anthropic)    — Game.java launcher, manifest embedding,
 *                           key passthrough design, context detection
 */

import system.MatrixState;
import modular.game.IceSandbox;
import modular.game.WorldState;
import modular.game.GameProtocol;

import javax.swing.*;
import java.io.File;
import java.io.PrintWriter;

public class Game
{
    private final MatrixState state;

    // Single instance — pressing [Game] again stops the running game
    private static IceSandbox sandboxInstance = null;

    public Game(MatrixState state) { this.state = state; }

    public void launch()
    {
        // ── TOGGLE: press [Game] again to stop ───────────────────────────────
        if (state.isGameModeActive && sandboxInstance != null)
        {
            stopGame();
            return;
        }
        // ─────────────────────────────────────────────────────────────────────

        SwingUtilities.invokeLater(() ->
        {
            try
            {
                File gameDir   = new File(state.aiHomeDirectory, "modular/game");
                gameDir.mkdirs();
                File sessionDir = state.sessionDirectories.get(state.currentSession);
                File dgapiSys  = (sessionDir != null)
                               ? new File(sessionDir, "dgapi/system") : null;

                sandboxInstance = new IceSandbox(gameDir, dgapiSys, true);

                state.sandboxInstance = sandboxInstance;

                // Embed in the ImageViewer manifest panel
                if (state.imageViewer != null)
                {
                    state.isGameModeActive = true;

                    // ── REGISTER KEY HOOK ────────────────────────────────────
                    final IceSandbox sb = sandboxInstance;
                    state.keyHook = e -> {
                        if (!state.isGameModeActive) return false;
                        if (e.getID() == java.awt.event.KeyEvent.KEY_PRESSED)
                            sb.keyPressed(e);
                        else if (e.getID() == java.awt.event.KeyEvent.KEY_RELEASED)
                            sb.keyReleased(e);
                        else if (e.getID() == java.awt.event.KeyEvent.KEY_TYPED)
                            sb.keyTyped(e);
                        return state.imageViewerMaximized;
                    };

                    // ── WIRE CAPABILITY HOOKS INTO PROTOCOL ───────────────────
                    // GameProtocol receives Matrix capabilities through hooks —
                    // it never imports system/* directly.
                    modular.game.GameProtocol proto = sandboxInstance.getProtocol();
                    proto.setAISendHook(state.aiSendHook);
                    proto.setErrorHook(state.errorHook);
                    proto.setChatHook(state.chatSystemHook);

                    // ── REGISTER AI RESPONSE HOOK ─────────────────────────────
                    // ApiBridge calls this for every [GAME_*] [WORLD_*] [ENTITY_*]
                    // [PANEL_*] line from GoddessAPI. Routes to protocol.handleTag()
                    // so all AI responses reach the game cleanly.
                    state.aiResponseHook = line -> {
                        try { proto.handleTag(line); }
                        catch (Exception e) {
                            if (state.errorHook != null)
                                state.errorHook.log("GameProtocol.handleTag", e);
                        }
                    };

                    state.imageViewer.startGameMode(sandboxInstance);
                    sandboxInstance.startEngine();

                    PrintWriter stdin = state.apiStdinMap.get(state.currentSession);
                    if (stdin != null) sandboxInstance.getProtocol().setPythonStdin(stdin);
                    state.gameProtocol = sandboxInstance.getProtocol();

                    sandboxInstance.setFullscreenExitCallback(() -> {
                        state.imageViewerMaximized = false;
                        state.imageViewer.restoreManifest();
                    });

                    if (state.statusLabel != null)
                        state.statusLabel.setText("SYS_GAME: ACTIVE | DBL-CLICK MANIFEST TO EXPAND");
                    if (state.chatHistory != null)
                        state.chatHistory.appendSystem(
                            "GAME_ENGINE: LAUNCHED IN MANIFEST | "
                            + "WASD=walk | SPACE=jump | ESC=timing | E=panels | "
                            + "[Game]=exit");
                }
                else
                {
                    // ImageViewer not available — fall back to standalone window
                    launchStandaloneWindow(sandboxInstance, gameDir);
                }
            }
            catch (Exception e)
            {
                // Game is a modular feature — fault-isolate so Matrix keeps running.
                // Full stack trace stored as clickable [rawdat_crash_log] in chat.
                if (state.chatHistory != null)
                    state.chatHistory.logModularCrash("Game Engine", e);
                if (state.statusLabel != null)
                    state.statusLabel.setText("SYS_GAME: FAULT — see [rawdat_crash_log]");
                sandboxInstance            = null;
                state.sandboxInstance      = null;
                state.gameProtocol         = null;
                state.imageViewerMaximized = false;
                state.isGameModeActive     = false;
            }
        });
    }

    private void stopGame()
    {
        if (sandboxInstance != null)
        {
            sandboxInstance.stopEngine();
            sandboxInstance = null;
        }
        if (state.imageViewer != null)
        {
            state.imageViewer.stopGameMode();
        }
        
        state.isGameModeActive     = false;
        state.sandboxInstance      = null;
        state.gameProtocol         = null;
        state.imageViewerMaximized = false;
        state.keyHook              = null;
        state.aiResponseHook       = null;
        state.isAIProcessing       = false;

        if (state.chatHistory != null)
            state.chatHistory.appendSystem("GAME_ENGINE: SESSION_ENDED");
        if (state.statusLabel != null)
            state.statusLabel.setText("SYS_GAME: ENGINE_OFFLINE");
    }

    // ── STANDALONE FALLBACK ───────────────────────────────────────────────────
    // Used only when ImageViewer is unavailable (should not happen in normal
    // Matrix operation, but guards against unexpected null state).

    private void launchStandaloneWindow(IceSandbox sandbox, File gameDir)
    {
        JFrame frame = new JFrame("ICE SANDBOX — Standalone Fallback");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
        frame.add(sandbox);
        frame.addWindowListener(new java.awt.event.WindowAdapter()
        {
            @Override public void windowClosed(java.awt.event.WindowEvent e)
            {
                sandbox.stopEngine();
                sandboxInstance            = null;
                state.sandboxInstance      = null;
                state.gameProtocol         = null;
                state.imageViewerMaximized = false;
                state.isGameModeActive     = false;
            }
        });
        frame.setVisible(true);
        sandbox.requestFocusInWindow();
        sandbox.startEngine();
        state.isGameModeActive = true;
    }
}
