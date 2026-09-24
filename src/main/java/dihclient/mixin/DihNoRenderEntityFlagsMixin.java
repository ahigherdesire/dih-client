package dihclient.mixin;

import dihclient.modules.NoRenderState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class DihNoRenderEntityFlagsMixin {
    @ModifyReturnValue(method = "isInvisibleTo", at = @At("RETURN"), require = 0)
    private boolean dih$showInvisible(boolean original) {
        return original && !NoRenderState.noInvisibility();
    }

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (NoRenderState.noGlowing()) cir.setReturnValue(false);
    }
}
