package dihclient.mixin;

import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
//? if !fabric {
/*import dihclient.platform.DihPlatform;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * Configuration-phase connection events for DihPlatform where the loader has none (NeoForge, Forge). Fabric API fires its
 * own ClientConfigurationConnectionEvents from the same places, so this mixin is empty there.
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class DihPlatformHooksMixin {
    //? if !fabric {
    /*@Inject(method = "<init>", at = @At("TAIL"))
    private void dih$configurationInit(CallbackInfo ci) {
        DihPlatform.fireConfigurationInit((ClientConfigurationPacketListenerImpl) (Object) this);
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void dih$configurationDisconnect(DisconnectionDetails details, CallbackInfo ci) {
        DihPlatform.fireConfigurationDisconnect();
    }
    *///?}
}
