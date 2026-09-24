package dihclient.mixin;

import dihclient.modules.GoldenLeverModule;
import dihclient.modules.NameCensorModule;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class DihItemStackMixin {
    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void dih$goldenLeverName(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (GoldenLeverModule.shouldStyle(stack)) {
            cir.setReturnValue(GoldenLeverModule.leverName());
        }
    }

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void dih$censorHoverName(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(NameCensorModule.censorComponent(cir.getReturnValue()));
    }
}
