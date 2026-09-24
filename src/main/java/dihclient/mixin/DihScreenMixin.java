package dihclient.mixin;

import dihclient.gui.vanillaui.components.ScreenButton;
import dihclient.ducks.DihExternalButtonScreen;
import dihclient.mixin.accessor.DihScreenAccessor;
import dihclient.modules.DihModule;
import dihclient.modules.PackHideState;
import dihclient.util.DihLecternButtons;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihNotifications;
import dihclient.util.DihPacketLoggerOverlay;
import dihclient.util.DihQueueEditorOverlay;
import dihclient.util.DihUiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.LecternScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class DihScreenMixin {
    @Unique private static final Minecraft MC = Minecraft.getInstance();

    @Unique private boolean dih$lecternInitialized;
    @Unique private DihQueueEditorOverlay dih$queueEditorOverlay;
    @Unique private DihPacketLoggerOverlay dih$packetLoggerOverlay;

    @Inject(method = "init()V", at = @At("TAIL"))
    private void dih$onInit(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!(screen instanceof LecternScreen) || !dih$isModuleActive()) return;

        if (dih$lecternInitialized) {
            if (dih$queueEditorOverlay != null) dih$queueEditorOverlay.restoreState();
            if (dih$queueEditorOverlay != null) DihOverlayManager.get().register(dih$queueEditorOverlay);
            return;
        }

        Font textRenderer = ((DihScreenAccessor) this).getFont();
        dih$queueEditorOverlay = new DihQueueEditorOverlay(textRenderer);
        dih$queueEditorOverlay.restoreState();
        DihOverlayManager.get().register(dih$queueEditorOverlay);

        dih$lecternInitialized = true;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void dih$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (screen instanceof DihExternalButtonScreen externalButtonScreen) {
            externalButtonScreen.dih$renderExternalButtons(context, mouseX, mouseY, delta);
        }

        if (!(screen instanceof LecternScreen) || MC.player == null) return;

        if (!dih$isModuleActive()) return;

        Font textRenderer = ((DihScreenAccessor) this).getFont();
        if (textRenderer == null) return;

        AbstractContainerMenu handler = MC.player.containerMenu;
        if (handler != null) {
            int virtualMouseX = DihUiScale.toVirtualInt(mouseX);
            int virtualMouseY = DihUiScale.toVirtualInt(mouseY);
            DihUiScale.pushOverlayScale(context);
            try {
                for (ScreenButton button : DihLecternButtons.build(MC, dih$queueEditorOverlay)) {
                    button.render(context, textRenderer, virtualMouseX, virtualMouseY);
                }
            } finally {
                DihUiScale.popOverlayScale(context);
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void dih$renderTopmostNotifications(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {

        if (!DihNotifications.hasVisible()) return;
        context.nextStratum();
        DihUiScale.pushOverlayScale(context);
        try {
            DihNotifications.render(context);
        } catch (Throwable t) {

            if (!dih$toastFailureLogged) {
                dih$toastFailureLogged = true;
                dihclient.DihClientAddon.LOG.warn("[UI] Notification render failed", t);
            }
        } finally {
            DihUiScale.popOverlayScale(context);
        }
    }

    @Unique private static boolean dih$toastFailureLogged;

    @Inject(method = "onClose", at = @At("HEAD"))
    private void dih$onClose(CallbackInfo ci) {
        dihclient.util.DihConfig.enqueuePendingSaveNow();
        Screen screen = (Screen) (Object) this;
        if (screen instanceof LecternScreen) {
            DihOverlayManager.get().unregister(dih$queueEditorOverlay);
            DihOverlayManager.get().unregister(dih$packetLoggerOverlay);
        }
    }

    @Unique
    private boolean dih$isModuleActive() {
        if (PackHideState.isHardLocked()) return false;
        DihModule module = DihModule.get();
        return module != null && module.isActive();
    }

}
