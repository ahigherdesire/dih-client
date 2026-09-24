package dihclient.gui.vanillaui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class UiRenderer {
    private UiRenderer() {
    }

    public static void rect(GuiGraphicsExtractor graphics, UiBounds bounds, int color) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), color);
    }

    public static void horizontalEdge(GuiGraphicsExtractor graphics, int x, int y, int width, int color) {
        if (width <= 0) return;
        rect(graphics, UiBounds.of(x, y, width, 1), color);
    }

    public static void verticalEdge(GuiGraphicsExtractor graphics, int x, int y, int height, int color) {
        if (height <= 0) return;
        rect(graphics, UiBounds.of(x, y, 1, height), color);
    }

    public static void outline(GuiGraphicsExtractor graphics, UiBounds bounds, int color) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        horizontalEdge(graphics, bounds.x(), bounds.y(), bounds.width(), color);
        if (bounds.height() > 1) {
            horizontalEdge(graphics, bounds.x(), bounds.bottom() - 1, bounds.width(), color);
        }
        if (bounds.height() > 2) {
            verticalEdge(graphics, bounds.x(), bounds.y() + 1, bounds.height() - 2, color);
            if (bounds.width() > 1) {
                verticalEdge(graphics, bounds.right() - 1, bounds.y() + 1, bounds.height() - 2, color);
            }
        }
    }

    /**
     * A filled rectangle with small cut corners (radius 1 or 2), built from non-overlapping strips so
     * translucent colours blend once. Reads as softly rounded at any GUI scale.
     */
    public static void softRect(GuiGraphicsExtractor graphics, UiBounds bounds, int radius, int color) {
        int x = bounds.x(), y = bounds.y(), w = bounds.width(), h = bounds.height();
        int r = Math.max(0, Math.min(Math.min(radius, 2), Math.min(w, h) / 2));
        if (w <= 0 || h <= 0) return;
        if (r == 0) {
            rect(graphics, bounds, color);
            return;
        }
        rect(graphics, UiBounds.of(x + r, y, w - 2 * r, h), color);
        for (int i = 0; i < r; i++) {
            int inset = r - i;
            rect(graphics, UiBounds.of(x + i, y + inset, 1, h - 2 * inset), color);
            rect(graphics, UiBounds.of(x + w - 1 - i, y + inset, 1, h - 2 * inset), color);
        }
    }

    public static void frame(GuiGraphicsExtractor graphics, UiBounds bounds, int fill, int border) {
        rect(graphics, bounds, fill);
        outline(graphics, bounds, border);
    }

    public static void window(GuiGraphicsExtractor graphics, UiBounds bounds, int headerHeight,
                              int fill, int headerFill, int bodyShade, int border, int accent, float alpha) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        int headerBottom = Math.min(bounds.bottom(), bounds.y() + Math.max(1, headerHeight));
        rect(graphics, bounds, applyAlpha(fill, alpha));
        rect(graphics, UiBounds.of(bounds.x() + 1, bounds.y() + 1, Math.max(0, bounds.width() - 2), Math.max(0, headerBottom - bounds.y() - 2)),
            applyAlpha(headerFill, alpha));
        if (headerBottom < bounds.bottom() - 1) {
            rect(graphics, UiBounds.of(bounds.x() + 1, headerBottom, Math.max(0, bounds.width() - 2), Math.max(0, bounds.bottom() - headerBottom - 1)),
                applyAlpha(bodyShade, alpha));
        }
        outline(graphics, bounds, applyAlpha(border, alpha));
        if (headerBottom > bounds.y()) {
            horizontalEdge(graphics, bounds.x(), headerBottom - 1, bounds.width(), applyAlpha(accent, alpha));
        }
    }

    public static void popup(GuiGraphicsExtractor graphics, UiBounds bounds, int fill, int border, int accent) {
        frame(graphics, bounds, fill, border);
        horizontalEdge(graphics, bounds.x(), bounds.y(), bounds.width(), accent);
    }

    public static int applyAlpha(int color, float alpha) {
        int sourceAlpha = (color >>> 24) & 0xFF;
        int resolvedAlpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * Math.max(0.0f, Math.min(1.0f, alpha)))));
        return (resolvedAlpha << 24) | (color & 0x00FFFFFF);
    }

    public static void cross(GuiGraphicsExtractor graphics, UiBounds bounds, int color) {
        dihclient.util.DihUiIcons.blit(graphics, dihclient.util.DihUiIcons.X,
            bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
    }

    public static void roundRect(GuiGraphicsExtractor graphics, UiBounds bounds, int radius, int color) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        if (radius <= 0) {
            rect(graphics, bounds, color);
            return;
        }
        dihclient.util.DihUiIcons.blitSliced(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
    }

    public static void disc(GuiGraphicsExtractor graphics, float cx, float cy, float r, int color) {
        int size = Math.max(1, Math.round(r * 2.0F));
        dihclient.util.DihUiIcons.blit(graphics, dihclient.util.DihUiIcons.DOT,
            Math.round(cx - r), Math.round(cy - r), size, size, color);
    }

    public static void roundFrame(GuiGraphicsExtractor graphics, UiBounds bounds, int radius, int fill, int border) {
        roundRect(graphics, bounds, radius, border);
        roundRect(graphics, bounds.inset(1), Math.max(0, radius - 1), fill);
    }

    public static void roundRectTop(GuiGraphicsExtractor graphics, UiBounds bounds, int radius, int color) {
        roundRect(graphics, bounds, radius, color);
        int half = bounds.height() / 2;
        rect(graphics, UiBounds.of(bounds.x(), bounds.y() + half, bounds.width(), bounds.height() - half), color);
    }

    public static void chevron(GuiGraphicsExtractor graphics, UiBounds bounds, boolean open, int color) {
        dihclient.util.DihUiIcons.blit(graphics,
            open ? dihclient.util.DihUiIcons.CHEVRON_DOWN : dihclient.util.DihUiIcons.CHEVRON_RIGHT,
            bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
    }

    public static void chevronUp(GuiGraphicsExtractor graphics, UiBounds bounds, int color) {
        dihclient.util.DihUiIcons.blit(graphics, dihclient.util.DihUiIcons.CHEVRON_UP,
            bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
    }

    public static void play(GuiGraphicsExtractor graphics, int x, int y, int h, int color) {
        for (int i = 0; i < h; i++) {
            int w = Math.min(i + 1, h - i);
            graphics.fill(x, y + i, x + w, y + i + 1, color);
        }
    }

    public static void check(GuiGraphicsExtractor graphics, UiBounds bounds, int color) {
        dihclient.util.DihUiIcons.blit(graphics, dihclient.util.DihUiIcons.CHECK,
            bounds.x(), bounds.y(), bounds.width(), bounds.height(), color);
    }
}
