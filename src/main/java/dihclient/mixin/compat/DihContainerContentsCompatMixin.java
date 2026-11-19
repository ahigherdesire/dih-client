package dihclient.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link dihclient.compat.ContainerContentsCompat} to ItemContainerContents at runtime (1.21.11 only; skipped elsewhere). */
@Mixin(net.minecraft.world.item.component.ItemContainerContents.class)
public abstract class DihContainerContentsCompatMixin implements dihclient.compat.ContainerContentsCompat {
}
