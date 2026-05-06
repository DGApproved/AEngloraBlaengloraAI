package modular.game;

/*
 * PlayerPhysics.java
 *
 * Player movement and physics on the surface of a sphere.
 * Writes 3D Cartesian world position to WorldState so the
 * SphereRenderer camera can track the player correctly.
 *
 * Coordinate system:
 *   playerAngleDeg — longitude (0-360, WASD left/right)
 *   playerLatDeg   — latitude (-90 south to +90 north, WASD fwd/back)
 *   playerAltitude — feet above surface (0 = standing)
 *
 * 3D position derived from spherical coordinates:
 *   x = (r + alt) * cos(lat) * sin(angle)
 *   y = (r + alt) * sin(lat)
 *   z = (r + alt) * cos(lat) * cos(angle)
 *
 * Gravity scales by WorldState.baseGravity(fnNode) so each planet
 * feels distinct — Moon is floaty, Jupiter is heavy.
 *
 * Contributors:
 *   Gemini (Google)        — original physics concept
 *   Derek Jason Gilhousen — per-planet gravity design
 *   Claude (Anthropic)    — 3D position write, gravity scaling
 */

public class PlayerPhysics
{
    // Base constants — scaled by planet gravity at tick time
    private static final float BASE_GRAVITY    = 0.08f;  // ft/tick²
    private static final float JUMP_VELOCITY   = 1.2f;   // ft/tick
    private static final float WALK_SPEED_DEG  = 1.0f;   // degrees/tick base

    // Camera follow offset in world units
    // Camera sits behind and above the player along the surface normal
    private static final float CAM_DISTANCE    = 6.0f;
    private static final float CAM_HEIGHT      = 2.5f;

    private float   angleDeg     = 0f;
    private float   latDeg       = 0f;
    private float   altitude     = 0f;
    private float   vertVelocity = 0f;
    private boolean jumping      = false;

    private boolean moveLeft    = false;
    private boolean moveRight   = false;
    private boolean moveForward = false;
    private boolean moveBack    = false;

    // ── INPUT ─────────────────────────────────────────────────────────────────

    public void setMoveLeft(boolean v)    { moveLeft    = v; }
    public void setMoveRight(boolean v)   { moveRight   = v; }
    public void setMoveForward(boolean v) { moveForward = v; }
    public void setMoveBack(boolean v)    { moveBack    = v; }

    public void requestJump()
    {
        if (!jumping && altitude < 0.01f)
        {
            jumping      = true;
            vertVelocity = JUMP_VELOCITY;
        }
    }

    // ── TICK ──────────────────────────────────────────────────────────────────

    public void tick(float planetRadius, float gravityScale)
    {
        float speed = WALK_SPEED_DEG / Math.max(0.5f, planetRadius / 1.5f);

        if (moveLeft)    angleDeg -= speed;
        if (moveRight)   angleDeg += speed;
        if (moveForward) latDeg   += speed;
        if (moveBack)    latDeg   -= speed;

        // Wrap longitude
        if (angleDeg <   0) angleDeg += 360f;
        if (angleDeg >= 360) angleDeg -= 360f;

        // Clamp latitude
        latDeg = Math.max(-89.9f, Math.min(89.9f, latDeg));

        // Vertical physics
        if (jumping || altitude > 0)
        {
            altitude     += vertVelocity;
            vertVelocity -= BASE_GRAVITY * gravityScale;
            if (altitude <= 0)
            {
                altitude     = 0;
                jumping      = false;
                vertVelocity = 0;
            }
        }
    }

    // ── WRITE TO WORLD ────────────────────────────────────────────────────────

    public void writeToWorld(WorldState world, float planetRadius)
    {
        world.playerAngleDeg     = angleDeg;
        world.playerLatDeg       = latDeg;
        world.playerAltitudeFeet = altitude;
        world.isJumping          = jumping;
        world.verticalVelocity   = vertVelocity;

        // 3D Cartesian position on sphere surface
        float r      = planetRadius + altitude;
        float latRad = (float) Math.toRadians(latDeg);
        float angRad = (float) Math.toRadians(angleDeg);

        float px = r * (float)(Math.cos(latRad) * Math.sin(angRad));
        float py = r * (float) Math.sin(latRad);
        float pz = r * (float)(Math.cos(latRad) * Math.cos(angRad));

        // Surface normal — outward from planet center through player
        float latRad = (float)Math.toRadians(latDeg);
        float angRad = (float)Math.toRadians(angleDeg);

        float outX = (float)(Math.cos(latRad) * Math.sin(angRad));
        float outY = (float) Math.sin(latRad);
        float outZ = (float)(Math.cos(latRad) * Math.cos(angRad));

        // North tangent — direction of increasing latitude on sphere surface
        float northX = (float)(-Math.sin(latRad) * Math.sin(angRad));
        float northY = (float) Math.cos(latRad);
        float northZ = (float)(-Math.sin(latRad) * Math.cos(angRad));

        // East tangent — direction of increasing longitude on sphere surface
        float eastX =  (float)Math.cos(angRad);
        float eastY =  0f;
        float eastZ = -(float)Math.sin(angRad);

        // Camera orbits player at CAM_DISTANCE, controlled by cameraYaw + cameraPitch
        float pitchRad  = (float)Math.toRadians(world.cameraPitch);
        float yawRad    = (float)Math.toRadians(world.cameraYaw);
        float horizDist = CAM_DISTANCE * (float)Math.cos(pitchRad);
        float vertDist  = CAM_DISTANCE * (float)Math.sin(pitchRad);

        float localEast  =  horizDist * (float)Math.sin(yawRad);
        float localNorth = -horizDist * (float)Math.cos(yawRad); // negative = behind

        world.camPos[0] = px + localEast*eastX + localNorth*northX + vertDist*outX;
        world.camPos[1] = py + localEast*eastY + localNorth*northY + vertDist*outY;
        world.camPos[2] = pz + localEast*eastZ + localNorth*northZ + vertDist*outZ;

        // Camera looks at player's approximate head height
        float headH = world.playerHeightFeet * 0.35f;
        world.camTarget[0] = px + outX * headH;
        world.camTarget[1] = py + outY * headH;
        world.camTarget[2] = pz + outZ * headH;

        // Avatar companion slightly ahead
        float aheadRad = (float)Math.toRadians(angleDeg + 15f);
        world.avatarPos[0] = (r + 0.1f) * (float)(Math.cos(latRad) * Math.sin(aheadRad));
        world.avatarPos[1] = (r + 0.1f) * (float) Math.sin(latRad);
        world.avatarPos[2] = (r + 0.1f) * (float)(Math.cos(latRad) * Math.cos(aheadRad));
    }

    public float getAngleDeg() { return angleDeg; }
    public float getLatDeg()   { return latDeg; }
    public float getAltitude() { return altitude; }
    public boolean isJumping() { return jumping; }
}
