package dihclient.util;

import net.minecraft.client.Minecraft;

public final class DihGuiClipboardUtil {
    private static final Minecraft MC = Minecraft.getInstance();

    private DihGuiClipboardUtil() {
    }

    public static void copyGuiTitleJson() {
        if (MC.gui.screen() == null || MC.keyboardHandler == null) {
            DihNotifications.error("Copy failed: no screen.");
            return;
        }

        String title = MC.gui.screen().getTitle() == null ? "" : MC.gui.screen().getTitle().getString();
        MC.keyboardHandler.setClipboard(title);
        DihNotifications.copied("GUI title copied.");
    }
}
