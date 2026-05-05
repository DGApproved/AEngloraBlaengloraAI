package modular.game;

/*
 * PlanetScaler.java
 *
 * Calculates planet scale from game folder file count and manages
 * the hierarchical scale tier system.
 *
 * SCALE MODEL:
 *   Base: 3ft diameter, 1 sphere = full budget
 *   Each tier: diameter divided by SUBDIVISIONS (4)
 *   Minimum sphere size: 6in (0.5ft)
 *   When next tier would breach minimum: worldScaleMultiplier doubles
 *   and tier resets — universe grows rather than spheres shrinking further.
 *
 *   This means more data = larger universe, not smaller spheres.
 *   Performance self-regulates: weaker hardware uses fewer LOD tiers.
 *
 * BEDROCK COUNT:
 *   = number of files in modular/game/ directory
 *   Same across all planetary bodies.
 *
 * Contributors:
 *   Derek Jason Gilhousen — scale tier concept, minimum sphere size,
 *                           world doubling mechanic
 *   Claude (Anthropic)    — PlanetScaler implementation
 */

import java.io.File;

public class PlanetScaler
{
    private final File gameDir;

    private int   bedrockCount        = 1;
    private int   scaleTier           = 0;
    private float worldScaleMultiplier = 1.0f;
    private float currentSphereRadius  = WorldState.BASE_DIAMETER_FT / 2f;

    private long  lastScanMs    = 0;
    private static final long RESCAN_INTERVAL = 30_000L;

    public PlanetScaler(File gameDir)
    {
        this.gameDir = gameDir;
        scan();
    }

    // ── SCAN ──────────────────────────────────────────────────────────────────

    private void scan()
    {
        lastScanMs = System.currentTimeMillis();
        if (gameDir == null || !gameDir.exists()) return;

        bedrockCount = countFiles(gameDir);
        if (bedrockCount < 1) bedrockCount = 1;

        recalculateTier();
    }

    private void recalculateTier()
    {
        // Reset to base
        scaleTier           = 0;
        worldScaleMultiplier = 1.0f;

        // Subdivide until we have enough tiers to fit bedrockCount spheres,
        // doubling the world scale when minimum sphere size would be breached.
        int spheresAtTier = 1;
        while (spheresAtTier < bedrockCount)
        {
            if (WorldState.tierBreachesMinimum(scaleTier + 1, worldScaleMultiplier))
            {
                // World doubles, tier resets
                worldScaleMultiplier *= 2.0f;
                scaleTier = 0;
                spheresAtTier = 1;
            }
            else
            {
                scaleTier++;
                spheresAtTier *= WorldState.SUBDIVISIONS;
            }
        }

        float diameter = WorldState.sphereDiameterAtTier(scaleTier, worldScaleMultiplier);
        currentSphereRadius = diameter / 2f;
    }

    private int countFiles(File dir)
    {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File f : files)
        {
            if (f.isFile()) count++;
            // Subdirectories not counted — only direct game files
            // define the bedrock layer
        }
        return count;
    }

    // ── ACCESSORS ─────────────────────────────────────────────────────────────

    public int getBedrockCount()
    {
        maybeRescan();
        return bedrockCount;
    }

    public int getScaleTier()
    {
        maybeRescan();
        return scaleTier;
    }

    public float getWorldScaleMultiplier()
    {
        maybeRescan();
        return worldScaleMultiplier;
    }

    public float getSphereRadius()
    {
        maybeRescan();
        return currentSphereRadius;
    }

    public float getSphereDiameter()
    {
        return getSphereRadius() * 2f;
    }

    public float getPlanetCoreRadius()
    {
        // Planet core is large enough to always stand on
        // regardless of individual bedrock sphere size
        maybeRescan();
        return WorldState.BASE_DIAMETER_FT * worldScaleMultiplier / 2f;
    }

    public String getDisplayString()
    {
        return String.format(
            "PLANET: %.2fft Ø | TIER:%d | SCALE:x%.0f | BEDROCK:%d",
            getSphereDiameter(),
            scaleTier,
            worldScaleMultiplier,
            bedrockCount
        );
    }

    /** Write current scale state into WorldState. */
    public void applyToWorld(WorldState world)
    {
        maybeRescan();
        world.scaleTier            = scaleTier;
        world.worldScaleMultiplier = worldScaleMultiplier;
        world.activeSphereRadius   = currentSphereRadius;
    }

    private void maybeRescan()
    {
        if (System.currentTimeMillis() - lastScanMs > RESCAN_INTERVAL) scan();
    }

    // ── WORLD STATE FIELD (added for compatibility) ───────────────────────────
    // WorldState needs activeSphereRadius — adding via extension note.
    // Add to WorldState: public float activeSphereRadius = 1.5f;
}
