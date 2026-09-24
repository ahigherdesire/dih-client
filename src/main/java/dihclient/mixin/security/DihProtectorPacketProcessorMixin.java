package dihclient.mixin.security;

import dihclient.security.DihProtector;
import dihclient.security.DihProtectorPacketContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
public class DihProtectorPacketProcessorMixin {

    @WrapOperation(
        method = "handle",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V")
    )
    private <T extends PacketListener> void dih$wrapHandle(Packet<?> instance, T listener,
                                                              Operation<Void> original) {

        if (!(instance instanceof ClientboundCustomPayloadPacket)
            || !DihProtector.shouldTagPacketComponents()) {
            original.call(instance, listener);
            return;
        }
        DihProtectorPacketContext.setProcessingPacket(true);
        try {
            original.call(instance, listener);
        } finally {
            DihProtectorPacketContext.setProcessingPacket(false);
        }
    }
}
