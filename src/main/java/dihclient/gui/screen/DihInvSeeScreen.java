package dihclient.gui.screen;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.UiScissorStack;
import dihclient.gui.vanillaui.components.CompactScreenPanel;
import dihclient.gui.vanillaui.components.CompactScrollbar;
import dihclient.gui.vanillaui.components.CompactSurfaces;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.gui.vanillaui.direct.DirectLayout;
import dihclient.util.DihEquipment;
import dihclient.util.DihUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code .invsee}: another player's armour and held items with durability and enchantments. Only shows; nothing here
 * clicks a slot or sends a packet, so nothing can be moved or taken. The server only tells clients what others wear
 * and hold, so their inventory itself cannot be seen.
 */
public final class DihInvSeeScreen extends DihScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int BG = 0xAA000000;
    private static final int TEXT = THEME.color(UiTone.BODY);
    private static final int MUTED = THEME.color(UiTone.MUTED);
    private static final int ENCHANT = 0xFFD9A6FF;
    private static final int HEADER_H = 26;
    private static final int PANEL_W = 300;
    private static final int PAD = 8;
    private static final int ROW_BASE_H = 26;
    private static final int LINE_H = 10;
    private static final int FOOTER_H = 14;

    private static final List<String> SLOT_NAMES = List.of("Main Hand", "Helmet", "Chestplate", "Leggings", "Boots", "Offhand");

    private final Player player;
    private final String name;
    private final ItemStack[] stacks = new ItemStack[DihEquipment.SLOTS.size()];
    private boolean stale;
    private int scroll;

    public DihInvSeeScreen(Player player) {
        super(Component.literal("Invsee"));
        this.player = player;
        this.name = player.getName().getString();
        for (int i = 0; i < stacks.length; i++) stacks[i] = ItemStack.EMPTY;
        refresh();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    /** Copies what the player has on now, or keeps the last copy once they leave render distance. */
    private void refresh() {
        stale = player.isRemoved() || minecraft != null && minecraft.level != null && player.level() != minecraft.level;
        if (stale) return;
        for (int i = 0; i < stacks.length; i++) {
            EquipmentSlot slot = DihEquipment.SLOTS.get(i);
            stacks[i] = player.getItemBySlot(slot).copy();
        }
    }

    private int panelW() {
        return DirectLayout.fitPanelDimension(screenWidth(), 4, PANEL_W);
    }

    private int panelH(int contentH) {
        return DirectLayout.fitPanelDimension(screenHeight(), 4, HEADER_H + PAD + contentH + FOOTER_H + PAD);
    }

    private Identifier body() {
        return THEME.fontFor(UiTone.BODY);
    }

    private List<String> enchantLines(ItemStack stack, int width) {
        List<String> names = DihEquipment.fullEnchantments(stack);
        if (names.isEmpty()) return List.of();
        return DihEquipment.wrapJoined(names, width, s -> UiText.width(font, s, body(), ENCHANT));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        refresh();
        int mx = DihUiScale.toVirtualInt(mouseX);
        int my = DihUiScale.toVirtualInt(mouseY);
        ItemStack hovered = null;
        DihUiScale.pushOverlayScale(graphics);
        try {
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), BG);
            int w = panelW();
            int rowW = w - PAD * 2 - CompactScrollbar.GUTTER;
            int textW = rowW - 32;

            List<List<String>> lines = new ArrayList<>(stacks.length);
            int contentH = 0;
            for (ItemStack stack : stacks) {
                List<String> enchants = stack.isEmpty() ? List.of() : enchantLines(stack, textW);
                lines.add(enchants);
                contentH += ROW_BASE_H + enchants.size() * LINE_H + 2;
            }

            int h = panelH(contentH);
            int x = DirectLayout.centerPanel(screenWidth(), w, 4);
            int y = DirectLayout.centerPanel(screenHeight(), h, 4);
            UiBounds bounds = UiBounds.of(x, y, w, h);
            CompactScreenPanel.render(UiContexts.overlay(graphics, font, mx, my), bounds, HEADER_H,
                name + (stale ? " (out of range)" : "") + " - view only",
                mx >= x && mx < x + w && my >= y && my < y + HEADER_H);
            if (w < 120 || h < HEADER_H + 40) return;

            int listX = x + PAD;
            int listY = y + HEADER_H + PAD;
            int listH = h - HEADER_H - PAD * 2 - FOOTER_H;
            int maxScroll = Math.max(0, contentH - listH);
            scroll = Math.max(0, Math.min(maxScroll, scroll));

            UiScissorStack.global().push(graphics, UiBounds.of(listX, listY, rowW, Math.max(0, listH)));
            try {
                int rowY = listY - scroll;
                for (int i = 0; i < stacks.length; i++) {
                    ItemStack stack = stacks[i];
                    List<String> enchants = lines.get(i);
                    int rowH = ROW_BASE_H + enchants.size() * LINE_H;
                    boolean visible = rowY + rowH > listY && rowY < listY + listH;
                    if (visible) {
                        boolean over = mx >= listX && mx < listX + rowW && my >= Math.max(rowY, listY)
                            && my < Math.min(rowY + rowH, listY + listH);
                        CompactSurfaces.row(graphics, listX, rowY, rowW, rowH, over, false);
                        drawRow(graphics, stack, SLOT_NAMES.get(i), enchants, listX, rowY, rowW);
                        if (over && !stack.isEmpty()) hovered = stack;
                    }
                    rowY += rowH + 2;
                }
            } finally {
                UiScissorStack.global().pop(graphics);
            }

            if (maxScroll > 0) {
                CompactScrollbar.Metrics metrics = CompactScrollbar.compute(contentH, listH,
                    listX + rowW, listY, CompactScrollbar.GUTTER, listH, scroll);
                CompactScrollbar.draw(graphics, metrics, metrics.contains(mx, my), false);
            }

            String footer = UiText.trimToWidth(font, "Servers share only armour and held items.", w - PAD * 2, body(), MUTED);
            UiText.draw(graphics, font, footer, body(), MUTED, listX, y + h - PAD - FOOTER_H + 4, false);
        } finally {
            DihUiScale.popOverlayScale(graphics);
        }
        if (hovered != null) graphics.setTooltipForNextFrame(font, hovered, mouseX, mouseY);
    }

    private void drawRow(GuiGraphicsExtractor graphics, ItemStack stack, String slotName, List<String> enchants,
                         int x, int y, int w) {
        int textX = x + 28;
        if (stack.isEmpty()) {
            UiText.draw(graphics, font, slotName, body(), MUTED, textX, y + 4, false);
            UiText.draw(graphics, font, "Empty", body(), MUTED, textX, y + 14, false);
            return;
        }
        graphics.item(stack, x + 6, y + 5);
        graphics.itemDecorations(font, stack, x + 6, y + 5);

        String durability = DihEquipment.durability(stack);
        int durabilityW = durability == null ? 0 : UiText.width(font, durability, body(), TEXT) + 6;
        String itemName = stack.getHoverName().getString();
        if (stack.getCount() > 1) itemName += " x" + stack.getCount();
        String shown = UiText.trimToWidthEllipsis(font, itemName, w - 34 - durabilityW, body(), TEXT);
        UiText.draw(graphics, font, shown, body(), TEXT, textX, y + 4, false);
        if (durability != null) {
            UiText.draw(graphics, font, durability, body(), 0xFF000000 | stack.getBarColor(),
                x + w - durabilityW, y + 4, false);
        }
        UiText.draw(graphics, font, slotName, body(), MUTED, textX, y + 14, false);
        int lineY = y + ROW_BASE_H - 2;
        for (String line : enchants) {
            UiText.draw(graphics, font, line, body(), ENCHANT, textX, lineY, false);
            lineY += LINE_H;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean allowBypass) {
        int mx = DihUiScale.toVirtualInt(event.x());
        int my = DihUiScale.toVirtualInt(event.y());
        int w = panelW();
        int x = DirectLayout.centerPanel(screenWidth(), w, 4);
        // The panel height depends on the rows; use the last frame's layout through the close button and bounds check.
        UiBounds bounds = lastBounds(w, x);
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && CompactScreenPanel.isOverClose(bounds, HEADER_H, mx, my)) {
            onClose();
            return true;
        }
        if (!bounds.contains(mx, my)) {
            onClose();
            return true;
        }
        return true;
    }

    private UiBounds lastBounds(int w, int x) {
        int contentH = 0;
        int textW = w - PAD * 2 - CompactScrollbar.GUTTER - 32;
        for (ItemStack stack : stacks) {
            contentH += ROW_BASE_H + (stack.isEmpty() ? 0 : enchantLines(stack, textW).size()) * LINE_H + 2;
        }
        int h = panelH(contentH);
        return UiBounds.of(x, DirectLayout.centerPanel(screenHeight(), h, 4), w, h);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.round(scrollY * LINE_H * 3);
        if (scroll < 0) scroll = 0;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE || minecraft != null && minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(null);
    }
}
