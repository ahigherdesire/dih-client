package dihclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Clicks for combat modules that act the moment they decide to, the way a crystal-PvP macro does: the look goes out
 * as its own packet right before the click, and the click is sent now instead of being queued for the next tick's
 * key handling ({@link DihCombatClicker}). The server reads the look from the last movement packet, so a click
 * after {@link #face} lands where it aims even though the player's view never turns.
 */
public final class DihInstantClick {
    private static final Minecraft MC = Minecraft.getInstance();

    private DihInstantClick() {
    }

    /** Tells the server the player looks along {@code rotation}; a no-op when the server already has it. */
    public static void face(DihRotationUtil.Rotation rotation) {
        LocalPlayer player = MC.player;
        if (rotation == null || player == null || MC.getConnection() == null) return;
        DihServerRotationView.WireSnapshot wire = DihServerRotationView.snapshot();
        if (wire.initialized()
            && Math.abs(DihRotationUtil.angleDifference(wire.currentYaw(), rotation.yaw())) <= 0.01F
            && Math.abs(wire.currentPitch() - rotation.pitch()) <= 0.01F) {
            return;
        }
        MC.getConnection().send(new ServerboundMovePlayerPacket.Rot(rotation.yaw(), rotation.pitch(),
            player.onGround(), player.horizontalCollision));
    }

    /** Uses the item in {@code hand} on {@code hit} now, swinging as a real click would. Whether the use took. */
    public static boolean use(BlockHitResult hit, InteractionHand hand) {
        LocalPlayer player = MC.player;
        if (hit == null || hand == null || player == null || MC.gameMode == null) return false;
        InteractionResult result = MC.gameMode.useItemOn(player, hand, hit);
        if (!(result instanceof InteractionResult.Success success)) return false;
        if (success.swingSource() == InteractionResult.SwingSource.CLIENT) DihEntities.swing(player, hand);
        return true;
    }

    /** Attacks {@code target} now with a main-hand swing. */
    public static boolean attack(Entity target) {
        LocalPlayer player = MC.player;
        if (target == null || player == null || MC.gameMode == null || !target.isAlive()) return false;
        MC.gameMode.attack(player, target);
        DihEntities.swing(player, InteractionHand.MAIN_HAND);
        return true;
    }
}
