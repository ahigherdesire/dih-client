package dihclient.mixin;

import dihclient.util.DihFonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DIH's own screens draw with the UI font; vanilla and other mods' screens keep theirs. */
@Mixin(Screen.class)
public abstract class DihScreenFontMixin {
    @Mutable
    @Shadow
    @Final
    protected Font font;

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;)V",
        at = @At("RETURN"))
    private void dih$useUiFont(Minecraft minecraft, Font font, Component title, CallbackInfo ci) {
        if (getClass().getName().startsWith("dihclient.")) {
            this.font = DihFonts.ui(font);
        }
    }
}
