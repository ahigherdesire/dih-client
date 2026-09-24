package dihclient.mixin;

import dihclient.commands.DihCommands;
import dihclient.util.DihClipboard;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyboardHandler.class)
public class DihKeyboardHandlerMixin {

    private static long dih$lastScreenSeenMs;

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void dih$prefixOpensChat(long window, CharacterEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || event == null || mc.player == null) return;
        if (mc.gui.screen() != null) {
            dih$lastScreenSeenMs = System.currentTimeMillis();
            return;
        }
        if (System.currentTimeMillis() - dih$lastScreenSeenMs < 250L) return;
        String prefix = DihCommands.effectivePrefix();
        if (prefix == null || prefix.length() != 1 || "/".equals(prefix)) return;
        if (event.codepoint() != prefix.charAt(0)) return;
        mc.gui.setScreen(new ChatScreen(prefix, false));
        dih$lastScreenSeenMs = System.currentTimeMillis();
        ci.cancel();
    }

    @Inject(method = "getClipboard", at = @At("HEAD"), cancellable = true)
    private void dih$linuxClipboardGet(CallbackInfoReturnable<String> cir) {
        if (Util.getPlatform() == Util.OS.LINUX) {
            cir.setReturnValue(DihClipboard.get());
        }
    }

    @Inject(method = "setClipboard", at = @At("HEAD"), cancellable = true)
    private void dih$linuxClipboardSet(String text, CallbackInfo ci) {
        if (Util.getPlatform() == Util.OS.LINUX) {
            DihClipboard.set(text);
            ci.cancel();
        }
    }
}
