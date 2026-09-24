package dihclient.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dihclient.mixin.accessor.DihClientConnectionAccessor;
import dihclient.modules.DihModule;
import dihclient.modules.PackHideState;
import dihclient.modules.ModuleRegistry;
import dihclient.modules.ScaffoldModule;
import dihclient.security.DihProtectorPackStrip;
import dihclient.security.DihResourcePackTruthGuard;
import dihclient.security.DihSpoofPayloadFilter;
import dihclient.util.macro.MacroExecutor;
import dihclient.util.macro.PacketGateManager;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihContainerHold;
import dihclient.util.DihContainerTarget;
import dihclient.util.DihSharedState;
import dihclient.util.DihServerRotationView;
import dihclient.DihClientAddon;
import dihclient.util.multi.MultiConnectionContext;
import dihclient.util.multi.MultiConnectionMarker;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.client.Minecraft;

@Mixin(Connection.class)
public abstract class DihClientConnectionMixin implements MultiConnectionMarker {
    @Unique
    private static final boolean DIH_PACKET_TRACE = Boolean.getBoolean("dih.packet.trace");

    @Unique
    private volatile boolean dih$spoofPipelineInstalled;

    @Unique
    private volatile boolean dih$multiManaged;

    @Unique
    private volatile MultiConnectionContext.ProxySpec dih$multiProxy;

    @Unique
    private volatile PacketListener dih$protocolHintListener;

    @Unique
    private volatile String dih$protocolHintCache = "";

    @Unique
    private static final String DIH_SPOOF_FILTER = "dih_spoof_filter";

    @Unique private static volatile dihclient.modules.Module dih$noFallCached;
    @Unique private static volatile int dih$noFallRevision = -1;

    @Unique
    private static dihclient.modules.Module dih$noFallModule() {
        int revision = dihclient.modules.ModuleRegistry.revision();
        if (revision != dih$noFallRevision) {
            dih$noFallCached = dihclient.modules.ModuleRegistry.get("no-fall");
            dih$noFallRevision = revision;
        }
        return dih$noFallCached;
    }

    @Unique
    private boolean dih$isMultiConnectionOrChannel() {

        return dih$multiManaged;
    }

    @Override
    public boolean dih$isMultiManaged() {
        return dih$multiManaged;
    }

    @Override
    public MultiConnectionContext.ProxySpec dih$multiProxy() {
        return dih$multiProxy;
    }

    @Override
    public void dih$setMultiManaged(MultiConnectionContext.ProxySpec proxy) {
        dih$multiProxy = proxy;
        dih$multiManaged = true;
    }

    @Override
    public void dih$clearMultiManaged() {
        dih$multiManaged = false;
        dih$multiProxy = null;
    }

    @Inject(method = "channelActive", at = @At("HEAD"))
    private void dih$onChannelActive(ChannelHandlerContext context, CallbackInfo ci) {
        dih$spoofPipelineInstalled = false;
        MultiConnectionContext.bindChannel((Connection) (Object) this, context.channel());
        if (dih$isMultiConnectionOrChannel()) return;

        dih$ensureSpoofPipelineFilter();
    }

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void dih$onChannelInactive(ChannelHandlerContext context, CallbackInfo ci) {
        dih$spoofPipelineInstalled = false;
        dihclient.util.DihNetworkCaptureState.clearCodecSuppression();
        if (dih$isMultiConnectionOrChannel()) {
            MultiConnectionContext.unbindChannel(context.channel());

            return;
        }

        if (!(((Connection) (Object) this).getPacketListener() instanceof ClientGamePacketListener)) {
            return;
        }
        DihServerRotationView.reset();
        ScaffoldModule.onConnectionClosed();
        DihProtectorPackStrip.clearAll();
        DihResourcePackTruthGuard.clearAll();
    }

    @Inject(method = "doSendPacket", at = @At("HEAD"))
    private void dih$recordWrittenServerRotation(
        Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci
    ) {
        if (!dih$isMultiConnectionOrChannel()) {
            DihServerRotationView.onPacketWritten(packet);
            ScaffoldModule.onFinalPacketWritten(packet);
        }
    }

    @Inject(method = "configurePacketHandler", at = @At("TAIL"))
    private void dih$onConfigurePacketHandler(ChannelPipeline pipeline, CallbackInfo ci) {
        if (dih$isMultiConnectionOrChannel()) return;
        dih$ensureSpoofPipelineFilter();
    }

    @ModifyVariable(
        method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0
    )
    private Packet<?> dih$silentUseItemRotation(Packet<?> packet) {
        if (PackHideState.isHardLocked()) return packet;
        if (dih$isMultiConnectionOrChannel()
            || !(packet instanceof ServerboundUseItemPacket usePacket)) return packet;
        float yaw = usePacket.getYRot();
        float pitch = usePacket.getXRot();
        if (dihclient.util.DihInputClicker.isFastExpUseInProgress()) {
            yaw = dihclient.modules.BuiltinModules.manualFastExpUseYaw(yaw);
            pitch = dihclient.modules.BuiltinModules.manualFastExpUsePitch(pitch);
        } else {
            dihclient.util.DihRotationUtil.Rotation rotation =
                dihclient.util.DihSilentAim.activeUseItemRotation(Minecraft.getInstance().player);
            if (rotation != null) {
                yaw = rotation.yaw();
                pitch = rotation.pitch();
            }
        }
        if (Float.compare(yaw, usePacket.getYRot()) == 0
            && Float.compare(pitch, usePacket.getXRot()) == 0) return packet;
        return new ServerboundUseItemPacket(
            usePacket.getHand(), usePacket.getSequence(), yaw, pitch);
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void yang$onSendPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {

        if (dih$isMultiConnectionOrChannel()) return;

        ScaffoldModule.onPacketQueued(packet);
        dih$trackSentMessage(packet);
        dih$ensureSpoofPipelineFilter();
        if (packet instanceof ServerboundResourcePackPacket resourcePackPacket) {
            if (DihResourcePackTruthGuard.shouldCancelOutboundStatus(resourcePackPacket)) {
                ci.cancel();
                return;
            }
            DihProtectorPackStrip.onPackFinalResponse(resourcePackPacket.id(), resourcePackPacket.action());
        }
        PacketListener packetListener = ((Connection) (Object) this).getPacketListener();
        DihModule module = DihModule.get();
        DihModule.PacketHookSnapshot hooks = module == null
            ? DihModule.PacketHookSnapshot.inactive()
            : module.packetHookSnapshot(isPlayConnectionActive());
        boolean normalLoggerPath = hooks.normalPath();
        String protocolHint = hooks.packetLoggerCapturing() || hooks.pluginDiscoveryObservation()
            ? dih$protocolHint(packetListener)
            : "";
        if (packet instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket) {
            if (DihSpoofPayloadFilter.shouldBlockForVanillaSpoof(module, packet)) {
                ci.cancel();
                return;
            }
            if (DihSpoofPayloadFilter.shouldDropForProtector(packet)) {
                ci.cancel();
                return;
            }
        }
        if (PackHideState.isHardLocked()) return;

        if (dihclient.util.multi.PacketTeleportController.isControllerOwnedSend()) return;
        if (dihclient.util.multi.PacketTeleportController.shouldSuppressMainMovement(packet)) {
            ci.cancel();
            return;
        }

        if (dihclient.util.DihClientWake.isActive()
            && packet instanceof ServerboundPlayerCommandPacket dih$cmd
            && dih$cmd.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SLEEPING) {
            ci.cancel();
            return;
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket) {
            dihclient.modules.Module dih$noFall = dih$noFallModule();
            if (dih$noFall != null && dih$noFall.isEnabled()) dih$noFall.onPacketSend(packet);
        }
        if (dih$isLocalClientPlayConnection()
            && (dihclient.util.macro.PingSpoofController.interceptOutbound(packet)
                || dihclient.modules.DihBlinkManager.interceptOutbound(packet))) {
            ci.cancel();
            return;
        }
        boolean payloadLoggedEarly = false;
        if (module != null && hooks.packetLoggerCapturing()) {
            payloadLoggedEarly = module.capturePayloadPacketForLogger(packet, "C2S", protocolHint);
        }
        if (module != null && !normalLoggerPath && hooks.pluginDiscoveryObservation()) {
            module.observePluginDiscoveryPacketSend(packet);
        }

        DihSharedState shared = DihSharedState.get();

        if (packet instanceof ServerboundUseItemOnPacket pibp) {
            if (shared.consumeBlockCaptureCallback(pibp.getHitResult().getBlockPos(), pibp.getHitResult().getDirection())) {
                ScaffoldModule.onPacketAbandoned(packet);
                ci.cancel();
                return;
            }
        }

        if (packet instanceof ServerboundInteractPacket && shared.hasEntityCaptureCallback()) {
            shared.consumeEntityCaptureCallback(Minecraft.getInstance().crosshairPickEntity);
            ScaffoldModule.onPacketAbandoned(packet);
            ci.cancel();
            return;
        }

        if (packet instanceof ServerboundContainerClosePacket closeForHold) {
            if (shared.consumeSuppressNextContainerClosePacket()) {
                ci.cancel();
                return;
            }
            if (DihContainerHold.isHeld(closeForHold.getContainerId())) {
                DihContainerHold.capturePendingClose(closeForHold.getContainerId(), closeForHold);
                ci.cancel();
                return;
            }
            if (dih$shouldKeepXCarryOpen(shared, closeForHold)) {
                ci.cancel();
                return;
            }
        }

        if (!normalLoggerPath) return;

        if (packet instanceof ServerboundUseItemOnPacket pibp) {
            shared.setLastInteractedBlockPos(pibp.getHitResult().getBlockPos());
            shared.setLastContainerTarget(DihContainerTarget.forBlockHit(pibp.getHitResult(), pibp.getHand()));
        }

        if (packet instanceof ServerboundInteractPacket entityPacket) {
            net.minecraft.world.entity.Entity targeted = Minecraft.getInstance().crosshairPickEntity;
            if (targeted != null && targeted != Minecraft.getInstance().player) {
                net.minecraft.world.InteractionHand capturedHand = entityPacket.hand();
                net.minecraft.world.phys.Vec3 capturedHitPos = entityPacket.location();
                shared.setLastContainerTarget(
                    capturedHitPos != null
                        ? DihContainerTarget.forEntityAt(targeted, capturedHand, capturedHitPos)
                        : DihContainerTarget.forEntity(targeted, capturedHand)
                );
            }
        }

        if (packet instanceof ServerboundSignUpdatePacket && shared.consumeSuppressNextSignUpdatePacket()) {
            ci.cancel();
            return;
        }

        if (packet instanceof ServerboundEditBookPacket && shared.consumeSuppressNextBookEditPacket()) {
            ci.cancel();
            return;
        }

        boolean forceBookOrSignPacket =
            packet instanceof ServerboundSignUpdatePacket && shared.consumeForceNextSignUpdatePacket()
                || packet instanceof ServerboundEditBookPacket && shared.consumeForceNextBookEditPacket();

        if (packet instanceof ServerboundSignUpdatePacket && !shared.shouldEditSigns()) {
            shared.setAllowSignEditing(true);
            if (!forceBookOrSignPacket) {
                ci.cancel();
                return;
            }
        }

        if (packet instanceof ServerboundEditBookPacket && !shared.shouldUpdateBook()) {
            shared.setAllowBookUpdate(true);
            if (!forceBookOrSignPacket) {
                ci.cancel();
                return;
            }
        }

        if (shared.isGBreakCapturing()) {
            if (packet instanceof ServerboundPlayerActionPacket) {

                shared.onGBreakPacket(packet);
            }

            return;
        }

        if (packet instanceof ServerboundPlayerActionPacket actionPacket
            && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            if (ModuleRegistry.dispatchStartBreakingBlock(actionPacket.getPos(), actionPacket.getDirection())) {
                ci.cancel();
                return;
            }
        }

        if (module.handlePacketSend(packet, payloadLoggedEarly)) {
            ScaffoldModule.onPacketAbandoned(packet);
            ci.cancel();
            return;
        }

        if (forceBookOrSignPacket) return;

        if (!shared.isFlushing()) {
            PacketGateManager.Result gateResult = PacketGateManager.handle(packet, "C2S");
            if (gateResult == PacketGateManager.Result.CANCEL) {
                ScaffoldModule.onPacketAbandoned(packet);
                ci.cancel();
                return;
            }
            if (gateResult == PacketGateManager.Result.DELAY) {
                shared.enqueuePacket(packet);
                ci.cancel();
                return;
            }
        }

        boolean anyFeatureActive = shared.shouldDelayGuiPackets()
            || !shared.shouldSendGuiPackets()
            || shared.shouldUseCustomPackets();
        if (!anyFeatureActive) return;

        if (shared.isFlushing()) return;

        if (dih$isTransactionSync(packet)) return;

        boolean shouldHandle = false;

        if (shared.shouldUseCustomPackets()) {

            shouldHandle = shared.getC2SPackets().contains(packet.getClass());
        } else {

            shouldHandle = isGuiPacket(packet);
        }

        if (!shouldHandle) return;

        if (DIH_PACKET_TRACE) {
            DihClientAddon.LOG.debug("[Dih] Packet detected: {} | Send={} Delay={} | Custom={}",
                packet.getClass().getSimpleName(), shared.shouldSendGuiPackets(),
                shared.shouldDelayGuiPackets(), shared.shouldUseCustomPackets());
        }

        if (!shared.shouldSendGuiPackets()) {
            if (DIH_PACKET_TRACE) DihClientAddon.LOG.debug("[Dih] CANCELLED packet (send disabled)");
            ScaffoldModule.onPacketAbandoned(packet);
            ci.cancel();
            return;
        }

        if (shared.shouldDelayGuiPackets()) {
            if (DIH_PACKET_TRACE) DihClientAddon.LOG.debug("[Dih] QUEUED packet (delay enabled)");
            DihModule captureModule = DihModule.get();
            DihSharedState.ReplayMode captureMode = (captureModule != null && captureModule.isCaptureAsExact())
                ? DihSharedState.ReplayMode.EXACT
                : DihSharedState.ReplayMode.REGENERATE;
            shared.enqueuePacket(packet, captureMode);
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("TAIL"))
    private void yang$afterSendPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if (dih$isMultiConnectionOrChannel()) return;
        if (PackHideState.isHardLocked()) return;
        if (!isDihActive()) return;
        if (!isPlayConnectionActive()) return;
        MacroExecutor.onPacketSent(packet);
    }

    @Unique
    private static boolean dih$isTransactionSync(Packet<?> packet) {
        return packet instanceof net.minecraft.network.protocol.common.ServerboundPongPacket
            || packet instanceof net.minecraft.network.protocol.common.ClientboundPingPacket
            || packet instanceof net.minecraft.network.protocol.common.ServerboundKeepAlivePacket
            || packet instanceof net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
    }

    @Unique
    private void dih$trackSentMessage(Packet<?> packet) {
        if (packet instanceof ServerboundChatPacket chat) {
            DihSharedState.get().setLastSentMessage(chat.message());
        } else if (packet instanceof ServerboundChatCommandPacket command) {
            DihSharedState.get().setLastSentMessage("/" + command.command());
        } else if (packet instanceof ServerboundChatCommandSignedPacket signed) {
            DihSharedState.get().setLastSentMessage("/" + signed.command());
        }
    }

    @Unique
    private boolean isGuiPacket(Packet<?> packet) {
        return packet instanceof ServerboundContainerClickPacket
            || packet instanceof ServerboundContainerButtonClickPacket
            || packet instanceof ServerboundSetCreativeModeSlotPacket
            || packet instanceof ServerboundPlayerActionPacket
            || packet instanceof ServerboundUseItemPacket
            || packet instanceof ServerboundSignUpdatePacket
            || packet instanceof ServerboundEditBookPacket
            || packet instanceof ServerboundChatPacket
            || packet instanceof ServerboundChatCommandPacket
            || packet instanceof ServerboundChatCommandSignedPacket
            || packet instanceof net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
    }

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void yang$onReceivePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {

        if (dih$isMultiConnectionOrChannel()) return;
        PacketListener listener = ((Connection) (Object) this).getPacketListener();
        DihModule module = DihModule.get();
        DihModule.PacketHookSnapshot hooks = module == null
            ? DihModule.PacketHookSnapshot.inactive()
            : module.packetHookSnapshot(isPlayReceiveListener(listener));
        boolean normalLoggerPath = hooks.normalPath();

        boolean vanillaDialogPacket = packet instanceof net.minecraft.network.protocol.common.ClientboundShowDialogPacket
            || packet instanceof net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
        boolean customMenuPacket = !vanillaDialogPacket
            && dihclient.api.custommenu.CustomMenuAdapterRegistry.acceptsInbound(packet);
        String protocolHint = hooks.packetLoggerCapturing() || hooks.pluginDiscoveryObservation() || customMenuPacket
            ? dih$protocolHint(listener)
            : "";
        if (PackHideState.isHardLocked()) {
            dih$clearRuntimeConnectionStateOnDisconnect(packet);
            return;
        }
        if (customMenuPacket) dihclient.util.custommenu.CustomMenuTracker.acceptInterested(packet, protocolHint);
        if (isPlayReceiveListener(listener)
            && (dihclient.util.macro.PingSpoofController.interceptInbound(packet)
                || dihclient.modules.DihBlinkManager.interceptInbound(packet))) {
            ci.cancel();
            return;
        }
        boolean payloadLoggedEarly = false;
        if (module != null && hooks.packetLoggerCapturing()) {
            payloadLoggedEarly = module.capturePayloadPacketForLogger(packet, "S2C", protocolHint);
        }
        if (module != null && !normalLoggerPath && hooks.pluginDiscoveryObservation()) {
            module.observePluginDiscoveryPacketReceive(packet);
        }

        if (dih$isIncomingChatPacket(packet)) {
            MacroExecutor.observeIncomingChat(packet);

            dihclient.modules.AutoLoginModule.observeIncomingChat(packet);
        }

        dihclient.modules.ModuleEspChunkCache.onPacketReceived(packet);
        if (!normalLoggerPath) return;

        dihclient.util.macro.ServerTickTracker.onS2CPacket(packet);
        MacroExecutor.onPacketReceived(packet);

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket macroSuggestions
                && dihclient.util.DihCommandSuggestionIds.isMacroId(macroSuggestions.id())) {
            ci.cancel();
            return;
        }

        if (packet instanceof ClientboundOpenScreenPacket openScreenPacket) {
            DihContainerHold.onContainerOpened(openScreenPacket.getContainerId());
        }
        if (packet instanceof ClientboundDisconnectPacket) {
            dih$clearRuntimeConnectionStateOnDisconnect(packet);
        }

        if (module.handlePacketReceive(packet, payloadLoggedEarly)) {
            ci.cancel();
            return;
        }

        DihSharedState shared = DihSharedState.get();

        PacketGateManager.Result gateResult = PacketGateManager.handle(packet, "S2C");
        if (gateResult == PacketGateManager.Result.CANCEL) {
            ci.cancel();
            return;
        }
        if (gateResult == PacketGateManager.Result.DELAY) {
            shared.enqueuePacket(packet);
            ci.cancel();
            return;
        }

        boolean anyFeatureActive = shared.shouldDelayGuiPackets()
            || !shared.shouldSendGuiPackets()
            || shared.shouldUseCustomPackets();
        if (!anyFeatureActive) return;

        if (dih$isTransactionSync(packet)) return;

        boolean shouldHandle = false;

        if (shared.shouldUseCustomPackets()) {

            shouldHandle = shared.getS2CPackets().contains(packet.getClass());
        }

        if (!shouldHandle) return;

        if (DIH_PACKET_TRACE) {
            DihClientAddon.LOG.debug("[Dih] S2C Packet detected: {} | Send={} Delay={}",
                packet.getClass().getSimpleName(), shared.shouldSendGuiPackets(),
                shared.shouldDelayGuiPackets());
        }

        if (!shared.shouldSendGuiPackets()) {
            ci.cancel();
            return;
        }

        if (shared.shouldDelayGuiPackets()) {
            shared.enqueuePacket(packet);
            ci.cancel();
        }
    }

    @Unique
    private boolean isDihActive() {
        DihModule module = DihModule.get();
        return module != null && module.arePacketHooksActive();
    }

    @Unique
    private void dih$ensureSpoofPipelineFilter() {
        if (dih$spoofPipelineInstalled) return;
        Channel channel = null;
        try {
            channel = ((DihClientConnectionAccessor) this).getChannel();
        } catch (Throwable ignored) {  }
        if (channel == null) return;

        try {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline == null || pipeline.get(DIH_SPOOF_FILTER) != null) {
                dih$spoofPipelineInstalled = true;
                return;
            }
            if (pipeline.get("encoder") != null) {
                pipeline.addAfter("encoder", DIH_SPOOF_FILTER, new DihSpoofPayloadFilter());
                dih$spoofPipelineInstalled = true;
            }
        } catch (Throwable t) {
            DihClientAddon.LOG.debug("[Dih] Failed to install client spoof payload filter", t);
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void dih$onSendPacketWithFlush(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (dih$isMultiConnectionOrChannel()) return;
        ScaffoldModule.onPacketQueued(packet);
        dih$trackSentMessage(packet);
        dih$ensureSpoofPipelineFilter();
        if (packet instanceof ServerboundResourcePackPacket resourcePackPacket
            && DihResourcePackTruthGuard.shouldCancelOutboundStatus(resourcePackPacket)) {
            ci.cancel();
            return;
        }
        if (packet instanceof ServerboundResourcePackPacket resourcePackPacket) {
            DihProtectorPackStrip.onPackFinalResponse(resourcePackPacket.id(), resourcePackPacket.action());
        }

        if (packet instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket) {
            if (DihSpoofPayloadFilter.shouldBlockForVanillaSpoof(DihModule.get(), packet)) {
                ci.cancel();
                return;
            }
            if (DihSpoofPayloadFilter.shouldDropForProtector(packet)) {
                ci.cancel();
            }
            return;
        }
        if (PackHideState.isHardLocked()) return;
        DihSharedState shared = DihSharedState.get();
        if (packet instanceof ServerboundContainerClosePacket closeForHold) {
            if (shared.consumeSuppressNextContainerClosePacket()) {
                ci.cancel();
                return;
            }
            if (DihContainerHold.isHeld(closeForHold.getContainerId())) {
                DihContainerHold.capturePendingClose(closeForHold.getContainerId(), closeForHold);
                ci.cancel();
                return;
            }
            if (dih$shouldKeepXCarryOpen(shared, closeForHold)) {
                ci.cancel();
            }
        }
    }

    @Unique
    private void dih$clearRuntimeConnectionStateOnDisconnect(Packet<?> packet) {
        if (!(packet instanceof ClientboundDisconnectPacket)) return;
        DihContainerHold.clearAll();
        PacketGateManager.clearAll();
        dihclient.util.macro.PingSpoofController.clearQueue();
        dihclient.modules.DihBlinkManager.clear();

        DihSharedState s = DihSharedState.get();
        s.setXCarryForcedTargets(java.util.Collections.emptySet(), false);
        s.setXCarryForced(false);
        s.setXCarryActive(false);
    }

    @Unique
    private boolean isPlayConnectionActive() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.getConnection() != null;
    }

    @Unique
    private static boolean isPlayReceiveListener(PacketListener listener) {
        return listener instanceof ClientGamePacketListener;
    }

    @Unique
    private boolean dih$isLocalClientPlayConnection() {
        return ((Connection) (Object) this).getPacketListener() instanceof ClientGamePacketListener;
    }

    @Unique
    private static boolean dih$isIncomingChatPacket(Packet<?> packet) {
        return packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket
            || packet instanceof net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket
            || packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
    }

    @Unique
    private String dih$protocolHint(PacketListener listener) {
        if (listener == null) return "";
        if (listener == dih$protocolHintListener) return dih$protocolHintCache;
        String hint;
        if (listener instanceof ClientGamePacketListener) {
            hint = "play";
        } else {
            String name = listener.getClass().getName();
            hint = name.toLowerCase(java.util.Locale.ROOT).contains("configuration")
                ? "configuration"
                : "";
        }
        dih$protocolHintCache = hint;
        dih$protocolHintListener = listener;
        return hint;
    }

    @Unique
    private boolean dih$shouldKeepXCarryOpen(DihSharedState shared, ServerboundContainerClosePacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return false;
        DihModule module = DihModule.get();
        boolean allowPassiveXCarry = module != null && module.isXCarryEnabled();

        if (!allowPassiveXCarry && !shared.isXCarryForced()) return false;
        if (packet.getContainerId() != client.player.inventoryMenu.containerId) return false;

        java.util.Set<Integer> mask;
        boolean carryCursor;
        if (shared.isXCarryForced()) {
            mask = shared.getXCarryForcedSlotMask();
            carryCursor = shared.isXCarryForcedCarryCursor();
        } else {
            mask = module == null ? null : module.getXCarryModuleSlotMask();
            carryCursor = module == null || module.isXCarryCarryCursor();
        }
        boolean hasItems = dihclient.util.macro.XCarryAction.hasStoredItems(
                client.player.inventoryMenu, carryCursor, mask);
        shared.setXCarryActive(hasItems);

        return true;
    }
}
