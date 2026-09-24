package dihclient.mixin;

import dihclient.modules.SkyboxRenderer;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CloudRenderer.class)
public abstract class DihSkyboxCloudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$skyboxNoClouds(CallbackInfo ci) {
        if (SkyboxRenderer.isActive()) ci.cancel();
    }
}
