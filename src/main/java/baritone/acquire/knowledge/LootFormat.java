package baritone.acquire.knowledge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * Loot tables in the shape {@link LootReader} reads (Minecraft 26.2's). 26.3 renamed the format:
 * <ul>
 *   <li>a pool, entry or function has one {@code "condition"} (an object, or the id of a named predicate such as
 *       {@code minecraft:tool/can_silk_touch}) instead of a {@code "conditions"} list;</li>
 *   <li>a condition says {@code "type"} instead of {@code "condition"}, and {@code match_block} with {@code blocks}
 *       and {@code state} replaced {@code block_state_property} with {@code block} and {@code properties};</li>
 *   <li>{@code "modifier"} (one object or a list) replaced {@code "functions"}, and a function says {@code "type"}
 *       instead of {@code "function"}.</li>
 * </ul>
 * Only shapes that exist in 26.3 alone are converted, so 26.2 tables come back unchanged.
 */
final class LootFormat {

    private LootFormat() {
    }

    /**
     * @param predicates named predicates ("minecraft:tool/can_silk_touch" -> JSON), from {@code data/minecraft/predicate}
     */
    static JsonObject normalize(JsonObject table, Map<String, JsonObject> predicates) {
        return holder(table, predicates);
    }

    /** Any object that isn't itself a condition or function: pools, entries, tables. */
    private static JsonObject holder(JsonObject in, Map<String, JsonObject> predicates) {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : in.entrySet()) {
            String key = e.getKey();
            JsonElement value = e.getValue();
            if (key.equals("condition") && isNewCondition(value, predicates)) {
                append(out, "conditions", condition(value, predicates));
            } else if (key.equals("modifier")) {
                JsonArray functions = new JsonArray();
                for (JsonElement f : list(value)) functions.add(function(f.getAsJsonObject(), predicates));
                out.add("functions", functions);
            } else {
                out.add(key, any(value, predicates));
            }
        }
        return out;
    }

    private static JsonElement any(JsonElement value, Map<String, JsonObject> predicates) {
        if (value.isJsonObject()) return holder(value.getAsJsonObject(), predicates);
        if (value.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement e : value.getAsJsonArray()) out.add(any(e, predicates));
            return out;
        }
        return value;
    }

    /** 26.3: an object under "condition", or a string naming a predicate file. 26.2 only has the type string. */
    private static boolean isNewCondition(JsonElement value, Map<String, JsonObject> predicates) {
        if (value.isJsonObject()) return true;
        return value.isJsonPrimitive() && predicates.containsKey(VanillaKnowledge.id(value.getAsString()));
    }

    private static JsonObject condition(JsonElement value, Map<String, JsonObject> predicates) {
        JsonObject in = value.isJsonObject() ? value.getAsJsonObject()
            : predicates.getOrDefault(VanillaKnowledge.id(value.getAsString()), new JsonObject());
        JsonObject out = new JsonObject();
        String type = in.has("type") && in.get("type").isJsonPrimitive() ? in.get("type").getAsString() : null;
        if (type == null && in.has("condition")) return holder(in, predicates); // already the 26.2 shape
        if (type != null && LootReader.unprefixed(type).equals("match_block")) {
            out.addProperty("condition", "minecraft:block_state_property");
            if (in.has("blocks")) out.add("block", in.get("blocks"));
            if (in.has("state")) out.add("properties", in.get("state"));
            return out;
        }
        for (Map.Entry<String, JsonElement> e : in.entrySet()) {
            String key = e.getKey();
            JsonElement v = e.getValue();
            switch (key) {
                case "type" -> out.add("condition", v);
                case "term" -> out.add("term", condition(v, predicates));
                case "terms" -> {
                    JsonArray terms = new JsonArray();
                    for (JsonElement t : list(v)) terms.add(condition(t, predicates));
                    out.add("terms", terms);
                }
                default -> out.add(key, v);
            }
        }
        return out;
    }

    private static JsonObject function(JsonObject in, Map<String, JsonObject> predicates) {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : in.entrySet()) {
            String key = e.getKey();
            JsonElement v = e.getValue();
            if (key.equals("type")) out.add("function", v);
            else if (key.equals("condition") && isNewCondition(v, predicates)) append(out, "conditions", condition(v, predicates));
            else out.add(key, any(v, predicates));
        }
        return out;
    }

    private static Iterable<JsonElement> list(JsonElement value) {
        if (value.isJsonArray()) return value.getAsJsonArray();
        JsonArray one = new JsonArray();
        one.add(value);
        return one;
    }

    private static void append(JsonObject out, String key, JsonElement value) {
        JsonArray array = out.has(key) && out.get(key).isJsonArray() ? out.getAsJsonArray(key) : new JsonArray();
        array.add(value);
        out.add(key, array);
    }
}
