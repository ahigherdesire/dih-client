package dihclient.mixin;

import dihclient.util.DihKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dihclient.modules.DihModule;
import dihclient.util.DihOverlayManager;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.CreativeModeTab;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class DihCreativeScreenMixin {
    @Shadow protected abstract void extractTabButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CreativeModeTab tab);

    @org.spongepowered.asm.mixin.Unique
    private static final ThreadLocal<Boolean> dih$inSafeRecall = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "checkTabHovering", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$blockCoveredTabHover(GuiGraphicsExtractor graphics, CreativeModeTab tab, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        DihModule module = DihModule.get();
        if (module == null || !module.isActive()) return;
        if (DihOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractTabButton", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$blockCoveredTabCursor(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CreativeModeTab tab, CallbackInfo ci) {
        if (dih$inSafeRecall.get()) return;
        DihModule module = DihModule.get();
        if (module == null || !module.isActive()) return;
        if (!DihOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) return;

        dih$inSafeRecall.set(Boolean.TRUE);
        try {
            this.extractTabButton(graphics, DihOverlayManager.HOVER_BLOCKED_MOUSE, DihOverlayManager.HOVER_BLOCKED_MOUSE, tab);
        } finally {
            dih$inSafeRecall.set(Boolean.FALSE);
        }
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        DihModule module = DihModule.get();
        if (module == null || !module.isActive()) return;

        if (DihOverlayManager.get().handleMouseClicked(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$mouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        DihModule module = DihModule.get();
        if (module == null || !module.isActive()) return;

        if (DihOverlayManager.get().handleMouseReleased(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$mouseDragged(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        DihModule module = DihModule.get();
        if (module == null || !module.isActive()) return;

        if (DihOverlayManager.get().handleMouseDragged(event.x(), event.y(), event.button(), deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        DihModule module = DihModule.get();
        if (module == null || !module.isActive()) return;

        if (DihOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        DihModule module = DihModule.get();
        if (module == null || !module.isActive()) return;

        if (DihOverlayManager.get().handleKeyPressed(input.key(), DihKeys.secondaryCode(input), input.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$charTyped(CharacterEvent input, CallbackInfoReturnable<Boolean> cir) {
        DihModule module = DihModule.get();
        if (module == null || !module.isActive()) return;

        if (DihOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            cir.setReturnValue(true);
        }
    }
}
