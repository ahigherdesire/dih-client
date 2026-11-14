package dihclient.security;

import dihclient.modules.DihModule;
import dihclient.util.DihPayloadSupport;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

public final class DihSpoofPayloadFilter extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        if (msg instanceof ServerboundCustomPayloadPacket packet) {
            if (shouldBlockForVanillaSpoof(DihModule.get(), packet)) {
                DihProtector.consumeUserBypass(packet);
                promise.setSuccess();
                return;
            }

            DihProtectorChannelFilter.Verdict verdict = DihProtectorChannelFilter.filter(packet);
            switch (verdict.kind) {
                case DROP -> {
                    DihProtector.consumeUserBypass(packet);
                    promise.setSuccess();
                    return;
                }
                case REPLACE -> {
                    DihProtector.consumeUserBypass(packet);
                    super.write(ctx, verdict.replacement, promise);
                    return;
                }
                case PASS -> DihProtector.consumeUserBypass(packet);
            }
        }
        super.write(ctx, msg, promise);
    }

    public static boolean shouldBlockForVanillaSpoof(DihModule module, Packet<?> packet) {
        if (!(packet instanceof ServerboundCustomPayloadPacket customPayload)) return false;
        if (DihProtector.isUserBypass(packet)) return false;
        if (module == null || !module.isSpoofClientVanilla()) return false;
        String channel = DihPayloadSupport.payloadChannel(customPayload.payload());
        if (isLoaderProtocolChannel(channel)) return false;
        return !DihPayloadSupport.isBrandChannel(channel);
    }

    /**
     * NeoForge's and Forge's own connection negotiation (neoforge:*, forge:*, fml:*). Such a client can't pass as
     * vanilla: without these it can't finish joining a modded server or its own singleplayer world, and vanilla
     * servers never ask for them.
     */
    public static boolean isLoaderProtocolChannel(String channel) {
        return channel != null && (channel.startsWith("neoforge:") || channel.startsWith("forge:") || channel.startsWith("fml:"));
    }

    public static boolean shouldDropForProtector(Packet<?> packet) {
        DihProtectorChannelFilter.Verdict verdict = DihProtectorChannelFilter.filter(packet);
        return verdict.kind == DihProtectorChannelFilter.Verdict.Kind.DROP;
    }
}
