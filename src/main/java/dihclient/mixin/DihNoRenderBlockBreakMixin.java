package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class DihNoRenderBlockBreakMixin {
    @Inject(method = "submitBlockDestroyAnimation", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noBlockBreakOverlay(CallbackInfo ci) {
        if (NoRenderState.noBlockBreakOverlay()) ci.cancel();
    }
}
