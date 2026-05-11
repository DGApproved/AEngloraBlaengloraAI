package modular.game;

/*
 * OptimizeRender.java
 *
 * Central optimization library for the Goddess Matrix game engine renderer.
 * All six systems live here. No instantiation required — pure static utility.
 *
 * SYSTEMS:
 *   FastMath   — sin/cos lookup tables (16-bit precision, ~20x faster than Math.*)
 *   Materials  — DOD parallel arrays for material properties, indexed by
 *                WorldState.MAT_* constants. Replaces per-pixel switch statements.
 *   SphereList — Active sphere culling. Horizon + FOV dot product test eliminates
 *                spheres below the horizon and outside the view cone before the
 *                inner ray march loop.
 *   Temporal   — Temporal reprojection. Renders a fraction of pixels per frame,
 *                fills the rest from the previous frame. Extends the existing
 *                half-resolution approach into a budget-aware grid system.
 *   Governor   — Hardware quality governor thread. Monitors actual FPS vs target,
 *                computes slope of the FPS/quality curve, adjusts qualityMultiplier
 *                continuously. SphereRenderer and TimingBridge both read this.
 *   PBR        — Cook-Torrance BRDF math (NDF, Geometry, Fresnel). Replaces the
 *                simple ambient+diffuse shader with physically-based result.
 *
 * REFERENCES (who uses what):
 *   SphereRenderer → FastMath (trig in pixel loop and planetWorldPos)
 *                  → Materials (color + roughness arrays replace switch)
 *                  → SphereList.cull() (before terrain sphere inner loop)
 *                  → Temporal.shouldRender() (pixel skip in render loop)
 *                  → PBR.shade() (replaces shade() method)
 *   TimingBridge   → Governor.getQualityMultiplier() (adjusts render budget)
 *   IceSandbox     → Governor.reportFPS() (called each frame end)
 *                  → Temporal.advance() (called each frame end)
 *
 * EXTRACTED FROM:
 *   MarbleRenderer.java (Gemini/Google) — FastMath, DOD material arrays,
 *   active sphere culling, temporal reprojection, hardware governor,
 *   Cook-Torrance BRDF equations.
 *
 * Contributors:
 *   Gemini (Google)        — MarbleRenderer source (FastMath, DOD layout,
 *                           temporal reprojection, hardware governor, PBR BRDF)
 *   Derek Jason Gilhousen — material system philosophy, world design,
 *                           render quality intent
 *   Claude (Anthropic)    — extraction, adaptation for Goddess Matrix
 *                           material IDs and sphere world architecture
 */

import java.util.List;
import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;

public final class OptimizeRender
{
    private OptimizeRender() {} // pure static utility

    // ═══════════════════════════════════════════════════════════════════════════
    // FAST MATH — sin/cos lookup tables
    // Replaces Math.sin / Math.cos throughout the render loop.
    // ~20x faster than Math.* for the trig-heavy SDF pixel loop.
    // 16-bit table → precision adequate for rendering; not for physics.
    // Use in: SphereRenderer pixel loop, planetWorldPos(), normal sampling.
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class FastMath
    {
        private static final int    BITS  = 16;
        private static final int    MASK  = ~(-1 << BITS);
        private static final int    COUNT = MASK + 1;
        private static final double TWO_PI      = Math.PI * 2.0;
        private static final double TO_INDEX    = COUNT / TWO_PI;
        private static final double[] SIN_TABLE = new double[COUNT];

        static
        {
            for (int i = 0; i < COUNT; i++)
                SIN_TABLE[i] = Math.sin((i + 0.5) / COUNT * TWO_PI);
            // Exact values at cardinal angles
            SIN_TABLE[0]                                             = 0.0;
            SIN_TABLE[(int)(Math.PI * 0.5  * TO_INDEX) & MASK]      = 1.0;
            SIN_TABLE[(int)(Math.PI        * TO_INDEX) & MASK]       = 0.0;
            SIN_TABLE[(int)(Math.PI * 1.5  * TO_INDEX) & MASK]       = -1.0;
        }

        public static double sin(double rad)
        {
            return SIN_TABLE[(int)(rad * TO_INDEX) & MASK];
        }

        public static double cos(double rad)
        {
            return SIN_TABLE[(int)((rad + Math.PI * 0.5) * TO_INDEX) & MASK];
        }

        // Float overloads for SphereRenderer which uses float[] vectors
        public static float sinf(float rad)
        {
            return (float) SIN_TABLE[(int)(rad * TO_INDEX) & MASK];
        }

        public static float cosf(float rad)
        {
            return (float) SIN_TABLE[(int)((rad + Math.PI * 0.5) * TO_INDEX) & MASK];
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MATERIALS — Data-Oriented parallel arrays
    // Indexed by WorldState.MAT_* constants.
    // Replaces per-pixel switch statements with direct array lookups.
    // Use in: SphereRenderer.materialColor(), SphereRenderer.shade().
    //
    // Material IDs must match WorldState.MAT_* values exactly.
    // Array size = 16 (covers all current + reserved slots).
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Materials
    {
        public  static final int SLOTS = 16;

        // Base color channels (0-255)
        public static final int[] COLOR_R = new int[SLOTS];
        public static final int[] COLOR_G = new int[SLOTS];
        public static final int[] COLOR_B = new int[SLOTS];

        // PBR properties (0.0 - 1.0)
        public static final double[] ROUGHNESS = new double[SLOTS];
        public static final double[] METALLIC  = new double[SLOTS];
        public static final double[] BASE_F0   = new double[SLOTS];
        public static final int[]    OPAQUE    = new int[SLOTS]; // 1=solid, 0=transparent

        static
        {
            // Defaults — opaque stone-like
            for (int i = 0; i < SLOTS; i++)
            {
                COLOR_R[i] = 80;  COLOR_G[i] = 80;  COLOR_B[i] = 80;
                ROUGHNESS[i] = 0.80; METALLIC[i] = 0.0; BASE_F0[i] = 0.04;
                OPAQUE[i] = 1;
            }

            // ── WorldState.MAT_VOID ────────────────────────────────────────────
            set(WorldState.MAT_VOID,
                0,   0,   0,   0.0,  0.0, 0.04, 0); // fully transparent

            // ── WorldState.MAT_DIRT ────────────────────────────────────────────
            // Default planet surface. Earthy brown, rough, non-metallic.
            set(WorldState.MAT_DIRT,
                139, 90,  43,  0.95, 0.0, 0.04, 1);

            // ── WorldState.MAT_SOIL ────────────────────────────────────────────
            // Warmer tan. Slightly less rough than dirt.
            set(WorldState.MAT_SOIL,
                160, 120, 70,  0.85, 0.0, 0.04, 1);

            // ── WorldState.MAT_SEDIMENT ────────────────────────────────────────
            // Grey-brown layered. Medium rough.
            set(WorldState.MAT_SEDIMENT,
                110, 100, 85,  0.80, 0.0, 0.04, 1);

            // ── WorldState.MAT_STONE ───────────────────────────────────────────
            // Cool grey. .sh files — rough surface.
            set(WorldState.MAT_STONE,
                130, 135, 140, 0.80, 0.0, 0.04, 1);

            // ── WorldState.MAT_ORE ─────────────────────────────────────────────
            // Deep blue-purple. Encyclopedia-validated knowledge. Slight sheen.
            set(WorldState.MAT_ORE,
                80,  60,  180, 0.45, 0.1, 0.08, 1);

            // ── WorldState.MAT_CONVERTED_ORE ──────────────────────────────────
            // Amber-gold. Religion theories transmuted into knowledge.
            // Slightly metallic — the conversion process leaves a lustre.
            set(WorldState.MAT_CONVERTED_ORE,
                200, 155, 40,  0.35, 0.3, 0.15, 1);

            // ── WorldState.MAT_CRAFTED ─────────────────────────────────────────
            // Bright cyan. Composite crafted artifacts. Low roughness — polished.
            set(WorldState.MAT_CRAFTED,
                40,  220, 210, 0.20, 0.0, 0.10, 1);

            // ── WorldState.MAT_BEDROCK ─────────────────────────────────────────
            // Near-black with faint grid. .java files. Hardest material.
            // Slight metallic feel — the skeleton of the system.
            set(WorldState.MAT_BEDROCK,
                30,  25,  35,  0.70, 0.2, 0.06, 1);
        }

        private static void set(int id, int r, int g, int b,
                                 double rough, double metal, double f0, int opaque)
        {
            if (id < 0 || id >= SLOTS) return;
            COLOR_R[id] = r; COLOR_G[id] = g; COLOR_B[id] = b;
            ROUGHNESS[id] = rough; METALLIC[id] = metal;
            BASE_F0[id]   = f0;   OPAQUE[id]   = opaque;
        }

        /** Returns packed RGB int for the given material ID. */
        public static int packedRGB(int matID)
        {
            int id = (matID >= 0 && matID < SLOTS) ? matID : WorldState.MAT_VOID;
            return (COLOR_R[id] << 16) | (COLOR_G[id] << 8) | COLOR_B[id];
        }

        /** Returns color as float[3] normalised 0-1 for PBR shading. */
        public static float[] normColor(int matID)
        {
            int id = (matID >= 0 && matID < SLOTS) ? matID : WorldState.MAT_VOID;
            return new float[]{
                COLOR_R[id] / 255f,
                COLOR_G[id] / 255f,
                COLOR_B[id] / 255f
            };
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SPHERE LIST — active sphere culling
    // Filters terrain spheres to those visible from the current camera.
    // Eliminates spheres below the player's horizon and outside the FOV cone.
    // Returns indices of active spheres into the terrain list.
    //
    // Use in: SphereRenderer.sceneSDF() — call cull() once before the pixel
    // loop, then only test activeIndices[0..activeCount-1] per ray.
    //
    // Two-stage test (from MarbleRenderer):
    //   Stage 1: horizon dot product — eliminates back-hemisphere
    //   Stage 2: FOV cone dot product — eliminates off-screen spheres
    //            (shadow casters get an extra sun-volume test)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class SphereList
    {
        private static final int MAX_ACTIVE = 2048;

        // Reusable output arrays — avoids allocation each frame
        private static final int[] INDICES = new int[MAX_ACTIVE];
        private static volatile int ACTIVE_COUNT = 0;

        /**
         * Culls terrain spheres to those visible from the current camera.
         * @param terrain     planet's terrain sphere list
         * @param planetCX/Y/Z planet center in world space
         * @param camPos      camera position [3]
         * @param camFwd      camera forward direction [3] (normalised)
         * @param sunPos      sun position [3] (for shadow caster inclusion)
         * @return            count of active spheres (indices in getIndices())
         */
        public static int cull(List<WorldState.TerrainSphere> terrain,
                                float planetCX, float planetCY, float planetCZ,
                                float[] camPos, float[] camFwd, float[] sunPos)
        {
            if (terrain == null) { ACTIVE_COUNT = 0; return 0; }

            // Camera-to-planet-center vector (normalised) — defines the horizon
            float cpx = camPos[0] - planetCX;
            float cpy = camPos[1] - planetCY;
            float cpz = camPos[2] - planetCZ;
            float cpLen = (float) Math.sqrt(cpx*cpx + cpy*cpy + cpz*cpz);
            if (cpLen < 0.001f) { ACTIVE_COUNT = 0; return 0; }
            cpx /= cpLen; cpy /= cpLen; cpz /= cpLen;

            // Sun direction from camera (normalised) — for shadow volume test
            float sdx = sunPos[0] - camPos[0];
            float sdy = sunPos[1] - camPos[1];
            float sdz = sunPos[2] - camPos[2];
            float sdLen = (float) Math.sqrt(sdx*sdx + sdy*sdy + sdz*sdz);
            if (sdLen > 0.001f) { sdx /= sdLen; sdy /= sdLen; sdz /= sdLen; }

            int count = 0;
            int size  = Math.min(terrain.size(), MAX_ACTIVE * 2);

            for (int i = 0; i < size && count < MAX_ACTIVE; i++)
            {
                WorldState.TerrainSphere s = terrain.get(i);

                // Stage 1: horizon test — is sphere on camera's hemisphere?
                float dvx = s.x - planetCX;
                float dvy = s.y - planetCY;
                float dvz = s.z - planetCZ;
                float dvLen = (float) Math.sqrt(dvx*dvx + dvy*dvy + dvz*dvz);
                if (dvLen < 0.001f) continue;
                float dotHorizon = cpx*(dvx/dvLen) + cpy*(dvy/dvLen) + cpz*(dvz/dvLen);
                if (dotHorizon < -0.25f) continue; // below horizon

                // Stage 2: FOV cone test
                float dtx = s.x - camPos[0];
                float dty = s.y - camPos[1];
                float dtz = s.z - camPos[2];
                float dtLen = (float) Math.sqrt(dtx*dtx + dty*dty + dtz*dtz);
                if (dtLen < 0.001f) { INDICES[count++] = i; continue; }
                float dotFOV = camFwd[0]*(dtx/dtLen)
                             + camFwd[1]*(dty/dtLen)
                             + camFwd[2]*(dtz/dtLen);
                float dotSun = sdx*(dtx/dtLen) + sdy*(dty/dtLen) + sdz*(dtz/dtLen);

                // Include if in FOV cone (0.3 = ~105° total FOV) or in sun volume
                if (dotFOV > -0.3f || dotSun > 0.0f)
                    INDICES[count++] = i;
            }

            ACTIVE_COUNT = count;
            return count;
        }

        /** Returns the backing index array. Valid up to getCount() entries. */
        public static int[] getIndices() { return INDICES; }
        public static int   getCount()   { return ACTIVE_COUNT; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TEMPORAL — frame-by-frame pixel budget
    // Extends the existing half-resolution approach into a full temporal grid.
    // When quality < 1.0, renders only 1/N² of pixels per frame.
    // Remaining pixels are carried forward from the previous rendered frame.
    //
    // Use in: SphereRenderer pixel loop.
    //   Call shouldRender(col, row) before computing each pixel.
    //   Call advance() once per rendered frame from IceSandbox.
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Temporal
    {
        private static volatile int frameCounter  = 0;
        private static volatile int gridSize      = 1; // 1 = no temporal split

        /** Called by IceSandbox once per rendered frame. */
        public static void advance()
        {
            int phases = gridSize * gridSize;
            frameCounter = (frameCounter + 1) % Math.max(1, phases * 4);
        }

        /** Called by Governor when quality multiplier changes. */
        static void setGridSize(double qualityMultiplier)
        {
            if (qualityMultiplier >= 1.0)
                gridSize = 1;
            else
                gridSize = (int) Math.max(2,
                           Math.sqrt(1.0 / Math.max(0.0625, qualityMultiplier)));
        }

        /**
         * Returns true if this pixel should be computed this frame.
         * When gridSize = 1 (full quality), always returns true.
         */
        public static boolean shouldRender(int col, int row)
        {
            int gs = gridSize;
            if (gs <= 1) return true;
            int phaseX = frameCounter % gs;
            int phaseY = (frameCounter / gs) % gs;
            return (col % gs == phaseX) && (row % gs == phaseY);
        }

        /** Fraction of pixels computed this frame (for display). */
        public static double activePixelPercent()
        {
            int gs = gridSize;
            return gs <= 1 ? 100.0 : 100.0 / (gs * gs);
        }

        public static int getGridSize() { return gridSize; }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GOVERNOR — hardware quality governor
    // Background thread. Monitors actual vs target FPS.
    // Computes slope of FPS/quality curve from history, predicts correction.
    // Adjusts qualityMultiplier continuously within safe bounds.
    //
    // Use in: TimingBridge.getDisplayRate() reads getQualityMultiplier().
    //         SphereRenderer reads getSoftShadowRays() and getGridSize().
    //         IceSandbox calls reportFPS() each frame and advance() each frame.
    //
    // Start with: Governor.start(targetFPS)
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class Governor
    {
        private static volatile double qualityMultiplier = 1.0;
        private static volatile double targetFPS         = 30.0;
        private static volatile double actualFPS         = 30.0;
        private static volatile double cpuLoad           = 0.0;
        private static volatile boolean running          = false;

        private static final int    HIST_MAX    = 200;
        private static final double[] histMult  = new double[HIST_MAX];
        private static final double[] histFPS   = new double[HIST_MAX];
        private static int histCount = 0;

        /** Call once from TimingBridge or IceSandbox on startup. */
        public static synchronized void start(double target)
        {
            if (running) return;
            running   = true;
            targetFPS = target;

            Thread t = new Thread(Governor::governLoop, "OptimizeRender-Governor");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }

        /** IceSandbox calls this at the end of each rendered frame. */
        public static void reportFPS(double fps)
        {
            actualFPS = fps;
        }

        public static double getQualityMultiplier() { return qualityMultiplier; }
        public static double getCPULoad()           { return cpuLoad; }
        public static double getActualFPS()         { return actualFPS; }
        public static double getTargetFPS()         { return targetFPS; }
        public static void   setTargetFPS(double t) { targetFPS = t; }

        /**
         * Returns how many soft shadow rays to use this frame.
         * When camera is moving: 1 (no soft shadows — prioritise frame rate).
         * When static: scales with quality multiplier.
         */
        public static int getSoftShadowRays(boolean cameraMoving)
        {
            if (cameraMoving) return 1;
            return (int) Math.max(1, qualityMultiplier);
        }

        private static void governLoop()
        {
            OperatingSystemMXBean os = null;
            try
            {
                os = (OperatingSystemMXBean)
                     ManagementFactory.getOperatingSystemMXBean();
            }
            catch (Exception ignored) {}

            while (running)
            {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

                if (os != null)
                {
                    double load = os.getProcessCpuLoad();
                    if (load >= 0) cpuLoad = load;
                }

                double currentMult = qualityMultiplier;
                double currentFPS  = actualFPS;

                // Shift history
                int gs    = Temporal.getGridSize();
                int phases = gs * gs;
                int window = Math.min(HIST_MAX, 3 * phases);

                for (int i = window - 1; i > 0; i--)
                {
                    histMult[i] = histMult[i - 1];
                    histFPS[i]  = histFPS[i - 1];
                }
                histMult[0] = currentMult;
                histFPS[0]  = currentFPS;
                histCount   = Math.min(window, histCount + 1);

                // Compute slope: dFPS / dMult (from MarbleRenderer governor)
                double slope = -0.1; // safe default
                if (histCount >= 2)
                {
                    int oldest   = histCount - 1;
                    double dMult = histMult[0] - histMult[oldest];
                    double dFPS  = histFPS[0]  - histFPS[oldest];
                    if (Math.abs(dMult) > 0.01 && Math.abs(dFPS) > 0.01)
                        slope = dFPS / dMult;
                }
                if (slope > -0.1) slope = -0.1; // prevent division instability

                // Correct multiplier toward target FPS
                double fpsError   = currentFPS - targetFPS;
                double correction = -fpsError / slope;
                qualityMultiplier = Math.max(0.0625,
                                    Math.min(50.0,
                                    qualityMultiplier + correction * 0.5));

                // Update temporal grid
                Temporal.setGridSize(qualityMultiplier);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PBR — Cook-Torrance BRDF
    // Physically-based lighting model replacing the simple ambient+diffuse shader.
    // Ported from MarbleRenderer.shadeFragment().
    //
    // Functions:
    //   fresnel(cosTheta, f0)      — Schlick approximation
    //   ndf(NdotH, roughness)      — GGX Normal Distribution
    //   geometry(NdotV, NdotL, r)  — Smith-Schlick Geometry
    //   shade(matID, n, viewDir, lightDir, lightRadiance) — full fragment
    //
    // Use in: SphereRenderer.shade() — replace the simple ndl*ambient calc.
    // ═══════════════════════════════════════════════════════════════════════════

    public static final class PBR
    {
        /** Schlick Fresnel approximation. */
        public static double fresnel(double cosTheta, double f0)
        {
            return f0 + (1.0 - f0) * Math.pow(1.0 - cosTheta, 5.0);
        }

        /** GGX Normal Distribution Function. */
        public static double ndf(double NdotH, double roughness)
        {
            double alpha   = roughness * roughness;
            double alphaSq = alpha * alpha;
            double denom   = NdotH * NdotH * (alphaSq - 1.0) + 1.0;
            return alphaSq / (Math.PI * denom * denom);
        }

        /** Smith-Schlick Geometry term. */
        public static double geometry(double NdotV, double NdotL,
                                       double roughness)
        {
            double k  = Math.pow(roughness + 1.0, 2.0) / 8.0;
            double g1 = NdotV / (NdotV * (1.0 - k) + k);
            double g2 = NdotL / (NdotL * (1.0 - k) + k);
            return g1 * g2;
        }

        /**
         * Full PBR fragment shading.
         *
         * @param matID       WorldState.MAT_* constant
         * @param nx ny nz    surface normal (normalised)
         * @param vx vy vz    view direction (toward camera, normalised)
         * @param lx ly lz    light direction (toward light, normalised)
         * @param radiance    light radiance (distance-attenuated intensity)
         * @param shadow      0.0 = fully shadowed, 1.0 = fully lit
         * @return packed RGB int
         */
        public static int shade(int matID,
                                 float nx, float ny, float nz,
                                 float vx, float vy, float vz,
                                 float lx, float ly, float lz,
                                 double radiance, double shadow)
        {
            int id = (matID >= 0 && matID < Materials.SLOTS) ? matID : WorldState.MAT_VOID;

            double roughness = Materials.ROUGHNESS[id];
            double metallic  = Materials.METALLIC[id];
            double f0        = Materials.BASE_F0[id];
            double albR      = Materials.COLOR_R[id] / 255.0;
            double albG      = Materials.COLOR_G[id] / 255.0;
            double albB      = Materials.COLOR_B[id] / 255.0;

            // Metallic F0 tint
            double f0R = f0 * (1.0 - metallic) + albR * metallic;
            double f0G = f0 * (1.0 - metallic) + albG * metallic;
            double f0B = f0 * (1.0 - metallic) + albB * metallic;

            double NdotV = Math.max(0.0001, nx*vx + ny*vy + nz*vz);
            double NdotL = Math.max(0.0001, nx*lx + ny*ly + nz*lz);

            // Half vector
            double hx = lx + vx, hy = ly + vy, hz = lz + vz;
            double hLen = Math.sqrt(hx*hx + hy*hy + hz*hz);
            if (hLen < 0.0001) hLen = 1.0;
            hx /= hLen; hy /= hLen; hz /= hLen;

            double NdotH = Math.max(0.0001, nx*hx + ny*hy + nz*hz);
            double VdotH = Math.max(0.0001, vx*hx + vy*hy + vz*hz);

            double N   = ndf(NdotH, roughness);
            double G   = geometry(NdotV, NdotL, roughness);
            double F_r = fresnel(VdotH, f0R);
            double F_g = fresnel(VdotH, f0G);
            double F_b = fresnel(VdotH, f0B);

            double denom = Math.max(0.001, 4.0 * NdotV * NdotL);
            double specR = N * G * F_r / denom;
            double specG = N * G * F_g / denom;
            double specB = N * G * F_b / denom;

            double kDr = (1.0 - F_r) * (1.0 - metallic);
            double kDg = (1.0 - F_g) * (1.0 - metallic);
            double kDb = (1.0 - F_b) * (1.0 - metallic);

            double litR = (kDr * albR / Math.PI + specR) * radiance * NdotL * shadow;
            double litG = (kDg * albG / Math.PI + specG) * radiance * NdotL * shadow;
            double litB = (kDb * albB / Math.PI + specB) * radiance * NdotL * shadow;

            // Ambient
            double outR = albR * 0.05 + litR;
            double outG = albG * 0.05 + litG;
            double outB = albB * 0.05 + litB;

            // Reinhard tonemapping + gamma correction
            outR = Math.pow(outR / (outR + 1.0), 1.0 / 2.2);
            outG = Math.pow(outG / (outG + 1.0), 1.0 / 2.2);
            outB = Math.pow(outB / (outB + 1.0), 1.0 / 2.2);

            int ir = (int)(Math.min(1.0, outR) * 255.0);
            int ig = (int)(Math.min(1.0, outG) * 255.0);
            int ib = (int)(Math.min(1.0, outB) * 255.0);
            return (ir << 16) | (ig << 8) | ib;
        }

        /**
         * Lightweight version for distant/LOD surfaces.
         * Skips specular, uses simple Lambert + ambient.
         * Use when hitDist > lodEntryThreshold.
         */
        public static int shadeLambert(int matID,
                                        float nx, float ny, float nz,
                                        float lx, float ly, float lz,
                                        double shadow)
        {
            int id  = (matID >= 0 && matID < Materials.SLOTS) ? matID : 0;
            float ndl  = Math.max(0f, nx*lx + ny*ly + nz*lz);
            float lit  = 0.15f + 0.85f * ndl * (float)shadow;
            int r = (int)(Materials.COLOR_R[id] * lit);
            int g = (int)(Materials.COLOR_G[id] * lit);
            int b = (int)(Materials.COLOR_B[id] * lit);
            return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
        }
    }
}
