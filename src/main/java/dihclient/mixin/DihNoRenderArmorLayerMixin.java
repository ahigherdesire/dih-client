package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public abstract class DihNoRenderArmorLayerMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noArmor(CallbackInfo ci) {
        if (NoRenderState.noArmor()) ci.cancel();
    }
}
