package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class DihNoRenderEatParticleMixin {
    @Inject(method = "spawnItemParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noEatParticles(ItemStack stack, int count, CallbackInfo ci) {
        if (NoRenderState.noEatingParticles()) ci.cancel();
    }
}
