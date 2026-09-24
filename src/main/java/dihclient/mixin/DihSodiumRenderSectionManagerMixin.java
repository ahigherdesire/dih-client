package dihclient.mixin;

import dihclient.modules.ModuleRenderUtil;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public abstract class DihSodiumRenderSectionManagerMixin {
    @Inject(method = "shouldUseOcclusionCulling", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$disableSodiumOcclusion(Camera camera, boolean spectator, CallbackInfoReturnable<Boolean> cir) {
        if (ModuleRenderUtil.shouldBypassOcclusionCulling()) {
            cir.setReturnValue(false);
        }
    }
}
