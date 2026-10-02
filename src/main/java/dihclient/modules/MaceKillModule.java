package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.IntSetting;
import dihclient.util.multi.PacketTeleportController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.AABB;

import java.util.function.IntPredicate;

/**
 * Lands every mace hit as a smash: just before the attack goes out, the server is told the player went up and came
 * back down, so the hit carries that fall. The server checks each move against where the player was at the start of
 * its tick, allowing 100 square blocks for each move packet it has had that tick, five at most: a few rotation packets
 * first raise that to 20 blocks (22 if no other move went out that tick). A smash hit resets the attacker's fall; one
 * the server turns down leaves it, to be taken on landing, so a target still flashing from a hit is left to a normal
 * swing.
 */
public final class MaceKillModule extends Module {
    /** Square blocks of movement the server allows per move packet in one of its ticks. */
    static final double MOVE_ALLOWANCE = 100.0D;
    /** Move packets per server tick that count toward the allowance; past this the count starts over at one. */
    static final int COUNTED_PACKETS = 5;
    /** Below this a fall is no smash. */
    static final int MIN_HEIGHT = (int) Math.ceil(MaceItem.SMASH_ATTACK_FALL_THRESHOLD) + 1;

    public MaceKillModule() {
        super("mace-kill", "MaceKill", ModuleCategory.COMBAT,
            "Fakes a fall before each mace hit so it lands as a smash. Will flag anticheats.");
        add(new IntSetting("fall-height", "Fall Height", 22, MIN_HEIGHT, 22, 1)
            .description("Most blocks of fall to fake; under a roof the lift stops below it."));
        add(new IntSetting("spam-packets", "Spam Packets", 3, 0, COUNTED_PACKETS - 1, 1)
            .description("Rotation packets sent first to raise the server's movement allowance. At 4, a normal move"
                + " already sent that tick makes six and the server turns the lift down."));
        add(new BoolSetting("skip-hurt", "Skip Hurt Targets", true)
            .description("Swing normally at a target still recovering from a hit: a refused smash would leave you the fall."));
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (!(packet instanceof ServerboundAttackPacket attack)) return false;
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.getConnection() == null) return false;
        if (!(player.getMainHandItem().getItem() instanceof MaceItem) || MaceItem.canSmashAttack(player)) return false;
        if (player.isPassenger() || player.isFallFlying() || player.getAbilities().flying) return false;
        Entity target = MC.level.getEntity(attack.entityId());
        if (!(target instanceof LivingEntity living) || !living.isAlive()) return false;
        if (bool("skip-hurt") && living.hurtTime > 0) return false;

        int spam = integer("spam-packets");
        int max = Math.min(integer("fall-height"), allowedHeight(spam));
        AABB box = player.getBoundingBox();
        int height = clearHeight(max, up -> MC.level.noCollision(player, box.move(0.0D, up, 0.0D)));
        if (height < MIN_HEIGHT) return false;

        double x = player.getX(), y = player.getY(), z = player.getZ();
        float yaw = player.getYRot(), pitch = player.getXRot();
        PacketTeleportController.runAtomicClipSend(() -> {
            for (int i = 0; i < spam; i++) {
                MC.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, false, player.horizontalCollision));
            }
            MC.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + height, z, false, false));
            MC.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
            MC.getConnection().send(attack);
        });
        return true;
    }

    /** The highest fall the server lets one move cover after {@code spam} other move packets in the same tick. */
    static int allowedHeight(int spam) {
        int counted = Math.max(1, Math.min(COUNTED_PACKETS, spam + 1));
        return (int) Math.floor(Math.sqrt(MOVE_ALLOWANCE * counted));
    }

    /**
     * The highest whole number of blocks, up to {@code max}, the player can be lifted with every height on the way
     * there clear ({@code clear} tests the box lifted that far); the server moves the player through the gap and turns
     * down a move that runs into a block.
     */
    static int clearHeight(int max, IntPredicate clear) {
        int height = 0;
        while (height < max && clear.test(height + 1)) height++;
        return height;
    }
}
