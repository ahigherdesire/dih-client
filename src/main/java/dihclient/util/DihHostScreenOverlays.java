package dihclient.util;

import dihclient.modules.DihModule;
import net.minecraft.client.gui.Font;

public final class DihHostScreenOverlays {
    private final DihLANSyncOverlay lanSyncOverlay;
    private final DihMacroListOverlay macroListOverlay;
    private final DihQueueEditorOverlay queueEditorOverlay;
    private final DihCustomFilterOverlay customFilterOverlay;
    private final DihCustomFilterPresetOverlay customFilterPresetOverlay;
    private final DihMacroEditorOverlay macroEditorOverlay;
    private final DihKeybindOverlay keybindOverlay;
    private final DihLauncherOverlay launcherOverlay;
    private DihPacketLoggerOverlay packetLoggerOverlay;
    private DihServerInfoOverlay serverInfoOverlay;

    private DihHostScreenOverlays(Font font) {
        DihLANSync.getInstance().setOnSessionStateChanged(() -> {});

        lanSyncOverlay = DihLANSyncOverlay.getSharedOverlay(font);
        macroListOverlay = new DihMacroListOverlay(font);
        queueEditorOverlay = new DihQueueEditorOverlay(font);
        customFilterOverlay = new DihCustomFilterOverlay(font);
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
        manager.register(dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay());

        DihItemNbtInspectOverlay itemNbtInspectOverlay = DihItemNbtInspectOverlay.getSharedOverlay(font);
        if (itemNbtInspectOverlay != null) manager.register(itemNbtInspectOverlay);

        DihModule dihModule = DihModule.get();

        if (!dihclient.util.DihLiteVariant.enabled()) {
            IDihOverlay multiOverlay = dihModule == null ? null : dihModule.getMultiOverlayIfExists();
            if (multiOverlay != null && multiOverlay.isVisible()) manager.register(multiOverlay);
        }

        keybindOverlay = new DihKeybindOverlay();
        keybindOverlay.restoreLayout();
        manager.register(keybindOverlay);

        launcherOverlay = new DihLauncherOverlay(macroListOverlay, null, lanSyncOverlay, queueEditorOverlay,
            packetLoggerOverlay, customFilterOverlay);
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
        if (serverInfoOverlay == null && DihServerInfoOverlay.shouldRestoreSavedVisible()) {
            serverInfoOverlay = DihModule.get().getServerDataOverlay();
            if (serverInfoOverlay != null) {
                serverInfoOverlay.restoreState();
                if (serverInfoOverlay.isVisible()) manager.register(serverInfoOverlay);
            }
        }

        if (!dihclient.util.DihLiteVariant.enabled() && dihModule != null) {
            IDihOverlay matchmakingOverlay = dihModule.getMatchmakingOverlay();
            if (matchmakingOverlay != null && matchmakingOverlay.isVisible()) manager.register(matchmakingOverlay);
            IDihOverlay profilesOverlay = dihModule.getProfilesOverlay();
            if (profilesOverlay != null && profilesOverlay.isVisible()) manager.register(profilesOverlay);
        }
        launcherOverlay.restoreLayout();
        manager.register(launcherOverlay);
    }

    public static DihHostScreenOverlays build(Font font) {
        return new DihHostScreenOverlays(font);
    }

    public void saveAndClear() {
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
