package dihclient.gui.profiles;

import com.mojang.blaze3d.platform.InputConstants;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.UiScissorStack;
import dihclient.gui.vanillaui.components.CompactScrollbar;
import dihclient.gui.vanillaui.components.CompactTextInput;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.gui.vanillaui.direct.DirectRenderContext;
import dihclient.gui.vanillaui.direct.DirectViewport;
import dihclient.util.DihNotifications;
import dihclient.util.DihProfile;
import dihclient.util.DihProfileManager;
import dihclient.util.DihTheme;
import dihclient.util.DihTheme.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class ProfilesPanel {
    public interface Host { Screen returnScreen(); }

    private enum NameMode { NONE, CREATE, RENAME, DUPLICATE }

    private static final int CARD_BG = 0x2A121214;
    private static final int DISABLED_FILL = 0x18111113;
    private static final int DISABLED_BORDER = 0xFF2A2A2E;
    private static final int WARN = 0xFFFFC857;
    private static final int BORDER_STOCK = 0xFF332428;
    private static final int BORDER_BRIGHT_STOCK = 0xFF7A5560;
    private static final int ACCENT_STOCK = 0xFF35D873;
    private static final int TEXT_STOCK = 0xFFF2F2F2;
    private static final int MUTED_STOCK = 0xFF9A9A9A;
    private static final int ERROR_STOCK = 0xFFFF5B5B;

    private int BORDER = BORDER_STOCK, BORDER_BRIGHT = BORDER_BRIGHT_STOCK, ACCENT = ACCENT_STOCK,
        TEXT = TEXT_STOCK, MUTED = MUTED_STOCK, ERROR = ERROR_STOCK;

    private final Host host;
    private final Font font;
    private final CompactTheme theme = new CompactTheme();
    private final DihProfileManager mgr = DihProfileManager.get();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final List<CompactTextInput> activeInputs = new ArrayList<>();

    private final CompactTextInput nameField = field("Profile name", 48);
    private final CompactTextInput ruleField = field("e.g. play.example.com or *.example.com", 80);

    private String selectedId = "";
    private int listScroll;
    private CompactScrollbar.Metrics listScrollbar;
    private boolean listDragging;
    private int listGrab;
    private int ruleScroll;

    private NameMode nameMode = NameMode.NONE;
    private boolean showPrompt;
    private DihProfile pendingLoad;

    private boolean createAutoSave = true, createOwnMacros = true, createOwnTheme = true;

    private boolean suppressInput;
    private float delta;
    private int lastMx, lastMy;
    private int bx, by, bw, bh;

    private int listX, listY, listW, listH, ruleTop, ruleBottom, ruleX, ruleW;
    private DihThemeStateRef cachedTheme = new DihThemeStateRef();

    public ProfilesPanel(Host host, Font font) {
        this.host = host;
        this.font = font;
        ruleField.setOnSubmit(v -> addCurrentRule());
        nameField.setOnSubmit(v -> confirmName());
    }

    private CompactTextInput field(String placeholder, int maxLen) {
        CompactTextInput f = new CompactTextInput();
        f.setPlaceholder(placeholder).setMaxLength(maxLen).setFieldHeight(16);
        return f;
    }

    public void render(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my, float partial) {
        refreshTheme();
        hotspots.clear();
        activeInputs.clear();
        this.delta = partial;
        this.lastMx = mx; this.lastMy = my;
        this.bx = x; this.by = y; this.bw = w; this.bh = h;

        boolean modal = nameMode != NameMode.NONE || showPrompt;
        suppressInput = modal;

        DihProfile selected = resolveSelected();

        DihProfile active = mgr.active();
        String activeLabel = "Active: " + (active == null ? "—" : active.displayName);
        drawText(g, activeLabel, x + 6, y + 6, TEXT, false, w - 140);
        boolean dirty = active != null && !active.autoSave && mgr.isDirty();
        if (dirty) drawText(g, "● unsaved", x + 6, y + 16, WARN, false, w - 140);
        boolean canSave = dirty;
        button(g, x + w - 60, y + 4, 54, 16, "Save", canSave ? ACCENT : BORDER, TEXT, mx, my,
            canSave ? () -> { mgr.save(mgr.active()); } : null);

        int contentTop = y + 28;
        int pad = 6;
        int leftW = Math.max(150, Math.min(230, (int) (w * 0.42f)));
        int leftX = x + pad;
        int rightX = x + leftW + 10;
        int rightW = x + w - pad - rightX;

        button(g, leftX, contentTop, leftW, 16, "+ New profile", ACCENT, TEXT, mx, my, () -> openName(NameMode.CREATE));
        listX = leftX;
        listY = contentTop + 20;
        listW = leftW;
        int listBottom = y + h - pad;
        listH = Math.max(20, listBottom - listY);
        renderList(g, selected, mx, my);

        renderDetail(g, selected, rightX, contentTop, Math.max(120, rightW), y + h - pad - contentTop, mx, my);

        if (modal) {
            suppressInput = false;
            UiRenderer.rect(g, UiBounds.of(x, y, w, h), 0xB0000000);
            if (showPrompt) renderPrompt(g, x, y, w, h, mx, my);
            else renderNameModal(g, x, y, w, h, mx, my);
        }
    }

    private DihProfile resolveSelected() {
        DihProfile sel = mgr.byId(selectedId);
        if (sel == null) {
            selectedId = mgr.activeId();
            sel = mgr.byId(selectedId);
            if (sel == null) {
                List<DihProfile> all = mgr.list();
                if (!all.isEmpty()) { sel = all.get(0); selectedId = sel.id; }
            }
        }
        return sel;
    }

    private void renderList(GuiGraphicsExtractor g, DihProfile selected, int mx, int my) {
        List<DihProfile> profiles = mgr.list();
        int rowH = 24;
        int contentH = profiles.size() * rowH;
        int maxScroll = Math.max(0, contentH - listH);
        listScroll = Math.max(0, Math.min(listScroll, maxScroll));

        UiScissorStack.global().push(g, UiBounds.of(listX, listY, listW, listH));
        try {
            int rowW = listW - (maxScroll > 0 ? 8 : 2);
            int ry = listY - listScroll;
            String activeId = mgr.activeId();
            for (DihProfile p : profiles) {
                if (ry + rowH > listY && ry < listY + listH) {
                    boolean isActive = p.id.equals(activeId);
                    boolean isSel = selected != null && p.id.equals(selected.id);
                    boolean hover = !suppressInput && mx >= listX && mx < listX + rowW && my >= ry && my < ry + rowH - 2
                        && my >= listY && my < listY + listH;
                    int fill = isSel ? tint(ACCENT, 0x33) : hover ? tint(BORDER_BRIGHT, 0x22) : CARD_BG;
                    int border = isSel ? ACCENT : BORDER;
                    UiRenderer.frame(g, UiBounds.of(listX, ry, rowW, rowH - 2), fill, border);
                    drawText(g, p.displayName, listX + 6, ry + 3, TEXT, false, rowW - 16);
                    drawText(g, subLabel(p), listX + 6, ry + 13, MUTED, false, rowW - 16);
                    if (isActive) drawText(g, "●", listX + rowW - 10, ry + 7, ACCENT, false);
                    final DihProfile pick = p;
                    if (!suppressInput) hotspots.add(new Hotspot(listX, ry, rowW, rowH - 2, () -> selectedId = pick.id));
                }
                ry += rowH;
            }
        } finally {
            UiScissorStack.global().pop(g);
        }

        if (maxScroll > 0) {
            listScrollbar = CompactScrollbar.compute(contentH, listH, listX + listW - 4, listY, 3, listH, listScroll);
            CompactScrollbar.draw(g, listScrollbar, listScrollbar.contains(mx, my), listDragging);
        } else {
            listScrollbar = null;
        }
    }

    private String subLabel(DihProfile p) {
        StringBuilder sb = new StringBuilder(p.autoSave ? "auto-save" : "manual");
        if (p.ownMacroLibrary) sb.append(" • own macros");
        int r = p.serverPatterns == null ? 0 : p.serverPatterns.size();
        if (r > 0) sb.append(" • ").append(r).append(r == 1 ? " server" : " servers");
        return sb.toString();
    }

    private void renderDetail(GuiGraphicsExtractor g, DihProfile p, int dx, int dy, int dw, int dh, int mx, int my) {
        if (p == null) { drawText(g, "Select a profile.", dx, dy + 4, MUTED, false, dw); return; }
        boolean isActive = p.id.equals(mgr.activeId());
        int cy = dy;
        drawText(g, p.displayName + (isActive ? "  (active)" : ""), dx, cy, TEXT, false, dw);
        cy += 14;

        int half = (dw - 4) / 2;
        button(g, dx, cy, half, 16, "Load", isActive ? BORDER : ACCENT, TEXT, mx, my, isActive ? null : () -> doLoad(p));
        button(g, dx + half + 4, cy, dw - half - 4, 16, "Rename", BORDER, TEXT, mx, my, () -> openName(NameMode.RENAME));
        cy += 19;
        boolean canDelete = !DihProfileManager.isDefault(p) && mgr.count() > 1;
        button(g, dx, cy, half, 16, "Duplicate", BORDER, TEXT, mx, my, () -> openName(NameMode.DUPLICATE));
        button(g, dx + half + 4, cy, dw - half - 4, 16, "Delete", ERROR, TEXT, mx, my, canDelete ? () -> doDelete(p) : null);
        cy += 22;

        cy = toggle(g, dx, cy, dw, "Auto-save changes", p.autoSave, mx, my, () -> mgr.setAutoSave(p, !p.autoSave));
        cy = toggle(g, dx, cy, dw, "Own macro library", p.ownMacroLibrary, mx, my, () -> mgr.setOwnMacroLibrary(p, !p.ownMacroLibrary));
        cy = toggle(g, dx, cy, dw, "Own theme color", p.ownThemeColor, mx, my, () -> mgr.setOwnThemeColor(p, !p.ownThemeColor));
        cy += 4;

        drawText(g, "Auto-apply on servers:", dx, cy, MUTED, false, dw);
        cy += 11;

        ruleX = dx; ruleW = dw;
        ruleTop = cy;
        ruleBottom = dy + dh - 22;
        int ruleViewH = Math.max(14, ruleBottom - ruleTop);
        List<String> patterns = p.serverPatterns == null ? List.of() : p.serverPatterns;
        int ruleContentH = patterns.size() * 14;
        int ruleMax = Math.max(0, ruleContentH - ruleViewH);
        ruleScroll = Math.max(0, Math.min(ruleScroll, ruleMax));
        UiScissorStack.global().push(g, UiBounds.of(dx, ruleTop, dw, ruleViewH));
        try {
            int ry = ruleTop - ruleScroll;
            for (String pat : patterns) {
                if (ry + 14 > ruleTop && ry < ruleTop + ruleViewH) {
                    UiRenderer.frame(g, UiBounds.of(dx, ry, dw, 13), CARD_BG, BORDER);
                    drawText(g, pat, dx + 5, ry + 3, TEXT, false, dw - 26);
                    final String rp = pat;
                    button(g, dx + dw - 16, ry, 14, 13, "×", BORDER, ERROR, mx, my, () -> removeRule(p, rp));
                }
                ry += 14;
            }
            if (patterns.isEmpty()) drawText(g, "(none — this profile is never auto-applied)", dx + 2, ruleTop + 2, MUTED, false, dw - 4);
        } finally {
            UiScissorStack.global().pop(g);
        }

        int addY = dy + dh - 18;
        int useW = 78;
        int addW = 40;
        placeField(g, ruleField, dx, addY, dw - useW - addW - 8);
        button(g, dx + dw - useW - addW - 4, addY, addW, 16, "Add", ACCENT, TEXT, mx, my, this::addCurrentRule);
        button(g, dx + dw - useW, addY, useW, 16, "Use current", BORDER, TEXT, mx, my, this::fillCurrentServer);
    }

    private void renderNameModal(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        boolean create = nameMode == NameMode.CREATE;
        int boxW = Math.min(w - 30, 280);
        int boxH = create ? 140 : 78;
        int boxX = x + (w - boxW) / 2;
        int boxY = y + (h - boxH) / 2;
        UiRenderer.frame(g, UiBounds.of(boxX, boxY, boxW, boxH), 0xF0121214, ACCENT);
        String title = switch (nameMode) { case CREATE -> "New profile"; case RENAME -> "Rename profile"; case DUPLICATE -> "Duplicate profile"; default -> ""; };
        drawText(g, title, boxX + boxW / 2, boxY + 8, TEXT, true);
        placeField(g, nameField, boxX + 10, boxY + 24, boxW - 20);
        int half = (boxW - 24) / 2;
        int btnY = boxY + 50;
        if (create) {
            int tw = boxW - 20;
            int ty = boxY + 44;
            ty = toggle(g, boxX + 10, ty, tw, "Auto-save changes", createAutoSave, mx, my, () -> createAutoSave = !createAutoSave);
            ty = toggle(g, boxX + 10, ty, tw, "Own macro library", createOwnMacros, mx, my, () -> createOwnMacros = !createOwnMacros);
            ty = toggle(g, boxX + 10, ty, tw, "Own theme color", createOwnTheme, mx, my, () -> createOwnTheme = !createOwnTheme);
            btnY = ty + 3;
        }
        button(g, boxX + 10, btnY, half, 18, "OK", ACCENT, TEXT, mx, my, this::confirmName);
        button(g, boxX + 14 + half, btnY, half, 18, "Cancel", BORDER, TEXT, mx, my, this::closeName);
    }

    private void renderPrompt(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        int boxW = Math.min(w - 30, 320);
        int boxH = 80;
        int boxX = x + (w - boxW) / 2;
        int boxY = y + (h - boxH) / 2;
        UiRenderer.frame(g, UiBounds.of(boxX, boxY, boxW, boxH), 0xF0121214, WARN);
        DihProfile active = mgr.active();
        drawText(g, "Unsaved changes", boxX + boxW / 2, boxY + 8, WARN, true);
        drawText(g, "Save \"" + (active == null ? "" : active.displayName) + "\" before switching?",
            boxX + boxW / 2, boxY + 22, MUTED, true, boxW - 12);
        int third = (boxW - 28) / 3;
        button(g, boxX + 10, boxY + 50, third, 18, "Save", ACCENT, TEXT, mx, my, () -> resolvePrompt(true));
        button(g, boxX + 14 + third, boxY + 50, third, 18, "Discard", ERROR, TEXT, mx, my, () -> resolvePrompt(false));
        button(g, boxX + 18 + third * 2, boxY + 50, third, 18, "Cancel", BORDER, TEXT, mx, my, () -> { showPrompt = false; pendingLoad = null; });
    }

    private void doLoad(DihProfile target) {
        if (target == null) return;
        DihProfile cur = mgr.active();
        if (cur != null && target.id.equals(cur.id)) return;
        if (cur != null && !cur.autoSave && mgr.isDirty()) { pendingLoad = target; showPrompt = true; return; }
        mgr.beginLoad(target);
        selectedId = target.id;
    }

    private void resolvePrompt(boolean save) {
        DihProfile target = pendingLoad;
        showPrompt = false;
        pendingLoad = null;
        if (target == null) return;
        if (save) mgr.save(mgr.active());
        mgr.beginLoad(target);
        selectedId = target.id;
    }

    private void doDelete(DihProfile p) {
        if (p == null) return;
        if (mgr.delete(p)) {
            selectedId = mgr.activeId();
            DihNotifications.show("Deleted profile: " + p.displayName, ERROR);
        }
    }

    private void openName(NameMode mode) {
        nameMode = mode;
        if (mode == NameMode.CREATE) { createAutoSave = true; createOwnMacros = true; createOwnTheme = true; }
        DihProfile sel = mgr.byId(selectedId);
        nameField.setText(mode == NameMode.RENAME && sel != null ? sel.displayName
            : mode == NameMode.DUPLICATE && sel != null ? sel.displayName + " copy" : "");
        nameField.setFocused(true);
    }

    private void closeName() {
        nameMode = NameMode.NONE;
        nameField.setFocused(false);
        nameField.setText("");
    }

    private void confirmName() {
        String name = nameField.text() == null ? "" : nameField.text().strip();
        if (name.isEmpty()) { DihNotifications.show("Enter a name.", WARN); return; }

        String exclude = nameMode == NameMode.RENAME ? selectedId : null;
        if (mgr.nameExists(name, exclude)) { DihNotifications.show("Name already in use.", WARN); return; }
        switch (nameMode) {
            case CREATE -> { DihProfile p = mgr.create(name, createAutoSave, createOwnMacros, createOwnTheme); if (p != null) { selectedId = p.id; mgr.beginLoad(p); } }
            case RENAME -> { DihProfile sel = mgr.byId(selectedId); if (sel != null) mgr.rename(sel, name); }
            case DUPLICATE -> { DihProfile sel = mgr.byId(selectedId); DihProfile d = mgr.duplicate(sel, name); if (d != null) selectedId = d.id; }
            default -> { }
        }
        closeName();
    }

    private void addCurrentRule() {
        DihProfile sel = mgr.byId(selectedId);
        if (sel == null) return;
        String pat = ruleField.text() == null ? "" : ruleField.text().strip().toLowerCase(java.util.Locale.ROOT);
        if (pat.isEmpty()) return;
        List<String> next = new ArrayList<>(sel.serverPatterns);
        if (!next.contains(pat)) next.add(pat);
        mgr.setServerPatterns(sel, next);
        ruleField.setText("");
    }

    private void removeRule(DihProfile p, String pattern) {
        if (p == null) return;
        List<String> next = new ArrayList<>(p.serverPatterns);
        next.remove(pattern);
        mgr.setServerPatterns(p, next);
    }

    private void fillCurrentServer() {
        Minecraft mc = Minecraft.getInstance();
        ServerData sd = mc == null ? null : mc.getCurrentServer();
        if (sd != null && sd.ip != null && !sd.ip.isBlank()) {
            ruleField.setText(sd.ip.strip().toLowerCase(java.util.Locale.ROOT));
            ruleField.setFocused(true);
        } else {
            DihNotifications.show("Not connected to a server.", WARN);
        }
    }

    public boolean mouseClicked(int mx, int my, int button) {
        this.lastMx = mx; this.lastMy = my;
        if (button == 0 && !suppressInput && listScrollbar != null && listScrollbar.contains(mx, my)) {
            listDragging = true;
            listGrab = my - listScrollbar.thumbY();
            clearFocus();
            return true;
        }

        DirectRenderContext ctx = renderCtx(null, mx, my);
        for (CompactTextInput in : activeInputs) {
            if (in.mouseClicked(ctx, mx, my, button)) {
                for (CompactTextInput other : activeInputs) if (other != in) other.setFocused(false);
                return true;
            }
        }
        clearFocus();
        if (button == 0) {
            for (Hotspot hsp : hotspots) {
                if (hsp.hit(mx, my)) {
                    try { hsp.action.run(); }
                    catch (Throwable t) { DihNotifications.show("Action failed: " + t.getClass().getSimpleName(), ERROR); }
                    return true;
                }
            }
        }
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    public boolean mouseReleased(int mx, int my, int button) {
        if (listDragging) { listDragging = false; return true; }
        DirectRenderContext ctx = renderCtx(null, mx, my);
        boolean any = false;
        for (CompactTextInput in : activeInputs) any |= in.mouseReleased(ctx, mx, my, button);
        return any;
    }

    public boolean mouseDragged(int mx, int my, int button, double dx, double dy) {
        if (listDragging && listScrollbar != null) {
            listScroll = CompactScrollbar.scrollFromThumb(listScrollbar, my, listGrab);
            return true;
        }
        DirectRenderContext ctx = renderCtx(null, mx, my);
        boolean any = false;
        for (CompactTextInput in : activeInputs) any |= in.mouseDragged(ctx, mx, my, button, (float) dx, (float) dy);
        return any;
    }

    public boolean mouseScrolled(int mx, int my, double amount) {
        if (suppressInput) return false;
        int step = (int) Math.signum(amount) * 16;
        if (mx >= ruleX && mx < ruleX + ruleW && my >= ruleTop && my < ruleBottom) {
            ruleScroll = Math.max(0, ruleScroll - step);
            return true;
        }
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            listScroll = Math.max(0, listScroll - step);
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (showPrompt) { showPrompt = false; pendingLoad = null; return true; }
            if (nameMode != NameMode.NONE) { closeName(); return true; }
        }
        DirectRenderContext ctx = renderCtx(null, lastMx, lastMy);
        for (CompactTextInput in : activeInputs) {
            if (in.isFocused() && in.keyPressed(ctx, keyCode, scanCode, modifiers)) return true;
        }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        DirectRenderContext ctx = renderCtx(null, lastMx, lastMy);
        for (CompactTextInput in : activeInputs) {
            if (in.isFocused() && in.charTyped(ctx, chr, modifiers)) return true;
        }
        return false;
    }

    public boolean hasFocusedTextInput() {
        for (CompactTextInput in : activeInputs) if (in.isFocused()) return true;
        return false;
    }

    public void clearFocus() {
        nameField.setFocused(false);
        ruleField.setFocused(false);
    }

    public int desiredHeight() { return 260; }

    private DirectRenderContext renderCtx(GuiGraphicsExtractor g, int mx, int my) {
        return new DirectRenderContext(g, font, DirectViewport.current(1.0f), theme, mx, my, delta);
    }

    private void placeField(GuiGraphicsExtractor g, CompactTextInput f, int x, int y, int w) {
        f.setBounds(x, y, Math.max(10, w), 16);
        f.render(renderCtx(g, lastMx, lastMy));
        if (!suppressInput) activeInputs.add(f);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int border, int textColor,
                        int mx, int my, Runnable action) {
        boolean enabled = action != null;
        boolean hover = enabled && !suppressInput && mx >= x && mx < x + w && my >= y && my < y + h;
        dihclient.gui.vanillaui.components.Button.Tone tone = border == ACCENT || textColor == ACCENT
            ? dihclient.gui.vanillaui.components.Button.Tone.PRIMARY
            : dihclient.gui.vanillaui.components.Button.Tone.NORMAL;
        dihclient.gui.vanillaui.components.Button.render(
            dihclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my),
            UiBounds.of(x, y, w, h), label, tone, hover, false);
        if (!enabled) UiRenderer.rect(g, UiBounds.of(x, y, w, h), 0x66000000);
        if (enabled && !suppressInput) hotspots.add(new Hotspot(x, y, w, h, action));
    }

    private int toggle(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean value, int mx, int my, Runnable onToggle) {
        int h = 18;
        boolean hover = !suppressInput && mx >= x && mx < x + w && my >= y && my < y + h;
        UiRenderer.frame(g, UiBounds.of(x, y, w, h), hover ? tint(BORDER, 0x33) : CARD_BG, BORDER);
        dihclient.gui.vanillaui.components.Toggle.renderLabeled(
            dihclient.gui.vanillaui.UiContexts.overlay(g, font, mx, my),
            UiBounds.of(x, y, w, h), label, value, hover, "profiles:" + label);
        if (!suppressInput) hotspots.add(new Hotspot(x, y, w, h, onToggle));
        return y + h + 3;
    }

    private void drawText(GuiGraphicsExtractor g, String text, int x, int y, int color, boolean center) {
        drawText(g, text, x, y, color, center, Integer.MAX_VALUE);
    }

    private void drawText(GuiGraphicsExtractor g, String text, int x, int y, int color, boolean center, int maxWidth) {
        Identifier fontId = theme.fontFor(UiTone.BODY);
        String value = text == null ? "" : text;
        if (maxWidth != Integer.MAX_VALUE && !center) {
            UiText.drawEllipsized(g, font, value, fontId, color, x, y, Math.max(1, maxWidth), false);
            return;
        }
        if (maxWidth != Integer.MAX_VALUE) value = UiText.trimToWidthEllipsis(font, value, maxWidth, fontId, color);
        int wpx = UiText.width(font, value, fontId, color);
        UiText.draw(g, font, value, fontId, color, center ? x - wpx / 2 : x, y, false);
    }

    private void refreshTheme() {
        DihTheme.State st = DihTheme.active();
        if (st == cachedTheme.state) return;
        cachedTheme.state = st;
        BORDER = DihTheme.recolor(BORDER_STOCK, Channel.OUTLINE);
        BORDER_BRIGHT = DihTheme.recolor(BORDER_BRIGHT_STOCK, Channel.OUTLINE);
        ACCENT = DihTheme.recolor(ACCENT_STOCK, Channel.ACCENT);
        TEXT = DihTheme.recolor(TEXT_STOCK, Channel.TEXT);
        MUTED = DihTheme.recolor(MUTED_STOCK, Channel.TEXT);
        ERROR = DihTheme.recolor(ERROR_STOCK, Channel.DANGER);
    }

    private static int tint(int color, int alpha) { return (alpha << 24) | (color & 0x00FFFFFF); }

    private static final class DihThemeStateRef { DihTheme.State state; }

    private static final class Hotspot {
        final int x, y, w, h; final Runnable action;
        Hotspot(int x, int y, int w, int h, Runnable action) { this.x = x; this.y = y; this.w = w; this.h = h; this.action = action; }
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }
}
