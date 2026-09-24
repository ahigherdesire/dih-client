package dihclient.mixin;

import dihclient.modules.ModuleRenderUtil;
import dihclient.util.DihChamsContext;
import dihclient.util.DihChamsHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class DihChamsLivingEntityMixin {
    @Inject(method = "submit", at = @At("HEAD"), require = 0)
    private void dih$chamsContextStart(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                          CameraRenderState camera, CallbackInfo ci) {
        DihChamsContext.clear();
        if (ModuleRenderUtil.hasChamsWork() && state instanceof DihChamsHolder holder && holder.dih$chamsActive()) {
            DihChamsContext.set(holder.dih$chamsVisible(), holder.dih$chamsOccluded(),
                ModuleRenderUtil.chamsDrawArmor());
        }
    }

    @Inject(method = "submit", at = @At("RETURN"), require = 0)
    private void dih$chamsContextEnd(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                        CameraRenderState camera, CallbackInfo ci) {
        DihChamsContext.clear();
    }
}
