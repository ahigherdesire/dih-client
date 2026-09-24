package dihclient.mixin.accessor;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEffectInstance.class)
public interface DihMobEffectInstanceAccessor {
    @Accessor("duration")
    void dih$setDuration(int duration);
}
