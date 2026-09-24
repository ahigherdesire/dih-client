package dihclient.mixin;

import dihclient.modules.AutoArmorModule;
import dihclient.modules.AutoTotemModule;
import dihclient.modules.BedDefenderModule;
import dihclient.modules.ScaffoldModule;
import dihclient.modules.SafeWalkModule;
import dihclient.util.DihSilentAim;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardInput.class)
public abstract class DihScaffoldInputMixin extends ClientInput {
    @ModifyExpressionValue(
        method = "tick",
        at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;")
    )
    private Input dih$optionalScaffoldStabilization(Input original) {
        Input scaffold = ScaffoldModule.modifyMovementInput(this, original);
        Input aura = DihSilentAim.modifyMovementInput(this, scaffold);

        Input bed = BedDefenderModule.modifyMovementInput(this, aura);
        Input safe = SafeWalkModule.modifyMovementInput(this, bed);

        Input totem = AutoTotemModule.modifyMovementInput(this, safe);
        return AutoArmorModule.modifyMovementInput(this, totem);
    }

}
