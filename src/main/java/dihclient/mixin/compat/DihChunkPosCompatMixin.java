package dihclient.mixin.compat;

import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link dihclient.compat.ChunkPosCompat} to ChunkPos at runtime (1.21.11 only; skipped elsewhere). */
@Mixin(ChunkPos.class)
public abstract class DihChunkPosCompatMixin implements dihclient.compat.ChunkPosCompat {
}
