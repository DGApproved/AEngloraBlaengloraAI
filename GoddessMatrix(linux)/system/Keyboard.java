package system;

/*
 * Keyboard.java
 *
 * Keyboard / physical control surface for Goddess Matrix.
 *
 * Responsibilities:
 * - key layout
 * - key mapping
 * - modifier state
 * - button routing
 * - extension buttons 107-112
 * - optional modular feature launch by reflection
 * - analog debounce noise filter (tunable via experimental.txt)
 * - universal USB key passthrough (fault tolerant)
 * - dynamic module mounting via matrix_modules.cfg
 * - chassis introspection (JAR vs IDE remapping)
 */

import assets.MatrixConfig;
import assets.CalculatorEngine;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Keyboard 
{
    private final MatrixState state;
    private JPanel panel;

    private final Map<Integer, JButton>   buttons    = new HashMap<>();
    private final Map<Integer, String[]>  dualKeyMap = createDualKeyMap();
    //DynamicScaling
    private final Map<Integer, java.awt.Rectangle> baseBounds = new HashMap<>();
    private float currentScale = 1.0f;

    public Keyboard(MatrixState state) 
    {
        this.state = state;
        state.keyboard  = this;
        state.buttons   = buttons;
        state.dualKeyMap = dualKeyMap;
    }

    public void initialize() 
    {
        try {
            // Read the physical hardware LED to perfectly sync the matrix state on boot
            state.isNumLockActive = Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_NUM_LOCK);
        } catch (Exception ignored) {}
        updateModifierVisuals();
    }

    public void attachToPanel(JPanel p) 
    {
        this.panel = p;
        setupKeyboard(p);
    }

    // ── KEYBOARD LAYOUT ──────────────────────────────────────────────────────

    private void setupKeyboard(JPanel panel) 
    {
        int currentY = 10;
        int currentX = 27;

        for (int i = 1; i <= 17; i++) {
            panel.add(createKey(i, currentX, currentY,
                    (int)(MatrixConfig.KEY_U * 0.9), MatrixConfig.KEY_H));
            currentX += (int)(MatrixConfig.KEY_U * 0.9) + MatrixConfig.KEY_GAP;
        }

        currentY += MatrixConfig.KEY_H + MatrixConfig.KEY_GAP;
        currentX  = 27;

        panel.add(createKey(18, currentX, currentY,
                (int)(MatrixConfig.KEY_U * 0.8), MatrixConfig.KEY_H));
        currentX += (int)(MatrixConfig.KEY_U * 0.8) + MatrixConfig.KEY_GAP;

        for (int i = 19; i <= 30; i++) {
            panel.add(createKey(i, currentX, currentY, MatrixConfig.KEY_U, MatrixConfig.KEY_H));
            currentX += MatrixConfig.KEY_U + MatrixConfig.KEY_GAP;
        }

        panel.add(createKey(31, currentX, currentY,
                (int)(MatrixConfig.KEY_U * 2.2) + 15, MatrixConfig.KEY_H));

        currentY += MatrixConfig.KEY_H + MatrixConfig.KEY_GAP;
        currentX  = 27;

        panel.add(createKey(32, currentX, currentY,
                (int)(MatrixConfig.KEY_U * 1.3), MatrixConfig.KEY_H));
        currentX += (int)(MatrixConfig.KEY_U * 1.3)
                  + (int)(MatrixConfig.KEY_U * 0.2)
                  + MatrixConfig.KEY_GAP;

        for (int i = 33; i <= 44; i++) {
            panel.add(createKey(i, currentX, currentY, MatrixConfig.KEY_U, MatrixConfig.KEY_H));
            currentX += MatrixConfig.KEY_U + MatrixConfig.KEY_GAP;
        }

        currentX += (int)(MatrixConfig.KEY_U * 0.2);
        panel.add(createKey(45, currentX, currentY,
                (int)(MatrixConfig.KEY_U * 1.7), MatrixConfig.KEY_H));

        currentY += MatrixConfig.KEY_H + MatrixConfig.KEY_GAP;
        currentX  = 27;

        panel.add(createKey(46, currentX, currentY,
                (int)(MatrixConfig.KEY_U * 1.5), MatrixConfig.KEY_H));
        currentX += (int)(MatrixConfig.KEY_U * 1.5)
                  + (int)(MatrixConfig.KEY_U * 0.2)
                  + MatrixConfig.KEY_GAP;

        for (int i = 47; i <= 57; i++) {
            panel.add(createKey(i, currentX, currentY, MatrixConfig.KEY_U, MatrixConfig.KEY_H));
            currentX += MatrixConfig.KEY_U + MatrixConfig.KEY_GAP;
        }

        currentX += (int)(MatrixConfig.KEY_U * 0.2);
        panel.add(createKey(58, currentX, currentY,
                (int)(MatrixConfig.KEY_U * 2.6), MatrixConfig.KEY_H));

        currentY += MatrixConfig.KEY_H + MatrixConfig.KEY_GAP;
        currentX  = 27;

        panel.add(createKey(59, currentX, currentY,
                (int)(MatrixConfig.KEY_U * 2.2), MatrixConfig.KEY_H));
        currentX += (int)(MatrixConfig.KEY_U * 2.2)
                  + (int)(MatrixConfig.KEY_U * 0.2)
                  + MatrixConfig.KEY_GAP;

        for (int i = 60; i <= 69; i++) {
            panel.add(createKey(i, currentX, currentY, MatrixConfig.KEY_U, MatrixConfig.KEY_H));
            currentX += MatrixConfig.KEY_U + MatrixConfig.KEY_GAP;
        }

        currentX += (int)(MatrixConfig.KEY_U * 0.2);
        panel.add(createKey(70, currentX, currentY,
                (int)(MatrixConfig.KEY_U * 2.7) + 15, MatrixConfig.KEY_H));

        currentY += MatrixConfig.KEY_H + MatrixConfig.KEY_GAP;
        currentX  = 27;

        for (int i = 71; i <= 74; i++) {
            panel.add(createKey(i, currentX, currentY,
                    (int)(MatrixConfig.KEY_U * 1.1), MatrixConfig.KEY_H));
            currentX += (int)(MatrixConfig.KEY_U * 1.1) + MatrixConfig.KEY_GAP;
        }

        panel.add(createKey(75, currentX, currentY,
                (int)(MatrixConfig.KEY_U * 5.3), MatrixConfig.KEY_H));
        currentX += (int)(MatrixConfig.KEY_U * 5.3) + MatrixConfig.KEY_GAP;

        for (int i = 76; i <= 78; i++) {
            panel.add(createKey(i, currentX, currentY,
                    (int)(MatrixConfig.KEY_U * 1.1), MatrixConfig.KEY_H));
            currentX += (int)(MatrixConfig.KEY_U * 1.1) + MatrixConfig.KEY_GAP;
        }

        panel.add(createKey(79, currentX, currentY,
                MatrixConfig.KEY_U, (int)(MatrixConfig.KEY_H * 0.4)));
        panel.add(createKey(80, currentX,
                currentY + (int)(MatrixConfig.KEY_H * 0.6),
                MatrixConfig.KEY_U, (int)(MatrixConfig.KEY_H * 0.4)));

        currentX += MatrixConfig.KEY_U + MatrixConfig.KEY_GAP;
        panel.add(createKey(81, currentX, currentY, MatrixConfig.KEY_U, MatrixConfig.KEY_H));

        currentX += MatrixConfig.KEY_U + MatrixConfig.KEY_GAP;
        panel.add(createKey(82, currentX, currentY,
                (int)(MatrixConfig.KEY_U * 0.8), MatrixConfig.KEY_H));

        // ── NUMPAD ────────────────────────────────────────────────────────────
        int numBaseX = currentX + 60;
        int numX = numBaseX;
        int numY = 10;

        int[] numIndices = {83,84,85,86,87,88,89,90,91,92,93,94,95,96,97,98,99,100};

        for (int i = 0; i < numIndices.length; i++) {
            panel.add(createKey(numIndices[i], numX, numY,
                    MatrixConfig.KEY_U, MatrixConfig.KEY_H));
            numX += MatrixConfig.KEY_U + MatrixConfig.KEY_GAP;
            if ((i + 1) % 3 == 0) {
                numX  = numBaseX;
                numY += MatrixConfig.KEY_H + MatrixConfig.KEY_GAP;
            }
        }

        // ── ACTION COLUMN (101-106) ───────────────────────────────────────────
        int actionBaseX = numBaseX + 3 * (MatrixConfig.KEY_U + MatrixConfig.KEY_GAP) + 15;
        int actionY     = 10;

        int[] actionIndices = {101, 102, 103, 104, 105, 106};

        for (int id : actionIndices) {
            panel.add(createKey(id, actionBaseX, actionY,
                    MatrixConfig.KEY_U + 10, MatrixConfig.KEY_H));
            actionY += MatrixConfig.KEY_H + MatrixConfig.KEY_GAP;
        }

        // ── EXTENSION COLUMN (107-112) ────────────────────────────────────────
        int extX = actionBaseX + (MatrixConfig.KEY_U + 10) + MatrixConfig.KEY_GAP;
        int extY = 10;

        int[] extIndices = {107, 108, 109, 110, 111, 112};

        for (int id : extIndices) {
            panel.add(createKey(id, extX, extY,
                    MatrixConfig.KEY_U + 10, MatrixConfig.KEY_H));
            extY += MatrixConfig.KEY_H + MatrixConfig.KEY_GAP;
        }
    }

    private JButton createKey(int index, int x, int y, int w, int h) 
    {
        JButton b = new JButton();
        b.setBounds(x, y, w, h);
        b.setBackground(MatrixConfig.KEY_BG);
        b.setFocusPainted(false);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 10), 1));

        // ── COMPLEX HARDWARE BUTTONS (PWR(calc) & GAME) ──
        if (index == 85 || index == 111) {
            b.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (index == 111) {
                        // [Game] Button Logic
                        if (SwingUtilities.isRightMouseButton(e) && state.isGameModeActive && state.imageViewer != null) {
                            state.imageViewer.enterCinematicPassthrough();
                        } else if (SwingUtilities.isLeftMouseButton(e)) {
                            handleMatrixEvent(111, b, true);
                        }
                    } else if (index == 85) {
                        // [PWR] Button Logic: Two-Stage Calculator Security
                        if (SwingUtilities.isRightMouseButton(e)) {
                            state.isCalculatorUnlocked = !state.isCalculatorUnlocked;
                            setStatus("SYS_CALC: " + (state.isCalculatorUnlocked ? "UNLOCKED" : "LOCKED"));
                            flashButton(b, true);
                        } else if (SwingUtilities.isLeftMouseButton(e)) {
                            if (state.isCalculatorUnlocked) {
                                // Route through CalculatorEngine.togglePower()
                                // so the 0-29 boot sequence runs on power-on.
                                String bootResult = CalculatorEngine.togglePower();
                                state.isCalculatorEnabled = CalculatorEngine.isPoweredOn();

                                setStatus("SYS_CALC: "
                                        + (state.isCalculatorEnabled ? "POWER ON" : "POWER OFF"));

                                if (state.chatHistory != null)
                                    state.chatHistory.appendSystem(bootResult);

                                if (state.isCalculatorEnabled)
                                {
                                    // Auto-switch ImageViewer to Mode C
                                    state.manifestViewMode = 2;
                                    if (state.imageViewer != null)
                                        state.imageViewer.getPanel().repaint();
                                }
                                else
                                {
                                    // Collapse graph if open when powering off
                                    if (state.imageViewer != null)
                                        state.imageViewer.collapseGraphMode();
                                    state.manifestViewMode = 0;
                                }
                            } else {
                                setStatus("SYS_CALC: LOCKED (R-CLICK TO UNLOCK)");
                            }
                            flashButton(b, true);
                        }
                    }
                }
            });
        } else {
            // Standard Buttons
            b.addActionListener(e -> handleMatrixEvent(index, b, true));
        }

        baseBounds.put(index, new java.awt.Rectangle(x, y, w, h));
        buttons.put(index, b);
        return b;
    }

    public void applyScale(float scale) {
        this.currentScale = scale;
        
        // Multiply the exact coordinates and dimensions of every single key
        for (Map.Entry<Integer, JButton> entry : buttons.entrySet()) {
            int id = entry.getKey();
            JButton btn = entry.getValue();
            java.awt.Rectangle base = baseBounds.get(id);
            if (base != null) {
                btn.setBounds((int)(base.x * scale), (int)(base.y * scale), 
                              (int)(base.width * scale), (int)(base.height * scale));
            }
        }
        updateModifierVisuals(); // Forces the fonts to scale to match the new key sizes
    }

    // ── HARDWARE BRIDGE ───────────────────────────────────────────────────────

    public void installHardwareBridge() 
    {
        resolveDebounceSettings();

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(e -> {

            // ── MODULAR KEY HOOK ──────────────────────────────────────────────
            // Called first, before all Matrix logic, for every event type.
            // The registered module decides what to consume and what to ignore.
            // Register via state.keyHook in the modular feature; clear on stop.
            if (state.keyHook != null) {
                try {
                    if (state.keyHook.onKeyEvent(e)) return true; // consumed
                } catch (Exception ex) {
                    // Module fault — clear hook and log so Matrix stays running
                    state.keyHook = null;
                    if (state.chatHistory != null)
                        state.chatHistory.logModularCrash("KeyHook", ex);
                }
            }

            if (e.getID() != KeyEvent.KEY_PRESSED)
            {
                return false;
            }

            // ── PHYSICAL NUMLOCK ENFORCEMENT ──
            // Allows numpad through if software NumLock is engaged OR the Calculator is active
            if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD 
                && !state.isNumLockActive 
                && !state.isCalculatorEnabled) {
                return true;
            }

            // ── CINEMATIC PASSTHROUGH (non-modular AI sessions) ───────────────
            // Only fires when no modular hook is registered.
            if (state.keyHook == null && state.imageViewer != null
                    && state.imageViewer.isCinematicPassthrough()) {
                state.imageViewer.routeKeyPressToSession(e.getKeyCode(), e.getKeyChar());
                return true;
            }

            // ── ANALOG DEBOUNCE ───────────────────────────────────────────────
            if (state.debounceEnabled) {
                long now = System.currentTimeMillis();
                if (e.getKeyCode() == state.lastKeyCode
                        && (now - state.lastKeyPressTime) < state.debounceMs) {
                    return true; 
                }
                state.lastKeyCode      = e.getKeyCode();
                state.lastKeyPressTime = now;
            }

            if (e.getKeyCode() == KeyEvent.VK_PAUSE) {
                handleMatrixEvent(14, buttons.get(14), false);
                return true;
            }

            int index = mapKeyCodeToIndex(e.getKeyCode(), e.getKeyLocation());

            if (state.isUserModeActive) {
                if (index != -1) {
                    flashButton(buttons.get(index), false);
                    if (state.chatHistory != null) {
                        state.chatHistory.cacheChatData(
                                "SYS_USER_MODE: CONTROL_SIGNAL_INDEX_" + index);
                    }
                }
                return true;
            }

            if (index != -1) {
                JButton btn = buttons.get(index);
                if (btn != null) handleMatrixEvent(index, btn, false);
                return true;
            }

            return false;
        });
    }

    private void resolveDebounceSettings() 
    {
        state.debounceEnabled = true;
        state.debounceMs      = 40;

        try {
            java.io.File sessionDir = state.sessionDirectories.get(state.currentSession);
            if (sessionDir == null) sessionDir = new java.io.File(
                    state.aiHomeDirectory, "fn/fn1");

            java.io.File expFile = new java.io.File(
                    sessionDir, "dgapi/datas/experimental.txt");

            if (!expFile.exists()) return; 

            for (String line : java.nio.file.Files.readAllLines(expFile.toPath())) {
                line = line.trim();
                if (line.startsWith("#") || !line.contains("=")) continue;

                String key = line.substring(0, line.indexOf('=')).trim();
                String val = line.substring(line.indexOf('=') + 1).trim();
                String[] parts = val.split(":", 3);

                if ("ANALOG_DEBOUNCE".equals(key) && parts.length >= 2) {
                    state.debounceEnabled = "on".equalsIgnoreCase(parts[1].trim());
                }

                if ("ANALOG_DEBOUNCE_MS".equals(key) && parts.length >= 3) {
                    try {
                        int ms = Integer.parseInt(parts[2].trim());
                        if (ms > 0) state.debounceMs = ms;
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ── SYSTEM INTROSPECTION ──────────────────────────────────────────────────

    /**
     * Detects if the OS is compiled (JAR) or on a test bench (IDE/Filesystem)
     */
    private boolean isRunningFromJar() {
        String className = this.getClass().getName().replace('.', '/') + ".class";
        java.net.URL classUrl = this.getClass().getClassLoader().getResource(className);
        return classUrl != null && classUrl.getProtocol().equals("jar");
    }

    // ── EVENT DISPATCH ────────────────────────────────────────────────────────

    public void handleMatrixEvent(int index, JButton btn, boolean isMouse) 
    {
        // ── EXIT ──────────────────────────────────────────────────────────────
        if (index == 101) {
            if (state.uiWindow != null) state.uiWindow.saveSession();
            for (int sessionId = 1; sessionId <= 12; sessionId++) {
                if (state.uiWindow != null) state.uiWindow.stopGoddessAPI(sessionId);
            }
            System.exit(0);
        }

        // ── HARD INTERRUPT ────────────────────────────────────────────────────
        if (index == 102) {
            hardInterrupt();
            return;
        }

        // ── CLEAR BUFFER ──────────────────────────────────────────────────────
        if (index == 103) {
            if (state.chatHistory != null) state.chatHistory.clearTypingBuffer();
            state.isAltPending  = false;
            state.isInsPending  = false;
            setStatus("SYS_STATE: BUFFER_PURGED");
            updateModifierVisuals();
            return;
        }

        // ── MODE BUTTONS ──────────────────────────────────────────────────────
        if (index == 104) { toggleExecMode();   return; }
        if (index == 105) { toggleAIMode();     return; }
        if (index == 106) { toggleScriptMode(); return; }

        // ── EXTENSION COLUMN ──────────────────────────────────────────────────
        if (index >= 107 && index <= 112) {
            handleExtensionButton(index);
            return;
        }

        // ── NTR ───────────────────────────────────────────────────────────────
        if (index == 14) {
            boolean doChroot = state.isFnPending;
            state.isFnPending = false;
            if (state.terminalRunner != null) {
                state.terminalRunner.launchExternalTerminal(doChroot);
            } else {
                setStatus(doChroot ? "SYS_EXEC: CHROOT_REQUESTED"
                                   : "SYS_EXEC: TERMINAL_REQUESTED");
            }
            flashButton(btn, isMouse);
            updateModifierVisuals();
            return;
        }

        // ── INTERFACE BRIDGE TOGGLE ───────────────────────────────────────────
        if (index == 82) {
            state.interfaceBridgeActive = !state.interfaceBridgeActive;
            updateModifierVisuals();
            return;
        }

        if (!isMouse && state.interfaceBridgeActive) {
            flashButton(btn, false);
            return;
        }

        // ── CLIPBOARD ─────────────────────────────────────────────────────────
        if (index == 15) {
            handleClipboard(btn, isMouse);
            return;
        }

        // ── NUMPAD GALLERY / VISIBILITY ───────────────────────────────────────
        boolean isPadActive = !state.isNumLockActive || !state.isCalculatorEnabled;

        if (!isPadActive) {
            if (index == 86) { if (state.imageViewer != null) state.imageViewer.cycleGallery(-1); return; }
            if (index == 87) { if (state.imageViewer != null) state.imageViewer.cycleGallery(1);  return; }
            if (index == 100){ if (state.imageViewer != null) state.imageViewer.toggleManifestVisibility(); return; }
        }

        // ── MODIFIER KEYS ─────────────────────────────────────────────────────
        if (index == 85) {
            if (state.isCapsLockActive && state.isShiftPending) {
                state.isCalculatorEnabled = !state.isCalculatorEnabled;
                state.isShiftPending = false;
            }
            updateModifierVisuals();
            return;
        }

        if (index == 46) {
            if (!state.isCalculatorEnabled) {
                state.isCapsLockActive = !state.isCapsLockActive;
                if (!state.isCapsLockActive) state.isShiftPending = false;
            }
            updateModifierVisuals();
            return;
        }

        if (index == 88) { state.isNumLockActive = !state.isNumLockActive; updateModifierVisuals(); return; }
        if (index == 16) { state.isInsPending    = !state.isInsPending;    updateModifierVisuals(); return; }

        if (index == 72) {
            long now = System.currentTimeMillis();
            if (now - state.lastFnClick < 400) {
                if (state.uiWindow != null) state.uiWindow.saveSession();
                state.isFnPending = false;
            } else {
                state.isFnPending = !state.isFnPending;
            }
            state.lastFnClick = now;
            updateModifierVisuals();
            return;
        }

        if (index >= 2 && index <= 13 && state.isFnPending) {
            int sessionId = index - 1;
            if (state.uiWindow != null) state.uiWindow.loadSession(sessionId);
            state.isFnPending = false;
            updateModifierVisuals();
            return;
        }

        if (index == 17 && state.isAltPending) {
            state.isAltDeleteCombo = true;
            state.isAltPending     = false;
            updateModifierVisuals();
            return;
        }

        if (index == 71 || index == 77) {
            state.isCtrlPending  = !state.isCtrlPending;
            state.isCtrlAltCombo = state.isCtrlPending && state.isAltPending;
            updateModifierVisuals();
            return;
        }

        if (index == 59 || index == 70) {
            state.isShiftPending    = !state.isShiftPending;
            state.numLockVisualState = 0;
            updateModifierVisuals();
            return;
        }

        if (index == 74 || index == 76) {
            state.isAltPending     = !state.isAltPending;
            state.isAltDeleteCombo = false;
            state.isCtrlAltCombo   = state.isCtrlPending && state.isAltPending;
            updateModifierVisuals();
            return;
        }

        // ── CHARACTER / TYPING ────────────────────────────────────────────────
        String[] labels = dualKeyMap.getOrDefault(index, new String[]{"ERR", ""});
        setStatus("ID_" + index + ": " + labels[0]);
        flashButton(btn, isMouse);
        processTyping(index, labels);
    }

    // ── EXTENSION BUTTONS & MODULAR MOUNTING ──────────────────────────────────

    private void handleExtensionButton(int index) 
    {
        launchOptionalModule(index);
    }

    private void launchOptionalModule(int buttonIndex) {
        String target = resolveModuleTarget(buttonIndex);
        
        if (target.equals("[NATIVE_DIR]")) {
            openScriptsFolderNative();
            return;
        }

        try {
            Class<?> cls;
            boolean isCompiledOS = isRunningFromJar();

            // ── CHASSIS AWARENESS ROUTING ──
            if (!isCompiledOS && target.endsWith(".jar")) {
                target = "modular." + target.replace(".jar", "");
                if (state.chatHistory != null) {
                    state.chatHistory.appendSystem("DEV_MODE: Remapped [" + target + ".jar] to native class [" + target + "]");
                }
            }

            // ── USB PLUG & PLAY MOUNTING ──
            if (isCompiledOS && target.endsWith(".jar")) {
                File jarFile = new File("modular/" + target);
                if (!jarFile.exists()) throw new FileNotFoundException("Missing Expansion Drive: " + target);
                
                URLClassLoader child = new URLClassLoader(new URL[]{jarFile.toURI().toURL()}, this.getClass().getClassLoader());
                
                String expectedClassName = "modular." + target.replace(".jar", ""); 
                cls = Class.forName(expectedClassName, true, child);
            } else {
                cls = Class.forName(target);
            }

            // Standard Boot Sequence
            java.lang.reflect.Constructor<?> ctor = cls.getConstructor(system.MatrixState.class);
            Object module = ctor.newInstance(state);
            java.lang.reflect.Method launch = cls.getMethod("launch");
            launch.invoke(module);
            
            setStatus("SYS_MODULE: " + target + " ONLINE");

        } catch (Exception e) {
            // HARDWARE FAULT LOGGING
            if (state.chatHistory != null) {
                state.chatHistory.logModularCrash("Boot Failure: " + target, e);
            }
            setStatus("SYS_MODULE: FAULT [" + buttonIndex + "]");
        }
    }

    private String resolveModuleTarget(int buttonIndex) {
        File configFile = new File("modular/matrix_modules.cfg");
        String defaultTarget = "modular.Unknown";

        // Fallbacks if the config file gets deleted
        switch (buttonIndex) {
            case 107: defaultTarget = "modular.Update"; break;
            case 108: defaultTarget = "[NATIVE_DIR]"; break;
            case 109: defaultTarget = "modular.LLMs"; break;
            case 110: defaultTarget = "modular.Terminal"; break;
            case 111: defaultTarget = "modular.game.IceSandbox"; break;
            case 112: defaultTarget = "modular.App"; break;
        }

        if (!configFile.exists()) return defaultTarget;

        try {
            Properties props = new Properties();
            props.load(Files.newInputStream(configFile.toPath()));
            return props.getProperty("BTN_" + buttonIndex, defaultTarget);
        } catch (Exception e) {
            return defaultTarget;
        }
    }

    private void openScriptsFolderNative() 
    {
        try {
            if (state.uiWindow == null) { setStatus("SYS_DIR: NO_WINDOW"); return; }

            java.io.File dir = state.uiWindow.getOSScriptFolderFile();
            dir.mkdirs();

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", dir.getAbsolutePath()});
            }

            if (state.chatHistory != null) {
                state.chatHistory.appendSystem(
                        "NATIVE_EXPLORER_TRIGGERED [" + dir.getAbsolutePath() + "]");
            }
            setStatus("SYS_DIR: FOLDER_OPENED");

        } catch (Exception e) {
            if (state.chatHistory != null)
                state.chatHistory.appendError("FOLDER_OPEN_FAILED: " + e.getMessage());
            setStatus("SYS_DIR: ERROR");
        }
    }

    // ── MODE TOGGLES ──────────────────────────────────────────────────────────

    private void hardInterrupt() 
    {
        SessionProcess sp = state.processMap.get(state.currentSession);
        if (sp != null && sp.isAlive()) {
            sp.destroy();
            if (state.chatHistory != null) {
                state.chatHistory.appendSystem("ACTIVE_PROCESS_TERMINATED_VIA_HARD_INTERRUPT");
                state.chatHistory.writeToSessionLog(state.currentSession, sp.isScript,
                        "SYSTEM> ACTIVE_PROCESS_TERMINATED_VIA_HARD_INTERRUPT\n");
            }
        }

        Process apiProcess = state.apiProcessMap.get(state.currentSession);
        if (apiProcess != null && apiProcess.isAlive() && state.uiWindow != null) {
            state.uiWindow.stopGoddessAPI(state.currentSession);
            if (state.chatHistory != null)
                state.chatHistory.appendSystem("API_PROCESS_TERMINATED_VIA_HARD_INTERRUPT");
        }

        state.processMap.remove(state.currentSession);

        if (state.chatHistory != null) {
            state.chatHistory.clearChat();
            state.chatHistory.clearTypingBuffer();
        }

        state.isCtrlPending  = false;
        state.isAltPending   = false;
        state.isInsPending   = false;
        state.isCtrlAltCombo = false;

        setStatus("SYS_STATE: NEURAL_PURGED");
        updateModifierVisuals();
    }

    private void toggleExecMode() 
    {
        boolean turningOn = !state.isSystemModeActive;
        state.isSystemModeActive = turningOn;
        state.isAIModeActive     = false;
        state.isScriptModeActive = false;
        state.isCloudModeActive  = false;
        state.sessionModes.put(state.currentSession, turningOn ? 1 : 0);
        
        if (state.chatHistory != null) {
            if (turningOn) {
                // CORRECTED LOG NAME AND AUTOGENERATION
                File execLog = new File(state.sessionDirectories.get(state.currentSession), "exec_cmd.log");
                try {
                    if (!execLog.exists()) {
                        execLog.getParentFile().mkdirs();
                        execLog.createNewFile();
                    }
                } catch (Exception ignored) {}
                state.chatHistory.loadLogFile(execLog);
            } else {
                if (state.uiWindow != null) state.uiWindow.loadSession(state.currentSession);
            }
        }
        updateModifierVisuals();
    }

    private void toggleAIMode() 
    {
        boolean turningOn = !state.isAIModeActive;
        state.isSystemModeActive = false;
        state.isScriptModeActive = false;
        state.isCloudModeActive  = false;
        state.isAIModeActive     = turningOn;
        state.sessionModes.put(state.currentSession, turningOn ? 2 : 0);

        if (turningOn && state.apiBridge != null) {
            boolean deep    = state.isFnPending;
            state.isFnPending = false;
            state.apiBridge.startGoddessAPI(deep);
        } else if (!turningOn && state.uiWindow != null) {
            state.uiWindow.stopGoddessAPI(state.currentSession);
        }

        if (state.uiWindow != null) state.uiWindow.loadSession(state.currentSession);
        updateModifierVisuals();
    }

private void toggleScriptMode() 
    {
        boolean turningOn = !state.isScriptModeActive;
        state.isScriptModeActive = turningOn;
        state.isSystemModeActive = false;
        state.isAIModeActive     = false;
        state.isCloudModeActive  = false;
        state.sessionModes.put(state.currentSession, turningOn ? 3 : 0);
        
        if (state.chatHistory != null) {
            if (turningOn) {
                // CORRECTED LOG NAME AND AUTOGENERATION
                File scriptLog = new File(state.sessionDirectories.get(state.currentSession), "scrpt_cmd.log");
                try {
                    if (!scriptLog.exists()) {
                        scriptLog.getParentFile().mkdirs();
                        scriptLog.createNewFile();
                    }
                } catch (Exception ignored) {}
                state.chatHistory.loadLogFile(scriptLog);
            } else {
                if (state.uiWindow != null) state.uiWindow.loadSession(state.currentSession);
            }
        }
        updateModifierVisuals();
    }

    // ── CLIPBOARD ─────────────────────────────────────────────────────────────

    private void handleClipboard(JButton btn, boolean isMouse) 
    {
        if (state.isShiftPending) {
            try {
                Transferable c = Toolkit.getDefaultToolkit()
                        .getSystemClipboard().getContents(null);
                if (c != null && c.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    String text = (String) c.getTransferData(DataFlavor.stringFlavor);
                    if (state.chatHistory != null) state.chatHistory.insertAtCaret(text);
                    setStatus("SYS_PRT: IMPORTED");
                    state.numLockVisualState = 2;
                }
            } catch (Exception ignored) {}
            state.isShiftPending = false;
        } else {
            String txt = state.chatHistory != null
                    ? state.chatHistory.getTypingText() : "";
            if (!txt.isEmpty()) {
                StringSelection s = new StringSelection(txt);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(s, s);
                setStatus("SYS_PRT: COPIED");
                state.numLockVisualState = 1;
            }
        }

        btn.setBackground(state.numLockVisualState == 2
                ? MatrixConfig.GODDESS_GOLD
                : (isMouse ? Color.WHITE : MatrixConfig.HW_PURPLE));
        updateModifierVisuals();
    }

    // ── TYPING ────────────────────────────────────────────────────────────────

    private void processTyping(int index, String[] labels) 
    {
        String std = labels[0];
        String sft = labels[1];

        boolean isPadActive = !state.isNumLockActive || !state.isCalculatorEnabled;
        if (index >= 83 && index <= 100 && !isPadActive) return;

        boolean processed = false;
        boolean skipAuto  = false;

        switch (std) {
            case "ENTER":
                handleEnter(state.chatHistory != null
                        ? state.chatHistory.getTypingText() : "");
                processed = true;
                break;

            case "ENT":
                if (state.isCalculatorEnabled && state.chatHistory != null) {
                    String cur = state.chatHistory.getTypingText();
                    state.chatHistory.insertAtCaret(
                            " = " + CalculatorEngine.solveSegment(cur));
                } else if (state.chatHistory != null) {
                    state.chatHistory.insertAtCaret("\n");
                }
                processed = true;
                break;

            case "BKSP":
            case "DEL":
                if (state.chatHistory != null) state.chatHistory.deleteAtCaret();
                processed = true;
                if (state.isCtrlPending || state.isAltPending) skipAuto = true;
                break;

            case "SPACE":
                if (state.chatHistory != null) state.chatHistory.insertAtCaret(" ");
                processed = true;
                break;

            default:
                boolean effShift = isInvertedIndex(index)
                        ? !state.isShiftPending
                        :  state.isShiftPending;
                // Hardwire Numpad digits (89-99) to bypass the shift void so numbers never go silent
                if (index >= 83 && index <= 84 || index >= 86 && index <= 87 || index >= 89 && index <= 100) {
                    effShift = false;
                }

                String chr = (index >= 2 && index <= 13)
                        ? (state.isFnPending ? sft : std)
                        : (effShift || state.isAltDeleteCombo ? sft : std);

                if ("TAB".equals(std)) chr = "    ";

                if (state.chatHistory != null) state.chatHistory.insertAtCaret(chr);
                resetTransientModifiers();
                processed = true;
                break;
        }

        if (processed && state.isCapsLockActive && !skipAuto) state.isShiftPending = true;
        updateModifierVisuals();
    }

    private void handleEnter(String cur) 
    {
        if (state.isCloudModeActive) {
            if (state.llmBridge instanceof modular.LLMs) {
                ((modular.LLMs) state.llmBridge).sendCloudPrompt(cur);
            } else if (state.chatHistory != null) {
                state.chatHistory.appendRaw(
                        "CLOUD_MODE> LLM bridge not loaded. Press [Server].\n");
            }
            if (state.chatHistory != null) state.chatHistory.clearTypingBuffer();
            resetTransientModifiers();
            return;
        }

        Process apiProcess = state.apiProcessMap.get(state.currentSession);
        java.io.PrintWriter apiStdin = state.apiStdinMap.get(state.currentSession);

        if (state.isAIModeActive
                && apiProcess != null && apiProcess.isAlive()
                && apiStdin != null) {
            apiStdin.println(cur);
            apiStdin.flush();
            if (state.chatHistory != null) {
                state.chatHistory.logUserAiInput(cur);
                state.chatHistory.clearTypingBuffer();
            }
            state.isAIProcessing = true;
            state.currentAIStatus = "PROCESSING";
            resetTransientModifiers();
            return;
        }

        SessionProcess sp = state.processMap.get(state.currentSession);

        if ((state.isSystemModeActive || state.isScriptModeActive)
                && sp != null && sp.isAlive()) {
            sp.sendLine(cur);
            if (state.chatHistory != null) {
                state.chatHistory.logProcessInput(sp, cur);
                state.chatHistory.clearTypingBuffer();
            }
            resetTransientModifiers();
            return;
        }

        if (cur.startsWith("/file ") && state.imageViewer != null) {
            state.imageViewer.loadLocalHTML(cur.substring(6).trim());
            if (state.chatHistory != null) state.chatHistory.clearTypingBuffer();
            resetTransientModifiers();
            return;
        }

        if (!state.isCalculatorEnabled && state.isSystemModeActive
                && state.terminalRunner != null) {
            state.terminalRunner.executeShellCommand(cur);
        } else if (!state.isCalculatorEnabled && state.isScriptModeActive
                && state.terminalRunner != null) {
            state.terminalRunner.executeDirectScript(cur);
        } else if (state.chatHistory != null) {
            state.chatHistory.appendChat("USER", cur);
        }

        if (state.chatHistory != null) state.chatHistory.clearTypingBuffer();
        resetTransientModifiers();
    }

    private void resetTransientModifiers() 
    {
        state.isShiftPending   = false;
        state.isCtrlPending    = false;
        state.isAltPending     = false;
        state.isFnPending      = false;
        state.isInsPending     = false;
        state.isAltDeleteCombo = false;
        state.isComboActive    = false;
        state.isCtrlAltCombo   = false;
        state.isCmdPending     = false;
    }

    // ── MODIFIER VISUALS ──────────────────────────────────────────────────────

    public void updateModifierVisuals() 
    {
        for (Map.Entry<Integer, JButton> entry : buttons.entrySet()) {
            int     id   = entry.getKey();
            JButton btn  = entry.getValue();
            String[] pair = dualKeyMap.get(id);
            if (pair == null) continue;

            // Calculate exact pixel sizes based on the current window scale
            int primarySz = Math.max((int)(7 * currentScale), (int)(6 * currentScale));
            int subSz = Math.max((int)(6 * currentScale), (int)(5 * currentScale));

            if (id >= 101 && id <= 112) {
                btn.setText("<html><center><span style='font-size:" + primarySz + "px; color:white;'>"
                        + pair[0] + "</span></center></html>");
            } else {
                btn.setText("<html><center><span style='font-size:" + subSz + "px; color:gray;'>"
                        + pair[1]
                        + "</span><br><span style='font-size:" + primarySz + "px; color:white;'>"
                        + pair[0] + "</span></center></html>");
            }
        }

        String mode = state.isCloudModeActive  ? "CLOUD"
                    : state.isSystemModeActive  ? "EXEC"
                    : state.isAIModeActive      ? "A.I."
                    : state.isScriptModeActive  ? "SCRIPT"
                    : "CHAT";

        if (state.modifierLabel != null) {
            state.modifierLabel.setText(String.format(
                    "BRIDGE: %s | SYS_MODE: %s | CAPS: %s | SESSION: FN%d",
                    state.interfaceBridgeActive ? "ACTIVE" : "OFFLINE",
                    mode,
                    state.isCapsLockActive ? "ON" : "OFF",
                    state.currentSession));
        }
    }

    private void flashButton(JButton btn, boolean isMouse) 
    {
        if (btn == null) return;
        btn.setBackground(isMouse ? MatrixConfig.GODDESS_PURPLE : MatrixConfig.HW_PURPLE);
        new Timer(150, evt -> {
            btn.setBackground(MatrixConfig.KEY_BG);
            updateModifierVisuals();
        }).start();
    }

    // ── KEY MAPPING ───────────────────────────────────────────────────────────

    public int mapKeyCodeToIndex(int code, int loc) 
    {
        if (loc == KeyEvent.KEY_LOCATION_NUMPAD) {
            if (code >= KeyEvent.VK_NUMPAD0 && code <= KeyEvent.VK_NUMPAD9) {
                int[] map = {98,95,96,97,92,93,94,89,90,91};
                return map[code - KeyEvent.VK_NUMPAD0];
            }
            switch (code) {
                case KeyEvent.VK_DIVIDE:   return 83;
                case KeyEvent.VK_MULTIPLY: return 84;
                case KeyEvent.VK_SUBTRACT: return 86;
                case KeyEvent.VK_ADD:      return 87;
                case KeyEvent.VK_DECIMAL:  return 99;
                case KeyEvent.VK_ENTER:    return 100;
                default:                   return -1;
            }
        }

        if (code >= KeyEvent.VK_F1  && code <= KeyEvent.VK_F12) return (code - KeyEvent.VK_F1) + 2;
        if (code >= KeyEvent.VK_1   && code <= KeyEvent.VK_9)   return (code - KeyEvent.VK_1) + 19;
        if (code == KeyEvent.VK_0)     return 28;
        if (code == KeyEvent.VK_PAUSE) return 14;

        switch (code) {
            case KeyEvent.VK_ESCAPE:      return 1;
            case KeyEvent.VK_BACK_QUOTE:  return 18;
            case KeyEvent.VK_MINUS:       return 29;
            case KeyEvent.VK_EQUALS:      return 30;
            case KeyEvent.VK_BACK_SPACE:  return 31;
            case KeyEvent.VK_TAB:         return 32;
            case KeyEvent.VK_OPEN_BRACKET:  return 43;
            case KeyEvent.VK_CLOSE_BRACKET: return 44;
            case KeyEvent.VK_BACK_SLASH:  return 45;
            case KeyEvent.VK_CAPS_LOCK:   return 46;
            case KeyEvent.VK_SEMICOLON:   return 56;
            case KeyEvent.VK_QUOTE:       return 57;
            case KeyEvent.VK_ENTER:       return 58;
            case KeyEvent.VK_COMMA:       return 67;
            case KeyEvent.VK_PERIOD:      return 68;
            case KeyEvent.VK_SLASH:       return 69;
            case KeyEvent.VK_SPACE:       return 75;
            case KeyEvent.VK_SHIFT:   return loc == KeyEvent.KEY_LOCATION_LEFT ? 59 : 70;
            case KeyEvent.VK_CONTROL: return loc == KeyEvent.KEY_LOCATION_LEFT ? 71 : 77;
            case KeyEvent.VK_ALT:     return loc == KeyEvent.KEY_LOCATION_LEFT ? 74 : 76;
            case KeyEvent.VK_LEFT:    return 78;
            case KeyEvent.VK_UP:      return 79;
            case KeyEvent.VK_DOWN:    return 80;
            case KeyEvent.VK_RIGHT:   return 81;
            case KeyEvent.VK_A: return 36; case KeyEvent.VK_B: return 41;
            case KeyEvent.VK_C: return 42; case KeyEvent.VK_D: return 47;
            case KeyEvent.VK_E: return 37; case KeyEvent.VK_F: return 55;
            case KeyEvent.VK_G: return 64; case KeyEvent.VK_H: return 60;
            case KeyEvent.VK_I: return 63; case KeyEvent.VK_J: return 65;
            case KeyEvent.VK_K: return 66; case KeyEvent.VK_L: return 50;
            case KeyEvent.VK_M: return 51; case KeyEvent.VK_N: return 49;
            case KeyEvent.VK_O: return 61; case KeyEvent.VK_P: return 62;
            case KeyEvent.VK_Q: return 40; case KeyEvent.VK_R: return 48;
            case KeyEvent.VK_S: return 52; case KeyEvent.VK_T: return 53;
            case KeyEvent.VK_U: return 54; case KeyEvent.VK_V: return 38;
            case KeyEvent.VK_W: return 39; case KeyEvent.VK_X: return 33;
            case KeyEvent.VK_Y: return 34; case KeyEvent.VK_Z: return 35;
            default: return -1;
        }
    }

    public int mapCharToIndex(char c) 
    {
        char upper = Character.toUpperCase(c);
        if (upper >= 'A' && upper <= 'Z')
            return mapKeyCodeToIndex(KeyEvent.getExtendedKeyCodeForChar(upper), 0);
        if (c >= '0' && c <= '9')
            return mapKeyCodeToIndex(KeyEvent.getExtendedKeyCodeForChar(c), 0);
        if (c == ' ')  return 75;
        if (c == '\n') return 58;
        return -1;
    }

    private boolean isInvertedIndex(int id) 
    {
        return (id >= 18 && id <= 30)
            || (id >= 43 && id <= 45)
            || (id >= 56 && id <= 57)
            || (id >= 67 && id <= 69);
    }

    private void setStatus(String text) 
    {
        if (state.statusLabel != null) state.statusLabel.setText(text);
    }

    // ── KEY MAP ───────────────────────────────────────────────────────────────

    private Map<Integer, String[]> createDualKeyMap() 
    {
        Map<Integer, String[]> m = new HashMap<>();
        m.put(1,   new String[]{"ESC",   ""});
        m.put(2,   new String[]{"F1",    "f1"});
        m.put(3,   new String[]{"F2",    "f2"});
        m.put(4,   new String[]{"F3",    "f3"});
        m.put(5,   new String[]{"F4",    "f4"});
        m.put(6,   new String[]{"F5",    "f5"});
        m.put(7,   new String[]{"F6",    "f6"});
        m.put(8,   new String[]{"F7",    "f7"});
        m.put(9,   new String[]{"F8",    "f8"});
        m.put(10,  new String[]{"F9",    "f9"});
        m.put(11,  new String[]{"F10",   "f10"});
        m.put(12,  new String[]{"F11",   "f11"});
        m.put(13,  new String[]{"F12",   "f12"});
        m.put(14,  new String[]{"NTR",   ""});
        m.put(15,  new String[]{"PRT",   ""});
        m.put(16,  new String[]{"INS",   ""});
        m.put(17,  new String[]{"DEL",   ""});
        m.put(18,  new String[]{"~",     "`"});
        m.put(19,  new String[]{"1",     "!"});
        m.put(20,  new String[]{"2",     "@"});
        m.put(21,  new String[]{"3",     "#"});
        m.put(22,  new String[]{"4",     "$"});
        m.put(23,  new String[]{"5",     "%"});
        m.put(24,  new String[]{"6",     "^"});
        m.put(25,  new String[]{"7",     "&"});
        m.put(26,  new String[]{"8",     "*"});
        m.put(27,  new String[]{"9",     "("});
        m.put(28,  new String[]{"0",     ")"});
        m.put(29,  new String[]{"-",     "_"});
        m.put(30,  new String[]{"=",     "+"});
        m.put(31,  new String[]{"BKSP",  ""});
        m.put(32,  new String[]{"TAB",   ""});
        m.put(33,  new String[]{"X",     "x"});
        m.put(34,  new String[]{"Y",     "y"});
        m.put(35,  new String[]{"Z",     "z"});
        m.put(36,  new String[]{"A",     "a"});
        m.put(37,  new String[]{"E",     "e"});
        m.put(38,  new String[]{"V",     "v"});
        m.put(39,  new String[]{"W",     "w"});
        m.put(40,  new String[]{"Q",     "q"});
        m.put(41,  new String[]{"B",     "b"});
        m.put(42,  new String[]{"C",     "c"});
        m.put(43,  new String[]{"[",     "{"});
        m.put(44,  new String[]{"]",     "}"});
        m.put(45,  new String[]{"\\",    "|"});
        m.put(46,  new String[]{"CAPS",  ""});
        m.put(47,  new String[]{"D",     "d"});
        m.put(48,  new String[]{"R",     "r"});
        m.put(49,  new String[]{"N",     "n"});
        m.put(50,  new String[]{"L",     "l"});
        m.put(51,  new String[]{"M",     "m"});
        m.put(52,  new String[]{"S",     "s"});
        m.put(53,  new String[]{"T",     "t"});
        m.put(54,  new String[]{"U",     "u"});
        m.put(55,  new String[]{"F",     "f"});
        m.put(56,  new String[]{";",     ":"});
        m.put(57,  new String[]{"'",     "\""});
        m.put(58,  new String[]{"ENTER", ""});
        m.put(59,  new String[]{"SHIFT", ""});
        m.put(60,  new String[]{"H",     "h"});
        m.put(61,  new String[]{"O",     "o"});
        m.put(62,  new String[]{"P",     "p"});
        m.put(63,  new String[]{"I",     "i"});
        m.put(64,  new String[]{"G",     "g"});
        m.put(65,  new String[]{"J",     "j"});
        m.put(66,  new String[]{"K",     "k"});
        m.put(67,  new String[]{",",     "<"});
        m.put(68,  new String[]{".",     ">"});
        m.put(69,  new String[]{"/",     "?"});
        m.put(70,  new String[]{"SHIFT", ""});
        m.put(71,  new String[]{"CTRL",  ""});
        m.put(72,  new String[]{"FN",    ""});
        m.put(73,  new String[]{"CMD",   ""});
        m.put(74,  new String[]{"ALT",   ""});
        m.put(75,  new String[]{"SPACE", ""});
        m.put(76,  new String[]{"ALT",   ""});
        m.put(77,  new String[]{"CTRL",  ""});
        m.put(78,  new String[]{"<",     "←"});
        m.put(79,  new String[]{"^",     "↑"});
        m.put(80,  new String[]{"V",     "↓"});
        m.put(81,  new String[]{">",     "→"});
        m.put(82,  new String[]{"DIV",   ""});
        m.put(83,  new String[]{"/",     ""});
        m.put(84,  new String[]{"*",     ""});
        m.put(85,  new String[]{"PWR",   ""});
        m.put(86,  new String[]{"-",     ""});
        m.put(87,  new String[]{"+",     ""});
        m.put(88,  new String[]{"NUM",   ""});
        m.put(89,  new String[]{"7",     ""});
        m.put(90,  new String[]{"8",     ""});
        m.put(91,  new String[]{"9",     ""});
        m.put(92,  new String[]{"4",     ""});
        m.put(93,  new String[]{"5",     ""});
        m.put(94,  new String[]{"6",     ""});
        m.put(95,  new String[]{"1",     ""});
        m.put(96,  new String[]{"2",     ""});
        m.put(97,  new String[]{"3",     ""});
        m.put(98,  new String[]{"0",     ""});
        m.put(99,  new String[]{".",     ""});
        m.put(100, new String[]{"ENT",   ""});

        // Action column
        m.put(101, new String[]{"Exit",  ""});
        m.put(102, new String[]{"clRH",  ""});
        m.put(103, new String[]{"clr",   ""});
        m.put(104, new String[]{"Exec",  ""});
        m.put(105, new String[]{"A.I.",  ""});
        m.put(106, new String[]{"Scrpt", ""});

        // Extension column
        m.put(107, new String[]{"Update",    ""});
        m.put(108, new String[]{"[DIR]",     ""});
        m.put(109, new String[]{"Server",    ""});
        m.put(110, new String[]{"Terminal",  ""});
        m.put(111, new String[]{"Game",      ""});
        m.put(112, new String[]{"App",       ""});

        return m;
    }
}