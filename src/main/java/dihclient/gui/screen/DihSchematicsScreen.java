package dihclient.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.components.Button;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.modules.Module;
import dihclient.modules.ModuleRegistry;
import dihclient.schematic.SchematicActions;
import dihclient.schematic.SchematicDrop;
import dihclient.schematic.SchematicPlacement;
import dihclient.schematic.SchematicVerifier;
import dihclient.schematic.Schematics;
import dihclient.util.DihTheme;
import dihclient.util.DihUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Schematics: the files in the schematics folder on the left (click one to place it in front of you), then the newest
 * ones in Downloads (copied into the folder when clicked), the placements in this world on the right (click one to
 * select it), and what to do with the selected one below. Open File... picks a file from anywhere; it and files
 * dropped on the window are handled by {@link SchematicDrop}.
 */
public final class DihSchematicsScreen extends DihScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int ROW_H = 14;
    private static final int TOP = 44;
    private static final int BUTTON_H = 18;
    private static final int OPEN_W = 90;

    private final Screen parent;
    /** A file to list: in the schematics folder ({@code download} null) or in Downloads. */
    private record FileEntry(String name, Path download) {}

    private List<FileEntry> files = List.of();
    private final List<int[]> fileRows = new ArrayList<>();
    private final List<int[]> placementRows = new ArrayList<>();
    private int fileScroll;
    private int placementScroll;
    private List<String> details = List.of();
    private int detailsColor = DihScreenPalette.MUTED;

    public DihSchematicsScreen(Screen parent) {
        super(Component.literal("Schematics"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        this.files = listFiles();
        int w = panelWidth();
        int x = (screenWidth() - w) / 2;
        int gap = 4;
        int bottom = screenHeight() - 28;
        String[] row1 = {"Move", "Rotate", "Mirror", "Show / Hide", "Remove"};
        String[] row2 = {"Materials", "Verify", "Build", "Folder", "Close"};
        addButton(x + w - OPEN_W, 17, OPEN_W, "Open File...", Button.Tone.SUCCESS, b -> SchematicDrop.pick(this.minecraft));
        int cell = (w - gap * 4) / 5;
        for (int i = 0; i < 5; i++) {
            String label = row1[i];
            addButton(x + i * (cell + gap), bottom - BUTTON_H - gap, cell, label,
                label.equals("Remove") ? Button.Tone.DANGER : Button.Tone.NORMAL, b -> act(label));
            String label2 = row2[i];
            addButton(x + i * (cell + gap), bottom, cell, label2,
                label2.equals("Build") ? Button.Tone.SUCCESS : Button.Tone.SECONDARY, b -> act(label2));
        }
    }

    private static List<FileEntry> listFiles() {
        List<FileEntry> entries = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (String file : Schematics.files()) {
            entries.add(new FileEntry(file, null));
            names.add(file.substring(file.lastIndexOf('/') + 1));
        }
        for (Path download : Schematics.downloads()) {
            String name = download.getFileName().toString();
            if (!names.contains(name)) {
                entries.add(new FileEntry(name, download));
            }
        }
        return entries;
    }

    private int panelWidth() {
        return Math.max(260, Math.min(520, screenWidth() - 40));
    }

    private void addButton(int x, int y, int w, String label, Button.Tone tone,
                           net.minecraft.client.gui.components.Button.OnPress press) {
        addRenderableWidget(new DihStyledButton(x, y, Math.max(1, w), BUTTON_H, Component.literal(label), tone, press));
    }

    private void act(String action) {
        if (action.equals("Close")) {
            onClose();
            return;
        }
        if (action.equals("Folder")) {
            java.io.File folder = Schematics.folder();
            folder.mkdirs();
            dihclient.util.DihLinks.openPath(folder.toPath());
            return;
        }
        SchematicPlacement p = Schematics.get().selected();
        if (p == null) {
            show(List.of("Pick a file on the left to place it, or select a placement on the right."), DihScreenPalette.MUTED);
            return;
        }
        switch (action) {
            case "Move" -> this.minecraft.gui.setScreen(new DihSchematicPlacementScreen(p, this));
            case "Rotate" -> {
                p.rotate(1);
                Schematics.get().changed();
            }
            case "Mirror" -> {
                p.cycleMirror();
                Schematics.get().changed();
            }
            case "Show / Hide" -> {
                p.setVisible(!p.visible());
                Schematics.get().changed();
            }
            case "Remove" -> {
                Schematics.get().remove(p);
                show(List.of("Removed " + p.name()), DihScreenPalette.MUTED);
            }
            case "Materials" -> show(SchematicActions.materials(this.minecraft, p, 8), DihScreenPalette.TEXT);
            case "Verify" -> {
                if (p.isLoaded()) {
                    SchematicVerifier.Counts c = SchematicActions.verify(this.minecraft, p);
                    show(List.of(c.correct() + " of " + c.total() + " positions right",
                        c.of(SchematicVerifier.Mismatch.MISSING) + " missing, " + c.of(SchematicVerifier.Mismatch.WRONG_BLOCK)
                            + " wrong block, " + c.of(SchematicVerifier.Mismatch.WRONG_STATE) + " wrong state, "
                            + c.of(SchematicVerifier.Mismatch.EXTRA) + " extra"), DihScreenPalette.TEXT);
                }
            }
            case "Build" -> show(List.of(SchematicActions.build(p)), DihScreenPalette.SUCCESS);
            default -> {
            }
        }
    }

    private void show(List<String> lines, int color) {
        this.details = List.copyOf(lines);
        this.detailsColor = color;
    }

    private void placeFile(FileEntry entry) {
        try {
            String file = entry.download() == null ? entry.name() : Schematics.importFile(entry.download());
            SchematicPlacement p = SchematicActions.place(this.minecraft, file, null);
            Module module = ModuleRegistry.get("schematics");
            if (module != null && !module.isEnabled()) {
                module.setEnabled(true);
            }
            this.minecraft.gui.setScreen(new DihSchematicPlacementScreen(p, this));
        } catch (IllegalArgumentException e) {
            show(List.of(e.getMessage()), DihScreenPalette.ERROR);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        MouseButtonEvent virtual = virtualEvent(event);
        if (virtual.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int[] r : this.fileRows) {
                if (inside(virtual, r) && r[4] < this.files.size()) {
                    placeFile(this.files.get(r[4]));
                    return true;
                }
            }
            List<SchematicPlacement> placements = Schematics.get().placements();
            for (int[] r : this.placementRows) {
                if (inside(virtual, r) && r[4] < placements.size()) {
                    Schematics.get().select(placements.get(r[4]));
                    show(List.of(SchematicActions.describe(placements.get(r[4]))), DihScreenPalette.MUTED);
                    return true;
                }
            }
        }
        return super.mouseClicked(virtual, doubled);
    }

    private static boolean inside(MouseButtonEvent e, int[] r) {
        return e.x() >= r[0] && e.x() < r[0] + r[2] && e.y() >= r[1] && e.y() < r[1] + r[3];
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int step = vertical < 0 ? 1 : -1;
        if (DihUiScale.toVirtual(mouseX) < screenWidth() / 2.0) {
            this.fileScroll = Math.max(0, this.fileScroll + step);
        } else {
            this.placementScroll = Math.max(0, this.placementScroll + step);
        }
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
            int accent = DihTheme.recolor(DihScreenPalette.SUCCESS, DihTheme.Channel.SUCCESS);
            int border = DihTheme.recolor(DihScreenPalette.BORDER, DihTheme.Channel.OUTLINE);
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()),
                DihTheme.recolor(DihScreenPalette.BG, DihTheme.Channel.BACKDROP) & 0xC0FFFFFF);
            UiRenderer.frame(graphics, UiBounds.of(x - 6, 14, w + 12, Math.max(1, screenHeight() - 20)),
                DihTheme.recolor(DihScreenPalette.PANEL_BG, DihTheme.Channel.BUTTON), border);
            UiText.draw(graphics, this.font, "Schematics", font, text, x, 22, false);
            UiText.drawEllipsized(graphics, this.font, Schematics.folder().toString(), font, muted, x + 70, 22, w - 70 - OPEN_W - 6, false);

            int colGap = 8;
            int colW = (w - colGap) / 2;
            int listBottom = screenHeight() - 28 - BUTTON_H * 2 - 8 - 4 * 11 - 6;
            UiText.drawEllipsized(graphics, this.font, "Click to place, or drop a file on the window", font, muted,
                x, TOP - 12, colW, false);
            UiText.draw(graphics, this.font, "Placements here", font, muted, x + colW + colGap, TOP - 12, false);

            this.fileRows.clear();
            int visible = Math.max(1, (listBottom - TOP) / ROW_H);
            this.fileScroll = Math.min(this.fileScroll, Math.max(0, this.files.size() - visible));
            if (this.files.isEmpty()) {
                UiText.drawEllipsized(graphics, this.font, "No files yet: click Open File... to pick a",
                    font, muted, x, TOP, colW, false);
                UiText.drawEllipsized(graphics, this.font, ".litematic, .schematic, .schem or .nbt from", font, muted, x, TOP + ROW_H, colW, false);
                UiText.drawEllipsized(graphics, this.font, "anywhere, or drag one onto the game window.", font, muted, x, TOP + ROW_H * 2, colW, false);
            }
            for (int i = this.fileScroll, y = TOP; i < this.files.size() && y + ROW_H <= listBottom; i++, y += ROW_H) {
                boolean hover = virtualMouseX >= x && virtualMouseX < x + colW && virtualMouseY >= y && virtualMouseY < y + ROW_H - 1;
                UiRenderer.rect(graphics, UiBounds.of(x, y, colW, ROW_H - 1), hover ? 0x33FFFFFF : 0x18000000);
                FileEntry entry = this.files.get(i);
                int tagW = 0;
                if (entry.download() != null) {
                    String tag = "Downloads";
                    tagW = UiText.width(this.font, tag, font, muted) + 6;
                    UiText.draw(graphics, this.font, tag, font, muted, x + colW - tagW + 2, y + 2, false);
                }
                UiText.drawEllipsized(graphics, this.font, entry.name(), font, text, x + 4, y + 2, colW - 8 - tagW, false);
                this.fileRows.add(new int[]{x, y, colW, ROW_H - 1, i});
            }

            this.placementRows.clear();
            List<SchematicPlacement> placements = Schematics.get().placements();
            SchematicPlacement selected = Schematics.get().selected();
            int px = x + colW + colGap;
            this.placementScroll = Math.min(this.placementScroll, Math.max(0, placements.size() - visible));
            if (placements.isEmpty()) {
                UiText.drawEllipsized(graphics, this.font, "None yet.", font, muted, px, TOP, colW, false);
            }
            for (int i = this.placementScroll, y = TOP; i < placements.size() && y + ROW_H <= listBottom; i++, y += ROW_H) {
                SchematicPlacement p = placements.get(i);
                boolean sel = p == selected;
                UiRenderer.rect(graphics, UiBounds.of(px, y, colW, ROW_H - 1), sel ? (accent & 0x00FFFFFF) | 0x33000000 : 0x18000000);
                if (sel) {
                    UiRenderer.rect(graphics, UiBounds.of(px, y, 2, ROW_H - 1), accent);
                }
                String label = (i + 1) + ". " + p.name() + (p.visible() ? "" : " (hidden)")
                    + (p.error().isEmpty() ? (p.isLoaded() ? "" : " (loading)") : " (failed)");
                UiText.drawEllipsized(graphics, this.font, label, font, sel ? accent : text, px + 6, y + 2, colW - 10, false);
                this.placementRows.add(new int[]{px, y, colW, ROW_H - 1, i});
            }

            List<String> lines = this.details.isEmpty() && selected != null
                ? List.of(SchematicActions.describe(selected)) : this.details;
            int dy = listBottom + 4;
            int color = DihTheme.recolor(this.detailsColor, DihTheme.Channel.TEXT);
            for (int i = 0; i < Math.min(4, lines.size()); i++) {
                UiText.drawEllipsized(graphics, this.font, lines.get(i), font, i == 0 ? color : muted, x, dy + i * 11, w, false);
            }
            super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
        } finally {
            DihUiScale.popOverlayScale(graphics);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
