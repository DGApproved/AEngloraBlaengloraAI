package system;

/*
 * ChatHistory.java
 *
 * Chat + logging subsystem for Goddess Matrix.
 *
 * Responsibilities:
 * - HTML-based chat display (JEditorPane)
 * - inline raw data expansion via Hyperlinks and DOM manipulation
 * - typing buffer (JTextField)
 * - session log read/write
 * - cloud-mode (non-persistent) handling
 * - manifest image tracking
 *
 * Cloud Mode Behavior:
 * - NO writes to disk
 * - NO session persistence
 * - in-memory only
 */

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatHistory 
{
    private final MatrixState state;

    private JEditorPane chatHistory;
    private JTextPane typingBuffer;
    private JScrollPane scrollPane;

    private final List<String> pendingLogs = new ArrayList<>();
    
    // ── RAW DATA CACHE ──
    private final Map<String, String> rawDataCache = new HashMap<>();
    private final Map<String, Boolean> expandedState = new HashMap<>();
    private final Map<String, String> tagLabelCache = new HashMap<>();
    private int rawDataCounter = 0;

    public ChatHistory(MatrixState state) 
    {
        this.state = state;
    }

    public void initialize() 
    {
        chatHistory = new JEditorPane();
        chatHistory.setContentType("text/html");
        chatHistory.setEditable(false);
        chatHistory.setBackground(Color.BLACK);
        
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();
        styleSheet.addRule("body { color: white; font-family: monospace; font-size: 13pt; background-color: black; margin: 4px; }");
        styleSheet.addRule("a { color: #facd68; text-decoration: none; }");
        chatHistory.setEditorKit(kit);
        chatHistory.setDocument(kit.createDefaultDocument());

        chatHistory.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String target = e.getDescription();

                if (target.startsWith("rawdat:")) {
                    String id = target.substring(7);
                    toggleRawData(id);
                } 
                else if (target.startsWith("image:")) {
                    String imagePath = target.substring(6);
                    if (state.imageViewer != null) {
                        state.imageViewer.renderStoredImage(imagePath);
                    }
                } 
                else if (target.startsWith("http:") || target.startsWith("https:")) {
                    try {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(target));
                    } catch (Exception ex) {
                        appendError("FAILED TO LAUNCH BROWSER: " + ex.getMessage());
                    }
                }
            }
        });

        scrollPane = new JScrollPane(chatHistory);
        scrollPane.setBorder(new LineBorder(Color.DARK_GRAY, 1));
        scrollPane.setPreferredSize(new Dimension(800, 375));

        typingBuffer = new JTextPane();
        typingBuffer.setBackground(Color.BLACK);
        typingBuffer.setForeground(Color.YELLOW);
        typingBuffer.setCaretColor(Color.WHITE);
        typingBuffer.setFont(new Font("Monospaced", Font.PLAIN, 14));

        // JTextPane requires a default attribute set for its text
        javax.swing.text.SimpleAttributeSet defaultAttr = new javax.swing.text.SimpleAttributeSet();
        javax.swing.text.StyleConstants.setForeground(defaultAttr, Color.WHITE); // Pure bone-white by default
        javax.swing.text.StyleConstants.setFontFamily(defaultAttr, "Monospaced");
        javax.swing.text.StyleConstants.setFontSize(defaultAttr, 14);
        typingBuffer.setCharacterAttributes(defaultAttr, true);

        // ── THE PHANTOM REFLEX SILENCER ──
        typingBuffer.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyTyped(java.awt.event.KeyEvent e) {
                // Consume the native OS event. 
                // The text box is no longer allowed to type on its own.
                // It must wait for Keyboard.java to call insertAtCaret().
                e.consume();
            }
        });
        // ─────────────────────────────────

        state.chatTextArea = chatHistory;
        state.typingBuffer = typingBuffer;
        state.chatHistory = this;
    }

    public JScrollPane getScrollPane() 
    {
        return scrollPane;
    }

    public JTextPane getTypingBuffer() 
    {
        return typingBuffer;
    }

    public JEditorPane getTextArea() 
    {
        return chatHistory;
    }

    // ─────────────────────────────────────────────
    // DISPLAY HELPERS
    // ─────────────────────────────────────────────

    public void appendHtml(String html) 
    {
        if (chatHistory == null || html == null) return;

        try {
            HTMLDocument doc = (HTMLDocument) chatHistory.getDocument();
            HTMLEditorKit kit = (HTMLEditorKit) chatHistory.getEditorKit();
            kit.insertHTML(doc, doc.getLength(), html, 0, 0, null);
            chatHistory.setCaretPosition(doc.getLength());
        } catch (Exception ignored) {}
    }

    public void appendRaw(String text) 
    {
        if (text == null) return;
        String safeText = text.replace("&", "&amp;")
                              .replace("<", "&lt;")
                              .replace(">", "&gt;")
                              .replace("\n", "<br>");
        appendHtml(safeText);
    }

    public void appendSystem(String text) 
    {
        appendRaw("SYSTEM> " + text + "\n");
        cacheChatData("SYSTEM> " + text);
    }

    public void appendError(String text) 
    {
        appendHtml("<span style='color: #ef4444;'>ERROR&gt; " + text + "</span><br>");
        cacheChatData("ERROR> " + text);
    }

    public void appendChat(String speaker, String text) 
    {
        appendRaw(speaker + "> " + text + "\n");
        cacheChatData(speaker + "> " + text);
    }

    public void clearChat() 
    {
        if (chatHistory != null) {
            chatHistory.setText("");
        }
    }

    public void clearTypingBuffer() 
    {
        if (typingBuffer != null) typingBuffer.setText("");
    }
    
    private void toggleRawData(String id) 
    {
        try {
            HTMLDocument doc = (HTMLDocument) chatHistory.getDocument();
            javax.swing.text.Element elem = doc.getElement("raw_" + id);
            
            if (elem != null) {
                boolean isExpanded = expandedState.getOrDefault(id, false);
                String tagLabel = tagLabelCache.getOrDefault(id, "[rawdat]");
                
                if (isExpanded) {
                    doc.setInnerHTML(elem, "<a href='rawdat:" + id + "'>" + tagLabel + "</a>");
                    expandedState.put(id, false);
                } else {
                    String safeData = rawDataCache.get(id)
                                        .replace("&", "&amp;")
                                        .replace("<", "&lt;")
                                        .replace(">", "&gt;")
                                        .replace("\n", "<br>");
                                        
                    String expandedHtml = "<a href='rawdat:" + id + "'>[-rawdat]</a><br>" +
                                          "<span style='color: #9d50bb; border-left: 2px solid #9d50bb; margin-left: 10px; padding-left: 5px; display: block;'>" + 
                                          safeData + "</span>";
                    doc.setInnerHTML(elem, expandedHtml);
                    expandedState.put(id, true);
                }
            }
        } catch (Exception ex) {
            // Safely ignore DOM errors
        }
    }

    // ─────────────────────────────────────────────
    // INPUT HELPERS
    // ─────────────────────────────────────────────

    public String getTypingText() 
    {
        if (typingBuffer == null) return "";
        try {
            // JTextPane can inject phantom \r characters. This safely extracts pure text.
            return typingBuffer.getDocument().getText(0, typingBuffer.getDocument().getLength());
        } catch (Exception e) {
            return "";
        }
    }

    public void insertAtCaret(String str) 
    {
        if (typingBuffer == null || str == null) return;
        try {
            javax.swing.text.StyledDocument doc = typingBuffer.getStyledDocument();
            int p = typingBuffer.getCaretPosition();

            // Set the incoming text to pure white
            javax.swing.text.SimpleAttributeSet attr = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setForeground(attr, Color.WHITE);
            javax.swing.text.StyleConstants.setFontFamily(attr, "Monospaced");
            javax.swing.text.StyleConstants.setFontSize(attr, 14);

            doc.insertString(p, str, attr);
        } catch (Exception ignored) {}
    }

    public void deleteAtCaret() 
    {
        if (typingBuffer == null) return;
        try {
            int p = typingBuffer.getCaretPosition();
            if (p > 0) {
                typingBuffer.getStyledDocument().remove(p - 1, 1);
            }
        } catch (Exception ignored) {}
    }

    // ── THE COLOR-SHIFTING PAINTBRUSH ──
    public void applyHardwareTint(Color color) 
    {
        if (typingBuffer == null) return;
        try {
            javax.swing.text.StyledDocument doc = typingBuffer.getStyledDocument();
            int length = doc.getLength();
            
            if (length > 0) {
                javax.swing.text.SimpleAttributeSet attr = new javax.swing.text.SimpleAttributeSet();
                javax.swing.text.StyleConstants.setForeground(attr, color);
                
                // Target the very last character only, leaving the rest of the string untouched
                doc.setCharacterAttributes(length - 1, 1, attr, false);
            }
        } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────
    // LOGGING (WITH CLOUD MODE CONTROL)
    // ─────────────────────────────────────────────

    public void cacheChatData(String text) 
    {
        if (text == null || text.isEmpty()) return;

        if (state.isCloudModeActive) return;

        pendingLogs.add(text + "\n");
    }

    public void saveSession() 
    {
        if (state.isCloudModeActive) 
        {
            pendingLogs.clear();
            return;
        }

        if (pendingLogs.isEmpty()) return;

        File logFile = getSessionLogFile(state.currentSession);

        try {
            logFile.getParentFile().mkdirs();

            for (String entry : pendingLogs) 
            {
                Files.write(
                        logFile.toPath(),
                        entry.getBytes(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            }

            pendingLogs.clear();

        } 
        catch (IOException e) 
        {
            appendError("SESSION SAVE FAILED: " + e.getMessage());
        }
    }

    public void loadSession(int sessionId) 
    {
        saveSession();

        state.currentSession = sessionId;

        if (state.isCloudModeActive) 
        {
            state.isCloudModeActive = false;
            clearChat();
        }

        File logFile = getSessionLogFile(sessionId);

        if (!logFile.exists()) 
        {
            clearChat();
            appendSystem("SESSION INIT: FN" + sessionId);
            return;
        }

        try 
        {
            List<String> lines = Files.readAllLines(logFile.toPath());
            clearChat();
            
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(line.replace("&", "&amp;")
                              .replace("<", "&lt;")
                              .replace(">", "&gt;"))
                  .append("<br>");
            }
            appendHtml(sb.toString());

        } 
        catch (IOException e) 
        {
            appendError("LOAD FAILED");
        }
    }

    public File getSessionLogFile(int sessionId) 
    {
        File sessionDir = state.sessionDirectories.get(sessionId);

        if (sessionDir == null) 
        {
            sessionDir = new File(state.fnBaseDirectory, "fn" + sessionId);
        }

        return new File(sessionDir, "convodata.txt");
    }

    public void flushPendingLogsIfNeeded() 
    {
        if (!state.isCloudModeActive && !pendingLogs.isEmpty()) 
        {
            saveSession();
        }
    }

    // ─────────────────────────────────────────────
    // IMAGE MANIFEST SUPPORT
    // ─────────────────────────────────────────────

    public void logManifestImage(String fileName) 
    {
        if (fileName == null) return;

        state.sessionImagePaths.add(fileName);

        appendHtml("MANIFEST_IMAGE: <a href='image:" + fileName + "'>[" + fileName + "]</a><br>");

        if (!state.isCloudModeActive) 
        {
            pendingLogs.add("MANIFEST_IMAGE: " + fileName + "\n");
        }
    }

    // ─────────────────────────────────────────────
    // SESSION LOGGING
    // ─────────────────────────────────────────────

    public void writeToSessionLog(int sessionId, boolean isScript, String text) 
    {
        if (state.isCloudModeActive || text == null) return;

        File logFile = getSessionLogFile(sessionId);

        try 
        {
            logFile.getParentFile().mkdirs();
            Files.write(
                logFile.toPath(),
                text.getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } 
        catch (IOException ignored) {}
    }

    public void logUserAiInput(String text) 
    {
        String logStr = "#AI> USER> " + text + "\n";
        appendRaw(logStr);
        writeToSessionLog(state.currentSession, false, logStr);
    }

    public void logProcessInput(SessionProcess sp, String text) 
    {
        String displayStr = state.isSudoEnabled && text.equals(state.cachedSudoPassword)
                ? "********"
                : text;

        String logStr = "> " + displayStr + "\n";
        appendRaw(logStr);
        writeToSessionLog(state.currentSession, sp != null && sp.isScript, logStr);
    }

    public void logApiChat(int sessionId, String chat) 
    {
        String line = "GODDESS> " + chat + "\n";

        if (state.currentSession == sessionId) 
        {
            appendRaw(line);
        }

        writeToSessionLog(sessionId, false, line);
    }

    public void logApiStdout(int sessionId, String line) 
    {
        String out = "API_STDOUT> " + line + "\n";

        if (state.currentSession == sessionId) 
        {
            int id = rawDataCounter++;
            rawDataCache.put(String.valueOf(id), line);
            tagLabelCache.put(String.valueOf(id), "[rawdat]");
            appendHtml("<div id='raw_" + id + "'><a href='rawdat:" + id + "'>[rawdat]</a></div>");
        }

        writeToSessionLog(sessionId, false, out);
    }

    public void logUnknownApiTag(int sessionId, String line) 
    {
        String out = "API_TAG_UNKNOWN> " + line + "\n";

        if (state.currentSession == sessionId) 
        {
            int id = rawDataCounter++;
            rawDataCache.put(String.valueOf(id), line);
            tagLabelCache.put(String.valueOf(id), "[rawdat_unknown]");
            appendHtml("<div id='raw_" + id + "'><a href='rawdat:" + id + "'>[rawdat_unknown]</a></div>");
        }

        writeToSessionLog(sessionId, false, out);
    }
}
