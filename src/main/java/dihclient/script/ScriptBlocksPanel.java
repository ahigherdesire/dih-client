package dihclient.script;

import com.mojang.blaze3d.platform.InputConstants;
import dihclient.gui.vanillaui.*;
import dihclient.gui.vanillaui.components.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static dihclient.script.ScriptBlocksModel.*;

/** Compact stacked editor: clickable insertion points make nesting possible without dragging. */
final class ScriptBlocksPanel {
    private record Hit(UiBounds bounds, Runnable action) { }
    private final Font font;
    private final Consumer<String> changed;
    private final List<Hit> hits = new ArrayList<>();
    private final ScriptEditorModel field = new ScriptEditorModel(false);
    private ScriptBlocksModel model = new ScriptBlocksModel();
    private Block selected;
    private List<Block> selectedList;
    private List<Block> insertionList;
    private int insertionIndex;
    private boolean cleanup, choosing, recipes, active, fieldFocused;
    private int scroll, chooserScroll, contentHeight;
    private UiBounds bounds = UiBounds.of(0, 0, 0, 0);
    private UiBounds viewport = bounds, fieldBounds = bounds;
    private String initialField = "", lastPublished = "", error;

    ScriptBlocksPanel(Font font, Consumer<String> changed) { this.font = font; this.changed = changed; }

    void load(String source) {
        model = ScriptBlocksModel.parse(source);
        lastPublished = source;
        selected = null; selectedList = null;
        choosing = recipes = fieldFocused = false;
        error = null; scroll = chooserScroll = 0;
    }
    boolean focused() { return active; }
    void blur() { active = fieldFocused = false; }
    String error() { return error; }

    /** Invalid drafts stay visible and block save/run/mode changes until corrected. */
    boolean commit() {
        if (choosing || selected == null || field.text().equals(initialField)) return true;
        String previous = selected.value;
        selected.value = field.text();
        try {
            String source = model.source();
            initialField = field.text(); error = null;
            lastPublished = source;
            changed.accept(source);
            return true;
        } catch (IllegalArgumentException e) {
            selected.value = previous; error = e.getMessage(); return false;
        }
    }

    private List<Block> root() { return cleanup ? model.cleanup : model.main; }
    private void publish() {
        try {
            String source = model.source();
            error = null; lastPublished = source; changed.accept(source);
        } catch (IllegalArgumentException e) {
            model = ScriptBlocksModel.parse(lastPublished);
            selected = null; selectedList = null; fieldFocused = false;
            error = e.getMessage();
        }
    }
    private void select(Block b, List<Block> parent) {
        if (!commit()) return;
        selected = b; selectedList = parent; fieldFocused = false;
        field.setText(b.value); field.selectAll(); initialField = field.text();
    }

    void render(UiContext ctx, UiBounds area, int mx, int my) {
        bounds = area; hits.clear();
        var g = ctx.graphics(); var c = ctx.theme().colors();
        UiRenderer.rect(g, area, c.field);
        int x = area.x() + 4, y = area.y() + 3;
        int w = Math.max(1, area.width() - 8);
        if (choosing) {
            button(ctx, "Back", x, y, 40, this::cancelChooser, mx, my);
            text(ctx, recipes ? "Choose a starter recipe" : "Choose a block", x + 46, y + 3, w - 48, c.text);
            y += 18;
            if (!recipes) {
                fieldBounds = UiBounds.of(x, y, w, 15);
                renderField(ctx, fieldBounds, "Search actions or conditions", mx, my);
                y += 20;
            }
            viewport = UiBounds.of(x, y, w, Math.max(1, area.bottom() - y - 3));
            renderChoices(ctx, mx, my);
            return;
        }

        int tabW = Math.max(1, (w - 3) / 2);
        button(ctx, "When started", x, y, tabW, () -> changeSection(false), mx, my);
        button(ctx, "When finished / stopped", x + tabW + 3, y, tabW, () -> changeSection(true), mx, my);
        UiRenderer.rect(g, UiBounds.of(cleanup ? x + tabW + 3 : x, y + 13, tabW, 1), c.accent);
        y += 18;
        button(ctx, "+ Step", x, y, 44, () -> choose(selectedList == null ? root() : selectedList,
            selected == null ? root().size() : selectedList.indexOf(selected) + 1), mx, my);
        button(ctx, "Up", x + 47, y, 24, () -> move(-1), mx, my);
        button(ctx, "Down", x + 74, y, 34, () -> move(1), mx, my);
        button(ctx, "Copy", x + 111, y, 31, this::duplicate, mx, my);
        button(ctx, "Remove", x + 145, y, 43, this::remove, mx, my);
        y += 18;
        button(ctx, "Recipes", x, y, 49, () -> {
            if (!commit()) return;
            choosing = recipes = true; chooserScroll = 0; fieldFocused = false;
        }, mx, my);
        text(ctx, "Steps run from top to bottom", x + 55, y + 3, w - 55, c.muted);
        y += 18;
        int inspectorH = selected != null && selected.kind.input != Input.NONE ? 39 : 16;
        viewport = UiBounds.of(x, y, w, Math.max(1, area.bottom() - y - inspectorH - 3));
        contentHeight = treeHeight(root());
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewport.height())));
        UiScissorStack.global().push(g, viewport);
        try { renderTree(ctx, root(), 0, y - scroll, mx, my); }
        finally { UiScissorStack.global().pop(g); }
        if (contentHeight > viewport.height()) {
            int thumb = Math.max(8, viewport.height() * viewport.height() / contentHeight);
            int sy = viewport.y() + (viewport.height() - thumb) * scroll / Math.max(1, contentHeight - viewport.height());
            UiRenderer.rect(g, UiBounds.of(viewport.right() - 2, sy, 2, thumb), c.borderSoft);
        }
        int fy = area.bottom() - inspectorH;
        if (selected != null && selected.kind.input != Input.NONE) {
            text(ctx, selected.kind.field, x, fy, w, c.muted);
            fieldBounds = UiBounds.of(x, fy + 11, Math.max(1, w - 42), 15);
            renderField(ctx, fieldBounds, "Enter a value", mx, my);
            button(ctx, "Apply", area.right() - 41, fy + 11, 37, this::commit, mx, my);
        }
        String hint = error != null ? error : selected == null ? "Choose a step to edit. + adds steps inside a group." : "Ctrl+Z undoes edits. Code is available above.";
        text(ctx, hint, x, area.bottom() - 10, w, error != null ? c.bad : c.muted);
    }

    private void changeSection(boolean value) {
        if (!commit()) return;
        cleanup = value; selected = null; selectedList = null; scroll = 0; fieldFocused = false;
    }
    private void cancelChooser() {
        choosing = recipes = false; fieldFocused = false;
        field.setText(selected == null ? "" : selected.value);
        initialField = field.text();
    }
    private void choose(List<Block> list, int index) {
        if (!commit()) return;
        insertionList = list; insertionIndex = index;
        choosing = true; recipes = false; chooserScroll = 0;
        field.setText(""); initialField = ""; fieldFocused = true;
    }
    private int treeHeight(List<Block> blocks) {
        int h = 18;
        for (Block b : blocks) h += 23 + (b.kind.container() ? treeHeight(b.children) + 4 : 0);
        return h;
    }
    private int renderTree(UiContext ctx, List<Block> blocks, int depth, int y, int mx, int my) {
        var c = ctx.theme().colors(); var g = ctx.graphics();
        int indent = Math.min(depth * 12, Math.max(0, viewport.width() - 95));
        int x = viewport.x() + indent;
        int w = viewport.width() - indent - 4;
        for (Block b : blocks) {
            UiBounds row = UiBounds.of(x, y, w, 21);
            if (row.bottom() > viewport.y() && row.y() < viewport.bottom()) {
                int color = b.kind.container() ? 0xFFE2B86B : b.kind.category.equals("Timing") ? 0xFFB8A1E3 : 0xFF7EBFCB;
                UiRenderer.rect(g, row, b == selected ? c.accentSoft : c.rowHover);
                UiRenderer.rect(g, UiBounds.of(x, y, 2, 21), color);
                text(ctx, b.kind.label, x + 6, y + 2, w - 10, c.text);
                text(ctx, b.kind.input == Input.NONE ? b.kind.category : b.value.replace('\n', ' '), x + 6, y + 12, w - 10, c.muted);
                hits.add(new Hit(intersection(row, viewport), () -> select(b, blocks)));
            }
            y += 23;
            if (b.kind.container()) {
                int start = y;
                y = renderTree(ctx, b.children, depth + 1, y, mx, my);
                int top = Math.max(start, viewport.y()), bottom = Math.min(y, viewport.bottom());
                if (bottom > top) UiRenderer.rect(g, UiBounds.of(x + 3, top, 1, bottom - top), c.borderSoft);
                y += 4;
            }
        }
        UiBounds add = UiBounds.of(x + 4, y, Math.max(1, w - 4), 15);
        if (add.y() >= viewport.y() && add.bottom() <= viewport.bottom())
            button(ctx, depth == 0 ? "+ Add step" : "+ Add step inside", add.x(), add.y(), add.width(), () -> choose(blocks, blocks.size()), mx, my);
        return y + 18;
    }

    private static UiBounds intersection(UiBounds a, UiBounds b) {
        int y = Math.max(a.y(), b.y());
        return UiBounds.of(a.x(), y, a.width(), Math.max(0, Math.min(a.bottom(), b.bottom()) - y));
    }
    private void renderChoices(UiContext ctx, int mx, int my) {
        List<Kind> kinds = java.util.Arrays.stream(Kind.values()).filter(k -> (k.label + " " + k.category).toLowerCase(java.util.Locale.ROOT)
            .contains(field.text().toLowerCase(java.util.Locale.ROOT))).toList();
        int count = recipes ? ScriptBlocksModel.recipes().size() : kinds.size();
        int height = count * 27;
        chooserScroll = Math.max(0, Math.min(chooserScroll, Math.max(0, height - viewport.height())));
        UiScissorStack.global().push(ctx.graphics(), viewport);
        try {
            for (int i = 0; i < count; i++) {
                int index = i, y = viewport.y() + i * 27 - chooserScroll;
                UiBounds row = UiBounds.of(viewport.x(), y, viewport.width(), 25);
                if (row.bottom() <= viewport.y() || row.y() >= viewport.bottom()) continue;
                UiRenderer.rect(ctx.graphics(), row, row.contains(mx, my) ? ctx.theme().colors().accentSoft : ctx.theme().colors().rowHover);
                String title = recipes ? ScriptBlocksModel.recipes().get(i).name() : kinds.get(i).label;
                String description = recipes ? "Insert these steps (keeps existing steps)" : kinds.get(i).category + (kinds.get(i).container() ? " - contains other steps" : "");
                text(ctx, title, row.x() + 4, y + 3, row.width() - 8, ctx.theme().colors().text);
                text(ctx, description, row.x() + 4, y + 14, row.width() - 8, ctx.theme().colors().muted);
                hits.add(new Hit(intersection(row, viewport), () -> {
                    if (recipes) {
                        ScriptBlocksModel recipe = ScriptBlocksModel.parse(ScriptBlocksModel.recipes().get(index).source());
                        model.main.addAll(recipe.main); model.cleanup.addAll(recipe.cleanup);
                        cleanup = false; selected = null; selectedList = null;
                    } else {
                        Kind k = kinds.get(index);
                        Block b = new Block(k, k.initial);
                        if (k == Kind.FOREVER) b.children.add(new Block(Kind.WAIT, "1"));
                        insertionList.add(insertionIndex, b);
                        selected = b; selectedList = insertionList;
                    }
                    choosing = recipes = false; fieldFocused = false;
                    field.setText(selected == null ? "" : selected.value); field.selectAll(); initialField = field.text();
                    publish();
                }));
            }
            if (count == 0) text(ctx, "No matching blocks. Try a different word.", viewport.x() + 3, viewport.y() + 4, viewport.width() - 6, ctx.theme().colors().muted);
        } finally { UiScissorStack.global().pop(ctx.graphics()); }
    }

    private void move(int delta) {
        if (!commit() || selected == null) return;
        int at = selectedList.indexOf(selected), to = at + delta;
        if (to < 0 || to >= selectedList.size()) return;
        java.util.Collections.swap(selectedList, at, to); publish();
    }
    private void duplicate() {
        if (!commit() || selected == null) return;
        selectedList.add(selectedList.indexOf(selected) + 1, selected.copy()); publish();
    }
    private void remove() {
        if (!commit() || selected == null) return;
        selectedList.remove(selected); selected = null; selectedList = null; fieldFocused = false; publish();
    }

    private void button(UiContext ctx, String label, int x, int y, int w, Runnable action, int mx, int my) {
        UiBounds b = UiBounds.of(x, y, w, 14);
        Button.render(ctx, b, label, Button.Tone.SECONDARY, b.contains(mx, my), false);
        hits.add(new Hit(b, action));
    }
    private void text(UiContext ctx, String text, int x, int y, int width, int color) {
        ctx.graphics().text(font, fit(text, width), x, y, color, false);
    }
    private String fit(String text, int width) {
        if (width <= 0) return "";
        if (font.width(text) <= width) return text;
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + "…") > width) end--;
        return text.substring(0, end) + "…";
    }
    private void renderField(UiContext ctx, UiBounds b, String placeholder, int mx, int my) {
        var c = ctx.theme().colors();
        UiRenderer.rect(ctx.graphics(), b, fieldFocused ? c.fieldFocused : c.field);
        UiRenderer.outline(ctx.graphics(), b, fieldFocused ? c.accent : c.borderSoft);
        String value = field.text().replace('\n', ' ').replace('\r', ' ');
        int offset = Math.max(0, font.width(value.substring(0, field.cursor())) - b.width() + 10);
        UiScissorStack.global().push(ctx.graphics(), b.inset(2));
        try {
            if (fieldFocused && field.hasSelection()) {
                int start = font.width(value.substring(0, field.selectionStart()));
                int end = font.width(value.substring(0, field.selectionEnd()));
                UiRenderer.rect(ctx.graphics(), UiBounds.of(b.x() + 3 + start - offset, b.y() + 2, Math.max(1, end - start), 10), c.accentSoft);
            }
            ctx.graphics().text(font, value.isEmpty() && !fieldFocused ? placeholder : value, b.x() + 3 - offset, b.y() + 3, value.isEmpty() ? c.muted : c.text, false);
            if (fieldFocused && System.currentTimeMillis() / 500 % 2 == 0)
                UiRenderer.rect(ctx.graphics(), UiBounds.of(b.x() + 3 + font.width(value.substring(0, field.cursor())) - offset, b.y() + 2, 1, 10), c.text);
        } finally { UiScissorStack.global().pop(ctx.graphics()); }
        hits.add(new Hit(b, () -> { fieldFocused = true; field.selectAll(); }));
    }

    boolean click(double mx, double my, int button) {
        if (!bounds.contains((int) mx, (int) my)) return false;
        active = true;
        if (button != 0) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.bounds.contains((int) mx, (int) my)) { h.action.run(); return true; }
        }
        if (commit()) fieldFocused = false;
        return true;
    }
    boolean scroll(double mx, double my, double amount) {
        if (!bounds.contains((int) mx, (int) my)) return false;
        if (choosing) chooserScroll -= (int) Math.signum(amount) * 45;
        else scroll -= (int) Math.signum(amount) * 36;
        return true;
    }
    boolean typing(char chr, int mods) {
        if (!active) return false;
        if (fieldFocused && chr >= 32 && chr != 127 && (mods & (InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER)) == 0) {
            field.insert(String.valueOf(chr)); chooserScroll = 0;
        }
        return true;
    }
    boolean key(int key, int mods) {
        if (!active) return false;
        boolean ctrl = (mods & (InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER)) != 0;
        boolean shift = (mods & InputConstants.MOD_SHIFT) != 0;
        if (key == InputConstants.KEY_ESCAPE) {
            if (choosing) cancelChooser();
            else if (fieldFocused) {
                field.setText(selected == null ? "" : selected.value);
                initialField = field.text(); fieldFocused = false; error = null;
            }
            else blur();
            return true;
        }
        if (!fieldFocused) return true;
        var keyboard = Minecraft.getInstance().keyboardHandler;
        if (ctrl) switch (key) {
            case InputConstants.KEY_A -> field.selectAll();
            case InputConstants.KEY_C -> keyboard.setClipboard(field.selectedText());
            case InputConstants.KEY_X -> keyboard.setClipboard(field.cut());
            case InputConstants.KEY_V -> field.insert(keyboard.getClipboard().replace('\n', ' ').replace('\r', ' '));
            case InputConstants.KEY_Z -> { if (shift) field.redo(); else field.undo(); }
            case InputConstants.KEY_Y -> field.redo();
            default -> { }
        } else switch (key) {
            case InputConstants.KEY_BACKSPACE -> field.backspace(false);
            case InputConstants.KEY_DELETE -> field.delete(false);
            case InputConstants.KEY_LEFT -> field.left(shift, false);
            case InputConstants.KEY_RIGHT -> field.right(shift, false);
            case InputConstants.KEY_HOME -> field.documentStart(shift);
            case InputConstants.KEY_END -> field.documentEnd(shift);
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_TAB -> { if (commit()) fieldFocused = false; }
            default -> { }
        }
        return true;
    }
    boolean editingField() { return fieldFocused; }
}
