package dihclient.mixin;

import dihclient.modules.NameCensorModule;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTeam.class)
public class DihPlayerTeamMixin {
    @Inject(method = "getFormattedName", at = @At("RETURN"), cancellable = true)
    private void dih$censorTeamName(CallbackInfoReturnable<MutableComponent> cir) {
        if (!NameCensorModule.isActive()) return;
        MutableComponent original = cir.getReturnValue();
        Component censored = NameCensorModule.censorServerComponent(original);
        if (censored != original) cir.setReturnValue(censored.copy());
    }
}
