package dihclient.mixin;

import dihclient.modules.ModuleMovementUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BedBlock.class)
public class DihBedBlockMixin {
    // 26.3 beds don't override fallOn; their bounce is a restitution, cancelled in DihEntityMovementMixin.
    //? if <26.3 {
    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void dih$noFallAntiBounce(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance, CallbackInfo ci) {
        if (ModuleMovementUtil.shouldCancelNoFallBounce(entity)) ci.cancel();
    }
    //?}
}
