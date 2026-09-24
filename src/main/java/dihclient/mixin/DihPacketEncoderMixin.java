package dihclient.mixin;

import dihclient.modules.DihModule;
import dihclient.util.DihNetworkCaptureState;
import dihclient.util.DihPacketCapture;
import dihclient.util.multi.MultiConnectionContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PacketEncoder.class)
public abstract class DihPacketEncoderMixin<T extends PacketListener> {
    @Shadow @Final private ProtocolInfo<T> protocolInfo;

    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V")
    private void dih$captureEncodedPlaintext(ChannelHandlerContext ctx, Packet<T> packet, ByteBuf output,
                                                 Operation<Void> original) {
        long captureState = DihNetworkCaptureState.state();
        if (DihNetworkCaptureState.mode(captureState) == 0) {
            original.call(ctx, packet, output);
            return;
        }
        boolean multi = ctx != null && MultiConnectionContext.isMulti(ctx.channel());
        boolean suppressPayloadCodec = multi && DihNetworkCaptureState.capturesPayloads(captureState);
        if (suppressPayloadCodec) DihNetworkCaptureState.beginMultiCodecSuppression();
        try {
            original.call(ctx, packet, output);
        } finally {
            if (suppressPayloadCodec) DihNetworkCaptureState.endMultiCodecSuppression();
        }
        if (multi) return;
        DihModule module = DihModule.get();
        if (module == null) return;
        String protocol = protocolInfo.id().id();
        if (DihNetworkCaptureState.capturesPlaintext(captureState)) {
            DihPacketCapture.capturePlaintext(packet, "C2S", protocol, packet.type(), output);
        }
        if (DihNetworkCaptureState.capturesPayloads(captureState) && dih$isPayloadCarrier(packet)) {
            module.captureDecodedPayloadPacket(packet, "C2S", protocol, "encoder");
        }
    }

    @Unique
    private static boolean dih$isPayloadCarrier(Packet<?> packet) {
        return packet instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket
            || packet instanceof net.minecraft.network.protocol.BundlePacket<?>;
    }
}
