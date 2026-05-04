package modular.game;

/*
 * VoidRenderer.java
 *
 * Renders the void surrounding the planet sphere.
 *
 * Java owns:
 *   - Background fill (deep space gradient)
 *   - Procedural starfield (fallback, always operational)
 *   - Image compositing and rotation as player moves
 *   - Atmosphere/weather effects (aurora, fog, storm)
 *
 * C/ASM owns (via image file hook):
 *   - Void background image generation
 *   - Written to: modular/game/images/
 *   - File format: PNG, any resolution (scaled to screen)
 *   - Java scans the directory and composites all found images
 *   - Multiple images layered at different depths (parallax)
 *   - If directory empty or absent: starfield fallback (fully operational)
 *
 * C/ASM IMAGE HOOK:
 *   C and ASM programs in modular/game/ generate void background images.
 *   These might be procedural nebulae, fractal noise fields, or other
 *   generative graphics that would be slow in Java but fast in native code.
 *   VoidRenderer scans modular/game/images/ at startup and when
 *   WorldState.voidImagesDirty is true (set by GameProtocol on file change).
 *   Images are named: void_layer_{N}.png where N is the depth order.
 *   Lower N = further back (lower parallax multiplier).
 *
 * PYTHON ATMOSPHERE HOOK:
 *   weatherIntensity, weatherType from WorldState drive effect overlays.
 *   Values set by GameProtocol when [WORLD_EVENT:type:intensity] received.
 *   hardware_live.txt CPU load → storm intensity (read on logic tick).
 *
 * Contributors:
 *   Gemini (Google)        — original starfield + parallax concept
 *   Derek Jason Gilhousen — void/C/ASM layer design
 *   Claude (Anthropic)    — VoidRenderer, image hooks, atmosphere effects
 */

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

public class VoidRenderer 
{
    // ── STARFIELD (procedural fallback) ───────────────────────────────────────
    private static final int STAR_COUNT = 300;
    private final int[]   starAngle;     // 0-359 degrees longitude
    private final int[]   starElev;      // -90 to +90 degrees latitude
    private final float[] starBrightness;// 0.5 - 1.0
    private final float[] starSize;      // 1.0 - 2.5

    // ── VOID IMAGE LAYERS (C/ASM generated) ───────────────────────────────────
    private final List<BufferedImage> voidLayers = new ArrayList<>();
    private File lastImagesDir = null;

    // ── AURORA PARTICLES ──────────────────────────────────────────────────────
    // Driven by GPU utilization from hardware_live.txt when Python is running.
    // Java generates aurora geometry; Python governs intensity via WorldState.
    private float[] auroraPhase;
    private static final int AURORA_BANDS = 5;

    // ─────────────────────────────────────────────────────────────────────────

    public VoidRenderer(File imagesDir) 
    {
        // Generate procedural starfield
        Random rand = new Random(42); // seeded for consistency
        starAngle      = new int[STAR_COUNT];
        starElev       = new int[STAR_COUNT];
        starBrightness = new float[STAR_COUNT];
        starSize       = new float[STAR_COUNT];

        for (int i = 0; i < STAR_COUNT; i++) 
        {
            starAngle[i]      = rand.nextInt(360);
            starElev[i]       = rand.nextInt(181) - 90;
            starBrightness[i] = 0.5f + rand.nextFloat() * 0.5f;
            starSize[i]       = 1.0f + rand.nextFloat() * 1.5f;
        }

        auroraPhase = new float[AURORA_BANDS];
        for (int i = 0; i < AURORA_BANDS; i++) auroraPhase[i] = i * 0.4f;

        // Load C/ASM void images if available
        if (imagesDir != null && imagesDir.exists()) 
        {
            loadVoidImages(imagesDir);
        }
    }

    // ── RENDER ────────────────────────────────────────────────────────────────

    public void render(Graphics2D g, WorldState world,
                       int width, int height, float renderPhase) 
    {
        // Reload void images if C/ASM programs have generated new ones
        if (world.voidImagesDirty && world.voidImagesDir != null) 
        {
            loadVoidImages(world.voidImagesDir);
            world.voidImagesDirty = false;
        }

        // Advance aurora phase
        for (int i = 0; i < AURORA_BANDS; i++) auroraPhase[i] += 0.02f;

        // ── DEEP SPACE BACKGROUND ─────────────────────────────────────────────
        GradientPaint bg = new GradientPaint(
            0, 0,      new Color(2,  4,  10),
            0, height, new Color(8,  12, 25)
        );
        g.setPaint(bg);
        g.fillRect(0, 0, width, height);

        // ── C/ASM VOID IMAGE LAYERS ────────────────────────────────────────────
        // [C/ASM HOOK: VOID IMAGES]
        // Each layer composited at a different parallax depth.
        // Deeper layers (lower index) rotate slower with player movement.
        if (!voidLayers.isEmpty()) 
        {
            renderVoidImageLayers(g, world, width, height);
        }

        // ── PROCEDURAL STARFIELD ──────────────────────────────────────────────
        // Always rendered — stars show through gaps in void images
        // or as the complete background when no images are present.
        renderStarfield(g, world, width, height);

        // ── AURORA (GPU utilization → intensity) ──────────────────────────────
        // [PYTHON HOOK: hardware_live.txt GPU_UTIL]
        // When Python monitor thread writes GPU utilization, WorldState.gpuUtil
        // is updated on the logic tick. Higher GPU util = brighter aurora.
        float auroraIntensity = Math.max(world.gpuUtil / 100.0f,
                                          world.weatherIntensity / 200.0f);
        if (auroraIntensity > 0.05f) 
        {
            renderAurora(g, width, height, auroraIntensity, renderPhase);
        }

        // ── WEATHER OVERLAY ───────────────────────────────────────────────────
        // [PYTHON HOOK: WORLD_EVENT]
        if (world.weatherIntensity > 5.0f) 
        {
            renderWeather(g, world, width, height, renderPhase);
        }
    }

    // ── STARFIELD ─────────────────────────────────────────────────────────────

    private void renderStarfield(Graphics2D g, WorldState world, int W, int H) 
    {
        float playerAngle = world.playerAngleDeg;

        for (int i = 0; i < STAR_COUNT; i++) 
        {
            // Stars parallax slightly slower than planet rotation
            float parallax = 0.6f;
            int renderAngle = (int)(starAngle[i] - playerAngle * parallax + 360) % 360;

            // Project from spherical to screen
            int sx = (int)(W / 2.0f + (renderAngle - 180) * (W / 360.0f));
            int sy = (int)(H / 2.0f - starElev[i] * (H / 220.0f));

            // Only render above the approximate horizon
            if (sy > H * 0.65f) continue;

            // Wrap horizontal
            sx = ((sx % W) + W) % W;

            int alpha = (int)(starBrightness[i] * 220);
            g.setColor(new Color(220, 230, 255, alpha));
            float sz = starSize[i];
            g.fill(new Ellipse2D.Float(sx - sz / 2, sy - sz / 2, sz, sz));
        }
    }

    // ── C/ASM VOID IMAGE LAYERS ───────────────────────────────────────────────

    private void renderVoidImageLayers(Graphics2D g, WorldState world, int W, int H) 
    {
        // [C/ASM HOOK: IMAGE COMPOSITING]
        // Layers drawn back to front. Each successive layer has stronger parallax.
        for (int i = 0; i < voidLayers.size(); i++) 
        {
            BufferedImage img   = voidLayers.get(i);
            float parallaxMult  = 0.1f + (i * 0.15f); // deeper = slower
            float offset        = (world.playerAngleDeg * parallaxMult) % W;

            AlphaComposite ac = AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER,
                0.6f + i * 0.1f   // deeper layers more transparent
            );
            g.setComposite(ac);

            // Draw image scrolling with player angle
            int drawX = (int)(-offset);
            g.drawImage(img, drawX,          0, W, (int)(H * 0.65f), null);
            g.drawImage(img, drawX + W,      0, W, (int)(H * 0.65f), null);
            g.drawImage(img, drawX + W * 2,  0, W, (int)(H * 0.65f), null);

            g.setComposite(AlphaComposite.SrcOver);
        }
    }

    private void loadVoidImages(File imagesDir) 
    {
        voidLayers.clear();
        if (imagesDir == null || !imagesDir.exists()) return;
        lastImagesDir = imagesDir;

        // Load void_layer_0.png, void_layer_1.png, etc. in order
        for (int i = 0; i < 8; i++) 
        {
            File f = new File(imagesDir, "void_layer_" + i + ".png");
            if (!f.exists()) break;
            try 
            {
                voidLayers.add(ImageIO.read(f));
            } 
            catch (IOException ignored) {}
        }
    }

    // ── AURORA ────────────────────────────────────────────────────────────────

    private void renderAurora(Graphics2D g, int W, int H,
                               float intensity, float phase) 
    {
        int auroraH = (int)(H * 0.3f);

        for (int band = 0; band < AURORA_BANDS; band++) 
        {
            float bandPhase = auroraPhase[band];
            int alpha = (int)(intensity * 60 * (1.0f - band * 0.15f));
            if (alpha < 5) continue;

            // Each band is a wavy horizontal stripe
            Color c;
            switch (band % 3) 
            {
                case 0: c = new Color(80,  220, 120, alpha); break;
                case 1: c = new Color(80,  150, 220, alpha); break;
                default: c = new Color(157, 80,  187, alpha); break;
            }

            GeneralPath bandShape = new GeneralPath();
            float bandY = H * 0.1f + band * (auroraH / (float)AURORA_BANDS);
            bandShape.moveTo(0, bandY);

            for (int x = 0; x <= W; x += 8) 
            {
                float y = bandY + (float)(Math.sin(x * 0.01f + bandPhase) * 15
                                         * intensity);
                bandShape.lineTo(x, y);
            }
            bandShape.lineTo(W, bandY + auroraH / (float)AURORA_BANDS);
            bandShape.lineTo(0, bandY + auroraH / (float)AURORA_BANDS);
            bandShape.closePath();

            g.setColor(c);
            g.fill(bandShape);
        }
    }

    // ── WEATHER ───────────────────────────────────────────────────────────────

    private void renderWeather(Graphics2D g, WorldState world,
                                int W, int H, float phase) 
    {
        float intensity = world.weatherIntensity / 100.0f;

        switch (world.weatherType) 
        {
            case "fog":
                g.setColor(new Color(180, 200, 220, (int)(intensity * 60)));
                g.fillRect(0, 0, W, H);
                break;

            case "storm":
                // Fast-moving dark overlay
                g.setColor(new Color(10, 15, 30, (int)(intensity * 80)));
                g.fillRect(0, 0, W, H);
                break;

            case "aurora":
                // Extra aurora pass at high intensity
                renderAurora(g, W, H, intensity * 1.5f, phase * 2);
                break;

            default:
                break;
        }
    }
}
