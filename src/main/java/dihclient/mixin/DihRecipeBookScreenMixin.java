package dihclient.mixin;

import dihclient.modules.DihModule;
import dihclient.util.DihOverlayManager;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractRecipeBookScreen.class)
public abstract class DihRecipeBookScreenMixin {

    @Unique
    private boolean dih$isActive() {
        DihModule module = DihModule.get();
        return module != null && module.isActive();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void dih$handleOverlayClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!dih$isActive()) return;

        DihOverlayManager manager = DihOverlayManager.get();
        if (manager.handleMouseClicked(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void dih$handleOverlayDrag(MouseButtonEvent click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (!dih$isActive()) return;

        if (DihOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void dih$handleOverlayKeys(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!dih$isActive()) return;

        if (DihOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void dih$handleOverlayChars(CharacterEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!dih$isActive()) return;

        if (DihOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            cir.setReturnValue(true);
        }
    }
}
