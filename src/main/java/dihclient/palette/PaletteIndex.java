package dihclient.palette;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The search behind the Ctrl+K palette, with no Minecraft types so it can be unit tested. Matches rank as
 * exact title, then title prefix, then the start of a word in the title, then keyword prefix, then substring, then
 * the query's letters in order (so "klra" finds KillAura), then the same with one typed letter ignored (a typo).
 * Recently run entries get a boost.
 */
public final class PaletteIndex {

    public enum Kind {
        MODULE("Module"), SETTING("Setting"), DOT_COMMAND("Command"), HASH_COMMAND("Baritone"), MACRO("Macro"), AI_TOOL("AI tool");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** One searchable thing. {@code action} identifies what to do, e.g. {@code module:killaura}. */
    public record Entry(Kind kind, String title, List<String> keywords, String description, String action) {
        public Entry {
            keywords = keywords == null ? List.of() : List.copyOf(keywords);
            description = description == null ? "" : description;
        }
    }

    static final int EXACT = 1000;
    static final int PREFIX = 800;
    static final int WORD_START = 600;
    static final int KEYWORD_PREFIX = 500;
    static final int SUBSTRING = 400;
    static final int SUBSEQUENCE = 200;
    static final int TYPO = 100;
    /** Boost for the most recently run entry; each older one gets a little less. */
    static final int RECENT_BOOST = 150;
    static final int RECENT_STEP = 10;

    private final List<Entry> entries;

    public PaletteIndex(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<Entry> entries() {
        return entries;
    }

    /**
     * The best {@code limit} entries for {@code query}. {@code recent} lists action ids, most recent first; an empty
     * query lists those first.
     */
    public List<Entry> search(String query, int limit, List<String> recent) {
        String q = normalize(query);
        List<String> recentActions = recent == null ? List.of() : recent;
        record Scored(Entry entry, int score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Entry entry : entries) {
            int base = q.isEmpty() ? 1 : score(q, entry);
            if (base <= 0) continue;
            int recentIndex = recentActions.indexOf(entry.action());
            int boost = recentIndex < 0 ? 0 : Math.max(0, RECENT_BOOST - recentIndex * RECENT_STEP);
            if (q.isEmpty() && recentIndex < 0) boost = 0;
            scored.add(new Scored(entry, base + boost));
        }
        scored.sort(Comparator.comparingInt((Scored s) -> -s.score())
            .thenComparingInt(s -> s.entry().kind().ordinal())
            .thenComparing(s -> s.entry().title().toLowerCase(Locale.ROOT)));
        List<Entry> out = new ArrayList<>();
        for (Scored s : scored) {
            if (out.size() >= limit) break;
            out.add(s.entry());
        }
        return out;
    }

    /** How well {@code query} (already normalized) matches {@code entry}; 0 for no match. */
    static int score(String query, Entry entry) {
        String title = normalize(entry.title());
        if (title.equals(query)) return EXACT;
        if (title.startsWith(query)) return PREFIX - Math.min(100, title.length() - query.length());
        if (wordStart(title, query)) return WORD_START;
        for (String keyword : entry.keywords()) {
            String k = normalize(keyword);
            if (k.equals(query) || k.startsWith(query) || wordStart(k, query)) return KEYWORD_PREFIX;
        }
        if (title.contains(query)) return SUBSTRING;
        for (String keyword : entry.keywords()) {
            if (normalize(keyword).contains(query)) return SUBSTRING - 50;
        }
        String compactTitle = title.replace(" ", "");
        int gaps = subsequenceGaps(query, compactTitle);
        if (gaps >= 0) return SUBSEQUENCE - Math.min(90, gaps);
        if (query.length() >= 4) {
            for (int skip = 0; skip < query.length(); skip++) {
                String withoutOne = query.substring(0, skip) + query.substring(skip + 1);
                int typoGaps = subsequenceGaps(withoutOne, compactTitle);
                if (typoGaps >= 0) return TYPO - Math.min(50, typoGaps);
            }
        }
        return 0;
    }

    private static boolean wordStart(String text, String query) {
        int from = 0;
        while (true) {
            int space = indexOfSeparator(text, from);
            if (space < 0) return false;
            if (text.startsWith(query, space + 1)) return true;
            from = space + 1;
        }
    }

    private static int indexOfSeparator(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '-' || c == '_' || c == '.' || c == '#' || c == ':') return i;
        }
        return -1;
    }

    /** Letters skipped when {@code query}'s letters appear in order in {@code text}; -1 when they don't. */
    static int subsequenceGaps(String query, String text) {
        if (query.isEmpty()) return 0;
        int qi = 0;
        int gaps = 0;
        boolean started = false;
        for (int i = 0; i < text.length() && qi < query.length(); i++) {
            if (text.charAt(i) == query.charAt(qi)) {
                qi++;
                started = true;
            } else if (started) {
                gaps++;
            }
        }
        return qi == query.length() ? gaps : -1;
    }

    static String normalize(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Puts {@code action} at the front of the recent list, keeping at most {@code max}. */
    public static List<String> remember(List<String> recent, String action, int max) {
        List<String> out = new ArrayList<>();
        out.add(action);
        if (recent != null) {
            for (String existing : recent) {
                if (out.size() >= max) break;
                if (existing != null && !existing.equals(action)) out.add(existing);
            }
        }
        return out;
    }
}
