package dihclient.mixin.security;

//? if >=26.1 {
import dihclient.security.DihRegistryComponentCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DataComponentInitializers.SingleComponentInitializer.class)
public interface DihDataComponentInitializerMixin {

    @SuppressWarnings("UnresolvedMixinReference")
    @WrapOperation(
        method = "lambda$asInitializer$0",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/component/DataComponentInitializers$SingleComponentInitializer;create(Lnet/minecraft/core/HolderLookup$Provider;)Ljava/lang/Object;"
        ),
        require = 0
    )
    private static Object dih$skipMissingRemoteComponent(
        DataComponentInitializers.SingleComponentInitializer<?> self,
        HolderLookup.Provider context,
        Operation<Object> original
    ) {
        try {
            return original.call(self, context);
        } catch (RuntimeException error) {
            if (!DihRegistryComponentCompat.shouldSkipMissingComponentData(error)) {
                throw error;
            }
            DihRegistryComponentCompat.reportSkippedMissingComponent(error);

            return null;
        }
    }
}
//?} else {
/*import org.spongepowered.asm.mixin.Mixin;

// Minecraft 1.21.11 has no target for this mixin; it is skipped there (DihMixinPlugin) and kept empty.
@Mixin(net.minecraft.client.Minecraft.class)
public abstract interface DihDataComponentInitializerMixin {
}
*///?}
