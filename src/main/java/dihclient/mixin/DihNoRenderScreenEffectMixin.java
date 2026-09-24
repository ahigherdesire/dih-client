package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class DihNoRenderScreenEffectMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dih$noFireOverlay(CallbackInfo ci) {
        if (NoRenderState.noFireOverlay()) ci.cancel();
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dih$noLiquidOverlay(CallbackInfo ci) {
        if (NoRenderState.noLiquidOverlay()) ci.cancel();
    }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dih$noInWallOverlay(CallbackInfo ci) {
        if (NoRenderState.noInWallOverlay()) ci.cancel();
    }
}
