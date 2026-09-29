package dihclient.gui.screen;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContext;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.assets.UiAssets;
import dihclient.gui.vanillaui.components.Button;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.modules.PackHideState;
import dihclient.util.DihLinks;
import dihclient.util.DihTheme;
import dihclient.util.DihTheme.Channel;
import dihclient.util.DihUiScale;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The "support DIH" card shown over the panorama the first time the title screen opens in each launch. It closes at
 * once (either button, Esc or Enter); nothing is locked behind it.
 */
public final class DihDonateScreen extends Screen {
    private static final Identifier FONT_TITLE = UiAssets.FONT_TITLE;
    private static final Identifier FONT_LABEL = UiAssets.FONT_LABEL;

    private static final int CARD_FILL = 0xF40B0C10;
    private static final int CARD_OUTLINE = 0x40FFFFFF;
    private static final int ACCENT = 0xFFFF4D4D; // the art palette's accent; DihTheme recolours it to the user's theme
    private static final int TITLE_COLOR = 0xFFFFF4F4;

    private static final int PAD = 14;
    private static final int STRIPE = 3;
    private static final int CARD_MAX_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;

    private static final String EYEBROW = "SUPPORT DIH";
    private static final String TITLE_TEXT = "Enjoying DIH Client?";
    private static final String[] PARAGRAPHS = {
        "DIH is free and open source, with no ads, and every feature ends up free for everyone. One person builds it in their spare time.",
        "If it saved you some grinding, a coffee helps keep the updates coming.",
    };
    private static final String PERK = "Give $20 or more and get previews of new advanced features 1–2 months early.";
    private static final int PERK_PAD = 6;

    private static boolean shownThisLaunch;

    private final Screen parent;
    private final CompactTheme theme = new CompactTheme();
    private final Btn coffee = new Btn("Buy me a coffee", this::donate);
    private final Btn later = new Btn("Not now", this::onClose);

    private int layoutWidth = -1;
    private int layoutHeight = -1;
    private int wrappedWidth = -1;
    private int perkWidth = -1;
    private List<String> perkLines = List.of();
    private List<List<String>> wrapped = List.of();

    private DihDonateScreen(Screen parent) {
        super(Component.literal(TITLE_TEXT));
        this.parent = parent;
    }

    /**
     * The screen to show in place of {@code title}: this card the first time in a launch, the title screen after
     * that. Never while the client is hidden (panic / pack hide) or under automated runs (game tests, dev tools).
     */
    public static Screen overTitle(Screen title) {
        if (shownThisLaunch || PackHideState.isActive() || automated()) return title;
        shownThisLaunch = true;
        return new DihDonateScreen(title);
    }

    /** Game tests, the audit run and dev capture tools: no welcome cards in those. */
    public static boolean automated() {
        return System.getProperty("fabric.client.gametest") != null
            || Boolean.getBoolean("dih.auditAndExit")
            || Boolean.getBoolean("dih.dev.showcase")
            || Boolean.getBoolean("dih.dev.screenshots");
    }

    private void donate() {
        DihLinks.open(DihLinks.DONATE);
        onClose();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(DihTour.after(parent));
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.minecraft.gameRenderer.panorama().extractRenderState(graphics, this.width, this.height);
        float uiMouseX = (float) DihUiScale.toVirtual(mouseX);
        float uiMouseY = (float) DihUiScale.toVirtual(mouseY);
        layout();

        DihUiScale.pushOverlayScale(graphics);
        try {
            int screenW = DihUiScale.getVirtualScreenWidth();
            int screenH = DihUiScale.getVirtualScreenHeight();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenW, screenH), 0x99000000);

            int cardW = cardWidth();
            int cardH = cardHeight();
            int cardX = (screenW - cardW) / 2;
            int cardY = Math.max(8, (screenH - cardH) / 2);
            int accent = DihTheme.recolor(ACCENT, Channel.ACCENT);

            UiRenderer.rect(graphics, UiBounds.of(cardX, cardY, cardW, cardH), CARD_FILL);
            outline(graphics, cardX, cardY, cardW, cardH, CARD_OUTLINE);
            UiRenderer.rect(graphics, UiBounds.of(cardX, cardY, STRIPE, cardH), accent);

            int x = cardX + PAD + STRIPE;
            int innerW = innerWidth();
            int y = cardY + PAD;
            UiText.draw(graphics, this.font, EYEBROW, FONT_LABEL, accent, x, y, false);
            y += UiText.fontHeight(FONT_LABEL) + 5;
            UiText.draw(graphics, this.font, TITLE_TEXT, FONT_TITLE, DihTheme.recolor(TITLE_COLOR, Channel.TEXT), x, y, false);
            y += UiText.fontHeight(FONT_TITLE) + 9;

            int body = theme.color(UiTone.MUTED);
            int lineH = UiText.fontHeight(FONT_LABEL) + 3;
            for (List<String> paragraph : wrapped(innerW)) {
                for (String line : paragraph) {
                    UiText.draw(graphics, this.font, line, FONT_LABEL, body, x, y, false);
                    y += lineH;
                }
                y += 6;
            }

            List<String> perk = wrappedPerk(innerW - PERK_PAD * 2);
            int perkH = perk.size() * lineH - 3 + PERK_PAD * 2;
            UiRenderer.rect(graphics, UiBounds.of(x, y, innerW, perkH), (accent & 0x00FFFFFF) | 0x26000000);
            UiRenderer.rect(graphics, UiBounds.of(x, y, 2, perkH), accent);
            int perkY = y + PERK_PAD;
            for (String line : perk) {
                UiText.draw(graphics, this.font, line, FONT_LABEL, DihTheme.recolor(TITLE_COLOR, Channel.TEXT), x + PERK_PAD, perkY, false);
                perkY += lineH;
            }

            UiContext ctx = UiContexts.overlay(graphics, this.font, (int) uiMouseX, (int) uiMouseY);
            boolean coffeeHovered = coffee.contains(uiMouseX, uiMouseY);
            Button.render(ctx, coffee.bounds(), coffee.label, Button.Tone.PRIMARY, coffeeHovered, false);
            UiRenderer.rect(graphics, coffee.bounds(), (accent & 0x00FFFFFF) | (coffeeHovered ? 0x55000000 : 0x38000000));
            Button.render(ctx, later.bounds(), later.label, Button.Tone.SECONDARY, later.contains(uiMouseX, uiMouseY), false);
        } finally {
            DihUiScale.popOverlayScale(graphics);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        float mx = (float) DihUiScale.toVirtual(event.x());
        float my = (float) DihUiScale.toVirtual(event.y());
        layout();
        if (coffee.contains(mx, my)) { coffee.action.run(); return true; }
        if (later.contains(mx, my)) { later.action.run(); return true; }
        return false;
    }

    private int cardWidth() {
        int screenW = DihUiScale.getVirtualScreenWidth();
        return Math.max(1, Math.min(Math.max(1, screenW - 12), CARD_MAX_WIDTH));
    }

    private int innerWidth() {
        return Math.max(1, cardWidth() - PAD * 2 - STRIPE);
    }

    private int cardHeight() {
        int lineH = UiText.fontHeight(FONT_LABEL) + 3;
        int h = PAD + UiText.fontHeight(FONT_LABEL) + 5 + UiText.fontHeight(FONT_TITLE) + 9;
        for (List<String> paragraph : wrapped(innerWidth())) h += paragraph.size() * lineH + 6;
        h += wrappedPerk(innerWidth() - PERK_PAD * 2).size() * lineH - 3 + PERK_PAD * 2 + 10;
        return h + BUTTON_HEIGHT + PAD;
    }

    private void layout() {
        int screenW = DihUiScale.getVirtualScreenWidth();
        int screenH = DihUiScale.getVirtualScreenHeight();
        if (layoutWidth == screenW && layoutHeight == screenH) return;
        layoutWidth = screenW;
        layoutHeight = screenH;
        int cardW = cardWidth();
        int cardH = cardHeight();
        int cardX = (screenW - cardW) / 2;
        int cardY = Math.max(8, (screenH - cardH) / 2);
        int x = cardX + PAD + STRIPE;
        int innerW = innerWidth();
        int btnY = cardY + cardH - PAD - BUTTON_HEIGHT;
        int laterW = Math.max(1, (innerW - BUTTON_GAP) * 2 / 5);
        int coffeeW = Math.max(1, innerW - BUTTON_GAP - laterW);
        coffee.set(x, btnY, coffeeW, BUTTON_HEIGHT);
        later.set(x + coffeeW + BUTTON_GAP, btnY, laterW, BUTTON_HEIGHT);
    }

    private List<List<String>> wrapped(int maxWidth) {
        if (wrappedWidth == maxWidth) return wrapped;
        List<List<String>> out = new ArrayList<>(PARAGRAPHS.length);
        for (String paragraph : PARAGRAPHS) out.add(wrap(paragraph, maxWidth));
        wrapped = List.copyOf(out);
        wrappedWidth = maxWidth;
        return wrapped;
    }

    private List<String> wrappedPerk(int maxWidth) {
        if (perkWidth != maxWidth) {
            perkLines = wrap(PERK, maxWidth);
            perkWidth = maxWidth;
        }
        return perkLines;
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
        return List.copyOf(lines);
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        UiRenderer.rect(graphics, UiBounds.of(x, y, w, 1), color);
        UiRenderer.rect(graphics, UiBounds.of(x, y + h - 1, w, 1), color);
        UiRenderer.rect(graphics, UiBounds.of(x, y, 1, h), color);
        UiRenderer.rect(graphics, UiBounds.of(x + w - 1, y, 1, h), color);
    }

    private static final class Btn {
        private final String label;
        private final Runnable action;
        private int x, y, w, h;

        private Btn(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }

        private void set(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }

        private UiBounds bounds() {
            return UiBounds.of(x, y, w, h);
        }

        private boolean contains(float mx, float my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }
}
