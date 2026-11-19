package dihclient.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link dihclient.compat.BlockStateCompat} to BlockStateBase at runtime (1.21.11 only; skipped elsewhere). */
@Mixin(net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase.class)
public abstract class DihBlockStateCompatMixin implements dihclient.compat.BlockStateCompat {
}
