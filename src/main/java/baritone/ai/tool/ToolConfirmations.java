package baritone.ai.tool;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dangerous tool calls typed in chat, waiting for their [confirm] click. Each click works once and only for a minute,
 * so an old chat line can't fire a world delete later.
 */
public final class ToolConfirmations {

    public static final long TTL_MILLIS = 60_000L;
    public static final int CAPACITY = 16;
    /** A click on [confirm] sends this "command"; the client intercepts it before it reaches the server. */
    private static final String CLICK_PREFIX = "dih-tool-confirm ";

    public record Pending(String tool, String args, long expiresAt) {
    }

    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();

    private ToolConfirmations() {
    }

    public static synchronized String add(String tool, String args, long now) {
        String id;
        do {
            id = Long.toString(ThreadLocalRandom.current().nextLong(0x100000000L, 0x1000000000L), 36);
        } while (PENDING.containsKey(id));
        PENDING.put(id, new Pending(tool, args == null ? "" : args, now + TTL_MILLIS));
        Iterator<String> oldest = PENDING.keySet().iterator();
        while (PENDING.size() > CAPACITY && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
        return id;
    }

    /** The pending call, removed; null if unknown, already used or expired. */
    public static synchronized Pending take(String id, long now) {
        Pending pending = id == null ? null : PENDING.remove(id);
        return pending == null || now > pending.expiresAt() ? null : pending;
    }

    public static synchronized void clear() {
        PENDING.clear();
    }

    public static String clickCommand(String id) {
        return CLICK_PREFIX + id;
    }

    /** The id in a clicked command, with or without a leading slash, or null if it isn't ours. */
    public static String idFromClick(String command) {
        if (command == null) {
            return null;
        }
        String text = command.trim();
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        if (!text.startsWith(CLICK_PREFIX)) {
            return null;
        }
        String id = text.substring(CLICK_PREFIX.length()).trim();
        return id.isEmpty() ? null : id;
    }
}
