package dihclient.mixin.compat;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Proxy;

@Pseudo
@Mixin(targets = "com.moulberry.flashback.mixin.record.MixinConnection", remap = false)
public abstract class DihFlashbackConnectionMixin {

    @Inject(method = "genericsFtw", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void dih$skipMultiBotTraffic(Packet<?> packet, PacketListener packetListener, CallbackInfo ci) {
        if (dih$isMultiBotListener(packetListener)) {
            ci.cancel();
        }
    }

    private static boolean dih$isMultiBotListener(PacketListener listener) {
        if (listener == null || !Proxy.isProxyClass(listener.getClass())) return false;
        try {
            return Proxy.getInvocationHandler(listener).getClass().getName().startsWith("dihclient.");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
