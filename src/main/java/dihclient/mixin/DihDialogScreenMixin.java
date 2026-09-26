package dihclient.mixin;

import dihclient.util.DihKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dihclient.modules.DihModule;
import dihclient.util.DihCustomFilterOverlay;
import dihclient.util.DihCustomFilterPresetOverlay;
import dihclient.util.DihLANSync;
import dihclient.util.DihLANSyncOverlay;
import dihclient.util.DihLauncherOverlay;
import dihclient.util.DihMacroEditorOverlay;
import dihclient.util.DihMacroListOverlay;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihPacketLoggerOverlay;
import dihclient.util.DihQueueEditorOverlay;
import dihclient.util.DihKeybindOverlay;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

@Mixin({DialogScreen.class, net.minecraft.client.gui.screens.dialog.WaitingForResponseScreen.class})
public abstract class DihDialogScreenMixin extends Screen {
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
    @Unique private boolean dih$overlaysBuilt;

    protected DihDialogScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void dih$init(CallbackInfo ci) {

        Screen screen = (Screen) (Object) this;
        dihclient.platform.DihPlatform.afterScreenExtract(screen, (scrn, drawContext, mouseX, mouseY, tickDelta) -> {
            if (!dih$isDihActive()) return;
            try {
                DihOverlayManager.get().renderAll(drawContext, mouseX, mouseY, tickDelta);
            } catch (Throwable ignored) {

            }
        });
        dihclient.platform.DihPlatform.onScreenRemove(screen, scrn -> {

            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            boolean stillOnDialog = mc != null
                && (mc.gui.screen() instanceof DialogScreen<?>
                    || mc.gui.screen() instanceof net.minecraft.client.gui.screens.dialog.WaitingForResponseScreen);
            if (!stillOnDialog) {
                dih$overlaysBuilt = false;
                dih$saveOverlays();
            }
        });
        if (!dih$isDihActive()) return;
        if (dih$overlaysBuilt && DihOverlayManager.get().hasRegisteredOverlays()) {

            if (launcherOverlay != null) launcherOverlay.setVisible(true);
            return;
        }
        try {
            dih$buildOverlays();
            dih$overlaysBuilt = true;
        } catch (Throwable ignored) {

        }
    }

    @Unique
    private void dih$buildOverlays() {
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

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean inWorld = mc != null && mc.player != null && mc.level != null;

        macroEditorOverlay = DihMacroEditorOverlay.getSharedOverlay();
        if (macroEditorOverlay != null) {
            macroEditorOverlay.restoreState();
            macroEditorOverlay.setConfigurationOnly(!inWorld);
        }

        DihOverlayManager manager = DihOverlayManager.get();
        manager.clear();
        manager.register(lanSyncOverlay);
        manager.register(macroListOverlay);
        manager.register(queueEditorOverlay);
        manager.register(customFilterOverlay);
        if (customFilterPresetOverlay != null) manager.register(customFilterPresetOverlay);
        if (macroEditorOverlay != null) manager.register(macroEditorOverlay);

        dihclient.gui.macro.editor.ActionEditorOverlay actionEditor =
            dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay();
        actionEditor.setWorldCaptureAllowed(inWorld);
        manager.register(actionEditor);

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
        if (packetLoggerOverlay == null && DihPacketLoggerOverlay.shouldRestoreSavedVisible()) {
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

        if (!dihclient.util.DihLiteVariant.enabled()) {
            dihclient.util.IDihOverlay matchmakingOverlay = DihModule.get().getMatchmakingOverlay();
            if (matchmakingOverlay != null && matchmakingOverlay.isVisible()) manager.register(matchmakingOverlay);
            dihclient.util.IDihOverlay profilesOverlay = DihModule.get().getProfilesOverlay();
            if (profilesOverlay != null && profilesOverlay.isVisible()) manager.register(profilesOverlay);
        }

        launcherOverlay.restoreLayout();

        launcherOverlay.setVisible(true);
        manager.register(launcherOverlay);
    }

    @Unique
    private void dih$saveOverlays() {
        if (!dih$isDihActive()) return;
        try {

            DihOverlayManager.get().restoreClampedAwayBounds();
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
        } finally {
            DihOverlayManager.get().clear();
        }
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

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (dih$isDihActive() && DihOverlayManager.get().handleKeyPressed(input.key(), DihKeys.secondaryCode(input), input.modifiers())) {
            return true;
        }
        return super.keyPressed(input);
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
        return module != null && module.isUsable();
    }
}
