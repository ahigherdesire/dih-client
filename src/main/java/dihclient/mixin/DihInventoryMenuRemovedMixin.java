package dihclient.mixin;

import dihclient.modules.DihModule;
import dihclient.util.DihSharedState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenu.class)
public abstract class DihInventoryMenuRemovedMixin {

    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void dih$skipDrainWhenXCarryForced(Player player, CallbackInfo ci) {
        if (!(player instanceof LocalPlayer)) return;
        DihSharedState shared = DihSharedState.get();
        DihModule module = DihModule.get();
        boolean passive = module != null && module.isXCarryEnabled();

        if (!passive && !shared.isXCarryForced()) return;

        InventoryMenu self = (InventoryMenu) (Object) this;
        if (player.inventoryMenu != self) return;

        ci.cancel();
    }
}
