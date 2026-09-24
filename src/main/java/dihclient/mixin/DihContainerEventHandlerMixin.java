package dihclient.mixin;

import dihclient.util.DihHudManager;
import dihclient.util.DihUiScale;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerEventHandler.class)
public interface DihContainerEventHandlerMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void dih$musicDisplayClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {

        if (dihclient.util.DihLiteVariant.enabled()) return;
        if (!((Object) this instanceof Screen screen)) return;
        if (event.button() != 0) return;
        if (DihHudManager.musicDisplayMouseClicked(
                DihUiScale.toVirtualInt(event.x()), DihUiScale.toVirtualInt(event.y()), screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void dih$musicDisplayDrag(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (dihclient.util.DihLiteVariant.enabled()) return;
        if (!((Object) this instanceof Screen screen)) return;
        if (DihHudManager.musicDisplayMouseDragged(
                DihUiScale.toVirtualInt(event.x()), DihUiScale.toVirtualInt(event.y()), screen)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void dih$musicDisplayRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (dihclient.util.DihLiteVariant.enabled()) return;
        if (!((Object) this instanceof Screen screen)) return;
        if (DihHudManager.musicDisplayMouseReleased(screen)) {
            cir.setReturnValue(true);
        }
    }
}
