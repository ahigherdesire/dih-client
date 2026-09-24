package dihclient.mixin.accessor;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface DihLivingEntityAccessor {
    @Accessor("attackStrengthTicker")
    int dih$getAttackStrengthTicker();

    @Accessor("autoSpinAttackDmg")
    float dih$getAutoSpinAttackDmg();

    @Invoker("getDamageAfterArmorAbsorb")
    float dih$getDamageAfterArmorAbsorb(DamageSource source, float amount);

    @Invoker("getDamageAfterMagicAbsorb")
    float dih$getDamageAfterMagicAbsorb(DamageSource source, float amount);

    @Invoker("calculateFallDamage")
    int dih$calculateFallDamage(double fallDistance, float damageMultiplier);
}
