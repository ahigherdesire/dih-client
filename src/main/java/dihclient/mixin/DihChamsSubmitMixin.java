package dihclient.mixin;

import dihclient.util.DihChams;
import dihclient.util.DihChamsContext;
import dihclient.util.DihChamsRenderQueue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public abstract class DihChamsSubmitMixin {
    @Inject(method = "submitModel", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$chamsModel(Model model, Object object, PoseStack pose, RenderType type, int light, int overlay,
                                   //? if >=26.3 {
                                   /*int tint, net.minecraft.client.renderer.texture.UvMapping sprite, int outlineColor,
                                   *///?} else {
                                   int tint, TextureAtlasSprite sprite, int outlineColor,
                                   ModelFeatureRenderer.CrumblingOverlay crumbling,
                                   //?}
                                   CallbackInfo ci) {
        if (!DihChamsContext.active()) return;

        if (!DihChamsContext.claimBody()) {

            if (!DihChamsContext.drawArmor()) {
                ci.cancel();
            } else {
                DihChamsRenderQueue.submitLayer(model, object, pose.last().copy(), type,
                    light, overlay, tint, sprite);
                ci.cancel();
            }
            return;
        }

        RenderType visible = DihChams.chamsVisible(type);
        RenderType occluded = DihChams.chamsOccluded(type);
        if (visible == null || occluded == null) return;

        DihChamsRenderQueue.submitBody(model, object, pose.last().copy(), visible, occluded,
            DihChams.FULLBRIGHT, overlay, DihChamsContext.visible(), DihChamsContext.occluded(), sprite);
        ci.cancel();
    }
}
