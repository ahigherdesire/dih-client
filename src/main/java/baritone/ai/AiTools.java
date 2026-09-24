/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * The AI's hands. Five tools, no more: the mod's own command set is the real action space, so
 * {@code run_command} does most of the work and the rest is looking, waiting, talking and
 * remembering.
 *
 * <p>Every tool returns a short human-readable string. That string is the only feedback the
 * model gets, so it says what happened <i>and</i> where things stand afterwards.
 */
public final class AiTools {

    private AiTools() {}

    /** How long to let an async command settle before reporting back. */
    private static final long SETTLE_MILLIS = 1500L;

    public static JsonArray definitions() {
        JsonArray tools = new JsonArray();

        tools.add(tool("run_command",
                "Run one DIH Client command, e.g. \"goto 100 64 -200\", \"mine diamond_ore\", "
                        + "\"follow player Steve\", \"stop\". Omit the # prefix. Commands start a job and return "
                        + "immediately; check progress with look_around.",
                object(
                        property("command", "string", "The command with its arguments, without the # prefix.")
                ),
                "command"));

        tools.add(tool("say",
                "Say something in server chat. Use it to answer players and to report what you are doing.",
                object(
                        property("message", "string", "Plain text, under 200 characters. Cannot start with /.")
                ),
                "message"));

        tools.add(tool("look_around",
                "Get a fresh report of your position, health, food, inventory, nearby players and mobs, "
                        + "and what job is currently running.",
                object(),
                null));

        tools.add(tool("wait",
                "Do nothing for a few seconds while a job runs, then get a fresh status report.",
                object(
                        property("seconds", "integer", "How long to wait, 1 to 30.")
                ),
                "seconds"));

        tools.add(tool("remember",
                "Store one short fact for later sessions, such as a base coordinate or a player's habits.",
                object(
                        property("fact", "string", "One sentence.")
                ),
                "fact"));

        return tools;
    }

    public static String execute(AiBrain brain, LlmClient.ToolCall call) {
        if (call.arguments.has("__malformed")) {
            return "Your arguments were not valid JSON. Try again with a proper JSON object.";
        }
        try {
            switch (call.name == null ? "" : call.name) {
                case "run_command":
                    return runCommand(brain, call.string("command", ""));
                case "say":
                    return brain.speak(call.string("message", ""));
                case "look_around":
                    return brain.onGameThread(() -> WorldSnapshot.describe(brain.getBaritone()), "Cannot see anything right now.");
                case "wait":
                    return waitFor(brain, call.integer("seconds", 5));
                case "remember":
                    return brain.getMemory().remember(call.string("fact", ""));
                default:
                    return "No such tool: " + call.name;
            }
        } catch (Exception e) {
            return "Tool failed: " + e;
        }
    }

    private static String runCommand(AiBrain brain, String rawInput) {
        String raw = rawInput == null ? "" : rawInput.trim();
        while (raw.startsWith("#")) {
            raw = raw.substring(1).trim();
        }
        if (raw.isEmpty()) {
            return "No command given.";
        }
        if (raw.startsWith("/")) {
            return "Refused: those are server commands, and you may only run DIH Client commands.";
        }

        String name = raw.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (!brain.getConfig().isCommandAllowed(name)) {
            return "Refused: \"" + name + "\" is on the deny list and cannot be run by the AI.";
        }

        final String command = raw;
        Boolean handled = brain.onGameThread(
                () -> brain.getBaritone().getCommandManager().execute(command),
                Boolean.FALSE
        );
        if (!Boolean.TRUE.equals(handled)) {
            return "\"" + name + "\" is not a real command, or it rejected those arguments. "
                    + "Check the command list before trying again.";
        }

        AiBrain.sleepQuietly(SETTLE_MILLIS);
        return "Ran #" + command + ". You are now " + brain.onGameThread(() -> WorldSnapshot.brief(brain.getBaritone()), "somewhere") + ".";
    }

    private static String waitFor(AiBrain brain, int seconds) {
        int clamped = Math.max(1, Math.min(30, seconds));
        AiBrain.sleepQuietly(clamped * 1000L);
        return "Waited " + clamped + "s. You are now "
                + brain.onGameThread(() -> WorldSnapshot.brief(brain.getBaritone()), "somewhere") + ".";
    }

    // ── JSON schema helpers ─────────────────────────────────────────────────

    private static JsonObject tool(String name, String description, JsonObject properties, String required) {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", properties);
        JsonArray requiredArray = new JsonArray();
        if (required != null) {
            requiredArray.add(required);
        }
        parameters.add("required", requiredArray);

        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        function.add("parameters", parameters);

        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }

    private static JsonObject object(JsonObject... entries) {
        JsonObject properties = new JsonObject();
        for (JsonObject entry : entries) {
            for (String key : entry.keySet()) {
                properties.add(key, entry.get(key));
            }
        }
        return properties;
    }

    private static JsonObject property(String name, String type, String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", type);
        schema.addProperty("description", description);
        JsonObject wrapper = new JsonObject();
        wrapper.add(name, schema);
        return wrapper;
    }
}
