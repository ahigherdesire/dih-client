package baritone.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Arguments of the acquire and plan_item tools, as models actually send them. */
final class ItemRequestTest {

    private static ItemRequest parse(String json) {
        JsonObject args = JsonParser.parseString(json).getAsJsonObject();
        return ItemRequest.parse(new LlmClient.ToolCall("call_1", "acquire", args));
    }

    @Test
    void countDefaultsToOne() {
        ItemRequest request = parse("{\"item\": \"iron_pickaxe\"}");
        assertTrue(request.ok());
        assertEquals("iron_pickaxe", request.item());
        assertEquals(1, request.count());

        assertEquals(1, parse("{\"item\": \"torch\", \"count\": null}").count());
    }

    @Test
    void acceptsCountAsNumberOrString() {
        assertEquals(3, parse("{\"item\": \"iron_ingot\", \"count\": 3}").count());
        assertEquals(64, parse("{\"item\": \"torch\", \"count\": \"64\"}").count());
        assertEquals(5, parse("{\"item\": \"torch\", \"count\": 5.0}").count());
    }

    @Test
    void readsACountWrittenIntoTheItemText() {
        ItemRequest leading = parse("{\"item\": \"3 iron_ingot\"}");
        assertEquals("iron_ingot", leading.item());
        assertEquals(3, leading.count());

        ItemRequest times = parse("{\"item\": \"16x oak planks\"}");
        assertEquals("oak planks", times.item());
        assertEquals(16, times.count());

        ItemRequest trailing = parse("{\"item\": \"torch x 12\"}");
        assertEquals("torch", trailing.item());
        assertEquals(12, trailing.count());
    }

    @Test
    void anExplicitCountWinsOverTheItemText() {
        ItemRequest request = parse("{\"item\": \"3 iron_ingot\", \"count\": 5}");
        assertEquals("iron_ingot", request.item());
        assertEquals(5, request.count());
    }

    @Test
    void keepsPlainWordsAndNamespacesForTheResolver() {
        assertEquals("iron pick", parse("{\"item\": \"  iron   pick \"}").item());
        assertEquals("minecraft:torch", parse("{\"item\": \"minecraft:torch\"}").item());
    }

    @Test
    void rejectsMissingOrBlankItems() {
        for (String json : new String[]{"{}", "{\"item\": \"\"}", "{\"item\": \"   \"}", "{\"count\": 3}"}) {
            ItemRequest request = parse(json);
            assertFalse(request.ok(), json);
            assertNotNull(request.error());
        }
    }

    @Test
    void rejectsBadCounts() {
        assertFalse(parse("{\"item\": \"torch\", \"count\": 0}").ok());
        assertFalse(parse("{\"item\": \"torch\", \"count\": -4}").ok());
        assertFalse(parse("{\"item\": \"torch\", \"count\": 2.5}").ok());
        assertFalse(parse("{\"item\": \"torch\", \"count\": \"a few\"}").ok());
        assertFalse(parse("{\"item\": \"torch\", \"count\": [3]}").ok());
        assertFalse(parse("{\"item\": \"torch\", \"count\": " + (ItemRequest.MAX_COUNT + 1) + "}").ok());
        assertTrue(parse("{\"item\": \"torch\", \"count\": " + ItemRequest.MAX_COUNT + "}").ok());
    }

    @Test
    void rejectsAbsurdlyLongNames() {
        assertFalse(parse("{\"item\": \"" + "a".repeat(ItemRequest.MAX_ITEM_CHARS + 1) + "\"}").ok());
    }
}
