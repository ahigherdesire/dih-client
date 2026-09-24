package dihclient.mixin.accessor;

import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FishingHook.class)
public interface DihFishingHookAccessor {
    @Accessor("biting")
    boolean dih$isBiting();

    @Accessor("DATA_HOOKED_ENTITY")
    static EntityDataAccessor<Integer> dih$getHookedEntityData() {
        throw new AssertionError();
    }
}
