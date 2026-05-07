package modular.game;

/*
 * VoidRenderer.java
 *
 * Renders the background void layer beneath the SDF ray march composite.
 *
 * RENDER ORDER (IceSandbox.paintComponent):
 *   1. VoidRenderer.render()   — background: images, starfield, aurora
 *   2. SphereRenderer.render() — SDF ray march composited on top
 *      SphereRenderer uses TYPE_INT_ARGB with transparent sky pixels
 *      so the VoidRenderer background shows through void space.
 *
 * LAYER OWNERSHIP:
 *   C/ASM programs generate void background images:
 *     modular/game/images/void_layer_0.png (furthest back)
 *     modular/game/images/void_layer_1.png
 *     ... etc
 *   Java renders and parallax-scrolls them.
 *   If images absent: procedural starfield fallback (always operational).
 *
 * AURORA:
 *   Intensity driven by WorldState.gpuUtil (from hardware_live.txt).
 *   GPU utilization → brighter aurora. Rendered as wavy horizontal bands.
 *
 * WEATHER OVERLAY:
 *   Fog, storm, clear driven by WorldState.weatherType/weatherIntensity.
 *   Set by GameProtocol when [WORLD_EVENT:] tag received from Python.
 *
 * Contributors:
 *   Gemini (Google)        — original starfield concept
 *   Derek Jason Gilhousen — C/ASM void image layer design
 *   Claude (Anthropic)    — VoidRenderer, background composite role
 */

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

public class VoidRenderer
{
    // ── STARFIELD ─────────────────────────────────────────────────────────────
    private static final int STAR_COUNT = 300;
    private final int[]   starAngle;
    private final int[]   starElev;
    private final float[] starBright;
    private final float[] starSize;

    // ── C/ASM IMAGE LAYERS ────────────────────────────────────────────────────
    private final List<BufferedImage> voidLayers = new ArrayList<>();
    private File lastImagesDir = null;

    // ── AURORA ────────────────────────────────────────────────────────────────
    private final float[] auroraPhase = new float[5];

    // ── STAR BODIES (assets/ and system/ folders) ─────────────────────────────
    // Rendered as bright distant points in addition to the procedural starfield.
    // Populated from WorldState.stars by IceSandbox at load time.

    // ─────────────────────────────────────────────────────────────────────────

    public VoidRenderer(File imagesDir)
    {
        Random rand    = new Random(42); // seeded — consistent starfield
        starAngle      = new int[STAR_COUNT];
        starElev       = new int[STAR_COUNT];
        starBright     = new float[STAR_COUNT];
        starSize       = new float[STAR_COUNT];

        for (int i = 0; i < STAR_COUNT; i++)
        {
            starAngle[i]  = rand.nextInt(360);
            starElev[i]   = rand.nextInt(181) - 90;
            starBright[i] = 0.5f + rand.nextFloat() * 0.5f;
            starSize[i]   = 1.0f + rand.nextFloat() * 1.5f;
        }

        for (int i = 0; i < auroraPhase.length; i++) auroraPhase[i] = i * 0.4f;

        if (imagesDir != null && imagesDir.exists()) loadVoidImages(imagesDir);
    }

    // ── RENDER ────────────────────────────────────────────────────────────────

    public void render(Graphics2D g, WorldState world, int W, int H, float phase)
    {
        // Reload C/ASM images if new ones have been generated
        if (world.voidImagesDirty && world.voidImagesDir != null)
        {
            loadVoidImages(world.voidImagesDir);
            world.voidImagesDirty = false;
        }

        // Advance aurora phase
        for (int i = 0; i < auroraPhase.length; i++) auroraPhase[i] += 0.015f;

        // 1. Deep space gradient
        g.setPaint(new GradientPaint(0, 0, new Color(2, 4, 10),
                                     0, H, new Color(8, 12, 25)));
        g.fillRect(0, 0, W, H);

        // 2. C/ASM void image layers (parallax, back to front)
        if (!voidLayers.isEmpty()) renderImageLayers(g, world, W, H);

        // 3. Procedural starfield
        renderStarfield(g, world, W, H);

        // 4. Named star bodies from assets/ and system/ folders
        renderNamedStars(g, world, W, H);

        //4.5 Render the other active nodes/planets in the skybox
        renderOtherPlanets(g, world, W, H);

        // 5. Aurora (GPU utilization driven)
        float auroraIntensity = Math.max(world.gpuUtil / 100f,
                                          world.weatherIntensity / 200f);
        if (auroraIntensity > 0.05f) renderAurora(g, W, H, auroraIntensity);

        // 6. Weather overlay
        if (world.weatherIntensity > 5f) renderWeather(g, world, W, H);
    }

    // ── STARFIELD ─────────────────────────────────────────────────────────────

    private void renderStarfield(Graphics2D g, WorldState world, int W, int H)
    {
        for (int i = 0; i < STAR_COUNT; i++)
        {
            // Parallax relative to player angle — slower than planet rotation
            float parallax  = 0.6f;
            int   renderAng = (int)(starAngle[i] - world.playerAngleDeg * parallax + 360) % 360;
            int   sx        = (int)(W / 2f + (renderAng - 180) * (W / 360f));
            int   sy        = (int)(H / 2f - starElev[i] * (H / 220f));

            if (sy > H * 0.65f) continue;
            sx = ((sx % W) + W) % W;

            int alpha = (int)(starBright[i] * 220);
            g.setColor(new Color(220, 230, 255, alpha));
            float sz = starSize[i];
            g.fillOval((int)(sx - sz/2), (int)(sy - sz/2), (int)sz, (int)sz);
        }
    }

    // ── NAMED STARS (assets/ and system/ reference bodies) ───────────────────

    private void renderNamedStars(Graphics2D g, WorldState world, int W, int H)
    {
        if (world.stars == null || world.stars.isEmpty()) return;

        for (WorldState.StarBody star : world.stars)
        {
            // Project far star to screen using a simple angular mapping
            // Stars are at very large distances — treat as directional
            float normalizedX = star.x / 500f;  // normalize from world scale
            float normalizedY = star.y / 500f;

            int sx = (int)(W/2f + normalizedX * W * 0.4f);
            int sy = (int)(H/2f - normalizedY * H * 0.4f);

            if (sx < 0 || sx >= W || sy < 0 || sy >= H) continue;

            int   alpha  = (int)(star.brightness * 255);
            int   radius = (int)(star.radius * 2);
            Color base   = new Color(255, 245, 220, alpha);

            // Glow falloff
            for (int r = radius * 3; r >= 1; r--)
            {
                float falloff = (float) r / (radius * 3f);
                int   a       = (int)(alpha * (1f - falloff) * 0.5f);
                g.setColor(new Color(255, 245, 220, Math.min(255, a)));
                g.fillOval(sx - r, sy - r, r*2, r*2);
            }
            g.setColor(base);
            g.fillOval(sx - radius, sy - radius, radius*2, radius*2);
        }
    }

    // ── ACTIVE PLANET NODES ───────────────────────────────────────────────────

    private void renderOtherPlanets(Graphics2D g, WorldState world, int W, int H) 
    {
        // 1. Calculate camera basis vectors
        float[] cam = world.camPos;
        float[] tgt = world.camTarget;
        
        // Forward vector
        float fx = tgt[0] - cam[0];
        float fy = tgt[1] - cam[1];
        float fz = tgt[2] - cam[2];
        float flen = (float) Math.sqrt(fx*fx + fy*fy + fz*fz);
        if (flen < 0.0001f) return;
        fx /= flen; fy /= flen; fz /= flen;

        // Right vector (cross Forward with global Up [0,1,0])
        float rx = fy * 0f - fz * 1f;
        float ry = fz * 0f - fx * 0f;
        float rz = fx * 1f - fy * 0f;
        float rlen = (float) Math.sqrt(rx*rx + ry*ry + rz*rz);
        if (rlen < 0.0001f) { rx = 1f; ry = 0f; rz = 0f; } // fallback
        else { rx /= rlen; ry /= rlen; rz /= rlen; }

        // True Up vector (cross Right with Forward)
        float ux = ry * fz - rz * fy;
        float uy = rz * fx - rx * fz;
        float uz = rx * fy - ry * fx;

        float aspect = (float) W / H;
        float tanFOV = (float) Math.tan(Math.toRadians(world.camFOV * 0.5));

        for (int i = 1; i <= 12; i++) 
        {
            if (i == world.activePlanet) continue; // Skip current planet
            if (i == WorldState.FN_ASTEROID_BELT) continue; // Debris field, no core body
            
            float[] targetPos = planetWorldPos(world, i);
            
            // Vector from camera to target planet
            float dx = targetPos[0] - cam[0];
            float dy = targetPos[1] - cam[1];
            float dz = targetPos[2] - cam[2];

            // Project onto camera's Forward vector (Depth)
            float depth = dx * fx + dy * fy + dz * fz;
            
            // If planet is behind the camera, clip it
            if (depth < 0.1f) continue;

            // Project onto camera's Right and Up vectors
            float rightOffset = dx * rx + dy * ry + dz * rz;
            float upOffset    = dx * ux + dy * uy + dz * uz;

            // Perspective division
            float screenX = rightOffset / (depth * tanFOV * aspect);
            float screenY = -upOffset   / (depth * tanFOV); // Invert Y for 2D screen

            // Map to panel pixel coordinates
            int sx = (int) ((screenX * 0.5f + 0.5f) * W);
            int sy = (int) ((screenY * 0.5f + 0.5f) * H);
            
            // Apparent radius based on distance and body type
            float baseRadius = (i == WorldState.FN_SUN) ? 120f : 40f;
            int radius = Math.max(2, (int)((baseRadius / depth) * H * 0.5f)); 

            // Cull if off screen entirely (with a small margin)
            if (sx < -radius || sx > W + radius || sy < -radius || sy > H + radius) continue;

            // Draw the celestial body
            if (i == WorldState.FN_SUN) {
                // Sun glow
                g.setColor(new Color(255, 200, 100, 80));
                g.fillOval(sx - radius*2, sy - radius*2, radius*4, radius*4);
                g.setColor(new Color(255, 240, 200, 240));
                g.fillOval(sx - radius, sy - radius, radius*2, radius*2);
            } else {
                // Standard planet
                g.setColor(new Color(157, 80, 187, 220)); 
                g.fillOval(sx - radius, sy - radius, radius*2, radius*2);
            }
        }
    }

    private float[] planetWorldPos(WorldState world, int fnNode) 
    {
        float r   = WorldState.orbitalRadius(fnNode);
        float ang = (float) Math.toRadians(world.orbitAngles[fnNode]);
        return new float[]{
            r * (float) Math.cos(ang),
            0f, // Orbital inclination is flat for V1
            r * (float) Math.sin(ang)
        };
    }

    // ── C/ASM IMAGE LAYERS ────────────────────────────────────────────────────

    private void renderImageLayers(Graphics2D g, WorldState world, int W, int H)
    {
        for (int i = 0; i < voidLayers.size(); i++)
        {
            BufferedImage img      = voidLayers.get(i);
            float         parallax = 0.1f + i * 0.15f;
            float         offset   = (world.playerAngleDeg * parallax) % W;

            AlphaComposite ac = AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, 0.5f + i * 0.1f);
            g.setComposite(ac);

            int horizH = (int)(H * 0.65f);
            int drawX  = (int)(-offset);
            g.drawImage(img, drawX,      0, W, horizH, null);
            g.drawImage(img, drawX + W,  0, W, horizH, null);
            g.drawImage(img, drawX + W*2, 0, W, horizH, null);

            g.setComposite(AlphaComposite.SrcOver);
        }
    }

    private void loadVoidImages(File dir)
    {
        voidLayers.clear();
        lastImagesDir = dir;
        for (int i = 0; i < 8; i++)
        {
            File f = new File(dir, "void_layer_" + i + ".png");
            if (!f.exists()) break;
            try   { voidLayers.add(ImageIO.read(f)); }
            catch (IOException ignored) {}
        }
    }

    // ── AURORA ────────────────────────────────────────────────────────────────

    private void renderAurora(Graphics2D g, int W, int H, float intensity)
    {
        int bandCount = auroraPhase.length;
        int auroraH   = (int)(H * 0.3f);

        for (int band = 0; band < bandCount; band++)
        {
            int   alpha = (int)(intensity * 55 * (1f - band * 0.15f));
            if (alpha < 5) continue;

            Color c;
            switch (band % 3)
            {
                case 0:  c = new Color(80,  220, 120, alpha); break;
                case 1:  c = new Color(80,  150, 220, alpha); break;
                default: c = new Color(157,  80, 187, alpha); break;
            }

            float        bandY = H * 0.08f + band * (auroraH / (float)bandCount);
            GeneralPath  path  = new GeneralPath();
            path.moveTo(0, bandY);

            for (int x = 0; x <= W; x += 8)
            {
                float y = bandY + (float)(
                    Math.sin(x * 0.01 + auroraPhase[band]) * 12 * intensity);
                path.lineTo(x, y);
            }
            path.lineTo(W,  bandY + auroraH / (float)bandCount);
            path.lineTo(0,  bandY + auroraH / (float)bandCount);
            path.closePath();

            g.setColor(c);
            g.fill(path);
        }
    }

    // ── WEATHER ───────────────────────────────────────────────────────────────

    private void renderWeather(Graphics2D g, WorldState world, int W, int H)
    {
        float t = world.weatherIntensity / 100f;
        switch (world.weatherType)
        {
            case "fog":
                g.setColor(new Color(180, 200, 220, (int)(t * 55)));
                g.fillRect(0, 0, W, H);
                break;
            case "storm":
                g.setColor(new Color(10, 15, 30, (int)(t * 70)));
                g.fillRect(0, 0, W, H);
                break;
            default:
                break;
        }
    }
}
