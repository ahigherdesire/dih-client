package dihclient.script.lang;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.util.*;

/** JSON values use the interpreter's mutable maps/lists; quoted identifiers remain strings. */
public final class ScriptJson {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private ScriptJson() {}

    public static Library create() {
        return new Library()
            .define("JSON", "jsonParse(text, fallback)", "Parse strict JSON. Invalid input returns the optional fallback, or raises an error if omitted.", 1, 2, a -> {
                try { return parse(a.string(0)); }
                catch (ScriptException e) { if (a.size() > 1) return a.get(1); throw e; }
            })
            .define("JSON", "jsonStringify(value)", "Encode a script value as JSON.", 1, 1, a -> stringify(a.get(0)))
            .define("JSON", "object()", "Create an empty mutable object; use value[\"key\"] to read/write.", 0, 0, a -> new LinkedHashMap<String, Object>())
            .define("JSON", "keys(object)", "List an object's keys.", 1, 1, a -> {
                if (!(a.get(0) instanceof Map<?, ?> map)) throw a.error("expected an object");
                return new ArrayList<>(map.keySet());
            });
    }

    public static Object parse(String text) {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setStrictness(Strictness.STRICT);
            JsonElement element = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("trailing data");
            return value(element);
        } catch (Exception e) {
            throw new ScriptException("Invalid JSON: " + e.getMessage(), 0);
        }
    }

    private static Object value(JsonElement element) {
        if (element.isJsonNull()) return null;
        if (element.isJsonObject()) {
            Map<String, Object> result = new LinkedHashMap<>();
            element.getAsJsonObject().entrySet().forEach(e -> result.put(e.getKey(), value(e.getValue())));
            return result;
        }
        if (element.isJsonArray()) {
            List<Object> result = new ArrayList<>();
            element.getAsJsonArray().forEach(e -> result.add(value(e)));
            return result;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isString()) return primitive.getAsString();
        double number = primitive.getAsDouble();
        if (!Double.isFinite(number)) throw new IllegalArgumentException("number outside supported range");
        return number;
    }

    public static String stringify(Object value) {
        try { return GSON.toJson(value); }
        catch (RuntimeException | StackOverflowError e) { throw new ScriptException("Cannot encode JSON value", 0); }
    }
}
