package dihclient.mixin;

import dihclient.modules.ModuleRenderUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class DihBlockRenderFaceMixin {
    // Descriptor pinned: Forge adds a positional overload (its own copy of the check), hooked below.
    @Inject(method = "shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private static void dih$xrayForceSelectedFaces(BlockState state, BlockState neighborState, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!ModuleRenderUtil.hasXrayRenderWork()) return;

        if (ModuleRenderUtil.shouldForceXrayFace(state, neighborState, direction)) cir.setReturnValue(true);
    }

    //? if forge {
    /*@Inject(method = "shouldRenderFace(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
        at = @At("HEAD"), cancellable = true)
    private static void dih$xrayForceSelectedFacesForge(net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
                                                        BlockState state, BlockState neighborState, Direction direction,
                                                        CallbackInfoReturnable<Boolean> cir) {
        dih$xrayForceSelectedFaces(state, neighborState, direction, cir);
    }
    *///?}
}
