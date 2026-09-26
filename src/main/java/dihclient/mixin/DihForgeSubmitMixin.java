package dihclient.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if forge {
/*import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dihclient.platform.DihPlatform;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Forge: DihPlatform's world-geometry hook, at the point NeoForge fires SubmitCustomGeometryEvent (after the block
 * outline, before gizmos). Fabric and NeoForge have an event for it, so this is empty there.
 */
@Mixin(LevelRenderer.class)
public abstract class DihForgeSubmitMixin {
    //? if forge {
    /*@Inject(method = "submitFeatures", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;finalizeGizmoCollection()V"))
    private void dih$submitCustomGeometry(LevelRenderState state, SubmitNodeCollector collector, boolean translucent,
                                          CallbackInfo ci, @Local PoseStack pose) {
        DihPlatform.fireSubmits(state, collector, pose);
    }
    *///?}
}
