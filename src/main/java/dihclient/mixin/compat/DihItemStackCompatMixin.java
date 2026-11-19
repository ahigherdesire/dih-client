package dihclient.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link dihclient.compat.ItemStackCompat} to ItemStack at runtime (1.21.11 only; skipped elsewhere). */
@Mixin(net.minecraft.world.item.ItemStack.class)
public abstract class DihItemStackCompatMixin implements dihclient.compat.ItemStackCompat {
}
