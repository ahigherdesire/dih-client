package dihclient.gui.screen;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.components.SectionPanel;
import dihclient.util.DihNotifications;
import dihclient.util.DihUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

public abstract class DihScreen extends Screen {
    protected DihScreen(Component title) {
        super(title);
    }

    protected static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    protected static MouseButtonEvent virtualEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(DihUiScale.toVirtual(event.x()), DihUiScale.toVirtual(event.y()), new MouseButtonInfo(event.button(), event.modifiers()));
    }

    protected int screenWidth() {
        int width = DihUiScale.getVirtualScreenWidth();
        return width <= 0 ? this.width : width;
    }

    protected int screenHeight() {
        int height = DihUiScale.getVirtualScreenHeight();
        return height <= 0 ? this.height : height;
    }

    protected void drawPanel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill) {
        SectionPanel.renderBody(UiContexts.overlay(graphics, font, -10000, -10000), UiBounds.of(x, y, w, h), fill);
    }

    protected void toast(String message, int accentColor) {
        if (message == null || message.isBlank() || this.minecraft == null) return;
        this.minecraft.execute(() -> DihNotifications.show(message, accentColor));
    }
}
