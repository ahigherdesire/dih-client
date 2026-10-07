package dihclient.mixin;

import dihclient.modules.BetterChatModule;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BetterChat on the public entry points, so the private addMessage (which Multi POV brackets) is never cancelled
 * part-way: a filtered line is dropped here, and a rewritten one is sent back in through the same entry point.
 */
@Mixin(ChatComponent.class)
public abstract class DihBetterChatMixin {
    @Unique private boolean dih$rewriting;

    @ModifyConstant(method = {"addMessageToQueue", "addMessageToDisplayQueue"}, constant = @Constant(intValue = 100))
    private int dih$historyLimit(int vanilla) {
        return BetterChatModule.historyLimit(vanilla);
    }

    @Inject(method = "addServerSystemMessage", at = @At("HEAD"), cancellable = true)
    private void dih$betterServerChat(Component message, CallbackInfo ci) {
        if (dih$rewriting) return;
        if (BetterChatModule.filtered(message)) {
            ci.cancel();
            return;
        }
        if (!BetterChatModule.rewrites(true)) return;
        ChatComponent self = (ChatComponent) (Object) this;
        Component shown = BetterChatModule.rewrite(self, message, true);
        ci.cancel();
        dih$rewriting = true;
        try {
            self.addServerSystemMessage(shown);
        } finally {
            dih$rewriting = false;
        }
        BetterChatModule.shown(self);
    }

    @Inject(method = "addPlayerMessage", at = @At("HEAD"), cancellable = true)
    private void dih$betterPlayerChat(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (dih$rewriting) return;
        if (BetterChatModule.filtered(message)) {
            ci.cancel();
            return;
        }
        if (!BetterChatModule.rewrites(true)) return;
        ChatComponent self = (ChatComponent) (Object) this;
        Component shown = BetterChatModule.rewrite(self, message, true);
        ci.cancel();
        dih$rewriting = true;
        try {
            self.addPlayerMessage(shown, signature, tag);
        } finally {
            dih$rewriting = false;
        }
        BetterChatModule.shown(self);
    }

    @Inject(method = "addClientSystemMessage", at = @At("HEAD"), cancellable = true)
    private void dih$betterClientChat(Component message, CallbackInfo ci) {
        if (dih$rewriting || !BetterChatModule.rewrites(false)) return;
        ChatComponent self = (ChatComponent) (Object) this;
        Component shown = BetterChatModule.rewrite(self, message, false);
        ci.cancel();
        dih$rewriting = true;
        try {
            self.addClientSystemMessage(shown);
        } finally {
            dih$rewriting = false;
        }
    }
}
