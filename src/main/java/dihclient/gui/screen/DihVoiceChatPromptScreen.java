package dihclient.gui.screen;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContext;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.assets.UiAssets;
import dihclient.gui.vanillaui.components.Button;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.modules.DihModule;
import dihclient.util.DihTheme;
import dihclient.util.DihTheme.Channel;
import dihclient.util.DihUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class DihVoiceChatPromptScreen extends Screen {
    private static final Identifier FONT_TITLE = UiAssets.FONT_TITLE;
    private static final Identifier FONT_LABEL = UiAssets.FONT_LABEL;

    private static final int TITLE_COLOR = 0xFFFFF4F4;
    private static final int HEADER_COLOR = 0xFFFF4D4D;
    private static final int CARD_FILL = 0xF20A0A0C;
    private static final int CARD_BORDER = 0xFFFF4A4A;
    private static final int DIVIDER_COLOR = 0x66FF4A4A;

    private static final int PAD = 12;
    private static final int CARD_MAX_WIDTH = 340;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;

    private static final String TITLE_TEXT = "Simple Voice Chat detected";

    private static final String[][] SECTIONS = {
        {"Voice chat can't connect right now",
            "DIH is presenting itself as a vanilla client",
            "That hides the voicechat channel, so the mod can't connect"},
        {"Switch to Modded and here's what changes",
            "Voice chat starts working",
            "Servers can now tell you're on Fabric",
            "Kept: module hiding, channel filter, payload spoof",
            "Lost: the vanilla disguise. Not full protection anymore"},
    };

    private final Screen parent;
    private final CompactTheme theme = new CompactTheme();
    private final Btn keepVanilla = new Btn("Keep Vanilla", () -> choose(true));
    private final Btn switchModded = new Btn("Switch to Modded", () -> choose(false));

    private int layoutScreenWidth = -1;
    private int layoutScreenHeight = -1;
    private int wrappedWidth = -1;
    private List<List<String>> wrappedSections = List.of();

    public DihVoiceChatPromptScreen(Screen parent) {
        super(Component.literal(TITLE_TEXT));
        this.parent = parent;
    }

    private void choose(boolean keepVanillaMode) {
        DihModule module = DihModule.get();
        if (module != null) module.setSpoofClientVanilla(keepVanillaMode);
        this.minecraft.gui.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        float uiMouseX = (float) DihUiScale.toVirtual(mouseX);
        float uiMouseY = (float) DihUiScale.toVirtual(mouseY);
        layout();

        DihUiScale.pushOverlayScale(graphics);
        try {
            int screenW = DihUiScale.getVirtualScreenWidth();
            int screenH = DihUiScale.getVirtualScreenHeight();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenW, screenH), 0xC8000000);

            int cardW = cardWidth();
            int cardX = (screenW - cardW) / 2;
            int cardH = cardHeight();
            int cardY = Math.max(8, (screenH - cardH) / 2);
            int innerW = cardW - PAD * 2;
            int x = cardX + PAD;

            UiRenderer.rect(graphics, UiBounds.of(cardX, cardY, cardW, cardH), CARD_FILL);
            drawThickBorder(graphics, cardX, cardY, cardW, cardH, DihTheme.recolor(CARD_BORDER, Channel.OUTLINE), 2);

            int y = cardY + PAD + 2;
            drawCentered(graphics, TITLE_TEXT, FONT_TITLE, DihTheme.recolor(TITLE_COLOR, Channel.TEXT), x, innerW, y);
            y += UiText.fontHeight(FONT_TITLE) + 7;
            UiRenderer.rect(graphics, UiBounds.of(x, y, innerW, 1), DihTheme.recolor(DIVIDER_COLOR, Channel.OUTLINE));
            y += 8;

            int headerH = UiText.fontHeight(FONT_LABEL);
            int lineH = UiText.fontHeight(FONT_LABEL) + 3;
            int bodyColor = theme.color(dihclient.gui.vanillaui.components.UiTone.MUTED);
            List<List<String>> linesBySection = wrappedSections(innerW);
            for (int i = 0; i < SECTIONS.length; i++) {
                UiText.draw(graphics, this.font, SECTIONS[i][0], FONT_LABEL, DihTheme.recolor(HEADER_COLOR, Channel.ACCENT), x, y, false);
                y += headerH + 3;
                for (String line : linesBySection.get(i)) {
                    UiText.draw(graphics, this.font, line, FONT_LABEL, bodyColor, x, y, false);
                    y += lineH;
                }
                y += 8;
            }

            renderButton(graphics, keepVanilla, uiMouseX, uiMouseY, true);
            renderButton(graphics, switchModded, uiMouseX, uiMouseY, true);
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
        if (keepVanilla.contains(mx, my)) { keepVanilla.action.run(); return true; }
        if (switchModded.contains(mx, my)) { switchModded.action.run(); return true; }
        return false;
    }

    private int cardWidth() {
        int screenW = DihUiScale.getVirtualScreenWidth();
        return Math.max(1, Math.min(Math.max(1, screenW - 12), CARD_MAX_WIDTH));
    }

    private int cardHeight() {
        int innerW = Math.max(1, cardWidth() - PAD * 2);
        int headerH = UiText.fontHeight(FONT_LABEL);
        int lineH = UiText.fontHeight(FONT_LABEL) + 3;
        int h = PAD + UiText.fontHeight(FONT_TITLE) + 7 + 1 + 8;
        List<List<String>> linesBySection = wrappedSections(innerW);
        for (int i = 0; i < SECTIONS.length; i++) {
            h += headerH + 3 + linesBySection.get(i).size() * lineH + 8;
        }
        h += BUTTON_HEIGHT + PAD;
        return h;
    }

    private void layout() {
        int screenW = DihUiScale.getVirtualScreenWidth();
        int screenH = DihUiScale.getVirtualScreenHeight();
        if (layoutScreenWidth == screenW && layoutScreenHeight == screenH) return;
        layoutScreenWidth = screenW;
        layoutScreenHeight = screenH;
        int cardW = cardWidth();
        int cardX = (screenW - cardW) / 2;
        int cardH = cardHeight();
        int cardY = Math.max(8, (screenH - cardH) / 2);
        int innerW = Math.max(1, cardW - PAD * 2);
        int btnW = Math.max(1, (innerW - BUTTON_GAP) / 2);
        int btnY = cardY + cardH - PAD - BUTTON_HEIGHT;
        int bx = cardX + PAD;
        keepVanilla.set(bx, btnY, btnW, BUTTON_HEIGHT);
        switchModded.set(bx + btnW + BUTTON_GAP, btnY, cardX + cardW - PAD - (bx + btnW + BUTTON_GAP), BUTTON_HEIGHT);
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

    private List<List<String>> wrappedSections(int innerWidth) {
        if (wrappedWidth == innerWidth && wrappedSections.size() == SECTIONS.length) return wrappedSections;
        List<List<String>> next = new ArrayList<>(SECTIONS.length);
        for (String[] section : SECTIONS) {
            List<String> lines = new ArrayList<>();

            for (int b = 1; b < section.length; b++) lines.addAll(wrap("- " + section[b], innerWidth));
            next.add(List.copyOf(lines));
        }
        wrappedWidth = innerWidth;
        wrappedSections = List.copyOf(next);
        return wrappedSections;
    }

    private void drawCentered(GuiGraphicsExtractor graphics, String text, Identifier fontId, int color, int x, int width, int y) {
        int textW = UiText.width(this.font, text, fontId, color);
        UiText.draw(graphics, this.font, text, fontId, color, x + (width - textW) / 2, y, false);
    }

    private void renderButton(GuiGraphicsExtractor graphics, Btn btn, float mouseX, float mouseY, boolean enabled) {
        boolean hovered = enabled && btn.contains(mouseX, mouseY);
        UiContext ctx = UiContexts.overlay(graphics, this.font, (int) mouseX, (int) mouseY);
        UiBounds bounds = UiBounds.of(btn.x, btn.y, btn.w, btn.h);
        Button.render(ctx, bounds, btn.label, Button.Tone.SECONDARY, hovered, false);
        if (!enabled) {
            UiRenderer.rect(graphics, bounds, 0x66000000);
        }
    }

    private static void drawThickBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, int t) {
        if (width <= 0 || height <= 0) return;
        UiRenderer.rect(graphics, UiBounds.of(x, y, width, t), color);
        UiRenderer.rect(graphics, UiBounds.of(x, y + height - t, width, t), color);
        UiRenderer.rect(graphics, UiBounds.of(x, y, t, height), color);
        UiRenderer.rect(graphics, UiBounds.of(x + width - t, y, t, height), color);
    }

    private static final class Btn {
        private String label;
        private final Runnable action;
        private int x, y, w, h;

        private Btn(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }

        private void set(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }

        private boolean contains(float mx, float my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }
}
