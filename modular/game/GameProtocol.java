package modular.game;

/*
 * GameProtocol.java
 *
 * Bridges the game engine and the AI runtime systems.
 *
 * Two directions:
 *
 *   INBOUND  (AI → Game):
 *     Receives protocol tags from GoddessAPI.py via ApiBridge.java.
 *     ApiBridge.parseAPIOutput() dispatches game tags to this class.
 *     Tags: [WORLD_SEED:], [WORLD_UPDATE:], [ENTITY_SPAWN:],
 *           [WORLD_EVENT:], [GAME_NARRATE:]
 *
 *   OUTBOUND (Game → AI):
 *     Writes game events to game_events.txt.
 *     GoddessAPI.sh tails this file and processes events through
 *     the knowledge pipeline (theory promotion, journal entries, etc.)
 *     The game never writes directly to encyclopedia.txt or religion.txt.
 *     It feeds events to the Bash AI which applies its own logic.
 *
 * LAUNCH CONTEXT:
 *   Matrix context:  full protocol, events written to dgapi/ path
 *   Standalone:      events written to local game_events.txt only
 *                    (no AI runtime present to process them,
 *                    but the log is preserved for later sessions)
 *
 * BASH AI HOOK:
 *   GoddessAPI.sh will watch game_events.txt via its log watcher.
 *   When it detects a DISCOVER event, it increments TIMES_PROPOSED
 *   on the matching theory in religion.txt.
 *   This makes player exploration a passive training mechanism for the AI.
 *   The game world gets richer as the knowledge base grows.
 *   [HOOK: extend GoddessAPI.sh with game_events.txt watcher]
 *
 * JAVA APIBRIGE HOOK:
 *   ApiBridge.parseAPIOutput() needs a case for [WORLD_*] and [GAME_*] tags.
 *   When those tags arrive on stdout from Python, ApiBridge should call:
 *     if (state.gameProtocol != null) state.gameProtocol.handleTag(line);
 *   MatrixState needs: public GameProtocol gameProtocol = null;
 *   Game.java sets this on launch when running in Matrix context.
 *   [HOOK: add to MatrixState, wire in ApiBridge.parseAPIOutput()]
 *
 * Contributors:
 *   Derek Jason Gilhousen — AI/game integration philosophy, event model
 *   Claude (Anthropic)    — GameProtocol implementation, event format,
 *                           hook stubs for ApiBridge and GoddessAPI.sh
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;

public class GameProtocol 
{
    // ── EVENT FILE ────────────────────────────────────────────────────────────
    // game_events.txt format (one event per line):
    // TIMESTAMP|EVENT_TYPE|DATA
    // Example: 2025-01-15T14:23:00|DISCOVER|theory_word_entropy
    //          2025-01-15T14:23:30|IDLE_AT|landmark_entropy_well|12s
    //          2025-01-15T14:24:00|JUMP|altitude:3.2ft|count:5

    private File eventsFile;
    private boolean matrixContext;
    private WorldState world;
    private SidePanels sidePanels;

    // ─────────────────────────────────────────────────────────────────────────

    public GameProtocol(WorldState world, SidePanels sidePanels,
                        File gameDir, File dgapiDir, boolean matrixContext) 
    {
        this.world         = world;
        this.sidePanels    = sidePanels;
        this.matrixContext = matrixContext;

        // In Matrix context, write events to the AI's dgapi directory
        // so GoddessAPI.sh can watch and process them.
        // In standalone: write to local game saves folder.
        if (matrixContext && dgapiDir != null && dgapiDir.exists()) 
        {
            File savesDir = new File(dgapiDir, "system");
            savesDir.mkdirs();
            eventsFile = new File(savesDir, "game_events.txt");
        } 
        else 
        {
            File savesDir = new File(gameDir, "saves");
            savesDir.mkdirs();
            eventsFile = new File(savesDir, "game_events.txt");
        }
    }

    // ── INBOUND TAG HANDLER ───────────────────────────────────────────────────
    // Called by ApiBridge.parseAPIOutput() when game protocol tags arrive.
    // [HOOK: wire this into ApiBridge — see class header]

    public void handleTag(String line) 
    {
        if (line == null) return;

        if (line.startsWith("[WORLD_SEED:")) 
        {
            String path = line.substring(12, line.length() - 1).trim();
            File seedFile = new File(path);
            if (seedFile.exists()) 
            {
                world.worldSeedFile  = seedFile;
                world.worldSeedDirty = true;
            }
        }
        else if (line.startsWith("[WORLD_UPDATE:")) 
        {
            // Format: [WORLD_UPDATE:op:uid]
            String payload = line.substring(14, line.length() - 1);
            String[] parts = payload.split(":", 2);
            if (parts.length == 2) handleWorldUpdate(parts[0], parts[1]);
        }
        else if (line.startsWith("[ENTITY_SPAWN:")) 
        {
            // Format: [ENTITY_SPAWN:type:angleDeg:latitude:label]
            String payload = line.substring(14, line.length() - 1);
            String[] parts = payload.split(":", 4);
            if (parts.length == 4) 
            {
                try 
                {
                    world.entities.add(new WorldState.GameEntity(
                        parts[0],
                        Float.parseFloat(parts[1]),
                        Float.parseFloat(parts[2]),
                        parts[3]
                    ));
                } 
                catch (NumberFormatException ignored) {}
            }
        }
        else if (line.startsWith("[WORLD_EVENT:")) 
        {
            // Format: [WORLD_EVENT:type:intensity]
            String payload = line.substring(13, line.length() - 1);
            String[] parts = payload.split(":", 2);
            if (parts.length == 2) 
            {
                world.weatherType = parts[0];
                try { world.weatherIntensity = Float.parseFloat(parts[1]); }
                catch (NumberFormatException ignored) {}
            }
        }
        else if (line.startsWith("[GAME_NARRATE:")) 
        {
            // Format: [GAME_NARRATE:text]
            // Content from Python. Java renders it, Python writes what it says.
            String text = line.substring(14, line.length() - 1).trim();
            world.narrateText     = text;
            world.narrateExpireMs = System.currentTimeMillis()
                                  + WorldState.NARRATE_DEFAULT_MS;
        }
        else if (line.startsWith("[PANEL_UPDATE:")) 
        {
            // Format: [PANEL_UPDATE:side:content]
            // Python sends panel content; Java renders it.
            String payload = line.substring(14, line.length() - 1);
            int colon = payload.indexOf(':');
            if (colon > 0) 
            {
                String side    = payload.substring(0, colon);
                String content = payload.substring(colon + 1);
                sidePanels.setContent(side, content);
            }
        }
    }

    private void handleWorldUpdate(String op, String uid) 
    {
        switch (op) 
        {
            case "promote":
                // Theory promoted to encyclopedia — find matching entity and stabilize
                for (WorldState.GameEntity e : world.entities) 
                {
                    if (uid.equals(e.label)) 
                    {
                        e.type = "landmark"; // promoted = stable landmark
                        break;
                    }
                }
                break;

            case "reveal":
                // [FUTURE: reveal hidden entity at uid location]
                break;

            case "decay":
                // [FUTURE: mark entity as stale/fading]
                break;

            case "texture":
                // Python has generated a new sphere texture
                File textureFile = new File(uid);
                if (textureFile.exists()) 
                {
                    world.sphereTextureFile    = textureFile;
                    world.textureReloadNeeded  = true;
                }
                break;
        }
    }

    // ── OUTBOUND EVENT WRITER ─────────────────────────────────────────────────
    // Writes game events to game_events.txt for GoddessAPI.sh to process.
    // All writes are append-only — events are never deleted or modified.
    // [HOOK: GoddessAPI.sh watches this file — see class header]

    public void writeEvent(String eventType, String data) 
    {
        if (eventsFile == null) return;
        String line = Instant.now().toString()
                    + "|" + eventType
                    + "|" + data;
        try (PrintWriter pw = new PrintWriter(new FileWriter(eventsFile, true))) 
        {
            pw.println(line);
            world.pendingEvents.add(line);
        } 
        catch (IOException ignored) {}
    }

    // ── CONVENIENCE EVENT WRITERS ─────────────────────────────────────────────

    /** Player walked over a theory location. */
    public void writeDiscoveryEvent(String theoryUID) 
    {
        world.lastDiscoveryUID = theoryUID;
        writeEvent("DISCOVER", theoryUID);
    }

    /** Player stood at a named landmark for a significant duration. */
    public void writeIdleAtEvent(String landmarkName, long seconds) 
    {
        writeEvent("IDLE_AT", landmarkName + "|" + seconds + "s");
    }

    /** Player jumped — altitude and cumulative count recorded. */
    public void writeJumpEvent(float altitude, int jumpCount) 
    {
        writeEvent("JUMP", "altitude:" + String.format("%.1f", altitude)
                         + "ft|count:" + jumpCount);
    }

    /** Session started at this FN node. */
    public void writeSessionEnterEvent(int fnNode) 
    {
        writeEvent("SESSION_ENTER", "fn" + fnNode);
    }

    /** Session ended. */
    public void writeSessionExitEvent(int fnNode) 
    {
        writeEvent("SESSION_EXIT", "fn" + fnNode);
    }

    // ── HARDWARE TELEMETRY READER ─────────────────────────────────────────────
    // [PYTHON HOOK: hardware_live.txt]
    // Called on logic tick. Reads hardware_live.txt written by GoddessAPI.py
    // monitor thread. Updates WorldState atmosphere values.

    public void pollHardwareTelemetry(File dgapiSystemDir) 
    {
        if (dgapiSystemDir == null) return;
        File liveFile = new File(dgapiSystemDir, "hardware_live.txt");
        if (!liveFile.exists()) return;

        try 
        {
            String content = new String(java.nio.file.Files.readAllBytes(liveFile.toPath()));
            // Format: CPU=42.0% | RAM=61.0% | GPU_VRAM=2048MB/6144MB | ...
            for (String token : content.split("\\|")) 
            {
                token = token.trim();
                if (token.startsWith("CPU=")) 
                {
                    world.cpuLoad = parseFloat(token.substring(4).replace("%", ""));
                } 
                else if (token.startsWith("RAM=")) 
                {
                    world.ramPercent = parseFloat(token.substring(4).replace("%", ""));
                } 
                else if (token.startsWith("GPU_UTIL=")) 
                {
                    world.gpuUtil = parseFloat(token.substring(9).replace("%", ""));
                }
            }

            // CPU load drives weather intensity (high load = stormy)
            world.weatherIntensity = Math.max(world.weatherIntensity * 0.9f,
                                              world.cpuLoad * 0.4f);

        } 
        catch (Exception ignored) {}
    }

    private float parseFloat(String s) 
    {
        try { return Float.parseFloat(s.trim()); }
        catch (Exception e) { return 0.0f; }
    }
}
