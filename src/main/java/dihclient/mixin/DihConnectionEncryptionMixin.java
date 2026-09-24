package dihclient.mixin;

import dihclient.util.DihCiphertextTap;
import dihclient.util.DihNetworkCaptureState;
import dihclient.util.DihPacketCapture;
import dihclient.util.multi.MultiConnectionContext;
import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.Cipher;

@Mixin(Connection.class)
public abstract class DihConnectionEncryptionMixin {
    @Shadow private Channel channel;

    @Inject(method = "setEncryptionKey", at = @At("TAIL"))
    private void dih$observeEncryptionBoundary(Cipher decryptCipher, Cipher encryptCipher, CallbackInfo ci) {
        if (MultiConnectionContext.isMulti((Connection) (Object) this)) return;
        if (channel == null) return;
        if (DihNetworkCaptureState.capturesPlaintext()) {
            DihPacketCapture.markEncryptionEnabled(channel);
        }
        try {
            if (channel.pipeline().get("dih_ciphertext_in") == null && channel.pipeline().get("decrypt") != null) {
                channel.pipeline().addBefore("decrypt", "dih_ciphertext_in", new DihCiphertextTap("S2C"));
            }
            if (channel.pipeline().get("dih_ciphertext_out") == null && channel.pipeline().get("encrypt") != null) {
                channel.pipeline().addBefore("encrypt", "dih_ciphertext_out", new DihCiphertextTap("C2S"));
            }
        } catch (Throwable ignored) {  }
    }
}
