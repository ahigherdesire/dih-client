package dihclient.mixin;

import dihclient.commands.DihCommands;
import dihclient.modules.DihModule;
import dihclient.modules.InventoryTweaksModule;
import dihclient.modules.PackHideState;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihSharedState;
import dihclient.util.macro.MacroConditionRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class DihClientPlayNetworkHandlerMixin {
    @Inject(method = "handleBlockChangedAck", at = @At("RETURN"))
    private void dih$onBlockPredictionAckApplied(
        ClientboundBlockChangedAckPacket packet, CallbackInfo ci
    ) {
        dihclient.modules.ScaffoldModule.onBlockChangedAckHandled(packet.sequence());
    }

    @Inject(method = "handleBlockUpdate", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V",
        shift = At.Shift.BEFORE))
    private void dih$observeSingleBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        dihclient.modules.AntiVanishModule.observeSingleBlockUpdate(packet);
    }

    @Unique private boolean dih$viewCaptured;
    @Unique private float dih$viewYaw;
    @Unique private float dih$viewPitch;
    @Unique private float dih$viewYawO;
    @Unique private float dih$viewPitchO;

    @Unique
    private void dih$captureView() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var wire = dihclient.modules.ScaffoldModule.wireContinuityRotation();
        if (wire == null) return;
        dih$viewYaw = mc.player.getYRot();
        dih$viewPitch = mc.player.getXRot();
        dih$viewYawO = mc.player.yRotO;
        dih$viewPitchO = mc.player.xRotO;
        dih$viewCaptured = true;

        mc.player.setYRot(wire.yaw());
        mc.player.setXRot(wire.pitch());
    }

    @Unique
    private void dih$restoreView(Minecraft mc) {
        if (!dih$viewCaptured) return;
        dih$viewCaptured = false;
        if (mc.player == null) return;

        dihclient.modules.ScaffoldModule.onServerRotationApplied(
            mc.player.getYRot(), mc.player.getXRot());
        mc.player.setYRot(dih$viewYaw);
        mc.player.setXRot(dih$viewPitch);
        mc.player.yRotO = dih$viewYawO;
        mc.player.xRotO = dih$viewPitchO;
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void dih$disarmViewCaptureMove(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        dih$viewCaptured = false;
    }

    @Inject(method = "handleRotatePlayer", at = @At("HEAD"))
    private void dih$disarmViewCaptureRotate(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        dih$viewCaptured = false;
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z"))
    private void dih$captureViewBeforeTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        dih$captureView();
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void dih$onServerPositionCorrection(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        dihclient.util.SodiumTerrainPassGuard.armForPositionCorrection();
        Minecraft mc = Minecraft.getInstance();
        dih$restoreView(mc);
        if (mc.player != null) {
            dihclient.util.multi.PacketTeleportController.onMainCorrection(mc.player.position());
            dihclient.modules.ScaffoldModule.onServerPositionCorrection();
        }
    }

    @Inject(method = "handleRotatePlayer", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/player/Player;setYRot(F)V"))
    private void dih$captureViewBeforeRotate(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        dih$captureView();
    }

    @Inject(method = "handleRotatePlayer", at = @At("RETURN"))
    private void dih$onServerRotationCorrection(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        dih$restoreView(Minecraft.getInstance());
    }

    @Inject(method = "handleMoveVehicle", at = @At("RETURN"))
    private void dih$onServerVehicleCorrection(ClientboundMoveVehiclePacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getVehicle() != null) {
            dihclient.util.multi.PacketTeleportController.onMainVehicleCorrection(
                mc.player.getVehicle().position());
        }
    }

    @Inject(method = "handleCommands", at = @At("RETURN"))
    private void dih$onCommandTreeApplied(ClientboundCommandsPacket packet, CallbackInfo ci) {
        DihModule module = DihModule.get();
        if (module == null) return;
        var overlay = module.getServerDataOverlayIfExists();
        if (overlay != null) overlay.onCommandTreeChanged();
    }

    @Inject(method = "sendUnattendedCommand", at = @At("HEAD"), cancellable = true)
    private void dih$interceptCardClick(String command, net.minecraft.client.gui.screens.Screen screen, CallbackInfo ci) {

        if (dihclient.util.DihLiteVariant.enabled()) return;
        if (dihclient.util.mm.MmCardActions.handleClickCommand(command)) ci.cancel();
    }

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void dih$infiniChatSplit(String message, CallbackInfo ci) {
        if (!dihclient.util.DihConfig.getGlobal().infiniChat
            || message == null || message.length() <= 256
            || (!DihCommands.plainChatBypass() && DihCommands.isDihCommandMessage(message))) return;
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        for (int i = 0; i < message.length(); i += 256) {
            self.sendChat(message.substring(i, Math.min(message.length(), i + 256)));
        }
        ci.cancel();
    }

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void dih$dispatchDihCommand(String message, CallbackInfo ci) {
        try {

            if (DihCommands.plainChatBypass()) return;
            if (!DihCommands.isDihCommandMessage(message)) return;
            if (DihCommands.isBlockedPanicCommandMessage(message)) {
                ci.cancel();
                return;
            }
            String body = DihCommands.commandBody(message);
            if (body.isBlank()) {
                ci.cancel();
                return;
            }

            dihclient.util.DihClientMessaging.rememberRecentChat(message);
            DihCommands.dispatch(body);
            ci.cancel();
        } catch (Throwable t) {

            dihclient.DihClientAddon.LOG.warn("[Commands] sendChat interception failed for '{}'", message, t);
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void yang$onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        MacroConditionRegistry.recordInventorySync();
        boolean macroWaits = MacroConditionRegistry.hasPendingInventoryConditions();
        boolean inventoryTweaks = InventoryTweaksModule.hasContainerSyncWork();
        if (!macroWaits && !inventoryTweaks) return;
        if (macroWaits) MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
        if (inventoryTweaks) InventoryTweaksModule.onContainerSynced(packet.containerId());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void yang$onSlotUpdate(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        MacroConditionRegistry.recordInventorySync();
        boolean macroWaits = MacroConditionRegistry.hasPendingInventoryConditions();
        boolean inventoryTweaks = InventoryTweaksModule.hasContainerSyncWork();
        if (!macroWaits && !inventoryTweaks) return;
        if (macroWaits) MacroConditionRegistry.onSlotUpdate(packet.getSlot());
        if (inventoryTweaks) InventoryTweaksModule.onContainerSynced(packet.getContainerId());
    }

    @Inject(method = "handleSetCursorItem", at = @At("RETURN"))
    private void dih$onSetCursorItem(ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        MacroConditionRegistry.recordInventorySync();
        if (MacroConditionRegistry.hasPendingInventoryConditions()) {
            MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
        }
    }

    @Inject(method = "handleSetPlayerInventory", at = @At("RETURN"))
    private void dih$onSetPlayerInventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        MacroConditionRegistry.recordInventorySync();
        if (MacroConditionRegistry.hasPendingInventoryConditions()) {
            MacroConditionRegistry.onInventorySync(Minecraft.getInstance());
        }
    }

    @Inject(method = "handleSoundEvent", at = @At("RETURN"))
    private void yang$onPlaySound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        boolean macroWaits = MacroConditionRegistry.hasPendingSoundConditions();
        boolean moduleHooks = dihclient.util.DihRuntimeActivity.has(dihclient.util.DihRuntimeActivity.SOUND);
        if (!macroWaits && !moduleHooks) return;
        if (macroWaits) dih$dispatchMacroSound(packet);
        if (moduleHooks) ModuleRegistry.onSoundPacket(packet);
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("RETURN"))
    private void dih$onPlayEntitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        if (!MacroConditionRegistry.hasPendingSoundConditions()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.level == null || packet == null) return;
            Entity entity = mc.level.getEntity(packet.getId());
            if (entity == null) return;
            String soundId = packet.getSound().value().location().toString();
            MacroConditionRegistry.onSoundPacket(soundId, entity.getX(), entity.getY(), entity.getZ());
        } catch (Exception ignored) {  }
    }

    @Inject(method = "handleSetTime", at = @At("RETURN"))
    private void yang$onWorldTimeUpdate(ClientboundSetTimePacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        if (!dih$packetHooksActive()) return;
        DihSharedState.get().onServerTimeSyncReceived();
    }

    @Unique
    private boolean dih$packetHooksActive() {
        DihModule module = DihModule.get();
        return module != null && module.arePacketHooksActive();
    }

    @Unique
    private static void dih$dispatchMacroSound(ClientboundSoundPacket packet) {
        try {
            if (packet == null) return;
            String soundId = packet.getSound().value().location().toString();
            MacroConditionRegistry.onSoundPacket(soundId, packet.getX(), packet.getY(), packet.getZ());
        } catch (Exception ignored) {  }
    }
}
