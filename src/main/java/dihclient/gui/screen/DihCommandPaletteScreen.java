package dihclient.gui.screen;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContext;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.components.TextField;
import dihclient.palette.DihCommandPalette;
import dihclient.palette.PaletteIndex;
import dihclient.palette.PaletteIndex.Entry;
import dihclient.util.DihConfig;
import dihclient.util.DihUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Ctrl+K: a search box over modules, settings, {@code .} and {@code #} commands and macros. Arrow keys move, Enter
 * runs, Esc goes back to where it was opened.
 */
public final class DihCommandPaletteScreen extends Screen {
    private static final int MAX_RESULTS = 8;
    private static final int WIDTH = 360;
    private static final int FIELD_H = 20;
    private static final int ROW_H = 18;
    private static final int PAD = 6;
    private static final int KIND_W = 58;

    private final Screen parent;
    private final PaletteIndex index;
    private String query = "";
    private List<Entry> results = List.of();
    private int selected;

    public DihCommandPaletteScreen(Screen parent) {
        super(Component.literal("Command Palette"));
        this.parent = parent;
        this.index = DihCommandPalette.buildIndex();
        refresh();
    }

    public List<Entry> results() {
        return results;
    }

    public String query() {
        return query;
    }

    /** Replaces the search text (used by tests). */
    public void setQuery(String text) {
        query = text == null ? "" : text;
        refresh();
    }

    private void refresh() {
        results = index.search(query, MAX_RESULTS, DihConfig.getGlobal().paletteRecent);
        selected = results.isEmpty() ? -1 : 0;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    /** Runs the highlighted entry (used by Enter, clicks and tests). */
    public void runSelected() {
        if (selected < 0 || selected >= results.size()) return;
        Screen next = DihCommandPalette.run(results.get(selected), parent);
        this.minecraft.gui.setScreen(next);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean ctrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (key == GLFW.GLFW_KEY_ESCAPE || DihCommandPalette.isShortcut(key, event.modifiers())) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            runSelected();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_TAB) {
            if (!results.isEmpty()) selected = (selected + 1) % results.size();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            if (!results.isEmpty()) selected = (selected - 1 + results.size()) % results.size();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!query.isEmpty()) setQuery(ctrl ? "" : query.substring(0, query.length() - 1));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int codepoint = event.codepoint();
        if (codepoint < 32 || codepoint == 127) return false;
        if (query.length() < 80) setQuery(query + new String(Character.toChars(codepoint)));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        int row = rowAt(DihUiScale.toVirtual(event.x()), DihUiScale.toVirtual(event.y()));
        if (row >= 0) {
            selected = row;
            runSelected();
            return true;
        }
        if (!panelBounds().contains((int) DihUiScale.toVirtual(event.x()), (int) DihUiScale.toVirtual(event.y()))) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        int row = rowAt(DihUiScale.toVirtual(mouseX), DihUiScale.toVirtual(mouseY));
        if (row >= 0) selected = row;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft.level == null) this.minecraft.gameRenderer.panorama().extractRenderState(graphics, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBackground(graphics, mouseX, mouseY, delta);
        int uiMouseX = DihUiScale.toVirtualInt(mouseX);
        int uiMouseY = DihUiScale.toVirtualInt(mouseY);
        DihUiScale.pushOverlayScale(graphics);
        try {
            UiContext ctx = UiContexts.overlay(graphics, this.font, uiMouseX, uiMouseY);
            var colors = ctx.theme().colors();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, DihUiScale.getVirtualScreenWidth(), DihUiScale.getVirtualScreenHeight()), 0x88000000);

            UiBounds panel = panelBounds();
            UiRenderer.softRect(graphics, UiBounds.of(panel.x() - 1, panel.y(), panel.width() + 2, panel.height() + 2), 2, 0x50000000);
            UiRenderer.softRect(graphics, panel, 2, colors.windowStrong);
            UiRenderer.rect(graphics, UiBounds.of(panel.x(), panel.y(), panel.width(), 2), colors.accent);

            UiBounds field = fieldBounds();
            TextField.render(ctx, field, query, "Search modules, settings, commands, macros...", true, query.length());

            int y = field.bottom() + 4;
            if (results.isEmpty()) {
                ctx.text().draw(graphics, "Nothing matches \"" + query + "\"", field.x() + 4, y + 5, colors.muted);
            }
            for (int i = 0; i < results.size(); i++) {
                Entry entry = results.get(i);
                UiBounds row = UiBounds.of(field.x(), y + i * ROW_H, field.width(), ROW_H);
                if (i == selected) UiRenderer.rect(graphics, row, colors.accentSoft);
                int textY = ctx.text().centeredY(row);
                ctx.text().drawEllipsized(graphics, entry.kind().label(), row.x() + 4, textY, KIND_W - 8,
                    i == selected ? colors.text : colors.muted);
                int titleX = row.x() + KIND_W;
                int titleW = ctx.text().width(entry.title());
                int titleMax = Math.max(40, (row.width() - KIND_W) * 3 / 5);
                ctx.text().drawEllipsized(graphics, entry.title(), titleX, textY, titleMax, colors.text);
                int descX = titleX + Math.min(titleW, titleMax) + 8;
                int descW = row.right() - 4 - descX;
                if (descW > 24 && !entry.description().isEmpty()) {
                    ctx.text().drawEllipsized(graphics, entry.description(), descX, textY, descW, colors.muted);
                }
            }

            String hint = selected >= 0 && selected < results.size() ? DihCommandPalette.hint(results.get(selected)) : "";
            String keys = "↑↓ move · Enter run · Esc close";
            int hintY = panel.bottom() - PAD - 9;
            ctx.text().drawEllipsized(graphics, hint, field.x() + 2, hintY, field.width() - ctx.text().width(keys) - 12, colors.accent);
            ctx.text().draw(graphics, keys, field.right() - ctx.text().width(keys) - 2, hintY, colors.muted);
        } finally {
            DihUiScale.popOverlayScale(graphics);
        }
    }

    private UiBounds panelBounds() {
        int screenW = DihUiScale.getVirtualScreenWidth();
        int screenH = DihUiScale.getVirtualScreenHeight();
        int w = Math.min(WIDTH, Math.max(160, screenW - 24));
        int h = PAD + FIELD_H + 4 + MAX_RESULTS * ROW_H + 6 + 9 + PAD;
        int x = (screenW - w) / 2;
        int y = Math.max(8, Math.min(screenH / 6, screenH - h - 8));
        return UiBounds.of(x, y, w, h);
    }

    private UiBounds fieldBounds() {
        UiBounds panel = panelBounds();
        return UiBounds.of(panel.x() + PAD, panel.y() + PAD, panel.width() - PAD * 2, FIELD_H);
    }

    private int rowAt(double x, double y) {
        UiBounds field = fieldBounds();
        int top = field.bottom() + 4;
        if (x < field.x() || x >= field.right() || y < top) return -1;
        int row = (int) ((y - top) / ROW_H);
        return row >= 0 && row < results.size() ? row : -1;
    }
}
