package dihclient.gui.screen;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.components.Button;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.util.DihPacketSelectorOverlay;
import dihclient.util.DihTheme;
import dihclient.util.DihTheme.Channel;
import dihclient.util.DihUiScale;
import dihclient.util.multi.MultiManager;
import dihclient.util.multi.MultiManualPackets;
import dihclient.util.multi.MultiQuickAction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static dihclient.gui.screen.DihScreenPalette.BG;
import static dihclient.gui.screen.DihScreenPalette.BORDER;
import static dihclient.gui.screen.DihScreenPalette.ERROR;
import static dihclient.gui.screen.DihScreenPalette.MUTED;
import static dihclient.gui.screen.DihScreenPalette.PANEL_BG;
import static dihclient.gui.screen.DihScreenPalette.SUCCESS;
import static dihclient.gui.screen.DihScreenPalette.TEXT;

public final class DihMultiQuickActionScreen extends DihScreen {
    private static final CompactTheme THEME = new CompactTheme();

    private final Screen parent;
    private final int slot;
    private final Consumer<MultiQuickAction> save;
    private final Function<MultiQuickAction, MultiManager.BroadcastResult> test;
    private final String initialName;
    private final List<String[]> editSteps = new ArrayList<>();
    private final List<EditBox> argFields = new ArrayList<>();

    private EditBox nameField;
    private DihPacketSelectorOverlay selector;
    private String status = "";
    private int statusColor = MUTED;

    public DihMultiQuickActionScreen(Screen parent, int slot, MultiQuickAction action,
                                        Consumer<MultiQuickAction> save,
                                        Function<MultiQuickAction, MultiManager.BroadcastResult> test) {
        super(Component.literal("Quick Action"));
        this.parent = parent;
        this.slot = slot;
        this.save = save;
        this.test = test;
        this.initialName = action == null ? "" : action.name;
        if (action != null) {
            for (MultiQuickAction.Step step : action.steps) editSteps.add(new String[]{step.packetClass(), step.arguments()});
        }
        if (editSteps.isEmpty()) editSteps.add(new String[]{"", ""});
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        String name = nameField == null ? initialName : nameField.getValue();
        captureArgs();
        clearWidgets();
        argFields.clear();
        int w = panelWidth();
        int x = (screenWidth() - w) / 2;

        nameField = new EditBox(font, x + 14, 40, w - 28, 18, Component.literal("Name"));
        nameField.setMaxLength(32);
        nameField.setHint(Component.literal("Preset name (optional)"));
        nameField.setValue(name);
        addRenderableWidget(nameField);

        int y = 78;
        for (int i = 0; i < editSteps.size(); i++) {
            final int index = i;
            String[] step = editSteps.get(i);
            String packetLabel = step[0].isBlank() ? "Choose packet" : MultiQuickAction.shortLabel(step[0]);
            addStyled(x + 14, y, 150, 18, packetLabel, step[0].isBlank() ? Button.Tone.PRIMARY : Button.Tone.NORMAL,
                b -> openPacketSelector(index));
            EditBox args = new EditBox(font, x + 170, y, w - 170 - 14 - 28, 18, Component.literal("Args"));
            args.setMaxLength(2048);
            args.setHint(Component.literal("packet args"));
            args.setValue(step[1]);
            addRenderableWidget(args);
            argFields.add(args);
            addStyled(x + w - 14 - 22, y, 22, 18, "X", Button.Tone.DANGER, b -> removeStep(index));
            y += 20;
        }
        if (editSteps.size() < MultiQuickAction.MAX_STEPS) {
            addStyled(x + 14, y, 110, 18, "+ Add packet", Button.Tone.NORMAL, b -> addStep());
        }

        int by = screenHeight() - 34;
        int gap = 4;
        int each = Math.max(1, (w - 28 - gap * 3) / 4);
        int fx = x + 14;
        addStyled(fx, by, each, 18, "Save", Button.Tone.SUCCESS, b -> saveDraft());
        addStyled(fx + each + gap, by, each, 18, "Test", Button.Tone.PRIMARY, b -> testDraft());
        addStyled(fx + (each + gap) * 2, by, each, 18, "Clear", Button.Tone.DANGER, b -> clearDraft());
        addStyled(fx + (each + gap) * 3, by, w - 14 - (fx + (each + gap) * 3 - x), 18,
            "Cancel", Button.Tone.NORMAL, b -> onClose());
    }

    private void captureArgs() {
        for (int i = 0; i < argFields.size() && i < editSteps.size(); i++) {
            editSteps.get(i)[1] = argFields.get(i).getValue();
        }
    }

    private void openPacketSelector(int index) {
        if (selector == null) selector = new DihPacketSelectorOverlay(font);

        selector.openC2S(packetClass -> {
            editSteps.get(index)[0] = packetClass.getName();
            if ((nameField == null || nameField.getValue().isBlank())) {
                String label = MultiQuickAction.shortLabel(packetClass.getName());
                if (nameField != null) nameField.setValue(label);
            }
            status = "Selected";
            statusColor = SUCCESS;
            rebuild();
        }, MultiManualPackets.unsafeC2S(), true);
    }

    private void addStep() {
        captureArgs();
        if (editSteps.size() < MultiQuickAction.MAX_STEPS) editSteps.add(new String[]{"", ""});
        rebuild();
    }

    private void removeStep(int index) {
        captureArgs();
        if (index >= 0 && index < editSteps.size()) editSteps.remove(index);
        if (editSteps.isEmpty()) editSteps.add(new String[]{"", ""});
        rebuild();
    }

    private MultiQuickAction buildAction() {
        captureArgs();
        MultiQuickAction action = new MultiQuickAction();
        action.name = nameField == null ? "" : nameField.getValue();
        for (String[] step : editSteps) {
            if (!step[0].isBlank()) action.steps.add(new MultiQuickAction.Step(step[0], step[1]));
        }
        action.normalize();
        return action;
    }

    private void saveDraft() {
        if (save != null) save.accept(buildAction());
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void clearDraft() {
        if (save != null) save.accept(new MultiQuickAction());
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void testDraft() {
        MultiQuickAction action = buildAction();
        if (action.empty()) {
            status = "Add a packet first";
            statusColor = MUTED;
            return;
        }
        MultiManager.BroadcastResult result = test == null ? null : test.apply(action);
        status = shortResult(result);
        statusColor = result == null || result.failed() > 0 ? ERROR : result.skipped() > 0 ? MUTED : SUCCESS;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = DihUiScale.toVirtualInt(mouseX);
        int virtualMouseY = DihUiScale.toVirtualInt(mouseY);
        DihUiScale.pushOverlayScale(graphics);
        try {
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), DihTheme.recolor(BG, Channel.BACKDROP));
            int w = panelWidth();
            int x = (screenWidth() - w) / 2;
            UiRenderer.frame(graphics, UiBounds.of(x, 24, w, screenHeight() - 48),
                DihTheme.recolor(PANEL_BG, Channel.BUTTON), DihTheme.recolor(BORDER, Channel.OUTLINE));
            drawText(graphics, "Edit Slot " + (slot + 1), x + 14, 30, DihTheme.recolor(TEXT, Channel.TEXT));
            drawText(graphics, "Packets sent together (" + packetCount() + "/" + MultiQuickAction.MAX_STEPS + ")",
                x + 14, 66, DihTheme.recolor(MUTED, Channel.TEXT));
            if (!status.isBlank()) drawFitted(graphics, status, x + 180, 66, Math.max(1, w - 194), themeStatusColor(statusColor));
            super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
            if (selector != null && selector.isVisible()) selector.render(graphics, virtualMouseX, virtualMouseY, delta);
        } finally {
            DihUiScale.popOverlayScale(graphics);
        }
    }

    private int packetCount() {
        int count = 0;
        for (String[] step : editSteps) {
            if (!step[0].isBlank()) count++;
        }
        return count;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selector != null && selector.isVisible()) {
            selector.keyPressed(event.key(), event.scancode(), event.modifiers());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (selector != null && selector.isVisible()) {
            selector.charTyped((char) event.codepoint(), 0);
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (selector != null && selector.isVisible()) {
            selector.mouseClicked((float) virtualEvent.x(), (float) virtualEvent.y(), virtualEvent.button());
            return true;
        }
        return super.mouseClicked(virtualEvent, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (selector != null && selector.isVisible()) {
            selector.mouseReleased((float) virtualEvent.x(), (float) virtualEvent.y(), virtualEvent.button());
            return true;
        }
        return super.mouseReleased(virtualEvent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (selector != null && selector.isVisible()) {
            selector.mouseDragged((float) virtualEvent.x(), (float) virtualEvent.y(), virtualEvent.button(), (float) DihUiScale.toVirtual(dragX), (float) DihUiScale.toVirtual(dragY));
            return true;
        }
        return super.mouseDragged(virtualEvent, DihUiScale.toVirtual(dragX), DihUiScale.toVirtual(dragY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double vx = DihUiScale.toVirtual(mouseX);
        double vy = DihUiScale.toVirtual(mouseY);
        if (selector != null && selector.isVisible()) {
            selector.mouseScrolled((float) vx, (float) vy, vertical);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private DihStyledButton addStyled(int x, int y, int w, int h, String text, Button.Tone tone,
                                         net.minecraft.client.gui.components.Button.OnPress press) {
        Button.Tone interactiveTone = tone == Button.Tone.NORMAL ? Button.Tone.SECONDARY : tone;
        DihStyledButton button = new DihStyledButton(x, y, Math.max(1, w), h,
            Component.literal(fitLabel(text, w - 8)), interactiveTone, press);
        addRenderableWidget(button);
        return button;
    }

    private String fitLabel(String text, int width) {
        return UiText.trimToWidthEllipsis(font, MultiManager.singleLine(text, 64), Math.max(1, width),
            THEME.fontFor(UiTone.BODY), DihTheme.recolor(TEXT, Channel.TEXT));
    }

    private int panelWidth() {
        return Math.max(1, Math.min(560, screenWidth() - 40));
    }

    private void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int width, int color) {
        graphics.text(font, Component.literal(fitLabel(text, width)), x, y, color, false);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        UiText.draw(graphics, font, MultiManager.singleLine(text, 140), fontId, color, x, y, false);
    }

    private static String shortResult(MultiManager.BroadcastResult result) {
        if (result == null) return "Failed";
        if (result.failed() > 0) return "Failed";
        if (result.sent() > 0 && result.skipped() == 0) return "Sent";
        if (result.sent() > 0) return "Sent " + result.sent();
        if (result.skipped() > 0) return "Skipped";
        return "Failed";
    }

    private static int themeStatusColor(int color) {
        if (color == SUCCESS) return DihTheme.recolor(SUCCESS, Channel.SUCCESS);
        if (color == ERROR) return DihTheme.recolor(ERROR, Channel.DANGER);
        return DihTheme.recolor(MUTED, Channel.TEXT);
    }
}
