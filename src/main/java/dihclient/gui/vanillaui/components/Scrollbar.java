package dihclient.gui.vanillaui.components;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContext;
import dihclient.gui.vanillaui.UiRenderer;

public final class Scrollbar {
    private Scrollbar() {
    }

    public static Metrics metrics(UiBounds track, int contentHeight, int viewHeight, int scroll) {
        int max = Math.max(0, contentHeight - Math.max(0, viewHeight));
        if (max <= 0 || track.height() <= 0) return new Metrics(track, UiBounds.of(track.x(), track.y(), track.width(), track.height()), 0);
        int thumbH = Math.max(12, (int) Math.round(track.height() * (viewHeight / (double) Math.max(viewHeight, contentHeight))));
        thumbH = Math.min(track.height(), thumbH);
        int thumbY = track.y() + (int) Math.round((track.height() - thumbH) * (scroll / (double) max));
        return new Metrics(track, UiBounds.of(track.x(), thumbY, track.width(), thumbH), max);
    }

    public static void render(UiContext context, Metrics metrics, boolean dragging) {
        render(context, metrics, false, dragging);
    }

    private static final int THUMB_GRAY = 0xB09BA0A8;
    private static final int THUMB_GRAY_ACTIVE = 0xFFC9CDD4;

    public static void render(UiContext context, Metrics metrics, boolean hovered, boolean dragging) {
        if (metrics == null || metrics.maxScroll() <= 0) return;

        UiBounds track = metrics.track();
        UiBounds thumb = metrics.thumb();
        boolean active = hovered || dragging;
        int width = active ? Math.max(3, thumb.width() - 2) : Math.max(2, thumb.width() - 3);
        int x = thumb.x() + (thumb.width() - width) / 2;
        UiRenderer.rounded(context.graphics(), UiBounds.of(x, track.y(), width, track.height()), width / 2, 0x14FFFFFF);
        UiRenderer.rounded(context.graphics(), UiBounds.of(x, thumb.y(), width, thumb.height()), width / 2,
            active ? THUMB_GRAY_ACTIVE : THUMB_GRAY);
    }

    public static int scrollFromMouse(Metrics metrics, int mouseY, int grabOffset) {
        if (metrics == null || metrics.maxScroll() <= 0) return 0;
        int available = Math.max(1, metrics.track().height() - metrics.thumb().height());
        int y = Math.max(0, Math.min(available, mouseY - grabOffset - metrics.track().y()));
        return (int) Math.round(metrics.maxScroll() * (y / (double) available));
    }

    public record Metrics(UiBounds track, UiBounds thumb, int maxScroll) {
        public boolean overTrack(int x, int y) {
            return track.contains(x, y);
        }

        public boolean overThumb(int x, int y) {
            return thumb.contains(x, y);
        }
    }
}
