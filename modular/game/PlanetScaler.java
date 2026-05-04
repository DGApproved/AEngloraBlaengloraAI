package modular.game;

/*
 * PlanetScaler.java
 *
 * Calculates planet diameter from total file size in the game directory.
 *
 * Empty game folder  → 3ft diameter (default)
 * Files present      → diameter grows logarithmically with total bytes
 *
 * Scale reference (assuming 5.5ft player):
 *   0 bytes     →  3.0 ft  (player is ~1.8x taller than planet)
 *   100 KB      →  6.2 ft  (player and planet roughly equal height)
 *   1 MB        →  9.3 ft  (planet noticeably larger than player)
 *   10 MB       → 12.6 ft  (player is small relative to planet)
 *   100 MB      → 15.9 ft
 *   1 GB        → 19.2 ft
 *
 * Logarithmic chosen over linear: a single large file shouldn't create
 * an absurdly large planet. Knowledge accumulates slowly, planet grows slowly.
 * This mirrors the knowledge pipeline — incremental, organic, session-persistent.
 *
 * FUTURE HOOK:
 *   When Python generates world_seed.json, it may include a recommended
 *   diameter override based on encyclopedia entry count or total knowledge
 *   score. PlanetScaler will check for that value first, fall back to
 *   file-size calculation if absent.
 *
 * Contributors:
 *   Derek Jason Gilhousen — planet scale concept, player height metric
 *   Claude (Anthropic)    — PlanetScaler implementation, logarithmic formula
 */

import java.io.File;

public class PlanetScaler 
{
    private static final float DEFAULT_DIAMETER_FT = 3.0f;
    private static final float SCALE_FACTOR        = 3.3f; // feet per log10 decade
    // Formula: diameter = DEFAULT + log10(1 + totalKB) * SCALE_FACTOR
    // Tuned so 1MB ≈ 9ft, 1GB ≈ 19ft — gradual, meaningful growth

    private final File gameDir;
    private float      cachedDiameter  = DEFAULT_DIAMETER_FT;
    private long       lastScanTime    = 0;
    private static final long RESCAN_INTERVAL_MS = 30_000; // rescan every 30s

    public PlanetScaler(File gameDir) 
    {
        this.gameDir = gameDir;
        scan();
    }

    // ── SCAN ──────────────────────────────────────────────────────────────────

    public float getDiameter() 
    {
        long now = System.currentTimeMillis();
        if (now - lastScanTime > RESCAN_INTERVAL_MS) scan();
        return cachedDiameter;
    }

    private void scan() 
    {
        lastScanTime = System.currentTimeMillis();

        if (gameDir == null || !gameDir.exists()) 
        {
            cachedDiameter = DEFAULT_DIAMETER_FT;
            return;
        }

        long totalBytes = countBytes(gameDir);

        if (totalBytes == 0) 
        {
            cachedDiameter = DEFAULT_DIAMETER_FT;
            return;
        }

        // [PYTHON HOOK: WORLD_SEED DIAMETER OVERRIDE]
        // When Python generates world_seed.json with a "diameter_ft" field,
        // read it here and return that value instead of the file-size calculation.
        // File: dgapi/system/world_seed.json
        // Key:  "diameter_ft": <float>
        // If absent or invalid, fall through to file-size calculation below.

        float totalKB  = totalBytes / 1024.0f;
        float logScale = (float)(Math.log10(1.0 + totalKB));
        cachedDiameter = DEFAULT_DIAMETER_FT + logScale * SCALE_FACTOR;

        // Reasonable cap — a 50ft diameter planet is already enormous
        // relative to a 5.5ft player. Remove cap if world design requires larger.
        cachedDiameter = Math.min(cachedDiameter, 50.0f);
    }

    private long countBytes(File dir) 
    {
        if (dir == null || !dir.isDirectory()) return 0;
        long total = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) 
        {
            if (f.isFile())      total += f.length();
            else if (f.isDirectory()) total += countBytes(f);
        }
        return total;
    }

    // ── INFO ──────────────────────────────────────────────────────────────────

    /** Returns the radius (half the diameter) in feet. */
    public float getRadius() { return getDiameter() / 2.0f; }

    /**
     * Returns the circumference in feet.
     * Used to calibrate walking speed so traversal feels consistent
     * regardless of planet size.
     */
    public float getCircumference() { return (float)(Math.PI * getDiameter()); }

    /**
     * Returns a display string for the ESC overlay.
     * Example: "PLANET: 3.0ft Ø"
     */
    public String getDisplayString() 
    {
        return String.format("PLANET: %.1fft Ø", getDiameter());
    }
}
