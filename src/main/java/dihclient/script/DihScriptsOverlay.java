package dihclient.script;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiColors;
import dihclient.gui.vanillaui.UiContext;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.UiScissorStack;
import dihclient.gui.vanillaui.components.Button;
import dihclient.script.lang.Library;
import dihclient.script.lang.ScriptException;
import dihclient.util.DihOverlayBase;
import dihclient.util.DihWindowLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;
import dihclient.util.DihKeys;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Scripts window: a list of scripts on the left, a code editor on the right, with
 * Run/Stop, Save and a built-in function reference. Opened from the module menu's "Scripts"
 * button or {@code .scripts edit}.
 */
public final class DihScriptsOverlay extends DihOverlayBase {
    public static final String TEMPLATE = """
        // New DihScript. Run it with .scripts run <name>, stop it with .scripts stop <name>.
        // Press ? in the editor toolbar for the list of built-in functions.

        function main() {
            print("Hello from " + playerName());
        }

        // Optional: runs when the script ends for any reason.
        function onStop() {
        }
        """;

    private static final String OVERLAY_ID = "dih-scripts";
    private static final int PAD = 4;
    private static final int SIDEBAR_W = 100;
    private static final int ROW_H = 13;
    private static final int TOOLBAR_H = 15;
    private static final int STATUS_H = 11;
    private static final int LINE_H = 10;
    private static final int BUTTON_H = 13;

    private static final int C_KEYWORD = 0xFFC792EA;
    private static final int C_STRING = 0xFFC3E88D;
    private static final int C_NUMBER = 0xFFF78C6C;
    private static final int C_COMMENT = 0xFF6F7A86;
    private static final int C_FUNCTION = 0xFF82AAFF;
    private static final int C_BUILTIN = 0xFF89DDFF;
    private static final int C_CONSTANT = 0xFFFFCB6B;
    private static final int C_EDITOR_BG = 0xF0101014;
    private static final int C_GUTTER_BG = 0xF018181E;
    private static final int C_CURRENT_LINE = 0x22FFFFFF;
    private static final int C_SELECTION = 0x664A78C2;
    private static final int C_ERROR_LINE = 0x55FF3B3B;

    private static DihScriptsOverlay shared;

    private final Font font;
    private final ScriptEditorModel editor = new ScriptEditorModel();
    private final List<Hit> hits = new ArrayList<>();

    private String selected;
    private String savedText = "";
    private boolean editorFocused;
    private boolean helpOpen;
    private int topLine;
    private int scrollX;
    private int listScroll;
    private int helpScroll;
    private boolean ensureCursorVisible;
    private boolean draggingWindow;
    private boolean draggingSelection;
    private double dragOffsetX;
    private double dragOffsetY;
    private long lastClickAt;
    private int lastClickOffset = -1;

    private boolean naming;
    private final StringBuilder newName = new StringBuilder();
    private long deleteArmedUntil;

    private String flash;
    private int flashColor;
    private long flashUntil;

    private long checkedRevision = -1;
    private long editedAt;
    private List<ScriptException> problems = List.of();

    private UiBounds editorBounds = UiBounds.of(0, 0, 0, 0);
    private UiBounds listBounds = UiBounds.of(0, 0, 0, 0);
    private UiBounds helpBounds = UiBounds.of(0, 0, 0, 0);
    private int gutterW;
    private long listRevision = -1;
    private List<String> names = List.of();

    private enum HitType { SCRIPT, NEW, DELETE, RUN, SAVE, HELP, HELP_ENTRY, NAME_FIELD }

    private record Hit(HitType type, UiBounds bounds, String value) { }

    private DihScriptsOverlay(Font font) {
        super(OVERLAY_ID, 460, 270);
        this.font = font;
        this.panelX = 60;
        this.panelY = 30;
        restoreLayout();
        visible = false;
        List<String> all = ScriptManager.names();
        if (!all.isEmpty()) select(all.contains("gold_miner") ? "gold_miner" : all.get(0));
    }

    public static DihScriptsOverlay shared() {
        if (shared == null) shared = new DihScriptsOverlay(Minecraft.getInstance().font);
        return shared;
    }

    /** Opens {@code name} in the editor (saving the current script first). */
    public void select(String name) {
        if (name == null || name.equals(selected)) return;
        saveIfDirty();
        try {
            String text = ScriptManager.read(name);
            selected = ScriptManager.resolve(name);
            editor.setText(text);
            savedText = editor.text();
            topLine = 0;
            scrollX = 0;
            checkedRevision = -1;
            problems = List.of();
        } catch (IOException e) {
            flash("Could not open: " + e.getMessage(), 0xFFFF5555);
        }
    }

    // ---------------------------------------------------------------- IDihOverlay basics

    @Override public int getMinWidth() { return 320; }

    @Override public int getMinHeight() { return 170; }

    @Override public boolean hasTextFieldFocused() { return visible && (editorFocused || naming); }

    @Override public boolean wantsKeyboardCapture() { return hasTextFieldFocused(); }

    @Override
    public void clearTextFieldFocus() {
        editorFocused = false;
        naming = false;
    }

    @Override
    public void setVisible(boolean v) {
        if (!v) saveIfDirty();
        super.setVisible(v);
    }

    @Override
    public boolean isOverResizeHandle(double mx, double my) {
        if (!visible || collapsed) return false;
        return mx >= panelX + panelWidth - RESIZE_HANDLE && mx <= panelX + panelWidth
            && my >= panelY + panelHeight - RESIZE_HANDLE && my <= panelY + panelHeight;
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, float delta) {
        if (!visible) return;
        recordMouse(mx, my);
        DihWindowLayout bounds = clampToScreen(this);
        panelX = bounds.x;
        panelY = bounds.y;
        panelWidth = bounds.width;
        panelHeight = bounds.height;
        String title = "Scripts" + (ScriptManager.runningNames().isEmpty() ? "" : "  · " + ScriptManager.runningNames().size() + " running");
        renderWindowFrame(g, mx, my, bounds, title, collapsed, draggingWindow);
        hits.clear();
        if (collapsed) return;

        refreshNames();
        runLiveCheck();
        UiContext ctx = UiContexts.overlay(g, font, mx, my);
        boolean clipped = beginWindowBodyClip(g, bounds, collapsed);
        try {
            int bx = panelX + PAD;
            int by = panelY + HEADER_HEIGHT + PAD;
            int bw = panelWidth - PAD * 2;
            int bh = panelHeight - HEADER_HEIGHT - PAD * 2;
            renderSidebar(ctx, UiBounds.of(bx, by, SIDEBAR_W, bh), mx, my);
            renderMain(ctx, UiBounds.of(bx + SIDEBAR_W + PAD, by, bw - SIDEBAR_W - PAD, bh), mx, my);
        } finally {
            endWindowBodyClip(g, clipped);
            renderWindowInactiveOverlay(g, bounds, collapsed, draggingWindow);
        }
    }

    private void refreshNames() {
        long rev = ScriptManager.revision();
        if (rev != listRevision) {
            listRevision = rev;
            names = ScriptManager.names();
        }
    }

    private void renderSidebar(UiContext ctx, UiBounds area, int mx, int my) {
        UiColors c = ctx.theme().colors();
        GuiGraphicsExtractor g = ctx.graphics();
        UiRenderer.rect(g, area, c.field);
        g.text(font, "SCRIPTS", area.x() + 4, area.y() + 3, c.muted, false);

        int footerH = BUTTON_H + 4;
        listBounds = UiBounds.of(area.x(), area.y() + 13, area.width(), area.height() - 13 - footerH);
        int maxScroll = Math.max(0, names.size() * ROW_H - listBounds.height());
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));
        UiScissorStack.global().push(g, listBounds);
        try {
            int y = listBounds.y() - listScroll;
            for (String name : names) {
                UiBounds row = UiBounds.of(listBounds.x(), y, listBounds.width(), ROW_H);
                boolean isSelected = name.equals(selected);
                boolean hover = row.contains(mx, my) && listBounds.contains(mx, my);
                if (isSelected) UiRenderer.rect(g, row, c.accentSoft);
                else if (hover) UiRenderer.rect(g, row, c.rowHover);
                boolean running = ScriptManager.isRunning(name);
                UiRenderer.rect(g, UiBounds.of(row.x() + 4, row.y() + 5, 3, 3), running ? c.success : c.disabled);
                String label = trim(name, row.width() - 14);
                g.text(font, label, row.x() + 11, row.y() + 3, isSelected ? c.text : c.muted, false);
                hits.add(new Hit(HitType.SCRIPT, row, name));
                y += ROW_H;
            }
            if (names.isEmpty()) g.text(font, "No scripts yet", listBounds.x() + 4, listBounds.y() + 3, c.disabled, false);
        } finally {
            UiScissorStack.global().pop(g);
        }

        int fy = area.bottom() - BUTTON_H - 2;
        if (naming) {
            UiBounds field = UiBounds.of(area.x() + 2, fy, area.width() - 4, BUTTON_H);
            UiRenderer.rect(g, field, c.fieldFocused);
            UiRenderer.outline(g, field, c.accent);
            String shown = newName.isEmpty() ? "" : newName.toString();
            if (shown.isEmpty()) g.text(font, "name, then Enter", field.x() + 3, field.y() + 3, c.disabled, false);
            else g.text(font, trim(shown, field.width() - 8), field.x() + 3, field.y() + 3, c.text, false);
            if (blink()) {
                int cx = field.x() + 3 + font.width(trim(shown, field.width() - 8));
                UiRenderer.rect(g, UiBounds.of(cx, field.y() + 2, 1, 9), c.text);
            }
            hits.add(new Hit(HitType.NAME_FIELD, field, null));
        } else {
            int half = (area.width() - 6) / 2;
            UiBounds newBtn = UiBounds.of(area.x() + 2, fy, half, BUTTON_H);
            UiBounds delBtn = UiBounds.of(area.x() + 4 + half, fy, area.width() - 6 - half, BUTTON_H);
            boolean armed = System.currentTimeMillis() < deleteArmedUntil;
            Button.render(ctx, newBtn, "+ New", Button.Tone.NORMAL, newBtn.contains(mx, my), false);
            Button.render(ctx, delBtn, armed ? "Sure?" : "Delete", Button.Tone.DANGER, delBtn.contains(mx, my), armed);
            hits.add(new Hit(HitType.NEW, newBtn, null));
            if (selected != null) hits.add(new Hit(HitType.DELETE, delBtn, null));
        }
    }

    private void renderMain(UiContext ctx, UiBounds area, int mx, int my) {
        UiColors c = ctx.theme().colors();
        GuiGraphicsExtractor g = ctx.graphics();

        // toolbar
        int ty = area.y();
        boolean running = selected != null && ScriptManager.isRunning(selected);
        int x = area.right();
        UiBounds help = UiBounds.of(x - 16, ty, 16, BUTTON_H);
        UiBounds save = UiBounds.of(help.x() - 3 - 38, ty, 38, BUTTON_H);
        UiBounds run = UiBounds.of(save.x() - 3 - 40, ty, 40, BUTTON_H);
        if (selected != null) {
            Button.render(ctx, run, running ? "Stop" : "Run", running ? Button.Tone.DANGER : Button.Tone.SUCCESS, run.contains(mx, my), running);
            Button.render(ctx, save, "Save", Button.Tone.NORMAL, save.contains(mx, my), false);
            hits.add(new Hit(HitType.RUN, run, null));
            hits.add(new Hit(HitType.SAVE, save, null));
        }
        Button.render(ctx, help, "?", Button.Tone.SECONDARY, help.contains(mx, my), helpOpen);
        hits.add(new Hit(HitType.HELP, help, null));
        String heading = selected == null ? "No script selected" : selected + ScriptManager.EXTENSION + (isDirty() ? " •" : "");
        g.text(font, trim(heading, run.x() - area.x() - 6), area.x() + 1, ty + 3, selected == null ? c.disabled : c.text, false);

        // status line
        UiBounds status = UiBounds.of(area.x(), area.bottom() - STATUS_H + 1, area.width(), STATUS_H - 1);
        renderStatus(g, c, status);

        UiBounds body = UiBounds.of(area.x(), ty + TOOLBAR_H + 2, area.width(), area.height() - TOOLBAR_H - 2 - STATUS_H - 1);
        if (helpOpen) renderHelp(ctx, body, mx, my);
        else renderEditor(ctx, body, mx, my);
    }

    private void renderStatus(GuiGraphicsExtractor g, UiColors c, UiBounds status) {
        String text;
        int color;
        String runStatus = selected == null ? null : ScriptManager.status(selected);
        if (flash != null && System.currentTimeMillis() < flashUntil) {
            text = flash;
            color = flashColor;
        } else if (!problems.isEmpty()) {
            ScriptException first = problems.get(0);
            text = "✖ " + first.describe() + (problems.size() > 1 ? "  (+" + (problems.size() - 1) + " more)" : "");
            color = c.bad;
        } else if (runStatus != null) {
            text = "● " + runStatus;
            color = c.success;
        } else if (selected != null) {
            int line = editor.lineOf(editor.cursor()) + 1;
            text = "✔ No problems   ·   Ln " + line + ", Col " + (editor.columnOf(editor.cursor()) + 1) + "   ·   .scripts run " + selected;
            color = c.muted;
        } else {
            text = "Create a script with + New";
            color = c.muted;
        }
        g.text(font, trim(text, status.width() - 2), status.x() + 1, status.y() + 2, color, false);
    }

    private void renderEditor(UiContext ctx, UiBounds area, int mx, int my) {
        UiColors c = ctx.theme().colors();
        GuiGraphicsExtractor g = ctx.graphics();
        editorBounds = area;
        UiRenderer.rect(g, area, C_EDITOR_BG);
        UiRenderer.outline(g, area, editorFocused ? c.accent : c.borderSoft);
        if (selected == null) {
            g.text(font, "Select a script on the left, or press + New.", area.x() + 6, area.y() + 6, c.disabled, false);
            return;
        }

        List<String> lines = editor.lines();
        gutterW = font.width(String.valueOf(Math.max(99, lines.size()))) + 8;
        int textX = area.x() + gutterW + 3;
        int textW = area.width() - gutterW - 5;
        int visibleLines = Math.max(1, (area.height() - 4) / LINE_H);

        int cursorLine = editor.lineOf(editor.cursor());
        int cursorCol = editor.columnOf(editor.cursor());
        String cursorLineText = lines.get(Math.min(cursorLine, lines.size() - 1));
        int cursorPx = font.width(cursorLineText.substring(0, Math.min(cursorCol, cursorLineText.length())));

        if (draggingSelection) autoScrollWhileDragging(mx, my, visibleLines, textX);
        if (ensureCursorVisible) {
            if (cursorLine < topLine) topLine = cursorLine;
            if (cursorLine >= topLine + visibleLines) topLine = cursorLine - visibleLines + 1;
            if (cursorPx < scrollX) scrollX = Math.max(0, cursorPx - 20);
            if (cursorPx > scrollX + textW - 4) scrollX = cursorPx - textW + 24;
            ensureCursorVisible = false;
        }
        topLine = Math.max(0, Math.min(topLine, Math.max(0, lines.size() - visibleLines)));

        UiRenderer.rect(g, UiBounds.of(area.x() + 1, area.y() + 1, gutterW, area.height() - 2), C_GUTTER_BG);
        java.util.Set<Integer> errorLines = new java.util.HashSet<>();
        for (ScriptException p : problems) if (p.line() > 0) errorLines.add(p.line() - 1);

        List<List<ScriptHighlighter.Span>> spans = highlighted(lines);
        int selStart = editor.selectionStart();
        int selEnd = editor.selectionEnd();

        UiScissorStack.global().push(g, UiBounds.of(area.x() + 1, area.y() + 1, area.width() - 2, area.height() - 2));
        try {
            int y = area.y() + 3;
            for (int i = topLine; i < lines.size() && i < topLine + visibleLines; i++, y += LINE_H) {
                String line = lines.get(i);
                if (errorLines.contains(i)) UiRenderer.rect(g, UiBounds.of(area.x() + 1, y - 1, gutterW, LINE_H), C_ERROR_LINE);
                if (i == cursorLine && editorFocused && !editor.hasSelection()) {
                    UiRenderer.rect(g, UiBounds.of(textX - 2, y - 1, textW + 3, LINE_H), C_CURRENT_LINE);
                }
                String num = String.valueOf(i + 1);
                g.text(font, num, area.x() + gutterW - 3 - font.width(num), y, i == cursorLine ? c.text : c.disabled, false);

                UiScissorStack.global().push(g, UiBounds.of(textX - 1, area.y() + 1, textW + 2, area.height() - 2));
                try {
                    int lineStart = editor.lineStart(i);
                    int lineEnd = lineStart + line.length();
                    if (selEnd > lineStart && selStart <= lineEnd && editor.hasSelection()) {
                        int a = Math.max(selStart, lineStart) - lineStart;
                        int b = Math.min(selEnd, lineEnd) - lineStart;
                        int x1 = textX - scrollX + font.width(line.substring(0, a));
                        int x2 = textX - scrollX + font.width(line.substring(0, b)) + (selEnd > lineEnd ? 3 : 0);
                        UiRenderer.rect(g, UiBounds.of(x1, y - 1, Math.max(1, x2 - x1), LINE_H), C_SELECTION);
                    }
                    int px = textX - scrollX;
                    for (ScriptHighlighter.Span s : spans.get(i)) {
                        String seg = line.substring(s.start(), s.end());
                        int w = font.width(seg);
                        if (px + w >= textX - 2 && px <= textX + textW) g.text(font, seg, px, y, colorFor(s.kind(), c), false);
                        px += w;
                    }
                    if (i == cursorLine && editorFocused && blink()) {
                        UiRenderer.rect(g, UiBounds.of(textX - scrollX + cursorPx, y - 1, 1, LINE_H), c.text);
                    }
                } finally {
                    UiScissorStack.global().pop(g);
                }
            }
        } finally {
            UiScissorStack.global().pop(g);
        }

        if (lines.size() > visibleLines) {
            int trackH = area.height() - 4;
            int thumbH = Math.max(10, trackH * visibleLines / lines.size());
            int thumbY = area.y() + 2 + (trackH - thumbH) * topLine / Math.max(1, lines.size() - visibleLines);
            UiRenderer.rect(g, UiBounds.of(area.right() - 3, thumbY, 2, thumbH), c.borderSoft);
        }
    }

    private List<String> highlightLines;
    private List<List<ScriptHighlighter.Span>> highlightCache;
    private long highlightRevision = -1;

    private List<List<ScriptHighlighter.Span>> highlighted(List<String> lines) {
        if (highlightRevision != editor.revision() || highlightCache == null || highlightLines == null || highlightLines.size() != lines.size()) {
            Library lib = ScriptManager.library();
            highlightCache = ScriptHighlighter.highlight(lines, name -> lib.get(name) != null);
            highlightLines = lines;
            highlightRevision = editor.revision();
        }
        return highlightCache;
    }

    private int colorFor(ScriptHighlighter.Kind kind, UiColors c) {
        return switch (kind) {
            case KEYWORD -> C_KEYWORD;
            case STRING -> C_STRING;
            case NUMBER, LITERAL -> C_NUMBER;
            case COMMENT -> C_COMMENT;
            case FUNCTION -> C_FUNCTION;
            case BUILTIN -> C_BUILTIN;
            case CONSTANT -> C_CONSTANT;
            case TEXT -> c.text;
        };
    }

    private void renderHelp(UiContext ctx, UiBounds area, int mx, int my) {
        UiColors c = ctx.theme().colors();
        GuiGraphicsExtractor g = ctx.graphics();
        helpBounds = area;
        UiRenderer.rect(g, area, C_EDITOR_BG);
        UiRenderer.outline(g, area, c.borderSoft);
        Library lib = ScriptManager.library();

        List<Object> rows = new ArrayList<>();
        rows.add("Language:  let x = 1;  const NAME = \"v\";  if (a && !b) { } else { }");
        rows.add("while (cond) { }   repeat (3) { }   for (let item in list) { }   break; continue;");
        rows.add("function name(a, b) { return a + b; }   main() runs first, onStop() runs last.");
        for (String category : lib.categories()) {
            rows.add(category.toUpperCase());
            rows.addAll(lib.byCategory(category));
        }
        int contentH = rows.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - area.height() + 4);
        helpScroll = Math.max(0, Math.min(helpScroll, maxScroll));
        UiScissorStack.global().push(g, area.inset(1));
        try {
            int y = area.y() + 3 - helpScroll;
            for (Object row : rows) {
                if (y + ROW_H >= area.y() && y <= area.bottom()) {
                    if (row instanceof Library.Entry e) {
                        UiBounds b = UiBounds.of(area.x() + 1, y - 1, area.width() - 2, ROW_H);
                        if (b.contains(mx, my) && area.contains(mx, my)) UiRenderer.rect(g, b, c.rowHover);
                        int sigW = font.width(e.signature());
                        g.text(font, e.signature(), area.x() + 8, y + 1, C_BUILTIN, false);
                        g.text(font, trim(e.description(), area.width() - sigW - 22), area.x() + 14 + sigW, y + 1, c.muted, false);
                        hits.add(new Hit(HitType.HELP_ENTRY, b, e.name()));
                    } else {
                        String text = (String) row;
                        boolean header = text.equals(text.toUpperCase()) && !text.contains(" ");
                        g.text(font, trim(text, area.width() - 10), area.x() + 4, y + 1, header ? c.accent : c.text, false);
                    }
                }
                y += ROW_H;
            }
        } finally {
            UiScissorStack.global().pop(g);
        }
    }

    // ---------------------------------------------------------------- input: mouse

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        if (!visible) return false;
        int mx = (int) Math.round(mxd);
        int my = (int) Math.round(myd);
        DihWindowLayout bounds = getBounds();
        if (isOverCloseButton(mxd, myd, bounds)) {
            setVisible(false);
            return true;
        }
        if (button == 0 && isOverDragBar(mxd, myd)) {
            draggingWindow = true;
            dragOffsetX = mxd - panelX;
            dragOffsetY = myd - panelY;
            return true;
        }
        if (collapsed || !isMouseOver(mxd, myd)) return false;

        boolean keepNaming = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.bounds().contains(mx, my)) continue;
            if (hit.type() == HitType.SCRIPT && !listBounds.contains(mx, my)) continue;
            if (hit.type() == HitType.HELP_ENTRY && !helpBounds.contains(mx, my)) continue;
            switch (hit.type()) {
                case SCRIPT -> {
                    select(hit.value());
                    editorFocused = false;
                }
                case NEW -> {
                    naming = true;
                    keepNaming = true;
                    newName.setLength(0);
                }
                case NAME_FIELD -> keepNaming = true;
                case DELETE -> deleteClicked();
                case RUN -> runClicked();
                case SAVE -> save(true);
                case HELP -> helpOpen = !helpOpen;
                case HELP_ENTRY -> {
                    helpOpen = false;
                    editorFocused = true;
                    editor.insert(hit.value() + "()");
                    editor.left(false, false);
                    ensureCursorVisible = true;
                }
            }
            naming = keepNaming;
            return true;
        }
        naming = false;

        if (!helpOpen && selected != null && editorBounds.contains(mx, my)) {
            editorFocused = true;
            int offset = offsetAt(mx, my);
            long now = System.currentTimeMillis();
            if (button == 0 && now - lastClickAt < 350 && offset == lastClickOffset) {
                editor.selectWord(offset);
            } else {
                editor.moveTo(offset, button == 0 && shiftHeld());
                draggingSelection = button == 0;
            }
            lastClickAt = now;
            lastClickOffset = offset;
            return true;
        }
        editorFocused = false;
        return true;
    }

    private static boolean shiftHeld() {
        return DihKeys.isKeyDown(InputConstants.KEY_LSHIFT) || DihKeys.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingWindow) {
            draggingWindow = false;
            saveLayout();
            return true;
        }
        if (draggingSelection) {
            draggingSelection = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingWindow) {
            DihWindowLayout c = clampToScreen(this, new DihWindowLayout(
                (int) Math.round(mx - dragOffsetX), (int) Math.round(my - dragOffsetY),
                panelWidth, panelHeight, visible, collapsed));
            panelX = c.x;
            panelY = c.y;
            return true;
        }
        if (draggingSelection) {
            editor.moveTo(offsetAt((int) Math.round(mx), (int) Math.round(my)), true);
            return true;
        }
        return false;
    }

    private void autoScrollWhileDragging(int mx, int my, int visibleLines, int textX) {
        if (my < editorBounds.y() && topLine > 0) topLine--;
        else if (my > editorBounds.bottom() && topLine + visibleLines < editor.lineCount()) topLine++;
        if (mx < textX && scrollX > 0) scrollX = Math.max(0, scrollX - 6);
        else if (mx > editorBounds.right()) scrollX += 6;
        if (my < editorBounds.y() || my > editorBounds.bottom() || mx < textX || mx > editorBounds.right()) {
            editor.moveTo(offsetAt(mx, my), true);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || collapsed || !isMouseOver(mx, my)) return false;
        int x = (int) Math.round(mx);
        int y = (int) Math.round(my);
        int steps = (int) Math.signum(amount) * Math.max(1, (int) Math.round(Math.abs(amount)));
        if (listBounds.contains(x, y)) listScroll -= steps * ROW_H;
        else if (helpOpen && helpBounds.contains(x, y)) helpScroll -= steps * ROW_H * 2;
        else if (editorBounds.contains(x, y)) {
            if (shiftHeld()) scrollX = Math.max(0, scrollX - steps * 18);
            else topLine = Math.max(0, topLine - steps * 3);
        }
        return true;
    }

    /** Character offset under a mouse position in the editor. */
    private int offsetAt(int mx, int my) {
        List<String> lines = editor.lines();
        int line = topLine + Math.floorDiv(my - (editorBounds.y() + 2), LINE_H);
        if (line < 0) return 0;
        if (line >= lines.size()) return editor.text().length();
        String text = lines.get(line);
        int target = mx - (editorBounds.x() + gutterW + 3) + scrollX;
        int col = 0;
        int width = 0;
        while (col < text.length()) {
            int w = font.width(String.valueOf(text.charAt(col)));
            if (width + w / 2 >= target) break;
            width += w;
            col++;
        }
        return editor.offsetOf(line, col);
    }

    // ---------------------------------------------------------------- input: keyboard

    @Override
    public boolean keyPressed(int key, int scanCode, int mods) {
        if (!visible || collapsed) return false;
        if (naming) return nameKey(key);
        if (!editorFocused) return false;

        boolean ctrl = (mods & (InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER)) != 0;
        boolean shift = (mods & InputConstants.MOD_SHIFT) != 0;
        boolean alt = (mods & InputConstants.MOD_ALT) != 0;
        Minecraft mc = Minecraft.getInstance();
        ensureCursorVisible = true;

        if (ctrl) {
            switch (key) {
                case InputConstants.KEY_S -> save(true);
                case InputConstants.KEY_A -> editor.selectAll();
                case InputConstants.KEY_C -> mc.keyboardHandler.setClipboard(editor.copy());
                case InputConstants.KEY_X -> mc.keyboardHandler.setClipboard(editor.cut());
                case InputConstants.KEY_V -> editor.insert(mc.keyboardHandler.getClipboard());
                case InputConstants.KEY_Z -> { if (shift) editor.redo(); else editor.undo(); }
                case InputConstants.KEY_Y -> editor.redo();
                case InputConstants.KEY_SLASH -> editor.toggleComment();
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> runClicked();
                case InputConstants.KEY_LEFT -> editor.left(shift, true);
                case InputConstants.KEY_RIGHT -> editor.right(shift, true);
                case InputConstants.KEY_HOME -> editor.documentStart(shift);
                case InputConstants.KEY_END -> editor.documentEnd(shift);
                case InputConstants.KEY_BACKSPACE -> editor.backspace(true);
                case InputConstants.KEY_DELETE -> editor.delete(true);
                default -> { }
            }
            noteEdit();
            return true;
        }
        switch (key) {
            case InputConstants.KEY_ESCAPE -> {
                editorFocused = false;
                return true;
            }
            case InputConstants.KEY_LEFT -> editor.left(shift, alt);
            case InputConstants.KEY_RIGHT -> editor.right(shift, alt);
            case InputConstants.KEY_UP -> editor.up(shift);
            case InputConstants.KEY_DOWN -> editor.down(shift);
            case InputConstants.KEY_HOME -> editor.home(shift);
            case InputConstants.KEY_END -> editor.end(shift);
            case InputConstants.KEY_PAGEUP -> editor.verticalBy(-pageLines(), shift);
            case InputConstants.KEY_PAGEDOWN -> editor.verticalBy(pageLines(), shift);
            case InputConstants.KEY_BACKSPACE -> editor.backspace(false);
            case InputConstants.KEY_DELETE -> editor.delete(false);
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> editor.newline();
            case InputConstants.KEY_TAB -> { if (shift) editor.outdent(); else editor.indent(); }
            default -> { }
        }
        noteEdit();
        // swallow every key while typing so game keybinds (inventory, drop...) don't fire
        return true;
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (!visible || collapsed) return false;
        if (naming) {
            if ((Character.isLetterOrDigit(chr) || chr == '_' || chr == '-') && newName.length() < 32) newName.append(chr);
            return true;
        }
        if (!editorFocused) return false;
        if ((mods & (InputConstants.MOD_CONTROL | InputConstants.MOD_SUPER)) != 0) return true;
        if (chr >= 32 && chr != 127) {
            editor.type(chr);
            ensureCursorVisible = true;
            noteEdit();
        }
        return true;
    }

    private boolean nameKey(int key) {
        switch (key) {
            case InputConstants.KEY_ESCAPE -> naming = false;
            case InputConstants.KEY_BACKSPACE -> { if (!newName.isEmpty()) newName.setLength(newName.length() - 1); }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> createScript();
            default -> { }
        }
        return true;
    }

    private int pageLines() {
        return Math.max(1, (editorBounds.height() - 4) / LINE_H - 1);
    }

    // ---------------------------------------------------------------- actions

    private void createScript() {
        String name = newName.toString();
        if (!ScriptManager.isValidName(name)) {
            flash("Names use letters, digits, _ or - (max 32).", 0xFFFF5555);
            return;
        }
        if (ScriptManager.exists(name)) {
            flash("'" + name + "' already exists.", 0xFFFF5555);
            return;
        }
        try {
            ScriptManager.write(name, TEMPLATE);
            naming = false;
            select(name);
            editorFocused = true;
            flash("Created " + name + ScriptManager.EXTENSION, 0xFF3FE87E);
        } catch (IOException e) {
            flash("Could not create: " + e.getMessage(), 0xFFFF5555);
        }
    }

    private void deleteClicked() {
        if (selected == null) return;
        long now = System.currentTimeMillis();
        if (now > deleteArmedUntil) {
            deleteArmedUntil = now + 3000;
            return;
        }
        deleteArmedUntil = 0;
        String name = selected;
        try {
            ScriptManager.delete(name);
            selected = null;
            editor.setText("");
            savedText = "";
            problems = List.of();
            List<String> left = ScriptManager.names();
            if (!left.isEmpty()) select(left.get(0));
            flash("Deleted " + name, 0xFFFFCB6B);
        } catch (IOException e) {
            flash("Could not delete: " + e.getMessage(), 0xFFFF5555);
        }
    }

    private void runClicked() {
        if (selected == null) return;
        if (ScriptManager.isRunning(selected)) {
            ScriptManager.stop(selected);
            flash("Stopping " + selected + "…", 0xFFFFCB6B);
            return;
        }
        if (!save(false)) return;
        String error = ScriptManager.run(selected);
        if (error != null) flash(error, 0xFFFF5555);
        else flash("Started " + selected, 0xFF3FE87E);
    }

    private boolean save(boolean announce) {
        if (selected == null) return false;
        try {
            ScriptManager.write(selected, editor.text());
            savedText = editor.text();
            if (announce) flash("Saved " + selected + ScriptManager.EXTENSION, 0xFF3FE87E);
            return true;
        } catch (IOException e) {
            flash("Could not save: " + e.getMessage(), 0xFFFF5555);
            return false;
        }
    }

    private void saveIfDirty() {
        if (selected != null && isDirty()) save(false);
    }

    private boolean isDirty() {
        return !editor.text().equals(savedText);
    }

    private void noteEdit() {
        editedAt = System.currentTimeMillis();
    }

    /** Re-checks the script ~300 ms after typing stops. */
    private void runLiveCheck() {
        if (selected == null || editor.revision() == checkedRevision) return;
        if (System.currentTimeMillis() - editedAt < 300 && checkedRevision >= 0) return;
        checkedRevision = editor.revision();
        problems = ScriptManager.check(editor.text());
    }

    private void flash(String message, int color) {
        flash = message;
        flashColor = color;
        flashUntil = System.currentTimeMillis() + 3500;
    }

    private static boolean blink() {
        return (System.currentTimeMillis() / 530) % 2 == 0;
    }

    private String trim(String s, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (font.width(s) <= maxWidth) return s;
        String dots = "…";
        int end = s.length();
        while (end > 0 && font.width(s.substring(0, end)) + font.width(dots) > maxWidth) end--;
        return s.substring(0, end) + dots;
    }
}
