package dihclient.mixin;

import dihclient.modules.DihModule;
import dihclient.util.DihOverlayManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pinned DIH windows stay drawn while chat is open, and can be moved, clicked and scrolled there. */
@Mixin(ChatScreen.class)
public abstract class DihChatPinnedWindowsMixin extends Screen {
    protected DihChatPinnedWindowsMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void dih$renderPinnedWindows(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!dih$active()) return;
        try {
            DihOverlayManager.get().renderAll(context, mouseX, mouseY, delta);
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void dih$clickPinnedWindows(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (dih$active() && DihOverlayManager.get().handleMouseClicked(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void dih$scrollPinnedWindows(double mouseX, double mouseY, double horizontal, double vertical,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (dih$active() && DihOverlayManager.get().handleMouseScrolled(mouseX, mouseY, vertical)) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (dih$active() && DihOverlayManager.get().handleMouseReleased(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (dih$active() && DihOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Unique
    private static boolean dih$active() {
        DihModule module = DihModule.get();
        return module != null && module.isUsable() && DihOverlayManager.get().hasRegisteredOverlays();
    }
}
