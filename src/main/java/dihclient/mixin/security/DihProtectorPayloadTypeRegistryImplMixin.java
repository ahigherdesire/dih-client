package dihclient.mixin.security;

// Fabric only: targets Fabric API internals (left out of the NeoForge mixin config).
//? if fabric {

import dihclient.security.DihProtectorModResolver;
import dihclient.security.DihProtectorTracker;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("UnstableApiUsage")
@Mixin(PayloadTypeRegistryImpl.class)
public class DihProtectorPayloadTypeRegistryImplMixin {
    @Inject(method = "register", at = @At("RETURN"))
    private void dih$trackPayloadDefaultMod(CustomPacketPayload.Type<?> type, StreamCodec<?, ?> codec,
                                               CallbackInfoReturnable<CustomPacketPayload.TypeAndCodec<?, ?>> cir) {
        for (String mod : DihProtectorModResolver.modsFromStacktrace()) {
            DihProtectorTracker.addDefaultAllowedMod(mod);
            DihProtectorTracker.addDefaultAllowedMods(DihProtectorModResolver.dependenciesFor(mod));
        }
    }
}
//?}
