package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.3 {
/*@Mixin(net.minecraft.client.player.LocalPlayer.class)
*///?} else {
@Mixin(GameRenderer.class)
//?}
public abstract class DihNoRenderTotemMixin {
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$noTotemAnimation(ItemStack stack, CallbackInfo ci) {
        if (NoRenderState.noTotemAnimation() || dihclient.modules.AutoTotemModule.hidesTotemAnimation()) {
            ci.cancel();
        }
    }
}
