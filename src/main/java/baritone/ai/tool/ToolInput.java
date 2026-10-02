package baritone.ai.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A tool's checked arguments. Anything the caller left out reads as the schema's default. */
public final class ToolInput {

    private final ToolSchema schema;
    private final JsonObject args;

    public ToolInput(ToolSchema schema, JsonObject args) {
        this.schema = schema;
        this.args = args == null ? new JsonObject() : args;
    }

    /** Whether the caller gave this argument (defaults don't count). */
    public boolean has(String name) {
        JsonElement element = this.args.get(name);
        return element != null && !element.isJsonNull();
    }

    public String string(String name) {
        if (has(name)) {
            return this.args.get(name).getAsString();
        }
        Object fallback = fallback(name);
        return fallback == null ? "" : fallback.toString();
    }

    public int integer(String name) {
        if (has(name)) {
            return this.args.get(name).getAsInt();
        }
        return fallback(name) instanceof Number n ? n.intValue() : 0;
    }

    public double number(String name) {
        if (has(name)) {
            return this.args.get(name).getAsDouble();
        }
        return fallback(name) instanceof Number n ? n.doubleValue() : 0;
    }

    public boolean bool(String name) {
        if (has(name)) {
            return this.args.get(name).getAsBoolean();
        }
        return fallback(name) instanceof Boolean b && b;
    }

    /** A copy of the given arguments, for tools that hand them on. */
    public JsonObject json() {
        return this.args.deepCopy();
    }

    private Object fallback(String name) {
        ToolSchema.Param param = this.schema.param(name);
        return param == null ? null : param.defaultValue();
    }
}
