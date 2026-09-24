package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BannerRenderer.class)
public abstract class DihNoRenderBannerMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noBanner(CallbackInfo ci) {
        if (NoRenderState.noBanners()) ci.cancel();
    }
}
