package dihclient.mixin;

import dihclient.util.DihPackets;
import dihclient.util.DihKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dihclient.modules.DihModule;
import dihclient.modules.AutoSignModule;
import dihclient.modules.Module;
import dihclient.modules.ModuleRegistry;
import dihclient.modules.PackHideState;
import dihclient.util.DihClientMessaging;
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
import dihclient.util.DihSharedState;
import dihclient.util.DihSpecialGuiActions;
import dihclient.util.DihSignEditAccess;
import dihclient.util.DihKeybindOverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;

@Mixin(AbstractSignEditScreen.class)
public abstract class DihSignEditScreenMixin extends Screen implements DihSpecialGuiActions, DihSignEditAccess {
    @Unique private static final Minecraft MC = Minecraft.getInstance();

    @Shadow @Final protected SignBlockEntity sign;
    @Shadow @Final private String[] messages;
    //? if >=26.3 {
    /*@Shadow @Final private net.minecraft.world.level.block.entity.SignTextSlot slot;

    @Unique
    private boolean dih$front() {
        return slot == net.minecraft.world.level.block.entity.SignTextSlot.FRONT;
    }
    *///?} else {
    @Shadow @Final private boolean isFrontText;

    @Unique
    private boolean dih$front() {
        return isFrontText;
    }
    //?}

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

    protected DihSignEditScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void yang$autoSignFill(CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        Module raw = ModuleRegistry.get("auto-sign");
        if (!(raw instanceof AutoSignModule autoSign) || !autoSign.isEnabled()) return;
        if (messages == null || this.font == null) return;

        boolean empty = true;
        for (String message : messages) {
            if (message != null && !message.isEmpty()) {
                empty = false;
                break;
            }
        }

        if (!empty && !autoSign.editExisting()) return;

        String[] lines = autoSign.signLines();
        int maxWidth = sign == null ? 90 : sign.getMaxTextLineWidth();
        for (int i = 0; i < messages.length && i < lines.length; i++) {
            messages[i] = this.font.plainSubstrByWidth(lines[i], maxWidth);
        }

        yang$autoSignDonePending = autoSign.autoDone();
    }

    @Unique private boolean yang$autoSignDonePending;

    @Inject(method = "tick", at = @At("HEAD"))
    private void yang$autoSignDone(CallbackInfo ci) {
        if (!yang$autoSignDonePending) return;
        yang$autoSignDonePending = false;
        this.onClose();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void yang$init(CallbackInfo ci) {
        if (!yang$isDihActive()) return;
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
                serverInfoOverlay = dihclient.modules.DihModule.get().getServerDataOverlay();
            }
            if (serverInfoOverlay != null) manager.register(serverInfoOverlay);
            return serverInfoOverlay;
        });
        if (packetLoggerOverlay == null && dihclient.util.DihPacketLoggerOverlay.shouldRestoreSavedVisible()) {
            packetLoggerOverlay = dihclient.modules.DihModule.get().getPacketLoggerOverlay();
            if (packetLoggerOverlay != null) {
                packetLoggerOverlay.restoreState();
                if (packetLoggerOverlay.isVisible()) manager.register(packetLoggerOverlay);
            }
        }
        if (serverInfoOverlay == null && dihclient.util.DihServerInfoOverlay.shouldRestoreSavedVisible()) {
            serverInfoOverlay = dihclient.modules.DihModule.get().getServerDataOverlay();
            if (serverInfoOverlay != null) {
                serverInfoOverlay.restoreState();
                if (serverInfoOverlay.isVisible()) manager.register(serverInfoOverlay);
            }
        }
        launcherOverlay.restoreLayout();
        manager.register(launcherOverlay);

        Screen screen = (Screen) (Object) this;
        dihclient.platform.DihPlatform.afterScreenExtract(screen, (scrn, drawContext, mouseX, mouseY, tickDelta) -> {
            if (yang$isDihActive()) {
                DihOverlayManager.get().renderAll(drawContext, mouseX, mouseY, tickDelta);
            }
        });
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void yang$removed(CallbackInfo ci) {
        if (yang$isDihActive()) {
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
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (yang$isDihActive() && DihOverlayManager.get().handleMouseClicked(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (yang$isDihActive() && DihOverlayManager.get().handleMouseReleased(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (yang$isDihActive() && DihOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (yang$isDihActive() && DihOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void yang$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!yang$isDihActive()) return;
        if (DihOverlayManager.get().handleKeyPressed(input.key(), DihKeys.secondaryCode(input), input.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void yang$charTyped(CharacterEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!yang$isDihActive()) return;
        if (DihOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private boolean yang$isDihActive() {
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
            DihSharedState.get().setForceNextSignUpdatePacket(true);
        }
        MC.gui.setScreen(null);
    }

    @Override
    public void dih$closeWithoutPacket() {
        dih$closeWithoutPacket(true);
    }

    @Override
    public void dih$closeWithoutPacket(boolean notify) {
        DihSharedState.get().setSuppressNextSignUpdatePacket(true);
        MC.gui.setScreen(null);
        if (notify) DihClientMessaging.sendPrefixed("Sign edit closed without packet.");
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
        DihSharedState.get().setForceNextSignUpdatePacket(true);
        MC.getConnection().send(DihPackets.signUpdate(
            sign.getBlockPos(),
            dih$front(),
            messages[0],
            messages[1],
            messages[2],
            messages[3]
        ));
        if (notify) DihClientMessaging.sendPrefixed("Sign update packet sent; editor intentionally stays open.");
    }

    @Override
    public BlockPos dih$getSignPos() {
        return sign == null ? BlockPos.ZERO : sign.getBlockPos();
    }

    @Override
    public boolean dih$isFrontText() {
        return dih$front();
    }

    @Override
    public String[] dih$getSignLines() {
        return messages == null ? new String[]{"", "", "", ""} : messages.clone();
    }
}
