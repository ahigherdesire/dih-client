package dihclient.util;

import dihclient.commands.DihCommands;
import dihclient.gui.vanillaui.assets.UiAssets;
import dihclient.gui.vanillaui.direct.DirectUiButton;
import dihclient.gui.vanillaui.direct.DirectUiInsets;
import dihclient.gui.vanillaui.direct.DirectRenderContext;
import dihclient.gui.vanillaui.direct.DirectCompactRow;
import dihclient.gui.vanillaui.direct.DirectAccordionSection;
import dihclient.gui.vanillaui.direct.DirectSpacer;
import dihclient.gui.vanillaui.direct.DirectIconLabel;
import dihclient.gui.vanillaui.direct.DirectMetricLabel;
import dihclient.gui.vanillaui.direct.DirectPanel;
import dihclient.gui.vanillaui.direct.DirectSurface;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.components.CompactTextInput;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.gui.vanillaui.direct.DirectViewport;
import dihclient.gui.vanillaui.components.ConnectedButton;
import dihclient.mixin.accessor.DihHandledScreenAccessor;
import dihclient.modules.DihModule;
import dihclient.modules.PackHideState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.function.Supplier;

public class DihLauncherOverlay extends DihOverlayBase {
    private static final Minecraft MC = Minecraft.getInstance();

    private static final int DEFAULT_X = 0;
    private static final int DEFAULT_Y = 0;
    private static final String SHARED_MAIN_MENU_LAYOUT = "MAIN_MENU";
    private static final String LEGACY_MODULE_UTILITIES_LAYOUT = "UTILITIES";
    private static final int PANEL_PAD = 0;
    private static final int SECTION_PAD = 4;
    private static final int SECTION_GAP = 3;
    private static final int ROW_GAP = 2;
    private static final int BUTTON_HEIGHT = 20;
    private static final int CHAT_HEIGHT = 20;
    private static final int CHAT_MIN_WIDTH = 136;
    private static final int TEXT_PAD = 8;
    private static final int ICON_SIZE = 14;
    private static final int ICON_GAP = 4;
    private static final int LABEL_ICON_SIZE = 13;
    private static final int MIN_PAIR_WIDTH = 58;
    private static final int MIN_PANEL_WIDTH = 164;
    private static final float HEADER_CLICK_DRAG_THRESHOLD = 3.0f;

    private final CompactTheme theme = new CompactTheme();
    private final DirectPanel panelNode = new DirectPanel();
    private final DirectSurface surface = new DirectSurface(theme, panelNode);

    private final DihMacroListOverlay macroListOverlay;
    private final DihFabricatorOverlay fabricatorOverlay;
    private final DihLANSyncOverlay lanSyncOverlay;
    private final DihQueueEditorOverlay queueEditorOverlay;
    private final DihPacketLoggerOverlay packetLoggerOverlay;
    private final DihCustomFilterOverlay customFilterOverlay;
    private Supplier<DihPacketLoggerOverlay> packetLoggerOverlaySupplier;
    private Supplier<DihServerInfoOverlay> serverInfoOverlaySupplier;
    private final String overlayId;
    private final boolean includeFabricatorButton;
    private final boolean includeScreenSection;
    private final boolean includeChatSection;
    private Runnable closeWithoutPacketAction;
    private Runnable desyncAction;
    private DihKeybindOverlay keybindOverlay;
    private DihServerInfoOverlay serverInfoOverlay;

    private DirectUiButton sendButton;
    private DirectUiButton delayButton;
    private DirectUiButton multiButton;
    private CompactTextInput chatField;
    private static final String CHAT_PLACEHOLDER = "Type message or /command...";
    private DirectMetricLabel revMetric;
    private DirectMetricLabel syncMetric;
    private DirectMetricLabel slotMetric;
    private DirectAccordionSection mainMenuSection;

    private int panelX = DEFAULT_X;
    private int panelY = DEFAULT_Y;
    private int panelWidth = MIN_PANEL_WIDTH;
    private int panelHeight = 220;
    private boolean visible = true;
    private boolean dragging = false;
    private float dragOffsetX;
    private float dragOffsetY;
    private float pressStartUiX;
    private float pressStartUiY;
    private int pressStartPanelX;
    private int pressStartPanelY;
    private boolean dragMoved = false;

    public DihLauncherOverlay(DihMacroListOverlay macroListOverlay,
                                   DihFabricatorOverlay fabricatorOverlay,
                                   DihLANSyncOverlay lanSyncOverlay,
                                   DihQueueEditorOverlay queueEditorOverlay,
                                   DihPacketLoggerOverlay packetLoggerOverlay,
                                   DihCustomFilterOverlay customFilterOverlay) {
        this(macroListOverlay, fabricatorOverlay, lanSyncOverlay, queueEditorOverlay, packetLoggerOverlay, customFilterOverlay, "dih-launcher", true, true);
    }

    public DihLauncherOverlay(DihMacroListOverlay macroListOverlay,
                                   DihFabricatorOverlay fabricatorOverlay,
                                   DihLANSyncOverlay lanSyncOverlay,
                                   DihQueueEditorOverlay queueEditorOverlay,
                                   DihPacketLoggerOverlay packetLoggerOverlay,
                                   DihCustomFilterOverlay customFilterOverlay,
                                   String overlayId,
                                   boolean includeScreenSection,
                                   boolean includeChatSection) {
        this(macroListOverlay, fabricatorOverlay, lanSyncOverlay, queueEditorOverlay, packetLoggerOverlay, customFilterOverlay, overlayId, true, includeScreenSection, includeChatSection);
    }

    public DihLauncherOverlay(DihMacroListOverlay macroListOverlay,
                                   DihFabricatorOverlay fabricatorOverlay,
                                   DihLANSyncOverlay lanSyncOverlay,
                                   DihQueueEditorOverlay queueEditorOverlay,
                                   DihPacketLoggerOverlay packetLoggerOverlay,
                                   DihCustomFilterOverlay customFilterOverlay,
                                   String overlayId,
                                   boolean includeFabricatorButton,
                                   boolean includeScreenSection,
                                   boolean includeChatSection) {
        this.macroListOverlay = macroListOverlay;
        this.fabricatorOverlay = fabricatorOverlay;
        this.lanSyncOverlay = lanSyncOverlay;
        this.queueEditorOverlay = queueEditorOverlay;
        this.packetLoggerOverlay = packetLoggerOverlay;
        this.customFilterOverlay = customFilterOverlay;
        this.packetLoggerOverlaySupplier = () -> this.packetLoggerOverlay;
        this.overlayId = overlayId == null || overlayId.isBlank() ? "dih-launcher" : overlayId;
        this.includeFabricatorButton = includeFabricatorButton;
        this.includeScreenSection = includeScreenSection;
        this.includeChatSection = includeChatSection;
        buildUi();
        restoreLayout();
        if (mainMenuSection != null) {
            mainMenuSection.syncExpanded(mainMenuSection.isExpanded());
        }
    }

    public void setCloseWithoutPacketAction(Runnable action) {
        this.closeWithoutPacketAction = action;
        buildUi();
    }

    public void setDesyncAction(Runnable action) {
        this.desyncAction = action;
        buildUi();
    }

    public void setKeybindOverlay(DihKeybindOverlay overlay) {
        this.keybindOverlay = overlay;
    }

    public void setServerDataOverlay(DihServerInfoOverlay overlay) {
        this.serverInfoOverlay = overlay;
        this.serverInfoOverlaySupplier = () -> this.serverInfoOverlay;
    }

    public void setPacketLoggerOverlaySupplier(Supplier<DihPacketLoggerOverlay> supplier) {
        this.packetLoggerOverlaySupplier = supplier == null ? () -> this.packetLoggerOverlay : supplier;
    }

    public void setServerDataOverlaySupplier(Supplier<DihServerInfoOverlay> supplier) {
        this.serverInfoOverlaySupplier = supplier == null ? () -> this.serverInfoOverlay : supplier;
    }

    private DihPacketLoggerOverlay packetLoggerOverlay() {
        DihPacketLoggerOverlay overlay = packetLoggerOverlaySupplier == null ? packetLoggerOverlay : packetLoggerOverlaySupplier.get();
        if (overlay != null) DihOverlayManager.get().register(overlay);
        return overlay;
    }

    private DihServerInfoOverlay serverInfoOverlay() {
        DihServerInfoOverlay overlay = serverInfoOverlaySupplier == null ? serverInfoOverlay : serverInfoOverlaySupplier.get();
        if (overlay != null) DihOverlayManager.get().register(overlay);
        return overlay;
    }

    private void buildUi() {
        panelNode.setPadding(DirectUiInsets.all(PANEL_PAD)).setDrawBorder(false).setDrawFill(false);
        panelNode.content().clearChildren();
        panelNode.content().setPadding(DirectUiInsets.NONE).setGap(0);

        mainMenuSection = new DirectAccordionSection("Main Menu")
            .setHeaderHeight(sectionHeaderHeight())
            .setContentTopGap(sectionContentTopGap())
            .setExpanded(true);
        mainMenuSection.content().setPadding(new DirectUiInsets(1, 0, 1, 1)).setGap(rowGap());
        panelNode.content().add(mainMenuSection);

        DirectUiButton macrosButton = actionButton("Macros", UiAssets.ICON_MACROS, DirectUiButton.Variant.SECONDARY, () -> {
            DihMacroEditorOverlay macroEditorOverlay = DihMacroEditorOverlay.getSharedOverlay();
            if (macroEditorOverlay != null && macroEditorOverlay.isVisible()) {
                if (macroListOverlay != null) macroListOverlay.setVisible(false);
                DihOverlayManager.get().bringToFront(macroEditorOverlay);
            } else if (macroListOverlay != null) {
                toggleRegisteredUtilityOverlay(macroListOverlay);
            }
        }).setGrowX(true);
        if (includeFabricatorButton) {
            addPairRow(mainMenuSection.content(),
                macrosButton,
                actionButton("Fabricator", UiAssets.ICON_FABRICATOR, DirectUiButton.Variant.SECONDARY, () -> {
                    if (fabricatorOverlay != null) toggleRegisteredUtilityOverlay(fabricatorOverlay);
                }).setGrowX(true)
            );
        } else {
            mainMenuSection.content().add(macrosButton);
        }
        addPairRow(mainMenuSection.content(),
            actionButton("LAN Sync", UiAssets.ICON_LANSYNC, DirectUiButton.Variant.SECONDARY, () -> {
                if (lanSyncOverlay != null) toggleRegisteredUtilityOverlay(lanSyncOverlay);
            }).setGrowX(true),
            actionButton("Queue", UiAssets.ICON_PACKET_Q_EDITOR, DirectUiButton.Variant.SECONDARY, () -> {
                if (queueEditorOverlay != null) toggleRegisteredUtilityOverlay(queueEditorOverlay);
            }).setGrowX(true)
        );
        addPairRow(mainMenuSection.content(),
            actionButton("Logger", UiAssets.ICON_PACKET_LOGGER, DirectUiButton.Variant.SECONDARY, () -> {
                DihPacketLoggerOverlay overlay = packetLoggerOverlay();
                toggleRegisteredUtilityOverlay(overlay);
            }).setGrowX(true),
            actionButton("Packets", UiAssets.ICON_FILTER, DirectUiButton.Variant.SECONDARY, () -> {
                if (customFilterOverlay != null) toggleRegisteredUtilityOverlay(customFilterOverlay);
            }).setGrowX(true)
        );
        addPairRow(mainMenuSection.content(),
            actionButton("Server Info", UiAssets.ICON_SERVER_INFO, DirectUiButton.Variant.SECONDARY, () -> {
                DihServerInfoOverlay overlay = serverInfoOverlay();
                toggleRegisteredUtilityOverlay(overlay);
            }).setGrowX(true),
            actionButton("Settings", UiAssets.ICON_KEYBINDS, DirectUiButton.Variant.SECONDARY, () -> {
                if (keybindOverlay != null) toggleRegisteredUtilityOverlay(keybindOverlay);
            }).setGrowX(true)
        );

        if (!dihclient.util.DihLiteVariant.enabled()) {
            DirectUiButton matchmakingButton = actionButton("Matchmaking", UiAssets.ICON_MATCHMAKING, DirectUiButton.Variant.SECONDARY, () -> {
                dihclient.modules.DihModule mod = dihclient.modules.DihModule.get();
                if (mod != null) {
                    dihclient.util.IDihOverlay ov = mod.getMatchmakingOverlay();
                    if (ov != null) { ((dihclient.util.DihMatchmakingOverlay) ov).setMainMenuMode(false); toggleRegisteredUtilityOverlay(ov); }
                }
            }).setGrowX(true);
            multiButton = actionButton("Multi", UiAssets.ICON_MULTI, DirectUiButton.Variant.SECONDARY, () -> {
                dihclient.modules.DihModule mod = dihclient.modules.DihModule.get();
                if (mod != null) mod.toggleMultiUiBehavior();
            }).setGrowX(true);
            addPairRow(mainMenuSection.content(), matchmakingButton, multiButton);
        }

        addSubCategory("PACKET", UiAssets.ICON_PACKET_CATEGORY);
        sendButton = actionButton("Send", null, DirectUiButton.Variant.DANGER, this::toggleSendPackets).setConnectedToggle(true).setGrowX(true);
        delayButton = actionButton("Delay", null, DirectUiButton.Variant.DANGER, this::toggleDelayPackets).setConnectedToggle(true).setGrowX(true);
        addPairRow(mainMenuSection.content(), sendButton, delayButton);
        addPairRow(mainMenuSection.content(),
            actionButton("Flush", null, DirectUiButton.Variant.SECONDARY, this::flushQueue).setGrowX(true),
            actionButton("Clear", null, DirectUiButton.Variant.SECONDARY, this::clearQueue).setGrowX(true)
        );

        if (includeScreenSection) {
            addSubCategory("SCREEN", UiAssets.ICON_SCREEN_CATEGORY);
            addPairRow(mainMenuSection.content(),
                actionButton("Close", null, DirectUiButton.Variant.SECONDARY,
                    closeWithoutPacketAction != null ? closeWithoutPacketAction : () -> DihGuiActions.closeCurrentScreen(MC, false)).setGrowX(true),
                actionButton("De-sync", null, DirectUiButton.Variant.DANGER, desyncAction != null ? desyncAction : this::sendDesync).setGrowX(true)
            );
            addPairRow(mainMenuSection.content(),
                actionButton("Save", null, DirectUiButton.Variant.SECONDARY, this::saveGui).setGrowX(true),
                actionButton("Load", null, DirectUiButton.Variant.SECONDARY, this::loadGui).setGrowX(true)
            );
            addPairRow(mainMenuSection.content(),
                    actionButton("Title", null, DirectUiButton.Variant.SECONDARY, DihGuiClipboardUtil::copyGuiTitleJson).setGrowX(true),
                actionButton("Disc+Send", null, DirectUiButton.Variant.DANGER, this::disconnectAndSend).setGrowX(true)
            );
        }

        if (includeChatSection) {
            chatField = new CompactTextInput()
                .setGrowX(true)
                .setFieldHeight(chatHeight())
                .setPreferredWidth(chatMinWidth())
                .setMinWidth(chatMinWidth())
                .setHorizontalPadding(6)
                .setPlaceholder(CHAT_PLACEHOLDER)
                .setHistoryNavigationEnabled(true)
                .setHistoryProvider(() -> MC.gui != null ? MC.gui.hud.getChat().getRecentChat() : java.util.List.of())
                .setOnSubmit(this::submitChat);
            mainMenuSection.content().add(chatField);

            mainMenuSection.content().add(new DirectSpacer(0, 2));
            DirectCompactRow syncRow = new DirectCompactRow().setGap(syncRowGap()).setPadding(new DirectUiInsets(4, 0, 2, 0)).setUnderlineOnHover(false);
            syncRow.setGrowX(true);
            revMetric = syncRow.add(new DirectMetricLabel("Rev: ", "--").setKeyColor(DihTheme.recolor(0xFFB79E9E, DihTheme.Channel.TEXT)).setValueColor(DihTheme.recolor(0xFFFF4A4A, DihTheme.Channel.ACCENT)).setGrowX(true));
            syncMetric = syncRow.add(new DirectMetricLabel("SyncID: ", "--").setKeyColor(DihTheme.recolor(0xFFB79E9E, DihTheme.Channel.TEXT)).setValueColor(DihTheme.recolor(0xFFF3ECE7, DihTheme.Channel.TEXT)).setGrowX(true));
            slotMetric = syncRow.add(new DirectMetricLabel("Slot: ", "--").setKeyColor(DihTheme.recolor(0xFFB79E9E, DihTheme.Channel.TEXT)).setValueColor(DihTheme.recolor(0xFF8FD7FF, DihTheme.Channel.ACCENT)).setGrowX(true));
            mainMenuSection.content().add(syncRow);
        } else {
            chatField = null;
            revMetric = null;
            syncMetric = null;
            slotMetric = null;
        }
    }

    private void toggleRegisteredUtilityOverlay(IDihOverlay overlay) {
        if (overlay == null) return;
        DihOverlayManager manager = DihOverlayManager.get();
        boolean wasRegistered = manager.getOverlays().contains(overlay);
        manager.register(overlay);
        manager.setTemporarilyHidden(overlay, false);
        if (!wasRegistered && overlay.isVisible()) {
            manager.bringToFront(overlay);
            return;
        }
        overlay.setVisible(!overlay.isVisible());
        if (overlay.isVisible()) manager.bringToFront(overlay);
    }

    private void addSubCategory(String label, Identifier icon) {
        if (!mainMenuSection.content().children().isEmpty()) {
            mainMenuSection.content().add(new DirectSpacer(0, sectionGap()));
        }
        mainMenuSection.content().add(new DirectIconLabel(label, icon).setIconSize(labelIconSize()).setConnectedStyle(true));
    }

    private DirectUiButton addFullButton(dihclient.gui.vanillaui.direct.DirectUiColumn parent, String label, Identifier icon, Runnable action) {
        DirectUiButton button = actionButton(label, icon, DirectUiButton.Variant.SECONDARY, action).setGrowX(true);
        parent.add(button);
        return button;
    }

    private void addPairRow(dihclient.gui.vanillaui.direct.DirectUiColumn parent, DirectUiButton left, DirectUiButton right) {
        DirectCompactRow row = new DirectCompactRow().setGap(rowGap()).setPadding(DirectUiInsets.NONE).setUnderlineOnHover(false);
        row.add(left.setConnectedEdges(ConnectedButton.LEFT_CELL));
        row.add(right.setConnectedEdges(ConnectedButton.RIGHT_CELL));
        row.setGrowX(true);
        parent.add(row);
    }

    private DirectUiButton actionButton(String label, Identifier icon, DirectUiButton.Variant variant, Runnable action) {
        DirectUiButton button = new DirectUiButton(label, variant, action)
            .setGrowX(false)
            .setConnectedStyle(true)
            .setButtonHeight(buttonHeight())
            .setHorizontalPadding(buttonPadding())
            .setMinWidth(requiredButtonWidth(label, icon))
            .setTextYOffset(0);
        if (icon != null) {
            button.setLeadingIcon(icon)
                .setContentAlignment(DirectUiButton.ContentAlignment.START)
                .setIconSize(buttonIconSize())
                .setIconGap(buttonIconGap());
        }
        return button;
    }

    @Override
    public String getOverlayId() {
        return overlayId;
    }

    @Override
    public int getMinWidth() {
        return computePanelWidth();
    }

    @Override
    public int getMinHeight() {
        return minimumPanelHeight();
    }

    @Override
    public DihWindowLayout getBounds() {
        return new DihWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, !mainMenuSection.isExpanded());
    }

    @Override
    public void setBounds(DihWindowLayout bounds) {
        if (bounds == null) return;
        DihWindowLayout clamped = clampToViewport(bounds);
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        visible = clamped.visible;
        if (mainMenuSection != null) {
            mainMenuSection.syncExpanded(!clamped.collapsed);
        }
    }

    @Override
    public void saveLayout() {
        DihSharedState.get().setWindowLayout(getOverlayId(), getBounds());
        DihConfig config = DihConfig.getGlobal();
        DihConfig.ModuleCategoryLayout layout = config.moduleCategoryLayouts.computeIfAbsent(SHARED_MAIN_MENU_LAYOUT, ignored -> new DihConfig.ModuleCategoryLayout());

        DihWindowLayout trueGeometry = DihOverlayManager.get().clampedAwayTrueGeometry(getOverlayId());
        int saveX = trueGeometry != null ? trueGeometry.x : panelX;
        int saveY = trueGeometry != null ? trueGeometry.y : panelY;

        if (layout.x == saveX && layout.y == saveY && layout.collapsed == isCollapsed()) return;
        layout.x = saveX;
        layout.y = saveY;
        layout.collapsed = isCollapsed();
        config.save();
    }

    @Override
    public void restoreLayout() {
        DihWindowLayout saved = DihSharedState.get().getWindowLayout(getOverlayId());
        boolean savedVisible = saved == null || saved.visible;
        boolean savedCollapsed = saved != null && saved.collapsed;
        DihConfig config = DihConfig.getGlobal();
        DihConfig.ModuleCategoryLayout layout = config.moduleCategoryLayouts.get(SHARED_MAIN_MENU_LAYOUT);
        if (layout == null) {
            DihConfig.ModuleCategoryLayout old = config.moduleCategoryLayouts.get(LEGACY_MODULE_UTILITIES_LAYOUT);
            if (old != null) {
                layout = new DihConfig.ModuleCategoryLayout();
                layout.x = old.x;
                layout.y = old.y;
                layout.collapsed = old.collapsed;
                config.moduleCategoryLayouts.put(SHARED_MAIN_MENU_LAYOUT, layout);
            }
        }
        if (layout != null && layout.x >= 0 && layout.y >= 0) {
            setBounds(new DihWindowLayout(layout.x, layout.y, panelWidth, panelHeight, savedVisible, layout.collapsed || savedCollapsed));
        } else if (saved != null) {
            setBounds(saved);
        }
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        DihSharedState shared = DihSharedState.get();
        if (sendButton != null) {
            boolean sending = shared.shouldSendGuiPackets();
            sendButton.setText("Send");
            sendButton.setVariant(sending ? DirectUiButton.Variant.SUCCESS : DirectUiButton.Variant.DANGER);
        }
        if (delayButton != null) {
            boolean delaying = shared.shouldDelayGuiPackets();
            delayButton.setText("Delay");
            delayButton.setVariant(delaying ? DirectUiButton.Variant.SUCCESS : DirectUiButton.Variant.DANGER);
        }
        if (multiButton != null) {
            dihclient.util.multi.MultiManager multi = dihclient.util.multi.MultiManager.get();
            multiButton.setText(multi.isActive() ? "Multi " + multi.readyFraction() : "Multi");
            multiButton.setVariant(multi.isActive() ? DirectUiButton.Variant.SUCCESS : DirectUiButton.Variant.SECONDARY);
        }
        updateSyncMetrics();
        updateChatGhost();

        panelWidth = computePanelWidth();
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        DirectRenderContext metrics = new DirectRenderContext(context, MC.font, viewport, theme, uiMouseX, uiMouseY, delta);
        panelHeight = Math.max(getMinHeight(), Math.round(panelNode.preferredHeight(metrics, panelWidth)));
        DihWindowLayout clamped = clampToViewport(new DihWindowLayout(
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            visible,
            mainMenuSection != null && !mainMenuSection.isExpanded()
        ));
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        panelNode.setActive(true);
        panelNode.setBounds(panelX, panelY, panelWidth, panelHeight);
        surface.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (button == 0 && isOverMainMenuHeader(uiMouseX, uiMouseY) && mainMenuSection != null) {
            dragging = true;
            dragMoved = false;
            dragOffsetX = uiMouseX - panelX;
            dragOffsetY = uiMouseY - panelY;
            pressStartUiX = uiMouseX;
            pressStartUiY = uiMouseY;
            pressStartPanelX = panelX;
            pressStartPanelY = panelY;
            return true;
        }

        if (surface.mouseClicked(mouseX, mouseY, button)) return true;
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            saveLayout();
            return true;
        }
        return surface.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            DirectViewport viewport = surface.viewport();
            float uiMouseX = viewport.toUiX(mouseX);
            float uiMouseY = viewport.toUiY(mouseY);
            DihWindowLayout clamped = clampToViewport(new DihWindowLayout(
                Math.round(uiMouseX - dragOffsetX),
                Math.round(uiMouseY - dragOffsetY),
                panelWidth,
                panelHeight,
                visible,
                mainMenuSection != null && !mainMenuSection.isExpanded()
            ));
            panelX = clamped.x;
            panelY = clamped.y;
            dragMoved = dragMoved
                || Math.abs(uiMouseX - pressStartUiX) >= HEADER_CLICK_DRAG_THRESHOLD
                || Math.abs(uiMouseY - pressStartUiY) >= HEADER_CLICK_DRAG_THRESHOLD
                || panelX != pressStartPanelX
                || panelY != pressStartPanelY;
            return true;
        }
        return surface.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return surface.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || isCollapsed()) return false;

        if (keyCode == GLFW.GLFW_KEY_TAB && chatField != null && chatField.isFocused()
            && chatField.text().isEmpty()) {
            String last = lastSentChatLine();
            if (last != null && !last.isBlank()) {
                chatField.setText(last);
                chatField.moveCursorToEnd();
            }
            return true;
        }
        return surface.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!visible || isCollapsed()) return false;
        return surface.charTyped(chr, modifiers);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            surface.clearFocusedTextInputs();
            dragging = false;
            dragMoved = false;
        } else if (mainMenuSection != null) {
            mainMenuSection.syncExpanded(mainMenuSection.isExpanded());
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiX = viewport.toUiX(mouseX);
        float uiY = viewport.toUiY(mouseY);
        return uiX >= panelX && uiX < panelX + panelWidth
            && uiY >= panelY && uiY < panelY + panelHeight;
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        return isOverMainMenuHeader(viewport.toUiX(mouseX), viewport.toUiY(mouseY));
    }

    @Override
    public boolean usesSharedHeaderClickCollapse() {
        return true;
    }

    @Override
    public boolean isCollapsed() {
        return mainMenuSection != null && !mainMenuSection.isExpanded();
    }

    @Override
    public void setCollapsed(boolean collapsed) {
        if (isCollapsed() == collapsed) return;
        if (mainMenuSection != null) {
            mainMenuSection.syncExpanded(!collapsed);
        }
        if (collapsed) clearHiddenInteractionState();
        saveLayout();
    }

    @Override
    public boolean hasTextFieldFocused() {
        return surface.hasFocusedTextInput();
    }

    @Override
    public void clearTextFieldFocus() {
        surface.clearFocusedTextInputs();
    }

    private boolean isOverMainMenuHeader(float uiMouseX, float uiMouseY) {
        return mainMenuSection != null
            && uiMouseX >= panelX
            && uiMouseX < panelX + panelWidth
            && uiMouseY >= panelY
            && uiMouseY < panelY + sectionHeaderHeight();
    }

    private DihWindowLayout clampToViewport(DihWindowLayout bounds) {
        DirectViewport viewport = surface.viewport();
        int margin = 4;
        int viewportW = Math.round(viewport.uiWidth());
        int viewportH = Math.round(viewport.uiHeight());
        int availableW = Math.max(1, viewportW - margin * 2);
        int availableH = Math.max(sectionHeaderHeight(), viewportH - margin * 2);
        int width = Math.max(Math.min(getMinWidth(), availableW), Math.min(bounds.width, availableW));
        int minHeight = bounds.collapsed ? sectionHeaderHeight() : getMinHeight();
        int height = Math.max(Math.min(minHeight, availableH), Math.min(bounds.height, availableH));
        int renderedHeight = bounds.collapsed ? sectionHeaderHeight() : height;
        int x = Math.max(margin, Math.min(bounds.x, Math.max(margin, viewportW - margin - width)));
        int y = Math.max(margin, Math.min(bounds.y, Math.max(margin, viewportH - margin - renderedHeight)));
        return new DihWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
    }

    private net.minecraft.client.gui.Font cachedWidthFont;
    private int cachedWidthReloadGen = Integer.MIN_VALUE;
    private boolean cachedWidthFabricator, cachedWidthScreen, cachedWidthChat;
    private int cachedPanelWidthValue = Integer.MIN_VALUE;

    private int computePanelWidth() {
        if (MC.font == null) return minPanelWidth();
        int reloadGen = UiText.reloadGeneration();
        if (cachedPanelWidthValue != Integer.MIN_VALUE
            && cachedWidthFont == MC.font && cachedWidthReloadGen == reloadGen
            && cachedWidthFabricator == includeFabricatorButton
            && cachedWidthScreen == includeScreenSection
            && cachedWidthChat == includeChatSection) {
            return cachedPanelWidthValue;
        }
        int computed = computePanelWidthUncached();
        cachedWidthFont = MC.font;
        cachedWidthReloadGen = reloadGen;
        cachedWidthFabricator = includeFabricatorButton;
        cachedWidthScreen = includeScreenSection;
        cachedWidthChat = includeChatSection;
        cachedPanelWidthValue = computed;
        return computed;
    }

    private int computePanelWidthUncached() {
        int pairWidth = minPairWidth();
        pairWidth = Math.max(pairWidth, requiredButtonWidth("Macros", UiAssets.ICON_MACROS));
        if (includeFabricatorButton) pairWidth = Math.max(pairWidth, requiredButtonWidth("Fabricator", UiAssets.ICON_FABRICATOR));
        pairWidth = Math.max(pairWidth, requiredButtonWidth("LAN Sync", UiAssets.ICON_LANSYNC));
        pairWidth = Math.max(pairWidth, requiredButtonWidth("Queue", UiAssets.ICON_PACKET_Q_EDITOR));
        pairWidth = Math.max(pairWidth, requiredButtonWidth("Logger", UiAssets.ICON_PACKET_LOGGER));
        pairWidth = Math.max(pairWidth, requiredButtonWidth("Packets", UiAssets.ICON_FILTER));
        pairWidth = Math.max(pairWidth, requiredButtonWidth("Server Info", UiAssets.ICON_SERVER_INFO));
        pairWidth = Math.max(pairWidth, requiredButtonWidth("Settings", UiAssets.ICON_KEYBINDS));

        if (!dihclient.util.DihLiteVariant.enabled()) {
            pairWidth = Math.max(pairWidth, requiredButtonWidth("Matchmaking", UiAssets.ICON_MATCHMAKING));
            pairWidth = Math.max(pairWidth, requiredButtonWidth(
                "Multi " + dihclient.util.multi.MultiProfile.MAX_SESSIONS, UiAssets.ICON_MULTI));
        }
        pairWidth = Math.max(pairWidth, requiredButtonWidth("Send", null));
        pairWidth = Math.max(pairWidth, requiredButtonWidth("Delay", null));
        pairWidth = Math.max(pairWidth, requiredButtonWidth("Flush", null));
        pairWidth = Math.max(pairWidth, requiredButtonWidth("Clear", null));
        if (includeScreenSection) {
            pairWidth = Math.max(pairWidth, requiredButtonWidth("Close", null));
            pairWidth = Math.max(pairWidth, requiredButtonWidth("De-sync", null));
            pairWidth = Math.max(pairWidth, requiredButtonWidth("Save", null));
            pairWidth = Math.max(pairWidth, requiredButtonWidth("Load", null));
            pairWidth = Math.max(pairWidth, requiredButtonWidth("Title", null));
            pairWidth = Math.max(pairWidth, requiredButtonWidth("Disc+Send", null));
        }

        int chatWidth = includeChatSection ? Math.max(chatMinWidth(), requiredTextFieldWidth("Type message or /command...")) : 0;
        int headerWidth = UiText.width(MC.font, "MAIN MENU", theme.fontFor(UiTone.LABEL), theme.color(UiTone.LABEL))
            + labelIconSize() + buttonIconGap() + headerReserveWidth();
        int subLabelWidth = requiredIconLabelWidth("PACKET");
        if (includeScreenSection) subLabelWidth = Math.max(subLabelWidth, requiredIconLabelWidth("SCREEN"));
        int contentWidth = Math.max(headerWidth, Math.max(subLabelWidth,
            Math.max((pairWidth * 2) + rowGap(), chatWidth)));
        return Math.max(minPanelWidth(), contentWidth + (sectionPadding() * 2) + panelWidthReserve());
    }

    private int requiredButtonWidth(String label, Identifier icon) {
        if (MC.font == null) return minPairWidth();
        int width = UiText.width(MC.font, label, theme.fontFor(UiTone.BODY), theme.color(UiTone.BODY)) + (buttonPadding() * 2) + buttonOutlineReserve();
        if (icon != null) width += buttonIconSize() + buttonIconGap();
        return width;
    }

    private int requiredIconLabelWidth(String label) {
        return UiText.width(MC.font, label, theme.fontFor(UiTone.LABEL), theme.color(UiTone.LABEL)) + labelIconSize() + buttonIconGap() + buttonOutlineReserve();
    }

    private int requiredTextFieldWidth(String placeholder) {
        return UiText.width(MC.font, placeholder, theme.fontFor(UiTone.BODY), theme.color(UiTone.MUTED)) + textFieldExtraWidth() + buttonOutlineReserve();
    }

    private int sectionPadding() {
        return 0;
    }

    private int sectionGap() {
        return 0;
    }

    private int rowGap() {
        return 0;
    }

    private int buttonHeight() {
        return 15;
    }

    private int buttonPadding() {
        return 5;
    }

    private int buttonIconSize() {
        return 12;
    }

    private int buttonIconGap() {
        return 3;
    }

    private int labelIconSize() {
        return 11;
    }

    private int minPairWidth() {
        return 54;
    }

    private int minPanelWidth() {
        return 152;
    }

    private int minimumPanelHeight() {
        return 56;
    }

    private int sectionHeaderHeight() {
        return 15;
    }

    private int sectionContentTopGap() {
        return 0;
    }

    private int chatHeight() {
        return 16;
    }

    private int chatMinWidth() {
        return 124;
    }

    private int syncRowGap() {
        return 4;
    }

    private int headerReserveWidth() {
        return 16;
    }

    private int textFieldExtraWidth() {
        return 10;
    }

    private int buttonOutlineReserve() {
        return 2;
    }

    private int panelWidthReserve() {
        return 2;
    }

        private void toggleSendPackets() {
        DihSharedState shared = DihSharedState.get();
        boolean newValue = !shared.shouldSendGuiPackets();
        DihModule.get().applySendGuiPacketsUiBehavior(newValue);
        DihNotifications.show("Send Packets " + (newValue ? "on" : "off"), newValue ? 0xFF35D873 : 0xFFFF3B3B);
    }

    private void toggleDelayPackets() {
        DihSharedState shared = DihSharedState.get();
        boolean newValue = !shared.shouldDelayGuiPackets();
        DihModule module = DihModule.get();
        int sent = module.applyDelayGuiPacketsUiBehavior(newValue);
        module.notifyDelayPacketsUiResult(newValue, sent);
    }

    private void flushQueue() {
        DihModule module = DihModule.get();
        int count = module.flushQueuedPacketsUiBehavior();
        module.notifyFlushQueuedPacketsUiResult(count);
    }

    private void clearQueue() {
        DihModule module = DihModule.get();
        int count = module.clearQueuedPacketsUiBehavior();
        module.notifyClearQueuedPacketsUiResult(count);
    }

    private void sendDesync() {
        if (!DihGuiActions.desyncCurrentScreen(MC)) {
            DihClientMessaging.sendPrefixed("Failed to desync: no open networked GUI.");
        }
    }

    private void saveGui() {
        DihGuiActions.saveCurrentGui(MC);
    }

    private void loadGui() {
        if (DihModule.get().restoreSavedScreenUiBehavior()) {
            DihNotifications.show("GUI restored.", 0xFF35D873);
        } else {
            DihNotifications.error("No stored GUI.");
        }
    }

    private void disconnectAndSend() {
        if (PackHideState.isHardLocked()) return;
        DihSharedState shared = DihSharedState.get();
        DihModule.get().setDelayGuiPackets(false);
        if (MC.getConnection() != null) {
            shared.flushDelayedPackets(MC.getConnection());
            MC.getConnection().getConnection().disconnect(Component.literal("Disconnecting (Dih)"));
        }
    }

    private void submitChat(String raw) {
        if (PackHideState.isHardLocked()) return;
        if (raw == null) return;
        String message = raw.trim();
        if (message.isEmpty()) {

            String last = lastSentChatLine();
            if (last == null || last.isBlank()) return;
            message = last.trim();
        }

        if (DihCommands.isDihCommandMessage(message)) {
            if (DihCommands.isBlockedPanicCommandMessage(message)) {
                if (chatField != null) {
                    chatField.setText("");
                    chatField.setFocused(false);
                }
                return;
            }
            String body = DihCommands.commandBody(message);
            if (!body.isBlank()) DihCommands.dispatch(body);
        } else if (MC.getConnection() == null) {
            return;
        } else if (message.startsWith("/") && message.length() > 1) {
            MC.getConnection().sendCommand(message.substring(1));
        } else {
            MC.getConnection().sendChat(message);
        }

        DihClientMessaging.rememberRecentChat(message);
        if (chatField != null) {
            chatField.addHistoryEntry(message);
        }
        if (chatField != null) {
            chatField.setText("");
            chatField.setFocused(false);
        }
    }

    private void updateChatGhost() {
        if (chatField == null) return;
        String last = lastSentChatLine();
        chatField.setPlaceholder(last != null && !last.isBlank() ? last : CHAT_PLACEHOLDER);
    }

    private String lastSentChatLine() {
        try {
            if (MC.gui == null || MC.gui.hud.getChat() == null) return null;
            net.minecraft.util.ArrayListDeque<String> recent = MC.gui.hud.getChat().getRecentChat();
            return recent == null ? null : recent.peekLast();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void updateSyncMetrics() {
        String revisionText = "--";
        String syncIdText = "--";
        String slotText = "--";

        if (MC.player != null && MC.player.containerMenu != null) {
            revisionText = Integer.toString(MC.player.containerMenu.getStateId());
            syncIdText = Integer.toString(MC.player.containerMenu.containerId);
            if (MC.gui.screen() instanceof AbstractContainerScreen<?> handledScreen) {
                Slot focusedSlot = ((DihHandledScreenAccessor) handledScreen).getFocusedSlot();
                if (focusedSlot != null) {
                    slotText = Integer.toString(DihInventoryHelper.toUserVisibleSlot(MC, focusedSlot.index));
                }
            }
        }

        if (revMetric != null) revMetric.setValue(revisionText);
        if (syncMetric != null) syncMetric.setValue(syncIdText);
        if (slotMetric != null) slotMetric.setValue(slotText);
    }
}
