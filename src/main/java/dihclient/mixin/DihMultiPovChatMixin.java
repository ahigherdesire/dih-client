package dihclient.mixin;

import dihclient.util.multi.MultiPovChat;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class DihMultiPovChatMixin {
    @Unique private ChatComponent.State dih$povMessageState;
    @Unique private ChatComponent.State dih$povDeleteState;
    @Unique private ChatComponent.State dih$povClearState;

    @Inject(method = "addMessage", at = @At("HEAD"))
    private void dih$beforeMainChatMessage(Component content, MessageSignature signature,
                                               GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        dih$povMessageState = MultiPovChat.beginRenderedClientMutation((ChatComponent) (Object) this);
    }

    @Inject(method = "addMessage", at = @At("RETURN"))
    private void dih$afterMainChatMessage(Component content, MessageSignature signature,
                                              GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        ChatComponent.State state = dih$povMessageState;
        dih$povMessageState = null;
        MultiPovChat.endRenderedClientMutation((ChatComponent) (Object) this, state);
    }

    @Inject(method = "deleteMessage", at = @At("HEAD"))
    private void dih$beforeMainChatDelete(MessageSignature signature, CallbackInfo ci) {
        dih$povDeleteState = MultiPovChat.beginRenderedClientMutation((ChatComponent) (Object) this);
    }

    @Inject(method = "deleteMessage", at = @At("RETURN"))
    private void dih$afterMainChatDelete(MessageSignature signature, CallbackInfo ci) {
        ChatComponent.State state = dih$povDeleteState;
        dih$povDeleteState = null;
        MultiPovChat.endRenderedClientMutation((ChatComponent) (Object) this, state);
    }

    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void dih$beforeMainChatClear(boolean clearHistory, CallbackInfo ci) {
        dih$povClearState = MultiPovChat.beginRenderedClientMutation((ChatComponent) (Object) this);
    }

    @Inject(method = "clearMessages", at = @At("RETURN"))
    private void dih$afterMainChatClear(boolean clearHistory, CallbackInfo ci) {
        ChatComponent.State state = dih$povClearState;
        dih$povClearState = null;
        MultiPovChat.endRenderedClientMutation((ChatComponent) (Object) this, state);
    }
}
