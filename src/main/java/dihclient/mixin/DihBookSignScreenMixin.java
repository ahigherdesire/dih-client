package dihclient.mixin;

import dihclient.util.DihKeys;
import dihclient.modules.DihModule;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihCustomFilterOverlay;
import dihclient.util.DihCustomFilterPresetOverlay;
import dihclient.util.DihKeybindOverlay;
import dihclient.util.DihLANSync;
import dihclient.util.DihLANSyncOverlay;
import dihclient.util.DihLauncherOverlay;
import dihclient.util.DihMacroEditorOverlay;
import dihclient.util.DihMacroListOverlay;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihPacketLoggerOverlay;
import dihclient.util.DihQueueEditorOverlay;
import dihclient.util.DihSharedState;
import dihclient.util.DihSpecialGuiActions;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BookSignScreen.class)
public abstract class DihBookSignScreenMixin extends Screen implements DihSpecialGuiActions {
    @Unique private static final Minecraft MC = Minecraft.getInstance();

    @Unique private DihLauncherOverlay launcherOverlay;
    @Unique private DihLANSyncOverlay lanSyncOverlay;
    @Unique private DihMacroListOverlay macroListOverlay;
    @Unique private DihQueueEditorOverlay queueEditorOverlay;
    @Unique private DihPacketLoggerOverlay packetLoggerOverlay;
    @Unique private DihCustomFilterOverlay customFilterOverlay;
    @Unique private DihCustomFilterPresetOverlay customFilterPresetOverlay;
    @Unique private DihMacroEditorOverlay macroEditorOverlay;
    @Unique private DihKeybindOverlay keybindOverlay;
    @Unique private dihclient.util.DihServerInfoOverlay serverInfoOverlay;

    protected DihBookSignScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void dih$init(CallbackInfo ci) {
        if (!dih$isDihActive()) return;

        DihLANSync.getInstance().setOnSessionStateChanged(() -> {});

        lanSyncOverlay = DihLANSyncOverlay.getSharedOverlay(this.font);
        macroListOverlay = new DihMacroListOverlay(this.font);
        queueEditorOverlay = new DihQueueEditorOverlay(this.font);
        customFilterOverlay = new DihCustomFilterOverlay(this.font);
        customFilterPresetOverlay = customFilterOverlay.getPresetManagerOverlay();

        lanSyncOverlay.restoreState();
        macroListOverlay.restoreState();
        queueEditorOverlay.restoreState();
        customFilterOverlay.restoreLayout();
        if (customFilterPresetOverlay != null) customFilterPresetOverlay.restoreLayout();

        macroEditorOverlay = DihMacroEditorOverlay.getSharedOverlay();
        if (macroEditorOverlay != null) macroEditorOverlay.restoreState();

        DihOverlayManager manager = DihOverlayManager.get();
        manager.clear();
        manager.register(lanSyncOverlay);
        manager.register(macroListOverlay);
        manager.register(queueEditorOverlay);
        manager.register(customFilterOverlay);
        if (customFilterPresetOverlay != null) manager.register(customFilterPresetOverlay);
        if (macroEditorOverlay != null) manager.register(macroEditorOverlay);

        DihModule dihModule = DihModule.get();

        if (!dihclient.util.DihLiteVariant.enabled()) {
            dihclient.util.IDihOverlay multiOverlay = dihModule == null ? null : dihModule.getMultiOverlayIfExists();
            if (multiOverlay != null && multiOverlay.isVisible()) manager.register(multiOverlay);
        }

        keybindOverlay = new DihKeybindOverlay();
        keybindOverlay.restoreLayout();
        manager.register(keybindOverlay);

        launcherOverlay = new DihLauncherOverlay(macroListOverlay, null, lanSyncOverlay, queueEditorOverlay, packetLoggerOverlay, customFilterOverlay);
        launcherOverlay.setKeybindOverlay(keybindOverlay);
        launcherOverlay.setPacketLoggerOverlaySupplier(() -> {
            if (packetLoggerOverlay == null && dihModule != null) {
                packetLoggerOverlay = dihModule.getPacketLoggerOverlay();
                if (packetLoggerOverlay != null) packetLoggerOverlay.restoreState();
            }
            if (packetLoggerOverlay != null) manager.register(packetLoggerOverlay);
            return packetLoggerOverlay;
        });
        launcherOverlay.setServerDataOverlaySupplier(() -> {
            if (serverInfoOverlay == null) {
                serverInfoOverlay = DihModule.get().getServerDataOverlay();
            }
            if (serverInfoOverlay != null) manager.register(serverInfoOverlay);
            return serverInfoOverlay;
        });
        if (packetLoggerOverlay == null && dihclient.util.DihPacketLoggerOverlay.shouldRestoreSavedVisible()) {
            packetLoggerOverlay = DihModule.get().getPacketLoggerOverlay();
            if (packetLoggerOverlay != null) {
                packetLoggerOverlay.restoreState();
                if (packetLoggerOverlay.isVisible()) manager.register(packetLoggerOverlay);
            }
        }
        if (serverInfoOverlay == null && dihclient.util.DihServerInfoOverlay.shouldRestoreSavedVisible()) {
            serverInfoOverlay = DihModule.get().getServerDataOverlay();
            if (serverInfoOverlay != null) {
                serverInfoOverlay.restoreState();
                if (serverInfoOverlay.isVisible()) manager.register(serverInfoOverlay);
            }
        }
        launcherOverlay.restoreLayout();
        manager.register(launcherOverlay);

        Screen screen = (Screen) (Object) this;
        ScreenEvents.afterExtract(screen).register((scrn, drawContext, mouseX, mouseY, tickDelta) -> {
            if (dih$isDihActive()) {
                DihOverlayManager.get().renderAll(drawContext, mouseX, mouseY, tickDelta);
            }
        });
    }

    @Override
    public void removed() {
        if (dih$isDihActive()) {
            if (lanSyncOverlay != null) lanSyncOverlay.saveState();
            if (macroListOverlay != null) macroListOverlay.saveState();
            if (queueEditorOverlay != null) queueEditorOverlay.saveState();
            if (macroEditorOverlay != null) macroEditorOverlay.saveState();
            if (launcherOverlay != null) launcherOverlay.saveLayout();
            if (packetLoggerOverlay != null) packetLoggerOverlay.saveState();
            if (customFilterOverlay != null) customFilterOverlay.saveLayout();
            if (customFilterPresetOverlay != null) customFilterPresetOverlay.saveLayout();
            if (keybindOverlay != null) keybindOverlay.saveLayout();
            if (serverInfoOverlay != null) serverInfoOverlay.saveState();
            DihOverlayManager.get().clear();
        }
        super.removed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (dih$isDihActive() && DihOverlayManager.get().handleMouseClicked(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (dih$isDihActive() && DihOverlayManager.get().handleMouseReleased(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (dih$isDihActive() && DihOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (dih$isDihActive() && DihOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void dih$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!dih$isDihActive()) return;
        if (DihOverlayManager.get().handleKeyPressed(input.key(), DihKeys.secondaryCode(input), input.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (dih$isDihActive() && DihOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            return true;
        }
        return super.charTyped(input);
    }

    @Unique
    private boolean dih$isDihActive() {
        DihModule module = DihModule.get();
        return module != null && module.isActive();
    }

    @Override
    public void dih$closeWithPacket() {
        dih$closeWithPacket(true);
    }

    @Override
    public void dih$closeWithPacket(boolean notify) {
        if (MC.getConnection() != null) {
            DihSharedState.get().setForceNextBookEditPacket(true);
            dih$invokeSaveChanges();
        }
        MC.gui.setScreen(null);
    }

    @Override
    public void dih$closeWithoutPacket() {
        dih$closeWithoutPacket(true);
    }

    @Override
    public void dih$closeWithoutPacket(boolean notify) {
        MC.gui.setScreen(null);
        if (notify) DihClientMessaging.sendPrefixed("Book signing closed without packet.");
    }

    @Override
    public void dih$desync() {
        dih$desync(true);
    }

    @Override
    public void dih$desync(boolean notify) {
        if (MC.getConnection() == null) {
            if (notify) DihClientMessaging.sendPrefixed("Failed to desync: no network.");
            return;
        }
        DihSharedState.get().setForceNextBookEditPacket(true);
        dih$invokeSaveChanges();
        if (notify) DihClientMessaging.sendPrefixed("Signed book packet sent; GUI intentionally stays open.");
    }

    @Invoker("saveChanges")
    protected abstract void dih$invokeSaveChanges();
}
