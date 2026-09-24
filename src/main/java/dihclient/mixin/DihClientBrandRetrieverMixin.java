package dihclient.mixin;

import dihclient.modules.DihModule;
import dihclient.security.DihProtector;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public class DihClientBrandRetrieverMixin {
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dih$spoofClientBrand(CallbackInfoReturnable<String> cir) {

        if (DihProtector.isFullExternalProtectorPresent()) return;

        DihModule module = DihModule.get();
        if (module != null && module.isSpoofClientVanilla()) {
            cir.setReturnValue("vanilla");
        }
    }
}
