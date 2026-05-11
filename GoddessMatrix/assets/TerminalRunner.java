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
 * - Cross-Platform Bridge: dynamically translates Linux commands and routes scripts
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
                // 'start cmd.exe' physically opens a new visible command prompt window
                cmd = new String[]{"cmd.exe", "/c", "start", "cmd.exe"};
            } else if (state.isMac) {
            	// macOS native visible terminal
            	cmd = new String[] {"open", "-a", "Terminal", dir.getAbsolutePath()};
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
                String translatedCommand = translateCommandForWindows(command);
                pb = new ProcessBuilder("cmd.exe", "/c", translatedCommand);
            } else {
                pb = new ProcessBuilder("bash", "-c", command);
            }

            pb.directory(state.currentWorkingDirectory);
            // Redirecting error stream ensures hidden failures are caught by your loggers
            pb.redirectErrorStream(true); 
            pb.start();

            setStatus("SYS_EXEC: CMD_SENT");

        } catch (IOException e) {
            setStatus("SYS_EXEC: CMD_FAIL");
        }
    }

    // ─────────────────────────────────────────────
    // SCRIPT MODE EXECUTION (Inline / Direct)
    // ─────────────────────────────────────────────
    public void executeDirectScript(String command) {
        try {
            ProcessBuilder pb;

            if (state.isWindows) {
                String translatedCommand = translateCommandForWindows(command);
                pb = new ProcessBuilder("cmd.exe", "/c", translatedCommand);
            } else {
                pb = new ProcessBuilder("bash", "-c", command); // Added -c to properly execute string commands in bash
            }

            pb.directory(state.currentWorkingDirectory);
            pb.redirectErrorStream(true);
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
                // Smart Script Routing for Windows 11
                String path = scriptFile.getAbsolutePath();
                if (scriptName.endsWith(".py")) {
                    pb = new ProcessBuilder("python", path);
                } else if (scriptName.endsWith(".sh")) {
                    // Leverages Windows 11 WSL or Git Bash alias if available
                    pb = new ProcessBuilder("bash", path);
                } else {
                    // Default fallback for .bat or general executables
                    pb = new ProcessBuilder("cmd.exe", "/c", path);
                }
            } else {
                // Linux native execution
                pb = new ProcessBuilder("bash", scriptFile.getAbsolutePath());
            }

            pb.directory(scriptDir);
            pb.redirectErrorStream(true);
            pb.start();

            setStatus("SYS_SCRIPT: LAUNCHED");

        } catch (Exception e) {
            setStatus("SYS_SCRIPT: FAIL");
        }
    }

    // ─────────────────────────────────────────────
    // WINDOWS COMMAND TRANSLATION LAYER
    // ─────────────────────────────────────────────
    private String translateCommandForWindows(String command) {
        String trimmed = command.trim();
        
        // Basic Linux -> DOS translation mapping
        if (trimmed.equals("ls") || trimmed.startsWith("ls ")) {
            return trimmed.replaceFirst("^ls", "dir");
        } else if (trimmed.equals("pwd")) {
            return "cd"; // typing 'cd' without args in cmd prints the working directory
        } else if (trimmed.equals("clear")) {
            return "cls";
        } else if (trimmed.startsWith("rm -rf ")) {
            return trimmed.replaceFirst("^rm -rf ", "rmdir /s /q ");
        } else if (trimmed.startsWith("rm ")) {
            return trimmed.replaceFirst("^rm ", "del ");
        } else if (trimmed.startsWith("cp ")) {
            return trimmed.replaceFirst("^cp ", "copy ");
        } else if (trimmed.startsWith("mv ")) {
            return trimmed.replaceFirst("^mv ", "move ");
        }
        
        // If no translation rule matches, pass it through raw
        return command;
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