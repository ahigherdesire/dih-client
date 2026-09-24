package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class DihNoRenderBlockEntityMixin {

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noBlockEntity(BlockEntity blockEntity, float partialTick,
                                      ModelFeatureRenderer.CrumblingOverlay crumbling, boolean crumblingOnly,
                                      CallbackInfoReturnable<BlockEntityRenderState> cir) {
        if (blockEntity != null && NoRenderState.noBlockEntity(blockEntity.getBlockState().getBlock())) {
            cir.setReturnValue(null);
        }
    }
}
