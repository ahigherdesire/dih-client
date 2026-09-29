package dihclient.util;

import baritone.ai.AiBrain;
import baritone.ai.AiProviders;
import baritone.ai.director.Director;
import baritone.ai.director.DirectorState;
import baritone.ai.director.RunStatus;
import baritone.guardian.GuardianLog;
import dihclient.ai.ToolRunner;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** A live, pinnable view of the director. It reads state on each render; it never blocks the game thread. */
public final class DihAiPanelOverlay extends DihOverlayBase {
    private static final String ID = "DihAiPanelOverlay";
    private static final int WIDTH = 300;
    private static final int HEIGHT = 258;
    private static final int MAX_STEPS = 6;
    private static final int TEXT = 0xFFF1F1F1, MUTED = 0xFFAAAAAA, ACCENT = 0xFF75DE9B, WARN = 0xFFFFC857;
    private static DihAiPanelOverlay instance;
    private final List<UiBounds> actions = new ArrayList<>();

    private DihAiPanelOverlay() {
        super(ID, WIDTH, HEIGHT);
        panelX = 12;
        panelY = 45;
        restoreLayout();
    }

    public static DihAiPanelOverlay get() {
        if (instance == null) instance = new DihAiPanelOverlay();
        DihOverlayManager.get().register(instance, OverlayScope.BACKGROUND_STATUS);
        return instance;
    }

    public static void open() {
        DihAiPanelOverlay panel = get();
        panel.setVisible(true);
        DihOverlayManager.get().bringToFront(panel);
    }

    public static void toggle() {
        DihAiPanelOverlay panel = get();
        panel.setVisible(!panel.isVisible());
        if (panel.isVisible()) DihOverlayManager.get().bringToFront(panel);
    }

    @Override public boolean supportsPinning() { return true; }
    @Override public int getMinWidth() { return WIDTH; }
    @Override public int getMinHeight() { return HEIGHT; }
    @Override public OverlayScope getDefaultOverlayScope() { return OverlayScope.BACKGROUND_STATUS; }
    @Override public boolean persistsAcrossScreenClose() { return true; }

    /** True while the panel is on screen (a waiting step then asks here rather than in chat). */
    public static boolean isOpen() {
        return instance != null && instance.isVisible();
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        DihWindowLayout bounds = getBounds();
        renderWindowFrame(graphics, mouseX, mouseY, bounds, "AI Run", collapsed, false);
        if (collapsed) return;
        boolean clip = beginWindowBodyClip(graphics, bounds, false);
        try {
            Font font = Minecraft.getInstance().font;
            AiBrain brain = ToolRunner.brain();
            Director director = brain == null ? null : brain.director();
            DirectorState state = director == null ? DirectorState.IDLE : director.state();
            int x = panelX + 8, y = panelY + HEADER_HEIGHT + 7;
            graphics.text(font, state.objective().isBlank() ? "No run yet: .ai start <objective>" : trim(state.objective(), 46),
                    x, y, TEXT, false);
            y += 15;
            String badge = state.basic() ? "Basic" : "Smart (" + (brain == null ? "" : brain.getConfig().model) + ")";
            graphics.text(font, trim(badge + "  " + state.status().id(), 46), x, y, ACCENT, false);
            y += 16;
            List<DirectorState.StepView> steps = state.steps();
            int first = Math.max(0, Math.min(state.current() - 2, steps.size() - MAX_STEPS));
            for (int i = first; i < steps.size() && i < first + MAX_STEPS; i++) {
                DirectorState.StepView step = steps.get(i);
                String mark = switch (step.status()) {
                    case DONE -> "✓ ";
                    case RUNNING -> "▶ ";
                    case FAILED -> "✗ ";
                    case PENDING -> i == state.current() && state.status().isActive() ? "▶ " : "· ";
                };
                graphics.text(font, trim(mark + step.tool() + (step.note().isBlank() ? "" : " - " + step.note()), 46),
                        x, y, i == state.current() ? TEXT : MUTED, false);
                y += 13;
            }
            y = panelY + panelHeight - 98;
            graphics.text(font, state.basic() ? "Basic mode: no tokens" : tokenLine(brain, state), x, y, MUTED, false);
            y += 14;
            for (String event : guardianEvents(brain)) {
                graphics.text(font, trim(event, 46), x, y, WARN, false);
                y += 11;
            }
            actions.clear();
            int buttonY = panelY + panelHeight - 21;
            String[] labels = {state.status() == RunStatus.PAUSED ? "Resume" : "Pause", "Stop", "Why?", "Confirm", "Deny"};
            for (int i = 0; i < labels.length; i++) {
                UiBounds box = UiBounds.of(panelX + 6 + i * 58, buttonY, 55, 15);
                actions.add(box);
                boolean enabled = i < 3 ? state.status() != RunStatus.IDLE : state.awaitingConfirm();
                int fill = i == 1 || i == 4 ? 0xFF68393E : 0xFF315A4A;
                UiRenderer.rect(graphics, box, enabled ? fill : 0x60303030);
                graphics.text(font, labels[i], box.x() + 4, box.y() + 3, enabled ? TEXT : MUTED, false);
            }
        } finally {
            endWindowBodyClip(graphics, clip);
        }
    }

    private static String tokenLine(AiBrain brain, DirectorState state) {
        String cost = brain == null ? null : AiProviders.cost(AiProviders.all(), brain.getConfig().baseUrl, brain.getConfig().model,
                state.promptTokens(), state.completionTokens());
        return (state.promptTokens() + state.completionTokens()) + " tokens" + (cost == null ? "" : "  ~" + cost);
    }

    private static List<String> guardianEvents(AiBrain brain) {
        List<String> out = new ArrayList<>();
        if (brain == null || brain.getBaritone().getGuardianProcess() == null) return out;
        List<GuardianLog.Event> recent = brain.getBaritone().getGuardianProcess().log().recent();
        for (int i = Math.max(0, recent.size() - 5); i < recent.size(); i++) out.add(recent.get(i).text());
        return out;
    }

    private static String trim(String text, int length) {
        return text.length() <= length ? text : text.substring(0, length - 1) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        if (isOverCloseButton(mouseX, mouseY, getBounds())) {
            setVisible(false);
            return true;
        }
        if (collapsed || button != 0) return isMouseOver(mouseX, mouseY);
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).contains((int) mouseX, (int) mouseY)) {
                press(i);
                return true;
            }
        }
        return isMouseOver(mouseX, mouseY);
    }

    /** Button {@code index}: pause-or-resume, stop, why, confirm, deny. */
    static void press(int index) {
        AiBrain brain = ToolRunner.brain();
        Director director = brain == null ? null : brain.director();
        if (director == null) return;
        DirectorState state = director.state();
        switch (index) {
            case 0 -> {
                if (state.status() == RunStatus.PAUSED) director.resume();
                else if (state.status().isActive()) director.pause("You paused it from the AI panel.");
            }
            case 1 -> director.stop("You stopped it from the AI panel.");
            case 2 -> DihClientMessaging.sendPrefixed("§7Why: §f" + why(state));
            case 3 -> { if (state.awaitingConfirm()) director.confirm(); }
            case 4 -> { if (state.awaitingConfirm()) director.stop("You denied " + currentTool(state) + "."); }
            default -> { }
        }
    }

    /** The director's reason for the current step, else the last thing it said. */
    static String why(DirectorState state) {
        int current = state.current();
        if (current >= 0 && current < state.steps().size()) {
            String reason = state.steps().get(current).reason();
            if (reason != null && !reason.isBlank()) return state.steps().get(current).tool() + ": " + reason;
        }
        return state.lastReason().isBlank() ? "no reason recorded yet." : state.lastReason();
    }

    public static String currentTool(DirectorState state) {
        int current = state.current();
        return current >= 0 && current < state.steps().size() ? state.steps().get(current).tool() : "the step";
    }

    @Override public boolean mouseReleased(double x, double y, int button) { return false; }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return false; }
    @Override public boolean mouseScrolled(double x, double y, double amount) { return false; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) { return false; }
    @Override public boolean charTyped(char chr, int modifiers) { return false; }
}
