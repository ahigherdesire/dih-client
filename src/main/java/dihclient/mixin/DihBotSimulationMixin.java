package dihclient.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
//? if >=26.3 {
/*import dihclient.util.multi.MultiPilot;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

/**
 * 26.3 made {@code Entity.canSimulateMovement()} final, so a piloted bot can no longer override it (26.2 does that
 * in {@link DihBotPilotMixin}); here it is answered at the source instead. Empty on 26.2.
 */
@Mixin(Entity.class)
public class DihBotSimulationMixin {
    //? if >=26.3 {
    /*@Inject(method = "canSimulateMovement", at = @At("HEAD"), cancellable = true)
    private void dih$pilotSimulatesBot(CallbackInfoReturnable<Boolean> cir) {
        if (MultiPilot.isManualControlEntity((Entity) (Object) this)) cir.setReturnValue(true);
    }
    *///?}
}
