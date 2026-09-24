package dihclient.mixin;

import dihclient.modules.ModuleRenderUtil;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SectionOcclusionGraph.class)
public class DihSectionOcclusionGraphMixin {
    @ModifyVariable(method = "runUpdates", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean dih$disableSmartCull(boolean smartCull) {
        return ModuleRenderUtil.shouldBypassOcclusionCulling() ? false : smartCull;
    }
}
