package dihclient.mixin;

import dihclient.modules.ModuleRenderUtil;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess", remap = false)
public abstract class DihSodiumLightDataAccessMixin {
    @Shadow(remap = false) protected BlockAndTintGetter level;
    @Shadow(remap = false) @Final private BlockPos.MutableBlockPos pos;

    @ModifyVariable(method = "compute", at = @At(value = "TAIL"), name = "bl", remap = false)
    private int dih$xraySodiumBlockLight(int bl) {
        if (!ModuleRenderUtil.hasXrayRenderWork()) return bl;
        BlockState state = level.getBlockState(pos);
        if (!ModuleRenderUtil.isXrayBlocked(state, pos)) return ModuleRenderUtil.sodiumFullLight();
        return bl;
    }

    @ModifyVariable(method = "compute", at = @At(value = "STORE"), name = "sl", remap = false)
    private int dih$fullbrightSodiumSkyLight(int sl) {
        if (!ModuleRenderUtil.hasFullbrightLuminanceWork()) return sl;
        int boosted = ModuleRenderUtil.fullbrightLuminance(LightLayer.SKY);
        return boosted > sl ? boosted : sl;
    }

    @ModifyVariable(method = "compute", at = @At(value = "STORE"), name = "bl", remap = false)
    private int dih$fullbrightSodiumBlockLight(int bl) {
        if (!ModuleRenderUtil.hasFullbrightLuminanceWork()) return bl;
        int boosted = ModuleRenderUtil.fullbrightLuminance(LightLayer.BLOCK);
        return boosted > bl ? boosted : bl;
    }
}
