package dihclient.mixin.accessor;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Ticks until a held jump key jumps again (read by the parkour simulator). */
@Mixin(LivingEntity.class)
public interface DihJumpDelayAccessor {
    @Accessor("noJumpDelay")
    int dih$getNoJumpDelay();
}
