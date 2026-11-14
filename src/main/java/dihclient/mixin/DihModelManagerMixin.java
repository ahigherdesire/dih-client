package dihclient.mixin;

import dihclient.DihClientAddon;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(ModelManager.class)
public class DihModelManagerMixin {
    @Unique private static long dih$lastBrokenItemModelWarningMs;

    // NeoForge patches getItemModel to Map.get plus its own missing-model fallback (taken when the model is null).
    //? if neoforge {
    /*@WrapOperation(
        method = "getItemModel",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object dih$guardBrokenDynamicItemModel(Map<Identifier, ItemModel> models, Object id, Operation<Object> original) {
        try {
            return original.call(models, id);
        } catch (RuntimeException error) {
            if (!dih$isNullDynamicModelLoad(error)) throw error;
            dih$warnBrokenModel(id);
            return null;
        }
    }
    *///?} else {
    @WrapOperation(
        method = "getItemModel",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;getOrDefault(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object dih$guardBrokenDynamicItemModel(
            Map<Identifier, ItemModel> models,
            Object id,
            Object fallback,
            Operation<Object> original) {
        try {
            Object model = original.call(models, id, fallback);
            return model != null ? model : fallback;
        } catch (RuntimeException error) {
            if (!dih$isNullDynamicModelLoad(error)) throw error;

            dih$warnBrokenModel(id);
            return fallback;
        }
    }
    //?}

    @Unique
    private static void dih$warnBrokenModel(Object id) {
        long now = System.currentTimeMillis();
        if (now - dih$lastBrokenItemModelWarningMs > 10_000L) {
            dih$lastBrokenItemModelWarningMs = now;
            DihClientAddon.LOG.warn("[DIH] Resource/model pack returned a null item model for {}. Falling back to Minecraft's missing item model.", id);
        }
    }

    @Unique
    private static boolean dih$isNullDynamicModelLoad(Throwable error) {
        String className = error.getClass().getName();
        if (className.contains("CacheLoader$InvalidCacheLoadException")) return true;
        String message = error.getMessage();
        return message != null && message.contains("CacheLoader returned null");
    }
}
