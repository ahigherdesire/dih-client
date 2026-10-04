package dihclient.gui.vanillaui.components;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContext;
import dihclient.gui.vanillaui.UiRenderer;

import java.util.Locale;

public final class CollapsibleSection {
    private CollapsibleSection() {
    }

    public static void renderHeader(UiContext context, UiBounds bounds, String title, boolean collapsed, boolean hovered) {
        var colors = context.theme().colors();
        if (hovered) UiRenderer.rounded(context.graphics(), bounds, Button.RADIUS, 0x10FFFFFF);
        UiRenderer.chevron(context.graphics(),
            UiBounds.of(bounds.x() + 3, bounds.y() + Math.max(0, (bounds.height() - 7) / 2), 7, 7), !collapsed, colors.accent);
        String label = safe(title).toUpperCase(Locale.ROOT);
        int textX = bounds.x() + 14;
        int maxW = Math.max(1, bounds.width() - 20);
        context.text().drawFitted(context.graphics(), label, textX, context.text().centeredY(bounds), maxW, colors.muted);
        int ruleX = textX + Math.min(maxW, context.text().width(label)) + 6;
        if (ruleX < bounds.right() - 4) {
            UiRenderer.horizontalEdge(context.graphics(), ruleX, bounds.y() + bounds.height() / 2, bounds.right() - 4 - ruleX, colors.hairline);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
