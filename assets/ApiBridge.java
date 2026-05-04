package assets;

/*
 * ApiBridge.java
 *
 * Handles GoddessAPI launch, stdin routing, stdout protocol parsing,
 * per-session API isolation, telemetry HUD state, and visual renderer hooks.
 */

import system.MatrixState;
import system.SessionProcess;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.SwingUtilities;

public class ApiBridge {

    private final MatrixState state;

    public ApiBridge(MatrixState state) {
        this.state = state;
    }

    public void startGoddessAPI(boolean useDeepAILocalization) {
        final int sessionId = state.currentSession;

        Process existing = state.apiProcessMap.get(sessionId);
        if (existing != null && existing.isAlive()) {
            String hud = state.apiStatusMap.getOrDefault(sessionId, "[API_LINK_ESTABLISHED: FN" + sessionId + "]");
            if (state.currentSession == sessionId && state.aiStatusLabel != null) {
                state.aiStatusLabel.setText(hud);
            }
            return;
        }

        state.apiStatusMap.put(sessionId, "API_BOOTING...");

        if (state.aiStatusLabel != null) {
            state.aiStatusLabel.setText("API_BOOTING...");
        }

        if (state.chatHistory != null) {
            state.chatHistory.appendSystem("INITIALIZING GODDESS_API HOOK FOR FN" + sessionId);
        }

        new Thread(() -> runApiProcess(sessionId, useDeepAILocalization), "GoddessAPI-FN" + sessionId).start();
    }

    private void runApiProcess(int sessionId, boolean useDeepAILocalization) {
        try {
            File apiFile = resolveAPIBinary(useDeepAILocalization);
            List<String> cmd = buildLaunchCommand(apiFile, useDeepAILocalization);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(state.sessionDirectories.getOrDefault(sessionId, state.aiHomeDirectory));

            Map<String, String> env = pb.environment();
            env.put("HOME", state.osDevHome.getAbsolutePath());
            String currentPath = env.getOrDefault("PATH", "");
            env.put("PATH", state.osDevBin.getAbsolutePath() + File.pathSeparator + currentPath);

            pb.redirectErrorStream(true);

            Process process = pb.start();
            PrintWriter stdin = new PrintWriter(new OutputStreamWriter(process.getOutputStream()), true);

            synchronized (state.apiProcessMap) {
                state.apiProcessMap.put(sessionId, process);
                state.apiStdinMap.put(sessionId, stdin);
            }

            String linked = "[API_LINK_ESTABLISHED: FN" + sessionId + "]";
            state.apiStatusMap.put(sessionId, linked);

            SwingUtilities.invokeLater(() -> {
                if (state.imageViewer != null) {
                    state.imageViewer.startAIRenderer(sessionId);
                }
                if (state.currentSession == sessionId && state.aiStatusLabel != null) {
                    state.aiStatusLabel.setText(linked);
                }
            });

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    parseAPIOutput(line, sessionId);
                }
                process.waitFor();
            }

        } catch (Exception e) {
            state.apiStatusMap.put(sessionId, "[API_ERROR]");

            SwingUtilities.invokeLater(() -> {
                if (state.currentSession == sessionId && state.aiStatusLabel != null) {
                    state.aiStatusLabel.setText("[API_ERROR]");
                }
                if (state.chatHistory != null) {
                    state.chatHistory.appendError("API_HOOK_FAILED: " + e.getMessage());
                }
            });

        } finally {
            synchronized (state.apiProcessMap) {
                state.apiProcessMap.remove(sessionId);
                state.apiStdinMap.remove(sessionId);
            }

            state.apiStatusMap.put(sessionId, MatrixConfig.API_OFFLINE);

            SwingUtilities.invokeLater(() -> {
                if (state.currentSession == sessionId && state.aiStatusLabel != null) {
                    state.aiStatusLabel.setText(MatrixConfig.API_OFFLINE);
                }

                if (state.imageViewer != null) {
                    if (state.isVideoStreamActive) {
                        state.imageViewer.stopVideoStream();
                    }
                    state.imageViewer.stopAIRenderer();
                }

                if (state.currentSession == sessionId && state.isAIModeActive) {
                    state.isAIModeActive = false;
                    state.sessionModes.put(sessionId, 0);
                    if (state.keyboard != null) {
                        state.keyboard.updateModifierVisuals();
                    }
                }
            });
        }
    }

    private File resolveAPIBinary(boolean useDeepAILocalization) {
        if (useDeepAILocalization) {
            File deep = new File(state.osDevAIBin, "GoddessAPI.py");
            if (deep.exists()) return deep;

            File standard = new File(getOSScriptFolderFile(), "GoddessAPI.py");
            if (standard.exists()) return standard;

            return null;
        }

        String scriptName = state.isWindows ? "GoddessAPI.bat" : "GoddessAPI.sh";

        File standard = new File(getOSScriptFolderFile(), scriptName);
        if (standard.exists()) return standard;

        File local = new File(state.aiHomeDirectory, scriptName);
        if (local.exists()) return local;

        return null;
    }

    private List<String> buildLaunchCommand(File apiFile, boolean useDeepAILocalization) {
        List<String> cmd = new ArrayList<>();

        if (useDeepAILocalization) {
            cmd.add(state.isWindows ? "python" : "python3");
            cmd.add(apiFile != null ? apiFile.getAbsolutePath() : new File(getOSScriptFolderFile(), "GoddessAPI.py").getAbsolutePath());
            return cmd;
        }

        if (state.isWindows) {
            cmd.add("cmd.exe");
            cmd.add("/c");
            cmd.add(apiFile != null ? apiFile.getAbsolutePath() : new File(getOSScriptFolderFile(), "GoddessAPI.bat").getAbsolutePath());
        } else {
            cmd.add("bash");
            cmd.add(apiFile != null ? apiFile.getAbsolutePath() : new File(getOSScriptFolderFile(), "GoddessAPI.sh").getAbsolutePath());
        }

        return cmd;
    }

    public void stopGoddessAPI(int sessionId) {
        Process process;
        PrintWriter stdin;

        synchronized (state.apiProcessMap) {
            process = state.apiProcessMap.get(sessionId);
            stdin = state.apiStdinMap.get(sessionId);
        }

        if (process != null && process.isAlive()) {
            if (stdin != null) {
                stdin.println("exit");
                stdin.flush();
            }

            final Process finalProcess = process;

            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    if (finalProcess.isAlive()) {
                        finalProcess.destroyForcibly();
                    }
                } catch (InterruptedException ignored) {
                }
            }, "GoddessAPI-Stop-FN" + sessionId).start();
        }

        state.apiStatusMap.put(sessionId, MatrixConfig.API_OFFLINE);

        if (state.currentSession == sessionId && state.aiStatusLabel != null) {
            state.aiStatusLabel.setText(MatrixConfig.API_OFFLINE);
        }

        if (state.imageViewer != null) {
            if (state.isVideoStreamActive) {
                state.imageViewer.stopVideoStream();
            }
            state.imageViewer.stopAIRenderer();
        }
    }

    private void parseAPIOutput(String line, int sessionId) {
        SwingUtilities.invokeLater(() -> {
            if (line.startsWith("[STATUS]")) {
                String status = line.substring(8).trim();
                state.apiStatusMap.put(sessionId, status);
                state.currentAIStatus = status;
                state.isAIProcessing = false;

                if (state.currentSession == sessionId && state.aiStatusLabel != null) {
                    state.aiStatusLabel.setText(status);
                }

            } else if (line.startsWith("[PROCESSING]")) {
                state.isAIProcessing = true;
                state.currentAIStatus = "GENERATING";

            } else if (line.startsWith("[IMAGE]")) {
                String imgPath = line.substring(7).trim();

                if (state.chatHistory != null) {
                    state.chatHistory.logManifestImage("[API_OVERRIDE] " + imgPath);
                }

                if (state.currentSession == sessionId && state.imageViewer != null) {
                    state.imageViewer.renderStoredImage(imgPath);
                }

            } else if (line.startsWith("[STREAM_START]")) {
                try {
                    int port = Integer.parseInt(line.substring(14).trim());
                    if (state.currentSession == sessionId && state.imageViewer != null) {
                        state.imageViewer.startVideoStream(port);
                    }
                } catch (NumberFormatException e) {
                    if (state.chatHistory != null) {
                        state.chatHistory.appendError("INVALID STREAM PORT");
                    }
                }

            } else if (line.startsWith("[STREAM_STOP]")) {
                if (state.currentSession == sessionId && state.imageViewer != null) {
                    state.imageViewer.stopVideoStream();
                }

            } else if (line.startsWith("[TYPE]")) {
                String typeContent = line.substring(6).trim();

                if (state.currentSession == sessionId && state.keyboard != null) {
                    if (state.chatHistory != null) {
                        state.chatHistory.appendRaw("GODDESS_API> KINETIC_OVERRIDE_ENGAGED\n");
                    }
                    simulateTyping(typeContent + "\n");
                } else {
                    SessionProcess sp = state.processMap.get(sessionId);
                    if (sp != null && sp.isAlive()) {
                        sp.sendLine(typeContent);
                        if (state.chatHistory != null) {
                            state.chatHistory.writeToSessionLog(sessionId, sp.isScript, "> " + typeContent + "\n");
                        }
                    }
                }

            } else if (line.startsWith("[CHAT]")) {
                String chat = line.substring(6).trim();
                if (state.chatHistory != null) {
                    state.chatHistory.logApiChat(sessionId, chat);
                }

            } else if (line.startsWith("[ACTION]")) {
                state.avatarAction = line.substring(8).trim().toUpperCase();

            } else if (line.startsWith("[WORLD_") || line.startsWith("[GAME_") ||
                    line.startsWith("[ENTITY_") || line.startsWith("[PANEL_")) {
                // ── GAME PROTOCOL DISPATCH ────────────────────────────────────
                // Routes game tags to the active IceSandbox GameProtocol instance.
                // state.gameProtocol is set by modular.Game on launch, null otherwise.
                // Safe to call when game is not running — instanceof check guards it.
                //direct cast method
                //if (state.gameProtocol instanceof modular.game.GameProtocol) {
                //    ((modular.game.GameProtocol) state.gameProtocol).handleTag(line);
                //}
                //reflection call method
                if (state.gameProtocol != null) {
                    try {
                        state.gameProtocol.getClass()
                            .getMethod("handleTag", String.class)
                            .invoke(state.gameProtocol, line);
                    } catch (Exception ignored) {}
                }


            } else if (line.startsWith("[")) {
                if (state.chatHistory != null) {
                    state.chatHistory.logUnknownApiTag(sessionId, line);
                }

            } else {
                if (state.chatHistory != null) {
                    state.chatHistory.logApiStdout(sessionId, line);
                }
            }
        });
    }

    private void simulateTyping(String content) {
        new Thread(() -> {
            for (char c : content.toCharArray()) {
                int index = state.keyboard != null ? state.keyboard.mapCharToIndex(c) : -1;

                SwingUtilities.invokeLater(() -> {
                    if (index != -1 && state.buttons != null && state.keyboard != null) {
                        state.keyboard.handleMatrixEvent(index, state.buttons.get(index), false);
                    } else if (state.chatHistory != null) {
                        state.chatHistory.insertAtCaret(String.valueOf(c));
                    }
                });

                try {
                    Thread.sleep(30);
                } catch (InterruptedException ignored) {
                }
            }
        }, "GoddessAPI-KineticTyping").start();
    }

    private File getOSScriptFolderFile() {
        if (state.uiWindow != null) {
            return state.uiWindow.getOSScriptFolderFile();
        }

        String subDir = "Linux";
        if (state.isWindows) subDir = "Windows";
        else if (state.isMac) subDir = "MacOSY";

        return new File(state.scriptRootDirectory, subDir);
    }
}
