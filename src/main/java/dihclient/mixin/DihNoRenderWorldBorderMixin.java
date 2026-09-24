package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.renderer.WorldBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorderRenderer.class)
public abstract class DihNoRenderWorldBorderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noWorldBorder(CallbackInfo ci) {
        if (NoRenderState.noWorldBorder()) ci.cancel();
    }
}
