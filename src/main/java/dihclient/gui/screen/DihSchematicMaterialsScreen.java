package dihclient.gui.screen;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.components.Button;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.schematic.SchematicActions;
import dihclient.schematic.SchematicMaterials;
import dihclient.schematic.SchematicPlacement;
import dihclient.schematic.Schematics;
import dihclient.util.DihTheme;
import dihclient.util.DihUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A placement's whole material list as a table, like Litematica's: every item with how many the build needs, how
 * many are in the inventory and how many are still missing, the biggest shortfalls first. It re-checks the world and
 * the inventory every couple of seconds, so it fills in while you gather. Missing only hides what you already have
 * enough of; Save CSV writes the same list to a file.
 */
public final class DihSchematicMaterialsScreen extends DihScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int ROW_H = 13;
    private static final int TOP = 58;
    private static final int BUTTON_H = 18;
    private static final int REFRESH_TICKS = 40;

    private final SchematicPlacement placement;
    private final Screen parent;
    private SchematicMaterials.Report report;
    private final Map<String, String> names = new HashMap<>();
    private boolean missingOnly;
    private int scroll;
    private int ticks;
    private String status = "";
    private int statusColor = DihScreenPalette.MUTED;

    public DihSchematicMaterialsScreen(SchematicPlacement placement, Screen parent) {
        super(Component.literal("Materials"));
        this.placement = placement;
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        clearWidgets();
        refresh();
        int w = panelWidth();
        int x = (screenWidth() - w) / 2;
        int gap = 4;
        int bottom = screenHeight() - 28;
        String[] labels = {this.missingOnly ? "Show all" : "Missing only", "Save CSV", "Refresh", "Back"};
        int cell = (w - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            addRenderableWidget(new DihStyledButton(x + i * (cell + gap), bottom, cell, BUTTON_H, Component.literal(label),
                label.equals("Save CSV") ? Button.Tone.SUCCESS : Button.Tone.SECONDARY, b -> act(label)));
        }
    }

    private int panelWidth() {
        return Math.max(260, Math.min(460, screenWidth() - 40));
    }

    private void act(String label) {
        switch (label) {
            case "Missing only", "Show all" -> {
                this.missingOnly = !this.missingOnly;
                this.scroll = 0;
                init();
            }
            case "Save CSV" -> {
                try {
                    status(SchematicActions.exportMaterials(this.minecraft, this.placement), DihScreenPalette.SUCCESS);
                } catch (IllegalArgumentException e) {
                    status(e.getMessage(), DihScreenPalette.ERROR);
                }
            }
            case "Refresh" -> refresh();
            case "Back" -> onClose();
            default -> {
            }
        }
    }

    private void status(String text, int color) {
        this.status = text;
        this.statusColor = color;
    }

    private void refresh() {
        this.ticks = 0;
        if (this.minecraft == null || this.minecraft.player == null || !this.placement.isLoaded()) {
            this.report = null;
            return;
        }
        this.report = SchematicActions.materialsReport(this.minecraft, this.placement);
    }

    @Override
    public void tick() {
        if (this.minecraft == null || this.minecraft.player == null || !Schematics.get().placements().contains(this.placement)) {
            onClose();
            return;
        }
        if (++this.ticks >= REFRESH_TICKS || this.report == null && this.placement.isLoaded()) {
            refresh();
        }
    }

    private List<SchematicMaterials.Line> rows() {
        if (this.report == null) {
            return List.of();
        }
        List<SchematicMaterials.Line> out = new ArrayList<>();
        for (SchematicMaterials.Line line : this.report.lines()) {
            if (!this.missingOnly || line.missing() > 0) {
                out.add(line);
            }
        }
        this.report.unplaceable().forEach((block, count) -> out.add(new SchematicMaterials.Line(block, count, -1)));
        return out;
    }

    private String name(String id) {
        return this.names.computeIfAbsent(id, SchematicActions::displayName);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        this.scroll = Math.max(0, this.scroll + (vertical < 0 ? 3 : -3));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = DihUiScale.toVirtualInt(mouseX);
        int virtualMouseY = DihUiScale.toVirtualInt(mouseY);
        DihUiScale.pushOverlayScale(graphics);
        try {
            int w = panelWidth();
            int x = (screenWidth() - w) / 2;
            Identifier font = THEME.fontFor(UiTone.BODY);
            int text = DihTheme.recolor(DihScreenPalette.TEXT, DihTheme.Channel.TEXT);
            int muted = DihTheme.recolor(DihScreenPalette.MUTED, DihTheme.Channel.TEXT);
            int good = DihTheme.recolor(DihScreenPalette.SUCCESS, DihTheme.Channel.SUCCESS);
            int warn = DihTheme.recolor(DihScreenPalette.WARN, DihTheme.Channel.TEXT);
            int border = DihTheme.recolor(DihScreenPalette.BORDER, DihTheme.Channel.OUTLINE);
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()),
                DihTheme.recolor(DihScreenPalette.BG, DihTheme.Channel.BACKDROP) & 0xC0FFFFFF);
            UiRenderer.frame(graphics, UiBounds.of(x - 6, 14, w + 12, Math.max(1, screenHeight() - 20)),
                DihTheme.recolor(DihScreenPalette.PANEL_BG, DihTheme.Channel.BUTTON), border);
            UiText.drawEllipsized(graphics, this.font, "Materials: " + this.placement.name(), font, text, x, 22, w, false);

            String summary;
            int summaryColor = muted;
            if (!this.placement.isLoaded()) {
                summary = this.placement.error().isEmpty() ? "Loading..." : "Not loaded: " + this.placement.error();
            } else if (this.report == null) {
                summary = "Join a world to compare it with your inventory.";
            } else if (this.report.remaining() == 0) {
                summary = "Complete: every block is placed.";
                summaryColor = good;
            } else if (this.report.ready()) {
                summary = this.report.remaining() + " blocks left, and you carry everything they need.";
                summaryColor = good;
            } else {
                summary = this.report.remaining() + " blocks left, " + this.report.missingTotal() + " items short";
                summaryColor = warn;
            }
            UiText.drawEllipsized(graphics, this.font, summary, font, summaryColor, x, 34, w, false);

            int colTotal = x + w - 150;
            int colHave = x + w - 100;
            int colMissing = x + w - 50;
            UiText.draw(graphics, this.font, "Item", font, muted, x + 4, TOP - 12, false);
            drawRight(graphics, "Total", font, muted, colTotal + 40);
            drawRight(graphics, "Have", font, muted, colHave + 40);
            drawRight(graphics, "Missing", font, muted, colMissing + 46);
            UiRenderer.rect(graphics, UiBounds.of(x, TOP - 2, w, 1), border);

            List<SchematicMaterials.Line> rows = rows();
            int listBottom = screenHeight() - 28 - BUTTON_H - 18;
            int visible = Math.max(1, (listBottom - TOP) / ROW_H);
            this.scroll = Math.min(this.scroll, Math.max(0, rows.size() - visible));
            if (rows.isEmpty() && this.report != null && this.report.remaining() > 0) {
                UiText.draw(graphics, this.font, "You have enough of everything. Show all lists it.", font, muted, x + 4, TOP + 2, false);
            }
            for (int i = this.scroll, y = TOP; i < rows.size() && y + ROW_H <= listBottom; i++, y += ROW_H) {
                SchematicMaterials.Line line = rows.get(i);
                boolean hover = virtualMouseX >= x && virtualMouseX < x + w && virtualMouseY >= y && virtualMouseY < y + ROW_H - 1;
                boolean noItem = line.have() < 0;
                boolean done = !noItem && line.missing() == 0;
                UiRenderer.rect(graphics, UiBounds.of(x, y, w, ROW_H - 1), hover ? 0x33FFFFFF : (i % 2 == 0 ? 0x18000000 : 0x10FFFFFF));
                int nameColor = done ? muted : text;
                String label = name(line.item()) + (noItem ? "  (no item places it)" : "");
                UiText.drawEllipsized(graphics, this.font, label, font, nameColor, x + 4, y + 2, colTotal - x - 12, false);
                drawRight(graphics, String.valueOf(line.needed()), font, nameColor, colTotal + 40, y + 2);
                drawRight(graphics, noItem ? "-" : String.valueOf(line.have()), font, nameColor, colHave + 40, y + 2);
                drawRight(graphics, noItem ? "-" : done ? "done" : String.valueOf(line.missing()), font,
                    done ? good : noItem ? muted : warn, colMissing + 46, y + 2);
            }
            if (rows.size() > visible) {
                int trackH = listBottom - TOP;
                int barH = Math.max(12, trackH * visible / rows.size());
                int barY = TOP + (trackH - barH) * this.scroll / Math.max(1, rows.size() - visible);
                UiRenderer.rect(graphics, UiBounds.of(x + w + 2, barY, 2, barH), muted);
            }

            String foot = this.status.isEmpty()
                ? "Updates every 2 seconds. Blocks in shulker boxes or chests don't count." : this.status;
            UiText.drawEllipsized(graphics, this.font, foot, font,
                this.status.isEmpty() ? muted : DihTheme.recolor(this.statusColor, DihTheme.Channel.TEXT), x, listBottom + 4, w, false);
            super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
        } finally {
            DihUiScale.popOverlayScale(graphics);
        }
    }

    private void drawRight(GuiGraphicsExtractor graphics, String value, Identifier font, int color, int right) {
        drawRight(graphics, value, font, color, right, TOP - 12);
    }

    private void drawRight(GuiGraphicsExtractor graphics, String value, Identifier font, int color, int right, int y) {
        UiText.draw(graphics, this.font, value, font, color, right - UiText.width(this.font, value, font, color), y, false);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
