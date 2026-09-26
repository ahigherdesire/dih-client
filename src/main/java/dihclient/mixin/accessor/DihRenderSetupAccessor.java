package dihclient.mixin.accessor;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** RenderSetup's package-private texture bindings (values are RenderSetup.TextureBinding, see DihTextureBindingAccessor). */
@Mixin(RenderSetup.class)
public interface DihRenderSetupAccessor {
    @Accessor("textures")
    Map<String, ?> dih$textures();
}
