package baritone.acquire.knowledge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Item or block tags from {@code data/<ns>/tags/<kind>/**.json}, with nested tags expanded. */
final class Tags {
    private final Map<String, List<String>> raw;
    private final Map<String, List<String>> expanded = new HashMap<>();
    private final Map<String, Set<String>> sets = new HashMap<>();

    private Tags(Map<String, List<String>> raw) {
        this.raw = raw;
    }

    /** Reads every tag of {@code kind} ("item" or "block") from the file map (path -> parsed JSON). */
    static Tags parse(Map<String, JsonObject> files, String kind) {
        Map<String, List<String>> raw = new HashMap<>();
        for (Map.Entry<String, JsonObject> file : files.entrySet()) {
            String id = VanillaKnowledge.idFromPath(file.getKey(), "tags/" + kind + "/");
            if (id == null) continue;
            JsonElement values = file.getValue().get("values");
            if (values == null || !values.isJsonArray()) continue;
            List<String> list = new ArrayList<>();
            for (JsonElement value : values.getAsJsonArray()) {
                if (value.isJsonPrimitive()) list.add(value.getAsString());
                else if (value.isJsonObject() && value.getAsJsonObject().has("id")) list.add(value.getAsJsonObject().get("id").getAsString());
            }
            raw.put(id, list);
        }
        return new Tags(raw);
    }

    static Tags of(Map<String, List<String>> raw) {
        return new Tags(new HashMap<>(raw));
    }

    boolean has(String tag) {
        return raw.containsKey(strip(tag));
    }

    /** Members of {@code tag} ("minecraft:planks" or "#minecraft:planks") in file order, nested tags expanded. Empty if unknown. */
    List<String> get(String tag) {
        String id = strip(tag);
        List<String> cached = expanded.get(id);
        if (cached != null) return cached;
        LinkedHashSet<String> out = new LinkedHashSet<>();
        expand(id, out, new HashSet<>());
        List<String> result = List.copyOf(out);
        expanded.put(id, result);
        return result;
    }

    boolean contains(String tag, String id) {
        return sets.computeIfAbsent(strip(tag), t -> Set.copyOf(get(t))).contains(id);
    }

    /** "#tag" -> its members; a plain id -> itself. */
    List<String> resolve(String ref) {
        return ref.startsWith("#") ? get(ref) : List.of(VanillaKnowledge.id(ref));
    }

    /** Resolves a JSON ingredient or holder set: "id", "#tag" or a list of those. */
    List<String> resolve(JsonElement element) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (element == null || element.isJsonNull()) return List.of();
        if (element.isJsonPrimitive()) {
            out.addAll(resolve(element.getAsString()));
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement e : array) out.addAll(resolve(e));
        } else if (element.isJsonObject()) {
            // Pre-1.21.2 ingredient objects, kept for robustness against old-format data packs.
            JsonObject o = element.getAsJsonObject();
            if (o.has("item")) out.addAll(resolve(o.get("item").getAsString()));
            if (o.has("tag")) out.addAll(get(o.get("tag").getAsString()));
        }
        return List.copyOf(out);
    }

    private void expand(String id, LinkedHashSet<String> out, Set<String> visiting) {
        List<String> values = raw.get(id);
        if (values == null || !visiting.add(id)) return;
        for (String value : values) {
            if (value.startsWith("#")) expand(strip(value), out, visiting);
            else out.add(VanillaKnowledge.id(value));
        }
    }

    private static String strip(String tag) {
        return VanillaKnowledge.id(tag.startsWith("#") ? tag.substring(1) : tag);
    }
}
