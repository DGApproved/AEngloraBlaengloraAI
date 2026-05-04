package modular.game;

/*
 * RefreshOverlay.java
 *
 * ESC mode overlay rendered at top center of the game panel.
 *
 * Shows:
 *   - Current timing mode and rate
 *   - Planet diameter (from PlanetScaler)
 *   - ASM timing status
 *   - Clickable rate display
 *
 * STANDALONE EXIT:
 *   When running without GoddessMatrix, clicking the refresh rate
 *   display acts as the exit button. This is the only exit path in
 *   standalone mode — no dedicated exit button exists in the panel UI.
 *   In Matrix context: [Game] button handles exit. This click does nothing.
 *
 * RATE CYCLING:
 *   Right-click (or repeated ESC tap) cycles through available rates.
 *   Available rates shown as a brief cycle: 60 → 75 → 81ms → back
 *   Actual timing governed by TimingBridge; overlay just reports it.
 *
 * Contributors:
 *   Derek Jason Gilhousen — three-mode timing design, ESC concept
 *   Claude (Anthropic)    — RefreshOverlay rendering, click detection,
 *                           standalone exit logic
 */

import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class RefreshOverlay 
{
    // ── LAYOUT ────────────────────────────────────────────────────────────────
    private static final int OVERLAY_W      = 280;
    private static final int OVERLAY_H      = 72;
    private static final int OVERLAY_PAD    = 12;
    private static final int CORNER_R       = 10;

    // Rate display clickable region (relative to overlay top-left)
    private static final int RATE_BTN_X     = 10;
    private static final int RATE_BTN_Y     = 30;
    private static final int RATE_BTN_W     = 260;
    private static final int RATE_BTN_H     = 28;

    // Colors
    private static final Color BG_COLOR     = new Color(8,   10,  18,  210);
    private static final Color BORDER_COLOR = new Color(157, 80,  187, 140);
    private static final Color TEXT_PRIMARY = new Color(250, 205, 104);
    private static final Color TEXT_DIM     = new Color(148, 163, 184);
    private static final Color RATE_HOVER   = new Color(157, 80,  187, 60);
    private static final Color RATE_NORMAL  = new Color(30,  20,  50,  120);

    // ── STATE ─────────────────────────────────────────────────────────────────
    private boolean rateHovered = false;

    // Callback fired when the rate button is clicked.
    // Standalone context:  set to exit the game entirely.
    // Matrix context:      set to restoreManifest() (exit fullscreen).
    // Not set:             click does nothing.
    private Runnable rateClickCallback = null;

    // ─────────────────────────────────────────────────────────────────────────

    public void setStandaloneExitCallback(Runnable cb) 
    {
        this.rateClickCallback = cb;
    }

    /** Set the callback fired when the rate display is clicked. */
    public void setRateClickCallback(Runnable cb)
    {
        this.rateClickCallback = cb;
    }

    // ── RENDER ────────────────────────────────────────────────────────────────

    public void render(Graphics2D g, int panelWidth, int panelHeight,
                       TimingBridge timing, PlanetScaler planet,
                       boolean isStandalone) 
    {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Position: top center
        int ox = (panelWidth  - OVERLAY_W) / 2;
        int oy = OVERLAY_PAD;

        // ── BACKGROUND ────────────────────────────────────────────────────────
        RoundRectangle2D bg = new RoundRectangle2D.Float(
            ox, oy, OVERLAY_W, OVERLAY_H, CORNER_R, CORNER_R);
        g.setColor(BG_COLOR);
        g.fill(bg);
        g.setColor(BORDER_COLOR);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(bg);

        // ── TITLE BAR ─────────────────────────────────────────────────────────
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(TEXT_DIM);
        String asmLabel = timing.isASMPresent() ? "ASM" : "JAVA";
        drawCenter(g, "TIMING GOVERNOR: " + asmLabel,
                   ox + OVERLAY_W / 2, oy + 14);

        // ── RATE DISPLAY (clickable) ───────────────────────────────────────────
        int rx = ox + RATE_BTN_X;
        int ry = oy + RATE_BTN_Y;

        // Button background — brighter on hover
        RoundRectangle2D rateBg = new RoundRectangle2D.Float(
            rx, ry, RATE_BTN_W, RATE_BTN_H, 6, 6);
        g.setColor(rateHovered ? RATE_HOVER : RATE_NORMAL);
        g.fill(rateBg);
        g.setColor(rateHovered ? BORDER_COLOR : new Color(80, 60, 100, 100));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(rateBg);

        // Rate text
        String modeLabel;
        switch (timing.getCurrentMode()) 
        {
            case TimingBridge.MODE_OVERLAY:  modeLabel = "ESC MODE"; break;
            case TimingBridge.MODE_PANELS:   modeLabel = "PANEL MODE"; break;
            default:                         modeLabel = "GAMEPLAY"; break;
        }

        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        g.setColor(TEXT_PRIMARY);
        String rateStr = timing.getDisplayRate() + "  [" + modeLabel + "]";
        drawCenter(g, rateStr, rx + RATE_BTN_W / 2, ry + 18);

        // Standalone exit hint — or Matrix fullscreen exit hint
        if (rateClickCallback != null) 
        {
            g.setFont(new Font("Monospaced", Font.PLAIN, 7));
            String hint = isStandalone ? "CLICK TO EXIT" : "CLICK TO RESTORE";
            g.setColor(rateHovered
                ? new Color(239, 68, 68, 200)
                : new Color(148, 163, 184, 100));
            drawCenter(g, hint,
                       rx + RATE_BTN_W / 2, ry + RATE_BTN_H + 10);
        }

        // Planet diameter
        g.setFont(new Font("Monospaced", Font.PLAIN, 8));
        g.setColor(TEXT_DIM);
        drawCenter(g, planet.getDisplayString(),
                   ox + OVERLAY_W / 2, oy + OVERLAY_H - 6);
    }

    // ── HIT DETECTION ─────────────────────────────────────────────────────────

    /**
     * Call on mouse move. Updates hover state.
     * @param mouseX mouse X relative to panel
     * @param mouseY mouse Y relative to panel
     * @param panelWidth panel width
     */
    public void onMouseMove(int mouseX, int mouseY, int panelWidth) 
    {
        int ox = (panelWidth - OVERLAY_W) / 2;
        int oy = OVERLAY_PAD;
        int rx = ox + RATE_BTN_X;
        int ry = oy + RATE_BTN_Y;

        rateHovered = mouseX >= rx && mouseX <= rx + RATE_BTN_W
                   && mouseY >= ry && mouseY <= ry + RATE_BTN_H;
    }

    /**
     * Call on mouse click. Returns true if click was consumed.
     * In standalone mode: rate button click triggers exit callback.
     * In Matrix mode: click does nothing (Matrix handles exit).
     */
    public boolean onMouseClick(int mouseX, int mouseY, int panelWidth,
                                boolean isStandalone) 
    {
        int ox = (panelWidth - OVERLAY_W) / 2;
        int oy = OVERLAY_PAD;
        int rx = ox + RATE_BTN_X;
        int ry = oy + RATE_BTN_Y;

        boolean hit = mouseX >= rx && mouseX <= rx + RATE_BTN_W
                   && mouseY >= ry && mouseY <= ry + RATE_BTN_H;

        if (hit) 
        {
            if (rateClickCallback != null) 
            {
                rateClickCallback.run();
            }
            return true;
        }
        return false;
    }

    public boolean isRateHovered() { return rateHovered; }

    // ── TEXT HELPERS ──────────────────────────────────────────────────────────

    private void drawCenter(Graphics2D g, String s, int cx, int y) 
    {
        if (s == null) return;
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }
}
