package modular.game;

/*
 * PlayerPhysics.java
 *
 * Player movement and physics on the surface of a sphere.
 * Writes 3D position and camera to WorldState each logic tick.
 *
 * COORDINATES:
 *   playerAngleDeg — longitude (0-360), A/D changes this
 *   playerLatDeg   — latitude (-90 to +90), W/S changes this
 *   playerAltitude — feet above surface (0 = standing)
 *
 * CAMERA:
 *   Orbits the player at CAM_DISTANCE.
 *   world.cameraYaw and world.cameraPitch are set by IceSandbox
 *   from arrow keys or mouse. Physics reads them here to place the camera.
 *
 * GRAVITY:
 *   Scales by gravityScale per planet (Earth=1.0, Moon=0.17, Jupiter=2.53).
 *
 * Contributors:
 *   Gemini (Google)        — original physics concept
 *   Derek Jason Gilhousen — per-planet gravity, spherical walking design
 *   Claude (Anthropic)    — 3D position/camera implementation
 */

public class PlayerPhysics
{
    private static final float BASE_GRAVITY   = 0.08f;
    private static final float JUMP_VELOCITY  = 1.2f;
    private static final float WALK_SPEED_DEG = 1.0f;
    private static final float CAM_DISTANCE   = 6.0f;

    private float   angleDeg     = 0f;
    private float   cameraYaw    = 0f;  // set by IceSandbox before tick()
    private float   latDeg       = 0f;
    private float   altitude     = 0f;
    private float   vertVelocity = 0f;
    private boolean jumping      = false;

    private boolean moveLeft    = false;
    private boolean moveRight   = false;
    private boolean moveForward = false;
    private boolean moveBack    = false;

    public void setMoveLeft(boolean v)    { moveLeft    = v; }
    public void setMoveRight(boolean v)   { moveRight   = v; }
    public void setMoveForward(boolean v) { moveForward = v; }
    public void setMoveBack(boolean v)    { moveBack    = v; }

    public void requestJump()
    {
        if (!jumping && altitude < 0.01f)
        {
            jumping = true;
            vertVelocity = JUMP_VELOCITY;
        }
    }

    public void setCameraYaw(float yaw) { this.cameraYaw = yaw; }

    public void tick(float planetRadius, float gravityScale)
    {
        float speed = WALK_SPEED_DEG / Math.max(0.5f, planetRadius / 1.5f);

        // Camera-relative movement (Skyrim/Morrowind style)
        // W/S move in camera facing direction, A/D strafe perpendicular
        float yawRad = (float) Math.toRadians(cameraYaw);
        float fwdAngle  =  (float) Math.sin(yawRad);
        float fwdLat    =  (float) Math.cos(yawRad);
        float rightAngle = (float) Math.cos(yawRad);
        float rightLat  = -(float) Math.sin(yawRad);

        if (moveForward) { angleDeg += fwdAngle * speed;  latDeg += fwdLat * speed;   }
        if (moveBack)    { angleDeg -= fwdAngle * speed;  latDeg -= fwdLat * speed;   }
        if (moveRight)   { angleDeg += rightAngle * speed; latDeg += rightLat * speed; }
        if (moveLeft)    { angleDeg -= rightAngle * speed; latDeg -= rightLat * speed; }

        if (angleDeg <    0) angleDeg += 360f;
        if (angleDeg >= 360) angleDeg -= 360f;
        latDeg = Math.max(-89.9f, Math.min(89.9f, latDeg));

        if (jumping || altitude > 0)
        {
            altitude     += vertVelocity;
            vertVelocity -= BASE_GRAVITY * gravityScale;
            if (altitude <= 0)
            {
                altitude = 0; jumping = false; vertVelocity = 0;
            }
        }
    }

    public void writeToWorld(WorldState world, float planetRadius)
    {
        world.playerAngleDeg     = angleDeg;
        world.playerLatDeg       = latDeg;
        world.playerAltitudeFeet = altitude;
        world.isJumping          = jumping;
        world.verticalVelocity   = vertVelocity;

        float r      = planetRadius + altitude;
        float latRad = (float) Math.toRadians(latDeg);
        float angRad = (float) Math.toRadians(angleDeg);

        // Player 3D position
        float px = r * (float)(Math.cos(latRad) * Math.sin(angRad));
        float py = r * (float) Math.sin(latRad);
        float pz = r * (float)(Math.cos(latRad) * Math.cos(angRad));

        // Local frame at surface position
        float outX = (float)(Math.cos(latRad) * Math.sin(angRad));
        float outY = (float) Math.sin(latRad);
        float outZ = (float)(Math.cos(latRad) * Math.cos(angRad));

        float northX = (float)(-Math.sin(latRad) * Math.sin(angRad));
        float northY = (float) Math.cos(latRad);
        float northZ = (float)(-Math.sin(latRad) * Math.cos(angRad));

        float eastX =  (float) Math.cos(angRad);
        float eastY =  0f;
        float eastZ = -(float) Math.sin(angRad);

        // Camera orbit using world.cameraYaw and world.cameraPitch
        float pitchRad   = (float) Math.toRadians(world.cameraPitch);
        float yawRad     = (float) Math.toRadians(world.cameraYaw);
        float horizDist  = CAM_DISTANCE * (float) Math.cos(pitchRad);
        float vertDist   = CAM_DISTANCE * (float) Math.sin(pitchRad);
        float localEast  =  horizDist * (float) Math.sin(yawRad);
        float localNorth = -horizDist * (float) Math.cos(yawRad);

        world.camPos[0] = px + localEast*eastX + localNorth*northX + vertDist*outX;
        world.camPos[1] = py + localEast*eastY + localNorth*northY + vertDist*outY;
        world.camPos[2] = pz + localEast*eastZ + localNorth*northZ + vertDist*outZ;

        float headH = world.playerHeightFeet * 0.35f;
        world.camTarget[0] = px + outX * headH;
        world.camTarget[1] = py + outY * headH;
        world.camTarget[2] = pz + outZ * headH;

        // Avatar companion slightly ahead
        float aheadRad = (float) Math.toRadians(angleDeg + 15f);
        float ar = planetRadius + 0.1f;
        world.avatarPos[0] = ar * (float)(Math.cos(latRad) * Math.sin(aheadRad));
        world.avatarPos[1] = ar * (float) Math.sin(latRad);
        world.avatarPos[2] = ar * (float)(Math.cos(latRad) * Math.cos(aheadRad));
    }

    public float   getAngleDeg() { return angleDeg; }
    public float   getLatDeg()   { return latDeg; }
    public float   getAltitude() { return altitude; }
    public boolean isJumping()   { return jumping; }
}
