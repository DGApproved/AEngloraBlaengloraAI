package modular.game;

/*
 * KeyState.java
 *
 * Central key state and configuration for the Goddess Matrix game engine.
 * Reads modular/game/config.txt to map user preferences to logical actions.
 * Exposes isGameKey() for the Matrix OS Keyboard hook to determine pass-through.
 */

import java.awt.event.KeyEvent;
import java.io.*;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class KeyState
{
    // ── LOGICAL STATE FLAGS (Read by Engine) ──────────────────────────────────
    public boolean moveLeft = false, moveRight = false, moveForward = false, moveBack = false;
    public boolean jump = false;

    public boolean key_CTRL = false, key_ALT = false;
    public boolean overlay = false, panels = false, chat = false, vrGlasses = false;

    public boolean camLeft = false, camRight = false, camUp = false, camDown = false;

    public boolean interact = false, altAction = false;
    public boolean addBlue = false, addGold = false;
    public boolean logicRing = false, loveRing = false;

    // ── BOUND KEYCODES (Loaded from config.txt) ───────────────────────────────
    private int bindMoveForward, bindMoveBack, bindMoveLeft, bindMoveRight, bindJump;
    private int bindChat, bindToggleGlasses, bindPanels, bindOverlay;
    private int bindHeadLeft, bindHeadRight, bindHeadUp, bindHeadDown;
    private int bindRightArmMod, bindLeftArmMod;
    private int bindInteract, bindAltAction, bindAddBlue, bindAddGold, bindLogicRing, bindLoveRing;

    // Fast O(1) lookup set for Keyboard.java OS pass-through
    private final Set<Integer> boundGameKeys = new HashSet<>();

    // ── ROUTING / SIGNAL STATE ────────────────────────────────────────────────
    // suppressMovement: set by MiniGameEngine when it needs exclusive input
    public boolean suppressMovement    = false;
    // Mouse focus signals — read by IceSandbox after each key event
    public boolean signalReleaseMouse  = false;
    public boolean signalCaptureMouse  = false;

    public KeyState(File gameDir)
    {
        if (gameDir == null)
            gameDir = new File("modular/game");

        if (!gameDir.exists())
            gameDir.mkdirs();

        File configFile = new File(gameDir, "config.txt");

        if (!configFile.exists())
        {
            generateDefaultConfig(configFile);
        }

        loadConfig(gameDir);
    }
        

    // ── CONFIGURATION I/O ─────────────────────────────────────────────────────

    private void loadConfig(File gameDir)
    {
        if (gameDir != null && !gameDir.exists()) gameDir.mkdirs();
        File configFile = new File(gameDir, "config.txt");

        if (!configFile.exists()) {
            generateDefaultConfig(configFile);
        }

        Properties props = new Properties();
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            props.load(reader);
        } catch (IOException e) {
            System.err.println("Failed to read config.txt. Using defaults.");
        }

        // Map properties to integer keycodes
        bindMoveForward   = parseKey(props.getProperty("MOVE_FORWARD", "W"));
        bindMoveBack      = parseKey(props.getProperty("MOVE_BACK", "S"));
        bindMoveLeft      = parseKey(props.getProperty("MOVE_LEFT", "A"));
        bindMoveRight     = parseKey(props.getProperty("MOVE_RIGHT", "D"));
        bindJump          = parseKey(props.getProperty("JUMP", "SPACE"));

        bindChat          = parseKey(props.getProperty("CHAT", "C"));
        bindToggleGlasses = parseKey(props.getProperty("TOGGLE_GLASSES", "P"));
        bindPanels        = parseKey(props.getProperty("PANELS", "E"));
        bindOverlay       = parseKey(props.getProperty("OVERLAY", "ESCAPE"));

        bindHeadLeft      = parseKey(props.getProperty("HEAD_LEFT", "LEFT"));
        bindHeadRight     = parseKey(props.getProperty("HEAD_RIGHT", "RIGHT"));
        bindHeadUp        = parseKey(props.getProperty("HEAD_UP", "UP"));
        bindHeadDown      = parseKey(props.getProperty("HEAD_DOWN", "DOWN"));

        bindRightArmMod   = parseKey(props.getProperty("RIGHT_ARM_MOD", "CONTROL"));
        bindLeftArmMod    = parseKey(props.getProperty("LEFT_ARM_MOD", "ALT"));

        bindInteract      = parseKey(props.getProperty("INTERACT", "F"));
        bindAltAction     = parseKey(props.getProperty("ALT_ACTION", "G"));
        bindAddBlue       = parseKey(props.getProperty("ADD_BLUE", "Z"));
        bindAddGold       = parseKey(props.getProperty("ADD_GOLD", "X"));
        bindLogicRing     = parseKey(props.getProperty("LOGIC_RING", "Q"));
        bindLoveRing      = parseKey(props.getProperty("LOVE_RING", "L"));

        // Populate fast lookup set
        boundGameKeys.clear();
        boundGameKeys.add(bindMoveForward); boundGameKeys.add(bindMoveBack);
        boundGameKeys.add(bindMoveLeft);    boundGameKeys.add(bindMoveRight);
        boundGameKeys.add(bindJump);        boundGameKeys.add(bindChat);
        boundGameKeys.add(bindToggleGlasses); boundGameKeys.add(bindPanels);
        boundGameKeys.add(bindOverlay);     boundGameKeys.add(bindHeadLeft);
        boundGameKeys.add(bindHeadRight);   boundGameKeys.add(bindHeadUp);
        boundGameKeys.add(bindHeadDown);    boundGameKeys.add(bindRightArmMod);
        boundGameKeys.add(bindLeftArmMod);  boundGameKeys.add(bindInteract);
        boundGameKeys.add(bindAltAction);   boundGameKeys.add(bindAddBlue);
        boundGameKeys.add(bindAddGold);     boundGameKeys.add(bindLogicRing);
        boundGameKeys.add(bindLoveRing);
    }

    private int parseKey(String keyName)
    {
        try {
            // Uses Java Reflection to translate "SPACE" to KeyEvent.VK_SPACE
            Field field = KeyEvent.class.getField("VK_" + keyName.trim().toUpperCase());
            return field.getInt(null);
        } catch (Exception e) {
            System.err.println("WARN: Invalid key binding: " + keyName);
            return -1;
        }
    }

    private void generateDefaultConfig(File configFile)
    {
        String defaults = """
            # Goddess Matrix Game Keybindings
            MOVE_FORWARD=W
            MOVE_BACK=S
            MOVE_LEFT=A
            MOVE_RIGHT=D
            JUMP=SPACE
            
            CHAT=C
            TOGGLE_GLASSES=P
            PANELS=E
            OVERLAY=ESCAPE
            
            HEAD_LEFT=LEFT
            HEAD_RIGHT=RIGHT
            HEAD_UP=UP
            HEAD_DOWN=DOWN
            
            RIGHT_ARM_MOD=CONTROL
            LEFT_ARM_MOD=ALT
            
            INTERACT=F
            ALT_ACTION=G
            ADD_BLUE=Z
            ADD_GOLD=X
            LOGIC_RING=Q
            LOVE_RING=L
            """;
        try (PrintWriter out = new PrintWriter(new FileWriter(configFile))) {
            out.print(defaults);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ── OS FIREWALL CHECK ─────────────────────────────────────────────────────

    /** * Called by OS Keyboard.java.
     * Returns true if the key is currently bound to a game action.
     */
    public boolean isGameKey(int keyCode)
    {
        return boundGameKeys.contains(keyCode);
    }

    // ── EVENT ROUTING ─────────────────────────────────────────────────────────

    public void onKeyPressed(int keyCode) { setKey(keyCode, true); }
    public void onKeyReleased(int keyCode) { setKey(keyCode, false); }

    private void setKey(int code, boolean active)
    {
        if (code == bindMoveForward)   moveForward = active;
        else if (code == bindMoveBack) moveBack = active;
        else if (code == bindMoveLeft) moveLeft = active;
        else if (code == bindMoveRight) moveRight = active;
        else if (code == bindJump)     jump = active;
        else if (code == bindChat)     chat = active;
        else if (code == bindToggleGlasses) vrGlasses = active;
        else if (code == bindPanels)   panels = active;
        else if (code == bindOverlay)  overlay = active;
        else if (code == bindHeadLeft) camLeft = active;
        else if (code == bindHeadRight) camRight = active;
        else if (code == bindHeadUp)   camUp = active;
        else if (code == bindHeadDown) camDown = active;
        else if (code == bindRightArmMod) key_CTRL = active;
        else if (code == bindLeftArmMod)  key_ALT = active;
        else if (code == bindInteract) interact = active;
        else if (code == bindAltAction) altAction = active;
        else if (code == bindAddBlue)  addBlue = active;
        else if (code == bindAddGold)  addGold = active;
        else if (code == bindLogicRing) logicRing = active;
        else if (code == bindLoveRing) loveRing = active;
    }

    public void applyToMiniGame(MiniGameEngine engine)
    {
        if (engine == null) return;
        engine.keyLeft = camLeft; engine.keyRight = camRight; engine.keyF = interact; engine.keyG = altAction;
        engine.keyZ = addBlue; engine.keyX = addGold; engine.keyA = logicRing; engine.keyL = loveRing;
    }

    public void applyToPhysics(PlayerPhysics physics)
    {
        if (physics == null) return;
        physics.setMoveLeft(!suppressMovement && moveLeft); physics.setMoveRight(!suppressMovement && moveRight);
        physics.setMoveForward(!suppressMovement && moveForward); physics.setMoveBack(!suppressMovement && moveBack);
    }

    public void clearSignals() { signalReleaseMouse = false; signalCaptureMouse = false; }

    /**
     * Called by IceSandbox keyPressed — sets signalReleaseMouse when
     * ESC/E/C open overlays that need cursor freedom.
     */
    public void checkMouseReleaseKeys(int keyCode)
    {
        switch (keyCode)
        {
            case java.awt.event.KeyEvent.VK_ESCAPE:
            case java.awt.event.KeyEvent.VK_E:
            case java.awt.event.KeyEvent.VK_C:
                signalReleaseMouse = true;
                break;
        }
    }

    /**
     * Returns true if a click at (mx,my) should recapture the mouse.
     * False when overlay/panels are open or click is inside the chatbox.
     */
    public boolean clickShouldCapture(int mx, int my, int W, int H,
                                       boolean overlayVisible,
                                       boolean panelsVisible,
                                       boolean chatOpen,
                                       int chatBoxX, int chatBoxY,
                                       int chatBoxW, int chatBoxH)
    {
        if (overlayVisible || panelsVisible) return false;
        if (chatOpen)
        {
            boolean inside = mx >= chatBoxX && mx <= chatBoxX + chatBoxW
                          && my >= chatBoxY && my <= chatBoxY + chatBoxH;
            return !inside;
        }
        return true;
    }
}
