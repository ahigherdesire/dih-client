package dihclient.mixin;

import dihclient.modules.PackHideState;
import dihclient.util.DihNetworkCaptureState;
import dihclient.util.DihPayloadSupport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$1")
public abstract class DihCustomPayloadCodecMixin {
    @Unique
    private static final ThreadLocal<dihclient.util.DihPayloadCaptureCursor> DIH_CAPTURE_CURSOR =
        ThreadLocal.withInitial(dihclient.util.DihPayloadCaptureCursor::new);

    @Inject(method = "encode(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void dih$encodeRawCustomPacketPayload(FriendlyByteBuf buf, CustomPacketPayload payload, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) {
            return;
        }
        long captureState = DihNetworkCaptureState.codecState();
        boolean capturePayload = DihNetworkCaptureState.capturesPayloads(captureState);
        if (capturePayload && buf != null) {
            dihclient.util.DihPayloadCaptureCursor cursor = DIH_CAPTURE_CURSOR.get();
            cursor.encodeStartIndex = buf.writerIndex();
            cursor.encodeState = captureState;
        }
        boolean isRaw = payload instanceof DihPayloadSupport.RawCustomPacketPayload;
        if (!isRaw && !capturePayload) return;

        byte[] rememberedBytes = DihPayloadSupport.getRememberedUnknownPayloadBytes(payload);
        if (rememberedBytes == null && !isRaw) return;

        CustomPacketPayload.Type<?> type = payload.type();
        if (type == null || type.id() == null) return;

        byte[] bytes = rememberedBytes != null
            ? rememberedBytes
            : ((DihPayloadSupport.RawCustomPacketPayload) payload).bytes();

        buf.writeIdentifier(type.id());
        if (bytes.length > 0) {
            buf.writeBytes(bytes);
        }
        ci.cancel();
    }

    @Inject(method = "encode(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("TAIL"))
    private void dih$captureEncodedCustomPacketPayload(FriendlyByteBuf buf, CustomPacketPayload payload, CallbackInfo ci) {
        long captureState = DihNetworkCaptureState.codecState();
        if (!DihNetworkCaptureState.capturesPayloads(captureState)) return;
        dihclient.util.DihPayloadCaptureCursor cursor = DIH_CAPTURE_CURSOR.get();
        int startIndex = cursor.encodeState == captureState ? cursor.encodeStartIndex : -1;
        cursor.encodeStartIndex = -1;
        cursor.encodeState = 0L;
        if (payload == null || buf == null || startIndex < 0) return;
        int end = buf.writerIndex();
        if (end <= startIndex) return;
        byte[] encodedBytes = new byte[end - startIndex];
        buf.getBytes(startIndex, encodedBytes);
        DihPayloadSupport.rememberDecodedPayloadBytes(payload, dih$payloadChannel(payload), encodedBytes);
    }

    @Inject(method = "decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;",
            at = @At("HEAD"))
    private void dih$captureDecodeStart(FriendlyByteBuf buf, CallbackInfoReturnable<CustomPacketPayload> cir) {
        long captureState = DihNetworkCaptureState.codecState();
        if (!DihNetworkCaptureState.capturesPayloads(captureState) || buf == null) return;
        dihclient.util.DihPayloadCaptureCursor cursor = DIH_CAPTURE_CURSOR.get();
        cursor.decodeStartIndex = buf.readerIndex();
        cursor.decodeState = captureState;
    }

    @Inject(method = "decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;",
            at = @At("RETURN"))
    private void dih$captureDecodedCustomPacketPayload(FriendlyByteBuf buf, CallbackInfoReturnable<CustomPacketPayload> cir) {
        long captureState = DihNetworkCaptureState.codecState();
        if (!DihNetworkCaptureState.capturesPayloads(captureState)) return;
        dihclient.util.DihPayloadCaptureCursor cursor = DIH_CAPTURE_CURSOR.get();
        int startIndex = cursor.decodeState == captureState ? cursor.decodeStartIndex : -1;
        cursor.decodeStartIndex = -1;
        cursor.decodeState = 0L;
        CustomPacketPayload payload = cir.getReturnValue();
        if (payload == null || buf == null || startIndex < 0) return;
        int end = buf.readerIndex();
        if (end <= startIndex) return;
        byte[] encodedBytes = new byte[end - startIndex];
        buf.getBytes(startIndex, encodedBytes);
        DihPayloadSupport.rememberDecodedPayloadBytes(payload, dih$payloadChannel(payload), encodedBytes);
    }

    @Unique
    private static String dih$payloadChannel(CustomPacketPayload payload) {
        try {
            CustomPacketPayload.Type<?> type = payload == null ? null : payload.type();
            if (type != null && type.id() != null) return type.id().toString();
        } catch (Throwable ignored) {  }
        return "";
    }
}
