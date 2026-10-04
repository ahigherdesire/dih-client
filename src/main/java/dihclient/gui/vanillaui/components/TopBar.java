package dihclient.gui.vanillaui.components;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContext;
import dihclient.gui.vanillaui.UiRenderer;

public final class TopBar {
    private TopBar() {
    }

    public static UiBounds collapseButton(UiBounds bounds) {
        int size = Math.min(12, Math.max(8, bounds.height() - 3));
        return UiBounds.of(bounds.x() + 2, bounds.y() + Math.max(1, (bounds.height() - size) / 2), size, size);
    }

    public static UiBounds closeButton(UiBounds bounds) {
        int size = Math.min(14, Math.max(10, bounds.height() - 1));
        return UiBounds.of(bounds.right() - size - 2, bounds.y() + Math.max(1, (bounds.height() - size) / 2), size, size);
    }

    public static void render(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean close, boolean hovered) {
        render(context, bounds, title, collapsed, true, close, hovered);
    }

    public static void render(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean collapse, boolean close, boolean hovered) {
        render(context, bounds, title, collapsed, collapse, close, hovered, 4, 4);
    }

    public static void render(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean collapse, boolean close, boolean hovered,
                              int titleLeftInset, int titleRightInset) {
        render(context, bounds, title, collapsed, collapse, close, hovered, titleLeftInset, titleRightInset, false);
    }

    /**
     * A quiet header: a slightly lifted band with the title in body text, the accent only on the chevron (or a dot
     * when there is none), and a hairline under it. {@code wholeWindow} rounds the bottom corners too, for a
     * collapsed window that is nothing but its header.
     */
    public static void render(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean collapse, boolean close, boolean hovered,
                              int titleLeftInset, int titleRightInset, boolean wholeWindow) {
        var graphics = context.graphics();
        var colors = context.theme().colors();
        int fill = hovered ? colors.headerHover : colors.header;
        int radius = CompactWindow.RADIUS - 1;
        if (wholeWindow) UiRenderer.rounded(graphics, bounds, radius, fill);
        else {
            UiRenderer.roundedTop(graphics, bounds, radius, fill);
            UiRenderer.horizontalEdge(graphics, bounds.x(), bounds.bottom(), bounds.width(), colors.hairline);
        }
        UiBounds collapseBounds = collapseButton(bounds);
        if (collapse) UiRenderer.chevron(graphics, collapseBounds.inset(2), !collapsed, colors.accent);
        UiBounds closeBounds = close ? closeButton(bounds) : null;
        int titleLeft;
        if (collapse) titleLeft = collapseBounds.right() + 2;
        else {
            int dotX = bounds.x() + Math.max(4, titleLeftInset + 1);
            UiRenderer.disc(graphics, dotX + 1.5F, bounds.y() + bounds.height() / 2.0F, 1.5F, colors.accent);
            titleLeft = dotX + 7;
        }
        int titleRight = close ? closeBounds.x() - 3 : bounds.right() - Math.max(0, titleRightInset);
        context.text().drawEllipsized(graphics, title, titleLeft, context.text().centeredY(bounds), Math.max(1, titleRight - titleLeft), colors.text);
        if (close) {
            boolean closeHovered = closeBounds.contains(context.mouseX(), context.mouseY());
            int icon = 7;
            int ix = closeBounds.x() + (closeBounds.width() - icon) / 2;
            int iy = closeBounds.y() + (closeBounds.height() - icon) / 2;
            if (closeHovered) {
                UiRenderer.disc(graphics, closeBounds.x() + closeBounds.width() / 2.0F,
                    closeBounds.y() + closeBounds.height() / 2.0F, 6.0F, (colors.bad & 0x00FFFFFF) | 0x66000000);
            }
            dihclient.util.DihUiIcons.blit(graphics, dihclient.util.DihUiIcons.X,
                ix, iy, icon, icon, closeHovered ? 0xFFFFFFFF : colors.muted);
        }
    }

    /** Readable text colour on top of {@code background}: near-black on light accents, white otherwise. */
    public static int onAccent(int background) {
        int r = (background >> 16) & 0xFF, g = (background >> 8) & 0xFF, b = background & 0xFF;
        double luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
        return luma > 0.62 ? 0xFF0B0C10 : 0xFFFFFFFF;
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
            | (Math.round(ar + (br - ar) * t) << 16)
            | (Math.round(ag + (bg - ag) * t) << 8)
            | Math.round(ab + (bb - ab) * t);
    }
}
