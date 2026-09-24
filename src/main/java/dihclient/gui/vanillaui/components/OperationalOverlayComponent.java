package dihclient.gui.vanillaui.components;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiComponent;
import dihclient.gui.vanillaui.UiContext;
import dihclient.gui.vanillaui.UiInputResult;
import dihclient.util.DihWindowLayout;
import dihclient.util.DihWindow;
import dihclient.util.IDihOverlay;

public final class OperationalOverlayComponent implements UiComponent {
    private final IDihOverlay overlay;
    private boolean renderSuppressed;
    private boolean hoverBlocked;
    private boolean inputSuppressed;

    public OperationalOverlayComponent(IDihOverlay overlay) {
        this.overlay = overlay;
    }

    public void setRenderSuppressed(boolean renderSuppressed) {
        this.renderSuppressed = renderSuppressed;
    }

    public void setHoverBlocked(boolean hoverBlocked) {
        this.hoverBlocked = hoverBlocked;
    }

    public void setInputSuppressed(boolean inputSuppressed) {
        this.inputSuppressed = inputSuppressed;
    }

    @Override
    public UiBounds bounds() {
        DihWindowLayout bounds = overlay.getBounds();
        return UiBounds.of(bounds.x, bounds.y, bounds.width,
            bounds.collapsed ? DihWindow.sharedHeaderHeight() : bounds.height);
    }

    @Override
    public void setBounds(UiBounds bounds) {
        if (bounds == null) return;
        DihWindowLayout current = overlay.getBounds();
        overlay.setBounds(new DihWindowLayout(bounds.x(), bounds.y(), bounds.width(), bounds.height(), current.visible, current.collapsed));
    }

    @Override
    public UiBounds hitBounds() {
        return inputSuppressed || !overlay.isVisible() ? null : bounds();
    }

    @Override
    public void render(UiContext context) {
        if (renderSuppressed || !overlay.isVisible()) return;
        long perfStart = dihclient.util.DihPerf.beginSampled();
        overlay.render(context.graphics(), hoverBlocked ? -10000 : context.mouseX(), hoverBlocked ? -10000 : context.mouseY(), context.delta());

        if (perfStart != 0L) dihclient.util.DihPerf.end("overlay." + overlay.getOverlayId(), perfStart);
    }

    @Override
    public UiInputResult mouseClicked(int mouseX, int mouseY, int button) {
        overlay.mouseClicked(mouseX, mouseY, button);
        return UiInputResult.HANDLED;
    }

    @Override
    public UiInputResult mouseReleased(int mouseX, int mouseY, int button) {
        return handled(overlay.mouseReleased(mouseX, mouseY, button));
    }

    @Override
    public UiInputResult mouseDragged(int mouseX, int mouseY, int button, double deltaX, double deltaY) {
        return handled(overlay.mouseDragged(mouseX, mouseY, button, deltaX, deltaY));
    }

    @Override
    public UiInputResult mouseScrolled(int mouseX, int mouseY, double amount) {
        overlay.mouseScrolled(mouseX, mouseY, amount);
        return UiInputResult.HANDLED;
    }

    @Override
    public UiInputResult keyPressed(int key, int scanCode, int modifiers) {
        return handled(overlay.keyPressed(key, scanCode, modifiers));
    }

    @Override
    public UiInputResult charTyped(char chr) {
        return charTyped(chr, 0);
    }

    public UiInputResult charTyped(char chr, int modifiers) {
        return handled(overlay.charTyped(chr, modifiers));
    }

    private static UiInputResult handled(boolean handled) {
        return handled ? UiInputResult.HANDLED : UiInputResult.IGNORED;
    }
}
