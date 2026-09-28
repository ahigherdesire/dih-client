package dihclient.util;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContext;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.components.CompactOverlayWindow;
import dihclient.gui.vanillaui.components.OverlayTopBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class DihWindow {
    protected static final int HEADER_HEIGHT = 16;
    protected static final int RESIZE_HANDLE = 10;

    public static int sharedHeaderHeight() {
        return HEADER_HEIGHT;
    }

    protected DihWindowLayout clampToScreen(IDihOverlay overlay) {
        return clampToScreen(overlay, overlay.getBounds());
    }

    protected DihWindowLayout clampToScreen(IDihOverlay overlay, DihWindowLayout bounds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || bounds == null) return bounds;

        return clampToScreenSize(bounds, overlay.getMinWidth(), overlay.getMinHeight(),
            DihUiScale.getVirtualScreenWidth(), DihUiScale.getVirtualScreenHeight());
    }

    static DihWindowLayout clampToScreenSize(DihWindowLayout bounds, int overlayMinWidth, int overlayMinHeight,
                                                int screenWidth, int screenHeight) {
        if (bounds == null) return null;

        if (screenWidth <= 0 || screenHeight <= 0) return bounds;

        int safeMargin = 4;
        int availableWidth = Math.max(1, screenWidth - safeMargin * 2);
        int availableHeight = Math.max(HEADER_HEIGHT, screenHeight - safeMargin * 2);
        int minWidth = Math.min(overlayMinWidth, availableWidth);
        int minHeight = Math.min(overlayMinHeight, availableHeight);

        int width = Math.max(minWidth, Math.min(bounds.width, availableWidth));
        int height = Math.max(minHeight, Math.min(bounds.height, availableHeight));
        int renderedHeight = bounds.collapsed ? HEADER_HEIGHT : height;
        int x = Math.max(safeMargin, Math.min(bounds.x, Math.max(safeMargin, screenWidth - safeMargin - width)));
        int y = Math.max(safeMargin, Math.min(bounds.y, Math.max(safeMargin, screenHeight - safeMargin - renderedHeight)));

        return new DihWindowLayout(x, y, width, height, bounds.visible, bounds.collapsed);
    }

    protected void renderWindowFrame(GuiGraphicsExtractor context, int mouseX, int mouseY, DihWindowLayout bounds, String title, boolean collapsed, boolean activeDrag) {
        boolean active = activeDrag || isWindowActive();
        int frameHeight = getRenderedFrameHeight(bounds, collapsed);
        UiContext ui = UiContexts.overlay(context, Minecraft.getInstance().font, mouseX, mouseY);
        CompactOverlayWindow.render(ui, UiBounds.of(bounds.x, bounds.y, bounds.width, frameHeight), HEADER_HEIGHT, title,
            collapsed, active, mouseY >= bounds.y && mouseY < bounds.y + HEADER_HEIGHT);
        if (this instanceof IDihOverlay overlay && overlay.supportsPinning()) {
            renderPinButton(ui, topBarPinBounds(bounds), overlay.isPinned());
        }
    }

    /** A small push-pin: filled while pinned, outlined when not. */
    private static void renderPinButton(UiContext ui, UiBounds pin, boolean pinned) {
        var graphics = ui.graphics();
        var colors = ui.theme().colors();
        int ink = dihclient.gui.vanillaui.components.TopBar.onAccent(colors.accent);
        boolean hovered = pin.contains(ui.mouseX(), ui.mouseY());
        UiRenderer.rect(graphics, pin, (colors.accent & 0x00FFFFFF) | 0xF0000000);
        if (pinned || hovered) {
            UiRenderer.disc(graphics, pin.x() + pin.width() / 2.0F, pin.y() + pin.height() / 2.0F, 6.0F,
                pinned ? (ink & 0x00FFFFFF) | 0x50000000 : colors.accentSoft);
        }
        int color = pinned || hovered ? ink : (ink & 0x00FFFFFF) | 0xB0000000;
        int cx = pin.x() + pin.width() / 2;
        int top = pin.y() + (pin.height() - 9) / 2;
        UiRenderer.rect(graphics, UiBounds.of(cx - 3, top, 6, 2), color);
        if (pinned) {
            UiRenderer.rect(graphics, UiBounds.of(cx - 2, top + 2, 4, 3), color);
        } else {
            UiRenderer.rect(graphics, UiBounds.of(cx - 2, top + 2, 1, 3), color);
            UiRenderer.rect(graphics, UiBounds.of(cx + 1, top + 2, 1, 3), color);
        }
        UiRenderer.rect(graphics, UiBounds.of(cx - 4, top + 5, 8, 1), color);
        UiRenderer.rect(graphics, UiBounds.of(cx - 1, top + 6, 1, 3), color);
    }

    protected boolean beginWindowBodyClip(GuiGraphicsExtractor context, DihWindowLayout bounds, boolean collapsed) {
        int frameHeight = getRenderedFrameHeight(bounds, collapsed);
        if (collapsed || frameHeight <= HEADER_HEIGHT + 1) return false;
        return CompactOverlayWindow.beginBodyClip(context, UiBounds.of(bounds.x, bounds.y, bounds.width, frameHeight), HEADER_HEIGHT, collapsed);
    }

    protected void endWindowBodyClip(GuiGraphicsExtractor context, boolean clipped) {
        CompactOverlayWindow.endBodyClip(context, clipped);
    }

    protected void renderWindowInactiveOverlay(GuiGraphicsExtractor context, DihWindowLayout bounds, boolean collapsed, boolean activeDrag) {
        int frameHeight = getRenderedFrameHeight(bounds, collapsed);
        if (activeDrag || isWindowActive()) return;
        UiRenderer.rect(context, UiBounds.of(bounds.x + 1, bounds.y + 1, Math.max(0, bounds.width - 2), Math.max(0, frameHeight - 2)), 0x24000000);
    }

    protected int alignViewportHeight(int innerHeight, int rowStep) {
        int safeInnerHeight = Math.max(0, innerHeight);
        int safeRowStep = Math.max(1, rowStep);
        if (safeInnerHeight == 0 || safeRowStep <= 1) {
            return safeInnerHeight;
        }

        int aligned = (safeInnerHeight / safeRowStep) * safeRowStep;
        return aligned > 0 ? aligned : Math.min(safeInnerHeight, safeRowStep);
    }

    protected int quantizeScrollOffset(int offset, int stepSize, int maxScroll) {
        int clampedMax = Math.max(0, maxScroll);
        int clampedOffset = Math.max(0, Math.min(offset, clampedMax));
        int safeStepSize = Math.max(1, stepSize);
        if (safeStepSize <= 1) {
            return clampedOffset;
        }

        int quantized = (clampedOffset / safeStepSize) * safeStepSize;
        if (quantized > clampedMax) {
            quantized = (clampedMax / safeStepSize) * safeStepSize;
        }
        return Math.max(0, Math.min(quantized, clampedMax));
    }

    protected int getRenderedFrameHeight(DihWindowLayout bounds, boolean collapsed) {
        if (bounds == null) return HEADER_HEIGHT;
        return collapsed ? HEADER_HEIGHT : Math.max(HEADER_HEIGHT, bounds.height);
    }

    protected boolean isOverCloseButton(double mouseX, double mouseY, DihWindowLayout bounds) {
        return topBarCloseBounds(bounds).contains((int) mouseX, (int) mouseY);
    }

    protected boolean isOverCollapseButton(double mouseX, double mouseY, DihWindowLayout bounds) {
        if (shouldUseSharedHeaderClickCollapse()) return false;
        return topBarCollapseBounds(bounds).contains((int) mouseX, (int) mouseY);
    }

    public boolean isOverPinButton(double mouseX, double mouseY, DihWindowLayout bounds) {
        if (!(this instanceof IDihOverlay overlay) || !overlay.supportsPinning() || bounds == null) return false;
        return topBarPinBounds(bounds).contains((int) mouseX, (int) mouseY);
    }

    protected boolean isOverWindowControl(double mouseX, double mouseY, DihWindowLayout bounds) {
        return isOverCloseButton(mouseX, mouseY, bounds) || isOverCollapseButton(mouseX, mouseY, bounds)
            || isOverPinButton(mouseX, mouseY, bounds);
    }

    private boolean isWindowActive() {
        if (!(this instanceof IDihOverlay overlay)) return true;
        return DihOverlayManager.get().isFocusedOverlay(overlay) || DihOverlayManager.get().isTopOverlay(overlay);
    }

    private UiBounds topBarBounds(DihWindowLayout bounds) {
        return UiBounds.of(bounds.x, bounds.y, bounds.width, HEADER_HEIGHT);
    }

    private UiBounds topBarCloseBounds(DihWindowLayout bounds) {
        return OverlayTopBar.closeButton(topBarBounds(bounds), HEADER_HEIGHT);
    }

    private UiBounds topBarPinBounds(DihWindowLayout bounds) {
        UiBounds close = topBarCloseBounds(bounds);
        return UiBounds.of(close.x() - close.width() - 1, close.y(), close.width(), close.height());
    }

    private UiBounds topBarCollapseBounds(DihWindowLayout bounds) {
        return OverlayTopBar.collapseButton(topBarBounds(bounds), HEADER_HEIGHT);
    }

    private boolean shouldUseSharedHeaderClickCollapse() {
        return this instanceof IDihOverlay overlay && overlay.usesSharedHeaderClickCollapse();
    }
}
