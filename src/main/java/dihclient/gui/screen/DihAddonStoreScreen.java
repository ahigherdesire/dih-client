package dihclient.gui.screen;

import dihclient.addons.AddonManager;
import dihclient.addons.store.AddonStore;
import dihclient.addons.store.AddonStoreEntry;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.components.CompactOverlayButton;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.util.DihLinks;
import dihclient.util.DihUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dihclient.gui.screen.DihScreenPalette.*;

/**
 * Browse the reviewed addon registry (needs internet): a grid of image cards; clicking one opens a details dialog with
 * Install. Installs land in the mods folder and load after a restart.
 */
public final class DihAddonStoreScreen extends DihScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int[] ACCENTS = {0xFF67E8F9, 0xFF8BA6FF, 0xFFA855F7, 0xFF4ADE80, 0xFFFBBF24, 0xFFF472B6, 0xFFEF4444};
    private static final int INFO = 0xFF8EA0FF;
    private static final int CARD_BG = 0xFF17171B;
    private static final int CARD_BG_HOVER = 0xFF1E1E24;
    private static final int CARD_BORDER = 0x22FFFFFF;
    private static final int SOFT_TEXT = 0xFFC8C8CC;
    private static final int MAX_WIDTH = 780;
    private static final int MARGIN = 12;
    private static final int TOP_Y = 10;
    private static final int TOP_H = 44;
    private static final int CARD_MIN_W = 170;
    private static final int GAP = 10;
    private static final int CARD_TEXT_H = 60;
    private static final int LINE = 11;

    private final Screen parent;
    private final List<CompactOverlayButton> buttons = new ArrayList<>();
    private EditBox searchField;
    private String searchQuery = "";
    private enum Confirm { NONE, INSTALL, UNINSTALL }

    private String openId = "";
    private Confirm confirming = Confirm.NONE;
    private int scrollOffset;
    private int seenGeneration = -1;

    public DihAddonStoreScreen(Screen parent) {
        super(Component.literal("Browse Addons"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AddonStore.refreshIfStale();
        searchField = new EditBox(this.font, searchX(), TOP_Y + 14, searchWidth(), 16, Component.literal("Search"));
        searchField.setHint(Component.literal("Search addons..."));
        searchField.setMaxLength(64);
        searchField.setValue(searchQuery);
        searchField.setResponder(value -> {
            searchQuery = safeTrim(value);
            scrollOffset = 0;
        });
        addRenderableWidget(searchField);
        rebuildButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (seenGeneration != AddonStore.generation()) {
            seenGeneration = AddonStore.generation();
            if (open() == null) openId = "";
            rebuildButtons();
        }
    }

    // ---- layout ----

    private int contentWidth() { return Math.min(MAX_WIDTH, screenWidth() - MARGIN * 2); }
    private int contentX() { return (screenWidth() - contentWidth()) / 2; }
    private int gridTop() { return TOP_Y + TOP_H + 10; }
    private int gridBottom() { return screenHeight() - 8; }
    private int columns() { return Math.max(1, (contentWidth() + GAP) / (CARD_MIN_W + GAP)); }
    private int cardWidth() { return (contentWidth() - GAP * (columns() - 1)) / columns(); }
    private int imageHeight() { return cardWidth() * 9 / 16; }
    private int cardHeight() { return imageHeight() + CARD_TEXT_H; }
    private int searchX() { return contentX() + 190; }
    private int searchWidth() { return Math.max(120, contentWidth() - 190 - 250); }

    private int dialogWidth() { return Math.min(580, screenWidth() - 40); }
    private int dialogHeight() { return Math.min(350, screenHeight() - 30); }
    private int dialogX() { return (screenWidth() - dialogWidth()) / 2; }
    private int dialogY() { return (screenHeight() - dialogHeight()) / 2; }
    private int dialogImageWidth() { return dialogWidth() * 46 / 100; }

    // ---- buttons ----

    private void rebuildButtons() {
        buttons.clear();
        AddonStoreEntry entry = open();
        if (searchField != null) searchField.visible = entry == null;
        if (entry == null) {
            int right = contentX() + contentWidth();
            int y = TOP_Y + 13;
            buttons.add(CompactOverlayButton.create(right - 60, y, 60, 18, Component.literal("Back"),
                b -> this.minecraft.gui.setScreen(parent)).setVariant(CompactOverlayButton.Variant.SECONDARY));
            CompactOverlayButton refresh = CompactOverlayButton.create(right - 128, y, 62, 18, Component.literal("Refresh"),
                b -> AddonStore.refresh()).setVariant(CompactOverlayButton.Variant.SECONDARY);
            refresh.active = AddonStore.state() != AddonStore.State.LOADING;
            buttons.add(refresh);
            buttons.add(CompactOverlayButton.create(right - 238, y, 104, 18, Component.literal("Submit an addon"),
                b -> DihLinks.open(AddonStore.SUBMIT_URL)).setVariant(CompactOverlayButton.Variant.GHOST));
            return;
        }

        int x = dialogX() + 14;
        int w = dialogWidth() - 28;
        int y = dialogY() + dialogHeight() - 30;
        int small = 74;
        AddonStore.Status status = AddonStore.status(entry);
        boolean canUninstall = AddonStore.canUninstall(entry);
        // Installed: the main button uninstalls. Update available: Update is main and Uninstall sits beside it.
        boolean mainUninstalls = canUninstall && !status.installable();
        boolean extraUninstall = canUninstall && status.installable();
        int installW = w - (small + 8) * (extraUninstall ? 4 : 3);

        CompactOverlayButton main;
        if (mainUninstalls) {
            boolean sure = confirming == Confirm.UNINSTALL;
            main = CompactOverlayButton.create(x, y, installW, 18,
                Component.literal(sure ? "Click again to uninstall" : "Uninstall"), b -> onUninstall());
            main.setVariant(CompactOverlayButton.Variant.DANGER);
        } else {
            String label = confirming == Confirm.INSTALL ? "Click again to install"
                : status == AddonStore.Status.UPDATE ? "Update to v" + entry.version()
                : status == AddonStore.Status.REMOVED ? "Install again"
                : status.installable() ? "Install" : status.label();
            main = CompactOverlayButton.create(x, y, installW, 18, Component.literal(label), b -> onInstall());
            main.setVariant(confirming == Confirm.INSTALL ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.SUCCESS);
            main.active = status.installable();
        }
        buttons.add(main);
        int bx = x + installW + 8;
        if (extraUninstall) {
            buttons.add(CompactOverlayButton.create(bx, y, small, 18,
                Component.literal(confirming == Confirm.UNINSTALL ? "Sure?" : "Uninstall"), b -> onUninstall())
                .setVariant(CompactOverlayButton.Variant.DANGER));
            bx += small + 8;
        }
        buttons.add(CompactOverlayButton.create(bx, y, small, 18, Component.literal("Source"),
            b -> DihLinks.open(entry.source())).setVariant(CompactOverlayButton.Variant.SECONDARY));
        buttons.add(CompactOverlayButton.create(bx + small + 8, y, small, 18, Component.literal("Mods Folder"),
            b -> DihLinks.openPath(AddonManager.modsFolder().toPath())).setVariant(CompactOverlayButton.Variant.SECONDARY));
        buttons.add(CompactOverlayButton.create(bx + (small + 8) * 2, y, small, 18, Component.literal("Close"),
            b -> closeDialog()).setVariant(CompactOverlayButton.Variant.PRIMARY));
    }

    private void onInstall() {
        AddonStoreEntry entry = open();
        if (entry == null) return;
        if (confirming != Confirm.INSTALL) {
            confirming = Confirm.INSTALL;
        } else {
            confirming = Confirm.NONE;
            AddonStore.install(entry);
        }
        rebuildButtons();
    }

    private void onUninstall() {
        AddonStoreEntry entry = open();
        if (entry == null) return;
        if (confirming != Confirm.UNINSTALL) {
            confirming = Confirm.UNINSTALL;
        } else {
            confirming = Confirm.NONE;
            AddonStore.uninstall(entry);
        }
        rebuildButtons();
    }

    private void openDialog(AddonStoreEntry entry) {
        openId = entry.id();
        confirming = Confirm.NONE;
        if (searchField != null) searchField.setFocused(false);
        setFocused(null);
        rebuildButtons();
    }

    /** Dev screenshots only: opens the first listing's dialog. */
    public void openFirstForScreenshot() {
        List<AddonStoreEntry> list = filtered();
        if (!list.isEmpty()) openDialog(list.get(0));
    }

    /** Dev screenshots only: uninstalls the open listing (both clicks). */
    public void uninstallOpenForScreenshot() {
        onUninstall();
        onUninstall();
    }

    private void closeDialog() {
        openId = "";
        confirming = Confirm.NONE;
        rebuildButtons();
    }

    // ---- rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int mx = DihUiScale.toVirtualInt(mouseX);
        int my = DihUiScale.toVirtualInt(mouseY);
        boolean dialog = open() != null;
        DihUiScale.pushOverlayScale(graphics);
        try {
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), BG);
            renderTopBar(graphics);
            renderGrid(graphics, dialog ? -1 : mx, dialog ? -1 : my);
            if (!dialog) {
                for (CompactOverlayButton button : buttons) CompactOverlayButton.renderStyled(graphics, this.font, button, mx, my);
                super.extractRenderState(graphics, mx, my, delta);
            } else {
                UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), 0xC8000000);
                renderDialog(graphics, open());
                for (CompactOverlayButton button : buttons) CompactOverlayButton.renderStyled(graphics, this.font, button, mx, my);
            }
        } finally {
            DihUiScale.popOverlayScale(graphics);
        }
    }

    private void renderTopBar(GuiGraphicsExtractor graphics) {
        int x = contentX();
        drawPanel(graphics, x, TOP_Y, contentWidth(), TOP_H, PANEL_BG);
        UiRenderer.rect(graphics, UiBounds.of(x + 10, TOP_Y + 10, 3, TOP_H - 20), BORDER_ACTIVE);
        text(graphics, "Addon Browser", x + 20, TOP_Y + 11, TEXT, 160);
        text(graphics, summaryText(), x + 20, TOP_Y + 24, MUTED, 165);
    }

    private void renderGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<AddonStoreEntry> list = filtered();
        AddonStore.State state = AddonStore.state();
        if (AddonStore.entries().isEmpty() && (state == AddonStore.State.LOADING || state == AddonStore.State.IDLE)) {
            renderPlaceholders(graphics);
            return;
        }
        if (list.isEmpty()) {
            String message = switch (state) {
                case LOADING, IDLE -> "Loading the addon list...";
                case FAILED -> "Could not reach GitHub. Check your internet, then press Refresh.";
                case READY -> AddonStore.entries().isEmpty() ? "No addons listed yet. Made one? Press Submit an addon." : "No addons match the search.";
            };
            int w = width(message);
            text(graphics, message, (screenWidth() - w) / 2, gridTop() + 40, MUTED, w + 4);
            return;
        }
        clampScroll(list.size());
        DihUiScale.enableOverlayScissor(graphics, contentX() - 2, gridTop(), contentX() + contentWidth() + 2, gridBottom());
        try {
            for (int i = 0; i < list.size(); i++) {
                int col = i % columns();
                int row = i / columns();
                int x = contentX() + col * (cardWidth() + GAP);
                int y = gridTop() + row * (cardHeight() + GAP) - scrollOffset;
                if (y + cardHeight() < gridTop() || y > gridBottom()) continue;
                boolean hovered = mouseX >= x && mouseX < x + cardWidth() && mouseY >= Math.max(y, gridTop())
                    && mouseY < Math.min(y + cardHeight(), gridBottom());
                renderCard(graphics, list.get(i), x, y, hovered);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    /** Pulsing empty cards shown while the first list loads. */
    private void renderPlaceholders(GuiGraphicsExtractor graphics) {
        double phase = (Util.getMillis() % 1400L) / 1400.0;
        int alpha = 0x14 + (int) (0x14 * (0.5 + 0.5 * Math.sin(phase * Math.PI * 2)));
        int shade = (alpha << 24) | 0xFFFFFF;
        int count = Math.min(columns() * 2, Math.max(columns(), 4));
        for (int i = 0; i < count; i++) {
            int x = contentX() + (i % columns()) * (cardWidth() + GAP);
            int y = gridTop() + (i / columns()) * (cardHeight() + GAP);
            if (y + cardHeight() > gridBottom()) break;
            UiRenderer.rect(graphics, UiBounds.of(x, y, cardWidth(), cardHeight()), CARD_BG);
            UiRenderer.rect(graphics, UiBounds.of(x, y, cardWidth(), imageHeight()), shade);
            UiRenderer.rect(graphics, UiBounds.of(x + 8, y + imageHeight() + 9, cardWidth() * 3 / 5, 7), shade);
            UiRenderer.rect(graphics, UiBounds.of(x + 8, y + imageHeight() + 24, cardWidth() - 16, 6), shade);
            UiRenderer.rect(graphics, UiBounds.of(x + 8, y + imageHeight() + 35, cardWidth() * 2 / 3, 6), shade);
            UiRenderer.outline(graphics, UiBounds.of(x, y, cardWidth(), cardHeight()), CARD_BORDER);
        }
    }

    private void renderCard(GuiGraphicsExtractor graphics, AddonStoreEntry entry, int x, int y, boolean hovered) {
        int w = cardWidth();
        int imgH = imageHeight();
        int accent = accent(entry);
        UiRenderer.rect(graphics, UiBounds.of(x, y, w, cardHeight()), hovered ? CARD_BG_HOVER : CARD_BG);
        drawImage(graphics, entry, x, y, w, imgH);
        UiRenderer.outline(graphics, UiBounds.of(x, y, w, cardHeight()), hovered ? accent : CARD_BORDER);

        AddonStore.Status status = AddonStore.status(entry);
        int pillW = width(status.label()) + 12;
        UiRenderer.rect(graphics, UiBounds.of(x + w - pillW - 6, y + 6, pillW, 13), 0xD00E0E10);
        text(graphics, status.label(), x + w - pillW, y + 9, statusColor(status), pillW);

        int tx = x + 8;
        int ty = y + imgH + 7;
        int textW = w - 16;
        String version = "v" + entry.version();
        int versionW = width(version);
        int nameW = Math.min(width(entry.name()), textW - versionW - 6);
        text(graphics, entry.name(), tx, ty, TEXT, nameW);
        text(graphics, version, tx + nameW + 6, ty, MUTED, versionW + 2);
        int line = 0;
        for (String s : wrap(entry.summary(), textW, 2)) {
            text(graphics, s, tx, ty + 14 + line * LINE, SOFT_TEXT, textW);
            line++;
        }
        text(graphics, entry.author() + "  ·  MC " + entry.minecraftVersions(), tx, ty + 40, MUTED, textW);
    }

    /** The listing's image cropped to the box, or an accent banner with the addon's initials. */
    private void drawImage(GuiGraphicsExtractor graphics, AddonStoreEntry entry, int x, int y, int w, int h) {
        AddonStore.Image image = AddonStore.image(entry);
        if (image != null) {
            int regionW = image.width();
            int regionH = image.height();
            int u = 0;
            int v = 0;
            if ((long) regionW * h > (long) regionH * w) {
                int fit = (int) ((long) regionH * w / h);
                u = (regionW - fit) / 2;
                regionW = fit;
            } else {
                int fit = (int) ((long) regionW * h / w);
                v = (regionH - fit) / 2;
                regionH = fit;
            }
            graphics.blit(RenderPipelines.GUI_TEXTURED, image.id(), x, y, u, v, w, h, regionW, regionH, image.width(), image.height());
            return;
        }
        int accent = accent(entry);
        UiRenderer.rect(graphics, UiBounds.of(x, y, w, h), 0xFF101014);
        UiRenderer.rect(graphics, UiBounds.of(x, y, w, h), (accent & 0x00FFFFFF) | 0x30000000);
        UiRenderer.rect(graphics, UiBounds.of(x, y + h * 2 / 3, w, h - h * 2 / 3), (accent & 0x00FFFFFF) | 0x18000000);
        String initials = initials(entry.name());
        float scale = Math.max(2.0F, h / 28.0F);
        int tw = width(initials);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + w / 2.0F - tw * scale / 2.0F, y + h / 2.0F - 4.5F * scale);
        graphics.pose().scale(scale, scale);
        text(graphics, initials, 0, 0, accent, tw + 2);
        graphics.pose().popMatrix();
    }

    private void renderDialog(GuiGraphicsExtractor graphics, AddonStoreEntry entry) {
        int dx = dialogX();
        int dy = dialogY();
        int dw = dialogWidth();
        int dh = dialogHeight();
        int accent = accent(entry);
        UiRenderer.rect(graphics, UiBounds.of(dx, dy, dw, dh), 0xFF141417);
        UiRenderer.outline(graphics, UiBounds.of(dx, dy, dw, dh), (accent & 0x00FFFFFF) | 0xAA000000);
        UiRenderer.rect(graphics, UiBounds.of(dx, dy, dw, 3), accent);

        int imgW = dialogImageWidth();
        int imgH = imgW * 9 / 16;
        int lx = dx + 14;
        int ly = dy + 16;
        drawImage(graphics, entry, lx, ly, imgW, imgH);
        UiRenderer.outline(graphics, UiBounds.of(lx, ly, imgW, imgH), CARD_BORDER);

        int iy = ly + imgH + 10;
        AddonStoreEntry.AddonFile file = entry.fileFor(AddonStore.gameVersion());
        iy = info(graphics, lx, iy, imgW, "Version", "v" + entry.version());
        iy = info(graphics, lx, iy, imgW, "Minecraft", entry.minecraftVersions());
        iy = info(graphics, lx, iy, imgW, "Licence", entry.license());
        iy = info(graphics, lx, iy, imgW, "API", "v" + entry.apiVersion());
        if (file != null) {
            iy = info(graphics, lx, iy, imgW, "Download", size(file.size()));
            info(graphics, lx, iy, imgW, "SHA-256", file.sha256().substring(0, 16) + "...");
        }

        int rx = lx + imgW + 16;
        int rw = dx + dw - 14 - rx;
        int ry = ly;
        graphics.pose().pushMatrix();
        graphics.pose().translate(rx, ry);
        graphics.pose().scale(1.5F, 1.5F);
        text(graphics, entry.name(), 0, 0, TEXT, (int) (rw / 1.5F));
        graphics.pose().popMatrix();
        ry += 18;
        text(graphics, "by " + entry.author(), rx, ry, MUTED, rw);
        ry += 14;
        int cx = rx;
        for (String category : entry.categories()) {
            int w = width(category) + 10;
            if (cx + w > rx + rw) break;
            UiRenderer.rect(graphics, UiBounds.of(cx, ry, w, 12), (accent & 0x00FFFFFF) | 0x2A000000);
            text(graphics, category, cx + 5, ry + 2, accent, w);
            cx += w + 4;
        }
        if (!entry.categories().isEmpty()) ry += 18;
        text(graphics, entry.summary(), rx, ry, TEXT, rw);
        ry += 14;
        int maxLines = Math.max(2, (dy + dh - 92 - ry) / LINE);
        for (String s : wrap(entry.description(), rw, maxLines)) {
            text(graphics, s, rx, ry, SOFT_TEXT, rw);
            ry += LINE;
        }

        AddonStore.Status status = AddonStore.status(entry);
        String message = AddonStore.message(entry.id());
        String note = !message.isBlank() ? message
            : confirming == Confirm.UNINSTALL ? "Deletes its jar from your mods folder. Click again to uninstall."
            : switch (status) {
                case RESTART -> "Installed. Restart Minecraft to load it.";
                case INSTALLED -> "Installed and loaded.";
                case REMOVED -> "Uninstalled. It stays active until you close Minecraft, then its jar is deleted.";
                case INSTALLING -> "Downloading and checking the SHA-256...";
                case WRONG_MINECRAFT -> "No build for Minecraft " + AddonStore.gameVersion() + " yet (has " + entry.minecraftVersions() + ").";
                case NEEDS_NEWER_DIH -> "Needs a newer DIH Client. Update DIH first.";
                case FABRIC_ONLY -> "Store addons are Fabric mods. Use the Fabric build of DIH to install them.";
                default -> confirming == Confirm.INSTALL
                    ? "Addons run code inside your game. Click again to install."
                    : "Addons run code inside your game. Only install ones you trust.";
            };
        int color = !message.isBlank() ? ERROR : switch (status) {
            case RESTART, INSTALLED -> SUCCESS;
            case FAILED, WRONG_MINECRAFT, NEEDS_NEWER_DIH, FABRIC_ONLY -> ERROR;
            default -> WARN;
        };
        List<String> lines = wrap(note, rw - 16, 2);
        int boxH = lines.size() * LINE + 10;
        int boxY = dy + dh - 42 - boxH;
        UiRenderer.rect(graphics, UiBounds.of(rx, boxY, rw, boxH), (color & 0x00FFFFFF) | 0x1A000000);
        UiRenderer.rect(graphics, UiBounds.of(rx, boxY, 2, boxH), color);
        int ny = boxY + 5;
        for (String s : lines) {
            text(graphics, s, rx + 9, ny, color, rw - 16);
            ny += LINE;
        }
    }

    private int info(GuiGraphicsExtractor graphics, int x, int y, int width, String label, String value) {
        text(graphics, label, x, y, MUTED, 64);
        text(graphics, value, x + 66, y, TEXT, width - 66);
        return y + LINE + 1;
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent virtual = virtualEvent(event);
        if (virtual.button() != 0) return super.mouseClicked(virtual, doubleClick);
        for (CompactOverlayButton button : buttons) {
            if (CompactOverlayButton.fireIfHit(button, virtual.x(), virtual.y(), virtual.button())) return true;
        }
        if (open() != null) {
            boolean inside = virtual.x() >= dialogX() && virtual.x() < dialogX() + dialogWidth()
                && virtual.y() >= dialogY() && virtual.y() < dialogY() + dialogHeight();
            if (!inside) closeDialog();
            return true;
        }
        AddonStoreEntry card = cardAt(virtual.x(), virtual.y());
        if (card != null) {
            openDialog(card);
            return true;
        }
        return super.mouseClicked(virtual, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (open() != null && event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            closeDialog();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (open() != null) return true;
        scrollOffset -= (int) Math.signum(scrollY) * (cardHeight() + GAP) / 2;
        clampScroll(filtered().size());
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    private void clampScroll(int count) {
        int rows = (count + columns() - 1) / columns();
        int content = rows * (cardHeight() + GAP) - GAP;
        scrollOffset = Math.max(0, Math.min(Math.max(0, content - (gridBottom() - gridTop())), scrollOffset));
    }

    private AddonStoreEntry cardAt(double mouseX, double mouseY) {
        if (mouseY < gridTop() || mouseY >= gridBottom() || mouseX < contentX()) return null;
        int relX = (int) mouseX - contentX();
        int relY = (int) mouseY - gridTop() + scrollOffset;
        int col = relX / (cardWidth() + GAP);
        int row = relY / (cardHeight() + GAP);
        if (col >= columns() || relX % (cardWidth() + GAP) >= cardWidth() || relY % (cardHeight() + GAP) >= cardHeight()) return null;
        List<AddonStoreEntry> list = filtered();
        int index = row * columns() + col;
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    // ---- data ----

    private List<AddonStoreEntry> filtered() {
        List<AddonStoreEntry> out = new ArrayList<>();
        for (AddonStoreEntry entry : AddonStore.entries()) {
            if (entry.matches(searchQuery)) out.add(entry);
        }
        return out;
    }

    private AddonStoreEntry open() {
        if (openId.isEmpty()) return null;
        for (AddonStoreEntry entry : AddonStore.entries()) {
            if (entry.id().equals(openId)) return entry;
        }
        return null;
    }

    private String summaryText() {
        int count = AddonStore.entries().size();
        if (count == 0) {
            return switch (AddonStore.state()) {
                case LOADING, IDLE -> "Loading...";
                case FAILED -> "Offline";
                case READY -> "0 addons · MC " + AddonStore.gameVersion();
            };
        }
        String base = count + " addon(s) · MC " + AddonStore.gameVersion();
        return switch (AddonStore.state()) {
            case LOADING, IDLE -> base + " · updating";
            case FAILED -> count + " saved · offline";
            case READY -> base;
        };
    }

    private static int accent(AddonStoreEntry entry) {
        int h = entry.id().hashCode() * 0x9E3779B9;
        return ACCENTS[Math.floorMod(h ^ (h >>> 15), ACCENTS.length)];
    }

    private static int statusColor(AddonStore.Status status) {
        return switch (status) {
            case INSTALLED, RESTART -> SUCCESS;
            case FAILED, WRONG_MINECRAFT, NEEDS_NEWER_DIH, FABRIC_ONLY -> ERROR;
            case UPDATE, INSTALLING, REMOVED -> WARN;
            case AVAILABLE -> INFO;
        };
    }

    private static String initials(String name) {
        StringBuilder out = new StringBuilder();
        for (String word : name.split("[^A-Za-z0-9]+")) {
            if (!word.isEmpty() && out.length() < 2) out.append(Character.toUpperCase(word.charAt(0)));
        }
        return out.isEmpty() ? "?" : out.toString();
    }

    private static String size(long bytes) {
        return bytes >= 1048576
            ? String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
            : Math.max(1, Math.round(bytes / 1024.0)) + " KB";
    }

    private List<String> wrap(String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : (text == null ? "" : text).split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && width(next) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        if (lines.size() > maxLines) {
            List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
            cut.set(maxLines - 1, cut.get(maxLines - 1) + "...");
            return cut;
        }
        return lines;
    }

    private int width(String value) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        return UiText.width(this.font, value == null ? "" : value, fontId, TEXT);
    }

    private void text(GuiGraphicsExtractor graphics, String value, int x, int y, int color, int maxWidth) {
        UiText.drawEllipsized(graphics, this.font, value == null ? "" : value, THEME.fontFor(UiTone.BODY), color, x, y,
            Math.max(1, maxWidth), false);
    }
}
