package dihclient.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link dihclient.compat.CameraCompat} to Camera at runtime (1.21.11 only; skipped elsewhere). */
@Mixin(net.minecraft.client.Camera.class)
public abstract class DihCameraCompatMixin implements dihclient.compat.CameraCompat {
}
