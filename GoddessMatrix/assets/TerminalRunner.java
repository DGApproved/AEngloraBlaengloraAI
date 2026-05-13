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
    public void launchExternalTerminal(boolean asChroot) {
        try {
            ProcessBuilder pb;

            if (asChroot) {
                // 1. Resolve script directory via state OS flags
                File scriptDir = state.uiWindow != null
                        ? state.uiWindow.getOSScriptFolderFile()
                        : new File(state.scriptRootDirectory,
                            state.isWindows ? "Windows" : (state.isMac ? "MacOSY" : "Linux"));
                scriptDir.mkdirs();

                File chrootScript = new File(scriptDir, "chroot_env.sh");

                // 2. Generate chroot script dynamically if missing
                if (!chrootScript.exists() && state.osDevDir != null) {
                    String scriptContent =
                            "#!/bin/bash\n" +
                            "CHROOT_DIR=\"" + state.osDevDir.getAbsolutePath() + "\"\n" +
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
                    java.nio.file.Files.write(chrootScript.toPath(), scriptContent.getBytes());
                    chrootScript.setExecutable(true);
                }

                // 3. Launch via state OS flags — no inline os.name needed
                if (state.isWindows) {
                    pb = new ProcessBuilder("cmd.exe", "/c", "start", "wsl.exe",
                            "--exec", "bash", chrootScript.getAbsolutePath());
                    if (state.chatHistory != null)
                        state.chatHistory.appendSystem("LAUNCHING WSL CHROOT BRIDGE...");
                } else if (state.isMac) {
                    pb = new ProcessBuilder("open", "-a", "Terminal",
                            chrootScript.getAbsolutePath());
                } else {
                    pb = new ProcessBuilder("x-terminal-emulator", "-e",
                            chrootScript.getAbsolutePath());
                    if (state.chatHistory != null)
                        state.chatHistory.appendSystem("LAUNCHING EXTERNAL CHROOT TERMINAL...");
                }
            } else {
                // Standard terminal — use state OS flags
                if (state.isWindows) {
                    pb = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe");
                } else if (state.isMac) {
                    pb = new ProcessBuilder("open", "-a", "Terminal");
                } else {
                    pb = new ProcessBuilder("x-terminal-emulator");
                }
            }
            
            // 4. Safely pull the working directory from the MatrixState
            pb.directory(state.currentWorkingDirectory);
            pb.start();
            
            // 5. Use the local setStatus helper
            setStatus(asChroot ? "SYS_EXEC: CHROOT_TERMINAL_OPEN" : "SYS_EXEC: TERMINAL_OPENED");
            
        } catch (Exception e) {
            setStatus("SYS_EXEC: FAILED");
            if (state.chatHistory != null) {
                state.chatHistory.appendError("TERMINAL_LAUNCH_FAILED: " + e.getMessage());
            }
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
