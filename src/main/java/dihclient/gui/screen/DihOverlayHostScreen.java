package dihclient.gui.screen;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.components.Button;
import dihclient.util.DihItemNbtInspectOverlay;
import dihclient.util.DihOverlayManager;
import dihclient.util.IDihOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class DihOverlayHostScreen extends Screen {

    private static final int BACK_W = 200;
    private static final int BACK_H = 20;
    private static final int BACK_BOTTOM_MARGIN = 27;

    private final IDihOverlay tiedOverlay;
    private final Screen returnScreen;

    private final boolean showBackButton;

    private final boolean dismissOnClose;

    public DihOverlayHostScreen() {
        this(null, null, false);
    }

    public DihOverlayHostScreen(IDihOverlay tiedOverlay) {
        this(tiedOverlay, null, false);
    }

    public DihOverlayHostScreen(IDihOverlay tiedOverlay, Screen returnScreen) {
        this(tiedOverlay, returnScreen, false);
    }

    public DihOverlayHostScreen(IDihOverlay tiedOverlay, Screen returnScreen, boolean showBackButton) {
        this(tiedOverlay, returnScreen, showBackButton, false);
    }

    private boolean darkenBackground;

    private dihclient.util.DihHostScreenOverlays overlaySet;

    public DihOverlayHostScreen(IDihOverlay tiedOverlay, Screen returnScreen, boolean showBackButton, boolean dismissOnClose) {
        super(Component.literal("Dih Overlays"));
        this.tiedOverlay = tiedOverlay;
        this.returnScreen = returnScreen;
        this.showBackButton = showBackButton;
        this.dismissOnClose = dismissOnClose;
    }

    public DihOverlayHostScreen withDarkenedBackground() {
        this.darkenBackground = true;
        return this;
    }

    public boolean hostsOverlay(IDihOverlay overlay) {
        return overlay != null && tiedOverlay == overlay;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        if (overlaySet == null && !showBackButton && minecraft != null && minecraft.level != null) {
            dihclient.modules.DihModule module = dihclient.modules.DihModule.get();
            if (module != null && module.isActive()) {
                try {
                    overlaySet = dihclient.util.DihHostScreenOverlays.build(this.font);
                } catch (Throwable error) {
                    dihclient.DihClientAddon.LOG.warn("Host screen window set failed to build", error);
                }

                if (tiedOverlay != null) {
                    DihOverlayManager.get().register(tiedOverlay);
                    DihOverlayManager.get().bringToFront(tiedOverlay);
                }
            }
        }
    }

    @Override
    public void removed() {
        if (overlaySet != null) {
            try {
                overlaySet.saveAndClear();
            } catch (Throwable error) {
                dihclient.DihClientAddon.LOG.warn("Host screen window set failed to save", error);
            }
            overlaySet = null;
        }
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();

        if (tiedOverlay != null && !tiedOverlay.isVisible() && minecraft != null && minecraft.gui.screen() == this) {
            minecraft.gui.setScreen(returnScreen);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (darkenBackground) {

            dihclient.gui.vanillaui.UiRenderer.rect(graphics,
                dihclient.gui.vanillaui.UiBounds.of(0, 0, this.width, this.height), 0xC0101010);
        }
        DihOverlayManager.get().renderAll(graphics, mouseX, mouseY, delta);

        if (showBackButton && this.font != null) {
            int bw = Math.min(BACK_W, Math.max(120, this.width - 40));
            int bx = (this.width - bw) / 2;
            int by = this.height - BACK_BOTTOM_MARGIN;
            boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + BACK_H;
            Button.render(UiContexts.overlay(graphics, this.font, mouseX, mouseY),
                UiBounds.of(bx, by, bw, BACK_H), "Back", Button.Tone.NORMAL, hovered, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (showBackButton) {
            int bw = Math.min(BACK_W, Math.max(120, this.width - 40));
            int bx = (this.width - bw) / 2;
            int by = this.height - BACK_BOTTOM_MARGIN;
            if (event.x() >= bx && event.x() < bx + bw && event.y() >= by && event.y() < by + BACK_H) {
                goBack();
                return true;
            }
        }
        return DihOverlayManager.get().handleMouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return DihOverlayManager.get().handleMouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return DihOverlayManager.get().handleMouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return DihOverlayManager.get().handleMouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {

        boolean inventoryKey = minecraft != null && minecraft.options != null
            && minecraft.options.keyInventory != null
            && minecraft.options.keyInventory.matches(input);
        boolean closeKey = input.key() == GLFW.GLFW_KEY_ESCAPE || inventoryKey;

        if (closeKey && minecraft != null && !DihOverlayManager.get().isAnyTextFieldFocused()) {
            goBack();
            return true;
        }

        if (DihOverlayManager.get().handleKeyPressed(input.key(), input.scancode(), input.modifiers())) {
            return true;
        }
        if (closeKey && minecraft != null) {
            goBack();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return DihOverlayManager.get().handleCharTyped((char) input.codepoint(), 0);
    }

    private void goBack() {

        if (tiedOverlay != null && (showBackButton || dismissOnClose)) tiedOverlay.setVisible(false);
        if (dismissOnClose) {
            if (tiedOverlay != null) DihOverlayManager.get().unregister(tiedOverlay);

            DihItemNbtInspectOverlay.dismissShared();
        }
        if (minecraft != null) minecraft.gui.setScreen(returnScreen);
    }
}
