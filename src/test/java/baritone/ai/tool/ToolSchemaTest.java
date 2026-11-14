package baritone.ai.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The schema builder renders OpenAI function parameters; a malformed one makes every model request fail. */
final class ToolSchemaTest {

    private static ToolSchema gotoSchema() {
        return ToolSchema.builder()
                .integer("x", "X coordinate").required()
                .integer("y", "Y coordinate").required()
                .enumOf("mode", "How to get there", "walk", "sprint").defaultsTo("walk")
                .number("radius", "How close is close enough").range(0.5, 16)
                .bool("sneak", "Sneak the whole way")
                .build();
    }

    @Test
    void rendersSeveralRequiredParamsEnumsAndRanges() {
        JsonObject json = gotoSchema().toJson();
        assertEquals("object", json.get("type").getAsString());

        JsonObject properties = json.getAsJsonObject("properties");
        assertEquals(List.of("x", "y", "mode", "radius", "sneak"), List.copyOf(properties.keySet()));
        assertEquals("integer", properties.getAsJsonObject("x").get("type").getAsString());
        assertEquals("X coordinate", properties.getAsJsonObject("x").get("description").getAsString());
        assertFalse(properties.getAsJsonObject("x").has("enum"));

        JsonObject mode = properties.getAsJsonObject("mode");
        assertEquals("string", mode.get("type").getAsString());
        JsonArray values = mode.getAsJsonArray("enum");
        assertEquals(2, values.size());
        assertEquals("walk", values.get(0).getAsString());
        assertEquals("sprint", values.get(1).getAsString());
        assertEquals("walk", mode.get("default").getAsString());

        JsonObject radius = properties.getAsJsonObject("radius");
        assertEquals("number", radius.get("type").getAsString());
        assertEquals(0.5, radius.get("minimum").getAsDouble());
        assertEquals(16.0, radius.get("maximum").getAsDouble());
        assertEquals("boolean", properties.getAsJsonObject("sneak").get("type").getAsString());

        JsonArray required = json.getAsJsonArray("required");
        assertEquals(2, required.size());
        assertEquals("x", required.get(0).getAsString());
        assertEquals("y", required.get(1).getAsString());

        assertEquals(json, JsonParser.parseString(json.toString()));
    }

    @Test
    void integerRangesRenderAsWholeNumbers() {
        JsonObject seconds = ToolSchema.builder().integer("seconds", "How long").range(1, 30).defaultsTo(5).build()
                .toJson().getAsJsonObject("properties").getAsJsonObject("seconds");
        assertEquals("1", seconds.get("minimum").toString());
        assertEquals("30", seconds.get("maximum").toString());
        assertEquals("5", seconds.get("default").toString());
    }

    @Test
    void anEmptySchemaIsAnEmptyObject() {
        JsonObject json = ToolSchema.EMPTY.toJson();
        assertEquals("object", json.get("type").getAsString());
        assertTrue(json.getAsJsonObject("properties").isEmpty());
        assertTrue(json.getAsJsonArray("required").isEmpty());
    }

    @Test
    void usageShowsRequiredThenOptional() {
        assertEquals("goto <x> <y> [mode] [radius] [sneak]", gotoSchema().usage("goto"));
        assertEquals("look_around", ToolSchema.EMPTY.usage("look_around"));
    }

    @Test
    void describesEachParamForHelp() {
        ToolSchema.Param seconds = ToolSchema.builder().integer("seconds", "How long").range(1, 30).defaultsTo(5)
                .build().param("seconds");
        assertEquals("seconds (integer, 1 to 30, default 5): How long", seconds.describe());
        ToolSchema.Param mode = gotoSchema().param("mode");
        assertEquals("mode (one of walk, sprint, default walk): How to get there", mode.describe());
        assertEquals("x (integer, required): X coordinate", gotoSchema().param("x").describe());
    }

    @Test
    void rejectsBadDefinitions() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolSchema.builder().string("item", "a").string("item", "b").build(), "duplicate");
        assertThrows(IllegalArgumentException.class,
                () -> ToolSchema.builder().string("Item Name", "a").build(), "not snake_case");
        assertThrows(IllegalArgumentException.class,
                () -> ToolSchema.builder().string("a", "a").string("b", "b").required().build(),
                "a required param after an optional one breaks positional arguments");
        assertThrows(IllegalArgumentException.class,
                () -> ToolSchema.builder().integer("n", "n").range(5, 1).build(), "min above max");
        assertThrows(IllegalArgumentException.class,
                () -> ToolSchema.builder().enumOf("m", "m", "a", "b").defaultsTo("c").build(), "default not a value");
        assertThrows(IllegalArgumentException.class,
                () -> ToolSchema.builder().integer("n", "n").range(1, 3).defaultsTo(9).build(), "default out of range");
        assertThrows(IllegalStateException.class,
                () -> ToolSchema.builder().required(), "a modifier needs a param");
        assertThrows(IllegalArgumentException.class,
                () -> ToolSchema.builder().string("a", " ").build(), "every param is described");
        assertThrows(IllegalArgumentException.class,
                () -> ToolSchema.builder().bool("b", "b").range(0, 1).build(), "ranges are for numbers");
    }
}
