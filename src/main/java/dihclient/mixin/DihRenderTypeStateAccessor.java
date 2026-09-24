package dihclient.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface DihRenderTypeStateAccessor {
    @Accessor("state")
    RenderSetup dih$getState();

    @Invoker("create")
    static RenderType dih$create(String name, RenderSetup setup) {
        throw new AssertionError("mixin invoker not applied");
    }
}
