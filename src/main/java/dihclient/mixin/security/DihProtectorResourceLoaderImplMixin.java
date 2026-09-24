package dihclient.mixin.security;

import dihclient.security.DihProtectorModResolver;
import dihclient.security.DihProtectorTracker;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.fabric.impl.resource.ResourceLoaderImpl;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("UnstableApiUsage")
@Mixin(ResourceLoaderImpl.class)
public class DihProtectorResourceLoaderImplMixin {
    @Inject(
        method = "registerBuiltinPack(Lnet/minecraft/resources/Identifier;Ljava/lang/String;Lnet/fabricmc/loader/api/ModContainer;Lnet/minecraft/network/chat/Component;Lnet/fabricmc/fabric/api/resource/v1/pack/PackActivationType;)Z",
        at = @At("RETURN"))
    private static void dih$trackBuiltinPackDefaults(Identifier id, String subPath, ModContainer container,
                                                        Component displayName, PackActivationType activationType,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || container == null) return;
        String mod = container.getMetadata().getId();
        DihProtectorTracker.addDefaultAllowedMod(mod);
        DihProtectorTracker.addDefaultAllowedMods(DihProtectorModResolver.dependenciesFor(mod));
    }
}
