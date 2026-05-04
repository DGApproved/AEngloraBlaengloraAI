package modular.game;

/*
 * PlayerPhysics.java
 *
 * Player movement and physics on the surface of a small sphere.
 *
 * Coordinate system:
 *   playerAngleDeg   — longitude around the sphere (0-360)
 *                      WASD left/right changes this
 *   playerLatDeg     — latitude from equator (-90 south pole, +90 north pole)
 *                      WASD forward/back changes this
 *   playerAltitudeFt — feet above sphere surface
 *                      0 = standing, >0 = airborne
 *
 * Gravity: pulls toward sphere center (radially inward).
 * Jump: initial velocity radially outward from sphere center.
 *
 * Scale calibration:
 *   Walking speed is calibrated so a full sphere circumference traversal
 *   takes ~30 seconds at default pace. This keeps the 3ft planet feel
 *   meaningful — you can walk around it in half a minute.
 *
 * FUTURE HOOKS:
 *   - Variable player height (set via Python persona.txt or world_seed.json)
 *   - Gravity strength modifier from world seed (light/heavy worlds)
 *   - Surface friction modifier from sphere texture type
 *
 * Contributors:
 *   Gemini (Google)        — original physics concept (IceSandbox v1)
 *   Derek Jason Gilhousen — spherical walking design intent
 *   Claude (Anthropic)    — spherical coordinate physics, calibration
 */

public class PlayerPhysics 
{
    // ── PHYSICS CONSTANTS ─────────────────────────────────────────────────────
    // Gravity in feet per tick squared (toward sphere center)
    private static final float GRAVITY_FT_PER_TICK = 0.08f;

    // Jump initial velocity in feet per tick (away from sphere center)
    private static final float JUMP_VELOCITY = 1.2f;

    // Walking speed in degrees per tick at default planet size
    // Calibrated: 30s to circumnavigate at 81ms tick = ~370 ticks = 360° / 370 ≈ 0.97°/tick
    private static final float WALK_SPEED_DEG = 1.0f;

    // ── STATE ─────────────────────────────────────────────────────────────────
    private float angleDeg      = 0.0f;    // longitude (0-360)
    private float latDeg        = 0.0f;    // latitude (-90 to +90)
    private float altitude      = 0.0f;    // feet above surface
    private float vertVelocity  = 0.0f;    // feet/tick, positive = outward
    private boolean jumping     = false;

    // Walk input state — set by key handler, consumed by physics tick
    private boolean moveLeft    = false;
    private boolean moveRight   = false;
    private boolean moveForward = false;
    private boolean moveBack    = false;
    private boolean jumpPressed = false;

    // Planet radius at time of last calibration
    private float lastPlanetRadius = 1.5f;

    // ── INPUT SETTERS ─────────────────────────────────────────────────────────

    public void setMoveLeft(boolean v)    { moveLeft    = v; }
    public void setMoveRight(boolean v)   { moveRight   = v; }
    public void setMoveForward(boolean v) { moveForward = v; }
    public void setMoveBack(boolean v)    { moveBack    = v; }

    public void requestJump() 
    {
        if (!jumping && altitude <= 0.001f) 
        {
            jumping      = true;
            vertVelocity = JUMP_VELOCITY;
        }
    }

    // ── PHYSICS TICK ──────────────────────────────────────────────────────────

    /**
     * Advance physics by one tick.
     * Called once per logic tick (81ms in MODE_GAMEPLAY).
     * @param planetRadius sphere radius in feet
     */
    public void tick(float planetRadius) 
    {
        lastPlanetRadius = planetRadius;

        // ── HORIZONTAL MOVEMENT ───────────────────────────────────────────────
        // Walk speed scales slightly with planet size so larger planets
        // don't feel impossibly slow to traverse.
        float speedScale  = Math.max(0.5f, planetRadius / 1.5f);
        float effectiveSpeed = WALK_SPEED_DEG / speedScale;

        if (moveLeft)    angleDeg -= effectiveSpeed;
        if (moveRight)   angleDeg += effectiveSpeed;
        if (moveForward) latDeg   += effectiveSpeed;
        if (moveBack)    latDeg   -= effectiveSpeed;

        // Wrap longitude
        if (angleDeg < 0)    angleDeg += 360.0f;
        if (angleDeg >= 360) angleDeg -= 360.0f;

        // Clamp latitude — can reach poles but not past them
        latDeg = Math.max(-89.9f, Math.min(89.9f, latDeg));

        // ── VERTICAL (JUMP / GRAVITY) ─────────────────────────────────────────
        if (jumping || altitude > 0) 
        {
            altitude     += vertVelocity;
            vertVelocity -= GRAVITY_FT_PER_TICK;

            if (altitude <= 0) 
            {
                altitude     = 0;
                jumping      = false;
                vertVelocity = 0;
            }
        }
    }

    // ── STATE WRITE TO WORLD ──────────────────────────────────────────────────

    public void writeToWorld(WorldState world) 
    {
        world.playerAngleDeg     = angleDeg;
        world.playerAltitudeFeet = altitude;
        world.isJumping          = jumping;
        world.verticalVelocity   = vertVelocity;
    }

    // ── ACCESSORS ─────────────────────────────────────────────────────────────

    public float getAngleDeg()   { return angleDeg; }
    public float getLatDeg()     { return latDeg; }
    public float getAltitude()   { return altitude; }
    public boolean isJumping()   { return jumping; }

    /**
     * Returns the player's 2D screen position given sphere center and screen radius.
     * X component: horizontal screen position
     * Y component: vertical screen position
     *
     * The sphere is rendered as a circle. The player appears on the rim
     * at their current longitude angle. Altitude lifts them off the rim
     * along the surface normal at that angle.
     */
    public float[] getScreenPosition(float sphereCenterX, float sphereCenterY,
                                     float sphereScreenRadius, float altitudeScale) 
    {
        double rad = Math.toRadians(angleDeg);

        // Position on sphere rim
        float rimX = sphereCenterX + sphereScreenRadius * (float)Math.sin(rad);
        float rimY = sphereCenterY - sphereScreenRadius * (float)Math.cos(rad);

        // Altitude offset along the normal at this angle (pointing away from center)
        float normX = (float)Math.sin(rad);
        float normY = -(float)Math.cos(rad);

        float altPx = altitude * altitudeScale;

        return new float[]{ rimX + normX * altPx, rimY + normY * altPx };
    }
}
