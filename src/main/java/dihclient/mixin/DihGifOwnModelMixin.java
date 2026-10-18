package dihclient.mixin;

import dihclient.modules.GifModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GIF module shield: marks the local player's third-person model so only their own shield gets the GIF. */
@Mixin(LivingEntityRenderer.class)
public abstract class DihGifOwnModelMixin {
    @Inject(method = "submit", at = @At("HEAD"), require = 0)
    private void dih$gifOwnModelStart(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                         CameraRenderState camera, CallbackInfo ci) {
        GifModule.setRenderingOwnItems(state instanceof AvatarRenderState avatar && GifModule.isLocalPlayer(avatar.id));
    }

    @Inject(method = "submit", at = @At("RETURN"), require = 0)
    private void dih$gifOwnModelEnd(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                       CameraRenderState camera, CallbackInfo ci) {
        GifModule.setRenderingOwnItems(false);
    }
}
