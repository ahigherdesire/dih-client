package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.StringListSetting;
import dihclient.api.module.StringSetting;
import dihclient.commands.DihCommands;
import dihclient.mixin.accessor.DihChatComponentAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Chat quality of life. Ported from LiquidBounce's BetterChat and AntiSpam (GPL-3.0, CCBlueX): a longer history,
 * repeated messages stacked into one line with a count, regex filters, and a prefix, suffix or full-width text on what
 * you send. Added here: timestamps. LiquidBounce's anti-clear, copy and chat-after-death are not ported.
 */
public final class BetterChatModule extends Module {
    /** How many recent messages a repeat is matched against. */
    private static final int STACK_WINDOW = 50;

    private static BetterChatModule instance;

    private static final class Stacked {
        final String key;
        int count;
        GuiMessage message;

        Stacked(String key) {
            this.key = key;
        }
    }

    private final ArrayDeque<Stacked> stacks = new ArrayDeque<>();
    private String cachedFilterSource;
    private List<Pattern> cachedFilters = List.of();

    public BetterChatModule() {
        super("better-chat", "BetterChat", ModuleCategory.MISC, "Longer chat history, stacked repeats, filters and more.");
        add(new IntSetting("history", "History", 1000, 100, 20000, 100).description("Messages chat keeps (vanilla is 100)."));
        add(new BoolSetting("stack", "Stack Repeats", true).description("Merge repeated messages into one with a count."));
        add(new BoolSetting("timestamps", "Timestamps", false));
        add(new ChoiceSetting("timestamp-format", "Format", "HH:mm", "HH:mm", "HH:mm:ss")
            .visibleWhen(() -> bool("timestamps")));
        add(new StringListSetting("filters", "Filters", "")
            .description("Hide server messages matching any of these regular expressions."));
        add(new StringSetting("prefix", "Prefix", "").group("Outgoing").description("Put before what you send."));
        add(new StringSetting("suffix", "Suffix", "").group("Outgoing").description("Put after what you send."));
        add(new BoolSetting("unicode", "Full-width", false).group("Outgoing")
            .description("Send letters and digits as full-width characters."));
        instance = this;
    }

    @Override
    public void onDisable() {
        stacks.clear();
    }

    private static BetterChatModule active() {
        BetterChatModule chat = instance;
        return chat != null && chat.isEnabled() && !PackHideState.isActive() ? chat : null;
    }

    /** History cap for the chat lists. */
    public static int historyLimit(int vanilla) {
        BetterChatModule chat = active();
        return chat == null ? vanilla : Math.max(vanilla, chat.integer("history"));
    }

    /** Whether an incoming server message should be hidden by a filter. */
    public static boolean filtered(Component message) {
        BetterChatModule chat = active();
        if (chat == null || message == null) return false;
        String plain = message.getString();
        for (Pattern pattern : chat.filters()) {
            if (pattern.matcher(plain).find()) return true;
        }
        return false;
    }

    /** Whether an incoming message needs rewriting before it is shown. */
    public static boolean rewrites(boolean server) {
        BetterChatModule chat = active();
        return chat != null && (chat.bool("timestamps") || server && chat.bool("stack"));
    }

    /**
     * The message as it should be shown: a repeat takes the place of its earlier copy with a count, then a timestamp
     * goes in front. Call {@link #shown} with the stored line once it is added.
     */
    public static Component rewrite(ChatComponent component, Component message, boolean server) {
        BetterChatModule chat = active();
        if (chat == null) return message;
        MutableComponent out = message.copy();
        if (server && chat.bool("stack")) {
            Stacked stacked = chat.stackFor(component, message.getString());
            if (stacked.count > 1) {
                out.append(Component.literal(" [x" + stacked.count + "]").withStyle(ChatFormatting.GRAY));
            }
        }
        if (chat.bool("timestamps")) {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern(chat.choice("timestamp-format")));
            out = Component.literal("[" + time + "] ").withStyle(ChatFormatting.DARK_GRAY).append(out);
        }
        return out;
    }

    /** Records the line chat stored for the newest repeat, so the next repeat can replace it. */
    public static void shown(ChatComponent component) {
        BetterChatModule chat = instance;
        if (chat == null || chat.stacks.isEmpty()) return;
        Stacked newest = chat.stacks.peekLast();
        if (newest.message != null) return;
        List<GuiMessage> all = ((DihChatComponentAccessor) component).dih$getAllMessages();
        if (!all.isEmpty()) newest.message = all.getFirst();
    }

    private Stacked stackFor(ChatComponent component, String key) {
        Stacked found = null;
        for (Iterator<Stacked> it = stacks.iterator(); it.hasNext(); ) {
            Stacked stacked = it.next();
            if (stacked.key.equals(key)) {
                found = stacked;
                it.remove();
                break;
            }
        }
        if (found == null) {
            found = new Stacked(key);
        } else if (found.message != null) {
            DihChatComponentAccessor accessor = (DihChatComponentAccessor) component;
            GuiMessage old = found.message;
            if (accessor.dih$getAllMessages().removeIf(line -> line == old)) accessor.dih$refreshTrimmedMessages();
            else found.count = 0;
        }
        found.count++;
        found.message = null;
        stacks.addLast(found);
        while (stacks.size() > STACK_WINDOW) stacks.removeFirst();
        return found;
    }

    private List<Pattern> filters() {
        String source = value("filters");
        if (!source.equals(cachedFilterSource)) {
            List<Pattern> patterns = new ArrayList<>();
            for (String raw : list("filters")) {
                try {
                    patterns.add(Pattern.compile(raw));
                } catch (PatternSyntaxException ignored) {
                }
            }
            cachedFilters = List.copyOf(patterns);
            cachedFilterSource = source;
        }
        return cachedFilters;
    }

    /** What to send for a chat line you typed; DIH and Baritone commands pass through untouched. */
    public static String outgoing(String message) {
        BetterChatModule chat = active();
        if (chat == null || message == null || message.isBlank()) return message;
        if (DihCommands.isDihCommandMessage(message) || message.startsWith("#") || message.startsWith("/")) return message;
        String body = chat.bool("unicode") ? fullWidth(message) : message;
        String out = chat.text("prefix") + body + chat.text("suffix");
        // The server refuses chat lines over 256 characters.
        return out.length() > 256 ? out.substring(0, 256) : out;
    }

    /** ASCII letters, digits and symbols as their full-width forms. */
    static String fullWidth(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(c >= '!' && c <= '~' ? (char) (c + 0xFEE0) : c);
        }
        return out.toString();
    }
}
