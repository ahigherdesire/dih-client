package baritone.acquire.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves what a user typed to an item id: ids with or without {@code minecraft:}, English display
 * names, spaces or underscores, plurals and a few common shorthands ("iron pick", "cobble", "planks").
 */
final class ItemNames {
    /** Whole-text shorthands, applied after the exact lookup fails. */
    private static final Map<String, String> WHOLE = Map.ofEntries(
            Map.entry("plank", "oak_planks"), Map.entry("planks", "oak_planks"),
            Map.entry("wood_planks", "oak_planks"), Map.entry("wooden_planks", "oak_planks"),
            Map.entry("log", "oak_log"), Map.entry("wood", "oak_log"), Map.entry("tree", "oak_log"),
            Map.entry("cobble", "cobblestone"),
            Map.entry("iron", "iron_ingot"), Map.entry("gold", "gold_ingot"),
            Map.entry("copper", "copper_ingot"), Map.entry("netherite", "netherite_ingot"),
            Map.entry("lapis", "lapis_lazuli"), Map.entry("wool", "white_wool"),
            Map.entry("table", "crafting_table"), Map.entry("workbench", "crafting_table"),
            Map.entry("craft_table", "crafting_table"), Map.entry("boat", "oak_boat"), Map.entry("sapling", "oak_sapling"));
    /** Per-word shorthands. */
    private static final Map<String, String> WORD = Map.of(
            "pick", "pickaxe", "picks", "pickaxe", "pickax", "pickaxe", "spade", "shovel",
            "cobble", "cobblestone", "pants", "leggings");
    /** Material words that change form in front of a tool or armour word ("wood pick" -> wooden_pickaxe). */
    private static final Map<String, String> MATERIAL = Map.of("wood", "wooden", "gold", "golden");
    private static final Set<String> GEAR = Set.of("pickaxe", "axe", "shovel", "hoe", "sword", "spear",
            "helmet", "chestplate", "leggings", "boots");

    private final Set<String> items;
    /** Normalized key -> id. Id paths win over display names. */
    private final Map<String, String> byKey = new HashMap<>();
    private final Map<String, List<String>> keysOf = new HashMap<>();

    /** @param displayNames item id -> English name (may miss ids) */
    ItemNames(Set<String> items, Map<String, String> displayNames) {
        this.items = items;
        List<String> sorted = new ArrayList<>(items);
        sorted.sort(null);
        for (String id : sorted) addKey(id, id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id, true);
        for (String id : sorted) {
            if (!id.startsWith("minecraft:")) addKey(id, id.substring(id.indexOf(':') + 1), false);
            String name = displayNames.get(id);
            if (name != null) addKey(id, normalize(name), false);
        }
    }

    private void addKey(String id, String key, boolean override) {
        if (key.isEmpty()) return;
        if (override || !byKey.containsKey(key)) byKey.put(key, id);
        List<String> keys = keysOf.computeIfAbsent(id, k -> new ArrayList<>(2));
        if (!keys.contains(key)) keys.add(key);
    }

    Optional<String> resolve(String text) {
        if (text == null) return Optional.empty();
        String n = normalize(text);
        if (n.isEmpty()) return Optional.empty();
        if (n.indexOf(':') >= 0) {
            if (items.contains(n)) return Optional.of(n);
            if (!n.startsWith("minecraft:")) return Optional.empty();
            n = n.substring("minecraft:".length());
        }
        for (String candidate : variants(n)) {
            String id = byKey.get(candidate);
            if (id != null) return Optional.of(id);
        }
        // Typos: accept a unique nearest key within 1 edit (2 for long names).
        int allowed = n.length() >= 8 ? 2 : n.length() >= 4 ? 1 : 0;
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        boolean tie = false;
        for (Map.Entry<String, String> e : byKey.entrySet()) {
            if (Math.abs(e.getKey().length() - n.length()) > allowed) continue;
            int d = distance(n, e.getKey());
            if (d < bestDistance) {
                bestDistance = d;
                best = e.getValue();
                tie = false;
            } else if (d == bestDistance && !e.getValue().equals(best)) {
                tie = true;
            }
        }
        return best != null && bestDistance <= allowed && !tie ? Optional.of(best) : Optional.empty();
    }

    List<String> suggest(String text, int limit) {
        if (text == null || limit <= 0) return List.of();
        String n = normalize(text);
        if (n.startsWith("minecraft:")) n = n.substring("minecraft:".length());
        if (n.isEmpty()) return List.of();
        List<String> queries = variants(n);
        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, List<String>> e : keysOf.entrySet()) {
            double best = 0;
            for (String key : e.getValue()) {
                for (String q : queries) best = Math.max(best, score(q, key));
            }
            if (best >= 0.4) scores.put(e.getKey(), best);
        }
        List<String> ranked = new ArrayList<>(scores.keySet());
        ranked.sort(Comparator.<String>comparingDouble(scores::get).reversed()
                .thenComparingInt(String::length)
                .thenComparing(Comparator.naturalOrder()));
        return List.copyOf(ranked.subList(0, Math.min(limit, ranked.size())));
    }

    /** Lower-case, trimmed, words joined by '_', apostrophes dropped. Keeps a namespace colon. */
    static String normalize(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT).replace("'", "").replace("’", "");
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            boolean keep = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ':' || c == '/' || c == '.';
            if (keep) sb.append(c);
            else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') sb.append('_');
        }
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == '_') end--;
        return sb.substring(0, end);
    }

    /** Candidate keys for a normalized text, most literal first: as typed, shorthands, then singular forms. */
    private static List<String> variants(String n) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(n);
        String[] words = n.split("_");
        for (int i = 0; i < words.length; i++) words[i] = WORD.getOrDefault(words[i], words[i]);
        for (int i = 0; i + 1 < words.length; i++) {
            if (GEAR.contains(words[i + 1]) || GEAR.contains(singular(words[i + 1]))) words[i] = MATERIAL.getOrDefault(words[i], words[i]);
        }
        out.add(String.join("_", words));
        for (String base : List.copyOf(out)) {
            String last = base.substring(base.lastIndexOf('_') + 1);
            String stem = base.substring(0, base.length() - last.length());
            for (String single : singulars(last)) out.add(stem + WORD.getOrDefault(single, single));
        }
        for (String base : List.copyOf(out)) {
            String alias = WHOLE.get(base);
            if (alias != null) out.add(alias);
        }
        return List.copyOf(out);
    }

    private static List<String> singulars(String word) {
        List<String> out = new ArrayList<>(3);
        if (word.length() > 3 && word.endsWith("ies")) out.add(word.substring(0, word.length() - 3) + "y");
        if (word.length() > 3 && word.endsWith("es")) out.add(word.substring(0, word.length() - 2));
        if (word.length() > 2 && word.endsWith("s") && !word.endsWith("ss")) out.add(word.substring(0, word.length() - 1));
        return out;
    }

    private static String singular(String word) {
        List<String> s = singulars(word);
        return s.isEmpty() ? word : s.get(s.size() - 1);
    }

    /** Similarity in [0, 1]: edit similarity or word overlap, plus a bonus when one contains the other. */
    private static double score(String q, String key) {
        if (q.equals(key)) return 1;
        double edit = 1 - (double) distance(q, key) / Math.max(q.length(), key.length());
        String[] qw = q.split("_");
        String[] kw = key.split("_");
        int matched = 0;
        for (String a : qw) {
            for (String b : kw) {
                if (wordsMatch(a, b)) {
                    matched++;
                    break;
                }
            }
        }
        double words = (double) matched / Math.max(qw.length, kw.length);
        double bonus = key.startsWith(q) ? 0.15 : key.contains(q) ? 0.1 : 0;
        return Math.min(0.99, Math.max(edit, words) * 0.85 + bonus);
    }

    private static boolean wordsMatch(String a, String b) {
        if (a.equals(b)) return true;
        if (Math.min(a.length(), b.length()) >= 3 && (a.startsWith(b) || b.startsWith(a))) return true;
        return Math.min(a.length(), b.length()) >= 4 && distance(a, b) <= 1;
    }

    /** Optimal string alignment distance (Levenshtein plus adjacent swaps). */
    static int distance(String a, String b) {
        int n = a.length();
        int m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, d[i - 2][j - 2] + 1);
                }
                d[i][j] = v;
            }
        }
        return d[n][m];
    }
}
