package dihclient.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link dihclient.compat.WindowCompat} to Window at runtime (1.21.11 only; skipped elsewhere). */
@Mixin(com.mojang.blaze3d.platform.Window.class)
public abstract class DihWindowCompatMixin implements dihclient.compat.WindowCompat {
}
