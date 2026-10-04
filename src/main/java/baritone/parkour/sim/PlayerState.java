package baritone.parkour.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Everything one movement tick reads and writes for the local player: position, velocity, the collision flags the
 * client keeps between ticks, the supporting block, fall distance, the jump cooldown, sprinting and crouching, and
 * last tick's keys (the client checks some of them before it reads the new ones). Mutable; {@link #copy()} for search.
 */
public final class PlayerState {
    public double x;
    public double y;
    public double z;
    public double vx;
    public double vy;
    public double vz;
    public float yaw;

    public boolean onGround;
    public boolean horizontalCollision;
    public boolean minorHorizontalCollision;
    public boolean verticalCollision;
    public boolean sprinting;
    /** The client's crouch flag, set from last tick's sneak key at the start of the tick. */
    public boolean crouching;
    /** The pose at the end of the last tick: it sets the bounding box height. */
    public boolean crouchPose;
    public double fallDistance;
    public int noJumpDelay;

    /** The block the player stands on, as the client tracks it, or null. */
    public BlockPos support;
    public boolean onGroundNoBlocks;

    public boolean prevJump;
    public boolean prevSneak;
    public float prevForwardImpulse;

    public PlayerState() {
    }

    /** A player standing still at {@code (x, y, z)} on the ground (with the downward velocity a resting player has). */
    public static PlayerState standing(double x, double y, double z, float yaw) {
        PlayerState state = new PlayerState();
        state.x = x;
        state.y = y;
        state.z = z;
        state.yaw = yaw;
        state.onGround = true;
        state.vy = PlayerPhysics.RESTING_VY;
        return state;
    }

    public PlayerState copy() {
        PlayerState c = new PlayerState();
        c.x = x;
        c.y = y;
        c.z = z;
        c.vx = vx;
        c.vy = vy;
        c.vz = vz;
        c.yaw = yaw;
        c.onGround = onGround;
        c.horizontalCollision = horizontalCollision;
        c.minorHorizontalCollision = minorHorizontalCollision;
        c.verticalCollision = verticalCollision;
        c.sprinting = sprinting;
        c.crouching = crouching;
        c.crouchPose = crouchPose;
        c.fallDistance = fallDistance;
        c.noJumpDelay = noJumpDelay;
        c.support = support;
        c.onGroundNoBlocks = onGroundNoBlocks;
        c.prevJump = prevJump;
        c.prevSneak = prevSneak;
        c.prevForwardImpulse = prevForwardImpulse;
        return c;
    }

    /** The bounding box at the current pose: 0.6 wide, 1.8 tall standing, 1.5 crouching (float sizes, as the game). */
    public AABB box() {
        return PlayerPhysics.box(x, y, z, crouchPose);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "pos(%.5f %.5f %.5f) vel(%.5f %.5f %.5f)%s%s%s", x, y, z, vx, vy, vz,
            onGround ? " ground" : "", sprinting ? " sprint" : "", horizontalCollision ? " hcol" : "");
    }
}
