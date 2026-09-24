package dihclient.mixin;

import dihclient.modules.InventoryTweaksModule;
import net.minecraft.client.gui.BundleMouseActions;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BundleMouseActions.class)
public abstract class DihBundleMouseActionsMixin {
    @Redirect(
        method = {"onMouseScrolled", "toggleSelectedBundleItem"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BundleItem;getNumberOfItemsToShow(Lnet/minecraft/world/item/ItemStack;)I"),
        require = 0
    )
    private int dih$uncapBundleScroll(ItemStack stack) {
        return InventoryTweaksModule.bundleScrollLimit(stack, BundleItem.getNumberOfItemsToShow(stack));
    }
}
