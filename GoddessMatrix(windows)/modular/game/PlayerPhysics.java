package modular.game;

/*
 * PlayerPhysics.java
 *
 * Merged spherical movement and Geordi-glasses camera physics.
 *
 * Contributors:
 *   Gemini (Google)        — original physics concept
 *   Derek Jason Gilhousen — per-planet gravity, spherical walking design,
 *                           Geordi-glasses camera model
 *   Claude (Anthropic)    — prior 3D position/camera implementation passes
 *   ChatGPT (OpenAI)      — merge stabilization, typo/scope repairs,
 *                           compatibility between old/new physics APIs
 *
 * Merged spherical movement and Geordi-glasses camera physics.
 * Now features dynamic Ray-Sphere terrain collision detection.
 */

public class PlayerPhysics
{
    private static final float BASE_GRAVITY = 0.08f;
    private static final float JUMP_VELOCITY = 1.2f;
    private static final float WALK_SPEED_DEG = 1.0f;

    private float angleDeg = 0f, latDeg = 0f, altitude = 0f, vertVelocity = 0f, cameraYaw = 0f;
    private float absoluteRadius = 0f;
    // Direction the body is physically facing — updated when WASD moves.
    // Separate from cameraYaw (where eyes point) and angleDeg (sphere position).
    private float bodyFacingYaw = 0f;
    private boolean jumping = false;
    private boolean moveLeft = false, moveRight = false, moveForward = false, moveBack = false;

    public void setMoveLeft(boolean v) { moveLeft = v; }
    public void setMoveRight(boolean v) { moveRight = v; }
    public void setMoveForward(boolean v) { moveForward = v; }
    public void setMoveBack(boolean v) { moveBack = v; }
    public void setCameraYaw(float yaw) { cameraYaw = yaw; }

    public void setSpawnPosition(float latitude, float longitude)
    {
        latDeg = Math.max(-89.9f, Math.min(89.9f, latitude));
        angleDeg = longitude;
        while (angleDeg < 0f) angleDeg += 360f;
        while (angleDeg >= 360f) angleDeg -= 360f;
    }

    public void requestJump()
    {
        if (!jumping) { jumping = true; vertVelocity = JUMP_VELOCITY; }
    }

    /**
     * Dynamically calculates the highest terrain point at the player's current lat/lon.
     * Raycasts from the core outward to intersect any TerrainSpheres.
     */
    public float calculateTerrainRadius(WorldState world, float coreRadius)
    {
        WorldState.SystemPlanet planet = world.planets[world.activePlanet];
        if (planet == null || planet.terrain == null || planet.terrain.isEmpty()) return coreRadius;

        float latRad = (float) Math.toRadians(latDeg);
        float lonRad = (float) Math.toRadians(angleDeg);

        // Normalized outward vector at player's coordinates
        float vx = (float)(Math.cos(latRad) * Math.sin(lonRad));
        float vy = (float) Math.sin(latRad);
        float vz = (float)(Math.cos(latRad) * Math.cos(lonRad));

        float maxRadius = coreRadius;

        for (WorldState.TerrainSphere s : planet.terrain)
        {
            if (s.materialType == WorldState.MAT_VOID) continue;
            
            // Ray-Sphere Intersection Math
            float tc = s.x * vx + s.y * vy + s.z * vz;
            if (tc < 0) continue; // Sphere is behind the core relative to player

            float d2 = (s.x*s.x + s.y*s.y + s.z*s.z) - (tc*tc);
            float r2 = s.radius * s.radius;

            if (d2 <= r2) {
                // Ray hits the sphere. Calculate the highest protruding point.
                float hitRadius = tc + (float)Math.sqrt(r2 - d2);
                if (hitRadius > maxRadius) {
                    maxRadius = hitRadius;
                }
            }
        }
        return maxRadius;
    }

    public void tick(float localFloorRadius, float gravityScale)
    {
        float speed = WALK_SPEED_DEG / Math.max(0.5f, localFloorRadius / 1.5f);
        float yawRad = (float)Math.toRadians(cameraYaw);
        float fwdAngle = (float)Math.sin(yawRad), fwdLat = (float)Math.cos(yawRad);
        float rightAngle = (float)Math.cos(yawRad), rightLat = -(float)Math.sin(yawRad);

        boolean moving = false;
        if (moveForward) { angleDeg += fwdAngle * speed; latDeg += fwdLat * speed; moving = true; }
        if (moveBack)    { angleDeg -= fwdAngle * speed; latDeg -= fwdLat * speed; moving = true; }
        if (moveRight)   { angleDeg += rightAngle * speed; latDeg += rightLat * speed; moving = true; }
        if (moveLeft)    { angleDeg -= rightAngle * speed; latDeg -= rightLat * speed; moving = true; }
        // When walking, body facing snaps to the movement direction.
        // When still, body facing holds its last value.
        if (moving) bodyFacingYaw = cameraYaw;
        wrapClampSurface();

        // Initialize absolute radius on first frame
        if (absoluteRadius < 0.1f) {
            absoluteRadius = localFloorRadius;
        }

        // Apply vertical momentum
        absoluteRadius += vertVelocity;

        // Collision detection against the dynamic terrain floor
        if (absoluteRadius > localFloorRadius)
        {
            // In the air: apply gravity
            vertVelocity -= BASE_GRAVITY * gravityScale;
            jumping = true;
        }
        else
        {
            // Hit the ground (acts as an automatic stair-stepper for rising terrain)
            absoluteRadius = localFloorRadius;
            vertVelocity = 0f;
            jumping = false;
        }

        altitude = absoluteRadius - localFloorRadius;
    }

    private void wrapClampSurface()
    {
        while (angleDeg < 0f) angleDeg += 360f;
        while (angleDeg >= 360f) angleDeg -= 360f;
        latDeg = Math.max(-89.9f, Math.min(89.9f, latDeg));
    }

    public void writeToWorld(WorldState world, float floorRadius)
    {
        world.playerAngleDeg  = angleDeg;
        world.playerLatDeg    = latDeg;
        world.playerAltitudeFeet = altitude;
        world.isJumping       = jumping;
        world.verticalVelocity = vertVelocity;
        // Body facing — used by renderer to orient the avatar torso.
        // Head yaw relative to body = cameraYaw - bodyFacingYaw.
        // This is what AstridHeadYaw should represent.
        world.bodyFacingYaw   = bodyFacingYaw;
        world.AstridHeadYaw   = cameraYaw - bodyFacingYaw;
        world.headYaw         = world.AstridHeadYaw;

        // Use the absolute radius directly for exact visual placement!
        float r = absoluteRadius; 
        float latRad = (float)Math.toRadians(latDeg), angRad = (float)Math.toRadians(angleDeg);
        float px = r * (float)(Math.cos(latRad) * Math.sin(angRad));
        float py = r * (float)Math.sin(latRad);
        float pz = r * (float)(Math.cos(latRad) * Math.cos(angRad));
        world.playerPos[0] = px; world.playerPos[1] = py; world.playerPos[2] = pz;

        float outX = (float)(Math.cos(latRad) * Math.sin(angRad));
        float outY = (float)Math.sin(latRad);
        float outZ = (float)(Math.cos(latRad) * Math.cos(angRad));
        float northX = (float)(-Math.sin(latRad) * Math.sin(angRad));
        float northY = (float)Math.cos(latRad);
        float northZ = (float)(-Math.sin(latRad) * Math.cos(angRad));
        float eastX = (float)Math.cos(angRad), eastY = 0f, eastZ = -(float)Math.sin(angRad);

        float headH = world.playerHeightFeet;
        float headX = px + outX * headH, headY = py + outY * headH, headZ = pz + outZ * headH;
        float pitchRad = (float)Math.toRadians(world.cameraPitch), yawRad = (float)Math.toRadians(world.cameraYaw);
        float viewLocalEast = (float)(Math.cos(pitchRad) * Math.sin(yawRad));
        float viewLocalOut = (float)Math.sin(pitchRad);
        float viewLocalNorth = (float)(Math.cos(pitchRad) * Math.cos(yawRad));
        float viewWorldX = viewLocalEast * eastX + viewLocalNorth * northX + viewLocalOut * outX;
        float viewWorldY = viewLocalEast * eastY + viewLocalNorth * northY + viewLocalOut * outY;
        float viewWorldZ = viewLocalEast * eastZ + viewLocalNorth * northZ + viewLocalOut * outZ;

        if (world.isFirstPerson)
        {
            world.camPos[0] = headX; world.camPos[1] = headY; world.camPos[2] = headZ;
            world.camTarget[0] = headX + viewWorldX * 100f;
            world.camTarget[1] = headY + viewWorldY * 100f;
            world.camTarget[2] = headZ + viewWorldZ * 100f;
        }
        else
        {
            float phoneDistance = 2.5f;
            world.camPos[0] = headX + viewWorldX * phoneDistance;
            world.camPos[1] = headY + viewWorldY * phoneDistance;
            world.camPos[2] = headZ + viewWorldZ * phoneDistance;
            world.camTarget[0] = headX;
            world.camTarget[1] = headY;
            world.camTarget[2] = headZ;
        }

        // Avatar Companion also snaps to the terrain
        float aheadRad = (float)Math.toRadians(angleDeg + 15f);
        float ar = absoluteRadius + 0.1f; 
        world.avatarAngleDeg = angleDeg + 15f; world.avatarLatDeg = latDeg;
        world.avatarPos[0] = ar * (float)(Math.cos(latRad) * Math.sin(aheadRad));
        world.avatarPos[1] = ar * (float)Math.sin(latRad);
        world.avatarPos[2] = ar * (float)(Math.cos(latRad) * Math.cos(aheadRad));
    }

    public float getAngleDeg() { return angleDeg; }
    public float getLatDeg() { return latDeg; }
    public float getAltitude() { return altitude; }
    public boolean isJumping() { return jumping; }
}
