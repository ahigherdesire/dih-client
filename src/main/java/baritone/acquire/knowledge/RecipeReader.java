package baritone.acquire.knowledge;

import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Source;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns one vanilla recipe JSON into a {@link CraftSource} or {@link SmeltSource}.
 *
 * <p>Handled: {@code crafting_shaped}, {@code crafting_shapeless}, {@code smelting}, {@code blasting},
 * {@code smoking}. Everything else (special, transmute, dye, imbue, decorated pot, stonecutting,
 * campfire cooking, smithing) is skipped and counted by type.
 */
final class RecipeReader {
    private final Tags itemTags;
    private final Map<String, Integer> skipped = new LinkedHashMap<>();

    RecipeReader(Tags itemTags) {
        this.itemTags = itemTags;
    }

    /** Recipe types that were skipped -> how many, for the load log and the report. */
    Map<String, Integer> skipped() {
        return skipped;
    }

    /** The source for recipe {@code id} ("minecraft:stick"), or null if the type is not supported or the recipe is unusable. */
    Source read(String id, JsonObject json) {
        String type = LootReader.unprefixed(json.has("type") ? json.get("type").getAsString() : "");
        Source source = switch (type) {
            case "crafting_shaped" -> shaped(id, json);
            case "crafting_shapeless" -> shapeless(id, json);
            case "smelting" -> cooking(id, json, "minecraft:furnace", 200);
            case "blasting" -> cooking(id, json, "minecraft:blast_furnace", 100);
            case "smoking" -> cooking(id, json, "minecraft:smoker", 100);
            default -> null;
        };
        if (source == null) skipped.merge(type.isEmpty() ? "?" : type, 1, Integer::sum);
        return source;
    }

    private CraftSource shaped(String id, JsonObject json) {
        JsonObject key = json.has("key") && json.get("key").isJsonObject() ? json.getAsJsonObject("key") : null;
        JsonArray patternJson = json.has("pattern") && json.get("pattern").isJsonArray() ? json.getAsJsonArray("pattern") : null;
        Result result = result(json);
        if (key == null || patternJson == null || result == null) return null;

        List<String> pattern = new ArrayList<>();
        for (JsonElement row : patternJson) pattern.add(row.getAsString());
        pattern = shrink(pattern);
        int height = pattern.size();
        int width = pattern.stream().mapToInt(String::length).max().orElse(0);
        if (width == 0 || height == 0) return null;

        // Slots per key symbol, in reading order; symbols with identical choices merge into one Ingredient.
        Map<Character, Integer> slots = new LinkedHashMap<>();
        for (String row : pattern) {
            for (char c : row.toCharArray()) if (c != ' ') slots.merge(c, 1, Integer::sum);
        }
        Map<List<String>, Integer> merged = new LinkedHashMap<>();
        for (Map.Entry<Character, Integer> slot : slots.entrySet()) {
            List<String> anyOf = itemTags.resolve(key.get(String.valueOf(slot.getKey())));
            if (anyOf.isEmpty()) return null;
            merged.merge(anyOf, slot.getValue(), Integer::sum);
        }
        return new CraftSource(id, result.item, result.count, ingredients(merged), width > 2 || height > 2);
    }

    private CraftSource shapeless(String id, JsonObject json) {
        JsonArray list = json.has("ingredients") && json.get("ingredients").isJsonArray() ? json.getAsJsonArray("ingredients") : null;
        Result result = result(json);
        if (list == null || list.isEmpty() || result == null) return null;
        Map<List<String>, Integer> merged = new LinkedHashMap<>();
        for (JsonElement element : list) {
            List<String> anyOf = itemTags.resolve(element);
            if (anyOf.isEmpty()) return null;
            merged.merge(anyOf, 1, Integer::sum);
        }
        return new CraftSource(id, result.item, result.count, ingredients(merged), list.size() > 4);
    }

    private SmeltSource cooking(String id, JsonObject json, String station, int defaultTicks) {
        Result result = result(json);
        List<String> input = itemTags.resolve(json.get("ingredient"));
        if (result == null || input.isEmpty()) return null;
        int ticks = json.has("cookingtime") ? json.get("cookingtime").getAsInt() : defaultTicks;
        return new SmeltSource(id, result.item, result.count, new Ingredient(input, 1), station, ticks);
    }

    private static List<Ingredient> ingredients(Map<List<String>, Integer> merged) {
        List<Ingredient> out = new ArrayList<>(merged.size());
        merged.forEach((anyOf, count) -> out.add(new Ingredient(anyOf, count)));
        return out;
    }

    /** Drops blank leading/trailing rows and columns, like {@code ShapedRecipePattern.shrink}. */
    static List<String> shrink(List<String> pattern) {
        int width = pattern.stream().mapToInt(String::length).max().orElse(0);
        int top = 0;
        int bottom = pattern.size() - 1;
        while (top <= bottom && pattern.get(top).isBlank()) top++;
        while (bottom >= top && pattern.get(bottom).isBlank()) bottom--;
        if (top > bottom) return List.of();
        int left = width;
        int right = -1;
        for (int r = top; r <= bottom; r++) {
            String row = pattern.get(r);
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) != ' ') {
                    left = Math.min(left, c);
                    right = Math.max(right, c);
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (int r = top; r <= bottom; r++) {
            String row = pattern.get(r);
            StringBuilder sb = new StringBuilder();
            for (int c = left; c <= right; c++) sb.append(c < row.length() ? row.charAt(c) : ' ');
            out.add(sb.toString());
        }
        return out;
    }

    private record Result(String item, int count) {
    }

    private static Result result(JsonObject json) {
        JsonElement r = json.get("result");
        if (r == null || r.isJsonNull()) return null;
        if (r.isJsonPrimitive()) return new Result(VanillaKnowledge.id(r.getAsString()), 1);
        if (!r.isJsonObject()) return null;
        JsonObject o = r.getAsJsonObject();
        JsonElement item = o.has("id") ? o.get("id") : o.get("item");
        if (item == null) return null;
        int count = o.has("count") ? o.get("count").getAsInt() : 1;
        return count > 0 ? new Result(VanillaKnowledge.id(item.getAsString()), count) : null;
    }
}
