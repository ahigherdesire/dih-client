package dihclient.mixin.security;

import dihclient.security.DihProtectorPackStrip;
import dihclient.security.DihResourcePackTruthGuard;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class DihProtectorClientCommonNetworkHandlerMixin {

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"))
    private void dih$onPackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        DihProtectorPackStrip.onPackPush(packet.id());
    }

    @Inject(method = "handleResourcePackPop", at = @At("HEAD"))
    private void dih$onPackPop(ClientboundResourcePackPopPacket packet, CallbackInfo ci) {
        Optional<UUID> id = packet.id();
        DihProtectorPackStrip.onPop(id.orElse(null));
        DihResourcePackTruthGuard.onPop(id.orElse(null));

        dihclient.security.DihPackResponseScheduler.cancel(id.orElse(null));
    }
}
