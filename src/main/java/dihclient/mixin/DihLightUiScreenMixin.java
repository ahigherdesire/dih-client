package dihclient.mixin;

import dihclient.util.DihLightUi;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Light UI: no menu blur behind DIH's own screens. Vanilla screens keep their own Menu Background Blur setting. */
@Mixin(Screen.class)
public abstract class DihLightUiScreenMixin {
    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$skipBlurForLightUi(CallbackInfo ci) {
        if (getClass().getName().startsWith("dihclient.") && DihLightUi.active()) ci.cancel();
    }
}
