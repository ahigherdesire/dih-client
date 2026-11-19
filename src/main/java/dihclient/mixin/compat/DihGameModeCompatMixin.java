package dihclient.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link dihclient.compat.GameModeCompat} to MultiPlayerGameMode at runtime (1.21.11 only; skipped elsewhere). */
@Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
public abstract class DihGameModeCompatMixin implements dihclient.compat.GameModeCompat {
}
