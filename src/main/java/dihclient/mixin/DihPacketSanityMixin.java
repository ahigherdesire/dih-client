package dihclient.mixin;

import dihclient.util.DihPackets;
import dihclient.security.DihNumericSanity;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class DihPacketSanityMixin {

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$saneSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (DihNumericSanity.motionOutOfRange(packet.movement())) ci.cancel();
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$saneExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        if (DihNumericSanity.outOfRange(packet.center())
            || DihNumericSanity.outOfRange(packet.radius())
            || (packet.playerKnockback().isPresent()
                && DihNumericSanity.motionOutOfRange(packet.playerKnockback().get()))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$saneMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (DihNumericSanity.positionMoveOutOfRange(packet.change())) ci.cancel();
    }

    @Inject(method = "handleTeleportEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$saneTeleportEntity(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        if (DihNumericSanity.positionMoveOutOfRange(packet.change())) ci.cancel();
    }

    @Inject(method = "handleEntityPositionSync", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$saneEntityPositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        net.minecraft.world.phys.Vec3 position = DihPackets.position(packet);
        net.minecraft.world.phys.Vec3 movement = DihPackets.movement(packet);
        if ((position != null && DihNumericSanity.outOfRange(position))
            || (movement != null && DihNumericSanity.motionOutOfRange(movement))
            || DihNumericSanity.outOfRange(DihPackets.yRot(packet))
            || DihNumericSanity.outOfRange(DihPackets.xRot(packet))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleAddEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$saneAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (DihNumericSanity.outOfRange(packet.getX())
            || DihNumericSanity.outOfRange(packet.getY())
            || DihNumericSanity.outOfRange(packet.getZ())
            || DihNumericSanity.motionOutOfRange(packet.getMovement())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$saneParticles(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (DihNumericSanity.outOfRange(DihPackets.x(packet))
            || DihNumericSanity.outOfRange(DihPackets.y(packet))
            || DihNumericSanity.outOfRange(DihPackets.z(packet))
            || DihNumericSanity.outOfRange(DihPackets.xDist(packet))
            || DihNumericSanity.outOfRange(DihPackets.yDist(packet))
            || DihNumericSanity.outOfRange(DihPackets.zDist(packet))
            || DihNumericSanity.outOfRange(DihPackets.maxSpeed(packet))
            || DihPackets.count(packet) > 100_000) {
            ci.cancel();
        }
    }

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$saneMoveVehicle(net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (DihNumericSanity.outOfRange(DihPackets.position(packet))
            || DihNumericSanity.outOfRange(DihPackets.yRot(packet))
            || DihNumericSanity.outOfRange(DihPackets.xRot(packet))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleAddObjective", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$saneObjective(net.minecraft.network.protocol.game.ClientboundSetObjectivePacket packet, CallbackInfo ci) {
        if (!dihclient.security.DihComponentSanity.isSafe(packet.getDisplayName())
            || (packet.getNumberFormat().isPresent()
                && !dihclient.security.DihComponentSanity.isSafe(packet.getNumberFormat().get()))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSetScore", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$saneScore(net.minecraft.network.protocol.game.ClientboundSetScorePacket packet, CallbackInfo ci) {
        if ((packet.display().isPresent()
                && !dihclient.security.DihComponentSanity.isSafe(packet.display().get()))
            || (packet.numberFormat().isPresent()
                && !dihclient.security.DihComponentSanity.isSafe(packet.numberFormat().get()))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSetPlayerTeamPacket", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$saneTeam(net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket packet, CallbackInfo ci) {
        if (packet.getParameters().isEmpty()) return;
        net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket.Parameters parameters = packet.getParameters().get();
        if (!dihclient.security.DihComponentSanity.isSafe(parameters.displayName())
            || !dihclient.security.DihComponentSanity.isSafe(parameters.playerPrefix())
            || !dihclient.security.DihComponentSanity.isSafe(parameters.playerSuffix())) {
            ci.cancel();
        }
    }
}
