package dihclient.util;

import dihclient.gui.vanillaui.assets.UiAssets;
import dihclient.gui.vanillaui.direct.DirectUiButton;
import dihclient.gui.vanillaui.direct.DirectUiInsets;
import dihclient.gui.vanillaui.direct.DirectUiLabel;
import dihclient.gui.vanillaui.direct.DirectRenderContext;
import dihclient.gui.vanillaui.direct.DirectRow;
import dihclient.gui.vanillaui.direct.DirectSurface;
import dihclient.gui.vanillaui.components.CompactScrollbar;
import dihclient.gui.vanillaui.components.CompactListRenderer;
import dihclient.gui.vanillaui.components.CompactListViewport;
import dihclient.gui.vanillaui.components.ScrollState;
import dihclient.gui.vanillaui.components.UiSizing;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.components.CompactTextInput;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.gui.vanillaui.direct.DirectViewport;
import dihclient.gui.vanillaui.direct.DirectViewportSlot;
import dihclient.gui.vanillaui.direct.DirectWindow;
import dihclient.gui.vanillaui.components.CompactSurfaces;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.components.OverlayTopBar;
import dihclient.util.macro.MacroExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DihMacroListOverlay extends DihOverlayBase {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final int MIN_PANEL_WIDTH = 258;
    private static final int ROW_HEIGHT = 18;
    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int PAD = 6;
    private static final int HEADER_CONTROL = 12;
    private static final int HEADER_ARROW_WIDTH = 10;
    private static final int HEADER_ARROW_GAP = 3;
    private static final long METEOR_CACHE_MS = 2000L;

    private static final int ROW_BUTTON_HEIGHT = 14;
    private static final int DELETE_BUTTON_WIDTH = 16;
    private static final int ROW_BUTTON_GAP = 4;
    private static final int ROW_BUTTON_PADDING = 2;
    private static final int VIEWPORT_BORDER = 1;

    private final Font textRenderer;
    private final DihMacroEditorOverlay activeEditor;
    private final CompactTheme theme = new CompactTheme();
    private final DirectWindow windowNode = new DirectWindow("Macro Library");
    private final DirectSurface surface = new DirectSurface(theme, windowNode);
    private final CompactTextInput searchField = new CompactTextInput();
    private final CompactTextInput pasteNameField = new CompactTextInput();
    private final DirectViewportSlot listSlot = new DirectViewportSlot();

    private int panelX = 500;
    private int panelY = 250;
    private int panelWidth = MIN_PANEL_WIDTH;
    private int panelHeight = 220;
    private boolean visible = false;
    private boolean collapsed = false;
    private boolean dragging = false;
    private float dragOffsetX;
    private float dragOffsetY;
    private final ScrollState listScroll = new ScrollState();
    private boolean pasteMode = false;
    private boolean scrollbarDragging = false;
    private int scrollbarGrabOffset = 0;

    private int lastKnownMacroCount = -1;
    private long lastKnownMacroRevision = Long.MIN_VALUE;
    private int lastTitleCount = Integer.MIN_VALUE;
    private String lastTitle = "";
    private boolean lastKnownPasteMode = false;
    private boolean needsUiRebuild = true;
    private boolean configurationOnly;

    private final List<ClickRegion> clickRegions = new ArrayList<>();
    private final List<RowButton> rowButtonScratch = new ArrayList<>(4);
    private List<DisplayItem> currentItems = List.of();
    private List<DihMacro> cachedMeteorMacros = Collections.emptyList();
    private long meteorMacroCacheTime = 0L;
    private long cachedVisibleCountRevision = Long.MIN_VALUE;
    private int cachedVisibleLocalMacroCount;

    public DihMacroListOverlay(Font textRenderer) {
        this(textRenderer, null);
    }

    public DihMacroListOverlay(Font textRenderer, DihMacroEditorOverlay activeEditor) {
        this.textRenderer = textRenderer;
        this.activeEditor = activeEditor;
        buildUi();
    }

    private void buildUi() {
        panelWidth = panelMinimumWidth();
        windowNode.setCenterTitle(false);
        windowNode.setTitleTone(UiTone.LABEL);
        windowNode.setHeaderControls(true, true);
        windowNode.setTitleAreaInsets(panelPadding() + 2, panelPadding() + headerControlSize() + headerArrowWidth() + headerArrowGap() + 12);
        windowNode.content().setGap(windowContentGap()).setPadding(DirectUiInsets.all(panelPadding()));

        searchField
            .setPlaceholder("Search macros...")
            .setFieldHeight(searchFieldHeight())
            .setGrowX(true)
            .setOnChange(text -> {
                listScroll.jumpTo(0, 0);
                needsUiRebuild = true;
            });

        pasteNameField
            .setPlaceholder("New macro name...")
            .setFieldHeight(searchFieldHeight())
            .setGrowX(true)
            .setOnSubmit(text -> pasteMacroFromClipboard());

        rebuildUi();
    }

    private void rebuildData() {
        DihLANSync sync = DihLANSync.getInstance();
        currentItems = buildDisplayItems(
            DihMacroManager.get().getAll(),
            getCachedMeteorMacros(),
            sync.getAllRemoteMacros(),
            sync.getAllRemoteMeteorMacros(),
            sync
        );
    }

    private void rebuildUi() {
        windowNode.content().clearChildren();
        rebuildData();
        windowNode.setTitle("Macro Library [" + visibleLocalMacroCount() + "]");

        windowNode.content().add(searchField);

        if (currentItems.isEmpty()) {
            windowNode.content().add(new DirectUiLabel("No macros match the current filter.", UiTone.MUTED).setTrimToBounds(true));
        } else {
            listSlot.setPreferredHeight(computeViewportHeight(currentItems.size()));
            windowNode.content().add(listSlot);
        }

        if (pasteMode) {
            windowNode.content().add(pasteNameField);

            DirectRow actions = new DirectRow().setGap(actionRowGap());
            actions.add(new DirectUiButton("Paste", DirectUiButton.Variant.DANGER, this::pasteMacroFromClipboard).setGrowX(true).setButtonHeight(actionButtonHeight()));
            actions.add(new DirectUiButton("Cancel", DirectUiButton.Variant.SECONDARY, this::cancelPasteMode).setGrowX(true).setButtonHeight(actionButtonHeight()));
            windowNode.content().add(actions);
            windowNode.content().add(new DirectUiLabel("Paste a copied macro under a new name.", UiTone.MUTED).setTrimToBounds(true));
        } else {
            DirectRow actions = new DirectRow().setGap(actionRowGap());
            actions.add(new DirectUiButton("Create New", DirectUiButton.Variant.SUCCESS, this::openCreateNew).setGrowX(true).setButtonHeight(actionButtonHeight()));
            actions.add(new DirectUiButton("Paste", DirectUiButton.Variant.DANGER, this::beginPasteMode).setGrowX(true).setButtonHeight(actionButtonHeight()));
            windowNode.content().add(actions);
        }
    }

    private int computeViewportHeight(int itemCount) {
        int visibleRows = Math.max(3, Math.min(maxVisibleRows(), itemCount));
        return visibleRows * rowHeight() + (VIEWPORT_BORDER * 2);
    }

    private List<DihMacro> getCachedMeteorMacros() {
        long now = System.currentTimeMillis();
        if (now - meteorMacroCacheTime > METEOR_CACHE_MS) {
            cachedMeteorMacros = MeteorMacroAdapter.getMeteorMacros();
            meteorMacroCacheTime = now;
        }
        return cachedMeteorMacros;
    }

    public DihMacroEditorOverlay getActiveEditor() {
        return activeEditor;
    }

    public void setConfigurationOnly(boolean configurationOnly) {
        if (this.configurationOnly == configurationOnly) return;
        this.configurationOnly = configurationOnly;
        this.needsUiRebuild = true;
    }

    public void saveState() {
        DihSharedState shared = DihSharedState.get();
        shared.setMacroListOverlayVisible(visible);
        shared.setMacroListOverlayX(panelX);
        shared.setMacroListOverlayY(panelY);
        shared.setMacroListOverlayScrollOffset(listScroll.targetOffset());
        shared.setMacroListOverlaySearch(searchField.text());
        saveLayout();
    }

    public void restoreState() {
        DihSharedState shared = DihSharedState.get();
        restoreLayout();
        this.visible = shared.isMacroListOverlayVisible();
        this.panelX = shared.getMacroListOverlayX();
        this.panelY = shared.getMacroListOverlayY();
        listScroll.restore(shared.getMacroListOverlayScrollOffset());
        searchField.setText(shared.getMacroListOverlaySearch());
        needsUiRebuild = true;
        windowNode.restoreShowBody(!collapsed);
    }

    @Override
    public String getOverlayId() {
        return "dih-macrolist";
    }

    @Override
    public int getMinWidth() {
        return panelMinimumWidth();
    }

    @Override
    public int getMinHeight() {
        return theme.headerHeight() + searchFieldHeight() + actionButtonHeight() + (panelPadding() * 2) + windowContentGap();
    }

    @Override
    public DihWindowLayout getBounds() {
        return new DihWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed);
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
        collapsed = clamped.collapsed;
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible && activeEditor != null && activeEditor.isVisible()) {
            this.visible = false;
            saveState();
            DihOverlayManager.get().bringToFront(activeEditor);
            return;
        }
        this.visible = visible;
        if (!visible) {
            surface.clearFocusedTextInputs();
            dragging = false;
        } else {
            needsUiRebuild = true;
            windowNode.restoreShowBody(!collapsed);
            DihOverlayManager.get().bringToFront(this);
        }
        saveState();
    }

    public void toggle() {
        setVisible(!visible);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        dragging = false;
        if (collapsed) {
            surface.clearFocusedTextInputs();
        }
        saveState();
    }

    @Override
    public boolean usesSharedHeaderClickCollapse() {
        return true;
    }

    @Override
    public boolean hasTextFieldFocused() {
        return surface.hasFocusedTextInput();
    }

    @Override
    public void clearTextFieldFocus() {
        surface.clearFocusedTextInputs();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiX = viewport.toUiX(mouseX);
        float uiY = viewport.toUiY(mouseY);
        return uiX >= panelX && uiX <= panelX + panelWidth
            && uiY >= panelY && uiY <= panelY + panelHeight;
    }

    @Override
    public boolean isOverDragBar(double mouseX, double mouseY) {
        if (!visible) return false;
        DirectViewport viewport = surface.viewport();
        float uiX = viewport.toUiX(mouseX);
        float uiY = viewport.toUiY(mouseY);
        return isOverHeaderUi(uiX, uiY) && !isOverCloseButton(uiX, uiY);
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible || MC == null || MC.font == null) return;

        long currentMacroRevision = DihMacroManager.get().getRevision();
        int currentMacroCount = visibleLocalMacroCount();
        if (currentMacroRevision != lastKnownMacroRevision) needsUiRebuild = true;
        if (needsUiRebuild || currentMacroCount != lastKnownMacroCount || pasteMode != lastKnownPasteMode) {
            rebuildUi();
            lastKnownMacroCount = currentMacroCount;
            lastKnownMacroRevision = currentMacroRevision;
            lastKnownPasteMode = pasteMode;
            needsUiRebuild = false;
        } else {
            if (currentMacroCount != lastTitleCount) {
                lastTitleCount = currentMacroCount;
                lastTitle = "Macro Library [" + currentMacroCount + "]";
            }
            windowNode.setTitle(lastTitle);
        }

        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);
        boolean active = DihOverlayManager.get().isFocusedOverlay(this) || DihOverlayManager.get().isTopOverlay(this);
        boolean headerHovered = isOverHeaderUi(uiMouseX, uiMouseY);

        DirectRenderContext metrics = new DirectRenderContext(context, MC.font, viewport, theme, uiMouseX, uiMouseY, delta);
        windowNode.setShowBody(!collapsed);
        windowNode.setActive(active);
        windowNode.setHeaderHovered(headerHovered);

        panelHeight = Math.round(windowNode.preferredHeight(metrics, panelWidth));
        panelHeight = Math.max(theme.headerHeight(), panelHeight);
        DihWindowLayout clamped = clampToViewport(new DihWindowLayout(panelX, panelY, panelWidth, panelHeight, visible, collapsed));
        panelX = clamped.x;
        panelY = clamped.y;
        panelWidth = clamped.width;
        panelHeight = clamped.height;
        windowNode.setBounds(panelX, panelY, panelWidth, panelHeight);

        surface.render(context, mouseX, mouseY, delta);
        if (panelHeight > theme.headerHeight() + 1 && !currentItems.isEmpty()) {
            renderlistViewport(context, viewport, uiMouseX, uiMouseY, delta, active);
        } else {
            clickRegions.clear();
        }
    }

    private void renderlistViewport(GuiGraphicsExtractor context, DirectViewport viewport, float uiMouseX, float uiMouseY, float delta, boolean active) {
        clickRegions.clear();

        int viewX = Math.round(listSlot.x());
        int viewY = Math.round(listSlot.y());
        int viewW = Math.round(listSlot.width());
        int viewH = Math.round(listSlot.height());
        if (viewW <= 2 || viewH <= 2) return;

        CompactListViewport.Layout baseLayout = macroListLayout(viewX, viewY, viewW, viewH, 0);
        int drawScroll = listScroll.tick(delta, baseLayout.maxScroll());
        CompactListViewport.Layout listLayout = macroListLayout(viewX, viewY, viewW, viewH, drawScroll);

        float alpha = active ? 1.0f : 0.56f;
        UiRenderer.frame(
            context,
            UiBounds.of(viewX, viewY, viewW, viewH),
            DirectRenderContext.applyAlpha(theme.listFill(), alpha),
            DirectRenderContext.applyAlpha(theme.borderSoft(), alpha)
        );

        listLayout.beginRows(context);
        try {
            DirectRenderContext rowContext = new DirectRenderContext(context, textRenderer, viewport, theme, uiMouseX, uiMouseY, delta, alpha);
            listLayout.forEachVisibleRow(currentItems.size(), (i, rowY) -> {
                DisplayItem item = currentItems.get(i);
                if (item.type == ItemType.SECTION_HEADER) {
                    renderSectionHeader(context, rowContext, item, viewX + VIEWPORT_BORDER, rowY, listLayout.contentWidth());
                } else {
                    renderMacroRow(context, rowContext, item, viewX + VIEWPORT_BORDER, rowY, listLayout.contentWidth());
                }
            });
        } finally {
            listLayout.endRows(context);
        }

        listLayout.drawScrollbar(context, uiMouseX, uiMouseY, scrollbarDragging);
    }

    private CompactScrollbar.Metrics getScrollbarMetrics(int viewX, int viewY, int viewW, int viewH) {
        CompactListViewport.Layout baseLayout = macroListLayout(viewX, viewY, viewW, viewH, 0);
        return macroListLayout(viewX, viewY, viewW, viewH, listScroll.tick(0.0f, baseLayout.maxScroll())).scrollbar();
    }

    private CompactListViewport.Layout macroListLayout(int viewX, int viewY, int viewW, int viewH, int scrollOffset) {
        return CompactListViewport.layout(
            viewX,
            viewY,
            viewW,
            viewH,
            currentItems.size(),
            rowHeight(),
            rowHeight(),
            scrollOffset,
            3,
            0,
            VIEWPORT_BORDER
        );
    }

    private void renderSectionHeader(GuiGraphicsExtractor context, DirectRenderContext rowContext, DisplayItem item, int x, int y, int width) {
        int textY = UiSizing.alignTextY(y, rowHeight(), theme.fontHeight(UiTone.LABEL), theme.bodyTextNudge());
        CompactSurfaces.divider(context, x + sectionHeaderInset(), y + rowHeight() - 2,
            width - (sectionHeaderInset() * 2), rowContext.applyAlpha(0x42FFFFFF));
        UiText.draw(
            context,
            textRenderer,
            item.label,
            theme.fontFor(UiTone.LABEL),
            rowContext.applyAlpha(item.color),
            x + sectionHeaderInset(),
            textY,
            false
        );
    }

    private void renderMacroRow(GuiGraphicsExtractor context, DirectRenderContext rowContext, DisplayItem item, int x, int y, int width) {
        boolean hovered = uiContains(x, y, width, rowHeight(), rowContext.mouseX(), rowContext.mouseY());
        if (hovered) {
            CompactSurfaces.tintedRow(context, x, y, width, rowHeight(), rowContext.applyAlpha(DihTheme.recolor(0x1AFF4A4A, DihTheme.Channel.ACCENT)));
        }

        List<RowButton> buttons = buildRowButtons(item);
        int buttonCursor = x + width - rowTextInset();
        int buttonY = y + rowButtonTopInset();
        for (int i = buttons.size() - 1; i >= 0; i--) {
            RowButton button = buttons.get(i);
            buttonCursor -= button.width;
            drawRowButton(context, rowContext, buttonCursor, buttonY, button.width, button);
            clickRegions.add(new ClickRegion(buttonCursor, buttonY, button.width, rowButtonHeight(), item, button.action));
            buttonCursor -= rowButtonGap();
        }

        int textLeft = x + rowTextInset();
        if (item.type == ItemType.LOCAL_MACRO) {
            boolean running = macroRunningForCurrentControl(item.macro.name);
            int dotColor = running ? rowContext.applyAlpha(0xFF33D968) : rowContext.applyAlpha(0xFF656565);
            int dotTop = y + UiSizing.alignMiddle(0, rowHeight(), rowDotSize());
            CompactSurfaces.indicator(context, textLeft, dotTop, rowDotSize(), rowDotSize(), dotColor);
            textLeft += rowTextInset() + 2;
        }

        int rightLimit = Math.max(textLeft + 20, buttonCursor - 2);
        int labelWidth = Math.max(20, rightLimit - textLeft);
        String primaryText = switch (item.type) {
            case LOCAL_MACRO -> item.macro.name;
            case REMOTE_MACRO -> item.label;
            case METEOR_MACRO -> "Meteor: " + item.macro.name;
            default -> item.label;
        };
        String secondaryText = switch (item.type) {
            case LOCAL_MACRO -> item.macro.actions.size() + " steps";
            case REMOTE_MACRO -> item.remoteSource == null || item.remoteSource.isBlank() ? "" : item.remoteSource;
            case METEOR_MACRO -> item.macro.actions.size() + " steps";
            default -> "";
        };

        int primaryColor = switch (item.type) {
            case LOCAL_MACRO -> rowContext.applyAlpha(theme.color(UiTone.BODY));
            case REMOTE_MACRO -> rowContext.applyAlpha(item.remoteMeteor ? 0xFFD6D2FF : 0xFFFFD0A6);
            case METEOR_MACRO -> rowContext.applyAlpha(0xFFD8E6FF);
            default -> rowContext.applyAlpha(theme.color(UiTone.BODY));
        };
        int secondaryColor = rowContext.applyAlpha(theme.color(UiTone.MUTED));
        int textY = UiSizing.alignTextY(y, rowHeight(), theme.fontHeight(UiTone.BODY), theme.bodyTextNudge());

        if (item.type == ItemType.LOCAL_MACRO) {
            int secondaryWidth = UiText.width(textRenderer, secondaryText, theme.fontFor(UiTone.BODY), secondaryColor);
            int secondaryX = Math.max(textLeft + 10, rightLimit - secondaryWidth);
            int primaryMaxWidth = Math.max(20, secondaryX - textLeft - 8);
            String trimmedPrimary = UiText.trimToWidth(textRenderer, primaryText, primaryMaxWidth, theme.fontFor(UiTone.BODY), primaryColor);
            UiText.draw(context, textRenderer, trimmedPrimary, theme.fontFor(UiTone.BODY), primaryColor, textLeft, textY, false);
            UiText.draw(context, textRenderer, secondaryText, theme.fontFor(UiTone.BODY), secondaryColor, secondaryX, textY, false);
        } else {
            String trimmedPrimary = UiText.trimToWidth(textRenderer, primaryText, labelWidth, theme.fontFor(UiTone.BODY), primaryColor);
            UiText.draw(context, textRenderer, trimmedPrimary, theme.fontFor(UiTone.BODY), primaryColor, textLeft, textY, false);
            if (!secondaryText.isEmpty()) {
                int secondaryX = textLeft + Math.min(labelWidth - 10, Math.max(UiText.width(textRenderer, trimmedPrimary, theme.fontFor(UiTone.BODY), primaryColor) + 10, labelWidth - 90));
                int secondaryWidth = Math.max(0, rightLimit - secondaryX);
                if (secondaryWidth > 18) {
                    String trimmedSecondary = UiText.trimToWidth(textRenderer, secondaryText, secondaryWidth, theme.fontFor(UiTone.BODY), secondaryColor);
                    UiText.draw(context, textRenderer, trimmedSecondary, theme.fontFor(UiTone.BODY), secondaryColor, secondaryX, textY, false);
                }
            }
        }
    }

    private void drawRowButton(GuiGraphicsExtractor context, DirectRenderContext rowContext, int x, int y, int width, RowButton button) {
        if (button.action == RowAction.DELETE) {
            int size = rowButtonHeight();
            CompactListRenderer.drawDeleteButton(
                context,
                x + Math.max(0, (width - size) / 2),
                y,
                size,
                uiContains(x, y, width, rowButtonHeight(), rowContext.mouseX(), rowContext.mouseY())
            );
            return;
        }

        int h = rowButtonHeight();
        boolean hovered = uiContains(x, y, width, h, rowContext.mouseX(), rowContext.mouseY());
        dihclient.gui.vanillaui.UiContext ui = dihclient.gui.vanillaui.UiContexts.overlay(
            rowContext.drawContext(), rowContext.textRenderer(),
            Math.round(rowContext.mouseX()), Math.round(rowContext.mouseY()));
        dihclient.gui.vanillaui.components.Button.render(
            ui, dihclient.gui.vanillaui.UiBounds.of(x, y, width, h),
            button.label, DirectUiButton.toneFor(button.variant), hovered, false);
        if (button.icon != null) {
            int iconSize = 8;
            DihUiIcons.blit(context, button.icon,
                x + (width - iconSize) / 2, y + (h - iconSize) / 2, iconSize, 0xF2FFFFFF);
        }
    }

    private int measureRowButtonWidth(String label, int minWidth, int maxWidth) {
        return UiSizing.fitTextWidthInt(
            textRenderer,
            label,
            theme.fontFor(UiTone.BODY),
            theme.color(UiTone.BODY),
            rowButtonPadding(),
            minWidth,
            maxWidth
        );
    }

    private List<RowButton> buildRowButtons(DisplayItem item) {
        rowButtonScratch.clear();
        switch (item.type) {
            case LOCAL_MACRO -> {
                boolean running = macroRunningForCurrentControl(item.macro.name);
                if (!configurationOnly) {
                    rowButtonScratch.add(new RowButton("", running ? DirectUiButton.Variant.DANGER : DirectUiButton.Variant.SUCCESS, 20, RowAction.RUN_TOGGLE,
                        running ? DihUiIcons.STOP : DihUiIcons.PLAY));
                }
                rowButtonScratch.add(new RowButton("", DirectUiButton.Variant.DANGER, 20, RowAction.EDIT, DihUiIcons.EDIT));
                rowButtonScratch.add(new RowButton("COPY", DirectUiButton.Variant.DANGER, measureRowButtonWidth("COPY", 26, 44), RowAction.COPY));
                rowButtonScratch.add(new RowButton("X", DirectUiButton.Variant.DANGER, deleteButtonWidth(), RowAction.DELETE));
            }
            case REMOTE_MACRO -> rowButtonScratch.add(new RowButton("IMPORT", DirectUiButton.Variant.SECONDARY, measureRowButtonWidth("IMPORT", 34, 58), RowAction.IMPORT_REMOTE));
            case METEOR_MACRO -> rowButtonScratch.add(new RowButton("REFACTOR", DirectUiButton.Variant.SECONDARY, measureRowButtonWidth("REFACTOR", 42, 70), RowAction.IMPORT_METEOR));
            default -> {
            }
        }
        return rowButtonScratch;
    }

    private void handleRowAction(DisplayItem item, RowAction action) {
        if (item == null || action == null) return;
        switch (action) {
            case RUN_TOGGLE -> {
                if (configurationOnly) return;
                if (item.macro == null) return;
                if (macroRunningForCurrentControl(item.macro.name)) {
                    if (dihclient.util.multi.MultiTakeoverState.isActive()) {
                        dihclient.util.multi.MultiManager multi = dihclient.util.multi.MultiManager.getIfInitialized();
                        if (multi != null) multi.stopMacroOnInteractiveScope(java.util.Set.of());
                    } else {
                        MacroExecutor.stopMacro(item.macro.name);
                    }
                } else {
                    item.macro.execute();
                }
            }
            case EDIT -> {
                if (item.macro == null) return;
                setVisible(false);
                DihMacroEditorOverlay.getSharedOverlay().open(item.macro, true);
            }
            case COPY -> {
                if (item.macro == null) return;
                try {
                    if (!DihClipboardHelper.copyMacroToClipboard(item.macro)) {
                        DihNotifications.error("Failed to copy macro.");
                        return;
                    }
                    DihNotifications.copied("Copied macro: " + item.macro.name);
                } catch (Exception ignored) {
                    DihNotifications.error("Failed to copy macro.");
                }
            }
            case DELETE -> {
                if (item.macro != null) DihMacroManager.get().delete(item.macro);
            }
            case IMPORT_REMOTE -> importRemoteMacro(item);
            case IMPORT_METEOR -> importMeteorMacro(item);
        }
    }

    private static boolean macroRunningForCurrentControl(String name) {
        if (dihclient.util.multi.MultiTakeoverState.isActive()) {
            dihclient.util.multi.MultiManager multi = dihclient.util.multi.MultiManager.getIfInitialized();
            return multi != null && multi.isMacroPlayingOnInteractiveScope(name, java.util.Set.of());
        }
        return MacroExecutor.isMacroRunning(name);
    }

    private void importRemoteMacro(DisplayItem item) {
        if (item.remoteSource == null || item.remoteSource.isBlank()) return;
        DihLANSync sync = DihLANSync.getInstance();
        Map<String, Map<String, DihMacro>> allRemote = item.remoteMeteor ? sync.getAllRemoteMeteorMacros() : sync.getAllRemoteMacros();
        Map<String, DihMacro> remoteMacros = allRemote.get(item.remoteSource);
        DihMacro source = remoteMacros != null ? remoteMacros.get(MacroNames.key(item.label)) : null;
        if (source == null) {
            DihClientMessaging.sendPrefixed("§cRemote macro is no longer available: " + item.label);
            return;
        }

        DihMacro imported = DihMacroManager.get().addImportedCopy(source, source.name);
        if (imported == null) {
            DihClientMessaging.sendPrefixed("§cFailed to import macro: " + item.label);
            return;
        }

        String sourceType = item.remoteMeteor ? "Meteor macro" : "macro";
        DihClientMessaging.sendPrefixed("§aImported " + sourceType + " as: " + imported.name);
        needsUiRebuild = true;
    }

    private void importMeteorMacro(DisplayItem item) {
        if (item.macro == null) return;
        DihMacro imported = DihMacroManager.get().addImportedCopy(item.macro, item.macro.name);
        if (imported == null) {
            DihClientMessaging.sendPrefixed("§cFailed to import from Meteor.");
            return;
        }
        DihClientMessaging.sendPrefixed("§aImported Meteor macro as: " + imported.name);
        needsUiRebuild = true;
    }

    private void openCreateNew() {
        setVisible(false);
                    DihMacroEditorOverlay.getSharedOverlay().open(null, true);
    }

    private void beginPasteMode() {
        pasteMode = true;
        pasteNameField.setText("");
        listScroll.jumpTo(0, 0);
        needsUiRebuild = true;
    }

    private void cancelPasteMode() {
        pasteMode = false;
        pasteNameField.setText("");
        pasteNameField.setFocused(false);
        needsUiRebuild = true;
    }

    private boolean pasteMacroFromClipboard() {
        String name = pasteNameField.text().trim();
        if (name.isEmpty()) {
            DihClientMessaging.sendPrefixed("§cEnter a name for the pasted macro.");
            return true;
        }
        if (DihMacroManager.get().get(name) != null) {
            DihClientMessaging.sendPrefixed("§cA macro with that name already exists.");
            return true;
        }

        DihMacro pasted = DihClipboardHelper.pasteMacroFromClipboard();
        if (pasted == null) {
            DihNotifications.error("Clipboard does not contain a valid macro.");
            return true;
        }

        pasted.name = name;
        DihMacroManager.get().add(pasted);
        DihClientMessaging.sendPrefixed("§aPasted macro: " + pasted.name);
        cancelPasteMode();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (button == 0 && isOverCloseButton(uiMouseX, uiMouseY)) {
            setVisible(false);
            return true;
        }

        if (button == 0 && isOverHeaderUi(uiMouseX, uiMouseY)) {
            dragging = true;
            dragOffsetX = uiMouseX - panelX;
            dragOffsetY = uiMouseY - panelY;
            return true;
        }

        if (!collapsed && surface.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (!collapsed && button == 0 && uiContains(listSlot.x(), listSlot.y(), listSlot.width(), listSlot.height(), uiMouseX, uiMouseY)) {
            CompactScrollbar.Metrics metrics = getScrollbarMetrics(Math.round(listSlot.x()), Math.round(listSlot.y()), Math.round(listSlot.width()), Math.round(listSlot.height()));
            if (metrics.hasScroll() && metrics.contains(uiMouseX, uiMouseY)) {
                scrollbarDragging = true;
                scrollbarGrabOffset = metrics.overThumb(uiMouseX, uiMouseY) ? (int) Math.round(uiMouseY) - metrics.thumbY() : metrics.thumbHeight() / 2;
                listScroll.setFromThumb(metrics, uiMouseY, scrollbarGrabOffset);
                return true;
            }
        }

        if (!collapsed && button == 0) {
            for (int i = clickRegions.size() - 1; i >= 0; i--) {
                ClickRegion region = clickRegions.get(i);
                if (region.contains(uiMouseX, uiMouseY)) {
                    handleRowAction(region.item, region.action);
                    return true;
                }
            }
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (button == 0 && dragging) {

            dragging = false;
            saveState();
            return true;
        }

        if (button == 0 && scrollbarDragging) {
            scrollbarDragging = false;
            saveState();
            return true;
        }

        if (!collapsed && surface.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!visible) return false;

        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (dragging && button == 0) {
            int nextX = Math.round(uiMouseX - dragOffsetX);
            int nextY = Math.round(uiMouseY - dragOffsetY);
            DihWindowLayout clamped = clampToViewport(new DihWindowLayout(nextX, nextY, panelWidth, panelHeight, visible, collapsed));
            panelX = clamped.x;
            panelY = clamped.y;
            return true;
        }

        if (scrollbarDragging && button == 0) {
            CompactScrollbar.Metrics metrics = getScrollbarMetrics(Math.round(listSlot.x()), Math.round(listSlot.y()), Math.round(listSlot.width()), Math.round(listSlot.height()));
            listScroll.setFromThumb(metrics, uiMouseY, scrollbarGrabOffset);
            return true;
        }

        if (!collapsed && surface.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible || collapsed) return false;
        DirectViewport viewport = surface.viewport();
        float uiMouseX = viewport.toUiX(mouseX);
        float uiMouseY = viewport.toUiY(mouseY);

        if (surface.mouseScrolled(mouseX, mouseY, amount)) {
            return true;
        }

        if (uiContains(listSlot.x(), listSlot.y(), listSlot.width(), listSlot.height(), uiMouseX, uiMouseY)) {
            CompactListViewport.Layout listLayout = macroListLayout(
                Math.round(listSlot.x()),
                Math.round(listSlot.y()),
                Math.round(listSlot.width()),
                Math.round(listSlot.height()),
                0
            );
            listScroll.nudge(amount, rowHeight(), listLayout.maxScroll());
            return true;
        }

        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (collapsed) return false;
        if (surface.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && pasteMode) {
            cancelPasteMode();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return visible && !collapsed && surface.charTyped(chr, modifiers);
    }

    private List<DisplayItem> buildDisplayItems(
        List<DihMacro> localMacros,
        List<DihMacro> meteorMacros,
        Map<String, Map<String, DihMacro>> remoteMacros,
        Map<String, Map<String, DihMacro>> remoteMeteorMacros,
        DihLANSync sync
    ) {
        String filter = searchField.text().trim().toLowerCase(Locale.ROOT);
        List<DisplayItem> items = new ArrayList<>();

        List<DisplayItem> localSection = new ArrayList<>();
        for (DihMacro macro : localMacros) {
            if (AutoFishStopMacroFactory.isGeneratedStopMacro(macro)) continue;
            if (!matchesFilter(macro.name, filter)) continue;
            localSection.add(DisplayItem.localMacro(macro));
        }
        if (!localSection.isEmpty()) {
            items.add(DisplayItem.section("DIH Client Macros", DihTheme.recolor(0xFFFF5555, DihTheme.Channel.ACCENT)));
            items.addAll(localSection);
        }

        List<DisplayItem> remoteSection = new ArrayList<>();
        if (sync.isInSession()) {
            for (Map.Entry<String, Map<String, DihMacro>> entry : remoteMacros.entrySet()) {
                for (DihMacro macro : entry.getValue().values()) {
                    if (AutoFishStopMacroFactory.isGeneratedStopMacro(macro)) continue;
                    if (DihMacroManager.get().get(macro.name) != null) continue;
                    if (!matchesFilter(macro.name, filter)) continue;
                    remoteSection.add(DisplayItem.remoteMacro(macro.name, entry.getKey(), false));
                }
            }
        }
        if (!remoteSection.isEmpty()) {
            if (localSection.isEmpty()) {
                items.add(DisplayItem.section("DIH Client Macros", DihTheme.recolor(0xFFFF5555, DihTheme.Channel.ACCENT)));
            }
            items.addAll(remoteSection);
        }

        if (!DihCompatManager.isMeteorAvailable()) return items;

        List<DisplayItem> meteorSection = new ArrayList<>();
        for (DihMacro macro : meteorMacros) {
            if (!matchesFilter(macro.name, filter)) continue;
            meteorSection.add(DisplayItem.meteorMacro(macro));
        }
        if (sync.isInSession()) {
            for (Map.Entry<String, Map<String, DihMacro>> entry : remoteMeteorMacros.entrySet()) {
                for (DihMacro macro : entry.getValue().values()) {
                    boolean alreadyLocalMeteor = false;
                    for (DihMacro localMeteor : meteorMacros) {
                        if (MacroNames.equal(localMeteor.name, macro.name)) {
                            alreadyLocalMeteor = true;
                            break;
                        }
                    }
                    if (alreadyLocalMeteor) continue;
                    if (!matchesFilter(macro.name, filter)) continue;
                    meteorSection.add(DisplayItem.remoteMacro(macro.name, entry.getKey(), true));
                }
            }
        }
        if (!meteorSection.isEmpty()) {
            items.add(DisplayItem.section("Meteor Macros", 0xFFD8E6FF));
            items.addAll(meteorSection);
        }

        return items;
    }

    private int visibleLocalMacroCount() {
        long revision = DihMacroManager.get().getRevision();
        if (revision == cachedVisibleCountRevision) return cachedVisibleLocalMacroCount;
        int count = 0;
        for (DihMacro macro : DihMacroManager.get().getAll()) {
            if (!AutoFishStopMacroFactory.isGeneratedStopMacro(macro)) count++;
        }
        cachedVisibleCountRevision = revision;
        cachedVisibleLocalMacroCount = count;
        return cachedVisibleLocalMacroCount;
    }

    private boolean matchesFilter(String name, String filter) {
        return filter.isEmpty() || (name != null && name.toLowerCase(Locale.ROOT).contains(filter));
    }

    private boolean isOverHeaderUi(float uiMouseX, float uiMouseY) {
        return uiMouseX >= panelX && uiMouseX < panelX + panelWidth
            && uiMouseY >= panelY && uiMouseY < panelY + theme.headerHeight();
    }

    private boolean isOverCloseButton(float uiMouseX, float uiMouseY) {
        return OverlayTopBar.isOverClose(UiBounds.of(panelX, panelY, panelWidth, Math.max(theme.headerHeight(), panelHeight)),
            theme.headerHeight(), uiMouseX, uiMouseY);
    }

    private DihWindowLayout clampToViewport(DihWindowLayout bounds) {
        DirectViewport viewport = surface.viewport();
        int margin = 4;
        int viewportW = Math.round(viewport.uiWidth());
        int viewportH = Math.round(viewport.uiHeight());
        int availableW = Math.max(1, viewportW - margin * 2);
        int availableH = Math.max(theme.headerHeight(), viewportH - margin * 2);
        int width = Math.max(Math.min(getMinWidth(), availableW), Math.min(bounds.width, availableW));
        int minHeight = bounds.collapsed ? theme.headerHeight() : getMinHeight();
        int height = Math.max(Math.min(minHeight, availableH), Math.min(bounds.height, availableH));
        int renderedHeight = bounds.collapsed ? theme.headerHeight() : height;
        int x = Math.max(margin, Math.min(bounds.x, Math.max(margin, viewportW - margin - width)));
        int y = Math.max(margin, Math.min(bounds.y, Math.max(margin, viewportH - margin - renderedHeight)));
        return new DihWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
    }

    private boolean uiContains(float x, float y, float width, float height, float mouseX, float mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

        private int panelMinimumWidth() {
        return 258;
    }

    private int rowHeight() {
        return 18;
    }

    private int maxVisibleRows() {
        return 6;
    }

    private int panelPadding() {
        return 6;
    }

    private int windowContentGap() {
        return 4;
    }

    private int searchFieldHeight() {
        return 16;
    }

    private int actionRowGap() {
        return 4;
    }

    private int actionButtonHeight() {
        return 16;
    }

    private int headerControlSize() {
        return 12;
    }

    private int headerArrowWidth() {
        return 10;
    }

    private int headerArrowGap() {
        return 3;
    }

    private int rowButtonHeight() {
        return 14;
    }

    private int deleteButtonWidth() {
        return 16;
    }

    private int rowButtonGap() {
        return 4;
    }

    private int rowButtonPadding() {
        return 2;
    }

    private int rowButtonTopInset() {
        return Math.max(1, (rowHeight() - rowButtonHeight()) / 2);
    }

    private int rowTextInset() {
        return 8;
    }

    private int sectionHeaderInset() {
        return 4;
    }

    private int rowDotSize() {
        return 4;
    }

    private enum ItemType {
        SECTION_HEADER,
        LOCAL_MACRO,
        REMOTE_MACRO,
        METEOR_MACRO
    }

    private enum RowAction {
        RUN_TOGGLE,
        EDIT,
        COPY,
        DELETE,
        IMPORT_REMOTE,
        IMPORT_METEOR
    }

    private static final class DisplayItem {
        private ItemType type;
        private String label;
        private int color;
        private DihMacro macro;
        private String remoteSource;
        private boolean remoteMeteor;

        private static DisplayItem section(String label, int color) {
            DisplayItem item = new DisplayItem();
            item.type = ItemType.SECTION_HEADER;
            item.label = label;
            item.color = color;
            return item;
        }

        private static DisplayItem localMacro(DihMacro macro) {
            DisplayItem item = new DisplayItem();
            item.type = ItemType.LOCAL_MACRO;
            item.macro = macro;
            return item;
        }

        private static DisplayItem remoteMacro(String label, String source, boolean remoteMeteor) {
            DisplayItem item = new DisplayItem();
            item.type = ItemType.REMOTE_MACRO;
            item.label = label;
            item.remoteSource = source;
            item.remoteMeteor = remoteMeteor;
            return item;
        }

        private static DisplayItem meteorMacro(DihMacro macro) {
            DisplayItem item = new DisplayItem();
            item.type = ItemType.METEOR_MACRO;
            item.macro = macro;
            return item;
        }
    }

    private static final class ClickRegion {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final DisplayItem item;
        private final RowAction action;

        private ClickRegion(int x, int y, int width, int height, DisplayItem item, RowAction action) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.item = item;
            this.action = action;
        }

        private boolean contains(float mouseX, float mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private static final class RowButton {
        private final String label;
        private final DirectUiButton.Variant variant;
        private final int width;
        private final RowAction action;
        private final net.minecraft.resources.Identifier icon;

        private RowButton(String label, DirectUiButton.Variant variant, int width, RowAction action) {
            this(label, variant, width, action, null);
        }

        private RowButton(String label, DirectUiButton.Variant variant, int width, RowAction action, net.minecraft.resources.Identifier icon) {
            this.label = label;
            this.variant = variant;
            this.width = width;
            this.action = action;
            this.icon = icon;
        }
    }
}
