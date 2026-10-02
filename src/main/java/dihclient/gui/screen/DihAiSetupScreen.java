package dihclient.gui.screen;

import baritone.ai.AiConfig;
import baritone.ai.AiProviders;
import baritone.ai.ConnectionCheck;
import dihclient.ai.ToolRunner;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.components.Button;
import dihclient.util.DihUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Provider setup for both the smart director and the older #ai chat command. */
public final class DihAiSetupScreen extends DihScreen {
    private final Screen parent;
    private final AiConfig saved;
    private final List<AiProviders.Provider> providers = AiProviders.all();
    private int selected;
    private EditBox url;
    private EditBox model;
    private EditBox key;
    private boolean reveal;
    private volatile String result = "";
    private volatile boolean checking;

    public DihAiSetupScreen(Screen parent) {
        super(Component.literal("AI Setup"));
        this.parent = parent;
        this.saved = ToolRunner.brain() == null ? new AiConfig() : ToolRunner.brain().getConfig();
        AiProviders.Provider match = AiProviders.forUrl(providers, saved.baseUrl);
        selected = match == null ? providers.size() - 1 : providers.indexOf(match);
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = left();
        int w = panelWidth();
        int columns = columns();
        int column = (w - 24 - (columns - 1) * GAP) / columns;
        for (int i = 0; i < providers.size(); i++) {
            final int index = i;
            AiProviders.Provider provider = providers.get(i);
            int px = x + 12 + (i % columns) * (column + GAP);
            int py = top() + GRID_TOP + (i / columns) * ROW;
            addButton(px, py, column, provider.name(), i == selected ? Button.Tone.SUCCESS : Button.Tone.SECONDARY,
                    () -> choose(index));
        }
        int fieldX = x + 12 + LABEL_W;
        int fieldW = w - 24 - LABEL_W;
        int y = fieldsTop();
        url = field(fieldX, y, fieldW, "Base URL", saved.baseUrl);
        model = field(fieldX, y + ROW, fieldW - 78, "Any model name", saved.model);
        addButton(fieldX + fieldW - 74, y + ROW, 74, "Next model", Button.Tone.SECONDARY, this::nextModel);
        key = field(fieldX, y + 2 * ROW, fieldW - 78, "API key", saved.apiKey);
        key.setMaxLength(4096);
        key.addFormatter((value, offset) -> FormattedCharSequence.forward(reveal ? value : "*".repeat(value.length()), Style.EMPTY));
        key.setResponder(this::keyChanged);
        addButton(fieldX + fieldW - 74, y + 2 * ROW, 74, "Show / Hide", Button.Tone.SECONDARY, () -> reveal = !reveal);
        int footer = footerTop();
        addButton(x + 12, footer, 70, "Test", Button.Tone.PRIMARY, this::test);
        addButton(x + 88, footer, 70, "Save", Button.Tone.SUCCESS, this::save);
        addButton(x + 164, footer, 70, "Back", Button.Tone.SECONDARY, this::onClose);
    }

    private EditBox field(int x, int y, int width, String hint, String value) {
        EditBox box = new EditBox(font, x, y, width, 19, Component.literal(hint));
        box.setHint(Component.literal(hint));
        box.setMaxLength(512);
        box.setValue(value == null ? "" : value);
        addRenderableWidget(box);
        return box;
    }

    private void addButton(int x, int y, int width, String label, Button.Tone tone, Runnable click) {
        addRenderableWidget(new DihStyledButton(x, y, width, 19, Component.literal(label), tone, b -> click.run()));
    }

    private void choose(int index) {
        selected = index;
        AiProviders.Provider provider = providers.get(index);
        if (!provider.baseUrl().isBlank()) url.setValue(provider.baseUrl());
        if (!provider.models().isEmpty()) model.setValue(provider.models().get(0).id());
        if (!provider.needsKey()) key.setValue("");
        result = provider.local() ? "Local model: no key needed." : "Use your own provider key.";
        // Rebuild the selected button style while preserving all three fields.
        String enteredUrl = url.getValue(), enteredModel = model.getValue(), enteredKey = key.getValue();
        init();
        url.setValue(enteredUrl);
        model.setValue(enteredModel);
        key.setValue(enteredKey);
    }

    /** A pasted key that says whose it is ("gsk_" is Groq's) picks that provider, URL and model with it. */
    private void keyChanged(String value) {
        AiProviders.Provider owner = AiProviders.forKey(providers, value);
        int index = owner == null ? -1 : providers.indexOf(owner);
        if (index < 0 || index == selected) return;
        choose(index);
        result = "That's " + owner.name() + "'s key: switched to " + owner.name() + ".";
    }

    private void nextModel() {
        List<AiProviders.Model> suggestions = providers.get(selected).models();
        if (suggestions.isEmpty()) return;
        int current = -1;
        for (int i = 0; i < suggestions.size(); i++) {
            if (suggestions.get(i).id().equals(model.getValue())) current = i;
        }
        model.setValue(suggestions.get((current + 1) % suggestions.size()).id());
    }

    private AiConfig candidate() {
        AiConfig draft = new AiConfig();
        draft.baseUrl = url.getValue().trim();
        draft.model = model.getValue().trim();
        draft.apiKey = key.getValue().trim();
        draft.extraBody = saved.extraBody;
        return draft;
    }

    private void test() {
        if (checking) return;
        AiConfig draft = candidate();
        checking = true;
        result = "Checking connection...";
        Thread worker = new Thread(() -> {
            ConnectionCheck.Result check = ConnectionCheck.run(draft);
            result = check.message();
            checking = false;
        }, "DIH AI connection check");
        worker.setDaemon(true);
        worker.start();
    }

    private void save() {
        AiConfig draft = candidate();
        if (draft.baseUrl.isBlank() || draft.model.isBlank()) {
            result = "Choose a provider and model first.";
            return;
        }
        saved.baseUrl = draft.baseUrl;
        saved.model = draft.model;
        saved.apiKey = draft.apiKey;
        saved.save();
        result = "Saved. The smart AI is ready when your provider accepts Test.";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int mx = DihUiScale.toVirtualInt(mouseX);
        int my = DihUiScale.toVirtualInt(mouseY);
        DihUiScale.pushOverlayScale(graphics);
        try {
            int x = left(), t = top(), w = panelWidth();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), DihScreenPalette.BG);
            UiRenderer.rect(graphics, UiBounds.of(x, t, w, panelHeight()), DihScreenPalette.PANEL_BG);
            graphics.text(font, "AI Setup", x + 12, t + 8, DihScreenPalette.TEXT, true);
            int y = fieldsTop();
            graphics.text(font, "URL", x + 12, y + 5, DihScreenPalette.MUTED, false);
            graphics.text(font, "Model", x + 12, y + ROW + 5, DihScreenPalette.MUTED, false);
            graphics.text(font, "Key", x + 12, y + 2 * ROW + 5, DihScreenPalette.MUTED, false);
            String mode = key != null && key.getValue().isBlank() && !providers.get(selected).local()
                    ? "No key: basic mode (simpler plans). Add a key for the smart AI."
                    : providers.get(selected).local() ? "Local model: free on this PC." : "Your key stays in your local ai.json.";
            graphics.text(font, font.plainSubstrByWidth(mode, w - 24), x + 12, y + 3 * ROW + 3, DihScreenPalette.MUTED, false);
            graphics.text(font, font.plainSubstrByWidth(result, w - 24), x + 12, y + 3 * ROW + 15, DihScreenPalette.TEXT, false);
            super.extractRenderState(graphics, mx, my, delta);
        } finally {
            DihUiScale.popOverlayScale(graphics);
        }
    }

    // Widgets sit in the DIH UI scale, so mouse events are moved into it first.
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(virtualEvent(event), doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(virtualEvent(event));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return super.mouseDragged(virtualEvent(event), DihUiScale.toVirtual(dx), DihUiScale.toVirtual(dy));
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    private static final int ROW = 22, GAP = 6, LABEL_W = 40, GRID_TOP = 22;

    private int panelWidth() { return Math.min(580, Math.max(300, screenWidth() - 20)); }
    /** Five provider columns when there's room, else three. */
    private int columns() { return panelWidth() >= 440 ? 5 : 3; }
    private int fieldsTop() { return top() + GRID_TOP + (providers.size() + columns() - 1) / columns() * ROW + 4; }
    private int footerTop() { return fieldsTop() + 3 * ROW + 30; }
    private int panelHeight() { return footerTop() + 19 + 8 - top(); }
    private int left() { return (screenWidth() - panelWidth()) / 2; }
    /** Centred, measured from the content: {@link #panelHeight} doesn't depend on it. */
    private int top() {
        int height = GRID_TOP + (providers.size() + columns() - 1) / columns() * ROW + 4 + 3 * ROW + 30 + 27;
        return Math.max(2, (screenHeight() - height) / 2);
    }
}
