package dihclient.mixin.security;

import dihclient.security.DihProtectorModResolver;
import dihclient.security.DihProtectorTracker;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashSet;

@Mixin(targets = "net.fabricmc.fabric.impl.client.keymapping.KeyMappingRegistryImpl")
public class DihProtectorKeyMappingRegistryImplMixin {
    @Inject(method = "registerKeyMapping", at = @At("RETURN"))
    private static void dih$trackModKeyMapping(KeyMapping keyMapping, CallbackInfoReturnable<KeyMapping> cir) {
        LinkedHashSet<String> mods = DihProtectorModResolver.modsFromStacktrace();
        if (!mods.isEmpty()) DihProtectorTracker.addModKeybind(keyMapping.getName(), mods.getLast());
    }
}
