package modular.game;

/*
 * SidePanels.java
 *
 * Four side panels sliding in from screen edges when E is pressed.
 *
 * PANEL ASSIGNMENTS:
 *   TOP    — Three-mode celestial clock (Standard / Hybrid / Aengloria)
 *   RIGHT  — Compact calendar (Standard / Celestial / Cotsworth)
 *   LEFT   — Options / settings (Python-driven via PANEL_UPDATE)
 *   BOTTOM — Inventory + standalone EXIT button
 *
 * MINIGAME LIST:
 *   The right panel uses the space below the calendar grid to list minigames
 *   discovered from HTML files by HtmlGameScanner and run by MiniGameEngine.
 *
 * Contributors:
 *   Derek Jason Gilhousen — panel design, three-phase clock concept,
 *                           drift mechanics, calendar systems
 *   Claude (Anthropic)    — SidePanels implementation
 *   ChatGPT (OpenAI)      — fixed calendar/minigame layout scope bug and
 *                           regenerated compile-safe minigame list area
 */

import java.awt.*;
import java.awt.geom.GeneralPath;

public class SidePanels
{
    private static final int TOP_HEIGHT    = 90;
    private static final int BOTTOM_HEIGHT = 80;
    private static final int SIDE_WIDTH    = 130;
    private static final int CORNER_R      = 18;

    private static final Color BG          = new Color(5,   8,   18,  225);
    private static final Color BORDER      = new Color(157, 80,  187, 130);
    private static final Color TEXT_TITLE  = new Color(250, 205, 104);
    private static final Color TEXT_DIM    = new Color(148, 163, 184);
    private static final Color TEXT_BRIGHT = new Color(220, 235, 255);
    private static final Color COL_STD     = new Color(80,  150, 255);
    private static final Color COL_HYB     = new Color(255, 215, 0);
    private static final Color COL_AENG    = new Color(157, 80,  187);
    private static final Color COL_DRIFT   = new Color(239, 68,  68,  210);
    private static final Color COL_SYNC    = new Color(80,  220, 120);
    private static final Color EXIT_NORM   = new Color(239, 68,  68,  160);
    private static final Color EXIT_HOV    = new Color(239, 68,  68,  220);

    private boolean  exitHovered        = false;
    public  boolean  calendarModeTapped = false;

    private Runnable standaloneExitCallback = null;
    private String   leftContent            = "";
    private String   bottomContent          = "";

    private final int[] mi1 = new int[2];

    public void setStandaloneExitCallback(Runnable cb) { standaloneExitCallback = cb; }

    public void setContent(String side, String content)
    {
        if ("left".equals(side))        leftContent   = content;
        else if ("bottom".equals(side)) bottomContent = content;
    }

    public void render(Graphics2D g, WorldState world, int W, int H, boolean standalone)
    {
        if (world.panelSlideProgress <= 0.001f) return;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        float p    = world.panelSlideProgress;
        int topH   = (int)(TOP_HEIGHT    * p);
        int botH   = (int)(BOTTOM_HEIGHT * p);
        int leftW  = (int)(SIDE_WIDTH    * p);
        int rightW = (int)(SIDE_WIDTH    * p);

        if (topH   > 2) renderTop(g, W, topH, world);
        if (botH   > 2) renderBottom(g, W, H, botH, standalone);
        if (leftW  > 2) renderLeft(g, H, leftW, topH, botH);
        if (rightW > 2) renderRight(g, W, H, rightW, topH, botH, world);
    }

    private void renderTop(Graphics2D g, int W, int pH, WorldState world)
    {
        fill(g, shapeTop(W, pH));

        CelestialClock c = world.clock;
        if (c == null || pH < 20) return;

        int x = 10, y = 14, dy = 20;

        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(COL_STD);
        g.drawString(String.format("STD  %02d:%02d:%02d %s",
            c.stdH, c.stdM, c.stdS, c.stdPM ? "PM" : "AM"), x, y);

        if (pH > y + dy)
        {
            String base = String.format("HYB  %02d:%02d:", c.hybDispH, c.hybM);
            g.setColor(COL_HYB);
            g.drawString(base, x, y + dy);

            FontMetrics fm = g.getFontMetrics();
            int sx = x + fm.stringWidth(base);

            if (c.hybridDrift)
            {
                g.setColor(COL_DRIFT);
                g.drawString(String.format("[%02d]", c.hybS), sx, y + dy);
                g.setColor(COL_HYB);
                g.drawString(c.hybPM ? " PM" : " AM",
                             sx + fm.stringWidth("[00]"), y + dy);
            }
            else
            {
                g.setColor(COL_HYB);
                g.drawString(String.format("%02d %s", c.hybS,
                    c.hybPM ? "PM" : "AM"), sx, y + dy);
            }
        }

        if (pH > y + dy * 2)
        {
            g.setFont(new Font("Monospaced", Font.BOLD, 9));
            g.setColor(COL_AENG);
            g.drawString(String.format("ANG  %02d:%02d:%02d %s",
                c.aengDispH, c.aengM, c.aengS,
                c.aengPM ? "PM" : "AM"), x, y + dy * 2);
        }

        if (c.atMidnightSync && pH > y + dy * 3)
        {
            g.setFont(new Font("Monospaced", Font.PLAIN, 7));
            g.setColor(COL_SYNC);
            drawCx(g, "── MIDNIGHT SYNC ──", W / 2, y + dy * 3);
        }
    }

    private void renderBottom(Graphics2D g, int W, int H, int pH, boolean standalone)
    {
        int top = H - pH;
        fill(g, shapeBottom(W, H, pH));

        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(TEXT_TITLE);
        g.drawString("INVENTORY", 14, top + 16);

        if (!bottomContent.isEmpty())
        {
            g.setFont(new Font("Monospaced", Font.PLAIN, 9));
            g.setColor(TEXT_DIM);
            drawWrapped(g, bottomContent, 14, top + 30, W - 28, 11);
        }

        if (standalone && pH >= BOTTOM_HEIGHT - 10)
        {
            int bW = 80, bH = 22, bX = W - bW - 14, bY = H - bH - 10;
            g.setColor(exitHovered ? EXIT_HOV : EXIT_NORM);
            g.fillRoundRect(bX, bY, bW, bH, 6, 6);
            g.setColor(new Color(255, 200, 200, 180));
            g.setStroke(new BasicStroke(0.8f));
            g.drawRoundRect(bX, bY, bW, bH, 6, 6);
            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            g.drawString("EXIT", bX + (bW - fm.stringWidth("EXIT")) / 2, bY + 15);
        }
    }

    private void renderLeft(Graphics2D g, int H, int pW, int topH, int botH)
    {
        fill(g, shapeLeft(H, pW, topH, botH));
        if (pW < 40) return;

        g.setFont(new Font("Monospaced", Font.BOLD, 9));
        g.setColor(TEXT_TITLE);
        g.drawString("OPT", 8, topH + 18);

        if (!leftContent.isEmpty())
        {
            g.setFont(new Font("Monospaced", Font.PLAIN, 8));
            g.setColor(TEXT_DIM);
            drawWrapped(g, leftContent, 6, topH + 32, pW - 12, 10);
        }
    }

    private void renderRight(Graphics2D g, int W, int H, int pW,
                              int topH, int botH, WorldState world)
    {
        int left = W - pW;
        int top  = topH;
        int bot  = H - botH;

        fill(g, shapeRight(W, H, pW, topH, botH));
        if (pW < 50 || (bot - top) < 60) return;

        CelestialClock clk = world.clock;
        if (clk == null) return;

        int cx = left + pW / 2;
        int y  = top + 12;

        g.setFont(new Font("Monospaced", Font.BOLD, 7));
        g.setColor(calModeColor(clk.calMode));
        drawCx(g, clk.calModeName(), cx, y);
        y += 11;

        g.setFont(new Font("Monospaced", Font.BOLD, 8));
        g.setColor(TEXT_TITLE);
        drawCx(g, clk.monthName() + " " + clk.calYear, cx, y);
        y += 12;

        int colW = Math.max(1, (pW - 8) / 7);
        g.setFont(new Font("Monospaced", Font.PLAIN, 6));
        g.setColor(TEXT_DIM);
        for (int d = 0; d < 7; d++)
            drawCx(g, CelestialClock.DAY_ABBR[d], left + 4 + d * colW + colW / 2, y);
        y += 9;

        int prevDays = 31;
        if (clk.calMonth > 0)
        {
            CelestialClock.getMonthInfo(clk.calYear, clk.calMonth - 1, clk.calMode, mi1);
            prevDays = mi1[1];
        }

        int rowH       = Math.max(8, Math.min(11, (bot - y - 4) / 6));
        int totalCells = ((clk.calStartWday + clk.calDaysInMonth + 6) / 7) * 7;
        int lastCalendarY = y;

        for (int i = 0; i < totalCells && i < 42; i++)
        {
            int col   = i % 7;
            int row   = i / 7;
            int cellX = left + 4 + col * colW + colW / 2;
            int cellY = y + row * rowH + rowH - 2;
            if (cellY > bot - 2) break;

            lastCalendarY = cellY;

            String dayStr;
            Color  dayCol;

            if (i < clk.calStartWday)
            {
                dayStr = String.valueOf(prevDays - clk.calStartWday + i + 1);
                dayCol = new Color(60, 60, 80, 140);
            }
            else if (i >= clk.calStartWday + clk.calDaysInMonth)
            {
                dayStr = String.valueOf(i - (clk.calStartWday + clk.calDaysInMonth) + 1);
                dayCol = new Color(60, 60, 80, 140);
            }
            else
            {
                int dayNum  = i - clk.calStartWday + 1;
                boolean today = (clk.calYear  == clk.todayYear
                              && clk.calMonth == clk.todayMonth
                              && dayNum       == clk.todayDay);
                dayStr = String.valueOf(dayNum);
                if (today)
                {
                    g.setColor(new Color(255, 215, 0, 55));
                    g.fillRoundRect(cellX - colW/2 + 1, cellY - rowH + 2,
                                    colW - 2, rowH - 1, 3, 3);
                    dayCol = new Color(255, 215, 0);
                }
                else dayCol = TEXT_BRIGHT;
            }

            g.setFont(new Font("Monospaced", Font.PLAIN, 6));
            g.setColor(dayCol);
            drawCx(g, dayStr, cellX, cellY);
        }

        renderMiniGameList(g, world, left, W, cx, lastCalendarY + 12, bot);
    }

    private void renderMiniGameList(Graphics2D g, WorldState world,
                                    int left, int W, int cx, int startY, int bot)
    {
        if (world.gameNodes == null || world.gameNodes.isEmpty()) return;

        int my = startY;
        if (my >= bot - 10) return;

        g.setColor(new Color(157, 80, 187, 60));
        g.setStroke(new BasicStroke(0.5f));
        g.drawLine(left + 4, my, W - 4, my);
        my += 8;

        g.setFont(new Font("Monospaced", Font.BOLD, 6));
        g.setColor(new Color(157, 80, 187, 200));
        drawCx(g, "MINIGAMES", cx, my);
        my += 9;

        for (WorldState.GameNode node : world.gameNodes)
        {
            if (my >= bot - 4) break;

            boolean isActive = (world.activeMiniGame == node);
            Color nodeCol = node.unlocked
                ? (isActive ? new Color(80, 220, 120)
                            : node.active ? new Color(250, 205, 104)
                                          : TEXT_BRIGHT)
                : new Color(80, 80, 100, 150);

            g.setFont(new Font("Monospaced", Font.PLAIN, 6));
            g.setColor(nodeCol);

            String prefix = isActive ? "▶ " : node.active ? "◉ " : "○ ";
            String label  = prefix + mechanicShort(node.mechanic);
            drawCx(g, label, cx, my);
            my += 8;

            if ((isActive || !node.unlocked) && node.statusMsg != null && !node.statusMsg.isEmpty())
            {
                if (my >= bot - 4) break;
                g.setFont(new Font("Monospaced", Font.PLAIN, 5));
                g.setColor(node.unlocked ? TEXT_DIM : new Color(90, 90, 120, 150));
                drawCx(g, trimTo(node.statusMsg, 18), cx, my);
                my += 7;
            }
        }
    }

    private String mechanicShort(String m)
    {
        if (m == null) return "UNKNOWN";
        switch (m)
        {
            case "SIGNAL":    return "SIGNAL";
            case "WEAVE":     return "WEAVER";
            case "DECRYPT":   return "LENS";
            case "SANCTUARY": return "SANCTUARY";
            case "CLOCK":     return "HARMONY";
            default:           return m.length() > 7 ? m.substring(0,7) : m;
        }
    }

    private String trimTo(String s, int max)
    {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private Color calModeColor(int m)
    {
        switch (m)
        {
            case CelestialClock.CAL_CELESTIAL: return COL_HYB;
            case CelestialClock.CAL_COTSWORTH: return new Color(0, 215, 200);
            default:                            return COL_STD;
        }
    }

    private void fill(Graphics2D g, GeneralPath s)
    {
        g.setColor(BG);
        g.fill(s);
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(s);
    }

    private GeneralPath shapeTop(int W, int pH)
    {
        GeneralPath s = new GeneralPath();
        s.moveTo(0, 0); s.lineTo(W, 0);
        s.lineTo(W, pH - CORNER_R);
        s.quadTo(W, pH, W - CORNER_R, pH);
        s.lineTo(CORNER_R, pH);
        s.quadTo(0, pH, 0, pH - CORNER_R);
        s.closePath(); return s;
    }

    private GeneralPath shapeBottom(int W, int H, int pH)
    {
        int top = H - pH;
        GeneralPath s = new GeneralPath();
        s.moveTo(0, H); s.lineTo(W, H);
        s.lineTo(W, top + CORNER_R);
        s.quadTo(W, top, W - CORNER_R, top);
        s.lineTo(CORNER_R, top);
        s.quadTo(0, top, 0, top + CORNER_R);
        s.closePath(); return s;
    }

    private GeneralPath shapeLeft(int H, int pW, int topH, int botH)
    {
        int top = topH, bot = H - botH;
        GeneralPath s = new GeneralPath();
        s.moveTo(0, top);
        s.lineTo(pW - CORNER_R, top);
        s.quadTo(pW, top, pW, top + CORNER_R);
        s.lineTo(pW, bot - CORNER_R);
        s.quadTo(pW, bot, pW - CORNER_R, bot);
        s.lineTo(0, bot);
        s.closePath(); return s;
    }

    private GeneralPath shapeRight(int W, int H, int pW, int topH, int botH)
    {
        int left = W - pW, top = topH, bot = H - botH;
        GeneralPath s = new GeneralPath();
        s.moveTo(W, top);
        s.lineTo(left + CORNER_R, top);
        s.quadTo(left, top, left, top + CORNER_R);
        s.lineTo(left, bot - CORNER_R);
        s.quadTo(left, bot, left + CORNER_R, bot);
        s.lineTo(W, bot);
        s.closePath(); return s;
    }

    public void onMouseMove(int mx, int my, int W, int H, float slide)
    {
        if (slide < 0.9f)
        {
            exitHovered = false;
            return;
        }
        int bW = 80, bH = 22, bX = W - bW - 14, bY = H - bH - 10;
        exitHovered = mx >= bX && mx <= bX + bW && my >= bY && my <= bY + bH;
    }

    public boolean onMouseClick(int mx, int my, int W, int H,
                                float slide, boolean standalone)
    {
        if (slide < 0.9f) return false;

        if (standalone)
        {
            int bW = 80, bH = 22, bX = W - bW - 14, bY = H - bH - 10;
            if (mx >= bX && mx <= bX + bW && my >= bY && my <= bY + bH)
            {
                if (standaloneExitCallback != null) standaloneExitCallback.run();
                return true;
            }
        }

        int rLeft = (int)(W - SIDE_WIDTH * slide);
        if (mx >= rLeft && my <= 22)
        {
            calendarModeTapped = true;
            return true;
        }

        return false;
    }

    private void drawCx(Graphics2D g, String s, int cx, int y)
    {
        if (s == null || s.isEmpty()) return;
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }

    private void drawWrapped(Graphics2D g, String text,
                              int x, int y, int maxW, int lineH)
    {
        if (text == null || text.isEmpty()) return;
        FontMetrics fm = g.getFontMetrics();
        int cy = y;
        for (String line : text.split("\n"))
        {
            StringBuilder cur = new StringBuilder();
            for (String word : line.split(" "))
            {
                String t = cur.length() == 0 ? word : cur + " " + word;
                if (fm.stringWidth(t) > maxW && cur.length() > 0)
                {
                    g.drawString(cur.toString(), x, cy);
                    cy += lineH;
                    cur = new StringBuilder(word);
                }
                else cur = new StringBuilder(t);
            }
            if (cur.length() > 0)
            {
                g.drawString(cur.toString(), x, cy);
                cy += lineH;
            }
        }
    }
}
