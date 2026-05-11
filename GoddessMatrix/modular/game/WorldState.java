package modular.game;

/*
 * WorldState.java
 *
 * Central state container for the Goddess Matrix game engine.
 * Pure data — no logic, no circular dependencies.
 *
 * CELESTIAL MAPPING (fn node → body):
 *   fn1  → Sun            (origin, self-luminous, high gravity)
 *   fn2  → Mercury        (small, dense, close orbit)
 *   fn3  → Venus          (dense atmosphere, slow rotation)
 *   fn4  → Earth          (default session, balanced)
 *   fn5  → Moon           (low gravity, Earth-adjacent)
 *   fn6  → Mars           (sparse, cold)
 *   fn7  → Asteroid Belt  (no single body, debris field)
 *   fn8  → Jupiter        (massive, high gravity, storm system)
 *   fn9  → Saturn         (rings as debris plane)
 *   fn10 → Uranus         (tilted axis)
 *   fn11 → Neptune        (distant, cold, sparse)
 *   fn12 → Kuiper Anomaly (outermost, user-named)
 *
 * MATERIAL SYSTEM:
 *   Each character in each .txt file = one sphere in-game.
 *   File type = material category. Character type = hardness.
 *
 *   dictionary.txt   → DIRT     (soft, abundant)
 *   almanac.txt      → STONE    (medium hardness)
 *   encyclopedia.txt → ORE      (hard, rare, validated)
 *   religion.txt     → CONVERTED_ORE (theories, partially processed)
 *   persona.txt      → SOIL     (personal, soft)
 *   journal.txt      → SEDIMENT (historical, compressed)
 *   comments (#...)  → CRAFTED  (composite, recipe-linked)
 *   .java files      → BEDROCK  (unbreakable)
 *
 * CHARACTER HARDNESS:
 *   whitespace   → 0 (void, air pockets)
 *   lowercase    → 1 (standard)
 *   uppercase    → 2 (named entity)
 *   digit        → 3 (quantified)
 *   punctuation  → 4 (connective)
 *   special char → 5 (structural: []=:)
 *
 * SCALE TIER SYSTEM:
 *   Base diameter: 3ft (1 sphere = full budget)
 *   Each subdivision divides diameter by SUBDIVISIONS (4)
 *   Minimum sphere size: 6in (0.5ft)
 *   At minimum: worldScaleMultiplier doubles, tier resets
 *   More data = larger universe. Self-regulating render budget.
 *
 * BEDROCK CORE:
 *   = number of files in modular/game/
 *   Same count on all planets — same rules govern all worlds.
 *   Guarantees standing surface even on empty fn nodes.
 *
 * STARS:
 *   assets/ and system/ folders → distant stars, always visible.
 *   File count determines brightness. Not visitable in V1.
 *
 * Contributors:
 *   Derek Jason Gilhousen — world design, material philosophy,
 *                           scale tier concept, celestial mapping,
 *                           character-as-sphere architecture
 *   Claude (Anthropic)    — WorldState implementation
 */

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WorldState
{
    // ── LAUNCH CONTEXT ────────────────────────────────────────────────────────
    public boolean launchedFromMatrix = false;

    // ── CELESTIAL BODY CONSTANTS ──────────────────────────────────────────────
    public static final int FN_SUN           = 1;
    public static final int FN_MERCURY       = 2;
    public static final int FN_VENUS         = 3;
    public static final int FN_EARTH         = 4;
    public static final int FN_MOON          = 5;
    public static final int FN_MARS          = 6;
    public static final int FN_ASTEROID_BELT = 7;
    public static final int FN_JUPITER       = 8;
    public static final int FN_SATURN        = 9;
    public static final int FN_URANUS        = 10;
    public static final int FN_NEPTUNE       = 11;
    public static final int FN_KUIPER        = 12;

    // ── MATERIAL TYPE CONSTANTS ───────────────────────────────────────────────
    public static final int MAT_VOID          = 0;
    public static final int MAT_DIRT          = 1;  // dictionary.txt
    public static final int MAT_SOIL          = 2;  // persona.txt
    public static final int MAT_SEDIMENT      = 3;  // journal.txt
    public static final int MAT_STONE         = 4;  // almanac.txt
    public static final int MAT_ORE           = 5;  // encyclopedia.txt
    public static final int MAT_CONVERTED_ORE = 6;  // religion.txt
    public static final int MAT_CRAFTED       = 7;  // comment blocks
    public static final int MAT_BEDROCK       = 8;  // .java files

    // ── CHARACTER HARDNESS CONSTANTS ──────────────────────────────────────────
    public static final int HARD_VOID       = 0;
    public static final int HARD_SOFT       = 1;
    public static final int HARD_MEDIUM     = 2;
    public static final int HARD_DENSE      = 3;
    public static final int HARD_JOINT      = 4;
    public static final int HARD_STRUCTURAL = 5;

    // ── SCALE TIER SYSTEM ─────────────────────────────────────────────────────
    public static final float BASE_DIAMETER_FT   = 3.0f;
    public static final float MIN_SPHERE_SIZE_FT = 0.5f;  // 6 inches
    public static final int   SUBDIVISIONS       = 4;

    public int   scaleTier            = 0;
    public float worldScaleMultiplier = 1.0f;
    public float activeSphereRadius   = BASE_DIAMETER_FT / 2f;

    // LOD thresholds in world units — tunable per hardware profile
    public float lodCharacterThreshold = 6.0f;
    public float lodEntryThreshold     = 30.0f;
    public float lodFileThreshold      = 150.0f;

    // ── SOLAR SYSTEM ──────────────────────────────────────────────────────────
    public SystemPlanet[] planets     = new SystemPlanet[13]; // 1-indexed
    public int            activePlanet = FN_EARTH;
    public float[]        sunPosition  = {0f, 0f, 0f};
    public float[]        orbitAngles  = new float[13];

    // ── STARS (assets/ and system/) ───────────────────────────────────────────
    public List<StarBody> stars = new ArrayList<>();

    // ── PLAYER ────────────────────────────────────────────────────────────────
    public float   playerAngleDeg     = 0f;
    public float   playerLatDeg       = 0f;
    public float   playerAltitudeFeet = 0f;
    public float   verticalVelocity   = 0f;
    public boolean isJumping          = false;
    public float   playerHeightFeet   = 5.5f;

    // ── CAMERA ────────────────────────────────────────────────────────────────
    public float[] camPos    = {0f, 5f, -10f};
    public float[] camTarget = {0f, 0f,   0f};
    public float   camFOV    = 60.0f;

    // Camera look angles controlled by IceSandbox arrow keys / mouse look
    public float   cameraYaw   = 0f;
    public float   cameraPitch = 15f;

    // ── TIMING / UI ───────────────────────────────────────────────────────────
    public int     timingMode         = TimingBridge.MODE_GAMEPLAY;
    public boolean overlayVisible     = false;
    public boolean panelsVisible      = false;
    public float   panelSlideProgress = 0f;
    public float   panelSlideTarget   = 0f;
    public static final float PANEL_SLIDE_SPEED = 0.08f;

    // ── AVATAR COMPANION ──────────────────────────────────────────────────────
    // [PYTHON HOOK: hardware_live.txt]
    // avatarGlowIntensity driven by CPU load — proves AI is listening.
    // avatarChatOpen toggled by C key — opens direct stdin conduit.
    public float[] avatarPos          = {1f, 0f, 0f};
    public float   avatarGlowIntensity = 0.2f;
    public boolean avatarChatOpen     = false;
    public String  avatarSpeechText   = "";

    // ── NARRATIVE ─────────────────────────────────────────────────────────────
    public String narrateText     = "";
    public long   narrateExpireMs = 0;
    public static final long NARRATE_DEFAULT_MS = 6000L;

    // ── ATMOSPHERE ────────────────────────────────────────────────────────────
    public float  weatherIntensity = 0f;
    public String weatherType      = "clear";
    public float  cpuLoad          = 0f;
    public float  gpuUtil          = 0f;
    public float  ramPercent       = 0f;

    // ── PYTHON PROTOCOL HOOKS ─────────────────────────────────────────────────
    public File    worldSeedFile       = null;
    public boolean worldSeedDirty      = false;
    public File    sphereTextureFile   = null;
    public boolean textureReloadNeeded = false;
    public File    voidImagesDir       = null;
    public boolean voidImagesDirty     = false;


    // ── CELESTIAL CLOCK + PHYSICS MODE ───────────────────────────────────────
    // Updated each logic tick by IceSandbox.updateLogic() via clock.update().
    // Governs day/night, orbital mechanics, atmosphere in non-standard modes.
    public CelestialClock clock       = new CelestialClock();
    public int physicsMode            = CelestialClock.PHYSICS_STANDARD;
    // ── EVENTS ────────────────────────────────────────────────────────────────
    public String       lastDiscoveryUID = null;
    public List<String> pendingEvents    = new ArrayList<>();
    public List<GameEntity> entities     = new ArrayList<>();

    // ── THREAD ECONOMY (from Aengloria resource system) ──────────────────────
    // rawNoise  → harvested by Signal Catcher mechanic (entropy input)
    // blueThreads → Logic resource (gained via Forensic Lens decrypt)
    // goldThreads → Love resource (gained via Forensic Lens decrypt)
    // artifacts   → crafted items (spent in Garden Weaver)
    public float rawNoise    = 0f;
    public int   blueThreads = 0;
    public int   goldThreads = 0;
    public int   artifacts   = 0;

    // ── MINIGAME NODES ────────────────────────────────────────────────────────
    // Populated by HtmlGameScanner at game load.
    // Each node is a location on the sphere surface where a mechanic is active.
    // Player walks to it, presses F to interact.
    // Mechanic runs via MiniGameEngine — no popup window, all in-viewport.
    public java.util.List<GameNode> gameNodes  = new ArrayList<>();
    public GameNode activeMiniGame             = null;  // currently running node

    // ─────────────────────────────────────────────────────────────────────────
    // INNER CLASSES
    // ─────────────────────────────────────────────────────────────────────────

    public static class SystemPlanet
    {
        public int    fnNode;
        public String celestialName;

        // Directories
        public File   fnDirectory;
        public File   encyclopediaFile;
        public File   dictionaryFile;
        public File   almanacFile;
        public File   religionFile;
        public File   personaFile;
        public File   journalFile;

        // Orbital properties
        public float  orbitRadius;
        public float  orbitAngleDeg;
        public float  orbitSpeedDeg;    // degrees per session tick
        public float  axialTiltDeg;
        public float  gravity;
        public float  atmosphereDensity;
        public String dominantBiome;

        // Body flags
        public boolean isSun          = false;
        public boolean isAsteroidBelt = false;  // fn7 — debris field, no core
        public boolean hasRings       = false;  // fn9 Saturn
        public boolean isUserNamed    = false;  // fn12

        // Bedrock core
        // Count = number of files in modular/game/
        // Same on all planets — consistent physical laws
        public int   bedrockCount = 0;
        public float coreRadius   = BASE_DIAMETER_FT / 2f;

        // Terrain spheres loaded from txt files
        public List<TerrainSphere> bedrockCore = new ArrayList<>();
        public List<TerrainSphere> terrain     = new ArrayList<>();

        // World calculation cache
        // Cached in comment block within source txt files on exit.
        // Validated by hash on next load for fast resume.
        public String  cacheHash  = "";
        public boolean cacheValid = false;

        // Live LOD counters (for debug overlay)
        public int activeCharSpheres  = 0;
        public int activeEntrySpheres = 0;
        public int activeFileSpheres  = 0;
    }

    public static class TerrainSphere
    {
        // Position and size in world space
        public float x, y, z;
        public float radius;

        // Material
        public int materialType;
        public int hardness;

        // Source — which file, which entry, which character
        public String sourceFile;
        public String sourceEntry;
        public int    sourceCharIndex;
        public char   sourceChar;

        // State
        public boolean modified = false;  // needs flush-back to txt file
        public boolean broken   = false;  // player mined this
        public boolean placed   = false;  // player placed this

        // LOD level at which this sphere is active
        // 0=planet aggregate, 1=file aggregate, 2=entry, 3=character
        public int lodLevel = 1;

        // SDF blend group — same group uses smooth minimum blending
        public int blendGroup = 0;

        public TerrainSphere(float x, float y, float z, float radius,
                             int materialType, int hardness,
                             String sourceFile, char sourceChar)
        {
            this.x            = x;
            this.y            = y;
            this.z            = z;
            this.radius       = radius;
            this.materialType = materialType;
            this.hardness     = hardness;
            this.sourceFile   = sourceFile;
            this.sourceChar   = sourceChar;
        }
    }

    public static class StarBody
    {
        public float  x, y, z;
        public float  brightness;   // 0-1, from file count in source folder
        public float  radius;       // apparent visual size
        public String sourcePath;
        public int    fileCount;

        public StarBody(float x, float y, float z,
                       float brightness, String sourcePath, int fileCount)
        {
            this.x          = x;
            this.y          = y;
            this.z          = z;
            this.brightness = brightness;
            this.sourcePath = sourcePath;
            this.fileCount  = fileCount;
            this.radius     = 0.5f + brightness * 2.0f;
        }
    }

    public static class GameEntity
    {
        public String type;
        public float  angleDeg;
        public float  latitude;
        public String label;

        public GameEntity(String type, float angleDeg,
                         float latitude, String label)
        {
            this.type     = type;
            this.angleDeg = angleDeg;
            this.latitude = latitude;
            this.label    = label;
        }
    }

    // ── GAME NODE ─────────────────────────────────────────────────────────────
    // A mechanic discovered by HtmlGameScanner from an HTML file in the
    // HTML folder. Placed at a deterministic position on the sphere surface.
    // Player walks to it and presses F to activate via MiniGameEngine.

    public static class GameNode
    {
        // Source
        public String htmlFile;      // filename (not full path)
        public String title;         // human-readable title parsed from HTML
        public String mechanic;      // SIGNAL / WEAVE / DECRYPT / SANCTUARY / CLOCK

        // Position on sphere surface
        public float  angleDeg;      // longitude
        public float  latDeg;        // latitude

        // State
        public boolean unlocked  = true;   // available to interact
        public boolean active    = false;  // player currently in range
        public String  statusMsg = "";     // brief status for sidebar display

        // Mechanic-specific state (used by MiniGameEngine)
        public float   progress  = 0f;    // 0-1 generic progress
        public int     streak    = 0;     // consecutive successes

        public GameNode(String htmlFile, String title, String mechanic,
                        float angleDeg, float latDeg)
        {
            this.htmlFile = htmlFile;
            this.title    = title;
            this.mechanic = mechanic;
            this.angleDeg = angleDeg;
            this.latDeg   = latDeg;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATIC UTILITIES
    // ─────────────────────────────────────────────────────────────────────────

    public static int charHardness(char c)
    {
        if (Character.isWhitespace(c))         return HARD_VOID;
        if (Character.isLowerCase(c))          return HARD_SOFT;
        if (Character.isUpperCase(c))          return HARD_MEDIUM;
        if (Character.isDigit(c))              return HARD_DENSE;
        if (".,;:!?-_'\"()".indexOf(c) >= 0)  return HARD_JOINT;
        return HARD_STRUCTURAL;
    }

    public static int fileToMaterial(String filename)
    {
        if (filename == null)                  return MAT_DIRT;
        String f = filename.toLowerCase();
        if (f.contains("dictionary"))          return MAT_DIRT;
        if (f.contains("persona"))             return MAT_SOIL;
        if (f.contains("journal"))             return MAT_SEDIMENT;
        if (f.contains("almanac"))             return MAT_STONE;
        if (f.contains("encyclopedia"))        return MAT_ORE;
        if (f.contains("religion"))            return MAT_CONVERTED_ORE;
        if (f.endsWith(".java"))               return MAT_BEDROCK;
        return MAT_DIRT;
    }

    public static String celestialName(int fnNode)
    {
        switch (fnNode)
        {
            case 1:  return "Sun";
            case 2:  return "Mercury";
            case 3:  return "Venus";
            case 4:  return "Earth";
            case 5:  return "Moon";
            case 6:  return "Mars";
            case 7:  return "Asteroid Belt";
            case 8:  return "Jupiter";
            case 9:  return "Saturn";
            case 10: return "Uranus";
            case 11: return "Neptune";
            case 12: return "Kuiper Anomaly";
            default: return "Unknown";
        }
    }

    public static float orbitalRadius(int fnNode)
    {
        switch (fnNode)
        {
            case 1:  return 0f;
            case 2:  return 12f;
            case 3:  return 20f;
            case 4:  return 28f;
            case 5:  return 32f;   // Moon near Earth
            case 6:  return 42f;
            case 7:  return 60f;
            case 8:  return 90f;
            case 9:  return 120f;
            case 10: return 150f;
            case 11: return 180f;
            case 12: return 220f;
            default: return 50f;
        }
    }

    public static float baseGravity(int fnNode)
    {
        switch (fnNode)
        {
            case 1:  return 2.80f;  // Sun
            case 2:  return 0.38f;  // Mercury
            case 3:  return 0.91f;  // Venus
            case 4:  return 1.00f;  // Earth — baseline
            case 5:  return 0.17f;  // Moon
            case 6:  return 0.38f;  // Mars
            case 7:  return 0.05f;  // Asteroid Belt
            case 8:  return 2.53f;  // Jupiter
            case 9:  return 1.07f;  // Saturn
            case 10: return 0.89f;  // Uranus
            case 11: return 1.14f;  // Neptune
            case 12: return 0.05f;  // Kuiper
            default: return 1.00f;
        }
    }

    public static float axialTilt(int fnNode)
    {
        switch (fnNode)
        {
            case 3:  return 177f;  // Venus — retrograde
            case 9:  return 27f;   // Saturn
            case 10: return 98f;   // Uranus — extreme tilt
            case 11: return 28f;   // Neptune
            default: return 23f;   // Earth-like default
        }
    }

    /**
     * Sphere diameter at a given scale tier and world scale multiplier.
     * When result falls below MIN_SPHERE_SIZE_FT, the caller should
     * double worldScaleMultiplier and reset scaleTier to 0.
     */
    public static float sphereDiameterAtTier(int tier, float scaleMultiplier)
    {
        float d = BASE_DIAMETER_FT * scaleMultiplier;
        for (int i = 0; i < tier; i++) d /= SUBDIVISIONS;
        return d;
    }

    public static boolean tierBreachesMinimum(int tier, float scaleMultiplier)
    {
        return sphereDiameterAtTier(tier + 1, scaleMultiplier) < MIN_SPHERE_SIZE_FT;
    }
}
