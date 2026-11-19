package dihclient.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link dihclient.compat.ChargedProjectilesCompat} to ChargedProjectiles at runtime (1.21.11 only; skipped elsewhere). */
@Mixin(net.minecraft.world.item.component.ChargedProjectiles.class)
public abstract class DihChargedProjectilesCompatMixin implements dihclient.compat.ChargedProjectilesCompat {
}
