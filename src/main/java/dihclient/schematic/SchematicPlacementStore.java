package dihclient.schematic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Placements on disk: one JSON file holding, per server or singleplayer world, the placements and which one is
 * selected. Reading is lenient: a damaged file, an unknown field or a bad entry is skipped, never an error, so an
 * old or hand-edited file always loads.
 * <pre>
 * {"version": 1, "scopes": {"play.example.net": {"selected": "&lt;id&gt;", "placements": [
 *   {"id": "...", "file": "house.litematic", "name": "House", "dimension": "minecraft:overworld",
 *    "x": 10, "y": 64, "z": -3, "rotation": 90, "mirror": "none", "visible": true}]}}}
 * </pre>
 */
public final class SchematicPlacementStore {

    public static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * One server's or world's placements.
     */
    public record Scope(String selected, List<SchematicPlacement> placements) {
        public Scope {
            selected = selected == null ? "" : selected;
            placements = List.copyOf(placements);
        }
    }

    private SchematicPlacementStore() {}

    public static Map<String, Scope> read(String json) {
        Map<String, Scope> out = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return out;
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return out;
        }
        JsonObject scopes = object(root, "scopes");
        if (scopes == null) {
            return out;
        }
        for (Map.Entry<String, JsonElement> entry : scopes.entrySet()) {
            if (entry.getKey().isBlank() || !entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject scope = entry.getValue().getAsJsonObject();
            List<SchematicPlacement> placements = new ArrayList<>();
            JsonElement list = scope.get("placements");
            if (list != null && list.isJsonArray()) {
                for (JsonElement element : list.getAsJsonArray()) {
                    SchematicPlacement placement = placement(element);
                    if (placement != null) {
                        placements.add(placement);
                    }
                }
            }
            out.put(entry.getKey(), new Scope(string(scope, "selected", ""), placements));
        }
        return out;
    }

    private static SchematicPlacement placement(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject o = element.getAsJsonObject();
        String file = string(o, "file", "");
        if (file.isBlank()) {
            return null;
        }
        BlockPos origin = new BlockPos(integer(o, "x", 0), integer(o, "y", 0), integer(o, "z", 0));
        SchematicTransform transform = new SchematicTransform(integer(o, "rotation", 0),
            SchematicTransform.Mirroring.parse(string(o, "mirror", "none")));
        return new SchematicPlacement(string(o, "id", ""), file, string(o, "name", ""), string(o, "dimension", ""),
            origin, transform, bool(o, "visible", true));
    }

    public static String write(Map<String, Scope> scopes) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonObject scopesJson = new JsonObject();
        for (Map.Entry<String, Scope> entry : scopes.entrySet()) {
            if (entry.getValue().placements().isEmpty()) {
                continue;
            }
            JsonObject scope = new JsonObject();
            scope.addProperty("selected", entry.getValue().selected());
            JsonArray list = new JsonArray();
            for (SchematicPlacement p : entry.getValue().placements()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", p.id());
                o.addProperty("file", p.file());
                o.addProperty("name", p.name());
                o.addProperty("dimension", p.dimension());
                o.addProperty("x", p.origin().getX());
                o.addProperty("y", p.origin().getY());
                o.addProperty("z", p.origin().getZ());
                o.addProperty("rotation", p.transform().rotation());
                o.addProperty("mirror", p.transform().mirror().id());
                o.addProperty("visible", p.visible());
                list.add(o);
            }
            scope.add("placements", list);
            scopesJson.add(entry.getKey(), scope);
        }
        root.add("scopes", scopesJson);
        return GSON.toJson(root);
    }

    private static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        return e.getAsString();
    }

    private static int integer(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return (int) Math.round(e.getAsDouble());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return fallback;
        }
        if (e.getAsJsonPrimitive().isBoolean()) {
            return e.getAsBoolean();
        }
        String text = e.getAsString();
        return text.equalsIgnoreCase("true") || (!text.equalsIgnoreCase("false") && fallback);
    }
}
