package dihclient.gui.screen;

import dihclient.util.DihKeys;
import dihclient.util.PacketListCodec;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.module.VanillaModuleMenuController;
import dihclient.modules.DihModule;
import dihclient.modules.PackHideState;
import dihclient.modules.Module;
import dihclient.api.module.Setting;
import dihclient.util.DihBindUtil;
import dihclient.util.DihConfig;
import dihclient.util.DihAdminToolsOverlay;
import dihclient.util.DihCustomFilterOverlay;
import dihclient.util.DihFabricatorOverlay;
import dihclient.util.DihKeybindOverlay;
import dihclient.util.DihLANSyncOverlay;
import dihclient.util.DihLauncherOverlay;
import dihclient.util.DihMacroEditorOverlay;
import dihclient.util.DihMacroListOverlay;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihPacketLoggerOverlay;
import dihclient.util.DihPacketSelectorOverlay;
import dihclient.util.DihQueueEditorOverlay;
import dihclient.util.DihServerInfoOverlay;
import dihclient.util.DihSharedState;
import dihclient.util.DihUiScale;
import dihclient.util.IDihOverlay;
import dihclient.util.macro.ToggleModuleAction;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;

import java.util.LinkedHashSet;
import java.util.Set;

public class DihModuleScreen extends DihScreen {
    public enum Mode {
        IN_GAME,
        TITLE_SETUP
    }

    private static final Set<String> TEMPORARILY_HIDDEN_UTILITY_OVERLAYS = new LinkedHashSet<>();

    private final Screen parent;
    private final Mode mode;
    private VanillaModuleMenuController menu;
    private DihPacketSelectorOverlay packetSelectorOverlay;
    private DihMacroListOverlay utilityMacroListOverlay;
    private DihFabricatorOverlay utilityFabricatorOverlay;
    private DihLANSyncOverlay utilityLanSyncOverlay;
    private DihQueueEditorOverlay utilityQueueEditorOverlay;
    private DihCustomFilterOverlay utilityCustomFilterOverlay;
    private DihKeybindOverlay utilityKeybindOverlay;
    private DihAdminToolsOverlay utilityAdminToolsOverlay;
    private String returnSettingsModuleId;
    private final Set<String> titleSetupHiddenOverlayIds = new LinkedHashSet<>();
    private boolean menuCloseCleanupDone;

    public DihModuleScreen(Screen parent) {
        this(parent, Mode.IN_GAME);
    }

    public DihModuleScreen(Screen parent, Mode mode) {
        super(Component.literal("Dih Modules"));
        this.parent = parent;
        this.mode = mode == null ? Mode.IN_GAME : mode;
    }

    /** Opens straight onto one module's settings (used by {@code #flee gui}). */
    public DihModuleScreen openingSettingsOf(String moduleId) {
        this.returnSettingsModuleId = moduleId;
        return this;
    }

    public boolean isTitleSetup() {
        return mode == Mode.TITLE_SETUP;
    }

    @Override
    protected void init() {
        menuCloseCleanupDone = false;
        syncUtilityOverlays();
        if (isTitleSetup()) hideRuntimeOverlaysForTitleSetup();
        else restoreTemporarilyHiddenUtilityOverlays();

        if (menu == null) menu = new VanillaModuleMenuController(new ModuleMenuHost());
        menu.init();
        if (returnSettingsModuleId != null && !returnSettingsModuleId.isBlank()) {
            menu.openSettingsByModuleId(returnSettingsModuleId);
            returnSettingsModuleId = null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public boolean blocksGlobalKeybinds() {
        return (menu != null && menu.blocksGlobalKeybinds())
            || (packetSelectorOverlay != null && packetSelectorOverlay.isVisible());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (menu == null) {
            menu = new VanillaModuleMenuController(new ModuleMenuHost());
            menu.init();
        }
        int mx = DihUiScale.toVirtualInt(mouseX);
        int my = DihUiScale.toVirtualInt(mouseY);
        boolean selectorOpen = packetSelectorOverlay != null && packetSelectorOverlay.isVisible();
        syncUtilityOverlaysForTopLayer(selectorOpen || menu.hasTopLayer());
        boolean overlayBlocksMenuHover = !selectorOpen
            && !menu.hasTopLayer()
            && DihOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY);
        int menuMouseX = overlayBlocksMenuHover ? DihOverlayManager.HOVER_BLOCKED_MOUSE : mx;
        int menuMouseY = overlayBlocksMenuHover ? DihOverlayManager.HOVER_BLOCKED_MOUSE : my;

        DihUiScale.pushOverlayScale(graphics);
        try {
            if (selectorOpen) {
                UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), 0xAA050507);
                packetSelectorOverlay.render(graphics, mx, my, delta);
            } else {
                menu.render(graphics, menuMouseX, menuMouseY, delta, screenWidth(), screenHeight());
            }
        } finally {
            DihUiScale.popOverlayScale(graphics);
        }

        if (!PackHideState.isActive() && !selectorOpen && !menu.hasSelectedModule() && !menu.hasTopLayer()) {
            DihOverlayManager.get().renderAll(graphics, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = DihUiScale.toVirtualInt(event.x());
        int my = DihUiScale.toVirtualInt(event.y());
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible()) {
            return packetSelectorOverlay.mouseClicked(mx, my, event.button());
        }
        if (menu != null && !menu.hasTopLayer() && DihOverlayManager.get().handleMouseClicked(event.x(), event.y(), event.button())) return true;
        return menu == null || menu.mouseClicked(mx, my, event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int mx = DihUiScale.toVirtualInt(event.x());
        int my = DihUiScale.toVirtualInt(event.y());
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible() && packetSelectorOverlay.mouseReleased(mx, my, event.button())) return true;
        if (menu != null && !menu.hasTopLayer() && DihOverlayManager.get().handleMouseReleased(event.x(), event.y(), event.button())) return true;
        return menu == null || menu.mouseReleased(mx, my, event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        int mx = DihUiScale.toVirtualInt(event.x());
        int my = DihUiScale.toVirtualInt(event.y());
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible() && packetSelectorOverlay.mouseDragged(mx, my, event.button(), dx, dy)) return true;
        if (menu != null && !menu.hasTopLayer() && DihOverlayManager.get().handleMouseDragged(event.x(), event.y(), event.button(), dx, dy)) return true;
        return menu == null || menu.mouseDragged(mx, my, event.button(), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int mx = DihUiScale.toVirtualInt(x);
        int my = DihUiScale.toVirtualInt(y);
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible()) return packetSelectorOverlay.mouseScrolled(mx, my, scrollY);
        if (menu != null && !menu.hasTopLayer() && DihOverlayManager.get().handleMouseScrolled(x, y, scrollY)) return true;
        return menu == null || menu.mouseScrolled(mx, my, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible() && packetSelectorOverlay.keyPressed(input.key(), DihKeys.secondaryCode(input), input.modifiers())) return true;
        if (menu != null && !menu.hasTopLayer() && DihOverlayManager.get().handleKeyPressed(input.key(), DihKeys.secondaryCode(input), input.modifiers())) return true;
        if (menu != null && menu.keyPressed(input.key(), DihKeys.secondaryCode(input), input.modifiers())) return true;
        if (passMovementKey(input, true)) return false;
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(KeyEvent input) {
        if (passMovementKey(input, false)) return false;
        return super.keyReleased(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        char chr = (char) input.codepoint();
        if (packetSelectorOverlay != null && packetSelectorOverlay.isVisible() && packetSelectorOverlay.charTyped(chr, 0)) return true;
        if (menu != null && !menu.hasTopLayer() && DihOverlayManager.get().handleCharTyped(chr, 0)) return true;
        if (menu != null && menu.charTyped(chr)) return true;
        return super.charTyped(input);
    }

    @Override
    public void onClose() {
        runMenuCloseCleanup();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {

        runMenuCloseCleanup();
    }

    private void runMenuCloseCleanup() {
        if (menuCloseCleanupDone) return;
        menuCloseCleanupDone = true;

        DihOverlayManager.get().restoreClampedAwayBounds();
        saveUtilityOverlayStates();
        hideUtilityOverlaysForMenuClose();
        restoreRuntimeOverlaysHiddenForTitleSetup();
        resetConfigurationOnlyOverlays();
    }

    private void openPacketSelector(Module module, Setting<?, ?> option) {
        if (module == null || option == null) return;
        if (packetSelectorOverlay == null) packetSelectorOverlay = new DihPacketSelectorOverlay(font);
        boolean c2s = PacketListCodec.isC2SOption(option.id());
        Set<Class<? extends Packet<?>>> selected = PacketListCodec.resolvePackets(module.value(option.id()), c2s);
        if (c2s) {
            packetSelectorOverlay.openToggleC2S((packetClass, enabled) -> setPacketSelected(module, option, true, packetClass, enabled), selected);
        } else {
            packetSelectorOverlay.openToggleS2C((packetClass, enabled) -> setPacketSelected(module, option, false, packetClass, enabled), selected);
        }
    }

    private void setPacketSelected(Module module, Setting<?, ?> option, boolean c2s, Class<? extends Packet<?>> packetClass, boolean selected) {
        Set<Class<? extends Packet<?>>> packets = new LinkedHashSet<>(PacketListCodec.resolvePackets(module.value(option.id()), c2s));
        if (selected) packets.add(packetClass);
        else packets.remove(packetClass);
        String encoded = PacketListCodec.encodePackets(packets);
        if (isTitleSetup()) module.setConfiguredValue(option.id(), encoded);
        else module.setValue(option.id(), encoded);
    }

    private void syncUtilityOverlays() {
        DihOverlayManager manager = DihOverlayManager.get();
        utilityMacroListOverlay = findRegisteredOverlay(DihMacroListOverlay.class, null);
        if (utilityMacroListOverlay == null) {
            utilityMacroListOverlay = new DihMacroListOverlay(font);
            utilityMacroListOverlay.restoreState();
        }
        manager.register(utilityMacroListOverlay);

        DihMacroEditorOverlay macroEditor = DihMacroEditorOverlay.getSharedOverlay();
        utilityMacroListOverlay.setConfigurationOnly(isTitleSetup());
        if (macroEditor != null) macroEditor.setConfigurationOnly(isTitleSetup());

        if (isTitleSetup()) {
            utilityLanSyncOverlay = DihLANSyncOverlay.getSharedOverlay(font);
            utilityLanSyncOverlay.restoreState();
            utilityLanSyncOverlay.setConfigurationOnly(true);
            manager.register(utilityLanSyncOverlay);

            utilityQueueEditorOverlay = findRegisteredOverlay(DihQueueEditorOverlay.class, null);
            if (utilityQueueEditorOverlay == null) {
                utilityQueueEditorOverlay = new DihQueueEditorOverlay(font);
                utilityQueueEditorOverlay.restoreState();
            }
            utilityQueueEditorOverlay.setConfigurationOnly(true);
            manager.register(utilityQueueEditorOverlay);

            utilityCustomFilterOverlay = findRegisteredOverlay(DihCustomFilterOverlay.class, null);
            if (utilityCustomFilterOverlay == null) {
                utilityCustomFilterOverlay = new DihCustomFilterOverlay(font);
                utilityCustomFilterOverlay.restoreLayout();
            }
            manager.register(utilityCustomFilterOverlay);
            if (utilityCustomFilterOverlay.getPresetManagerOverlay() != null) {

                if (findRegisteredOverlay(dihclient.util.DihCustomFilterPresetOverlay.class, null) == null) {
                    utilityCustomFilterOverlay.getPresetManagerOverlay().restoreLayout();
                }
                manager.register(utilityCustomFilterOverlay.getPresetManagerOverlay());
            }

            utilityKeybindOverlay = findRegisteredOverlay(DihKeybindOverlay.class, null);
            if (utilityKeybindOverlay == null) {
                utilityKeybindOverlay = new DihKeybindOverlay();
                utilityKeybindOverlay.restoreLayout();
            }
            manager.register(utilityKeybindOverlay);
            manager.setTemporarilyHidden(utilityMacroListOverlay, false);
            manager.setTemporarilyHidden(utilityKeybindOverlay, false);
            if (macroEditor != null) {
                if (findRegisteredOverlay(dihclient.util.DihMacroEditorOverlay.class, null) == null) {
                    macroEditor.restoreState();
                }
                manager.register(macroEditor);
                manager.setTemporarilyHidden(macroEditor, false);
            }
            dihclient.gui.macro.editor.ActionEditorOverlay actionEditor = dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay();
            actionEditor.setConfigurationOnly(true);
            manager.register(actionEditor);
            manager.setTemporarilyHidden(actionEditor, false);

            DihModule global = DihModule.get();
            if (global != null) {
                DihPacketLoggerOverlay logger = global.getPacketLoggerOverlay();
                if (logger != null) {
                    logger.restoreState();
                    logger.setConfigurationOnly(true);
                    manager.register(logger);
                }
                DihServerInfoOverlay serverInfo = global.getServerDataOverlay();
                if (serverInfo != null) {
                    serverInfo.restoreState();
                    serverInfo.setConfigurationOnly(true);
                    manager.register(serverInfo);
                }
            }
            return;
        }

        if (parent instanceof AbstractContainerScreen<?> handledScreen) {
            utilityFabricatorOverlay = findRegisteredOverlay(DihFabricatorOverlay.class, null);
            if (utilityFabricatorOverlay == null) {
                utilityFabricatorOverlay = DihFabricatorOverlay.getSharedOverlay(handledScreen);
                utilityFabricatorOverlay.restoreState();
            }
            manager.register(utilityFabricatorOverlay);
        }

        utilityLanSyncOverlay = findRegisteredOverlay(DihLANSyncOverlay.class, null);
        if (utilityLanSyncOverlay == null) {
            utilityLanSyncOverlay = DihLANSyncOverlay.getSharedOverlay(font);
            utilityLanSyncOverlay.restoreState();
        }
        utilityLanSyncOverlay.setConfigurationOnly(false);
        manager.register(utilityLanSyncOverlay);

        utilityQueueEditorOverlay = findRegisteredOverlay(DihQueueEditorOverlay.class, null);
        if (utilityQueueEditorOverlay == null) {
            utilityQueueEditorOverlay = new DihQueueEditorOverlay(font);
            utilityQueueEditorOverlay.restoreState();
        }
        utilityQueueEditorOverlay.setConfigurationOnly(false);
        manager.register(utilityQueueEditorOverlay);

        utilityCustomFilterOverlay = findRegisteredOverlay(DihCustomFilterOverlay.class, null);
        if (utilityCustomFilterOverlay == null) {
            utilityCustomFilterOverlay = new DihCustomFilterOverlay(font);
            utilityCustomFilterOverlay.restoreLayout();
        }
        manager.register(utilityCustomFilterOverlay);
        if (utilityCustomFilterOverlay.getPresetManagerOverlay() != null) {

            if (findRegisteredOverlay(dihclient.util.DihCustomFilterPresetOverlay.class, null) == null) {
                utilityCustomFilterOverlay.getPresetManagerOverlay().restoreLayout();
            }
            manager.register(utilityCustomFilterOverlay.getPresetManagerOverlay());
        }

        utilityKeybindOverlay = findRegisteredOverlay(DihKeybindOverlay.class, null);
        if (utilityKeybindOverlay == null) {
            utilityKeybindOverlay = new DihKeybindOverlay();
            utilityKeybindOverlay.restoreLayout();
        }
        manager.register(utilityKeybindOverlay);

        utilityAdminToolsOverlay = findRegisteredOverlay(DihAdminToolsOverlay.class, null);
        if (utilityAdminToolsOverlay == null) {
            utilityAdminToolsOverlay = DihAdminToolsOverlay.getSharedOverlay();
            utilityAdminToolsOverlay.restoreLayout();
        }
        manager.register(utilityAdminToolsOverlay);

        if (macroEditor != null) {
            if (findRegisteredOverlay(dihclient.util.DihMacroEditorOverlay.class, null) == null) {
                macroEditor.restoreState();
            }
            manager.register(macroEditor);
        }
        dihclient.gui.macro.editor.ActionEditorOverlay actionEditor = dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay();
        actionEditor.setConfigurationOnly(false);
        manager.register(actionEditor);

        DihModule global = DihModule.get();
        if (global != null) {
            DihPacketLoggerOverlay logger = global.getPacketLoggerOverlay();
            if (logger != null) {
                logger.restoreState();
                logger.setConfigurationOnly(false);
                manager.register(logger);
            }
            DihServerInfoOverlay serverInfo = global.getServerDataOverlay();
            if (serverInfo != null) {
                serverInfo.restoreState();
                serverInfo.setConfigurationOnly(false);
                manager.register(serverInfo);
            }
        }
    }

    private void syncUtilityOverlaysForTopLayer(boolean hidden) {
        if (hidden) hideUtilityOverlaysForMenuClose();
        else restoreTemporarilyHiddenUtilityOverlays();
    }

    private void hideUtilityOverlaysForMenuClose() {
        TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.clear();
        setOverlayHiddenForMenuClose(utilityMacroListOverlay);
        setOverlayHiddenForMenuClose(utilityFabricatorOverlay);
        setOverlayHiddenForMenuClose(utilityLanSyncOverlay);
        setOverlayHiddenForMenuClose(utilityQueueEditorOverlay);
        setOverlayHiddenForMenuClose(utilityCustomFilterOverlay);
        setOverlayHiddenForMenuClose(utilityKeybindOverlay);
        setOverlayHiddenForMenuClose(utilityAdminToolsOverlay);
        DihModule global = DihModule.get();
        if (global != null) {
            setOverlayHiddenForMenuClose(global.getPacketLoggerOverlayIfExists());
            setOverlayHiddenForMenuClose(global.getServerDataOverlayIfExists());

            setOverlayHiddenForMenuClose(global.getMultiOverlayIfExists());
        }
        setOverlayHiddenForMenuClose(DihMacroEditorOverlay.getSharedOverlay());
        setOverlayHiddenForMenuClose(dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay());
        if (utilityCustomFilterOverlay != null) setOverlayHiddenForMenuClose(utilityCustomFilterOverlay.getPresetManagerOverlay());
    }

    private void setOverlayHiddenForMenuClose(IDihOverlay overlay) {
        if (overlay == null || !overlay.isVisible()) return;
        TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.add(overlay.getOverlayId());
        DihOverlayManager.get().setTemporarilyHidden(overlay, true);
    }

    private void restoreTemporarilyHiddenUtilityOverlays() {
        if (TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.isEmpty()) return;
        Set<String> restoreIds = new LinkedHashSet<>(TEMPORARILY_HIDDEN_UTILITY_OVERLAYS);
        TEMPORARILY_HIDDEN_UTILITY_OVERLAYS.clear();
        for (IDihOverlay overlay : DihOverlayManager.get().getOverlays()) {
            if (overlay != null && restoreIds.contains(overlay.getOverlayId())) {
                DihOverlayManager.get().setTemporarilyHidden(overlay, false);
            }
        }
    }

    private void hideRuntimeOverlaysForTitleSetup() {
        DihOverlayManager manager = DihOverlayManager.get();
        titleSetupHiddenOverlayIds.clear();
        for (IDihOverlay overlay : manager.getOverlays()) {
            if (overlay == null || !overlay.isVisible() || isTitleSetupOverlay(overlay)) continue;
            titleSetupHiddenOverlayIds.add(overlay.getOverlayId());
            manager.setTemporarilyHidden(overlay, true);
        }
    }

    private boolean isTitleSetupOverlay(IDihOverlay overlay) {
        return overlay == utilityMacroListOverlay
            || overlay == utilityLanSyncOverlay
            || overlay == utilityQueueEditorOverlay
            || overlay == utilityCustomFilterOverlay
            || utilityCustomFilterOverlay != null && overlay == utilityCustomFilterOverlay.getPresetManagerOverlay()
            || overlay == utilityKeybindOverlay
            || DihModule.get() != null && overlay == DihModule.get().getPacketLoggerOverlayIfExists()
            || DihModule.get() != null && overlay == DihModule.get().getServerDataOverlayIfExists()
            || overlay == DihMacroEditorOverlay.getSharedOverlay()
            || overlay == dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay();
    }

    private void restoreRuntimeOverlaysHiddenForTitleSetup() {
        if (titleSetupHiddenOverlayIds.isEmpty()) return;
        DihOverlayManager manager = DihOverlayManager.get();
        for (IDihOverlay overlay : manager.getOverlays()) {
            if (overlay != null && titleSetupHiddenOverlayIds.contains(overlay.getOverlayId())) {
                manager.setTemporarilyHidden(overlay, false);
            }
        }
        titleSetupHiddenOverlayIds.clear();
    }

    private <T extends IDihOverlay> T findRegisteredOverlay(Class<T> type, String overlayId) {
        for (IDihOverlay overlay : DihOverlayManager.get().getOverlays()) {
            if (overlay == null || !type.isInstance(overlay)) continue;
            if (overlayId != null && !overlayId.equals(overlay.getOverlayId())) continue;
            return type.cast(overlay);
        }
        return null;
    }

    private void runUtility(String id) {
        if ("macros".equals(id)) {
            toggleMacroPanel();
            return;
        }
        if ("keys".equals(id)) {
            toggleOverlay(utilityKeybindOverlay);
            return;
        }
        if (isTitleSetup()) {
            switch (id) {
                case "lan" -> toggleOverlay(utilityLanSyncOverlay);
                case "queue" -> toggleOverlay(utilityQueueEditorOverlay);
                case "packets" -> toggleOverlay(utilityCustomFilterOverlay);
                case "logger" -> {
                    DihModule global = DihModule.get();
                    if (global != null) toggleOverlay(global.getPacketLoggerOverlay());
                }
                case "server" -> {
                    DihModule global = DihModule.get();
                    if (global != null) toggleOverlay(global.getServerDataOverlay());
                }
                case "matchmaking" -> {
                    DihModule global = DihModule.get();
                    if (global != null) {
                        dihclient.util.IDihOverlay mm = global.getMatchmakingOverlay();
                        if (mm != null) {
                            DihOverlayManager.get().register(mm);
                            ((dihclient.util.DihMatchmakingOverlay) mm).setMainMenuMode(true);
                            mm.setVisible(true);
                            if (minecraft != null) minecraft.gui.setScreen(new DihOverlayHostScreen(mm, this, true));
                        }
                    }
                }
                case "multi" -> {
                    if (minecraft != null) {
                        dihclient.gui.screen.DihMultiDisclaimerScreen.open(
                            minecraft, this, () -> minecraft.gui.setScreen(new DihMultiScreen(this, "")));
                    }
                }
                default -> {
                }
            }
            return;
        }
        DihModule global = DihModule.get();
        if (global == null) return;
        switch (id) {
            case "admin" -> toggleOverlay(utilityAdminToolsOverlay);
            case "lan" -> toggleOverlay(utilityLanSyncOverlay);
            case "queue" -> toggleOverlay(utilityQueueEditorOverlay);
            case "logger" -> {
                DihPacketLoggerOverlay logger = global.getPacketLoggerOverlay();
                if (logger != null) {
                    logger.restoreLayout();
                    DihOverlayManager.get().register(logger);
                    toggleOverlay(logger);
                }
            }
            case "packets" -> toggleOverlay(utilityCustomFilterOverlay);
            case "server" -> {
                DihServerInfoOverlay serverInfo = global.getServerDataOverlay();
                if (serverInfo != null) {
                    DihOverlayManager.get().register(serverInfo);
                    toggleOverlay(serverInfo);
                }
            }
            case "matchmaking" -> {
                dihclient.util.IDihOverlay matchmaking = global.getMatchmakingOverlay();
                if (matchmaking != null) {
                    DihOverlayManager.get().register(matchmaking);
                    ((dihclient.util.DihMatchmakingOverlay) matchmaking).setMainMenuMode(false);
                    toggleOverlay(matchmaking);
                }
            }
            case "multi" -> global.toggleMultiUiBehavior();
            case "send" -> {
                boolean newValue = !DihSharedState.get().shouldSendGuiPackets();
                global.applySendGuiPacketsUiBehavior(newValue);
                dihclient.util.DihNotifications.show("Send Packets " + (newValue ? "on" : "off"), newValue ? 0xFF35D873 : 0xFFFF3B3B);
            }
            case "delay" -> {
                boolean newValue = !DihSharedState.get().shouldDelayGuiPackets();
                int sent = global.applyDelayGuiPacketsUiBehavior(newValue);
                global.notifyDelayPacketsUiResult(newValue, sent);
            }
            case "flush" -> {
                int count = global.flushQueuedPacketsUiBehavior();
                global.notifyFlushQueuedPacketsUiResult(count);
            }
            case "clear" -> {
                int count = global.clearQueuedPacketsUiBehavior();
                global.notifyClearQueuedPacketsUiResult(count);
            }
            default -> {
            }
        }
    }

    private void toggleMacroPanel() {
        DihMacroEditorOverlay editor = DihMacroEditorOverlay.getSharedOverlay();
        if (editor != null) DihOverlayManager.get().register(editor);
        if (editor != null && editor.isVisible()) {
            if (utilityMacroListOverlay != null) utilityMacroListOverlay.setVisible(false);
            DihOverlayManager.get().bringToFront(editor);
            return;
        }
        toggleOverlay(utilityMacroListOverlay);
    }

    private void toggleOverlay(IDihOverlay overlay) {
        if (overlay == null) return;
        DihOverlayManager.get().register(overlay);
        overlay.setVisible(!overlay.isVisible());
        if (overlay.isVisible()) DihOverlayManager.get().bringToFront(overlay);
    }

    private DihFabricatorOverlay utilityFabricatorOverlay() {
        if (!(parent instanceof AbstractContainerScreen<?> handledScreen)) return null;
        utilityFabricatorOverlay = DihFabricatorOverlay.getSharedOverlay(handledScreen);
        utilityFabricatorOverlay.restoreState();
        DihOverlayManager.get().register(utilityFabricatorOverlay);
        return utilityFabricatorOverlay;
    }

    private void addQuickToggleMacroStep(Module module) {
        if (module == null) return;
        DihMacroEditorOverlay editor = DihMacroEditorOverlay.getSharedOverlay();
        if (editor == null) return;
        editor.setConfigurationOnly(isTitleSetup());
        DihOverlayManager manager = DihOverlayManager.get();
        manager.register(editor);
        manager.setTemporarilyHidden(editor, false);
        if (!editor.isVisible() || DihSharedState.get().getEditingMacro() == null) {
            editor.open(null, true);
        } else {
            editor.setVisible(true);
            manager.bringToFront(editor);
        }
        if (utilityMacroListOverlay != null) utilityMacroListOverlay.setVisible(false);
        editor.addAction(new ToggleModuleAction(module.name()));
    }

    private void resetConfigurationOnlyOverlays() {
        if (utilityMacroListOverlay != null) utilityMacroListOverlay.setConfigurationOnly(false);
        if (utilityLanSyncOverlay != null) utilityLanSyncOverlay.setConfigurationOnly(false);
        if (utilityQueueEditorOverlay != null) utilityQueueEditorOverlay.setConfigurationOnly(false);
        dihclient.gui.macro.editor.ActionEditorOverlay actionEditor = dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
        if (actionEditor != null) actionEditor.setConfigurationOnly(false);
        DihModule global = DihModule.get();
        if (global != null) {
            DihPacketLoggerOverlay logger = global.getPacketLoggerOverlayIfExists();
            if (logger != null) logger.setConfigurationOnly(false);
            DihServerInfoOverlay serverInfo = global.getServerDataOverlayIfExists();
            if (serverInfo != null) serverInfo.setConfigurationOnly(false);
        }
        DihMacroEditorOverlay editor = DihMacroEditorOverlay.getSharedOverlay();
        if (editor != null) editor.setConfigurationOnly(false);
    }

    private void saveUtilityOverlayStates() {
        saveOverlayState(utilityMacroListOverlay);
        saveOverlayState(utilityFabricatorOverlay);
        saveOverlayState(utilityLanSyncOverlay);
        saveOverlayState(utilityQueueEditorOverlay);
        saveOverlayState(utilityCustomFilterOverlay);
        saveOverlayState(utilityKeybindOverlay);
        saveOverlayState(utilityAdminToolsOverlay);
        DihModule global = DihModule.get();
        if (global != null) {
            saveOverlayState(global.getPacketLoggerOverlayIfExists());
            saveOverlayState(global.getServerDataOverlayIfExists());
        }
        saveOverlayState(DihMacroEditorOverlay.getSharedOverlay());
        saveOverlayState(dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay());
        if (utilityCustomFilterOverlay != null) saveOverlayState(utilityCustomFilterOverlay.getPresetManagerOverlay());
    }

    private void saveOverlayState(IDihOverlay overlay) {
        if (overlay == null) return;
        if (overlay instanceof DihMacroListOverlay macroList) macroList.saveState();
        else if (overlay instanceof DihFabricatorOverlay fabricator) fabricator.saveState();
        else if (overlay instanceof DihLANSyncOverlay lanSync) lanSync.saveState();
        else if (overlay instanceof DihQueueEditorOverlay queue) queue.saveState();
        else if (overlay instanceof DihCustomFilterOverlay filter) filter.saveLayout();
        else if (overlay instanceof DihKeybindOverlay keybind) keybind.saveLayout();
        else if (overlay instanceof DihPacketLoggerOverlay logger) logger.saveState();
        else if (overlay instanceof DihServerInfoOverlay serverInfo) serverInfo.saveState();
        else if (overlay instanceof DihMacroEditorOverlay macroEditor) macroEditor.saveState();
        else overlay.saveLayout();
    }

    private boolean passMovementKey(KeyEvent input, boolean down) {
        if (blocksGlobalKeybinds()) return false;
        if (minecraft == null || minecraft.options == null) return false;
        KeyMapping[] movement = {
            minecraft.options.keyUp,
            minecraft.options.keyDown,
            minecraft.options.keyLeft,
            minecraft.options.keyRight,
            minecraft.options.keyJump,
            minecraft.options.keyShift,
            minecraft.options.keySprint
        };
        for (KeyMapping key : movement) {
            if (key != null && key.matches(input)) {
                key.setDown(down);
                return true;
            }
        }
        return false;
    }

    private final class ModuleMenuHost implements VanillaModuleMenuController.Host {
        @Override
        public boolean offlineSetup() {
            return isTitleSetup();
        }

        @Override
        public Screen screen() {
            return DihModuleScreen.this;
        }

        @Override
        public Font font() {
            return DihModuleScreen.this.font;
        }

        @Override
        public void closeMenu() {
            DihModuleScreen.this.onClose();
        }

        @Override
        public void saveConfig() {
            DihConfig.getGlobal().save();
        }

        @Override
        public void openPacketSelector(Module module, Setting<?, ?> option) {
            DihModuleScreen.this.openPacketSelector(module, option);
        }

        @Override
        public void openStringListEditor(Module module, Setting<?, ?> option) {
            if (minecraft != null) {
                returnSettingsModuleId = module == null ? null : module.id();
                minecraft.gui.setScreen(new DihStringListSettingScreen(DihModuleScreen.this, module, option));
            }
        }

        @Override
        public void openRegistryListEditor(Module module, Setting<?, ?> option) {
            if (minecraft != null) {
                returnSettingsModuleId = module == null ? null : module.id();
                minecraft.gui.setScreen(new DihRegistryListSettingScreen(DihModuleScreen.this, module, option));
            }
        }

        @Override
        public void openMacroCreator(Module module, Setting<?, ?> option) {
            openMacroCreator(module, option, null);
        }

        @Override
        public void openMacroCreator(Module module, Setting<?, ?> option, dihclient.util.DihMacro macro) {
            DihMacroEditorOverlay editor = DihMacroEditorOverlay.getSharedOverlay();
            if (editor == null) return;
            editor.setConfigurationOnly(isTitleSetup());
            DihOverlayManager manager = DihOverlayManager.get();
            manager.register(editor);
            manager.setTemporarilyHidden(editor, false);
            editor.open(macro, true);
            editor.setVisible(true);
            manager.bringToFront(editor);
            if (utilityMacroListOverlay != null) utilityMacroListOverlay.setVisible(false);
        }

        @Override
        public void runUtility(String id) {
            DihModuleScreen.this.runUtility(id);
        }

        @Override
        public void addToggleMacro(Module module) {
            DihModuleScreen.this.addQuickToggleMacroStep(module);
        }
    }
}
