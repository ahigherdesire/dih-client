package dihclient.mixin;

import dihclient.modules.NameCensorModule;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerScoreEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerScoreEntry.class)
public class DihPlayerScoreEntryMixin {
    @Inject(method = "ownerName", at = @At("RETURN"), cancellable = true)
    private void dih$censorScoreOwner(CallbackInfoReturnable<Component> cir) {
        cir.setReturnValue(NameCensorModule.censorServerComponent(cir.getReturnValue()));
    }
}
