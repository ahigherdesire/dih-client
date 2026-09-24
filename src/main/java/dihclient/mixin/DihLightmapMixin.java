package dihclient.mixin;

import dihclient.modules.ModuleRenderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Vector4f;

@Mixin(Lightmap.class)
public abstract class DihLightmapMixin {
    @Shadow
    @Final
    private GpuTexture texture;

    @Unique
    private boolean dih$wasBright;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void dih$fullbrightLightmap(LightmapRenderState renderState, CallbackInfo ci) {
        if (ModuleRenderUtil.hasBrightLightmapWork()) {
            var profiler = Profiler.get();
            profiler.push("dih_lightmap");
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F));
            profiler.pop();
            dih$wasBright = true;
            ci.cancel();
            return;
        }

        if (dih$wasBright) {
            dih$wasBright = false;
            renderState.needsUpdate = true;
        }
    }
}
