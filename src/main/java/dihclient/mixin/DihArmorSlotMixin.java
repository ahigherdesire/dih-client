package dihclient.mixin;

import dihclient.modules.DihModule;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public abstract class DihArmorSlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void dih$mayPlaceForXCarry(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (dih$armorAllowed()) cir.setReturnValue(true);
    }

    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)
    private void dih$maxStackSizeForXCarry(CallbackInfoReturnable<Integer> cir) {
        if (dih$armorAllowed()) cir.setReturnValue(64);
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean dih$armorAllowed() {
        DihModule mod = DihModule.get();
        boolean modAllow = mod != null && mod.isXCarryUseArmor();
        boolean bypass = dihclient.util.DihSharedState.get().isXCarryArmorBypass();
        return modAllow || bypass;
    }
}
