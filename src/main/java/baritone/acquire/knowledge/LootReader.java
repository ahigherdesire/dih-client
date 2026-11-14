package baritone.acquire.knowledge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Expected drops of a loot table for one {@link Scenario}, without Fortune or Looting.
 *
 * <p>Pools are evaluated exactly under the scenario: every entry's conditions become a probability,
 * composite entries ({@code alternatives}, {@code sequence}, {@code group}) are expanded into weighted
 * branches, and each pool roll picks among the expanded entries by weight like vanilla does. Item
 * counts are tracked as distributions ({@link CountDist}) so {@code set_count} and {@code limit_count}
 * combine exactly.
 *
 * <p>Conditions that depend on the world are fixed by documented rules:
 * <ul>
 *   <li>{@code match_tool}: the scenario's tool; enchantment predicates only match Silk Touch, and only in the silk scenario.</li>
 *   <li>{@code block_state_property}: a canonical state per table: {@code age} is the highest age the
 *       table tests (a mature crop), {@code half=lower}, {@code part=head}, {@code type=bottom} (a single
 *       slab), other numeric properties are 1 (one candle, one pickle, one snow layer), and booleans are
 *       false except {@code berries}, {@code bottom} and {@code down} (one lichen face).</li>
 *   <li>{@code table_bonus}: its first chance; {@code random_chance_with_enchanted_bonus}: its unenchanted chance.</li>
 *   <li>{@code entity_properties}: an empty predicate passes; cube-mob size passes (every size gets killed
 *       eventually); anything else (on fire, riding, captain, attacker type or gear) fails. A table that
 *       tests sheep colour is evaluated once per colour and weighted by the natural spawn odds, since the
 *       colours exclude each other. {@code damage_source_properties} fails.</li>
 *   <li>{@code survives_explosion}, {@code location_check} and unknown conditions pass.</li>
 * </ul>
 * Functions other than {@code set_count}, {@code limit_count} and the level-0 part of
 * {@code apply_bonus}/{@code binomial_with_bonus_count} (crop seeds) are ignored. {@code dynamic} entries drop nothing.
 */
final class LootReader {

    /** Who breaks the block or kills the mob. {@code tool} is an item id, or null for the bare hand. */
    record Scenario(String tool, boolean silkTouch, boolean playerKill) {
    }

    /** Natural spawn colours of sheep (the classic {@code Sheep.getRandomSheepColor} odds). */
    private static final Map<String, Double> SHEEP_COLOR_ODDS = Map.of(
            "white", 0.81836, "black", 0.05, "gray", 0.05, "light_gray", 0.05, "brown", 0.03, "pink", 0.00164);
    private static final Set<String> TRUE_BOOLEAN_PROPERTIES = Set.of("berries", "bottom", "down");
    private static final int MAX_BRANCHES = 4096;
    private static final double EPS = 1e-12;

    private final Map<String, JsonObject> tables;
    private final Tags itemTags;
    private final Map<JsonObject, Map<String, String>> canonicalStates = new IdentityHashMap<>();
    private final Map<String, Boolean> testsSheepColor = new HashMap<>();

    /** @param tables loot table id ("minecraft:blocks/stone") -> JSON */
    LootReader(Map<String, JsonObject> tables, Tags itemTags) {
        this.tables = tables;
        this.itemTags = itemTags;
    }

    /** Item id -> expected count per block broken / mob killed. Empty for an unknown table. */
    Map<String, Double> expectedDrops(String tableId, Scenario scenario) {
        if (!testsSheepColor(tableId, new HashSet<>())) return table(tableId, scenario, null, new HashSet<>());
        Map<String, Double> out = new TreeMap<>();
        SHEEP_COLOR_ODDS.forEach((color, odds) -> table(tableId, scenario, color, new HashSet<>())
                .forEach((item, n) -> out.merge(item, n * odds, Double::sum)));
        return out;
    }

    /** Whether the table, or one it references, has a condition on sheep colour. */
    private boolean testsSheepColor(String id, Set<String> seen) {
        Boolean cached = testsSheepColor.get(id);
        if (cached != null) return cached;
        JsonObject json = tables.get(id);
        if (json == null || !seen.add(id)) return false;
        boolean tests = json.toString().contains("sheep/color");
        if (!tests) {
            for (String ref : referencedTables(json, new ArrayList<>())) tests |= testsSheepColor(ref, seen);
        }
        testsSheepColor.put(id, tests);
        return tests;
    }

    private static List<String> referencedTables(JsonElement element, List<String> out) {
        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) referencedTables(e, out);
        } else if (element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            if (o.has("type") && o.get("type").isJsonPrimitive() && unprefixed(o.get("type").getAsString()).equals("loot_table")
                    && o.has("value") && o.get("value").isJsonPrimitive()) {
                out.add(VanillaKnowledge.id(o.get("value").getAsString()));
            }
            for (Map.Entry<String, JsonElement> e : o.entrySet()) referencedTables(e.getValue(), out);
        }
        return out;
    }

    private Map<String, Double> table(String id, Scenario scenario, String sheepColor, Set<String> stack) {
        JsonObject json = tables.get(id);
        if (json == null || !stack.add(id)) return Map.of();
        try {
            return table(json, scenario, sheepColor, stack);
        } finally {
            stack.remove(id);
        }
    }

    private Map<String, Double> table(JsonObject json, Scenario scenario, String sheepColor, Set<String> stack) {
        Ctx ctx = new Ctx(scenario, canonicalState(json), sheepColor, stack);
        List<JsonObject> tableFunctions = objects(json, "functions");
        Map<String, Double> out = new TreeMap<>();
        for (JsonObject pool : objects(json, "pools")) {
            double chance = conditions(pool, ctx);
            double rolls = CountDist.of(pool.has("rolls") ? pool.get("rolls") : null).mean();
            if (chance <= 0 || rolls <= 0) continue;
            List<JsonObject> functions = new ArrayList<>(objects(pool, "functions"));
            functions.addAll(tableFunctions);
            for (Map.Entry<String, Double> e : pool(pool, functions, ctx).entrySet()) {
                out.merge(e.getKey(), e.getValue() * chance * rolls, Double::sum);
            }
        }
        out.values().removeIf(v -> v <= EPS);
        return out;
    }

    /** Expected drops of one roll of {@code pool}. */
    private Map<String, Double> pool(JsonObject pool, List<JsonObject> functions, Ctx ctx) {
        List<Branch> combos = List.of(new Branch(1, true, List.of()));
        for (JsonObject entry : objects(pool, "entries")) {
            List<Branch> expanded = expand(entry, ctx);
            List<Branch> next = new ArrayList<>(combos.size() * expanded.size());
            for (Branch a : combos) {
                for (Branch b : expanded) next.add(new Branch(a.p * b.p, true, concat(a.leaves, b.leaves)));
            }
            combos = merge(next);
            if (combos.size() > MAX_BRANCHES) {
                // Only chest-like tables with many independent chance entries get here; keep the likeliest.
                combos = new ArrayList<>(combos);
                combos.sort(Comparator.comparingDouble((Branch b) -> b.p).reversed());
                combos = combos.subList(0, MAX_BRANCHES);
            }
        }
        Map<String, Double> out = new HashMap<>();
        for (Branch combo : combos) {
            List<Leaf> leaves = new ArrayList<>();
            int total = 0;
            for (Leaf leaf : combo.leaves) {
                if (leaf.weight > 0) {
                    leaves.add(leaf);
                    total += leaf.weight;
                }
            }
            if (leaves.isEmpty()) continue;
            for (Leaf leaf : leaves) {
                double pick = leaves.size() == 1 ? 1 : (double) leaf.weight / total;
                for (Map.Entry<String, Double> e : leaf.yield(this, functions, ctx).entrySet()) {
                    out.merge(e.getKey(), combo.p * pick * e.getValue(), Double::sum);
                }
            }
        }
        return out;
    }

    /** The ways {@code entry} can expand: probability, whether it succeeded, and the leaf entries it added. */
    private List<Branch> expand(JsonObject entry, Ctx ctx) {
        double chance = conditions(entry, ctx);
        List<Branch> out = new ArrayList<>();
        if (chance < 1) out.add(new Branch(1 - chance, false, List.of()));
        if (chance <= 0) return out;
        String type = unprefixed(string(entry, "type", "item"));
        int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
        switch (type) {
            case "alternatives", "sequence", "group" -> out.addAll(composite(type, objects(entry, "children"), chance, ctx));
            case "tag" -> {
                String tag = string(entry, "name", "");
                if (entry.has("expand") && entry.get("expand").getAsBoolean()) {
                    List<Leaf> leaves = new ArrayList<>();
                    for (String item : itemTags.get(tag)) leaves.add(new Leaf(entry, Leaf.Kind.ITEM, item, weight));
                    out.add(new Branch(chance, true, leaves));
                } else {
                    out.add(new Branch(chance, true, List.of(new Leaf(entry, Leaf.Kind.TAG, tag, weight))));
                }
            }
            case "item" -> out.add(new Branch(chance, true, List.of(new Leaf(entry, Leaf.Kind.ITEM, VanillaKnowledge.id(string(entry, "name", "minecraft:air")), weight))));
            case "loot_table" -> out.add(new Branch(chance, true, List.of(new Leaf(entry, Leaf.Kind.TABLE, null, weight))));
            // empty, dynamic (block-entity contents such as pot sherds) and unknown entries drop nothing
            default -> out.add(new Branch(chance, true, List.of(new Leaf(entry, Leaf.Kind.NOTHING, null, weight))));
        }
        return merge(out);
    }

    private List<Branch> composite(String type, List<JsonObject> children, double chance, Ctx ctx) {
        List<Branch> out = new ArrayList<>();
        List<Branch> pending = List.of(new Branch(chance, false, List.of()));
        for (JsonObject child : children) {
            List<Branch> childBranches = expand(child, ctx);
            List<Branch> next = new ArrayList<>();
            for (Branch a : pending) {
                for (Branch b : childBranches) {
                    Branch m = new Branch(a.p * b.p, b.ok, concat(a.leaves, b.leaves));
                    switch (type) {
                        case "alternatives" -> (b.ok ? out : next).add(m);   // first child that expands wins
                        case "sequence" -> (b.ok ? next : out).add(m);       // stops at the first child that fails
                        default -> next.add(m);                               // group: every child
                    }
                }
            }
            pending = merge(next);
        }
        boolean endOk = !type.equals("alternatives");
        for (Branch b : pending) out.add(new Branch(b.p, endOk, b.leaves));
        return merge(out);
    }

    private static List<Branch> merge(List<Branch> branches) {
        Map<BranchKey, Double> merged = new LinkedHashMap<>();
        for (Branch b : branches) {
            if (b.p > EPS) merged.merge(new BranchKey(b.ok, b.leaves), b.p, Double::sum);
        }
        List<Branch> out = new ArrayList<>(merged.size());
        merged.forEach((k, p) -> out.add(new Branch(p, k.ok, k.leaves)));
        return out;
    }

    // ---- conditions ----

    private double conditions(JsonObject holder, Ctx ctx) {
        double p = 1;
        for (JsonObject c : objects(holder, "conditions")) {
            p *= condition(c, ctx);
            if (p <= 0) return 0;
        }
        return p;
    }

    private double condition(JsonObject c, Ctx ctx) {
        return switch (unprefixed(string(c, "condition", ""))) {
            case "inverted" -> 1 - condition(object(c, "term"), ctx);
            case "any_of", "alternative" -> {
                double none = 1;
                for (JsonObject term : objects(c, "terms")) none *= 1 - condition(term, ctx);
                yield 1 - none;
            }
            case "all_of" -> {
                double all = 1;
                for (JsonObject term : objects(c, "terms")) all *= condition(term, ctx);
                yield all;
            }
            case "random_chance", "random_chance_with_looting" -> clamp01(CountDist.expected(c.get("chance")));
            case "random_chance_with_enchanted_bonus" -> clamp01(CountDist.expected(c.get("unenchanted_chance")));
            case "table_bonus" -> {
                JsonArray chances = c.has("chances") ? c.getAsJsonArray("chances") : null;
                yield chances == null || chances.isEmpty() ? 1 : clamp01(chances.get(0).getAsDouble());
            }
            case "killed_by_player" -> ctx.scenario.playerKill ? 1 : 0;
            case "match_tool" -> matchTool(object(c, "predicate"), ctx.scenario) ? 1 : 0;
            case "block_state_property" -> matchesState(object(c, "properties"), ctx.state) ? 1 : 0;
            case "entity_properties" -> entityProperties(c, ctx.sheepColor);
            case "damage_source_properties" -> 0;
            default -> 1; // survives_explosion, location_check, weather/time/value checks, references
        };
    }

    private boolean matchTool(JsonObject predicate, Scenario scenario) {
        if (predicate == null) return true;
        if (predicate.has("items")) {
            if (scenario.tool == null || !itemMatches(predicate.get("items"), scenario.tool)) return false;
        }
        JsonObject predicates = object(predicate, "predicates");
        if (predicates != null) {
            for (String key : predicates.keySet()) {
                if (!key.endsWith("enchantments")) continue;
                boolean silk = !key.endsWith("stored_enchantments") && predicates.get(key).toString().contains("silk_touch");
                if (!silk || !scenario.silkTouch) return false;
            }
        }
        return true;
    }

    private boolean itemMatches(JsonElement items, String tool) {
        if (items.isJsonArray()) {
            for (JsonElement e : items.getAsJsonArray()) if (itemMatches(e, tool)) return true;
            return false;
        }
        String ref = items.getAsString();
        return ref.startsWith("#") ? itemTags.contains(ref, tool) : VanillaKnowledge.id(ref).equals(tool);
    }

    private static double entityProperties(JsonObject c, String sheepColor) {
        JsonObject predicate = object(c, "predicate");
        if (predicate == null || predicate.isEmpty()) return 1;
        String entity = string(c, "entity", "this");
        if (!entity.equals("this")) {
            // The attacker is us, holding nothing special.
            JsonElement type = predicate.get("minecraft:entity_type");
            return predicate.size() == 1 && type != null && type.isJsonPrimitive()
                    && VanillaKnowledge.id(type.getAsString()).equals("minecraft:player") ? 1 : 0;
        }
        for (Map.Entry<String, JsonElement> e : predicate.entrySet()) {
            switch (unprefixed(e.getKey())) {
                case "type_specific/cube_mob" -> {
                }
                case "type_specific/sheep" -> {
                    JsonElement sheared = e.getValue().getAsJsonObject().get("sheared");
                    if (sheared != null && sheared.getAsBoolean()) return 0;
                }
                case "components" -> {
                    for (Map.Entry<String, JsonElement> comp : e.getValue().getAsJsonObject().entrySet()) {
                        if (!unprefixed(comp.getKey()).equals("sheep/color")) return 0;
                        if (!unprefixed(comp.getValue().getAsString()).equals(sheepColor)) return 0;
                    }
                }
                default -> {
                    return 0;
                }
            }
        }
        return 1;
    }

    // ---- block states ----

    private static boolean matchesState(JsonObject properties, Map<String, String> state) {
        if (properties == null) return true;
        for (Map.Entry<String, JsonElement> e : properties.entrySet()) {
            String value = state.get(e.getKey());
            if (value == null) return false;
            JsonElement want = e.getValue();
            if (want.isJsonPrimitive()) {
                if (!want.getAsString().equals(value)) return false;
            } else if (want.isJsonObject()) {
                Integer v = intOrNull(value);
                if (v == null) return false;
                JsonObject range = want.getAsJsonObject();
                if (range.has("min") && v < Integer.parseInt(range.get("min").getAsString())) return false;
                if (range.has("max") && v > Integer.parseInt(range.get("max").getAsString())) return false;
            }
        }
        return true;
    }

    /** The canonical block state the table's {@code block_state_property} conditions are tested against (see class doc). */
    private Map<String, String> canonicalState(JsonObject table) {
        Map<String, String> cached = canonicalStates.get(table);
        if (cached != null) return cached;
        Map<String, Set<String>> tested = new HashMap<>();
        collectTestedProperties(table, tested);
        Map<String, String> state = new HashMap<>();
        tested.forEach((property, values) -> state.put(property, canonicalValue(property, values)));
        canonicalStates.put(table, state);
        return state;
    }

    static String canonicalValue(String property, Set<String> tested) {
        if (property.equals("age")) {
            int max = 0;
            for (String v : tested) {
                Integer i = intOrNull(v);
                if (i != null) max = Math.max(max, i);
            }
            return Integer.toString(max);
        }
        switch (property) {
            case "half" -> {
                return "lower";
            }
            case "part" -> {
                return "head";
            }
            case "type" -> {
                return "bottom";
            }
            default -> {
            }
        }
        boolean allBoolean = tested.stream().allMatch(v -> v.equals("true") || v.equals("false"));
        if (allBoolean) return TRUE_BOOLEAN_PROPERTIES.contains(property) ? "true" : "false";
        boolean allNumeric = tested.stream().allMatch(v -> intOrNull(v) != null);
        return allNumeric ? "1" : "";
    }

    private static void collectTestedProperties(JsonElement element, Map<String, Set<String>> out) {
        if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) collectTestedProperties(e, out);
        } else if (element.isJsonObject()) {
            JsonObject o = element.getAsJsonObject();
            if (o.has("condition") && unprefixed(o.get("condition").getAsString()).equals("block_state_property")) {
                JsonObject properties = object(o, "properties");
                if (properties != null) {
                    for (Map.Entry<String, JsonElement> p : properties.entrySet()) {
                        Set<String> values = out.computeIfAbsent(p.getKey(), k -> new HashSet<>());
                        if (p.getValue().isJsonPrimitive()) {
                            values.add(p.getValue().getAsString());
                        } else if (p.getValue().isJsonObject()) {
                            for (String bound : List.of("min", "max")) {
                                JsonElement b = p.getValue().getAsJsonObject().get(bound);
                                if (b != null) values.add(b.getAsString());
                            }
                        }
                    }
                }
            }
            for (Map.Entry<String, JsonElement> e : o.entrySet()) collectTestedProperties(e.getValue(), out);
        }
    }

    // ---- functions ----

    private CountDist applyFunctions(CountDist count, List<JsonObject> functions, Ctx ctx) {
        for (JsonObject fn : functions) {
            double chance = conditions(fn, ctx);
            if (chance <= 0) continue;
            CountDist next = switch (unprefixed(string(fn, "function", ""))) {
                case "set_count" -> {
                    CountDist value = CountDist.of(fn.get("count"));
                    yield fn.has("add") && fn.get("add").getAsBoolean() ? count.plus(value) : value;
                }
                case "limit_count" -> {
                    JsonElement limit = fn.get("limit");
                    if (limit == null || !limit.isJsonObject()) yield count;
                    JsonObject l = limit.getAsJsonObject();
                    int min = l.has("min") ? (int) Math.round(CountDist.expected(l.get("min"))) : Integer.MIN_VALUE;
                    int max = l.has("max") ? (int) Math.round(CountDist.expected(l.get("max"))) : Integer.MAX_VALUE;
                    yield count.clamp(min, max);
                }
                case "apply_bonus" -> {
                    // Without Fortune only binomial_with_bonus_count changes the count: it always rolls `extra` extra tries.
                    JsonObject params = object(fn, "parameters");
                    if (!unprefixed(string(fn, "formula", "")).equals("binomial_with_bonus_count") || params == null) yield count;
                    yield count.plus(CountDist.binomial(params.get("extra").getAsInt(), params.get("probability").getAsDouble()));
                }
                default -> count;
            };
            count = count.mix(next, chance);
        }
        return count;
    }

    // ---- model ----

    /** @param sheepColor the colour being evaluated, or null when the table does not test it */
    private record Ctx(Scenario scenario, Map<String, String> state, String sheepColor, Set<String> stack) {
    }

    private record Branch(double p, boolean ok, List<Leaf> leaves) {
    }

    /** Branches with the same outcome are merged; leaves compare by identity. */
    private record BranchKey(boolean ok, List<Leaf> leaves) {
    }

    /** One entry a pool roll can pick. Identity matters: the same entry in two branches is the same Leaf. */
    private static final class Leaf {
        enum Kind { ITEM, TAG, TABLE, NOTHING }

        final JsonObject entry;
        final Kind kind;
        final String name;
        final int weight;
        private Map<String, Double> cached;

        Leaf(JsonObject entry, Kind kind, String name, int weight) {
            this.entry = entry;
            this.kind = kind;
            this.name = name;
            this.weight = weight;
        }

        /** Expected drops when this entry is picked. Cached: a Leaf lives within one pool evaluation. */
        Map<String, Double> yield(LootReader reader, List<JsonObject> poolFunctions, Ctx ctx) {
            if (cached != null) return cached;
            cached = switch (kind) {
                case ITEM -> single(reader, name, poolFunctions, ctx);
                case TAG -> {
                    Map<String, Double> all = new HashMap<>();
                    for (String item : reader.itemTags.get(name)) all.putAll(single(reader, item, poolFunctions, ctx));
                    yield all;
                }
                case TABLE -> {
                    JsonElement value = entry.has("value") ? entry.get("value") : entry.get("name");
                    if (value == null) yield Map.of();
                    if (value.isJsonObject()) yield reader.table(value.getAsJsonObject(), ctx.scenario, ctx.sheepColor, ctx.stack);
                    yield reader.table(VanillaKnowledge.id(value.getAsString()), ctx.scenario, ctx.sheepColor, ctx.stack);
                }
                case NOTHING -> Map.of();
            };
            return cached;
        }

        private Map<String, Double> single(LootReader reader, String item, List<JsonObject> poolFunctions, Ctx ctx) {
            CountDist count = reader.applyFunctions(CountDist.point(1), objects(entry, "functions"), ctx);
            count = reader.applyFunctions(count, poolFunctions, ctx);
            double expected = count.expectedDrop();
            return expected > EPS && !item.equals("minecraft:air") ? Map.of(item, expected) : Map.of();
        }
    }

    // ---- json helpers ----

    static String unprefixed(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    static List<JsonObject> objects(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonArray()) return List.of();
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement x : e.getAsJsonArray()) if (x.isJsonObject()) out.add(x.getAsJsonObject());
        return out;
    }

    private static JsonObject object(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static Integer intOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        List<T> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }
}
