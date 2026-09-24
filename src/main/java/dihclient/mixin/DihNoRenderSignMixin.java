package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.renderer.blockentity.AbstractSignRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignRenderer.class)
public abstract class DihNoRenderSignMixin {
    @Inject(method = "submitSignText", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noSignText(CallbackInfo ci) {
        if (NoRenderState.noSignText()) ci.cancel();
    }
}
