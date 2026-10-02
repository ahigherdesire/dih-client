package baritone.ai.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a tool's arguments into checked JSON. Typed arguments ({@code .tools goto 100 64 -200}, macro steps) go
 * through {@link #parseLine}; the model's JSON goes through {@link #validate}. Both coerce the same way, so a tool
 * sees the same thing whoever called it, and both return an error a player or a model can act on.
 *
 * <p>Typed arguments are positional in schema order, or {@code key=value} in any order. Quotes ({@code "…"} or
 * {@code '…'}) keep spaces, and when the last free parameter is plain text it takes the rest of the line, so
 * {@code .tools say hello there} needs no quotes.
 */
public final class ToolArgs {

    private ToolArgs() {
    }

    /** Exactly one of {@code args} and {@code error} is set. Defaults are not filled in; {@link ToolInput} does that. */
    public record Parsed(JsonObject args, String error) {
        static Parsed ok(JsonObject args) {
            return new Parsed(args, null);
        }

        static Parsed error(String error) {
            return new Parsed(null, error);
        }

        public boolean ok() {
            return this.error == null;
        }
    }

    /** A word of the line. {@code keyEnd} is where an unquoted {@code =} sits, or -1. */
    private record Token(String text, int keyEnd) {
    }

    public static Parsed parseLine(ToolSchema schema, String line) {
        List<Token> tokens = tokenize(line == null ? "" : line);
        if (tokens == null) {
            return Parsed.error("Unfinished quote in the arguments.");
        }
        Map<String, String> keyed = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();
        for (Token token : tokens) {
            ToolSchema.Param param = token.keyEnd() > 0 ? schema.param(token.text().substring(0, token.keyEnd())) : null;
            if (param == null) {
                positional.add(token.text());
                continue;
            }
            if (keyed.containsKey(param.name())) {
                return Parsed.error(param.name() + " is given twice.");
            }
            keyed.put(param.name(), token.text().substring(token.keyEnd() + 1));
        }

        List<ToolSchema.Param> free = new ArrayList<>();
        for (ToolSchema.Param param : schema.params()) {
            if (!keyed.containsKey(param.name())) {
                free.add(param);
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < positional.size(); i++) {
            if (i >= free.size()) {
                return Parsed.error("Too many arguments. Usage: " + usage(schema));
            }
            ToolSchema.Param param = free.get(i);
            boolean last = i == free.size() - 1;
            if (last && positional.size() > free.size() && param.type() == ToolSchema.Type.STRING && !param.isEnum()) {
                values.put(param.name(), String.join(" ", positional.subList(i, positional.size())));
                break;
            }
            values.put(param.name(), positional.get(i));
        }
        values.putAll(keyed);

        JsonObject out = new JsonObject();
        for (ToolSchema.Param param : schema.params()) {
            String raw = values.get(param.name());
            if (raw == null) {
                continue;
            }
            Object coerced = coerce(param, raw);
            if (coerced instanceof String error) {
                return Parsed.error(error);
            }
            out.add(param.name(), (JsonElement) coerced);
        }
        String missing = missing(schema, out);
        return missing == null ? Parsed.ok(out) : Parsed.error(missing);
    }

    /** The model's arguments, coerced to the schema: {@code "3"} becomes 3, unknown keys are dropped, null is unset. */
    public static Parsed validate(ToolSchema schema, JsonObject in) {
        JsonObject out = new JsonObject();
        for (ToolSchema.Param param : schema.params()) {
            JsonElement element = find(in, param.name());
            if (element == null || element.isJsonNull()) {
                continue;
            }
            String raw;
            if (element.isJsonPrimitive()) {
                raw = element.getAsString();
            } else if (param.type() == ToolSchema.Type.STRING && !param.isEnum()) {
                raw = element.toString();
            } else {
                return Parsed.error(param.name() + " must be " + article(param) + ", not " + (element.isJsonArray() ? "a list" : "an object") + ".");
            }
            Object coerced = coerce(param, raw);
            if (coerced instanceof String error) {
                return Parsed.error(error);
            }
            out.add(param.name(), (JsonElement) coerced);
        }
        String missing = missing(schema, out);
        return missing == null ? Parsed.ok(out) : Parsed.error(missing);
    }

    /**
     * Completions for the argument being typed at the end of {@code line}: enum values or true/false. The text they
     * replace starts at {@link #completionStart}.
     */
    public static List<String> suggestions(ToolSchema schema, String line) {
        String text = line == null ? "" : line;
        List<Token> tokens = tokenize(text);
        if (tokens == null) {
            return List.of();
        }
        boolean fresh = text.isEmpty() || Character.isWhitespace(text.charAt(text.length() - 1));
        Token current = fresh || tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
        List<Token> done = current == null ? tokens : tokens.subList(0, tokens.size() - 1);

        ToolSchema.Param param = null;
        String prefix = "";
        if (current != null && current.keyEnd() > 0) {
            param = schema.param(current.text().substring(0, current.keyEnd()));
            prefix = current.text().substring(current.keyEnd() + 1);
        }
        if (param == null) {
            List<String> keyed = new ArrayList<>();
            int positional = 0;
            for (Token token : done) {
                ToolSchema.Param named = token.keyEnd() > 0 ? schema.param(token.text().substring(0, token.keyEnd())) : null;
                if (named != null) {
                    keyed.add(named.name());
                } else {
                    positional++;
                }
            }
            int index = 0;
            for (ToolSchema.Param candidate : schema.params()) {
                if (keyed.contains(candidate.name())) {
                    continue;
                }
                if (index++ == positional) {
                    param = candidate;
                    break;
                }
            }
            prefix = current == null ? "" : current.text();
        }
        if (param == null) {
            return List.of();
        }
        List<String> options = param.isEnum() ? param.values()
                : param.type() == ToolSchema.Type.BOOLEAN ? List.of("true", "false") : List.of();
        String start = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(start)) {
                out.add(option);
            }
        }
        return out;
    }

    /** Where the text that {@link #suggestions} replaces begins: after the last space, and after {@code key=}. */
    public static int completionStart(String line) {
        String text = line == null ? "" : line;
        int start = text.length();
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        int equals = text.indexOf('=', start);
        return equals >= 0 ? equals + 1 : start;
    }

    // ── Internals ───────────────────────────────────────────────────────────

    /** Splits on spaces outside quotes; backslash escapes inside quotes. Null for an unfinished quote. */
    private static List<Token> tokenize(String line) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inToken = false;
        char quote = 0;
        int keyEnd = -1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < line.length()) {
                    current.append(line.charAt(++i));
                } else if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(new Token(current.toString(), keyEnd));
                    current.setLength(0);
                    inToken = false;
                    keyEnd = -1;
                }
                continue;
            }
            inToken = true;
            if (c == '"' || c == '\'') {
                quote = c;
            } else {
                if (c == '=' && keyEnd < 0) {
                    keyEnd = current.length();
                }
                current.append(c);
            }
        }
        if (quote != 0) {
            return null;
        }
        if (inToken) {
            tokens.add(new Token(current.toString(), keyEnd));
        }
        return tokens;
    }

    /** A {@link JsonElement} on success, or the error text as a {@link String}. */
    private static Object coerce(ToolSchema.Param param, String raw) {
        String name = param.name();
        switch (param.type()) {
            case STRING: {
                if (!param.isEnum()) {
                    return new JsonPrimitive(raw);
                }
                String key = normalize(raw);
                for (String value : param.values()) {
                    if (normalize(value).equals(key)) {
                        return new JsonPrimitive(value);
                    }
                }
                return name + " must be one of: " + String.join(", ", param.values()) + ".";
            }
            case BOOLEAN: {
                switch (raw.trim().toLowerCase(Locale.ROOT)) {
                    case "true", "yes", "on", "1":
                        return new JsonPrimitive(true);
                    case "false", "no", "off", "0":
                        return new JsonPrimitive(false);
                    default:
                        return name + " must be true or false.";
                }
            }
            default: {
                double value;
                try {
                    value = Double.parseDouble(raw.trim());
                } catch (NumberFormatException e) {
                    value = Double.NaN;
                }
                boolean integer = param.type() == ToolSchema.Type.INTEGER;
                if (Double.isNaN(value) || Double.isInfinite(value) || integer && (value != Math.rint(value) || Math.abs(value) > Long.MAX_VALUE / 2)) {
                    return name + (integer ? " must be a whole number" : " must be a number") + ", not \"" + raw + "\".";
                }
                if (param.min() != null && value < param.min()) {
                    return name + " must be at least " + ToolSchema.number(param.min()) + ".";
                }
                if (param.max() != null && value > param.max()) {
                    return name + " must be at most " + ToolSchema.number(param.max()) + ".";
                }
                return integer ? new JsonPrimitive((long) value) : new JsonPrimitive(value);
            }
        }
    }

    private static String missing(ToolSchema schema, JsonObject args) {
        for (ToolSchema.Param param : schema.params()) {
            if (param.required() && !args.has(param.name())) {
                return "Missing " + param.name() + " (" + param.description() + "). Usage: " + usage(schema);
            }
        }
        return null;
    }

    private static String usage(ToolSchema schema) {
        return schema.params().isEmpty() ? "no arguments" : schema.usage("").trim();
    }

    private static JsonElement find(JsonObject in, String name) {
        if (in == null) {
            return null;
        }
        if (in.has(name)) {
            return in.get(name);
        }
        for (String key : in.keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return in.get(key);
            }
        }
        return null;
    }

    private static String article(ToolSchema.Param param) {
        return switch (param.type()) {
            case INTEGER -> "a whole number";
            case NUMBER -> "a number";
            case BOOLEAN -> "true or false";
            case STRING -> "text";
        };
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
