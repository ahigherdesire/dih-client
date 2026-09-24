package dihclient.mixin.accessor;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Player.class)
public interface DihPlayerAccessor {
    @Invoker("getEnchantedDamage")
    float dih$getEnchantedDamage(Entity entity, float dmg, DamageSource damageSource);

    @Invoker("getBlockSpeedFactor")
    float dih$getBlockSpeedFactor();
}
