package dihclient.mixin;

import dihclient.modules.ModuleNameTagRenderer;
import dihclient.security.DihComponentSanity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class DihEntityNameTagMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void dih$suppressVanillaNameTag(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {

        if (state != null && state.nameTag != null && ModuleNameTagRenderer.tags(entity)) {
            state.nameTag = null;
        }
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void dih$scrubHostileNameTags(EntityRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                             net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector,
                                             net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {

        if (state == null) return;
        if (state.nameTag != null && !DihComponentSanity.isSafe(state.nameTag)) {
            state.nameTag = Component.literal("[unsafe name removed]").withStyle(ChatFormatting.DARK_GRAY);
        }
        if (state.scoreText != null && !DihComponentSanity.isSafe(state.scoreText)) {
            state.scoreText = Component.literal("[unsafe score removed]").withStyle(ChatFormatting.DARK_GRAY);
        }
    }
}
