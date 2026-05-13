package modular.game;

/*
 * SphereRenderer.java
 *
 * Software SDF ray marcher for the Goddess Matrix game engine.
 * Renders directly to a DataBufferInt — bypasses Java2D pipeline entirely.
 *
 * RENDERING MODEL:
 *   World is built on sphere primitives.
 *   Scene SDF = smooth minimum over all active TerrainSphere objects.
 *   Empty space costs near-zero — step sizes grow large when distant
 *   from all surfaces, so interplanetary void is cheap to traverse.
 *
 * LOD SYSTEM:
 *   Character-level spheres only evaluated within lodCharacterThreshold.
 *   Entry-level aggregates between that and lodEntryThreshold.
 *   File-level aggregates beyond that.
 *   Planet-level aggregate at maximum distance.
 *   Thresholds tunable per hardware in WorldState.
 *
 * MATERIAL COLORS:
 *   MAT_DIRT          → earthy brown
 *   MAT_SOIL          → warm tan
 *   MAT_SEDIMENT      → grey-brown layered
 *   MAT_STONE         → cool grey
 *   MAT_ORE           → deep blue-purple (encyclopedia validated)
 *   MAT_CONVERTED_ORE → amber-gold (religion theories)
 *   MAT_CRAFTED       → bright cyan (composite)
 *   MAT_BEDROCK       → near-black with faint grid
 *   MAT_VOID          → transparent / not rendered
 *
 * AVATAR:
 *   Glowing stick figure NPC at avatarPos.
 *   Glow intensity driven by WorldState.avatarGlowIntensity (CPU load).
 *   Speech text rendered above avatar head when avatarSpeechText set.
 *
 * Note on performance:
 *   Software ray marching at full resolution is CPU-intensive.
 *   This renderer uses half-resolution internal buffer upscaled 2x,
 *   which gives 4x the performance for acceptable quality at current scope.
 *   When hardware warrants, remove the 2x downscale.
 *
 * Contributors:
 *   Derek Jason Gilhousen — sphere world design, material philosophy,
 *                           layer ownership, avatar concept
 *   Claude (Anthropic)    — SDF ray marcher implementation,
 *                           LOD system, material color mapping
 */

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.List;

public class SphereRenderer
{
    // ── RENDER BUFFER ─────────────────────────────────────────────────────────
    // Internal buffer at half resolution for performance.
    // Drawn scaled 2x to panel via Graphics2D.drawImage.
    private BufferedImage renderBuffer = null;
    private int[]         pixels       = null;
    private int           bufW         = 0;
    private int           bufH         = 0;

    // ── RAY MARCH PARAMETERS ──────────────────────────────────────────────────
    private static final int   MAX_STEPS    = 64;
    private static final float SURF_DIST    = 0.01f;   // hit threshold
    private static final float MAX_DIST     = 500f;    // far clip
    private static final float SMOOTH_K     = 8.0f;    // smin blend factor

    // ── LIGHT ─────────────────────────────────────────────────────────────────
    // Sun direction in world space (normalized toward fn1 origin).
    // Updated each frame based on sun position relative to active planet.
    private float lightX = 0.577f;
    private float lightY = 0.577f;
    private float lightZ = 0.577f;

    // ─────────────────────────────────────────────────────────────────────────

    public void render(Graphics2D g, WorldState world, int panelW, int panelH)
    {
        // Allocate or reallocate buffer at half resolution
        int bW = Math.max(1, panelW / 2);
        int bH = Math.max(1, panelH / 2);

        if (renderBuffer == null || bufW != bW || bufH != bH)
        {
            renderBuffer = new BufferedImage(bW, bH, BufferedImage.TYPE_INT_RGB);
            pixels = ((DataBufferInt) renderBuffer.getRaster()
                                                  .getDataBuffer()).getData();
            bufW = bW;
            bufH = bH;
        }

        // Update light direction toward sun (fn1 at world origin)
        WorldState.SystemPlanet active = world.planets[world.activePlanet];
        if (active != null)
        {
            float[] pPos = planetWorldPos(world, world.activePlanet);
            float lx = world.sunPosition[0] - pPos[0];
            float ly = world.sunPosition[1] - pPos[1];
            float lz = world.sunPosition[2] - pPos[2];
            float len = (float) Math.sqrt(lx*lx + ly*ly + lz*lz);
            if (len > 0.001f) { lightX = lx/len; lightY = ly/len; lightZ = lz/len; }
        }

        // Build camera basis from camPos/camTarget
        float[] cam  = world.camPos;
        float[] tgt  = world.camTarget;
        float[] fwd  = normalize(sub(tgt, cam));

        // Restore the local 'Up' vector (surface normal) fix!
        float latRad = (float) Math.toRadians(world.playerLatDeg);
        float angRad = (float) Math.toRadians(world.playerAngleDeg);
        float[] playerUp = new float[]{
            (float)(Math.cos(latRad) * Math.sin(angRad)),
            (float) Math.sin(latRad),
            (float)(Math.cos(latRad) * Math.cos(angRad))
        };

        float[] right = normalize(cross(fwd, playerUp));
        float[] up   = cross(right, fwd);

        float aspect = (float) bW / bH;
        float tanFOV = (float) Math.tan(Math.toRadians(world.camFOV * 0.5));

        // ── SPHERE CULLING ────────────────────────────────────────────────────
        // Cull terrain spheres to visible set before the pixel loop.
        WorldState.SystemPlanet activePlanet2 = world.planets[world.activePlanet];
        float pcx = 0f, pcy = 0f, pcz = 0f;
        if (activePlanet2 != null)
        {
            float[] pp = planetWorldPos(world, world.activePlanet);
            pcx = pp[0]; pcy = pp[1]; pcz = pp[2];
        }
        int activeCount = 0;
        if (activePlanet2 != null && activePlanet2.terrain != null)
        {
            activeCount = OptimizeRender.SphereList.cull(
                activePlanet2.terrain, pcx, pcy, pcz,
                cam, fwd, world.sunPosition);
        }

        // ── PIXEL LOOP ────────────────────────────────────────────────────────
        for (int py = 0; py < bH; py++)
        {
            for (int px = 0; px < bW; px++)
            {
                // Temporal reprojection: skip pixels not in this frame's budget
                if (!OptimizeRender.Temporal.shouldRender(px, py)) continue;
                // NDC coordinates (-1 to +1)
                float ndcX = (2f * px / bW - 1f) * aspect * tanFOV;
                float ndcY = (1f - 2f * py / bH) * tanFOV;

                // Ray direction
                float rdx = fwd[0] + right[0]*ndcX + up[0]*ndcY;
                float rdy = fwd[1] + right[1]*ndcX + up[1]*ndcY;
                float rdz = fwd[2] + right[2]*ndcX + up[2]*ndcY;
                float rlen = (float) Math.sqrt(rdx*rdx + rdy*rdy + rdz*rdz);
                rdx /= rlen; rdy /= rlen; rdz /= rlen;

                pixels[py * bW + px] = march(
                    cam[0], cam[1], cam[2],
                    rdx, rdy, rdz,
                    world
                );
            }
        }

        // Render avatar into pixel buffer
        renderAvatar(world, cam, fwd, right, up, tanFOV, aspect);

        // Scale 2x to panel
        g.drawImage(renderBuffer, 0, 0, panelW, panelH, null);

        // Avatar speech text — rendered via Java2D on top (text is Python-owned)
        if (world.avatarChatOpen || !world.avatarSpeechText.isEmpty())
        {
            renderAvatarText(g, world, panelW, panelH);
        }
    }

    //Color Blender
    private int blendColor(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int)(r1 + (r2 - r1) * t);
        int g = (int)(g1 + (g2 - g1) * t);
        int b = (int)(b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }

    // ── RAY MARCHER ───────────────────────────────────────────────────────────

    private int march(float rox, float roy, float roz,
                      float rdx, float rdy, float rdz,
                      WorldState world)
    {
        float t = 0f;

        for (int i = 0; i < MAX_STEPS; i++)
        {
            float px = rox + rdx * t;
            float py = roy + rdy * t;
            float pz = roz + rdz * t;

            SDFResult res = sceneSDF(px, py, pz, world);
            float d = res.dist;

            if (d < SURF_DIST)
            {
                // Hit — calculate normal and shade
                int surfaceColor = shade(px, py, pz, res.materialType, world);
                
                // Atmospheric depth fog (light tinted blue)
                float fogFactor = 1.0f - (float)Math.exp(-t * 0.02f);
                return blendColor(surfaceColor, 0x55AADD, fogFactor * 0.8f);
            }

            t += d;
            if (t > MAX_DIST) break;
        }

        // Miss — sky/void color
        int skyCol = skyColor(rdx, rdy, rdz, world);
        
        // Atmospheric Halo around the planet
        WorldState.SystemPlanet planet = world.planets[world.activePlanet];
        if (planet != null) {
            float atmoRadius = planet.coreRadius * 1.35f; // 35% larger than core
            float b = 2.0f * (rox*rdx + roy*rdy + roz*rdz);
            float c = (rox*rox + roy*roy + roz*roz) - atmoRadius*atmoRadius;
            float disc = b*b - 4.0f*c;
            if (disc > 0) {
                float t1 = (-b - (float)Math.sqrt(disc)) / 2.0f;
                float t2 = (-b + (float)Math.sqrt(disc)) / 2.0f;
                float atmoDist = Math.max(0, t2 - Math.max(0, t1));
                if (atmoDist > 0) {
                    float glow = Math.min(1.0f, atmoDist / (atmoRadius * 1.5f));
                    skyCol = blendColor(skyCol, 0x55AADD, glow * 0.9f); 
                }
            }
        }
        return skyCol;
        // Miss — sky/void color
        //return skyColor(rdx, rdy, rdz, world);
    }

    // ── SCENE SDF ─────────────────────────────────────────────────────────────

    private static class SDFResult
    {
        float dist;
        int   materialType;
        int   hardness;

        SDFResult(float dist, int mat, int hard)
        {
            this.dist         = dist;
            this.materialType = mat;
            this.hardness     = hard;
        }
    }

    private SDFResult sceneSDF(float px, float py, float pz, WorldState world)
    {
        float  minDist = MAX_DIST;
        int    minMat  = WorldState.MAT_VOID;
        int    minHard = WorldState.HARD_VOID;

        WorldState.SystemPlanet planet = world.planets[world.activePlanet];
        if (planet == null) return new SDFResult(MAX_DIST, WorldState.MAT_VOID, 0);

        // ── BEDROCK CORE ──────────────────────────────────────────────────────
        // Always present. Sphere at planet center.
        float coreDist = sphereSDF(px, py, pz, 0f, 0f, 0f, planet.coreRadius);
        if (coreDist < minDist)
        {
            minDist = coreDist;
            minMat  = WorldState.MAT_BEDROCK;
            minHard = WorldState.HARD_STRUCTURAL;
        }

        // ── PLAYER AVATAR (Glasses Off / Selfie Mode) ─────────────────────────
        /*if (!world.isFirstPerson) {
            float px2 = world.playerPos[0];
            float py2 = world.playerPos[1];
            float pz2 = world.playerPos[2];

            // 1. Get local "Up" vector to stand perpendicular to the ground
            float latRad = (float) Math.toRadians(world.playerLatDeg);
            float angRad = (float) Math.toRadians(world.playerAngleDeg);
            float ux = (float)(Math.cos(latRad) * Math.sin(angRad));
            float uy = (float) Math.sin(latRad);
            float uz = (float)(Math.cos(latRad) * Math.cos(angRad));

            // 2. Base forms
            float hx = px2 + ux * world.playerHeightFeet;
            float hy = py2 + uy * world.playerHeightFeet;
            float hz = pz2 + uz * world.playerHeightFeet;
            float headDist = sphereSDF(px, py, pz, hx, hy, hz, 0.5f);

            float tx = px2 + ux * (world.playerHeightFeet * 0.5f);
            float ty = py2 + uy * (world.playerHeightFeet * 0.5f);
            float tz = pz2 + uz * (world.playerHeightFeet * 0.5f);
            float torsoDist = sphereSDF(px, py, pz, tx, ty, tz, 0.8f);

            // Blend head and torso
            float bodyDist = smin(headDist, torsoDist, 6.0f);

            // 3. Face Math (Always look at the camera/glasses)
            float fx = world.camPos[0] - hx;
            float fy = world.camPos[1] - hy;
            float fz = world.camPos[2] - hz;
            float flen = (float)Math.sqrt(fx*fx + fy*fy + fz*fz);
            if (flen > 0.001f) { fx /= flen; fy /= flen; fz /= flen; }
            
            // Right vector for eye spacing
            float rx = uy*fz - uz*fy;
            float ry = uz*fx - ux*fz;
            float rz = ux*fy - uy*fx;

            // 4. Carve out eyes using Math.max() for boolean subtraction
            float eyeSpacing = 0.2f;
            float eyeHeight  = 0.1f;
            float eyeDepth   = 0.45f; 
            float eyeSize    = 0.08f;
            
            float leftEye = sphereSDF(px, py, pz, 
                hx + fx*eyeDepth + rx*eyeSpacing + ux*eyeHeight, 
                hy + fy*eyeDepth + ry*eyeSpacing + uy*eyeHeight, 
                hz + fz*eyeDepth + rz*eyeSpacing + uz*eyeHeight, eyeSize);
                
            float rightEye = sphereSDF(px, py, pz, 
                hx + fx*eyeDepth - rx*eyeSpacing + ux*eyeHeight, 
                hy + fy*eyeDepth - ry*eyeSpacing + uy*eyeHeight, 
                hz + fz*eyeDepth - rz*eyeSpacing + uz*eyeHeight, eyeSize);

            // Carve the eye sockets out of the solid head
            bodyDist = Math.max(bodyDist, -leftEye);
            bodyDist = Math.max(bodyDist, -rightEye);

            // 5. Add a pointy nose (sphere protruding out and slightly down)
            float noseX = hx + fx*0.55f - ux*0.05f;
            float noseY = hy + fy*0.55f - uy*0.05f;
            float noseZ = hz + fz*0.55f - uz*0.05f;
            float noseDist = sphereSDF(px, py, pz, noseX, noseY, noseZ, 0.07f);
            
            // Smoothly blend the nose onto the face
            bodyDist = smin(bodyDist, noseDist, 12.0f);

            if (bodyDist < minDist) {
                minDist = bodyDist;
                minMat  = WorldState.MAT_CRAFTED; 
                minHard = WorldState.HARD_STRUCTURAL;
            }
        }*/
        // ── ASTRID AVATAR (Blue Soda Straws) ──────────────────────────────────
        if (!world.isFirstPerson) {
            float px2 = world.playerPos[0];
            float py2 = world.playerPos[1];
            float pz2 = world.playerPos[2];

            // 1. Local Coordinate System (Up, East, North)
            float latRad = (float) Math.toRadians(world.playerLatDeg);
            float angRad = (float) Math.toRadians(world.playerAngleDeg);
            float outX = (float)(Math.cos(latRad) * Math.sin(angRad));
            float outY = (float) Math.sin(latRad);
            float outZ = (float)(Math.cos(latRad) * Math.cos(angRad));
            float northX = (float)(-Math.sin(latRad) * Math.sin(angRad));
            float northY = (float) Math.cos(latRad);
            float northZ = (float)(-Math.sin(latRad) * Math.cos(angRad));
            float eastX =  (float) Math.cos(angRad);
            float eastY =  0f;
            float eastZ = -(float) Math.sin(angRad);

            float strawRadius = 0.08f; // Thickness of the blue straws
            float astridDist = MAX_DIST;

            // 2. The Spine (Torso)
            float spineBaseX = px2 + outX * 2.0f; // Hips
            float spineBaseY = py2 + outY * 2.0f;
            float spineBaseZ = pz2 + outZ * 2.0f;
            
            float spineTopX = px2 + outX * 4.5f; // Neck/Shoulders
            float spineTopY = py2 + outY * 4.5f;
            float spineTopZ = pz2 + outZ * 4.5f;
            
            astridDist = smin(astridDist, capsuleSDF(px, py, pz, spineBaseX, spineBaseY, spineBaseZ, spineTopX, spineTopY, spineTopZ, strawRadius), 0.5f);

            // 3. Legs
            float leftFootX = px2 - eastX * 0.5f; float leftFootY = py2 - eastY * 0.5f; float leftFootZ = pz2 - eastZ * 0.5f;
            float rightFootX = px2 + eastX * 0.5f; float rightFootY = py2 + eastY * 0.5f; float rightFootZ = pz2 + eastZ * 0.5f;
            astridDist = smin(astridDist, capsuleSDF(px, py, pz, spineBaseX, spineBaseY, spineBaseZ, leftFootX, leftFootY, leftFootZ, strawRadius), 0.5f);
            astridDist = smin(astridDist, capsuleSDF(px, py, pz, spineBaseX, spineBaseY, spineBaseZ, rightFootX, rightFootY, rightFootZ, strawRadius), 0.5f);

            // 4. The Head (Controlled by Arrow Keys)
            float headPitch = (float) Math.toRadians(world.headPitch);
            float headYaw   = (float) Math.toRadians(world.headYaw);
            float hDirX = (float)(Math.cos(headPitch) * Math.sin(headYaw));
            float hDirY = (float) Math.sin(headPitch);
            float hDirZ = -(float)(Math.cos(headPitch) * Math.cos(headYaw));
            
            float hwX = hDirX * eastX + hDirZ * northX + hDirY * outX;
            float hwY = hDirX * eastY + hDirZ * northY + hDirY * outY;
            float hwZ = hDirX * eastZ + hDirZ * northZ + hDirY * outZ;
            
            float headTopX = spineTopX + hwX * 1.0f;
            float headTopY = spineTopY + hwY * 1.0f;
            float headTopZ = spineTopZ + hwZ * 1.0f;
            astridDist = smin(astridDist, capsuleSDF(px, py, pz, spineTopX, spineTopY, spineTopZ, headTopX, headTopY, headTopZ, strawRadius), 0.5f);

            // 5. Right Arm (Controlled by CTRL + Arrow Keys)
            float rShoulderX = spineTopX + eastX * 0.8f; float rShoulderY = spineTopY + eastY * 0.8f; float rShoulderZ = spineTopZ + eastZ * 0.8f;
            float rArmPitch = (float) Math.toRadians(world.rightArmPitch);
            float rArmYaw   = (float) Math.toRadians(world.rightArmYaw);
            float rDirX = (float)(Math.cos(rArmPitch) * Math.sin(rArmYaw));
            float rDirY = (float) Math.sin(rArmPitch);
            float rDirZ = -(float)(Math.cos(rArmPitch) * Math.cos(rArmYaw));
            float rwX = rDirX * eastX + rDirZ * northX + rDirY * outX;
            float rwY = rDirX * eastY + rDirZ * northY + rDirY * outY;
            float rwZ = rDirX * eastZ + rDirZ * northZ + rDirY * outZ;
            
            float rHandX = rShoulderX + rwX * 2.0f; float rHandY = rShoulderY + rwY * 2.0f; float rHandZ = rShoulderZ + rwZ * 2.0f;
            astridDist = smin(astridDist, capsuleSDF(px, py, pz, spineTopX, spineTopY, spineTopZ, rShoulderX, rShoulderY, rShoulderZ, strawRadius), 0.5f); // collarbone
            astridDist = smin(astridDist, capsuleSDF(px, py, pz, rShoulderX, rShoulderY, rShoulderZ, rHandX, rHandY, rHandZ, strawRadius), 0.5f);

            // 6. Left Arm (Controlled by ALT + Arrow Keys)
            float lShoulderX = spineTopX - eastX * 0.8f; float lShoulderY = spineTopY - eastY * 0.8f; float lShoulderZ = spineTopZ - eastZ * 0.8f;
            float lArmPitch = (float) Math.toRadians(world.leftArmPitch);
            float lArmYaw   = (float) Math.toRadians(world.leftArmYaw);
            float lDirX = (float)(Math.cos(lArmPitch) * Math.sin(lArmYaw));
            float lDirY = (float) Math.sin(lArmPitch);
            float lDirZ = -(float)(Math.cos(lArmPitch) * Math.cos(lArmYaw));
            float lwX = lDirX * eastX + lDirZ * northX + lDirY * outX;
            float lwY = lDirX * eastY + lDirZ * northY + lDirY * outY;
            float lwZ = lDirX * eastZ + lDirZ * northZ + lDirY * outZ;
            
            float lHandX = lShoulderX + lwX * 2.0f; float lHandY = lShoulderY + lwY * 2.0f; float lHandZ = lShoulderZ + lwZ * 2.0f;
            astridDist = smin(astridDist, capsuleSDF(px, py, pz, spineTopX, spineTopY, spineTopZ, lShoulderX, lShoulderY, lShoulderZ, strawRadius), 0.5f); // collarbone
            astridDist = smin(astridDist, capsuleSDF(px, py, pz, lShoulderX, lShoulderY, lShoulderZ, lHandX, lHandY, lHandZ, strawRadius), 0.5f);

            // Render Astrid in bright blue to match the soda straw theme
            if (astridDist < minDist) {
                minDist = astridDist;
                minMat  = WorldState.MAT_CRAFTED; // Or any blue MAT you prefer
                minHard = WorldState.HARD_STRUCTURAL;
            }
        }
// ── TERRAIN SPHERES ───────────────────────────────────────────────────
        // Only evaluate spheres within LOD threshold of current position.
        float playerDist = (float) Math.sqrt(px*px + py*py + pz*pz);

        List<WorldState.TerrainSphere> terrain = planet.terrain;
        for (int i = 0; i < terrain.size(); i++)
        {
            WorldState.TerrainSphere s = terrain.get(i);
            if (s.materialType == WorldState.MAT_VOID) continue;

            // LOD gate — skip character spheres beyond threshold
            if (s.lodLevel >= 3 && playerDist > world.lodCharacterThreshold) continue;
            if (s.lodLevel >= 2 && playerDist > world.lodEntryThreshold)     continue;

            float sd = sphereSDF(px, py, pz, s.x, s.y, s.z, s.radius);

            // Smooth minimum with previous if same blend group
            if (i > 0 && terrain.get(i-1).blendGroup == s.blendGroup
                      && s.blendGroup != 0)
            {
                sd = smin(sd, minDist, SMOOTH_K);
            }

            if (sd < minDist)
            {
                // FABRIC WEAVE MICRO-DISPLACEMENT
                if (s.materialType == WorldState.MAT_AGGREGATE_D && playerDist < 15.0f) {
                    float weave = (float)(Math.sin(px * 40f) * Math.sin(py * 40f) * Math.sin(pz * 40f)) * 0.015f;
                    sd += weave;
                }

                minDist = sd;
                minMat  = s.materialType;
                minHard = s.hardness;
            }
        }

        return new SDFResult(minDist, minMat, minHard);
    }

    // ── SDF PRIMITIVES ────────────────────────────────────────────────────────

    private static float sphereSDF(float px, float py, float pz,
                                   float cx, float cy, float cz, float r)
    {
        float dx = px - cx;
        float dy = py - cy;
        float dz = pz - cz;
        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz) - r;
    }

    /**
     * Capsule SDF (A cylinder with rounded ends). Perfect for "soda straws".
     * @param pa The start point of the capsule (e.g., shoulder)
     * @param pb The end point of the capsule (e.g., hand)
     * @param r  The radius (thickness) of the straw
     */
    private static float capsuleSDF(float px, float py, float pz, 
                                    float pax, float pay, float paz, 
                                    float pbx, float pby, float pbz, float r) {
        float abX = pbx - pax, abY = pby - pay, abZ = pbz - paz;
        float apX = px - pax,  apY = py - pay,  apZ = pz - paz;
        
        float t = (apX*abX + apY*abY + apZ*abZ) / (abX*abX + abY*abY + abZ*abZ);
        t = Math.max(0f, Math.min(1f, t)); // clamp between 0 and 1
        
        float cx = pax + t * abX;
        float cy = pay + t * abY;
        float cz = paz + t * abZ;
        
        float dx = px - cx, dy = py - cy, dz = pz - cz;
        return (float) Math.sqrt(dx*dx + dy*dy + dz*dz) - r;
    }

    /**
     * Smooth minimum — blends two SDF values with radius k.
     * Gives organic rounded joins between adjacent spheres.
     * Exponential formulation: -ln(e^(-ka) + e^(-kb)) / k
     */
    private static float smin(float a, float b, float k)
    {
        float ea = (float) Math.exp(-k * a);
        float eb = (float) Math.exp(-k * b);
        return (float) (-Math.log(ea + eb) / k);
    }

    // ── NORMAL CALCULATION ────────────────────────────────────────────────────

    private float[] normal(float px, float py, float pz, WorldState world)
    {
        float eps = 0.001f;
        float dx = sceneSDF(px+eps, py, pz, world).dist
                 - sceneSDF(px-eps, py, pz, world).dist;
        float dy = sceneSDF(px, py+eps, pz, world).dist
                 - sceneSDF(px, py-eps, pz, world).dist;
        float dz = sceneSDF(px, py, pz+eps, world).dist
                 - sceneSDF(px, py, pz-eps, world).dist;
        return normalize(new float[]{dx, dy, dz});
    }

    // ── SHADING ───────────────────────────────────────────────────────────────

    private int shade(float px, float py, float pz,
                      int materialType, WorldState world)
    {
        float[] n = normal(px, py, pz, world);

        // Camera direction toward surface point
        float[] cam = world.camPos;
        float vx = cam[0] - px, vy = cam[1] - py, vz = cam[2] - pz;
        float vLen = (float) Math.sqrt(vx*vx + vy*vy + vz*vz);
        if (vLen > 0.001f) { vx /= vLen; vy /= vLen; vz /= vLen; }

        // Use PBR for near surfaces, Lambert for distant (LOD)
        float dist = vLen;
        if (dist < world.lodCharacterThreshold)
        {
            // Full PBR — Cook-Torrance BRDF
            boolean moving = world.isJumping ||
                             (Math.abs(world.verticalVelocity) > 0.01f);
            int shadowRays = OptimizeRender.Governor.getSoftShadowRays(moving);
            // Single shadow ray for now; multi-ray soft shadows future work
            double shadow = 1.0; // shadow ray handled by march occlusion
            return OptimizeRender.PBR.shade(
                materialType,
                n[0], n[1], n[2],
                vx, vy, vz,
                lightX, lightY, lightZ,
                1.5, shadow);
        }
        else
        {
            // Lambert for distant geometry — cheaper
            return OptimizeRender.PBR.shadeLambert(
                materialType, n[0], n[1], n[2],
                lightX, lightY, lightZ, 1.0);
        }
    }

    // materialColor() replaced by OptimizeRender.Materials arrays.
    // Left as a compatibility shim for any call sites not yet updated.
    private int[] materialColor(int mat)
    {
        int id = (mat >= 0 && mat < OptimizeRender.Materials.SLOTS) ? mat : 0;
        return new int[]{
            OptimizeRender.Materials.COLOR_R[id],
            OptimizeRender.Materials.COLOR_G[id],
            OptimizeRender.Materials.COLOR_B[id]
        };
    }

    private int skyColor(float rdx, float rdy, float rdz, WorldState world)
    {
        // Deep void with slight upward gradient
        float t = rdy * 0.5f + 0.5f;
        t = Math.max(0f, Math.min(1f, t));
        int r = (int)(2  + t * 6);
        int g = (int)(4  + t * 8);
        int b = (int)(10 + t * 15);
        return (r << 16) | (g << 8) | b;
    }

    // ── AVATAR RENDER ─────────────────────────────────────────────────────────
    // Avatar is rendered into the pixel buffer as a simple glowing sphere
    // at avatarPos. Glow intensity = CPU load from hardware_live.txt.
    // Stick figure detail is added via Java2D overlay in IceSandbox.

    private void renderAvatar(WorldState world, float[] cam,
                              float[] fwd, float[] right, float[] up,
                              float tanFOV, float aspect)
    {
        float[] ap  = world.avatarPos;
        float   glow = world.avatarGlowIntensity;
        float   aRadius = 0.3f; // avatar sphere radius in world units

        // Project avatar position to screen space
        float[] toAvatar = sub(ap, cam);
        float   depth    = dot(toAvatar, fwd);
        if (depth < 0.1f) return; // behind camera

        float screenX = dot(toAvatar, right) / (depth * tanFOV * aspect);
        float screenY = -dot(toAvatar, up)   / (depth * tanFOV);

        int sx = (int)((screenX * 0.5f + 0.5f) * bufW);
        int sy = (int)((screenY * 0.5f + 0.5f) * bufH);
        int sr = (int)(aRadius / depth * bufH * 0.5f);
        sr = Math.max(2, sr);

        // Draw glowing sphere into pixel buffer
        for (int py = Math.max(0, sy-sr*2); py < Math.min(bufH, sy+sr*2); py++)
        {
            for (int px = Math.max(0, sx-sr*2); px < Math.min(bufW, sx+sr*2); px++)
            {
                float dx = px - sx;
                float dy = py - sy;
                float dist = (float) Math.sqrt(dx*dx + dy*dy);
                float falloff = Math.max(0f, 1f - dist / (sr * 2f));

                if (falloff > 0)
                {
                    float intensity = falloff * falloff * glow;
                    int existing = pixels[py * bufW + px];
                    int er = (existing >> 16) & 0xFF;
                    int eg = (existing >>  8) & 0xFF;
                    int eb =  existing        & 0xFF;

                    // Glow color: purple-white, brighter at CPU spike
                    int gr = (int) Math.min(255, er + intensity * 157);
                    int gg = (int) Math.min(255, eg + intensity * 80);
                    int gb = (int) Math.min(255, eb + intensity * 187);
                    pixels[py * bufW + px] = (gr << 16) | (gg << 8) | gb;
                }
            }
        }
    }

    private void renderAvatarText(Graphics2D g, WorldState world,
                                  int panelW, int panelH)
    {
        // [PYTHON HOOK: avatarSpeechText]
        // Content from Python via C key chat conduit.
        // Java renders position and style; Python writes what it says.
        if (world.avatarSpeechText == null || world.avatarSpeechText.isEmpty()) return;

        g.setFont(new Font("Monospaced", Font.ITALIC, 11));
        FontMetrics fm = g.getFontMetrics();
        String text = world.avatarSpeechText;
        int tw = fm.stringWidth(text);
        int tx = (panelW - tw) / 2;
        int ty = (int)(panelH * 0.25f);

        g.setColor(new Color(0, 0, 0, 120));
        g.fillRoundRect(tx - 6, ty - 14, tw + 12, 20, 6, 6);

        g.setColor(new Color(220, 200, 255, 220));
        g.drawString(text, tx, ty);
    }

    // ── MATH UTILITIES ────────────────────────────────────────────────────────

    private static float[] sub(float[] a, float[] b)
    { return new float[]{a[0]-b[0], a[1]-b[1], a[2]-b[2]}; }

    private static float dot(float[] a, float[] b)
    { return a[0]*b[0] + a[1]*b[1] + a[2]*b[2]; }

    private static float[] cross(float[] a, float[] b)
    {
        return new float[]{
            a[1]*b[2] - a[2]*b[1],
            a[2]*b[0] - a[0]*b[2],
            a[0]*b[1] - a[1]*b[0]
        };
    }

    private static float[] normalize(float[] v)
    {
        float len = (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
        if (len < 0.0001f) return new float[]{0,1,0};
        return new float[]{v[0]/len, v[1]/len, v[2]/len};
    }

    private static float[] planetWorldPos(WorldState world, int fnNode)
    {
        float r   = WorldState.orbitalRadius(fnNode);
        float ang = (float) Math.toRadians(world.orbitAngles[fnNode]);
        return new float[]{
            r * OptimizeRender.FastMath.cosf(ang),
            0f,
            r * OptimizeRender.FastMath.sinf(ang)
        };
    }
}
