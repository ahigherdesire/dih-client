package dihclient.util;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public final class DihCiphertextTap extends ChannelDuplexHandler {
    private final String direction;

    public DihCiphertextTap(String direction) {
        this.direction = direction;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (packetHooksActive() && msg instanceof ByteBuf buf) {
            DihPacketCapture.captureCiphertext(ctx.channel(), direction, buf);
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (packetHooksActive() && msg instanceof ByteBuf buf) {
            DihPacketCapture.captureCiphertext(ctx.channel(), direction, buf);
        }
        super.write(ctx, msg, promise);
    }

    private static boolean packetHooksActive() {
        return DihNetworkCaptureState.capturesPlaintext();
    }
}
