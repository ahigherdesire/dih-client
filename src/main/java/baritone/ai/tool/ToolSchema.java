package baritone.ai.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A tool's parameters, in order. The order matters twice: it is the positional order for {@code .tools} and macro
 * steps, and it is the order the model sees, which stays fixed so providers can cache the prompt.
 *
 * <pre>{@code
 * ToolSchema.builder()
 *         .string("item", "The item id").required()
 *         .integer("count", "How many").range(1, 2304).defaultsTo(1)
 *         .enumOf("mode", "How to get there", "walk", "sprint").defaultsTo("walk")
 *         .build();
 * }</pre>
 */
public final class ToolSchema {

    public static final ToolSchema EMPTY = new ToolSchema(List.of());

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    public enum Type {
        STRING("string"), INTEGER("integer"), NUMBER("number"), BOOLEAN("boolean");

        private final String json;

        Type(String json) {
            this.json = json;
        }

        public String json() {
            return this.json;
        }
    }

    /**
     * One parameter. {@code values} is non-empty for an enum (always a string); {@code min}/{@code max} are only for
     * numbers; {@code defaultValue} is what {@link ToolInput} returns when the caller left it out.
     */
    public record Param(String name, Type type, String description, boolean required, List<String> values,
                        Object defaultValue, Double min, Double max) {

        public boolean isEnum() {
            return !this.values.isEmpty();
        }

        /** One line for {@code .tools help}: {@code seconds (integer, 1 to 30, default 5): How long}. */
        public String describe() {
            List<String> parts = new ArrayList<>();
            parts.add(isEnum() ? "one of " + String.join(", ", this.values) : this.type.json());
            if (this.min != null && this.max != null) {
                parts.add(number(this.min) + " to " + number(this.max));
            } else if (this.min != null) {
                parts.add("at least " + number(this.min));
            } else if (this.max != null) {
                parts.add("at most " + number(this.max));
            }
            if (this.required) {
                parts.add("required");
            } else if (this.defaultValue != null) {
                parts.add("default " + this.defaultValue);
            }
            return this.name + " (" + String.join(", ", parts) + "): " + this.description;
        }

        String usageToken() {
            return this.required ? "<" + this.name + ">" : "[" + this.name + "]";
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("type", this.type.json());
            json.addProperty("description", this.description);
            if (isEnum()) {
                JsonArray array = new JsonArray();
                this.values.forEach(array::add);
                json.add("enum", array);
            }
            if (this.min != null) {
                json.add("minimum", primitive(this.min));
            }
            if (this.max != null) {
                json.add("maximum", primitive(this.max));
            }
            if (this.defaultValue instanceof Number n) {
                json.add("default", primitive(n.doubleValue()));
            } else if (this.defaultValue instanceof Boolean b) {
                json.addProperty("default", b);
            } else if (this.defaultValue != null) {
                json.addProperty("default", this.defaultValue.toString());
            }
            return json;
        }
    }

    private final List<Param> params;

    private ToolSchema(List<Param> params) {
        this.params = Collections.unmodifiableList(params);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Param> params() {
        return this.params;
    }

    /** The param with this name, or null. */
    public Param param(String name) {
        for (Param param : this.params) {
            if (param.name().equalsIgnoreCase(name)) {
                return param;
            }
        }
        return null;
    }

    /** The {@code parameters} object of an OpenAI function definition. */
    public JsonObject toJson() {
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (Param param : this.params) {
            properties.add(param.name(), param.toJson());
            if (param.required()) {
                required.add(param.name());
            }
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "object");
        json.add("properties", properties);
        json.add("required", required);
        return json;
    }

    /** {@code goto <x> <y> [mode]}. */
    public String usage(String toolName) {
        StringBuilder sb = new StringBuilder(toolName);
        for (Param param : this.params) {
            sb.append(' ').append(param.usageToken());
        }
        return sb.toString();
    }

    /** Whole numbers render without a fraction so integer schemas stay integers. */
    static JsonPrimitive primitive(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15 ? new JsonPrimitive((long) value) : new JsonPrimitive(value);
    }

    static String number(double value) {
        return primitive(value).toString();
    }

    /** Adds params in order; {@code required}, {@code defaultsTo} and {@code range} apply to the last one added. */
    public static final class Builder {
        private final List<Param> params = new ArrayList<>();

        private Builder() {
        }

        public Builder string(String name, String description) {
            return add(name, Type.STRING, description, List.of());
        }

        public Builder integer(String name, String description) {
            return add(name, Type.INTEGER, description, List.of());
        }

        public Builder number(String name, String description) {
            return add(name, Type.NUMBER, description, List.of());
        }

        public Builder bool(String name, String description) {
            return add(name, Type.BOOLEAN, description, List.of());
        }

        /** A string that must be one of {@code values}; callers may use any case and dashes for underscores. */
        public Builder enumOf(String name, String description, String... values) {
            if (values.length == 0) {
                throw new IllegalArgumentException(name + ": an enum needs values");
            }
            return add(name, Type.STRING, description, List.of(values));
        }

        public Builder required() {
            Param last = last();
            return replace(new Param(last.name(), last.type(), last.description(), true, last.values(),
                    last.defaultValue(), last.min(), last.max()));
        }

        public Builder defaultsTo(Object value) {
            Param last = last();
            return replace(new Param(last.name(), last.type(), last.description(), last.required(), last.values(),
                    value, last.min(), last.max()));
        }

        public Builder range(double min, double max) {
            if (min > max) {
                throw new IllegalArgumentException(last().name() + ": min " + min + " is above max " + max);
            }
            return bounds(min, max);
        }

        /** A lower bound only, e.g. a count of at least 1. */
        public Builder min(double min) {
            return bounds(min, null);
        }

        private Builder bounds(Double min, Double max) {
            Param last = last();
            if (last.type() != Type.INTEGER && last.type() != Type.NUMBER) {
                throw new IllegalArgumentException(last.name() + ": ranges are for numbers");
            }
            return replace(new Param(last.name(), last.type(), last.description(), last.required(), last.values(),
                    last.defaultValue(), min, max));
        }

        public ToolSchema build() {
            boolean optionalSeen = false;
            for (Param param : this.params) {
                if (param.required() && optionalSeen) {
                    throw new IllegalArgumentException(param.name() + ": required params must come before optional ones");
                }
                optionalSeen |= !param.required();
                Object value = param.defaultValue();
                if (value == null) {
                    continue;
                }
                if (param.isEnum() && !param.values().contains(value.toString())) {
                    throw new IllegalArgumentException(param.name() + ": default " + value + " is not one of " + param.values());
                }
                if (value instanceof Number n && (param.min() != null && n.doubleValue() < param.min()
                        || param.max() != null && n.doubleValue() > param.max())) {
                    throw new IllegalArgumentException(param.name() + ": default " + value + " is out of range");
                }
            }
            return this.params.isEmpty() ? EMPTY : new ToolSchema(new ArrayList<>(this.params));
        }

        private Builder add(String name, Type type, String description, List<String> values) {
            if (name == null || !NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("param names are snake_case: " + name);
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException(name + " needs a description");
            }
            for (Param param : this.params) {
                if (param.name().equals(name)) {
                    throw new IllegalArgumentException("duplicate param " + name);
                }
            }
            for (String value : values) {
                if (!value.equals(value.toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException(name + ": enum values are lower case: " + value);
                }
            }
            this.params.add(new Param(name, type, description.trim(), false, values, null, null, null));
            return this;
        }

        private Param last() {
            if (this.params.isEmpty()) {
                throw new IllegalStateException("add a param first");
            }
            return this.params.get(this.params.size() - 1);
        }

        private Builder replace(Param param) {
            this.params.set(this.params.size() - 1, param);
            return this;
        }
    }
}
