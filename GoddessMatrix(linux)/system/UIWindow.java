package system;

/*
 * UIWindow.java
 *
 * Main JFrame/window layer for Goddess Matrix.
 *
 * Responsibilities:
 * - frame construction
 * - header/footer/status HUD
 * - matrix panel assembly
 * - drag/drop gateway
 * - session switching coordination
 * - API shutdown delegation
 * - OS script folder resolution
 */

import assets.MatrixConfig;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class UIWindow extends JFrame {

    private final MatrixState state;
    private final Keyboard keyboard;
    private final ChatHistory chatHistory;
    private final ImageViewer imageViewer;

    private JPanel headerPanel;
    private JPanel matrixPanel;
    private JPanel footerPanel;

    private JLabel titleLabel;
    private JLabel subtitleLabel;

    private JButton uiOptionBtn;
    private JPopupMenu uiMenu;

    private JComboBox<String> resCombo;
    private JComboBox<String> refreshCombo;

    public UIWindow(
            MatrixState state,
            Keyboard keyboard,
            ChatHistory chatHistory,
            ImageViewer imageViewer
    ) {
        this.state = state;
        this.keyboard = keyboard;
        this.chatHistory = chatHistory;
        this.imageViewer = imageViewer;
        state.uiWindow = this;
    }

    public void initialize() {
        setTitle("Goddess Input Matrix - " + MatrixConfig.VERSION_LABEL);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(MatrixConfig.BG_DARK);
        setLayout(new BorderLayout());

        buildHeader();
        buildMatrixPanel();
        buildFooter();

        add(headerPanel, BorderLayout.NORTH);
        add(matrixPanel, BorderLayout.CENTER);
        add(footerPanel, BorderLayout.SOUTH);

        applyWindowSize("720p");

        setLocationRelativeTo(null);
    }

    private void buildHeader() {
        headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(MatrixConfig.BG_DARK);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 27, 10, 27));

        JPanel titleGroup = new JPanel(new GridLayout(2, 1));
        titleGroup.setBackground(MatrixConfig.BG_DARK);

        titleLabel = new JLabel("GODDESS INPUT MATRIX", SwingConstants.CENTER);
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Inter", Font.BOLD, 20));

        subtitleLabel = new JLabel(
                "SPLIT-BRAIN SANDBOX • CHROOT MANAGER • MODULAR V14.4",
                SwingConstants.CENTER
        );
        subtitleLabel.setForeground(MatrixConfig.TEXT_COLOR);
        subtitleLabel.setFont(new Font("Monospaced", Font.PLAIN, 10));

        titleGroup.add(titleLabel);
        titleGroup.add(subtitleLabel);

        headerPanel.add(titleGroup, BorderLayout.NORTH);
        headerPanel.add(chatHistory.getScrollPane(), BorderLayout.CENTER);
        headerPanel.add(chatHistory.getTypingBuffer(), BorderLayout.SOUTH);
    }

    private void buildMatrixPanel() {
        matrixPanel = new JPanel(null);
        matrixPanel.setBackground(MatrixConfig.BG_DARK);
        matrixPanel.setBorder(BorderFactory.createEmptyBorder(0, 27, 20, 27));

        state.matrixPanel = matrixPanel;

        keyboard.attachToPanel(matrixPanel);
        imageViewer.attachToPanel(matrixPanel);
    }

    private void buildFooter() {
        footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(MatrixConfig.BG_DARK);
        footerPanel.setBorder(BorderFactory.createEmptyBorder(10, 27, 10, 27));

        JPanel leftFooter = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftFooter.setBackground(MatrixConfig.BG_DARK);

        state.statusLabel = new JLabel("READY_FOR_INTERRUPT... | ");
        state.statusLabel.setForeground(MatrixConfig.GODDESS_PURPLE);
        state.statusLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));

        state.modifierLabel = new JLabel("BRIDGE: OFFLINE | NAVIGATION: READY");
        state.modifierLabel.setForeground(MatrixConfig.TEXT_COLOR);
        state.modifierLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));

        leftFooter.add(state.statusLabel);
        leftFooter.add(state.modifierLabel);

        JPanel rightFooter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        rightFooter.setBackground(MatrixConfig.BG_DARK);

        buildUiOptionMenu();

        state.aiStatusLabel = new JLabel(MatrixConfig.API_OFFLINE);
        state.aiStatusLabel.setForeground(MatrixConfig.GODDESS_GOLD);
        state.aiStatusLabel.setFont(new Font("Monospaced", Font.BOLD, 12));

        rightFooter.add(uiOptionBtn);
        rightFooter.add(state.aiStatusLabel);

        footerPanel.add(leftFooter, BorderLayout.WEST);
        footerPanel.add(rightFooter, BorderLayout.EAST);
    }

    private void buildUiOptionMenu() {
        uiMenu = new JPopupMenu();
        uiMenu.setBackground(MatrixConfig.KEY_BG);
        uiMenu.setBorder(new LineBorder(new Color(157, 80, 187, 80), 1));

        // ── RESOLUTION CASCADING MENU ─────────────────────────────────────────
        JMenu resMenu = new JMenu("RESOLUTION ► ");
        resMenu.setForeground(MatrixConfig.GODDESS_GOLD);
        resMenu.setFont(new Font("Monospaced", Font.PLAIN, 11));
        resMenu.getPopupMenu().setBackground(MatrixConfig.KEY_BG);
        resMenu.getPopupMenu().setBorder(new LineBorder(new Color(157, 80, 187, 80), 1));

        String[] resOptions = {"1080p", "720p", "4K"};
        for (String res : resOptions) {
            JMenuItem item = new JMenuItem(res);
            item.setBackground(MatrixConfig.KEY_BG);
            item.setForeground(MatrixConfig.TEXT_COLOR);
            item.setFont(new Font("Monospaced", Font.PLAIN, 11));
            item.addActionListener(e -> {
                applyWindowSize(res);
                if (chatHistory != null) {
                    chatHistory.appendSystem("RESOLUTION_MODE_CHANGED: " + res);
                }
            });
            resMenu.add(item);
        }

        // ── REFRESH RATE CASCADING MENU ───────────────────────────────────────
        JMenu refreshMenu = new JMenu("REFRESH_RATE ► ");
        refreshMenu.setForeground(MatrixConfig.GODDESS_GOLD);
        refreshMenu.setFont(new Font("Monospaced", Font.PLAIN, 11));
        refreshMenu.getPopupMenu().setBackground(MatrixConfig.KEY_BG);
        refreshMenu.getPopupMenu().setBorder(new LineBorder(new Color(157, 80, 187, 80), 1));

        String[] refOptions = {"Auto", "60Hz", "120Hz", "144Hz", "160Hz", "166Hz", "240Hz"};
        for (String ref : refOptions) {
            JMenuItem item = new JMenuItem(ref);
            item.setBackground(MatrixConfig.KEY_BG);
            item.setForeground(MatrixConfig.TEXT_COLOR);
            item.setFont(new Font("Monospaced", Font.PLAIN, 11));
            item.addActionListener(e -> {
                imageViewer.updateRefreshRate(ref);
            });
            refreshMenu.add(item);
        }

        uiMenu.add(resMenu);
        uiMenu.add(refreshMenu);

        // ── TRIGGER BUTTON ────────────────────────────────────────────────────
        uiOptionBtn = new JButton("<ui_option()>");
        uiOptionBtn.setBackground(MatrixConfig.BG_DARK);
        uiOptionBtn.setForeground(MatrixConfig.TEXT_COLOR);
        uiOptionBtn.setFont(new Font("Monospaced", Font.PLAIN, 11));
        uiOptionBtn.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        uiOptionBtn.setFocusPainted(false);
        uiOptionBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        uiOptionBtn.addActionListener(e -> {
            uiOptionBtn.setForeground(MatrixConfig.GODDESS_GOLD);
            uiMenu.show(uiOptionBtn, 0, -uiMenu.getPreferredSize().height - 5);
        });

        uiMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {}
            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                uiOptionBtn.setForeground(MatrixConfig.TEXT_COLOR);
            }
            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                uiOptionBtn.setForeground(MatrixConfig.TEXT_COLOR);
            }
        });
    }

    private JComboBox<String> createDarkComboBox(String[] items) {
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setBackground(MatrixConfig.KEY_BG);
        combo.setForeground(MatrixConfig.GODDESS_GOLD);
        combo.setFont(new Font("Monospaced", Font.PLAIN, 10));
        combo.setBorder(new LineBorder(new Color(157, 80, 187, 80), 1));
        combo.setFocusable(false);
        return combo;
    }

    private void applyWindowSize(String mode) {
        float scale = 1.0f;
        switch (mode) {
            case "1080p":
                setSize(1450, 960);
                scale = 1.35f;
                break;

            case "4K":
                setSize(1800, 1100);
                scale = 1.8f;
                break;

            case "720p":
            default:
                setSize(1100, 760);
                scale = 1.0f;
                break;
        }
        // Sets position ui after resize
        setLocationRelativeTo(null);

        // Broadcast the scale factor to all subsystems
        if (keyboard != null) keyboard.applyScale(scale);
        if (chatHistory != null) chatHistory.applyScale(scale);
        if (imageViewer != null) imageViewer.applyScale(scale);

        // Scale the main UIWindow HUD text
        titleLabel.setFont(new Font("Inter", Font.BOLD, (int)(20 * scale)));
        subtitleLabel.setFont(new Font("Monospaced", Font.PLAIN, (int)(10 * scale)));
        state.statusLabel.setFont(new Font("Monospaced", Font.PLAIN, (int)(12 * scale)));
        state.modifierLabel.setFont(new Font("Monospaced", Font.PLAIN, (int)(12 * scale)));
        state.aiStatusLabel.setFont(new Font("Monospaced", Font.BOLD, (int)(12 * scale)));
        uiOptionBtn.setFont(new Font("Monospaced", Font.PLAIN, (int)(11 * scale)));

        SwingUtilities.invokeLater(() -> {
            if (matrixPanel != null) { matrixPanel.revalidate(); matrixPanel.repaint(); }
            if (headerPanel != null) { headerPanel.revalidate(); headerPanel.repaint(); }
            if (footerPanel != null) { footerPanel.revalidate(); footerPanel.repaint(); }
            revalidate();
            repaint();
        });

        SwingUtilities.invokeLater(() -> 
        {
            if (matrixPanel != null) 
            {
                matrixPanel.revalidate();
                matrixPanel.repaint();
            }

            if (headerPanel != null) 
            {
                headerPanel.revalidate();
                headerPanel.repaint();
            }

            if (footerPanel != null) 
            {
                footerPanel.revalidate();
                footerPanel.repaint();
            }

            revalidate();
            repaint();
        });
    }

    public void installHardwareBridge() {
        keyboard.installHardwareBridge();
    }

    public void installDragAndDrop() {
        new DropTarget(this, new DropTargetAdapter() {
            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);

                    Transferable transferable = dtde.getTransferable();

                    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        return;
                    }

                    List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

                    for (File file : files) {
                        handleDroppedFile(file);
                    }

                } catch (Exception ex) {
                    chatHistory.appendError("DND_IO_ERROR: " + ex.getMessage());

                    if (state.statusLabel != null) {
                        state.statusLabel.setText("SYS_DND: FAILED");
                    }
                }
            }
        });
    }

    private void handleDroppedFile(File file) {
        String name = file.getName().toLowerCase();

        if (name.endsWith(".html")) {
            importHtmlFile(file);
            return;
        }

        if (state.isScriptModeActive && (name.endsWith(".sh") || name.endsWith(".bat"))) {
            importScriptFile(file);
        }
    }

    private void importHtmlFile(File file) {
        try {
            state.htmlDirectory.mkdirs();

            File targetFile = new File(state.htmlDirectory, file.getName());

            Files.copy(
                    file.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            targetFile.setReadable(true, false);

            chatHistory.appendSystem(
                    "DND_GATEWAY: IMPORTED [" + file.getName() + "] TO /HTML/ [WEB_PERMISSIONS_GRANTED]"
            );

            if (state.statusLabel != null) {
                state.statusLabel.setText("SYS_DND: HTML_SUCCESS");
            }

        } catch (Exception e) {
            chatHistory.appendError("HTML_IMPORT_FAILED: " + e.getMessage());

            if (state.statusLabel != null) {
                state.statusLabel.setText("SYS_DND: FAILED");
            }
        }
    }

    private void importScriptFile(File file) {
        try {
            File scriptDir = getOSScriptFolderFile();
            scriptDir.mkdirs();

            File targetFile = new File(scriptDir, file.getName());

            Files.copy(
                    file.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            if (!state.isWindows && file.getName().toLowerCase().endsWith(".sh")) {
                targetFile.setExecutable(true);
            }

            chatHistory.appendSystem(
                    "DND_GATEWAY: SCRIPT [" + file.getName() + "] ADDED TO " + scriptDir.getAbsolutePath()
            );

            if (state.statusLabel != null) {
                state.statusLabel.setText("SYS_DND: SCRIPT_READY");
            }

        } catch (Exception e) {
            chatHistory.appendError("SCRIPT_IMPORT_FAILED: " + e.getMessage());

            if (state.statusLabel != null) {
                state.statusLabel.setText("SYS_DND: FAILED");
            }
        }
    }

    public File getOSScriptFolderFile() {
        String subDir = "Linux";

        if (state.isWindows) {
            subDir = "Windows";
        } else if (state.isMac) {
            subDir = "MacOSY";
        }

        return new File(state.scriptRootDirectory, subDir);
    }

    public void loadInitialSession() {
        loadSession(state.currentSession);
        imageViewer.updateRefreshRate("Auto");
    }

    public void loadSession(int sessionId) {
        chatHistory.loadSession(sessionId);
        imageViewer.loadSession(sessionId);
        keyboard.updateModifierVisuals();

        if (state.statusLabel != null) {
            state.statusLabel.setText("LINK_ESTABLISHED: SECTOR_FN" + sessionId);
        }

        if (state.aiStatusLabel != null) {
            state.aiStatusLabel.setText(
                    state.apiStatusMap.getOrDefault(sessionId, MatrixConfig.API_OFFLINE)
            );
        }
    }

    public void saveSession() {
        chatHistory.saveSession();
    }

    public void stopGoddessAPI(int sessionId) {
        Process process = state.apiProcessMap.get(sessionId);
        java.io.PrintWriter stdin = state.apiStdinMap.get(sessionId);

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
            }).start();
        }

        state.apiProcessMap.remove(sessionId);
        state.apiStdinMap.remove(sessionId);
        state.apiStatusMap.put(sessionId, MatrixConfig.API_OFFLINE);

        if (state.currentSession == sessionId && state.aiStatusLabel != null) {
            state.aiStatusLabel.setText(MatrixConfig.API_OFFLINE);
        }

        if (state.isVideoStreamActive) {
            imageViewer.stopVideoStream();
        }

        imageViewer.stopAIRenderer();
    }

    public JPanel getMatrixPanel() {
        return matrixPanel;
    }

    public void showWindow() {
        setVisible(true);
    }
}
