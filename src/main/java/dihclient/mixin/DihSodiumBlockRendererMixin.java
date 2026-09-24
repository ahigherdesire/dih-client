package dihclient.mixin;

import dihclient.modules.GoldenLeverModule;
import dihclient.modules.ModuleRenderUtil;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class DihSodiumBlockRendererMixin {
    @Unique private int dih$xrayAlpha = -1;
    @Unique private boolean dih$goldenLever;
    @Unique private int dih$darkenTint = -1;

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void dih$xraySodiumBlockStart(@Coerce Object model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        boolean xrayActive = ModuleRenderUtil.hasXrayRenderWork();
        boolean goldenLeverActive = GoldenLeverModule.isStylingActive();
        boolean darkenActive = ModuleRenderUtil.hasWorldDarkenWork();
        if (!xrayActive && !goldenLeverActive && !darkenActive) {
            dih$xrayAlpha = -1;
            dih$goldenLever = false;
            dih$darkenTint = -1;
            return;
        }
        dih$xrayAlpha = xrayActive ? ModuleRenderUtil.xrayAlpha(state, pos) : -1;
        dih$goldenLever = goldenLeverActive && GoldenLeverModule.shouldStyle(state);
        dih$darkenTint = darkenActive ? ModuleRenderUtil.worldDarkenTint(state, pos) : -1;
        if (dih$xrayAlpha == 0) ci.cancel();
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private void dih$xraySodiumBlockEnd(@Coerce Object model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        dih$xrayAlpha = -1;
        dih$goldenLever = false;
        dih$darkenTint = -1;
    }

    @Inject(method = "processQuad", at = @At("HEAD"))
    private void dih$xraySodiumBlockMaterial(@Coerce Object quad, CallbackInfo ci) {
        int alpha = dih$xrayAlpha;
        if (dih$goldenLever) ModuleRenderUtil.applySodiumQuadTint(quad, GoldenLeverModule.GOLD_TINT);
        if (dih$darkenTint != -1) ModuleRenderUtil.applySodiumQuadTint(quad, dih$darkenTint);
        if (alpha < 0) return;
        ModuleRenderUtil.applySodiumQuadAlpha(quad, alpha);
        if (alpha > 0 && alpha < 255) ModuleRenderUtil.applySodiumQuadRenderLayer(quad, ChunkSectionLayer.TRANSLUCENT);
    }
}
