package dihclient.mixin;

import dihclient.modules.GifModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.equipment.ShieldModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GIF module shield: draws the local player's shield from the current GIF frame (a full 64x64 shield
 * texture, see DihGifTextures) instead of the atlas sprite. Banner patterns are skipped while it is on.
 */
@Mixin(ShieldSpecialRenderer.class)
public abstract class DihGifShieldMixin {
    @Shadow
    @Final
    private ShieldModel model;

    @Inject(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$gifShield(DataComponentMap components, PoseStack poseStack, SubmitNodeCollector collector,
                                  int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor, CallbackInfo ci) {
        Identifier frame = GifModule.shieldFrame();
        if (frame == null) return;
        //? if >=26.3 {
        /*collector.submitModel(model, Unit.INSTANCE, poseStack,
            hasFoil ? RenderTypes.entitySolidGlint(frame) : RenderTypes.entitySolid(frame),
            lightCoords, overlayCoords, outlineColor);
        *///?} else {
        collector.submitModel(model, Unit.INSTANCE, poseStack, RenderTypes.entitySolid(frame),
            lightCoords, overlayCoords, outlineColor, null);
        if (hasFoil) {
            collector.order(1).submitModel(model, Unit.INSTANCE, poseStack, RenderTypes.entityGlint(),
                lightCoords, overlayCoords, outlineColor, null);
        }
        //?}
        ci.cancel();
    }
}
