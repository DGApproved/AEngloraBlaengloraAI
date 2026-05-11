package modular;

/*
 * LLMs.java
 * ========================================================================
 * MODULE: Cloud LLM Bridge (modular.LLMs)
 * // Optional Server / Cloud LLM expansion card for Goddess Matrix.
 * // Button:
 * // - 109 [Server] -> modular.LLMs
 * DEPENDENCY: Java 11+ (java.net.http)
 *
 * -- MODULE LINEAGE --
 * Structural foundation, API formatting, and zero-dependency JSON
 * parsers generated via ChatGPT. Orchestration integration and UI
 * threading refined in triadic collaboration.
 *
 * LOGIC NOTES:
 * - Operates entirely async. Does not block the Matrix event loop.
 * - Prioritizes OS environment variables for API key security.
 *
 * DESIGN:
 * - Optional module required for Cloud LLM features.
 * - Reflection-launched.
 * - Failure-isolated.
 * - Does not break core Matrix if absent/corrupt.
 * - Cloud mode may be configured as volatile or persistent depending on user preference.
 *
 * COMPLIANCE NOTE:
 * - Persistence behavior may be subject to external LLM provider policies.
 * - Verify provider terms before enabling persistent logging or storage of responses.
 *
 * ========================================================================
 */

import system.MatrixState;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Properties;

public class LLMs {

    private final MatrixState state;
    private final HttpClient client = HttpClient.newHttpClient();

    private File configFile;
    private Properties config;

    public LLMs(MatrixState state) {
        this.state = state;
    }

    public void launch() {
        state.isCloudModeActive = true;
        state.isSystemModeActive = false;
        state.isAIModeActive = false;
        state.isScriptModeActive = false;
        state.sessionModes.put(state.currentSession, 4);
        state.llmBridge = this;

        loadOrCreateConfig();

        if (state.chatHistory != null) {
            state.chatHistory.clearChat();
            state.chatHistory.appendRaw("CLOUD_MODE> Server / LLM bridge online.\n");
            state.chatHistory.appendRaw("CLOUD_MODE> Provider: " + config.getProperty("provider", "manual") + "\n");
            state.chatHistory.appendRaw("CLOUD_MODE> This mode is volatile: no session log persistence.\n");
        }

        if (state.keyboard != null) {
            state.keyboard.updateModifierVisuals();
        }

        if (state.statusLabel != null) {
            state.statusLabel.setText("SYS_MODULE: LLM_SERVER_READY");
        }
    }

    public void sendCloudPrompt(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) return;

        String provider = config.getProperty("provider", "manual").trim().toLowerCase();

        if (state.chatHistory != null) {
            state.chatHistory.appendRaw("CLOUD_USER> " + prompt + "\n");
        }

        new Thread(() -> {
            try {
                String response;

                switch (provider) {
                    case "openai":
                        response = callOpenAI(prompt);
                        break;
                    case "claude":
                        response = callClaude(prompt);
                        break;
                    case "gemini":
                        response = callGemini(prompt);
                        break;
                    case "local_http":
                        response = callLocalHttp(prompt);
                        break;
                    case "manual":
                    default:
                        response = "Manual provider selected. Edit " + configFile.getAbsolutePath()
                                + " and set provider=openai, claude, gemini, or local_http.";
                        break;
                }

                SwingUtilities.invokeLater(() -> {
                    if (state.chatHistory != null) {
                        state.chatHistory.appendRaw("CLOUD_LLM> " + response + "\n");
                    }
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    if (state.chatHistory != null) {
                        state.chatHistory.appendError("LLM_BRIDGE_FAILED: " + e.getMessage());
                    }
                    if (state.statusLabel != null) {
                        state.statusLabel.setText("SYS_MODULE: LLM_FAILED");
                    }
                });
            }
        }, "LLMs-CloudPrompt").start();
    }

    private void loadOrCreateConfig() {
        try {
            File sessionDir = state.sessionDirectories.get(state.currentSession);
            File datasDir = new File(sessionDir, "dgapi/datas");
            datasDir.mkdirs();

            configFile = new File(datasDir, "llms.properties");
            config = new Properties();

            if (!configFile.exists()) {
                config.setProperty("provider", "manual");
                config.setProperty("model.openai", "gpt-4.1-mini");
                config.setProperty("model.claude", "claude-3-5-sonnet-latest");
                config.setProperty("model.gemini", "gemini-1.5-flash");
                config.setProperty("local_http.url", "http://localhost:11434/api/generate");
                config.setProperty("notes", "Use environment variables OPENAI_API_KEY, ANTHROPIC_API_KEY, GEMINI_API_KEY.");
                saveConfig();
            } else {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    config.load(fis);
                }
            }
        } catch (Exception e) {
            config = new Properties();
            config.setProperty("provider", "manual");
        }
    }

    private void saveConfig() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            config.store(fos, "Goddess Matrix LLM provider settings");
        }
    }

    private String callOpenAI(String prompt) throws Exception {
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.isBlank()) return "OPENAI_API_KEY is not set.";

        String model = config.getProperty("model.openai", "gpt-4.1-mini");

        String body = "{"
                + "\"model\":\"" + json(model) + "\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + json(prompt) + "\"}]"
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        String raw = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        return extractJsonText(raw, "\"content\":\"");
    }

    private String callClaude(String prompt) throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) return "ANTHROPIC_API_KEY is not set.";

        String model = config.getProperty("model.claude", "claude-3-5-sonnet-latest");

        String body = "{"
                + "\"model\":\"" + json(model) + "\","
                + "\"max_tokens\":512,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"" + json(prompt) + "\"}]"
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        String raw = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        return extractJsonText(raw, "\"text\":\"");
    }

    private String callGemini(String prompt) throws Exception {
        String key = System.getenv("GEMINI_API_KEY");
        if (key == null || key.isBlank()) return "GEMINI_API_KEY is not set.";

        String model = config.getProperty("model.gemini", "gemini-1.5-flash");

        String body = "{"
                + "\"contents\":[{\"parts\":[{\"text\":\"" + json(prompt) + "\"}]}]"
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        String raw = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        return extractJsonText(raw, "\"text\":\"");
    }

    private String callLocalHttp(String prompt) throws Exception {
        String url = config.getProperty("local_http.url", "http://localhost:11434/api/generate");

        String body = "{"
                + "\"model\":\"" + json(config.getProperty("local_http.model", "llama3")) + "\","
                + "\"prompt\":\"" + json(prompt) + "\","
                + "\"stream\":false"
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        String raw = client.send(req, HttpResponse.BodyHandlers.ofString()).body();
        return extractJsonText(raw, "\"response\":\"");
    }

    private static String json(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private static String extractJsonText(String raw, String marker) {
        if (raw == null) return "";

        int start = raw.indexOf(marker);
        if (start < 0) return raw;

        start += marker.length();
        StringBuilder out = new StringBuilder();

        boolean escape = false;

        for (int i = start; i < raw.length(); i++) {
            char c = raw.charAt(i);

            if (escape) {
                switch (c) {
                    case 'n': out.append('\n'); break;
                    case 't': out.append('\t'); break;
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    default: out.append(c); break;
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }

        return out.toString();
    }
}
