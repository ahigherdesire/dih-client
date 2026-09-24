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
        var graphics = context.graphics();
        var colors = context.theme().colors();
        int fill = hovered ? mix(colors.accent, 0xFFFFFFFF, 0.12f) : colors.accent;
        UiRenderer.rect(graphics, bounds, (fill & 0x00FFFFFF) | 0xF0000000);
        int ink = onAccent(colors.accent);
        UiBounds collapseBounds = collapseButton(bounds);
        if (collapse) UiRenderer.chevron(graphics, collapseBounds.inset(1), !collapsed, ink);
        UiBounds closeBounds = close ? closeButton(bounds) : null;
        int titleLeft = collapse ? collapseBounds.right() + 3 : bounds.x() + Math.max(0, titleLeftInset);
        int titleRight = close ? closeBounds.x() - 3 : bounds.right() - Math.max(0, titleRightInset);
        context.text().drawEllipsized(graphics, title, titleLeft, context.text().centeredY(bounds), Math.max(1, titleRight - titleLeft), ink);
        if (close) {

            boolean closeHovered = closeBounds.contains(context.mouseX(), context.mouseY());
            int icon = 8;
            int ix = closeBounds.x() + (closeBounds.width() - icon) / 2;
            int iy = closeBounds.y() + (closeBounds.height() - icon) / 2;
            if (closeHovered) {
                UiRenderer.disc(graphics, closeBounds.x() + closeBounds.width() / 2.0F,
                    closeBounds.y() + closeBounds.height() / 2.0F, 6.5F, colors.accentSoft);
            }
            dihclient.util.DihUiIcons.blit(graphics, dihclient.util.DihUiIcons.X,
                ix, iy, icon, icon, closeHovered ? ink : (ink & 0x00FFFFFF) | 0xB0000000);
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
