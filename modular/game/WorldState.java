package modular.game;

/*
 * WorldState.java
 *
 * Central state container for the Goddess Matrix game engine.
 * Single source of truth for all mutable game state.
 * Read by renderers, written by physics and protocol handlers.
 *
 * Design mirrors MatrixState.java in the core system:
 * no logic, no circular dependencies, pure data.
 *
 * PYTHON INTEGRATION HOOKS:
 *   worldSeedPath     → path to Python-generated world_seed.json
 *   narrateText       → text received via [GAME_NARRATE:] protocol tag
 *   pendingEntities   → entities queued via [ENTITY_SPAWN:] tags
 *   weatherIntensity  → set via [WORLD_EVENT:] tags
 *   textureFile       → Python-generated sphere texture (PNG)
 *   voidImageFiles    → C/ASM-generated void background images
 *
 * BASH AI HOOKS:
 *   lastDiscoveryUID  → theory UID last discovered by player
 *                       written to game_events.txt for GoddessAPI.sh
 *   pendingEvents     → event queue draining to game_events.txt
 *
 * Contributors:
 *   Derek Jason Gilhousen — world design, integration philosophy
 *   Claude (Anthropic)    — WorldState implementation, hook stubs
 */

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WorldState 
{
    // ── LAUNCH CONTEXT ────────────────────────────────────────────────────────
    public boolean launchedFromMatrix = false;   // set by Game.java constructor path

    // ── PLANET SCALE ──────────────────────────────────────────────────────────
    public float  planetDiameterFeet = 3.0f;     // set by PlanetScaler at launch
    public float  playerHeightFeet   = 5.5f;     // default, variable later
    public float  playerAngleDeg     = 0.0f;     // current position on sphere (0-360)
    public float  playerAltitudeFeet = 0.0f;     // feet above surface (0 = standing)
    public float  verticalVelocity   = 0.0f;     // feet/tick, positive = away from center
    public boolean isJumping         = false;

    // ── CAMERA ────────────────────────────────────────────────────────────────
    public float cameraElevationDeg  = 25.0f;    // degrees above equator
    public float cameraDistanceFeet  = 12.0f;    // feet from sphere center

    // ── TIMING ────────────────────────────────────────────────────────────────
    public int  timingMode           = TimingBridge.MODE_GAMEPLAY;
    public boolean overlayVisible    = false;    // ESC active
    public boolean panelsVisible     = false;    // E active

    // ── UI / OVERLAY ──────────────────────────────────────────────────────────
    public float  panelSlideProgress = 0.0f;     // 0.0 = closed, 1.0 = fully open
                                                  // animated toward target each tick
    public float  panelSlideTarget   = 0.0f;     // 0.0 or 1.0
    public static final float PANEL_SLIDE_SPEED = 0.08f; // per tick

    // ── IN-GAME NARRATIVE TEXT ────────────────────────────────────────────────
    // [PYTHON HOOK: GAME_NARRATE]
    // Populated by GameProtocol when [GAME_NARRATE:text] tag received from Python.
    // Rendered as floating overlay text by IceSandbox.
    // Content is Python-owned. Rendering position/style is Java-owned.
    public String narrateText        = "";
    public long   narrateExpireMs    = 0;        // System.currentTimeMillis() deadline
    public static final long NARRATE_DEFAULT_MS = 6000L;

    // ── WORLD SEED ────────────────────────────────────────────────────────────
    // [PYTHON HOOK: WORLD_SEED]
    // Path set by GameProtocol when [WORLD_SEED:path] tag received.
    // IceSandbox reloads world geometry when this changes.
    public File   worldSeedFile      = null;
    public boolean worldSeedDirty    = false;    // true = reload needed

    // ── SPHERE TEXTURE ────────────────────────────────────────────────────────
    // [PYTHON HOOK: TEXTURE GENERATION]
    // Python generates sphere surface textures via GGUF or rule-based methods.
    // Texture file is written to modular/game/textures/ by Python.
    // SphereRenderer polls this path; falls back to procedural gradient if absent.
    // File name convention: sphere_fn{N}.png where N = current session node.
    public File   sphereTextureFile  = null;     // null = use procedural fallback
    public boolean textureReloadNeeded = false;

    // ── VOID IMAGES ───────────────────────────────────────────────────────────
    // [C/ASM HOOK: VOID BACKGROUND]
    // C and ASM programs generate background images written to modular/game/images/.
    // VoidRenderer scans this directory and composites available images.
    // If directory is empty, VoidRenderer uses procedural starfield.
    public File   voidImagesDir      = null;
    public List<File> voidImages     = new ArrayList<>();
    public boolean voidImagesDirty   = false;

    // ── WEATHER / ATMOSPHERE ──────────────────────────────────────────────────
    // [PYTHON HOOK: WORLD_EVENT]
    // Set by GameProtocol when [WORLD_EVENT:type:intensity] tag received.
    // Java renders; Python drives content.
    public float  weatherIntensity   = 0.0f;     // 0-100
    public String weatherType        = "clear";  // clear, storm, aurora, fog, quake

    // ── HARDWARE TELEMETRY ATMOSPHERE ─────────────────────────────────────────
    // [PYTHON HOOK: hardware_live.txt]
    // Polled by game logic tick from dgapi/system/hardware_live.txt.
    // CPU load → weather intensity. GPU util → aurora brightness.
    // File written by GoddessAPI.py hardware monitor thread.
    public float  cpuLoad            = 0.0f;
    public float  gpuUtil            = 0.0f;
    public float  ramPercent         = 0.0f;

    // ── ENTITIES ──────────────────────────────────────────────────────────────
    // [PYTHON HOOK: ENTITY_SPAWN]
    // Populated by GameProtocol when [ENTITY_SPAWN:type:x:y:label] tag received.
    // Rendered by IceSandbox; content from Python/encyclopedia pipeline.
    public List<GameEntity> entities = new ArrayList<>();

    // ── DISCOVERY STATE ───────────────────────────────────────────────────────
    // [BASH AI HOOK: game_events.txt]
    // When player discovers a theory location, lastDiscoveryUID is set.
    // GameProtocol writes a DISCOVER event to game_events.txt.
    // GoddessAPI.sh tails that file and increments TIMES_PROPOSED.
    public String lastDiscoveryUID   = null;
    public List<String> pendingEvents = new ArrayList<>();

    // ── SIMPLE ENTITY RECORD ─────────────────────────────────────────────────

    public static class GameEntity 
    {
        public String type;       // landmark, anomaly, ruin, signal
        public float  angleDeg;   // position on sphere (longitude)
        public float  latitude;   // position on sphere (0 = equator, 90 = pole)
        public String label;      // display name

        public GameEntity(String type, float angleDeg, float latitude, String label) 
        {
            this.type     = type;
            this.angleDeg = angleDeg;
            this.latitude = latitude;
            this.label    = label;
        }
    }
}
