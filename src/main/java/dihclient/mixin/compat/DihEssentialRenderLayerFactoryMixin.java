package dihclient.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dihclient.mixin.DihRenderTypeStateAccessor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "gg.essential.model.backend.minecraft.RenderLayerFactory$Companion", remap = false)
public abstract class DihEssentialRenderLayerFactoryMixin {
    @WrapMethod(method = "createRenderLayer")
    private RenderType dih$createRenderLayerDirectly(String name, RenderSetup setup, Operation<RenderType> original) {
        return DihRenderTypeStateAccessor.dih$create(name, setup);
    }
}
