package modular.game;

/*
 * GameHiberfile.java
 *
 * Saves and restores game session state between launches.
 * Parallel to AIpy.hiberfile in GoddessAPI.py.
 *
 * File location: modular/game/saves/game.hiberfile
 * Format: plain text key=value (same shared format as hardware.cache)
 *
 * On save: writes player position, active planet, orbital angles,
 *          scale tier, world cache hash for fast resume validation.
 * On load: validates cache hash against current txt file state.
 *          If hash matches: world loads from cache (fast).
 *          If hash differs: world recalculates from source files (correct).
 *
 * Contributors:
 *   Derek Jason Gilhousen — hiberfile concept, cache validation design
 *   Claude (Anthropic)    — GameHiberfile implementation
 */

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class GameHiberfile
{
    private final File hiberFile;

    public GameHiberfile(File gameDir)
    {
        File savesDir = new File(gameDir, "saves");
        savesDir.mkdirs();
        this.hiberFile = new File(savesDir, "game.hiberfile");
    }

    // ── SAVE ──────────────────────────────────────────────────────────────────

    public void save(WorldState world)
    {
        try (PrintWriter pw = new PrintWriter(new FileWriter(hiberFile)))
        {
            pw.println("# game.hiberfile — Goddess Matrix game session state");
            pw.println("# Format: key=value — readable by GameHiberfile.java");
            pw.println("# Saved: " + Instant.now());
            pw.println();

            // Player position
            pw.println("player_angle_deg="    + world.playerAngleDeg);
            pw.println("player_lat_deg="      + world.playerLatDeg);
            pw.println("player_altitude_ft="  + world.playerAltitudeFeet);
            pw.println("player_height_ft="    + world.playerHeightFeet);

            // Camera
            pw.printf("cam_pos=%.4f,%.4f,%.4f%n",
                world.camPos[0], world.camPos[1], world.camPos[2]);
            pw.printf("cam_target=%.4f,%.4f,%.4f%n",
                world.camTarget[0], world.camTarget[1], world.camTarget[2]);

            // Active planet and orbital state
            pw.println("active_planet="       + world.activePlanet);
            pw.println("scale_tier="          + world.scaleTier);
            pw.println("world_scale_mult="    + world.worldScaleMultiplier);

            // Orbital angles for all fn nodes
            StringBuilder orb = new StringBuilder();
            for (int i = 1; i <= 12; i++)
            {
                if (i > 1) orb.append(",");
                orb.append(String.format("%.2f", world.orbitAngles[i]));
            }
            pw.println("orbit_angles=" + orb);

            // World calculation cache hash
            // Built from hashes of all active txt files.
            // On next load, recompute and compare — skip recalc if match.
            pw.println("world_cache_hash=" + buildCacheHash(world));

            // LOD thresholds (hardware-tuned at runtime)
            pw.println("lod_char_threshold="  + world.lodCharacterThreshold);
            pw.println("lod_entry_threshold=" + world.lodEntryThreshold);
            pw.println("lod_file_threshold="  + world.lodFileThreshold);

            // Avatar state
            pw.printf("avatar_pos=%.4f,%.4f,%.4f%n",
                world.avatarPos[0], world.avatarPos[1], world.avatarPos[2]);
            pw.println("avatar_glow="         + world.avatarGlowIntensity);

            // Modified sphere log — records which source chars were changed
            // so flush-back only rewrites what was actually modified
            int modifiedCount = 0;
            if (world.planets[world.activePlanet] != null)
            {
                for (WorldState.TerrainSphere s :
                     world.planets[world.activePlanet].terrain)
                {
                    if (s.modified) modifiedCount++;
                }
            }
            pw.println("modified_sphere_count=" + modifiedCount);

        }
        catch (IOException e)
        {
            // Hiberfile write failure is non-fatal
            System.err.println("GameHiberfile: save failed — " + e.getMessage());
        }
    }

    // ── LOAD ──────────────────────────────────────────────────────────────────

    /**
     * Loads saved state into WorldState.
     * Returns true if cache hash is valid (fast resume possible).
     * Returns false if hash mismatch (world needs recalculation).
     */
    public boolean load(WorldState world)
    {
        if (!hiberFile.exists()) return false;

        Map<String, String> kv = parseKV(hiberFile);
        if (kv.isEmpty()) return false;

        try
        {
            // Player
            world.playerAngleDeg     = parseFloat(kv, "player_angle_deg",   0f);
            world.playerLatDeg       = parseFloat(kv, "player_lat_deg",     0f);
            world.playerAltitudeFeet = parseFloat(kv, "player_altitude_ft", 0f);
            world.playerHeightFeet   = parseFloat(kv, "player_height_ft",   5.5f);

            // Camera
            float[] cp = parseVec3(kv, "cam_pos",    0f, 5f, -10f);
            float[] ct = parseVec3(kv, "cam_target",  0f, 0f,  0f);
            world.camPos    = cp;
            world.camTarget = ct;

            // Planet and scale
            world.activePlanet        = parseInt(kv, "active_planet",    WorldState.FN_EARTH);
            world.scaleTier           = parseInt(kv, "scale_tier",       0);
            world.worldScaleMultiplier = parseFloat(kv, "world_scale_mult", 1f);

            // Orbital angles
            String orb = kv.getOrDefault("orbit_angles", "");
            if (!orb.isEmpty())
            {
                String[] parts = orb.split(",");
                for (int i = 0; i < parts.length && i + 1 <= 12; i++)
                {
                    try { world.orbitAngles[i + 1] = Float.parseFloat(parts[i].trim()); }
                    catch (NumberFormatException ignored) {}
                }
            }

            // LOD thresholds
            world.lodCharacterThreshold = parseFloat(kv, "lod_char_threshold",  6f);
            world.lodEntryThreshold     = parseFloat(kv, "lod_entry_threshold", 30f);
            world.lodFileThreshold      = parseFloat(kv, "lod_file_threshold",  150f);

            // Avatar
            float[] av = parseVec3(kv, "avatar_pos", 1f, 0f, 0f);
            world.avatarPos          = av;
            world.avatarGlowIntensity = parseFloat(kv, "avatar_glow", 0.2f);

            // Validate cache hash
            String savedHash   = kv.getOrDefault("world_cache_hash", "");
            String currentHash = buildCacheHash(world);
            return savedHash.equals(currentHash) && !savedHash.isEmpty();
        }
        catch (Exception e)
        {
            // Corrupted hiberfile — signal full recalc
            return false;
        }
    }

    // ── CACHE HASH ────────────────────────────────────────────────────────────
    // Simple hash of last-modified timestamps and sizes of all active txt files.
    // Cheap to compute. Collision risk is acceptable for this use case —
    // worst case is an unnecessary world recalculation, not data corruption.

    private String buildCacheHash(WorldState world)
    {
        StringBuilder sb = new StringBuilder();

        WorldState.SystemPlanet planet = world.planets[world.activePlanet];
        if (planet == null) return "empty";

        appendFileHash(sb, planet.encyclopediaFile);
        appendFileHash(sb, planet.dictionaryFile);
        appendFileHash(sb, planet.almanacFile);
        appendFileHash(sb, planet.religionFile);
        appendFileHash(sb, planet.personaFile);
        appendFileHash(sb, planet.journalFile);

        // Simple djb2-style hash of the combined string
        long hash = 5381L;
        for (char c : sb.toString().toCharArray())
        {
            hash = ((hash << 5) + hash) + c;
        }
        return Long.toHexString(hash);
    }

    private void appendFileHash(StringBuilder sb, File f)
    {
        if (f != null && f.exists())
        {
            sb.append(f.getName())
              .append(":")
              .append(f.length())
              .append(":")
              .append(f.lastModified())
              .append("|");
        }
    }

    // ── KV PARSER ─────────────────────────────────────────────────────────────

    private Map<String, String> parseKV(File f)
    {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f)))
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq > 0)
                {
                    map.put(line.substring(0, eq).trim(),
                            line.substring(eq + 1).trim());
                }
            }
        }
        catch (IOException e) { /* return empty map */ }
        return map;
    }

    private float parseFloat(Map<String,String> kv, String key, float def)
    {
        try { return Float.parseFloat(kv.getOrDefault(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private int parseInt(Map<String,String> kv, String key, int def)
    {
        try { return Integer.parseInt(kv.getOrDefault(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private float[] parseVec3(Map<String,String> kv, String key,
                               float dx, float dy, float dz)
    {
        String v = kv.getOrDefault(key, "");
        if (!v.isEmpty())
        {
            String[] p = v.split(",");
            if (p.length == 3)
            {
                try
                {
                    return new float[]{
                        Float.parseFloat(p[0].trim()),
                        Float.parseFloat(p[1].trim()),
                        Float.parseFloat(p[2].trim())
                    };
                }
                catch (NumberFormatException ignored) {}
            }
        }
        return new float[]{dx, dy, dz};
    }

    public boolean exists() { return hiberFile.exists(); }
}
