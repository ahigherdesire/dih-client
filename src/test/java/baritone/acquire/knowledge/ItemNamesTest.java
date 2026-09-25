package baritone.acquire.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class ItemNamesTest {
    private static final ItemNames NAMES = new ItemNames(
            Set.of("minecraft:iron_pickaxe", "minecraft:wooden_pickaxe", "minecraft:golden_sword", "minecraft:diamond",
                    "minecraft:torch", "minecraft:oak_planks", "minecraft:oak_log", "minecraft:cobblestone", "minecraft:stick",
                    "minecraft:iron_block", "minecraft:iron_ingot", "minecraft:ender_eye", "minecraft:sweet_berries",
                    "minecraft:glass", "minecraft:jack_o_lantern", "minecraft:diamond_pickaxe"),
            Map.of("minecraft:iron_block", "Block of Iron", "minecraft:ender_eye", "Eye of Ender",
                    "minecraft:jack_o_lantern", "Jack o'Lantern", "minecraft:iron_pickaxe", "Iron Pickaxe"));

    private static void resolves(String text, String id) {
        assertEquals(Optional.of(id), NAMES.resolve(text), text);
    }

    @Test void idsAndDisplayNames() {
        resolves("minecraft:iron_pickaxe", "minecraft:iron_pickaxe");
        resolves("iron_pickaxe", "minecraft:iron_pickaxe");
        resolves("Iron Pickaxe", "minecraft:iron_pickaxe");
        resolves("  IRON   pickaxe ", "minecraft:iron_pickaxe");
        resolves("block of iron", "minecraft:iron_block");
        resolves("Eye of Ender", "minecraft:ender_eye");
        resolves("jack o'lantern", "minecraft:jack_o_lantern");
    }

    @Test void shorthandsAndPlurals() {
        resolves("iron pick", "minecraft:iron_pickaxe");
        resolves("wood pick", "minecraft:wooden_pickaxe");
        resolves("gold sword", "minecraft:golden_sword");
        resolves("diamonds", "minecraft:diamond");
        resolves("torches", "minecraft:torch");
        resolves("sticks", "minecraft:stick");
        resolves("glass", "minecraft:glass");
        resolves("sweet berries", "minecraft:sweet_berries");
        resolves("planks", "minecraft:oak_planks");
        resolves("log", "minecraft:oak_log");
        resolves("logs", "minecraft:oak_log");
        resolves("wood", "minecraft:oak_log");
        resolves("cobble", "minecraft:cobblestone");
        resolves("iron", "minecraft:iron_ingot");
        resolves("iron ingots", "minecraft:iron_ingot");
    }

    @Test void smallTyposResolveButJunkDoesNot() {
        resolves("diamnod pickaxe", "minecraft:diamond_pickaxe");
        assertEquals(Optional.empty(), NAMES.resolve("elytra"));
        assertEquals(Optional.empty(), NAMES.resolve(""));
        assertEquals(Optional.empty(), NAMES.resolve("othermod:iron_pickaxe"));
    }

    @Test void suggestionsRankTheClosestFirst() {
        List<String> s = NAMES.suggest("pickaxe", 3);
        assertEquals(3, s.size());
        assertTrue(s.stream().allMatch(id -> id.endsWith("_pickaxe")), s.toString());
        assertEquals("minecraft:iron_pickaxe", NAMES.suggest("iron pikax", 1).get(0));
        assertEquals("minecraft:torch", NAMES.suggest("torchs", 5).get(0));
        assertTrue(NAMES.suggest("zzzzqqq", 5).isEmpty());
        assertTrue(NAMES.suggest("iron", 0).isEmpty());
    }
}
