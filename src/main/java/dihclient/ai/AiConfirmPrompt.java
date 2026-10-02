package dihclient.ai;

import baritone.ai.AiBrain;
import baritone.ai.director.Director;
import dihclient.util.DihAiPanelOverlay;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihNotifications;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * A run's dangerous step waiting for the player's OK. The AI panel has its own confirm / deny buttons; with the panel
 * closed the player gets a toast and a chat line with clickable [confirm] [deny] instead.
 */
public final class AiConfirmPrompt {

    public static final String CONFIRM = "dih-ai-confirm";
    public static final String DENY = "dih-ai-deny";

    private AiConfirmPrompt() {
    }

    /** Called on the game thread. */
    public static void show(String tool, String why) {
        if (DihAiPanelOverlay.isOpen()) return;
        DihNotifications.warning("AI wants to use " + tool + ": confirm in chat");
        DihClientMessaging.send(Component.empty()
                .append(DihClientMessaging.themedTag("DIH"))
                .append(DihClientMessaging.themedBody("§e" + tool + " needs your OK" + (why == null || why.isBlank() ? "" : " (" + why + ")") + ". "))
                .append(link("[confirm]", ChatFormatting.GREEN, CONFIRM, "Lets this one step run"))
                .append(Component.literal(" "))
                .append(link("[deny]", ChatFormatting.RED, DENY, "Stops the run")));
    }

    private static MutableComponent link(String label, ChatFormatting color, String command, String hover) {
        return Component.literal(label).withStyle(style -> style
                .withColor(color)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    /** A clicked [confirm] or [deny]; true when the click was ours (the caller keeps it from the server). */
    public static boolean handleClick(String command) {
        if (command == null) return false;
        String text = command.trim();
        if (text.startsWith("/")) text = text.substring(1);
        if (!text.equals(CONFIRM) && !text.equals(DENY)) return false;
        AiBrain brain = ToolRunner.brain();
        Director director = brain == null ? null : brain.director();
        if (director == null || !director.state().awaitingConfirm()) {
            DihClientMessaging.sendPrefixed("§7Nothing is waiting for your OK.");
        } else if (text.equals(CONFIRM)) {
            director.confirm();
        } else {
            director.stop("You denied " + DihAiPanelOverlay.currentTool(director.state()) + ".");
        }
        return true;
    }
}
