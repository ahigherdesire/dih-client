package dihclient.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dihclient.util.DihMacro;
import dihclient.util.macro.MacroAction;
import dihclient.util.macro.MacroActionType;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a macro from steps written as JSON, for macro_write: {@code [{"type":"SEND_CHAT","fields":{"message":"hi"}}]}.
 * Every type must be a macro step type and every field a public setting of that step; the first mistake is reported.
 */
public final class MacroSteps {

    public static final int MAX_STEPS = 200;

    /** The macro, or why it couldn't be built. */
    public record Built(DihMacro macro, String error) {
        static Built failed(String error) {
            return new Built(null, error);
        }
    }

    private MacroSteps() {
    }

    public static Built build(String name, String stepsJson) {
        JsonArray steps;
        try {
            JsonElement parsed = JsonParser.parseString(stepsJson == null ? "" : stepsJson);
            if (!parsed.isJsonArray()) return Built.failed("steps must be a JSON array of {\"type\":..., \"fields\":{...}}.");
            steps = parsed.getAsJsonArray();
        } catch (RuntimeException e) {
            return Built.failed("steps isn't valid JSON: " + e.getMessage());
        }
        if (steps.isEmpty()) return Built.failed("A macro needs at least one step.");
        if (steps.size() > MAX_STEPS) return Built.failed("At most " + MAX_STEPS + " steps.");
        DihMacro macro = new DihMacro(name);
        for (int i = 0; i < steps.size(); i++) {
            String where = "Step " + (i + 1) + ": ";
            if (!steps.get(i).isJsonObject()) return Built.failed(where + "each step is an object with a type.");
            JsonObject step = steps.get(i).getAsJsonObject();
            if (!step.has("type") || !step.get("type").isJsonPrimitive()) return Built.failed(where + "missing \"type\".");
            String typeName = step.get("type").getAsString().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            MacroActionType type;
            try {
                type = MacroActionType.valueOf(typeName);
            } catch (IllegalArgumentException e) {
                return Built.failed(where + "no step type " + typeName + ".");
            }
            CompoundTag tag = new CompoundTag();
            tag.putString("type", type.name());
            MacroAction action = DihMacro.createActionFromTag(tag);
            if (action == null) return Built.failed(where + typeName + " can't be created here.");
            if (step.has("fields")) {
                if (!step.get("fields").isJsonObject()) return Built.failed(where + "\"fields\" must be an object.");
                for (Map.Entry<String, JsonElement> field : step.getAsJsonObject("fields").entrySet()) {
                    String error = set(action, field.getKey(), field.getValue());
                    if (error != null) return Built.failed(where + error);
                }
            }
            macro.actions.add(action);
        }
        return new Built(macro, null);
    }

    /** Sets one public field of {@code action} from JSON, or says why not. */
    static String set(MacroAction action, String key, JsonElement value) {
        Field field;
        try {
            field = action.getClass().getField(key);
        } catch (NoSuchFieldException e) {
            return action.getType().name() + " has no field \"" + key + "\".";
        }
        if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
            return action.getType().name() + " has no field \"" + key + "\".";
        }
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) return key + " must be a plain value.";
        String text = value.getAsString();
        Class<?> type = field.getType();
        try {
            Object parsed;
            if (type == String.class) parsed = text;
            else if (type == boolean.class || type == Boolean.class) {
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) return key + " must be true or false.";
                parsed = Boolean.parseBoolean(text);
            } else if (type == int.class || type == Integer.class) parsed = Integer.parseInt(text.trim());
            else if (type == long.class || type == Long.class) parsed = Long.parseLong(text.trim());
            else if (type == double.class || type == Double.class) parsed = Double.parseDouble(text.trim());
            else if (type == float.class || type == Float.class) parsed = Float.parseFloat(text.trim());
            else if (type.isEnum()) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object e = Enum.valueOf((Class<Enum>) type, text.trim().toUpperCase(Locale.ROOT));
                parsed = e;
            } else return key + " can't be set from here (" + type.getSimpleName() + ").";
            field.set(action, parsed);
            return null;
        } catch (NumberFormatException e) {
            return key + " must be a number, not \"" + text + "\".";
        } catch (IllegalArgumentException e) {
            return "Bad value for " + key + ": \"" + text + "\".";
        } catch (IllegalAccessException e) {
            return key + " can't be set.";
        }
    }
}
