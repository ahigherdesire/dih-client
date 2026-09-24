package dihclient.mixin.security;

import dihclient.security.DihFromPacketAccess;
import dihclient.security.DihProtector;
import dihclient.security.DihProtectorTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Mixin(KeybindContents.class)
public abstract class DihProtectorKeybindContentsMixin implements DihFromPacketAccess {

    @Shadow @Final private String name;

    @Unique
    private boolean dih$fromPacket;

    @Unique
    private Object dih$cachedBlocked;

    @Override
    public void dih$setFromPacket() {
        this.dih$fromPacket = true;
    }

    @WrapOperation(
        method = "getNestedComponent",
        at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;")
    )
    private Object dih$interceptKeybind(Supplier<?> supplier, Operation<Object> original) {
        if (!this.dih$fromPacket) return original.call(supplier);
        if (!DihProtector.shouldProtectTranslationKeys()) return original.call(supplier);

        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable ignored) {
            return original.call(supplier);
        }
        if (mc == null || mc.hasSingleplayerServer()) return original.call(supplier);

        if (!DihProtectorTracker.shouldBlockKeybind(name)) return original.call(supplier);

        if (dih$cachedBlocked != null) return dih$cachedBlocked;
        Component replacement = Component.literal(name);
        dih$cachedBlocked = replacement;
        return replacement;
    }
}
