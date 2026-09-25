package baritone.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tool list is sent to the model verbatim; a malformed schema makes every request fail. */
final class AiToolsDefinitionTest {

    private static final Pattern NAME = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");
    private static final Set<String> JSON_TYPES = Set.of("string", "integer", "number", "boolean", "array", "object");

    private static Map<String, JsonObject> functionsByName() {
        Map<String, JsonObject> byName = new LinkedHashMap<>();
        for (JsonElement element : AiTools.definitions()) {
            JsonObject tool = element.getAsJsonObject();
            assertEquals("function", tool.get("type").getAsString());
            JsonObject function = tool.getAsJsonObject("function");
            String name = function.get("name").getAsString();
            assertTrue(byName.put(name, function) == null, "duplicate tool " + name);
        }
        return byName;
    }

    @Test
    void offersTheExpectedTools() {
        assertEquals(
                List.of("run_command", "acquire", "plan_item", "find", "say", "look_around", "wait", "remember"),
                List.copyOf(functionsByName().keySet()));
    }

    @Test
    void everySchemaIsWellFormed() {
        for (Map.Entry<String, JsonObject> entry : functionsByName().entrySet()) {
            String name = entry.getKey();
            JsonObject function = entry.getValue();
            assertTrue(NAME.matcher(name).matches(), "bad tool name " + name);
            assertFalse(function.get("description").getAsString().isBlank(), name + " has no description");

            JsonObject parameters = function.getAsJsonObject("parameters");
            assertEquals("object", parameters.get("type").getAsString(), name);
            JsonObject properties = parameters.getAsJsonObject("properties");
            for (String key : properties.keySet()) {
                JsonObject property = properties.getAsJsonObject(key);
                assertTrue(JSON_TYPES.contains(property.get("type").getAsString()), name + "." + key + " type");
                assertFalse(property.get("description").getAsString().isBlank(), name + "." + key + " description");
            }
            Set<String> seen = new HashSet<>();
            for (JsonElement required : parameters.getAsJsonArray("required")) {
                String key = required.getAsString();
                assertTrue(properties.has(key), name + " requires unknown property " + key);
                assertTrue(seen.add(key), name + " lists " + key + " twice");
            }
        }
    }

    @Test
    void acquireAndPlanTakeARequiredItemAndOptionalCount() {
        Map<String, JsonObject> tools = functionsByName();
        for (String name : List.of("acquire", "plan_item")) {
            JsonObject parameters = tools.get(name).getAsJsonObject("parameters");
            JsonObject properties = parameters.getAsJsonObject("properties");
            assertEquals("string", properties.getAsJsonObject("item").get("type").getAsString());
            assertEquals("integer", properties.getAsJsonObject("count").get("type").getAsString());
            JsonArray required = parameters.getAsJsonArray("required");
            assertEquals(1, required.size(), name);
            assertEquals("item", required.get(0).getAsString(), name);
        }
        assertTrue(tools.get("acquire").get("description").getAsString().contains("instead of chaining mine and craft"));
        assertTrue(tools.get("plan_item").get("description").getAsString().contains("how do I make X"));
    }

    @Test
    void survivesAJsonRoundTrip() {
        JsonArray tools = AiTools.definitions();
        assertEquals(tools, JsonParser.parseString(tools.toString()));
    }

    @Test
    void promptGuideMentionsTheToolsAndStop() {
        String on = AiTools.acquireGuide(true);
        assertTrue(on.contains("acquire") && on.contains("plan_item"));
        assertTrue(on.contains("\"stop\""));
        assertTrue(on.contains("[event]"));

        String off = AiTools.acquireGuide(false);
        assertFalse(off.contains("[event]"), "without follow-ups the model must not wait for events");
    }
}
