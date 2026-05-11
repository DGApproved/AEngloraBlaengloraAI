package modular.game;

/*
 * HtmlGameScanner.java
 *
 * Scans the HTML folder for game files and registers them as
 * in-game mechanic nodes on the planet surface.
 *
 * HTML files are design documents — their mechanics get expressed
 * natively in 3D space rather than opened in a browser window.
 * No popup windows. Everything stays inside the game viewport.
 *
 * MECHANIC DETECTION (keyword scan of HTML content):
 *   SIGNAL    — Signal Catcher  (frequency tuning → raw noise)
 *   WEAVE     — Garden Weaver   (blue + gold threads → artifacts)
 *   DECRYPT   — Forensic Lens   (raw noise → threads via decrypt)
 *   SANCTUARY — Sanctuary       (entropy vs insight balance)
 *   CLOCK     — Harmony Engine  (timing rhythm → harmony score)
 *   UNKNOWN   — fallback for unrecognized files
 *
 * NODE PLACEMENT:
 *   Deterministic from filename hash — same file always appears
 *   at the same location on the sphere. Spread across latitudes
 *   so nodes are not all clustered at the equator.
 *
 * UNLOCK SEQUENCE (optional, can be disabled):
 *   SIGNAL is always unlocked (it's how raw noise is gathered).
 *   DECRYPT unlocks when rawNoise > 10.
 *   WEAVE unlocks when blueThreads > 0 and goldThreads > 0.
 *   SANCTUARY and CLOCK unlock when artifacts > 0.
 *
 * Contributors:
 *   Derek Jason Gilhousen — HTML game design, mechanic concepts,
 *                           Aengloria resource loop
 *   Claude (Anthropic)    — HtmlGameScanner implementation
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class HtmlGameScanner
{
    // ── MECHANIC IDENTIFIERS ──────────────────────────────────────────────────
    public static final String MECH_SIGNAL    = "SIGNAL";
    public static final String MECH_WEAVE     = "WEAVE";
    public static final String MECH_DECRYPT   = "DECRYPT";
    public static final String MECH_SANCTUARY = "SANCTUARY";
    public static final String MECH_CLOCK     = "CLOCK";
    public static final String MECH_UNKNOWN   = "UNKNOWN";

    // ── DETECTION KEYWORDS ────────────────────────────────────────────────────
    private static final String[][] MECHANIC_KEYWORDS = {
        { MECH_SIGNAL,    "signalLock", "targetFreq", "Signal Catcher",
                          "SIGNAL CATCHER", "SIGNAL_CATCHER" },
        { MECH_WEAVE,     "wovenBlue", "wovenGold", "Garden Weaver",
                          "GARDEN WEAVER", "GARDEN_WEAVER", "currentRecipe" },
        { MECH_DECRYPT,   "decryptProgress", "Forensic Lens",
                          "FORENSIC LENS", "FORENSIC_LENS" },
        { MECH_SANCTUARY, "Sanctuary of Equilibrium", "sanctuary",
                          "strain", "useHammer", "useHeaddress" },
        { MECH_CLOCK,     "logicAngle", "loveAngle", "Harmony Engine",
                          "Clock and Pancake", "LOGIC_SPEED", "LOVE_SPEED" }
    };

    // ── MECHANIC DISPLAY NAMES ────────────────────────────────────────────────
    public static String displayName(String mechanic)
    {
        switch (mechanic)
        {
            case MECH_SIGNAL:    return "SIGNAL CATCHER";
            case MECH_WEAVE:     return "GARDEN WEAVER";
            case MECH_DECRYPT:   return "FORENSIC LENS";
            case MECH_SANCTUARY: return "SANCTUARY";
            case MECH_CLOCK:     return "HARMONY ENGINE";
            default:             return "UNKNOWN";
        }
    }

    public static String mechanicDescription(String mechanic)
    {
        switch (mechanic)
        {
            case MECH_SIGNAL:
                return "Tune frequency to harvest raw noise";
            case MECH_WEAVE:
                return "Spend threads to craft artifacts";
            case MECH_DECRYPT:
                return "Spend noise to gain logic+love threads";
            case MECH_SANCTUARY:
                return "Balance entropy and insight";
            case MECH_CLOCK:
                return "Time logic and love to build harmony";
            default:
                return "Unknown mechanic";
        }
    }

    // ── SCAN ──────────────────────────────────────────────────────────────────

    /**
     * Scans the given HTML directory and returns a list of GameNodes.
     * Call once at game load from IceSandbox.startEngine().
     * @param htmlDir  the HTML folder (sibling to modular/, assets/, system/)
     */
    public static List<WorldState.GameNode> scan(File htmlDir)
    {
        List<WorldState.GameNode> nodes = new ArrayList<>();
        if (htmlDir == null || !htmlDir.exists() || !htmlDir.isDirectory())
            return nodes;

        File[] files = htmlDir.listFiles(
            f -> f.isFile() && f.getName().toLowerCase().endsWith(".html"));
        if (files == null || files.length == 0) return nodes;

        // Sort for consistent ordering
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));

        for (int i = 0; i < files.length; i++)
        {
            File f = files[i];
            String content = readFile(f);
            if (content == null) continue;

            String mechanic = detectMechanic(content);
            String title    = extractTitle(content, f.getName());

            // Deterministic placement — evenly spaced around the sphere
            // Distribute by index so nodes don't cluster
            float angleDeg = (360f / Math.max(files.length, 1)) * i;
            float latDeg   = latForIndex(i, files.length);

            WorldState.GameNode node = new WorldState.GameNode(
                f.getName(), title, mechanic, angleDeg, latDeg);

            // Initial status message
            node.statusMsg = mechanicDescription(mechanic);

            nodes.add(node);
        }

        return nodes;
    }

    /**
     * Updates unlock state for all nodes based on current resource levels.
     * Call each logic tick from IceSandbox.updateLogic().
     */
    public static void updateUnlocks(List<WorldState.GameNode> nodes,
                                     WorldState world)
    {
        for (WorldState.GameNode node : nodes)
        {
            switch (node.mechanic)
            {
                case MECH_SIGNAL:
                    node.unlocked = true; // always available
                    break;
                case MECH_DECRYPT:
                    node.unlocked = (world.rawNoise >= 5f);
                    if (!node.unlocked) node.statusMsg = "Need 5 raw noise";
                    else node.statusMsg = mechanicDescription(node.mechanic);
                    break;
                case MECH_WEAVE:
                    node.unlocked = (world.blueThreads > 0 || world.goldThreads > 0);
                    if (!node.unlocked) node.statusMsg = "Need threads (Forensic Lens)";
                    else node.statusMsg = mechanicDescription(node.mechanic);
                    break;
                case MECH_SANCTUARY:
                case MECH_CLOCK:
                    node.unlocked = (world.artifacts > 0);
                    if (!node.unlocked) node.statusMsg = "Need 1 artifact (Garden Weaver)";
                    else node.statusMsg = mechanicDescription(node.mechanic);
                    break;
                default:
                    node.unlocked = true;
                    break;
            }
        }
    }

    /**
     * Checks whether the player is near any GameNode and updates active flags.
     * @param playerAngle player's current longitude in degrees
     * @param playerLat   player's current latitude in degrees
     * @param proximity   how close (in degrees) to count as "near"
     */
    public static WorldState.GameNode findNearestActive(
        List<WorldState.GameNode> nodes,
        float playerAngle, float playerLat, float proximity)
    {
        WorldState.GameNode nearest = null;
        float minDist = Float.MAX_VALUE;

        for (WorldState.GameNode node : nodes)
        {
            if (!node.unlocked) continue;

            float da = Math.abs(playerAngle - node.angleDeg);
            if (da > 180) da = 360 - da; // wrap longitude
            float dl = Math.abs(playerLat - node.latDeg);
            float dist = (float) Math.sqrt(da*da + dl*dl);

            node.active = (dist < proximity);
            if (node.active && dist < minDist)
            {
                minDist = dist;
                nearest = node;
            }
        }

        return nearest;
    }

    // ── MECHANIC DETECTION ────────────────────────────────────────────────────

    private static String detectMechanic(String content)
    {
        // Score each mechanic by how many keywords match
        int bestScore = 0;
        String bestMech = MECH_UNKNOWN;

        for (String[] entry : MECHANIC_KEYWORDS)
        {
            String mechanic = entry[0];
            int score = 0;
            for (int i = 1; i < entry.length; i++)
            {
                if (content.contains(entry[i])) score++;
            }
            if (score > bestScore)
            {
                bestScore = score;
                bestMech  = mechanic;
            }
        }

        return bestMech;
    }

    private static String extractTitle(String content, String filename)
    {
        // Try <title> tag first
        int start = content.indexOf("<title>");
        int end   = content.indexOf("</title>");
        if (start >= 0 && end > start)
        {
            String t = content.substring(start + 7, end).trim();
            // Strip "Aengloria: " prefix for brevity
            if (t.startsWith("Aengloria: ")) t = t.substring(11);
            if (!t.isEmpty()) return t;
        }

        // Fall back to filename without extension
        String name = filename.replaceAll("\\.html$", "");
        // CamelCase → spaced
        return name.replaceAll("([A-Z])", " $1").trim();
    }

    // ── LATITUDE SPREAD ───────────────────────────────────────────────────────
    // Spread nodes across latitudes so they're not all on the equator.
    // Uses a zigzag pattern: 0, +20, -20, +35, -35, +50...

    private static float latForIndex(int i, int total)
    {
        int band = i / 2;
        float lat = band * 20f;
        if (lat > 60f) lat = 60f;
        return (i % 2 == 0) ? lat : -lat;
    }

    // ── FILE READER ───────────────────────────────────────────────────────────

    private static String readFile(File f)
    {
        try
        {
            byte[] bytes = Files.readAllBytes(f.toPath());
            return new String(bytes, "UTF-8");
        }
        catch (IOException e)
        {
            return null;
        }
    }
}
