package dihclient.mixin;

import dihclient.commands.DihCommands;
import dihclient.modules.DihModule;
import dihclient.modules.PackHideState;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class DihChatScreenMixin {
    @Shadow
    protected EditBox input;

    @Inject(method = "init()V", at = @At("TAIL"))
    private void dih$infiniChatMaxLength(CallbackInfo ci) {
        if (input != null && DihConfig.getGlobal().infiniChat) {
            input.setMaxLength(Integer.MAX_VALUE);
        }
    }

    @Inject(method = "normalizeChatMessage", at = @At("HEAD"), cancellable = true)
    private void dih$infiniChatNoTruncate(String message, CallbackInfoReturnable<String> cir) {
        if (DihConfig.getGlobal().infiniChat) {
            cir.setReturnValue(StringUtils.normalizeSpace(message.trim()));
        }
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void yang$onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (message == null) return;
        String trimmed = message.trim();
        if (trimmed.isEmpty()) return;
        if (DihCommands.isBlockedPanicCommandMessage(trimmed)) {
            ci.cancel();
            return;
        }

        if ("^toggledih".equalsIgnoreCase(trimmed)) {
            if (PackHideState.isActive()) {
                ci.cancel();
                return;
            }
            DihModule module = DihModule.get();
            module.toggle();
            Minecraft mc = Minecraft.getInstance();
            DihClientMessaging.sendPrefixed("Dih is now " + (module.isActive() ? "enabled" : "disabled") + ".");
            mc.gui.setScreen(null);
            ci.cancel();
        }
    }
}
