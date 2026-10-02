package dihclient.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContext;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.assets.UiAssets;
import dihclient.gui.vanillaui.components.Button;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.palette.DihCommandPalette;
import dihclient.util.DihBindUtil;
import dihclient.util.DihConfig;
import dihclient.util.DihTheme;
import dihclient.util.DihTheme.Channel;
import dihclient.util.DihUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** The tour's cards, one at a time, with Next and Skip. Enter is Next, Esc is Skip; the last card says Done. */
public final class DihTourScreen extends Screen {
    private static final Identifier FONT_TITLE = UiAssets.FONT_TITLE;
    private static final Identifier FONT_LABEL = UiAssets.FONT_LABEL;
    private static final int CARD_FILL = 0xF40B0C10;
    private static final int CARD_OUTLINE = 0x40FFFFFF;
    private static final int ACCENT = 0xFFFF4D4D;
    private static final int TITLE_COLOR = 0xFFFFF4F4;
    private static final int PAD = 14;
    private static final int STRIPE = 3;
    private static final int CARD_MAX_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;

    /** One card: a heading and a short paragraph. */
    public record Card(String title, String body) {
    }

    private final Screen parent;
    private final List<Card> cards;
    private final CompactTheme theme = new CompactTheme();
    private int index;
    private UiBounds nextBounds = UiBounds.of(0, 0, 0, 0);
    private UiBounds skipBounds = UiBounds.of(0, 0, 0, 0);

    DihTourScreen(Screen parent) {
        super(Component.literal("DIH tour"));
        this.parent = parent;
        this.cards = cards();
    }

    /** The cards, with the player's own keys filled in. */
    public static List<Card> cards() {
        DihConfig config = DihConfig.getGlobal();
        String menuKey = config == null ? "Right Shift" : DihBindUtil.getBindName(config.keybindModuleMenu);
        return List.of(
            new Card("Open the DIH menu",
                "Press " + menuKey + " any time to open the module menu. Click a module to turn it on, right-click it for its settings. Macros and Settings are in its Main Menu window."),
            new Card("Find anything with " + DihCommandPalette.shortcutLabel(),
                "Type a few letters of a module, setting, command or macro, then press Enter. It toggles, opens or runs what you picked."),
            new Card("Let DIH get items for you",
                "Type #acquire iron_pickaxe in chat (or any item). DIH gathers, crafts and smelts it, and fights off mobs on the way. #stop cancels."),
            new Card("AI assistant (coming soon)",
                "Next update: .ai setup connects your own AI model (or a local one), and .ai start <goal> plans and plays toward it."),
            new Card("Seed map",
                "#seedmap marks every structure from the world seed on the full-screen map, so you know where to go. It reads the seed itself in singleplayer; on servers, give it one with #seedinput."));
    }

    public int index() {
        return index;
    }

    public int cardCount() {
        return cards.size();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    /** Esc and Skip end the tour for good, like finishing it. */
    @Override
    public void onClose() {
        DihTour.complete();
        this.minecraft.gui.setScreen(parent);
    }

    public void next() {
        if (index + 1 >= cards.size()) {
            onClose();
            return;
        }
        index++;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            next();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        int mx = (int) DihUiScale.toVirtual(event.x());
        int my = (int) DihUiScale.toVirtual(event.y());
        if (nextBounds.contains(mx, my)) { next(); return true; }
        if (skipBounds.contains(mx, my)) { onClose(); return true; }
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft.level == null) this.minecraft.gameRenderer.panorama().extractRenderState(graphics, this.width, this.height);
        int uiMouseX = DihUiScale.toVirtualInt(mouseX);
        int uiMouseY = DihUiScale.toVirtualInt(mouseY);
        DihUiScale.pushOverlayScale(graphics);
        try {
            int screenW = DihUiScale.getVirtualScreenWidth();
            int screenH = DihUiScale.getVirtualScreenHeight();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenW, screenH), 0x99000000);

            Card card = cards.get(index);
            int cardW = Math.max(1, Math.min(screenW - 12, CARD_MAX_WIDTH));
            int innerW = Math.max(1, cardW - PAD * 2 - STRIPE);
            List<String> body = wrap(card.body(), innerW);
            int lineH = UiText.fontHeight(FONT_LABEL) + 3;
            int cardH = PAD + UiText.fontHeight(FONT_LABEL) + 5 + UiText.fontHeight(FONT_TITLE) + 9
                + body.size() * lineH + 10 + BUTTON_HEIGHT + PAD;
            int cardX = (screenW - cardW) / 2;
            int cardY = Math.max(8, (screenH - cardH) / 2);
            int accent = DihTheme.recolor(ACCENT, Channel.ACCENT);

            UiRenderer.rect(graphics, UiBounds.of(cardX, cardY, cardW, cardH), CARD_FILL);
            UiRenderer.outline(graphics, UiBounds.of(cardX, cardY, cardW, cardH), CARD_OUTLINE);
            UiRenderer.rect(graphics, UiBounds.of(cardX, cardY, STRIPE, cardH), accent);

            int x = cardX + PAD + STRIPE;
            int y = cardY + PAD;
            UiText.draw(graphics, this.font, "WELCOME TO DIH · " + (index + 1) + "/" + cards.size(), FONT_LABEL, accent, x, y, false);
            y += UiText.fontHeight(FONT_LABEL) + 5;
            UiText.draw(graphics, this.font, card.title(), FONT_TITLE, DihTheme.recolor(TITLE_COLOR, Channel.TEXT), x, y, false);
            y += UiText.fontHeight(FONT_TITLE) + 9;
            for (String line : body) {
                UiText.draw(graphics, this.font, line, FONT_LABEL, theme.color(UiTone.MUTED), x, y, false);
                y += lineH;
            }

            int btnY = cardY + cardH - PAD - BUTTON_HEIGHT;
            int skipW = Math.max(1, (innerW - BUTTON_GAP) * 2 / 5);
            int nextW = Math.max(1, innerW - BUTTON_GAP - skipW);
            nextBounds = UiBounds.of(x, btnY, nextW, BUTTON_HEIGHT);
            skipBounds = UiBounds.of(x + nextW + BUTTON_GAP, btnY, skipW, BUTTON_HEIGHT);
            UiContext ctx = UiContexts.overlay(graphics, this.font, uiMouseX, uiMouseY);
            boolean last = index + 1 >= cards.size();
            boolean nextHovered = nextBounds.contains(uiMouseX, uiMouseY);
            Button.render(ctx, nextBounds, last ? "Done" : "Next", Button.Tone.PRIMARY, nextHovered, false);
            UiRenderer.rect(graphics, nextBounds, (accent & 0x00FFFFFF) | (nextHovered ? 0x55000000 : 0x38000000));
            Button.render(ctx, skipBounds, last ? "Close" : "Skip tour", Button.Tone.SECONDARY, skipBounds.contains(uiMouseX, uiMouseY), false);
        } finally {
            DihUiScale.popOverlayScale(graphics);
        }
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (current.length() == 0 || UiText.width(this.font, candidate, FONT_LABEL, 0xFFFFFFFF) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }
}
