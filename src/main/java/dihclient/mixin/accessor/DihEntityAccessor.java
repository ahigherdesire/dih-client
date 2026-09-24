package dihclient.mixin.accessor;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface DihEntityAccessor {
    @Invoker("isInvulnerableToBase")
    boolean dih$isInvulnerableToBase(DamageSource source);

    @Accessor("position")
    void dih$setPosition(Vec3 position);

    @Invoker("getInputVector")
    static Vec3 dih$getInputVector(Vec3 relative, float motion, float facing) {
        throw new AssertionError();
    }
}
