package dihclient.mixin;

import dihclient.modules.NoRenderState;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public abstract class DihNoRenderTimeMixin {
    // 26.3 reads time from clock instances; the overworld's is marked here and forced in DihNoRenderClockInstanceMixin.
    //? if >=26.3 {
    /*@Inject(method = "getInstance(Lnet/minecraft/core/Holder;)Lnet/minecraft/client/ClientClockManager$ClientClockInstance;",
        at = @At("RETURN"), require = 0)
    private void dih$markOverworld(Holder<WorldClock> definition,
                                   CallbackInfoReturnable<ClientClockManager.ClientClockInstance> cir) {
        if (cir.getReturnValue() instanceof dihclient.util.DihClockInstanceAccess clock && definition.is(WorldClocks.OVERWORLD)) {
            clock.dih$markOverworld();
        }
    }
    *///?} else {
    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$forceTime(Holder<WorldClock> definition, CallbackInfoReturnable<Long> cir) {
        if (NoRenderState.timeChanged() && definition.is(WorldClocks.OVERWORLD)) {
            cir.setReturnValue(NoRenderState.timeTicks());
        }
    }
    //?}
}
