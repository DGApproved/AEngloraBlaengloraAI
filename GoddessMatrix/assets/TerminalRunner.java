package assets;

/*
 * TerminalRunner.java
 *
 * Handles OS-level execution for Goddess Matrix.
 *
 * Responsibilities:
 * - launch external terminal (NTR key)
 * - execute shell commands (EXEC mode)
 * - execute scripts (SCRIPT mode)
 * - support FN+NTR sandbox targeting
 *
 * Design:
 * - no dependency on modular/*
 * - minimal assumptions about environment
 */

import system.MatrixState;

import java.io.File;
import java.io.IOException;

public class TerminalRunner {

    private final MatrixState state;

    public TerminalRunner(MatrixState state) {
        this.state = state;
    }

    // ─────────────────────────────────────────────
    // EXTERNAL TERMINAL LAUNCH (NTR)
    // ─────────────────────────────────────────────
    public void launchExternalTerminal(boolean asChroot, File targetDir) {
        try {
            File dir = resolveTargetDirectory(asChroot, targetDir);

            String[] cmd;

            if (state.isWindows) {
                cmd = new String[]{"cmd.exe", "/c", "start", "cmd.exe"};
            } else {
                // Linux / Mac fallback
                cmd = new String[]{
                        "x-terminal-emulator",
                        "--working-directory=" + dir.getAbsolutePath()
                };
            }

            new ProcessBuilder(cmd)
                    .directory(dir)
                    .start();

            setStatus("SYS_EXEC: TERMINAL_LAUNCHED");

        } catch (Exception e) {
            setStatus("SYS_EXEC: TERMINAL_FAIL");
        }
    }

    private File resolveTargetDirectory(boolean asChroot, File baseDir) {
        if (asChroot && state.osDevDir != null) {
            return state.osDevDir;
        }
        return baseDir != null ? baseDir : state.currentWorkingDirectory;
    }

    // ─────────────────────────────────────────────
    // EXEC MODE COMMAND
    // ─────────────────────────────────────────────
    public void executeShellCommand(String command) {
        try {
            ProcessBuilder pb;

            if (state.isWindows) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("bash", "-c", command);
            }

            pb.directory(state.currentWorkingDirectory);
            pb.start();

            setStatus("SYS_EXEC: CMD_SENT");

        } catch (IOException e) {
            setStatus("SYS_EXEC: CMD_FAIL");
        }
    }

    // ─────────────────────────────────────────────
    // SCRIPT MODE EXECUTION
    // ─────────────────────────────────────────────
    public void executeDirectScript(String command) {
        try {
            ProcessBuilder pb;

            if (state.isWindows) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("bash", command);
            }

            pb.directory(state.currentWorkingDirectory);
            pb.start();

            setStatus("SYS_SCRIPT: EXECUTED");

        } catch (IOException e) {
            setStatus("SYS_SCRIPT: FAIL");
        }
    }

    // ─────────────────────────────────────────────
    // MODULAR SCRIPT LAUNCH (for buttons)
    // ─────────────────────────────────────────────
    public void launchScript(String scriptName) {
        try {
            File scriptDir = state.uiWindow != null
                    ? state.uiWindow.getOSScriptFolderFile()
                    : state.scriptRootDirectory;

            File scriptFile = new File(scriptDir, scriptName);

            if (!scriptFile.exists()) {
                setStatus("SYS_SCRIPT: NOT_FOUND");
                return;
            }

            ProcessBuilder pb;

            if (state.isWindows) {
                pb = new ProcessBuilder("cmd.exe", "/c", scriptFile.getAbsolutePath());
            } else {
                pb = new ProcessBuilder("bash", scriptFile.getAbsolutePath());
            }

            pb.directory(scriptDir);
            pb.start();

            setStatus("SYS_SCRIPT: LAUNCHED");

        } catch (Exception e) {
            setStatus("SYS_SCRIPT: FAIL");
        }
    }

    // ─────────────────────────────────────────────
    // STATUS HELPER
    // ─────────────────────────────────────────────
    private void setStatus(String msg) {
        if (state.statusLabel != null) {
            state.statusLabel.setText(msg);
        }
    }
}
