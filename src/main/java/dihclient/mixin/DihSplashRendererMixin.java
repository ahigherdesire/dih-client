package dihclient.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SplashRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SplashRenderer.class, priority = 2000)
public class DihSplashRendererMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void dih$hideSplashText(GuiGraphicsExtractor graphics, int screenWidth, Font font, float alpha, CallbackInfo ci) {
        if (dihclient.modules.PackHideState.isActive()) {
            if (!Minecraft.getInstance().options.hideSplashTexts().get()) {
                dihclient.util.DihVanillaSplash.renderPanicSplash(Minecraft.getInstance(), graphics, screenWidth, font, alpha);
            }
            ci.cancel();
            return;
        }

        if (dihclient.util.DihMenuPrefs.vanillaMenuVisuals()) return;
        ci.cancel();
    }
}
