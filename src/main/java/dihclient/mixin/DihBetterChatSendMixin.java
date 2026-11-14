package dihclient.mixin;

import dihclient.modules.BetterChatModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** BetterChat's prefix, suffix and full-width text: the rewritten line is sent back through sendChat once. */
@Mixin(ClientPacketListener.class)
public abstract class DihBetterChatSendMixin {
    @Unique private boolean dih$rewritingChat;

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void dih$betterChatOutgoing(String message, CallbackInfo ci) {
        if (dih$rewritingChat) return;
        String rewritten = BetterChatModule.outgoing(message);
        if (rewritten == null || rewritten.equals(message)) return;
        ci.cancel();
        dih$rewritingChat = true;
        try {
            ((ClientPacketListener) (Object) this).sendChat(rewritten);
        } finally {
            dih$rewritingChat = false;
        }
    }
}
