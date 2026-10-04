package dihclient.modules;

import dihclient.api.module.ChoiceSetting;
import dihclient.util.DihPackets;
import dihclient.util.multi.PacketTeleportController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.MaceItem;

/**
 * Turns hits made on the ground into critical hits. The server grants a critical when the attacker is falling
 * (fall distance above zero, not on the ground), not sprinting, not climbing, swimming, riding or blind.
 * <ul>
 *   <li><b>Packet</b> tells the server the player hopped 1/16 of a block and came back down just before the hit,
 *   pausing sprint around it. Every hit crits at once. Anticheats flag it.</li>
 *   <li><b>Jump</b> holds the hit, really jumps, and lets the hit go on the way down. Plain vanilla movement, so it
 *   is safe anywhere, but each grounded hit lands about half a second later.</li>
 * </ul>
 */
public final class CriticalsModule extends Module {
    /** The hop the Packet mode reports; any rise then fall gives the server a fall distance above zero. */
    static final double HOP = 0.0625D;
    /** A held Jump-mode hit goes out anyway after this many ticks. */
    static final int MAX_HOLD_TICKS = 14;

    private ServerboundAttackPacket heldAttack;
    /** The swing that went with the held hit: the server resets the attack charge on a swing, so it waits too. */
    private Packet<?> heldSwing;
    private boolean holdingSwing;
    private int heldTicks;

    public CriticalsModule() {
        super("criticals", "Criticals", ModuleCategory.COMBAT,
            "Turns hits on the ground into critical hits. Packet mode will flag anticheats.");
        add(new ChoiceSetting("mode", "Mode", "Packet", "Packet", "Jump")
            .description("Packet: every hit crits at once (anticheats flag it). Jump: really jump and hit on the way down."));
    }

    @Override
    public void onDisable() {
        releaseHeld();
    }

    @Override
    public void onGameLeft() {
        heldAttack = null;
        heldSwing = null;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (DihPackets.isSwing(packet) && holdingSwing && heldAttack != null && heldSwing == null) {
            heldSwing = packet;
            return true;
        }
        if (!(packet instanceof ServerboundAttackPacket attack)) return false;
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.getConnection() == null) return false;
        Entity target = MC.level.getEntity(attack.entityId());
        if (!(target instanceof LivingEntity living) || !living.isAlive()) return false;
        if (!canCritFromGround(player)) return false;
        if (player.getMainHandItem().getItem() instanceof MaceItem && maceKillActive()) return false;

        if ("Jump".equals(choice("mode"))) {
            if (heldAttack != null) return false;
            heldAttack = attack;
            heldSwing = null;
            holdingSwing = true;
            heldTicks = 0;
            player.jumpFromGround();
            return true;
        }

        double x = player.getX(), y = player.getY(), z = player.getZ();
        boolean sprinting = player.isSprinting();
        boolean collision = player.horizontalCollision;
        PacketTeleportController.runAtomicClipSend(() -> {
            if (sprinting) MC.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            MC.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y + HOP, z, false, collision));
            MC.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, collision));
            MC.getConnection().send(attack);
            if (sprinting) MC.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        });
        return true;
    }

    @Override
    public void tick() {
        holdingSwing = false;
        if (heldAttack == null) return;
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null) {
            heldAttack = null;
            return;
        }
        heldTicks++;
        Entity target = MC.level.getEntity(heldAttack.entityId());
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            heldAttack = null;
            Packet<?> swing = heldSwing;
            heldSwing = null;
            if (swing != null && MC.getConnection() != null) PacketTeleportController.runAtomicClipSend(() -> MC.getConnection().send(swing));
            return;
        }
        if (fallingForCrit(player.onGround(), player.fallDistance, player.getDeltaMovement().y) || heldTicks >= MAX_HOLD_TICKS
            || player.onGround() && heldTicks > 2) {
            releaseHeld();
        }
    }

    private void releaseHeld() {
        ServerboundAttackPacket attack = heldAttack;
        Packet<?> swing = heldSwing;
        heldAttack = null;
        heldSwing = null;
        if (attack == null || MC.getConnection() == null || MC.player == null) return;
        LocalPlayer player = MC.player;
        boolean sprinting = player.isSprinting();
        PacketTeleportController.runAtomicClipSend(() -> {
            if (sprinting) MC.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            MC.getConnection().send(attack);
            if (swing != null) MC.getConnection().send(swing);
            if (sprinting) MC.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        });
    }

    /** Whether a hit from where the player stands could be turned into a crit (on the ground, nothing else blocking it). */
    private static boolean canCritFromGround(LocalPlayer player) {
        return player.onGround()
            && !player.onClimbable()
            && !player.isInWater()
            && !player.isInLava()
            && !player.isPassenger()
            && !player.isFallFlying()
            && !player.getAbilities().flying
            && !player.hasEffect(MobEffects.BLINDNESS)
            && !player.hasEffect(MobEffects.LEVITATION);
    }

    /** The point of a real jump where a hit counts as a critical: in the air and coming down. */
    static boolean fallingForCrit(boolean onGround, double fallDistance, double motionY) {
        return !onGround && fallDistance > 0.0D && motionY < 0.0D;
    }

    private static boolean maceKillActive() {
        Module mace = ModuleRegistry.get("mace-kill");
        return mace != null && mace.isEnabled();
    }
}
