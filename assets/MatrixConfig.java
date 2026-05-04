package assets;

/*
 * MatrixConfig.java
 *
 * Central configuration constants for Goddess Matrix.
 *
 * Purpose:
 * - UI layout sizing
 * - color palette
 * - timing constants
 * - version labeling
 *
 * Design Rules:
 * - NO logic
 * - constants only
 * - safe dependency for system/*
 */

import java.awt.Color;

public final class MatrixConfig {

    private MatrixConfig() {} // prevent instantiation

    // ─────────────────────────────────────────────
    // VERSION
    // ─────────────────────────────────────────────
    public static final String VERSION_LABEL = "V14.4 Modular";

    // ─────────────────────────────────────────────
    // KEYBOARD LAYOUT
    // ─────────────────────────────────────────────
    public static final int KEY_U   = 27; // base unit width
    public static final int KEY_H   = 24; // key height
    public static final int KEY_GAP = 4;  // spacing

    // ─────────────────────────────────────────────
    // COLOR PALETTE
    // ─────────────────────────────────────────────
    public static final Color BG_DARK        = new Color(10, 10, 12);
    public static final Color KEY_BG         = new Color(28, 28, 32);
    public static final Color TEXT_COLOR     = new Color(180, 180, 180);

    public static final Color GODDESS_PURPLE = new Color(157, 80, 187);
    public static final Color HW_PURPLE      = new Color(120, 60, 150);
    public static final Color GODDESS_GOLD   = new Color(250, 205, 104);

    public static final Color MANIFEST_BG    = new Color(12, 12, 16);

    // ─────────────────────────────────────────────
    // TIMING (ms)
    // ─────────────────────────────────────────────
    public static final int LOGIC_TICK_MS = 50;      // core loop tick
    public static final int IMAGE_HOLD_MS = 1500;    // fallback render delay

    // ─────────────────────────────────────────────
    // API / STATUS
    // ─────────────────────────────────────────────
    public static final String API_OFFLINE = "[API_OFFLINE]";
}
