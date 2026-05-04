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

import javax.swing.*;
import java.io.File;

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

                // [APIBRIGE HOOK] — uncomment when ready:
                // state.gameProtocol = sandboxInstance.getProtocol();

                // Embed in the ImageViewer manifest panel
                if (state.imageViewer != null)
                {
                    state.imageViewer.startGameMode(sandboxInstance);
                    sandboxInstance.startEngine();

                    // Clicking the ESC overlay rate display exits fullscreen
                    // (restores manifest from cinematic passthrough mode).
                    // In standalone mode the callback exits entirely instead.
                    sandboxInstance.setFullscreenExitCallback(
                        () -> state.imageViewer.restoreManifest()
                    );

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
                if (state.chatHistory != null)
                    state.chatHistory.appendError("GAME_ENGINE_FAILED: " + e.getMessage());
                if (state.statusLabel != null)
                    state.statusLabel.setText("SYS_GAME: LAUNCH_FAILED");
                sandboxInstance = null;
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
        // [APIBRIGE HOOK] — uncomment when ready:
        // state.gameProtocol = null;

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
                sandboxInstance = null;
                state.isGameModeActive = false;
            }
        });
        frame.setVisible(true);
        sandbox.requestFocusInWindow();
        sandbox.startEngine();
        state.isGameModeActive = true;
    }
}
