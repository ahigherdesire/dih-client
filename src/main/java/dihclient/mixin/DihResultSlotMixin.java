package dihclient.mixin;

import dihclient.modules.DihModule;
import dihclient.util.DihSharedState;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResultSlot.class)
public abstract class DihResultSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void dih$xcarryMayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        DihSharedState shared = DihSharedState.get();
        DihModule module = DihModule.get();
        if (shared.isXCarryForced() || (module != null && module.isXCarryEnabled() && module.isXCarryUseCrafting())) {
            cir.setReturnValue(true);
        }
    }
}
