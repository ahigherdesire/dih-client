package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public abstract class DihNoRenderWeatherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noWeather(CallbackInfo ci) {
        if (NoRenderState.noWeather()) ci.cancel();
    }
}
