package dihclient.mixin;

import dihclient.modules.ViewmodelState;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Viewmodel: the "item used" bob and re-equip rules, in the hand renderer (26.2) or FirstPersonHandsAndItems (26.3). */
//? if >=26.3 {
/*@Mixin(net.minecraft.client.player.FirstPersonHandsAndItems.class)
*///?} else {
@Mixin(ItemInHandRenderer.class)
//?}
public class DihViewmodelStateMixin {
    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$ignorePlace(InteractionHand hand, CallbackInfo ci) {
        if (ViewmodelState.active() && ViewmodelState.ignorePlace()) ci.cancel();
    }

    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"), cancellable = true, require = 0)
    //? if >=26.3 {
    /*private void dih$ignoreAmount(ItemStack currentlyVisibleItem, ItemStack expectedItem,
                                     net.minecraft.client.player.LocalPlayer player, CallbackInfoReturnable<Boolean> cir) {
    *///?} else {
    private void dih$ignoreAmount(ItemStack currentlyVisibleItem, ItemStack expectedItem,
                                     CallbackInfoReturnable<Boolean> cir) {
    //?}
        if (ViewmodelState.active() && !cir.getReturnValueZ()) {
            cir.setReturnValue(!ViewmodelState.equipOffsetOn()
                || (currentlyVisibleItem.getCount() == expectedItem.getCount() || ViewmodelState.ignoreAmount())
                && ItemStack.isSameItemSameComponents(currentlyVisibleItem, expectedItem));
        }
    }
}
