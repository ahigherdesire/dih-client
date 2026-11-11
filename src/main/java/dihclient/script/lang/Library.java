package dihclient.script.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A set of built-in functions a script can call. Each entry carries its signature and a one-line
 * description so the editor and {@code .scripts functions} can list them.
 */
public final class Library {
    @FunctionalInterface
    public interface Fn {
        Object call(Args args);
    }

    public record Entry(String name, String category, String signature, String description, int minArgs, int maxArgs, Fn fn) { }

    /** Arguments handed to a built-in, with typed accessors that raise readable errors. */
    public static final class Args {
        private final Entry entry;
        private final List<Object> values;
        private final Interpreter interpreter;
        private final int line;

        Args(Entry entry, List<Object> values, Interpreter interpreter, int line) {
            this.entry = entry;
            this.values = values;
            this.interpreter = interpreter;
            this.line = line;
        }

        public int size() { return values.size(); }

        public boolean has(int i) { return i < values.size() && values.get(i) != null; }

        public Object get(int i) { return i < values.size() ? values.get(i) : null; }

        public List<Object> all() { return values; }

        public Interpreter interpreter() { return interpreter; }

        public int line() { return line; }

        public double number(int i) {
            Object v = get(i);
            if (v instanceof Double d) return d;
            throw error("argument " + (i + 1) + " must be a number, got " + Values.typeName(v));
        }

        public double number(int i, double fallback) {
            return has(i) ? number(i) : fallback;
        }

        public int integer(int i) {
            return (int) Math.floor(number(i));
        }

        public int integer(int i, int fallback) {
            return has(i) ? integer(i) : fallback;
        }

        public String string(int i) {
            Object v = get(i);
            if (v instanceof String s) return s;
            throw error("argument " + (i + 1) + " must be a string, got " + Values.typeName(v));
        }

        public String string(int i, String fallback) {
            return has(i) ? string(i) : fallback;
        }

        @SuppressWarnings("unchecked")
        public List<Object> list(int i) {
            Object v = get(i);
            if (v instanceof List<?> l) return (List<Object>) l;
            throw error("argument " + (i + 1) + " must be a list, got " + Values.typeName(v));
        }

        public ScriptException error(String message) {
            return new ScriptException(entry.name() + "(): " + message, line);
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public Library define(String category, String signature, String description, int minArgs, int maxArgs, Fn fn) {
        int paren = signature.indexOf('(');
        String name = paren < 0 ? signature : signature.substring(0, paren);
        entries.put(name, new Entry(name, category, signature, description, minArgs, maxArgs, fn));
        return this;
    }

    public Library include(Library other) {
        entries.putAll(other.entries);
        return this;
    }

    public Entry get(String name) {
        return entries.get(name);
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public List<Entry> byCategory(String category) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.category().equalsIgnoreCase(category)) out.add(e);
        return out;
    }

    public List<String> categories() {
        List<String> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (!out.contains(e.category())) out.add(e.category());
        }
        return out;
    }

    /** Closest built-in name, for "did you mean" hints. */
    public String suggest(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : entries.keySet()) {
            int d = distance(lower, candidate.toLowerCase(Locale.ROOT));
            if (d < bestDistance) {
                bestDistance = d;
                best = candidate;
            }
        }
        return bestDistance <= Math.max(2, name.length() / 3) ? best : null;
    }

    private static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
