package modular.game;

/*
 * SidePanels.java
 *
 * Four side panels that slide in from all screen edges when E is pressed.
 *
 * Geometry:
 *   - Four panels: top, bottom, left, right
 *   - Outer corners: flush with screen edge (sharp)
 *   - Inner corners: rounded (facing the game world)
 *   - Game world remains visible behind panels
 *   - Panels slide in/out animated via WorldState.panelSlideProgress
 *
 * Panel assignments (V1 placeholder content):
 *   TOP    — status / narrative display
 *   BOTTOM — inventory (placeholder)
 *   LEFT   — options / settings (placeholder)
 *   RIGHT  — knowledge index (placeholder, links to encyclopedia entries)
 *
 * PYTHON CONTENT HOOKS:
 *   Panel text content is Python-owned.
 *   Java owns: layout, animation, rendering frame.
 *   Python populates via protocol tags (future [PANEL_UPDATE:side:content]).
 *   For V1: panels show static placeholder content.
 *   narrateText from WorldState is displayed in the top panel.
 *
 * STANDALONE EXIT:
 *   In standalone mode, bottom panel includes an EXIT button.
 *   In Matrix mode: no exit button. [Game] button handles exit.
 *
 * Contributors:
 *   Derek Jason Gilhousen — E panel concept, rounded inner corner design,
 *                           exit button logic per context
 *   Claude (Anthropic)    — SidePanels implementation, GeneralPath panel
 *                           geometry, animation, content slots
 */

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;

public class SidePanels 
{
    // ── LAYOUT ────────────────────────────────────────────────────────────────
    private static final int TOP_HEIGHT    = 80;
    private static final int BOTTOM_HEIGHT = 80;
    private static final int SIDE_WIDTH    = 120;
    private static final int CORNER_R      = 18;   // inner corner radius

    // ── COLORS ────────────────────────────────────────────────────────────────
    private static final Color PANEL_BG     = new Color(5,   8,   18,  220);
    private static final Color PANEL_BORDER = new Color(157, 80,  187, 120);
    private static final Color TEXT_TITLE   = new Color(250, 205, 104);
    private static final Color TEXT_DIM     = new Color(148, 163, 184);
    private static final Color EXIT_BG      = new Color(239, 68,  68,  160);
    private static final Color EXIT_HOVER   = new Color(239, 68,  68,  220);

    // ── STATE ─────────────────────────────────────────────────────────────────
    private boolean exitHovered = false;

    // Runnable called when EXIT button clicked in standalone mode
    private Runnable standaloneExitCallback = null;

    // [PYTHON HOOK: PANEL CONTENT]
    // These strings are placeholders. Python will populate via [PANEL_UPDATE:] tags.
    // Format: side=top|bottom|left|right, content=multi-line string
    private String topContent    = "";
    private String bottomContent = "";
    private String leftContent   = "";
    private String rightContent  = "";

    // ─────────────────────────────────────────────────────────────────────────

    public void setStandaloneExitCallback(Runnable cb) 
    {
        this.standaloneExitCallback = cb;
    }

    // [PYTHON HOOK: PANEL_UPDATE tag handler]
    public void setContent(String side, String content) 
    {
        switch (side) 
        {
            case "top":    topContent    = content; break;
            case "bottom": bottomContent = content; break;
            case "left":   leftContent   = content; break;
            case "right":  rightContent  = content; break;
        }
    }

    // ── RENDER ────────────────────────────────────────────────────────────────

    public void render(Graphics2D g, WorldState world,
                       int W, int H, boolean isStandalone) 
    {
        if (world.panelSlideProgress <= 0.001f) return;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        float p = world.panelSlideProgress; // 0.0 = closed, 1.0 = fully open

        // ── TOP PANEL ─────────────────────────────────────────────────────────
        int topH = (int)(TOP_HEIGHT * p);
        if (topH > 2) 
        {
            renderTopPanel(g, W, topH, world);
        }

        // ── BOTTOM PANEL ──────────────────────────────────────────────────────
        int botH = (int)(BOTTOM_HEIGHT * p);
        if (botH > 2) 
        {
            renderBottomPanel(g, W, H, botH, isStandalone);
        }

        // ── LEFT PANEL ────────────────────────────────────────────────────────
        int leftW = (int)(SIDE_WIDTH * p);
        if (leftW > 2) 
        {
            renderLeftPanel(g, H, leftW, topH, botH);
        }

        // ── RIGHT PANEL ───────────────────────────────────────────────────────
        int rightW = (int)(SIDE_WIDTH * p);
        if (rightW > 2) 
        {
            renderRightPanel(g, W, H, rightW, topH, botH);
        }
    }

    // ── TOP PANEL ─────────────────────────────────────────────────────────────

    private void renderTopPanel(Graphics2D g, int W, int panelH, WorldState world) 
    {
        // Shape: full width, top-flush outer corners, rounded inner (bottom) corners
        GeneralPath shape = new GeneralPath();
        shape.moveTo(0, 0);
        shape.lineTo(W, 0);
        shape.lineTo(W, panelH - CORNER_R);
        shape.quadTo(W, panelH, W - CORNER_R, panelH);   // bottom-right inner
        shape.lineTo(CORNER_R, panelH);
        shape.quadTo(0, panelH, 0, panelH - CORNER_R);   // bottom-left inner
        shape.closePath();

        g.setColor(PANEL_BG);
        g.fill(shape);
        g.setColor(PANEL_BORDER);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(shape);

        // Content
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(TEXT_TITLE);
        g.drawString("STATUS", 14, 16);

        // Narrate text from Python
        String narrate = world.narrateText;
        if (narrate != null && !narrate.isEmpty()
                && System.currentTimeMillis() < world.narrateExpireMs) 
        {
            g.setFont(new Font("Monospaced", Font.PLAIN, 10));
            g.setColor(new Color(220, 235, 255, 200));
            drawWrapped(g, narrate, 14, 30, W - 28, 12);
        } 
        else if (!topContent.isEmpty()) 
        {
            // [PYTHON HOOK: top panel content from PANEL_UPDATE tag]
            g.setFont(new Font("Monospaced", Font.PLAIN, 9));
            g.setColor(TEXT_DIM);
            drawWrapped(g, topContent, 14, 30, W - 28, 11);
        }
    }

    // ── BOTTOM PANEL ──────────────────────────────────────────────────────────

    private void renderBottomPanel(Graphics2D g, int W, int H,
                                   int panelH, boolean isStandalone) 
    {
        int top = H - panelH;

        // Shape: full width, bottom-flush outer corners, rounded inner (top) corners
        GeneralPath shape = new GeneralPath();
        shape.moveTo(0, H);
        shape.lineTo(W, H);
        shape.lineTo(W, top + CORNER_R);
        shape.quadTo(W, top, W - CORNER_R, top);         // top-right inner
        shape.lineTo(CORNER_R, top);
        shape.quadTo(0, top, 0, top + CORNER_R);         // top-left inner
        shape.closePath();

        g.setColor(PANEL_BG);
        g.fill(shape);
        g.setColor(PANEL_BORDER);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(shape);

        // Content
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(TEXT_TITLE);
        g.drawString("INVENTORY", 14, top + 16);

        // [PYTHON HOOK: bottom panel content from PANEL_UPDATE tag]
        if (!bottomContent.isEmpty()) 
        {
            g.setFont(new Font("Monospaced", Font.PLAIN, 9));
            g.setColor(TEXT_DIM);
            drawWrapped(g, bottomContent, 14, top + 30, W - 28, 11);
        }

        // STANDALONE EXIT BUTTON
        if (isStandalone && panelH >= BOTTOM_HEIGHT - 10) 
        {
            renderExitButton(g, W, H);
        }
    }

    private void renderExitButton(Graphics2D g, int W, int H) 
    {
        int btnW = 80;
        int btnH = 22;
        int btnX = W - btnW - 14;
        int btnY = H - btnH - 10;

        g.setColor(exitHovered ? EXIT_HOVER : EXIT_BG);
        g.fillRoundRect(btnX, btnY, btnW, btnH, 6, 6);
        g.setColor(new Color(255, 200, 200, 180));
        g.setStroke(new BasicStroke(0.8f));
        g.drawRoundRect(btnX, btnY, btnW, btnH, 6, 6);

        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        String label = "EXIT";
        g.drawString(label,
                     btnX + (btnW - fm.stringWidth(label)) / 2,
                     btnY + 15);
    }

    // ── LEFT PANEL ────────────────────────────────────────────────────────────

    private void renderLeftPanel(Graphics2D g, int H, int panelW,
                                  int topH, int botH) 
    {
        int top = topH;
        int bot = H - botH;

        // Shape: left-flush outer corners, rounded inner (right) corners
        GeneralPath shape = new GeneralPath();
        shape.moveTo(0, top);
        shape.lineTo(panelW - CORNER_R, top);
        shape.quadTo(panelW, top, panelW, top + CORNER_R);          // top-right inner
        shape.lineTo(panelW, bot - CORNER_R);
        shape.quadTo(panelW, bot, panelW - CORNER_R, bot);          // bottom-right inner
        shape.lineTo(0, bot);
        shape.closePath();

        g.setColor(PANEL_BG);
        g.fill(shape);
        g.setColor(PANEL_BORDER);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(shape);

        // Content
        if (panelW < 40) return;
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(TEXT_TITLE);
        g.drawString("OPT", 8, top + 18);

        // [PYTHON HOOK: left panel content from PANEL_UPDATE tag]
        if (!leftContent.isEmpty()) 
        {
            g.setFont(new Font("Monospaced", Font.PLAIN, 8));
            g.setColor(TEXT_DIM);
            drawWrapped(g, leftContent, 6, top + 32, panelW - 12, 10);
        }
    }

    // ── RIGHT PANEL ───────────────────────────────────────────────────────────

    private void renderRightPanel(Graphics2D g, int W, int H,
                                   int panelW, int topH, int botH) 
    {
        int left = W - panelW;
        int top  = topH;
        int bot  = H - botH;

        // Shape: right-flush outer corners, rounded inner (left) corners
        GeneralPath shape = new GeneralPath();
        shape.moveTo(W, top);
        shape.lineTo(left + CORNER_R, top);
        shape.quadTo(left, top, left, top + CORNER_R);              // top-left inner
        shape.lineTo(left, bot - CORNER_R);
        shape.quadTo(left, bot, left + CORNER_R, bot);              // bottom-left inner
        shape.lineTo(W, bot);
        shape.closePath();

        g.setColor(PANEL_BG);
        g.fill(shape);
        g.setColor(PANEL_BORDER);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(shape);

        // Content
        if (panelW < 40) return;
        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(TEXT_TITLE);
        g.drawString("KNO", left + 8, top + 18);

        // [PYTHON HOOK: right panel content from PANEL_UPDATE tag]
        // Intended: encyclopedia entry index, discoverable theory list.
        // Python sends entry labels via PANEL_UPDATE:right: tags.
        if (!rightContent.isEmpty()) 
        {
            g.setFont(new Font("Monospaced", Font.PLAIN, 8));
            g.setColor(TEXT_DIM);
            drawWrapped(g, rightContent, left + 6, top + 32, panelW - 12, 10);
        }
    }

    // ── HIT DETECTION ─────────────────────────────────────────────────────────

    public void onMouseMove(int mouseX, int mouseY, int W, int H,
                            float slideProgress) 
    {
        if (slideProgress < 0.9f) { exitHovered = false; return; }
        int panelH = BOTTOM_HEIGHT;
        int btnW = 80, btnH = 22;
        int btnX = W - btnW - 14;
        int btnY = H - btnH - 10;
        exitHovered = mouseX >= btnX && mouseX <= btnX + btnW
                   && mouseY >= btnY && mouseY <= btnY + btnH;
    }

    public boolean onMouseClick(int mouseX, int mouseY, int W, int H,
                                float slideProgress, boolean isStandalone) 
    {
        if (!isStandalone || slideProgress < 0.9f) return false;
        int panelH = BOTTOM_HEIGHT;
        int btnW = 80, btnH = 22;
        int btnX = W - btnW - 14;
        int btnY = H - btnH - 10;
        boolean hit = mouseX >= btnX && mouseX <= btnX + btnW
                   && mouseY >= btnY && mouseY <= btnY + btnH;
        if (hit && standaloneExitCallback != null) 
        {
            standaloneExitCallback.run();
            return true;
        }
        return false;
    }

    // ── TEXT HELPERS ──────────────────────────────────────────────────────────

    private void drawWrapped(Graphics2D g, String text, int x, int y,
                              int maxW, int lineH) 
    {
        if (text == null || text.isEmpty()) return;
        FontMetrics fm = g.getFontMetrics();
        int cy = y;
        for (String line : text.split("\n")) 
        {
            // Simple word wrap
            StringBuilder current = new StringBuilder();
            for (String word : line.split(" ")) 
            {
                String test = current.length() == 0 ? word : current + " " + word;
                if (fm.stringWidth(test) > maxW && current.length() > 0) 
                {
                    g.drawString(current.toString(), x, cy);
                    cy += lineH;
                    current = new StringBuilder(word);
                } 
                else 
                {
                    current = new StringBuilder(test);
                }
            }
            if (current.length() > 0) 
            {
                g.drawString(current.toString(), x, cy);
                cy += lineH;
            }
        }
    }
}
