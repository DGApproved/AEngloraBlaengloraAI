package modular.game;

/*
 * GameProtocol.java
 *
 * Bridges the game engine and AI runtime systems.
 *
 * INBOUND (AI → Game):
 *   Receives protocol tags forwarded by ApiBridge from GoddessAPI stdout.
 *   Tags: [WORLD_SEED:] [WORLD_UPDATE:] [ENTITY_SPAWN:]
 *         [WORLD_EVENT:] [GAME_NARRATE:] [PANEL_UPDATE:]
 *
 * OUTBOUND (Game → AI):
 *   Event writes to game_events.txt for GoddessAPI.sh to tail.
 *   C key: direct stdin write to the active Python session.
 *   Game never writes directly to knowledge files.
 *
 * C KEY CHAT CONDUIT:
 *   pythonStdin set by IceSandbox from MatrixState.apiStdinMap at launch.
 *   C key press → avatarChatOpen = true → typed text routes to sendToPython().
 *   Python responds via [GAME_NARRATE:] tag → renders above avatar head.
 *   Direct stdin is faster than the session log roundtrip.
 *   Null in standalone mode — falls back to event file log only.
 *
 * Contributors:
 *   Derek Jason Gilhousen — AI/game integration philosophy, C key design
 *   Claude (Anthropic)    — GameProtocol implementation
 */

import java.io.*;
import java.time.Instant;

public class GameProtocol
{
    private final WorldState world;
    private final SidePanels sidePanels;
    private final File       eventsFile;
    private final boolean    matrixContext;

    // Matrix capability hooks — set by Game.java after launch.
    // Null in standalone mode; each use is guarded.
    private system.MatrixState.AISendHook  aiSendHook  = null;
    private system.MatrixState.ErrorHook   errorHook   = null;
    private system.MatrixState.ChatAppendHook chatHook  = null;

    // Fallback for standalone mode — direct stdin when no hook available.
    private PrintWriter pythonStdin = null;

    // ─────────────────────────────────────────────────────────────────────────

    public GameProtocol(WorldState world, SidePanels sidePanels,
                        File gameDir, File dgapiDir, boolean matrixContext)
    {
        this.world         = world;
        this.sidePanels    = sidePanels;
        this.matrixContext = matrixContext;

        if (matrixContext && dgapiDir != null && dgapiDir.exists())
        {
            File sysDir = new File(dgapiDir, "system");
            sysDir.mkdirs();
            eventsFile = new File(sysDir, "game_events.txt");
        }
        else
        {
            File savesDir = new File(gameDir, "saves");
            savesDir.mkdirs();
            eventsFile = new File(savesDir, "game_events.txt");
        }
    }

    // ── C KEY CONDUIT ─────────────────────────────────────────────────────────

    /** Wired by Game.java from state.aiSendHook (preferred over direct stdin). */
    public void setAISendHook(system.MatrixState.AISendHook hook)  { this.aiSendHook = hook; }
    public void setErrorHook(system.MatrixState.ErrorHook hook)     { this.errorHook  = hook; }
    public void setChatHook(system.MatrixState.ChatAppendHook hook) { this.chatHook   = hook; }

    /** Fallback for standalone mode — direct stdin when no hook available. */
    public void setPythonStdin(PrintWriter stdin)
    {
        this.pythonStdin = stdin;
    }

    public void sendToPython(String text)
    {
        if (text == null || text.trim().isEmpty()) return;
        String t = text.trim();

        // Add to in-game chat history as the player's message
        addChatMessage("YOU", t);

        if (aiSendHook != null)
        {
            // Matrix hook path — preferred
            try { aiSendHook.send(t); }
            catch (Exception e) { logError("AISendHook", e); }
            writeEvent("C_KEY_QUERY", t.substring(0, Math.min(80, t.length())));
        }
        else if (pythonStdin != null)
        {
            // Standalone fallback — direct stdin
            pythonStdin.println(t);
            pythonStdin.flush();
            writeEvent("C_KEY_QUERY", t.substring(0, Math.min(80, t.length())));
        }
        else
        {
            writeEvent("C_KEY_QUERY_OFFLINE", t);
        }
    }

    private void addChatMessage(String speaker, String text)
    {
        world.chatMessages.addLast(new WorldState.ChatMessage(speaker, text));
        while (world.chatMessages.size() > WorldState.MAX_CHAT_MESSAGES)
            world.chatMessages.removeFirst();
    }

    private void logError(String context, Exception e)
    {
        if (errorHook != null) errorHook.log("GameProtocol:" + context, e);
        else writeEvent("ERROR", context + ": " + e.getMessage());
    }

    // ── INBOUND TAG HANDLER ───────────────────────────────────────────────────

    public void handleTag(String line)
    {
        if (line == null) return;

        if (line.startsWith("[WORLD_SEED:"))
        {
            String path = line.substring(12, line.length() - 1).trim();
            File f = new File(path);
            if (f.exists()) { world.worldSeedFile = f; world.worldSeedDirty = true; }
        }
        else if (line.startsWith("[WORLD_UPDATE:"))
        {
            String[] p = line.substring(14, line.length() - 1).split(":", 2);
            if (p.length == 2) handleWorldUpdate(p[0], p[1]);
        }
        else if (line.startsWith("[ENTITY_SPAWN:"))
        {
            String[] p = line.substring(14, line.length() - 1).split(":", 4);
            if (p.length == 4)
            {
                try {
                    world.entities.add(new WorldState.GameEntity(
                        p[0], Float.parseFloat(p[1]),
                        Float.parseFloat(p[2]), p[3]));
                } catch (NumberFormatException ignored) {}
            }
        }
        else if (line.startsWith("[WORLD_EVENT:"))
        {
            String[] p = line.substring(13, line.length() - 1).split(":", 2);
            if (p.length == 2)
            {
                world.weatherType = p[0];
                try { world.weatherIntensity = Float.parseFloat(p[1]); }
                catch (NumberFormatException ignored) {}
            }
        }
        else if (line.startsWith("[GAME_CHAT:"))
        {
            // [GAME_CHAT:] — shows in the in-game chat history overlay.
            // Use this for conversational AI responses.
            // Use [GAME_NARRATE:] for ambient floating text above the avatar.
            String text = line.substring(11, line.length() - 1).trim();
            addChatMessage("AI", text);
            // Also surface as narration briefly so the player sees it
            // even if the chat overlay isn't open.
            world.narrateText     = text;
            world.narrateExpireMs = System.currentTimeMillis() + 4000L;
        }
        else if (line.startsWith("[GAME_NARRATE:"))
        {
            // [GAME_NARRATE:] — floating text above avatar, no chat history.
            // Use for ambient scene description, world events.
            String text = line.substring(14, line.length() - 1).trim();
            world.narrateText     = text;
            world.narrateExpireMs = System.currentTimeMillis()
                                  + WorldState.NARRATE_DEFAULT_MS;
            if (world.avatarChatOpen) world.avatarSpeechText = text;
        }
        else if (line.startsWith("[PANEL_UPDATE:"))
        {
            String payload = line.substring(14, line.length() - 1);
            int c = payload.indexOf(':');
            if (c > 0) sidePanels.setContent(
                payload.substring(0, c), payload.substring(c + 1));
        }
    }

    private void handleWorldUpdate(String op, String uid)
    {
        switch (op)
        {
            case "promote":
                for (WorldState.GameEntity e : world.entities)
                    if (uid.equals(e.label)) { e.type = "landmark"; break; }
                break;

            case "texture":
                File tf = new File(uid);
                if (tf.exists())
                {
                    world.sphereTextureFile   = tf;
                    world.textureReloadNeeded = true;
                }
                break;
        }
    }

    // ── OUTBOUND EVENT WRITER ─────────────────────────────────────────────────

    public void writeEvent(String eventType, String data)
    {
        if (eventsFile == null) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(eventsFile, true)))
        {
            pw.println(Instant.now() + "|" + eventType + "|" + data);
        }
        catch (IOException ignored) {}
    }

    public void writeDiscoveryEvent(String uid)
    { world.lastDiscoveryUID = uid; writeEvent("DISCOVER", uid); }

    public void writeIdleAtEvent(String landmark, long seconds)
    { writeEvent("IDLE_AT", landmark + "|" + seconds + "s"); }

    public void writeJumpEvent(float altitude, int count)
    { writeEvent("JUMP", String.format("altitude:%.1fft|count:%d", altitude, count)); }

    public void writeSessionEnterEvent(int fn)
    { writeEvent("SESSION_ENTER", "fn" + fn); }

    public void writeSessionExitEvent(int fn)
    { writeEvent("SESSION_EXIT", "fn" + fn); }

    // ── HARDWARE TELEMETRY ────────────────────────────────────────────────────

    public void pollHardwareTelemetry(File dgapiSystemDir)
    {
        if (dgapiSystemDir == null) return;
        File liveFile = new File(dgapiSystemDir, "hardware_live.txt");
        if (!liveFile.exists()) return;
        try
        {
            String content = new String(
                java.nio.file.Files.readAllBytes(liveFile.toPath()));
            for (String token : content.split("\\|"))
            {
                token = token.trim();
                if      (token.startsWith("CPU="))
                    world.cpuLoad    = pf(token.substring(4).replace("%",""));
                else if (token.startsWith("RAM="))
                    world.ramPercent = pf(token.substring(4).replace("%",""));
                else if (token.startsWith("GPU_UTIL="))
                    world.gpuUtil    = pf(token.substring(9).replace("%",""));
            }
            // CPU load drives avatar glow intensity
            world.avatarGlowIntensity = 0.2f + (world.cpuLoad / 100f) * 0.8f;
        }
        catch (Exception ignored) {}
    }

    private float pf(String s)
    {
        try { return Float.parseFloat(s.trim()); }
        catch (Exception e) { return 0f; }
    }
}
