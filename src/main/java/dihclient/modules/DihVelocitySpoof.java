package dihclient.modules;

import dihclient.util.multi.PacketTeleportController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Makes the server think the player moved fast for one tick without really going anywhere.
 *
 * <p>The server takes a player's speed from the step of the last move packet it accepted ({@code getKnownMovement}).
 * Arrows and tridents add that speed to their own ({@code Projectile.shootFromRotation}) and arrows deal damage in
 * proportion to their speed; spear charges read it as blocks a second ({@code KineticWeapon}). The "moved too
 * quickly" check measures each packet only against where the player stood when the server tick began, allowing 10
 * blocks (17.3 gliding) for every move packet received that tick, up to 5. So stepping back and then forward makes
 * the forward step count as both distances.
 *
 * <p>Every step has to be clear of blocks, or the server's collision stops it and rubber-bands the player.
 */
final class DihVelocitySpoof {
    /** Blocks a move packet may reach from the tick's start position: the server allows the square of it, 100. */
    static final double WALK_REACH = 10.0D;
    /** The same for a gliding player: the server allows 300. */
    static final double GLIDE_REACH = Math.sqrt(300.0D);
    /** Move packets that count towards the allowance in one server tick; more resets it to one packet's worth. */
    static final int MAX_PACKETS = 5;
    /** Kept short of every limit so rounding on the wire never tips a move over. */
    static final double MARGIN = 0.05D;
    /** Distance between collision probes along a step. */
    private static final double PROBE = 0.25D;

    /**
     * The moves of one burst: {@code pads} packets where the player stands (each raises the allowance), a step
     * {@code back} against the direction, then one {@code forward} of the start along it. The last step is
     * {@code back + forward} long.
     */
    record Plan(int pads, double back, double forward) {
        double boost() {
            return back + forward;
        }
    }

    private DihVelocitySpoof() {
    }

    /** How far from the tick's start the {@code n}-th move packet of a tick may go. */
    static double reach(int n, double perPacket) {
        return perPacket * Math.sqrt(Math.max(1, Math.min(n, MAX_PACKETS))) - MARGIN;
    }

    /**
     * A burst that ends where it started, so the boost is the step back: the fewest pads that reach {@code boost},
     * capped by {@code clearBack}, the free room behind.
     */
    static Plan returning(double boost, double clearBack, double perPacket) {
        double want = Math.min(boost, clearBack);
        for (int pads = 0; pads < MAX_PACKETS; pads++) {
            if (reach(pads + 1, perPacket) >= want) return new Plan(pads, Math.max(0.0D, want), 0.0D);
        }
        return new Plan(MAX_PACKETS - 1, reach(MAX_PACKETS, perPacket), 0.0D);
    }

    /**
     * A burst that ends ahead of the start: back then through the start and on. The fewest pads that reach
     * {@code boost}, split evenly where the room ({@code clearBack}, {@code clearAhead}) and the limits allow.
     */
    static Plan through(double boost, double clearBack, double clearAhead, double perPacket) {
        Plan best = new Plan(0, 0.0D, 0.0D);
        for (int pads = 0; pads < MAX_PACKETS - 1; pads++) {
            double backMax = Math.min(clearBack, reach(pads + 1, perPacket));
            double aheadMax = Math.min(clearAhead, reach(pads + 2, perPacket));
            double forward = Math.min(aheadMax, Math.max(boost * 0.5D, boost - backMax));
            double back = Math.min(backMax, boost - forward);
            Plan plan = new Plan(pads, Math.max(0.0D, back), Math.max(0.0D, forward));
            if (plan.boost() > best.boost() + 1.0E-6D) best = plan;
            if (plan.boost() >= boost - 1.0E-6D) return plan;
        }
        return best;
    }

    /** The allowance a packet earns for this player: gliding players get more. */
    static double perPacket(LocalPlayer player) {
        return player.isFallFlying() ? GLIDE_REACH : WALK_REACH;
    }

    /** Whether a burst can work at all: the server ignores a rider's position and has no checks to fool in spectator. */
    static boolean usable(LocalPlayer player) {
        return player != null && !player.isPassenger() && !player.isSpectator() && !player.isSleeping()
            && Minecraft.getInstance().getConnection() != null;
    }

    /** How far, up to {@code max}, the player's box slides from where it stands along {@code dir} without touching a block. */
    static double clearDistance(LocalPlayer player, Vec3 dir, double max) {
        AABB box = player.getBoundingBox();
        double travelled = 0.0D;
        while (travelled < max) {
            double next = Math.min(max, travelled + PROBE);
            if (!player.level().noCollision(player, box.move(dir.scale(next)))) return Math.max(0.0D, travelled - PROBE);
            travelled = next;
        }
        return max;
    }

    /**
     * Sends the burst for {@code plan} from {@code start} along the unit vector {@code dir}, facing {@code yaw},
     * {@code pitch}, then {@code during} (the packet that should see the speed, or null), then, for a plan that ends
     * ahead, the way back. Sprint is paused around it so the steps cost no hunger. A burst that leaves the ground
     * ends with a hop of a hair, which clears the fall distance the steps built up.
     */
    static void send(LocalPlayer player, Vec3 start, Vec3 dir, float yaw, float pitch, Plan plan, Packet<?> during) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        boolean onGround = player.onGround();
        boolean collision = player.horizontalCollision;
        boolean sprinting = player.isSprinting();
        boolean airborne = Math.abs(dir.y) > 1.0E-6D;
        Vec3 back = start.subtract(dir.scale(plan.back()));
        Vec3 ahead = start.add(dir.scale(plan.forward()));
        PacketTeleportController.runAtomicClipSend(() -> {
            if (sprinting) connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            for (int i = 0; i < plan.pads(); i++) connection.send(move(start, yaw, pitch, onGround, collision));
            connection.send(move(back, yaw, pitch, onGround && !airborne, collision));
            connection.send(move(ahead, yaw, pitch, onGround && !airborne, collision));
            if (during != null) connection.send(during);
            if (plan.forward() > 0.0D) connection.send(move(start, yaw, pitch, onGround && !airborne, collision));
            if (airborne) {
                connection.send(move(start.add(0.0D, 0.001D, 0.0D), yaw, pitch, false, collision));
                connection.send(move(start, yaw, pitch, onGround, collision));
            }
            if (sprinting) connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        });
    }

    private static ServerboundMovePlayerPacket move(Vec3 at, float yaw, float pitch, boolean onGround, boolean collision) {
        return new ServerboundMovePlayerPacket.PosRot(at.x, at.y, at.z, yaw, pitch, onGround, collision);
    }
}
