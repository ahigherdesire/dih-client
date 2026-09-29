package baritone.ai.director;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Long inventories are summarised so the state stays small. */
final class StateJsonTest {

    @Test
    void keepsTheBiggestStacksAndCountsTheRest() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) counts.put("item_" + i, i + 1);
        JsonObject inventory = StateJson.inventory(counts, 5);
        assertEquals(6, inventory.size());
        assertEquals(30, inventory.get("item_29").getAsInt());
        assertEquals(26, inventory.get("item_25").getAsInt());
        assertFalse(inventory.has("item_24"));
        assertEquals("25 more kinds", inventory.get("more").getAsString());

        JsonObject small = StateJson.inventory(Map.of("dirt", 3), 5);
        assertEquals(1, small.size());
    }

    @Test
    void durabilityIsLeftOverUses() {
        assertEquals("200/250", StateJson.durability(50, 250));
        assertEquals(null, StateJson.durability(0, 0));
    }
}
