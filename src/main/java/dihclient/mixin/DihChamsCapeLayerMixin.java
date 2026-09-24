package dihclient.mixin;

import dihclient.util.DihChamsContext;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class DihChamsCapeLayerMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$hideCapeForChams(CallbackInfo ci) {
        if (DihChamsContext.active()) ci.cancel();
    }
}