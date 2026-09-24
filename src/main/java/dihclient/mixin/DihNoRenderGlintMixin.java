package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class DihNoRenderGlintMixin {
    @ModifyVariable(method = "setFoilType", at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemStackRenderState.FoilType dih$noGlint(ItemStackRenderState.FoilType foilType) {
        return NoRenderState.noEnchantGlint() ? ItemStackRenderState.FoilType.NONE : foilType;
    }
}
