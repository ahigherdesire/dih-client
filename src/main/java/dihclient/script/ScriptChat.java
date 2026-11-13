package dihclient.script;

import dihclient.script.lang.Interpreter;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Recent chat that arrived from the server, for the script chat functions. Each running script
 * keeps its own read position per search text, so {@code chatSeen("tpa")} and
 * {@code chatSeen("hello")} don't use up each other's messages, and a script never sees chat
 * from before it started.
 */
public final class ScriptChat {
    static final int CAPACITY = 500;

    private record Line(long seq, long nanos, String text, String lower) {}

    private static final ArrayDeque<Line> LINES = new ArrayDeque<>();
    private static final Map<Interpreter, Map<String, Long>> CURSORS = new WeakHashMap<>();
    private static long nextSeq = 1;

    private ScriptChat() {
    }

    /** Called for every chat or system message the server sends (not the action bar). */
    public static synchronized void record(String text) {
        if (text == null) return;
        String clean = text.replaceAll("§.", "").strip();
        if (clean.isEmpty()) return;
        LINES.addLast(new Line(nextSeq++, System.nanoTime(), clean, clean.toLowerCase(Locale.ROOT)));
        while (LINES.size() > CAPACITY) LINES.removeFirst();
    }

    /**
     * The oldest message containing {@code needle} (ignoring capitals) that this script hasn't
     * already been given for that same text, or null. Marks it as read.
     */
    static synchronized String next(Interpreter it, String needle) {
        String key = needle.toLowerCase(Locale.ROOT);
        Map<String, Long> cursors = CURSORS.computeIfAbsent(it, k -> new HashMap<>());
        long after = cursors.getOrDefault(key, 0L);
        for (Line line : LINES) {
            if (line.seq <= after || line.nanos < it.startedNanos()) continue;
            if (line.lower.contains(key)) {
                cursors.put(key, line.seq);
                return line.text;
            }
        }
        return null;
    }

    /** The newest message since the script started, or null. */
    static synchronized String last(Interpreter it) {
        Line line = LINES.peekLast();
        return line == null || line.nanos < it.startedNanos() ? null : line.text;
    }

    static synchronized void clearForTests() {
        LINES.clear();
        CURSORS.clear();
    }
}
