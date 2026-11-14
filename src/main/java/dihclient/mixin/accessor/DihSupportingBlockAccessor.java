package dihclient.mixin.accessor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

/** The block an entity stands on, as movement tracks it (read by the parkour simulator). */
@Mixin(Entity.class)
public interface DihSupportingBlockAccessor {
    @Accessor("mainSupportingBlockPos")
    Optional<BlockPos> dih$getMainSupportingBlockPos();
}
