package dihclient.mixin;

import net.minecraft.client.multiplayer.resolver.AddressCheck;
import dihclient.render.mc.DihAddressChecks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AddressCheck.class)
public interface DihBlocklistBypassMixin {
    @Inject(method = "createFromService", at = @At("HEAD"), cancellable = true, require = 0)
    private static void dih$starveBlocklist(CallbackInfoReturnable<AddressCheck> cir) {
        cir.setReturnValue(DihAddressChecks.allowAll());
    }
}
