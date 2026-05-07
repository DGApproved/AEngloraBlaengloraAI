package modular.game;

/*
 * KeyState.java
 *
 * Central key state for the Goddess Matrix game engine.
 * IceSandbox populates this each keyPressed/keyReleased event.
 * All game systems read from it — no key handling scattered across classes.
 *
 * ROUTING LOGIC:
 *   When world.activeMiniGame != null, minigame keys take priority.
 *   Movement keys (WASD) continue working unless minigame explicitly
 *   needs to suppress them (set suppressMovement = true).
 *
 * KEY ASSIGNMENTS:
 *
 *   MOVEMENT          WASD / SPACE
 *   CAMERA LOOK       Arrow keys / Mouse (M toggles capture)
 *   SYSTEM            ESC, E, C, M
 *
 *   MINIGAME PRIMARY:
 *     F               Interact / primary action
 *                     MiniGame: hold to decrypt (Forensic Lens)
 *                               hammer (Sanctuary)
 *     G               Secondary action
 *                     MiniGame: headdress (Sanctuary)
 *     Z               Add blue/logic resource
 *                     MiniGame: add blue thread (Garden Weaver)
 *     X               Add gold/love resource
 *                     MiniGame: add gold thread (Garden Weaver)
 *     Q               Logic ring — chose Q not A to avoid WASD conflict
 *                     MiniGame: press when logic runner hits target (Clock)
 *     L               Love ring
 *                     MiniGame: press when love runner hits target (Clock)
 *
 *   UNASSIGNED — available for future mechanics:
 *     R               (reserved: reload / reset?)
 *     T               (reserved: transmute — matches Aengloria shell)
 *     Y U I O P       (open)
 *     H J K           (open)
 *     N B V           (open)
 *     1 2 3 4 5       (open: quick-select slots?)
 *     6 7 8 9 0       (open)
 *     TAB             (open: map/inventory toggle?)
 *     SHIFT           (open: sprint modifier?)
 *     CTRL            (open: crouch / precision modifier?)
 *     ALT             (open)
 *
 * Contributors:
 *   Derek Jason Gilhousen — key layout concept, minigame mechanic design
 *   Claude (Anthropic)    — KeyState implementation, routing design
 */

import java.awt.event.KeyEvent;

public class KeyState
{
    // ── MOVEMENT ──────────────────────────────────────────────────────────────
    public boolean moveLeft    = false;  // A
    public boolean moveRight   = false;  // D
    public boolean moveForward = false;  // W
    public boolean moveBack    = false;  // S
    public boolean jump        = false;  // SPACE

    // ── CAMERA ────────────────────────────────────────────────────────────────
    public boolean camLeft     = false;  // Arrow Left
    public boolean camRight    = false;  // Arrow Right
    public boolean camUp       = false;  // Arrow Up
    public boolean camDown     = false;  // Arrow Down
    public boolean mouseLook   = false;  // M toggle (not a held key)

    // ── SYSTEM ────────────────────────────────────────────────────────────────
    public boolean overlay     = false;  // ESC
    public boolean panels      = false;  // E
    public boolean chat        = false;  // C

    // ── MINIGAME / INTERACTION ────────────────────────────────────────────────
    public boolean interact    = false;  // F — primary action, can be held
    public boolean altAction   = false;  // G — secondary action
    public boolean addBlue     = false;  // Z — add blue/logic resource
    public boolean addGold     = false;  // X — add gold/love resource
    public boolean logicRing   = false;  // Q — clock logic ring (not A, avoids WASD)
    public boolean loveRing    = false;  // L — clock love ring

    // ── UNASSIGNED — reserved for future mechanics ────────────────────────────
    public boolean key_R       = false;
    public boolean key_T       = false;  // transmute (matches Aengloria shell)
    public boolean key_Y       = false;
    public boolean key_U       = false;
    public boolean key_I       = false;
    public boolean key_O       = false;
    public boolean key_P       = false;
    public boolean key_H       = false;
    public boolean key_J       = false;
    public boolean key_K       = false;
    public boolean key_N       = false;
    public boolean key_B       = false;
    public boolean key_V       = false;
    public boolean key_TAB     = false;
    public boolean key_SHIFT   = false;
    public boolean key_CTRL    = false;
    public boolean key_ALT     = false;

    // Number row
    public boolean key_1 = false, key_2 = false, key_3 = false;
    public boolean key_4 = false, key_5 = false, key_6 = false;
    public boolean key_7 = false, key_8 = false, key_9 = false;
    public boolean key_0 = false;

    // ── ROUTING STATE ─────────────────────────────────────────────────────────
    // Set by MiniGameEngine when it needs exclusive key capture.
    // Movement keys are still readable even when this is true,
    // but the minigame may choose to ignore them.
    public boolean suppressMovement = false;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called by IceSandbox.keyPressed().
     * Routes the key code to the appropriate flag.
     */
    public void onKeyPressed(int keyCode)
    {
        switch (keyCode)
        {
            // Movement
            case KeyEvent.VK_A:          moveLeft    = true; break;
            case KeyEvent.VK_D:          moveRight   = true; break;
            case KeyEvent.VK_W:          moveForward = true; break;
            case KeyEvent.VK_S:          moveBack    = true; break;
            case KeyEvent.VK_SPACE:      jump        = true; break;

            // Camera
            case KeyEvent.VK_LEFT:       camLeft     = true; break;
            case KeyEvent.VK_RIGHT:      camRight    = true; break;
            case KeyEvent.VK_UP:         camUp       = true; break;
            case KeyEvent.VK_DOWN:       camDown     = true; break;

            // Minigame / interaction
            case KeyEvent.VK_F:          interact    = true; break;
            case KeyEvent.VK_G:          altAction   = true; break;
            case KeyEvent.VK_Z:          addBlue     = true; break;
            case KeyEvent.VK_X:          addGold     = true; break;
            case KeyEvent.VK_Q:          logicRing   = true; break;
            case KeyEvent.VK_L:          loveRing    = true; break;

            // Unassigned
            case KeyEvent.VK_R:          key_R       = true; break;
            case KeyEvent.VK_T:          key_T       = true; break;
            case KeyEvent.VK_Y:          key_Y       = true; break;
            case KeyEvent.VK_U:          key_U       = true; break;
            case KeyEvent.VK_I:          key_I       = true; break;
            case KeyEvent.VK_O:          key_O       = true; break;
            case KeyEvent.VK_P:          key_P       = true; break;
            case KeyEvent.VK_H:          key_H       = true; break;
            case KeyEvent.VK_J:          key_J       = true; break;
            case KeyEvent.VK_K:          key_K       = true; break;
            case KeyEvent.VK_N:          key_N       = true; break;
            case KeyEvent.VK_B:          key_B       = true; break;
            case KeyEvent.VK_V:          key_V       = true; break;
            case KeyEvent.VK_TAB:        key_TAB     = true; break;
            case KeyEvent.VK_SHIFT:      key_SHIFT   = true; break;
            case KeyEvent.VK_CONTROL:    key_CTRL    = true; break;
            case KeyEvent.VK_ALT:        key_ALT     = true; break;

            // Number row
            case KeyEvent.VK_1:          key_1       = true; break;
            case KeyEvent.VK_2:          key_2       = true; break;
            case KeyEvent.VK_3:          key_3       = true; break;
            case KeyEvent.VK_4:          key_4       = true; break;
            case KeyEvent.VK_5:          key_5       = true; break;
            case KeyEvent.VK_6:          key_6       = true; break;
            case KeyEvent.VK_7:          key_7       = true; break;
            case KeyEvent.VK_8:          key_8       = true; break;
            case KeyEvent.VK_9:          key_9       = true; break;
            case KeyEvent.VK_0:          key_0       = true; break;
        }
    }

    /**
     * Called by IceSandbox.keyReleased().
     */
    public void onKeyReleased(int keyCode)
    {
        switch (keyCode)
        {
            case KeyEvent.VK_A:          moveLeft    = false; break;
            case KeyEvent.VK_D:          moveRight   = false; break;
            case KeyEvent.VK_W:          moveForward = false; break;
            case KeyEvent.VK_S:          moveBack    = false; break;
            case KeyEvent.VK_SPACE:      jump        = false; break;

            case KeyEvent.VK_LEFT:       camLeft     = false; break;
            case KeyEvent.VK_RIGHT:      camRight    = false; break;
            case KeyEvent.VK_UP:         camUp       = false; break;
            case KeyEvent.VK_DOWN:       camDown     = false; break;

            case KeyEvent.VK_F:          interact    = false; break;
            case KeyEvent.VK_G:          altAction   = false; break;
            case KeyEvent.VK_Z:          addBlue     = false; break;
            case KeyEvent.VK_X:          addGold     = false; break;
            case KeyEvent.VK_Q:          logicRing   = false; break;
            case KeyEvent.VK_L:          loveRing    = false; break;

            case KeyEvent.VK_R:          key_R       = false; break;
            case KeyEvent.VK_T:          key_T       = false; break;
            case KeyEvent.VK_Y:          key_Y       = false; break;
            case KeyEvent.VK_U:          key_U       = false; break;
            case KeyEvent.VK_I:          key_I       = false; break;
            case KeyEvent.VK_O:          key_O       = false; break;
            case KeyEvent.VK_P:          key_P       = false; break;
            case KeyEvent.VK_H:          key_H       = false; break;
            case KeyEvent.VK_J:          key_J       = false; break;
            case KeyEvent.VK_K:          key_K       = false; break;
            case KeyEvent.VK_N:          key_N       = false; break;
            case KeyEvent.VK_B:          key_B       = false; break;
            case KeyEvent.VK_V:          key_V       = false; break;
            case KeyEvent.VK_TAB:        key_TAB     = false; break;
            case KeyEvent.VK_SHIFT:      key_SHIFT   = false; break;
            case KeyEvent.VK_CONTROL:    key_CTRL    = false; break;
            case KeyEvent.VK_ALT:        key_ALT     = false; break;

            case KeyEvent.VK_1:          key_1       = false; break;
            case KeyEvent.VK_2:          key_2       = false; break;
            case KeyEvent.VK_3:          key_3       = false; break;
            case KeyEvent.VK_4:          key_4       = false; break;
            case KeyEvent.VK_5:          key_5       = false; break;
            case KeyEvent.VK_6:          key_6       = false; break;
            case KeyEvent.VK_7:          key_7       = false; break;
            case KeyEvent.VK_8:          key_8       = false; break;
            case KeyEvent.VK_9:          key_9       = false; break;
            case KeyEvent.VK_0:          key_0       = false; break;
        }
    }

    /**
     * Copies held-state flags to MiniGameEngine before its tick.
     * Single-press flags (addBlue, addGold, logicRing, loveRing) are
     * consumed by MiniGameEngine — it sets them false after reading.
     */
    public void applyToMiniGame(MiniGameEngine engine)
    {
        engine.keyLeft    = camLeft;      // signal tuning
        engine.keyRight   = camRight;
        engine.keyF       = interact;     // decrypt hold / hammer
        engine.keyG       = altAction;    // headdress
        engine.keyZ       = addBlue;      // weaver blue thread
        engine.keyX       = addGold;      // weaver gold thread
        engine.keyA       = logicRing;    // clock — mapped from Q
        engine.keyL       = loveRing;     // clock
    }

    /**
     * Applies movement keys to PlayerPhysics.
     * Skipped when suppressMovement is true.
     */
    public void applyToPhysics(PlayerPhysics physics)
    {
        if (suppressMovement) return;
        physics.setMoveLeft(moveLeft);
        physics.setMoveRight(moveRight);
        physics.setMoveForward(moveForward);
        physics.setMoveBack(moveBack);
    }

    // ── MOUSE FOCUS SIGNALS ───────────────────────────────────────────────────
    // IceSandbox reads these after each key event and acts on them.
    // Set by onKeyPressed when a UI key opens an overlay that needs the cursor.
    // Cleared by IceSandbox after it handles the signal.

    public boolean signalReleaseMouse  = false;  // IceSandbox should release cursor
    public boolean signalCaptureMouse  = false;  // IceSandbox should capture cursor

    /**
     * Called by IceSandbox after reading the signals.
     */
    public void clearMouseSignals()
    {
        signalReleaseMouse = false;
        signalCaptureMouse = false;
    }

    /**
     * Evaluates whether a key press should release the mouse.
     * Called from onKeyPressed with the UI state context.
     *
     * @param overlayVisible  ESC overlay currently open
     * @param panelsVisible   E panels currently open
     * @param chatOpen        C chat conduit currently open
     */
    public boolean keyWantsMouseRelease(int keyCode,
                                         boolean overlayVisible,
                                         boolean panelsVisible,
                                         boolean chatOpen)
    {
        switch (keyCode)
        {
            case KeyEvent.VK_ESCAPE:
                // ESC opening overlay needs cursor for rate click
                return true;

            case KeyEvent.VK_E:
                // E opening panels needs cursor for panel interaction
                return true;

            case KeyEvent.VK_C:
                // C opening chat needs cursor for text selection / editing
                // (also when E is already open so chat can coexist with panels)
                return true;

            default:
                return false;
        }
    }

    /**
     * Evaluates whether a click at (mx, my) should recapture the mouse.
     * Returns true when the click is on the game world area — i.e. not inside
     * an active UI element that still needs the cursor.
     *
     * @param mx              click X in panel coordinates
     * @param my              click Y in panel coordinates
     * @param W               panel width
     * @param H               panel height
     * @param overlayVisible  ESC overlay open
     * @param panelsVisible   E panels open
     * @param chatOpen        C chat open
     * @param chatBoxX        chatbox left edge
     * @param chatBoxY        chatbox top edge
     * @param chatBoxW        chatbox width
     * @param chatBoxH        chatbox height
     */
    public boolean clickShouldCapture(int mx, int my, int W, int H,
                                       boolean overlayVisible,
                                       boolean panelsVisible,
                                       boolean chatOpen,
                                       int chatBoxX, int chatBoxY,
                                       int chatBoxW, int chatBoxH)
    {
        // Never capture while overlay or panels are open
        if (overlayVisible || panelsVisible) return false;

        // If chat is open: capture only if click is OUTSIDE the chatbox
        if (chatOpen)
        {
            boolean insideChat = mx >= chatBoxX && mx <= chatBoxX + chatBoxW
                              && my >= chatBoxY && my <= chatBoxY + chatBoxH;
            return !insideChat;
        }

        // No UI open — any click on the game area captures
        return true;
    }
}
