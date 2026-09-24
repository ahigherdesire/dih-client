package dihclient.mixin.security;

import dihclient.security.DihProtector;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class DihProtectorChatSigningMixin {

    @WrapOperation(
        method = "sendChat",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/SignedMessageChain$Encoder;pack(Lnet/minecraft/network/chat/SignedMessageBody;)Lnet/minecraft/network/chat/MessageSignature;")
    )
    private MessageSignature dih$skipSigning(SignedMessageChain.Encoder encoder,
                                                SignedMessageBody body,
                                                Operation<MessageSignature> original) {
        if (DihProtector.shouldSkipChatSigning()) return null;
        return original.call(encoder, body);
    }
}
