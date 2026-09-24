package dihclient.mixin.accessor;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Font.class)
public interface DihFontAccessor {
    @Accessor("provider")
    Font.Provider dih$provider();
}
