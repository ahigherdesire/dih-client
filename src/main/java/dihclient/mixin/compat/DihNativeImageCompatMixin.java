package dihclient.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link dihclient.compat.NativeImageCompat} to NativeImage at runtime (1.21.11 only; skipped elsewhere). */
@Mixin(com.mojang.blaze3d.platform.NativeImage.class)
public abstract class DihNativeImageCompatMixin implements dihclient.compat.NativeImageCompat {
}
