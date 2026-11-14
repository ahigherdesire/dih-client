package dihclient.mixin;

import dihclient.modules.NoRenderState;
import dihclient.util.DihClockInstanceAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No Render time override on 26.3, where time is read from clock instances. The class doesn't exist on 26.2 (there
 * DihNoRenderTimeMixin overrides getTotalTicks), so this mixin is skipped there.
 */
@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
public abstract class DihNoRenderClockInstanceMixin implements DihClockInstanceAccess {
    @Unique
    private boolean dih$overworld;

    @Override
    public void dih$markOverworld() {
        dih$overworld = true;
    }

    @Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$forceTime(CallbackInfoReturnable<Long> cir) {
        if (dih$overworld && NoRenderState.timeChanged()) {
            cir.setReturnValue(NoRenderState.timeTicks());
        }
    }
}
