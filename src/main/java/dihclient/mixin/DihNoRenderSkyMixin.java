package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public abstract class DihNoRenderSkyMixin {
    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noSkyDisc(CallbackInfo ci) {
        if (NoRenderState.noSky()) ci.cancel();
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noEndSky(CallbackInfo ci) {
        if (NoRenderState.noSky()) ci.cancel();
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noStars(CallbackInfo ci) {
        if (NoRenderState.noStars()) ci.cancel();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noSun(CallbackInfo ci) {
        if (NoRenderState.noSun()) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noMoon(CallbackInfo ci) {
        if (NoRenderState.noMoon()) ci.cancel();
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noSunriseGlow(CallbackInfo ci) {
        if (NoRenderState.noSunriseGlow()) ci.cancel();
    }
}
