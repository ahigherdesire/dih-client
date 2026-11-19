package dihclient.mixin;

import dihclient.util.DihFreecamHighlightRenderer;
import dihclient.util.DihWorldHighlightRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class DihLevelRendererOutlineMixin {
    //? if >=26.2 {
    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void dih$hideVanillaOutlineInFreecam(PoseStack poseStack, SubmitNodeCollector collector,
                                                    LevelRenderState state, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "renderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void dih$hideVanillaOutlineInFreecam(net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers,
                                                    PoseStack poseStack, boolean translucent,
                                                    LevelRenderState state, CallbackInfo ci) {
    *///?}

        if (DihFreecamHighlightRenderer.isReplacingVanillaOutline() || DihWorldHighlightRenderer.isActive()) {
            ci.cancel();
        }
    }
}
