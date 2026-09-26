package dihclient.gui.vanillaui.components;

import com.mojang.blaze3d.platform.InputConstants;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContext;
import dihclient.util.DihBindUtil;

public final class CompactKeybindButton {
    public static final int WIDTH = 34;
    public static final int HEIGHT = 11;

    private CompactKeybindButton() {
    }

    public static UiBounds atRowEnd(UiBounds row, int rightInset, int verticalInset) {
        int height = Math.max(1, row.height() - verticalInset * 2);
        return UiBounds.of(row.right() - WIDTH - rightInset, row.y() + verticalInset, WIDTH, height);
    }

    public static String label(int bindCode, boolean capturing) {
        if (capturing) return "Key";
        return bindCode == -1 ? "Bind" : DihBindUtil.getBindName(bindCode);
    }

    public static int keyOrClear(int keyCode) {
        return keyCode == InputConstants.KEY_ESCAPE
            || keyCode == InputConstants.KEY_BACKSPACE
            || keyCode == InputConstants.KEY_DELETE
            ? -1
            : keyCode;
    }

    public static void render(UiContext context, UiBounds bounds, int bindCode, boolean capturing, boolean hovered) {
        render(context, bounds, bindCode, capturing, hovered, true);
    }

    /**
     * Borderless key chip. An unbound key stays invisible until its row is hovered, so a list of
     * modules isn't a wall of "Bind" boxes.
     */
    public static void render(UiContext context, UiBounds bounds, int bindCode, boolean capturing, boolean hovered, boolean rowHovered) {
        boolean unbound = bindCode == -1 && !capturing;
        if (unbound && !rowHovered && !hovered) return;
        var colors = context.theme().colors();
        int fill = capturing ? (colors.accent & 0x00FFFFFF) | 0x80000000
            : hovered ? 0x3AFFFFFF : 0x1CFFFFFF;
        dihclient.gui.vanillaui.UiRenderer.softRect(context.graphics(), bounds, 1, fill);
        String text = capturing ? "..." : unbound ? "+ key" : label(bindCode, false);
        int color = unbound && !hovered ? colors.muted : colors.text;
        context.text().drawCentered(context.graphics(), text, bounds, color);
    }
}
