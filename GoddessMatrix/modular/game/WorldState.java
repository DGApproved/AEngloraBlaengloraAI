package modular.game;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/*
 * WorldState.java
 *
 * Merged central state container for the Goddess Matrix game engine.
 *
 * MERGE GOALS:
 *   - Preserve the earlier celestial/planet/minigame state model.
 *   - Add newer Astrid / AGAB / Geordi-glasses camera fields.
 *   - Keep compatibility fields used by IceSandbox, PlayerPhysics,
 *     SphereRenderer, SidePanels, HtmlGameScanner, MiniGameEngine,
 *     GameProtocol, PlanetScaler, and GameHiberfile.
 *
 * Contributors:
 *   Derek Jason Gilhousen — project/world design, Astrid/AGAB concept,
 *                           Geordi-glasses camera metaphor, data-as-world model
 *   Claude (Anthropic)    — prior Java implementation passes
 *   ChatGPT (OpenAI)      — four-version merge stabilization pass
 */

public class WorldState
{
    public boolean launchedFromMatrix = false;

    public int activeNode      = 4;
    public int activePlanet    = FN_EARTH;
    public int planetsInSystem = 12;
    public boolean needsTerrainGeneration = true;

    public boolean overlayVisible = false;
    public boolean panelsVisible  = false;
    public boolean chatOpen       = false;
    public boolean avatarChatOpen = false;
    public String  avatarSpeechText = "";

    public float panelSlideProgress = 0f;
    public float panelSlideTarget   = 0f;
    public static final float PANEL_SLIDE_SPEED = 0.08f;

    public int    timingMode = TimingBridge.MODE_GAMEPLAY;
    public double simulationStepSeconds = 0.016;
    public double fps = 60.0;
    public float  cpuLoad = 0f;
    public float  gpuUtil = 0f;
    public float  ramPercent = 0f;

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

    public static final int MAT_VOID           = 0;
    public static final int MAT_BEDROCK        = 1;
    public static final int MAT_AGGREGATE_G    = 2;
    public static final int MAT_AGGREGATE_T    = 3;
    public static final int MAT_AGGREGATE_D    = 4;
    public static final int MAT_CRAFTED_BLUE   = 5;
    public static final int MAT_CRAFTED_GOLD   = 6;
    public static final int MAT_AVATAR_GLOW    = 7;

    public static final int MAT_DIRT          = MAT_AGGREGATE_D;
    public static final int MAT_SOIL          = MAT_AGGREGATE_G;
    public static final int MAT_SEDIMENT      = MAT_AGGREGATE_T;
    public static final int MAT_STONE         = MAT_AGGREGATE_T;
    public static final int MAT_ORE           = MAT_AGGREGATE_G;
    public static final int MAT_CONVERTED_ORE = MAT_CRAFTED_GOLD;
    public static final int MAT_CRAFTED       = MAT_CRAFTED_BLUE;

    public static final int HARD_VOID       = 0;
    public static final int HARD_SOFT       = 1;
    public static final int HARD_MEDIUM     = 2;
    public static final int HARD_DENSE      = 3;
    public static final int HARD_JOINT      = 4;
    public static final int HARD_STRUCTURAL = 5;

    public static final float BASE_DIAMETER_FT   = 3.0f;
    public static final float MIN_SPHERE_SIZE_FT = 0.5f;
    public static final int   SUBDIVISIONS       = 4;

    public int   scaleTier = 0;
    public float worldScaleMultiplier = 1.0f;
    public float activeSphereRadius = BASE_DIAMETER_FT / 2f;

    public long  totalSurfaceSpheresNeeded = 0;
    public float coreRadius = 12.0f;
    public float systemPlanetAggregationMultiplier = 1.0f;

    public float lodCharacterThreshold = 6.0f;
    public float lodEntryThreshold     = 30.0f;
    public float lodFileThreshold      = 150.0f;

    public SystemPlanet[] planets = new SystemPlanet[13];
    public float[] sunPosition = {0f, 0f, 0f};
    public float[] orbitAngles = new float[13];
    public List<StarBody> stars = new ArrayList<>();

    public float[] camPos    = {0f, 5f, -10f};
    public float[] camTarget = {0f, 0f,   0f};
    public float   camFOV    = 60.0f;
    public boolean isFirstPerson = true;
    public float cameraYaw   = 0f;
    public float cameraPitch = 15f;

    public float playerLatDeg       = 0.0f;
    public float playerAngleDeg     = 0.0f;
    public float playerAltitudeFeet = 0.0f;
    public float[] playerPos = {0f, 0f, 0f};
    // Body facing yaw — direction the avatar's torso is physically facing.
    // Updated by PlayerPhysics when WASD moves. Separate from playerAngleDeg
    // (sphere position) and from cameraYaw (where eyes point).
    // AstridHeadYaw = cameraYaw - bodyFacingYaw (for avatar body/head animation).
    public float bodyFacingYaw = 0f;

    public float AstridHeadYaw   = 0f;
    public float AstridHeadPitch = 0f;
    public float headYaw         = 0f;
    public float headPitch       = 0f;
    // Eyes: arrow-key-driven, ±30° relative to head.
    // Fine focus — read a book, check a shelf — without moving the head.
    public float eyeYaw   = 0f;
    public float eyePitch = 0f;

    public float rightArmYaw   = 0f;
    public float rightArmPitch = 0f;
    public float leftArmYaw    = 0f;
    public float leftArmPitch  = 0f;

    public float   playerHeightFeet = 6.0f;
    public boolean isJumping = false;
    public float   verticalVelocity = 0f;

    public float avatarLatDeg   = 0.0f;
    public float avatarAngleDeg = 15.0f;
    public float[] avatarPos = {1f, 0f, 0f};

    public float AGABHeadYaw = 0f;
    public float AGABHeadPitch = 0f;
    public float AGABRightArmYaw = 0f;
    public float AGABRightArmPitch = 0f;
    public float AGABLeftArmYaw = 0f;
    public float AGABLeftArmPitch = 0f;

    public float avatarHeightFeet = 6.0f;
    public float avatarGlowIntensity = 0.1f;
    public String activeAGABTextLoad = "";

    public String narrateText = "";
    public long narrateExpireMs = 0;
    public static final long NARRATE_DEFAULT_MS = 6000L;

    public float  weatherIntensity = 0f;
    public String weatherType = "clear";

    public File worldSeedFile = null;
    public boolean worldSeedDirty = false;
    public File sphereTextureFile = null;
    public boolean textureReloadNeeded = false;
    public File voidImagesDir = null;
    public boolean voidImagesDirty = false;

    public CelestialClock clock = new CelestialClock();
    public int physicsMode = CelestialClock.PHYSICS_STANDARD;

    public String lastDiscoveryUID = null;
    public List<String> pendingEvents = new ArrayList<>();
    public List<GameEntity> entities = new ArrayList<>();

    public float rawNoise = 0f;
    public int blueThreads = 0;
    public int goldThreads = 0;
    public int artifacts = 0;

    // ── IN-GAME CHAT HISTORY ──────────────────────────────────────────────────
    public static class ChatMessage {
        public final String speaker; // "YOU" or "AI"
        public final String text;
        public final long timestamp;
        public ChatMessage(String speaker, String text)
        { this.speaker = speaker; this.text = text; this.timestamp = System.currentTimeMillis(); }
    }
    public final java.util.ArrayDeque<ChatMessage> chatMessages = new java.util.ArrayDeque<>();
    public static final int MAX_CHAT_MESSAGES = 12;

    public List<GameNode> gameNodes = new ArrayList<>();
    public GameNode activeMiniGame = null;

    public static class SystemPlanet
    {
        public int fnNode;
        public String celestialName;
        public File fnDirectory, encyclopediaFile, dictionaryFile, almanacFile, religionFile, personaFile, journalFile;
        public float orbitRadius, orbitAngleDeg, orbitSpeedDeg, axialTiltDeg, gravity, atmosphereDensity;
        public String dominantBiome;
        public boolean isSun = false, isAsteroidBelt = false, hasRings = false, isUserNamed = false;
        public int bedrockCount = 0;
        public float coreRadius = BASE_DIAMETER_FT / 2f;
        public List<TerrainSphere> bedrockCore = new ArrayList<>();
        public List<TerrainSphere> terrain = new ArrayList<>();
        public String cacheHash = "";
        public boolean cacheValid = false;
        public int activeCharSpheres = 0, activeEntrySpheres = 0, activeFileSpheres = 0;
    }

    public static class TerrainSphere
    {
        public float x, y, z, radius;
        public int materialType, hardness;
        public String sourceFile, sourceEntry;
        public int sourceCharIndex;
        public char sourceChar;
        public boolean modified = false, broken = false, placed = false;
        public int lodLevel = 1, blendGroup = 0;

        public TerrainSphere(float x, float y, float z, float radius, int materialType, int hardness, String sourceFile, char sourceChar)
        {
            this.x = x; this.y = y; this.z = z; this.radius = radius;
            this.materialType = materialType; this.hardness = hardness;
            this.sourceFile = sourceFile; this.sourceChar = sourceChar;
        }
    }

    public static class StarBody
    {
        public float x, y, z, brightness, radius;
        public String sourcePath;
        public int fileCount;
        public StarBody(float x, float y, float z, float brightness, String sourcePath, int fileCount)
        {
            this.x = x; this.y = y; this.z = z; this.brightness = brightness;
            this.sourcePath = sourcePath; this.fileCount = fileCount;
            this.radius = 0.5f + brightness * 2.0f;
        }
    }

    public static class GameEntity
    {
        public String type, label;
        public float angleDeg, latitude;
        public GameEntity(String type, float angleDeg, float latitude, String label)
        { this.type = type; this.angleDeg = angleDeg; this.latitude = latitude; this.label = label; }
    }

    public static class GameNode
    {
        public String htmlFile, title, mechanic;
        public float angleDeg, latDeg;
        public boolean unlocked = true, active = false;
        public String statusMsg = "";
        public float progress = 0f;
        public int streak = 0;
        public GameNode(String htmlFile, String title, String mechanic, float angleDeg, float latDeg)
        { this.htmlFile = htmlFile; this.title = title; this.mechanic = mechanic; this.angleDeg = angleDeg; this.latDeg = latDeg; }
    }

    public static float scaledSphereDiameter(int tier, float worldScaleMultiplier)
    {
        float diameter = BASE_DIAMETER_FT * worldScaleMultiplier;
        for (int i = 0; i < tier; i++) diameter /= SUBDIVISIONS;
        return diameter;
    }

    public static boolean miniTierSphereSizes(int tier, float worldScaleMultiplier)
    {
        return scaledSphereDiameter(tier, worldScaleMultiplier) < MIN_SPHERE_SIZE_FT;
    }

    public static int charHardness(char c)
    {
        if (Character.isWhitespace(c)) return HARD_VOID;
        if (Character.isLowerCase(c)) return HARD_SOFT;
        if (Character.isUpperCase(c)) return HARD_MEDIUM;
        if (Character.isDigit(c)) return HARD_DENSE;
        if (".,;:!?-_'\"()".indexOf(c) >= 0) return HARD_JOINT;
        return HARD_STRUCTURAL;
    }

    public static int fileToMaterial(String filename)
    {
        if (filename == null) return MAT_DIRT;
        String f = filename.toLowerCase();
        if (f.contains("dictionary")) return MAT_DIRT;
        if (f.contains("persona")) return MAT_SOIL;
        if (f.contains("journal")) return MAT_SEDIMENT;
        if (f.contains("almanac")) return MAT_STONE;
        if (f.contains("encyclopedia")) return MAT_ORE;
        if (f.contains("religion")) return MAT_CONVERTED_ORE;
        if (f.endsWith(".java")) return MAT_BEDROCK;
        return MAT_DIRT;
    }

    public static String celestialName(int fnNode)
    {
        switch (fnNode)
        {
            case 1: return "Sun"; case 2: return "Mercury"; case 3: return "Venus"; case 4: return "Earth";
            case 5: return "Moon"; case 6: return "Mars"; case 7: return "Asteroid Belt"; case 8: return "Jupiter";
            case 9: return "Saturn"; case 10: return "Uranus"; case 11: return "Neptune"; case 12: return "Kuiper Anomaly";
            default: return "Unknown";
        }
    }

    public static float orbitalRadius(int fnNode)
    {
        switch (fnNode)
        {
            case 1: return 0f; case 2: return 12f; case 3: return 20f; case 4: return 28f; case 5: return 32f; case 6: return 42f;
            case 7: return 60f; case 8: return 90f; case 9: return 120f; case 10: return 150f; case 11: return 180f; case 12: return 220f;
            default: return 28f;
        }
    }

    public static float axialTilt(int fnNode)
    {
        switch (fnNode)
        {
            case 3: return 177.4f; case 4: return 23.4f; case 5: return 6.7f; case 6: return 25.2f;
            case 8: return 3.1f; case 9: return 26.7f; case 10: return 97.8f; case 11: return 28.3f;
            default: return 0f;
        }
    }

    public static float baseGravity(int fnNode)
    {
        switch (fnNode)
        {
            case FN_SUN: return 27.9f; case FN_MERCURY: return 0.38f; case FN_VENUS: return 0.90f; case FN_EARTH: return 1.00f;
            case FN_MOON: return 0.17f; case FN_MARS: return 0.38f; case FN_JUPITER: return 2.53f; case FN_SATURN: return 1.07f;
            case FN_URANUS: return 0.89f; case FN_NEPTUNE: return 1.14f; default: return 0.50f;
        }
    }
}
