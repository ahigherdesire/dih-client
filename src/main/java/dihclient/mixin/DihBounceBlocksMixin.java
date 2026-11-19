package dihclient.mixin;

import dihclient.modules.ModuleMovementUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.SlimeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 26.1 only (see {@link DihMixinPlugin}): slime blocks and beds bounce in their own
 * {@code updateEntityMovementAfterFallOn}; 26.2 moved that to {@code Block.getBounceRestitution}, which
 * {@link DihEntityMovementMixin} handles. Cancelling lands like a plain block does.
 */
@Mixin({SlimeBlock.class, BedBlock.class})
public class DihBounceBlocksMixin {
    @Inject(method = "updateEntityMovementAfterFallOn", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$antiBounce(BlockGetter level, Entity entity, CallbackInfo ci) {
        if (ModuleMovementUtil.shouldCancelNoFallBounce(entity)) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
            ci.cancel();
        }
    }
}
