package dihclient.mixin.accessor;

import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The texture of a (package-private) RenderSetup.TextureBinding. */
@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public interface DihTextureBindingAccessor {
    @Accessor("location")
    Identifier dih$location();
}
