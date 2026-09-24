package dihclient.mixin.security;

import dihclient.security.DihProtectorTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.BooleanSupplier;

@Mixin(Options.class)
public class DihProtectorOptionsMixin {
    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;ILnet/minecraft/client/KeyMapping$Category;)Lnet/minecraft/client/KeyMapping;"))
    private KeyMapping dih$keyMapping(String name, int key, KeyMapping.Category category, Operation<KeyMapping> original) {
        DihProtectorTracker.addVanillaKeybind(name);
        return original.call(name, key, category);
    }

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;)Lnet/minecraft/client/KeyMapping;"))
    private KeyMapping dih$keyMapping(String name, InputConstants.Type type, int key, KeyMapping.Category category, Operation<KeyMapping> original) {
        DihProtectorTracker.addVanillaKeybind(name);
        return original.call(name, type, key, category);
    }

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;I)Lnet/minecraft/client/KeyMapping;"))
    private KeyMapping dih$keyMapping(String name, InputConstants.Type type, int key, KeyMapping.Category category, int order, Operation<KeyMapping> original) {
        DihProtectorTracker.addVanillaKeybind(name);
        return original.call(name, type, key, category, order);
    }

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;ILnet/minecraft/client/KeyMapping$Category;Ljava/util/function/BooleanSupplier;Z)Lnet/minecraft/client/ToggleKeyMapping;"))
    private ToggleKeyMapping dih$toggleKeyMapping(String name, int key, KeyMapping.Category category, BooleanSupplier needsToggle,
                                                     boolean shouldRestore, Operation<ToggleKeyMapping> original) {
        DihProtectorTracker.addVanillaKeybind(name);
        return original.call(name, key, category, needsToggle, shouldRestore);
    }

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;Ljava/util/function/BooleanSupplier;Z)Lnet/minecraft/client/ToggleKeyMapping;"))
    private ToggleKeyMapping dih$toggleKeyMapping(String name, InputConstants.Type type, int key, KeyMapping.Category category,
                                                     BooleanSupplier needsToggle, boolean shouldRestore,
                                                     Operation<ToggleKeyMapping> original) {
        DihProtectorTracker.addVanillaKeybind(name);
        return original.call(name, type, key, category, needsToggle, shouldRestore);
    }
}
