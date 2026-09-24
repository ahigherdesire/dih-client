package dihclient.mixin.security;

import dihclient.security.DihProtector;
import dihclient.security.DihProtectorPacketContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

@Mixin(PacketDecoder.class)
public class DihProtectorPacketDecoderMixin {

    @WrapOperation(
        method = "decode",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/codec/StreamCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private Object dih$wrapDecode(StreamCodec instance, Object buffer, Operation<Object> original) {
        if (!DihProtector.shouldTagPacketComponents()) {
            return dih$decodeCompatibly(instance, buffer, original);
        }
        DihProtectorPacketContext.setProcessingPacket(true);
        try {
            return dih$decodeCompatibly(instance, buffer, original);
        } finally {
            DihProtectorPacketContext.setProcessingPacket(false);
        }
    }

    @Unique
    private static Object dih$decodeCompatibly(StreamCodec instance, Object buffer,
                                                    Operation<Object> original) {
        if (!(buffer instanceof ByteBuf byteBuf)) {
            return original.call(instance, buffer);
        }

        int startIndex = byteBuf.readerIndex();
        try {
            return original.call(instance, buffer);
        } catch (DecoderException decodeFailure) {
            if (dih$isMissingLoginSessionId(decodeFailure)) {
                byteBuf.readerIndex(startIndex);
                try {
                    dih$readVarInt(byteBuf);
                    var profile = ByteBufCodecs.GAME_PROFILE.decode(byteBuf);
                    if (byteBuf.isReadable()) {
                        byteBuf.readerIndex(startIndex);
                        throw decodeFailure;
                    }

                    return new ClientboundLoginFinishedPacket(profile, UUID.randomUUID());
                } catch (RuntimeException fallbackFailure) {
                    byteBuf.readerIndex(startIndex);
                    throw decodeFailure;
                }
            }
            if (dih$isTruncatedEntityData(decodeFailure)) {
                byteBuf.readerIndex(startIndex);
                try {
                    int entityId = dih$readVarInt(byteBuf);

                    return new ClientboundSetEntityDataPacket(entityId, java.util.List.of());
                } catch (RuntimeException fallbackFailure) {
                    byteBuf.readerIndex(startIndex);
                    throw decodeFailure;
                }
            }
            throw decodeFailure;
        }
    }

    @Unique
    private static boolean dih$isTruncatedEntityData(DecoderException failure) {
        String message = failure.getMessage();
        if (message == null || !message.contains("clientbound/minecraft:set_entity_data")) return false;
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof IndexOutOfBoundsException) return true;
            cause = cause.getCause();
        }
        return false;
    }

    @Unique
    private static boolean dih$isMissingLoginSessionId(DecoderException failure) {
        String message = failure.getMessage();
        if (message == null || !message.contains("clientbound/minecraft:login_finished")) return false;
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof IndexOutOfBoundsException) return true;
            cause = cause.getCause();
        }
        return false;
    }

    @Unique
    private static int dih$readVarInt(ByteBuf buffer) {
        int value = 0;
        for (int byteIndex = 0; byteIndex < 5; byteIndex++) {
            int current = buffer.readByte();
            value |= (current & 0x7F) << (byteIndex * 7);
            if ((current & 0x80) == 0) return value;
        }
        throw new DecoderException("VarInt is too big");
    }
}
