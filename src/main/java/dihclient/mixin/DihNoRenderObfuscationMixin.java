package dihclient.mixin;

import dihclient.modules.NoRenderState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Font.class)
public abstract class DihNoRenderObfuscationMixin {
    @ModifyExpressionValue(
        method = "getGlyph",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Style;isObfuscated()Z"),
        require = 0)
    private boolean dih$noObfuscation(boolean original) {
        return original && !NoRenderState.noObfuscation();
    }
}
