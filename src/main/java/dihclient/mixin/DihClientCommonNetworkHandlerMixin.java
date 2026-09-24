package dihclient.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dihclient.modules.DihModule;
import dihclient.modules.PackHideState;
import dihclient.security.DihPackResponseScheduler;
import dihclient.security.DihProtectorPackStrip;
import dihclient.security.DihResourcePackTruthGuard;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihSharedState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class DihClientCommonNetworkHandlerMixin {
    @Shadow @Final protected Minecraft minecraft;

    @Shadow public abstract void send(Packet<?> packet);

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void yang$onResourcePackSend(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        DihSharedState shared = DihSharedState.get();
        DihModule module = DihModule.get();
        boolean shouldForceDeny = shared.shouldForceDenyResourcePack() || (module != null && module.isForceDenyResourcePack());
        boolean shouldBypass = shared.shouldBypassResourcePack() || (module != null && module.isBypassResourcePack());

        DihResourcePackTruthGuard.Verdict verdict =
            DihResourcePackTruthGuard.classify(packet, shouldForceDeny, shouldBypass);
        if (!verdict.shouldCancelVanilla()) return;

        DihProtectorPackStrip.onPop(packet.id());

        java.util.function.Consumer<ServerboundResourcePackPacket> sender = this::send;
        switch (verdict.kind()) {
            case BYPASS_SUCCESS -> {
                long accepted = DihPackResponseScheduler.acceptDelayMs();
                long downloaded = DihPackResponseScheduler.downloadedDelayMs(accepted);
                long applied = DihPackResponseScheduler.appliedDelayMs(downloaded);
                DihPackResponseScheduler.schedule(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED, accepted, sender);
                DihPackResponseScheduler.schedule(packet.id(), ServerboundResourcePackPacket.Action.DOWNLOADED, downloaded, sender);
                DihPackResponseScheduler.schedule(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED, applied, sender);
            }
            case DECLINE -> DihPackResponseScheduler.schedule(packet.id(),
                ServerboundResourcePackPacket.Action.DECLINED, DihPackResponseScheduler.declineDelayMs(), sender,
                () -> DihClientMessaging.sendPrefixed("Dih denied server resource pack."));

            case INVALID_URL -> send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.INVALID_URL));
            case FAILED_DOWNLOAD -> {
                long accepted = DihPackResponseScheduler.acceptDelayMs();
                DihPackResponseScheduler.schedule(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED, accepted, sender);
                DihPackResponseScheduler.schedule(packet.id(), ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD,
                    DihPackResponseScheduler.failedDelayMs(accepted), sender);
            }
            default -> {
            }
        }

        ci.cancel();
    }

    @Inject(method = "handleShowDialog", at = @At("TAIL"))
    private void dih$trackCustomMenu(ClientboundShowDialogPacket packet, CallbackInfo ci) {
        String phase = ((Object) this) instanceof ClientConfigurationPacketListener ? "CONFIGURATION" : "PLAY";
        dihclient.util.custommenu.CustomMenuTracker.accept(packet, phase);
    }

    @Inject(method = "handleClearDialog", at = @At("TAIL"))
    private void dih$clearCustomMenu(ClientboundClearDialogPacket packet, CallbackInfo ci) {
        String phase = ((Object) this) instanceof ClientConfigurationPacketListener ? "CONFIGURATION" : "PLAY";
        dihclient.util.custommenu.CustomMenuTracker.accept(packet, phase);
    }
}
