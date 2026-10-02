package baritone.acquire.exec;

import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.Plan;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Junk choice, furnace click planning and recipe key matching. */
final class ExecHelpersTest {

    // ---- JunkPolicy

    @Test
    void junkNeverIncludesWhatThePlanStillNeeds() {
        Plan plan = new Plan("minecraft:furnace", 1, List.of(
                new Step.Mine(List.of("minecraft:stone", "minecraft:cobblestone"), "minecraft:cobblestone", 8,
                        new ToolReq("pickaxe", 1, true), 8),
                AcquireRunTest.craft("minecraft:furnace", 1, 1, true, 1, "minecraft:cobblestone", 8)
        ), List.of(), 1);
        Set<String> needed = JunkPolicy.neededItems(plan.goal(), plan.steps(), 0);
        assertTrue(needed.contains("minecraft:cobblestone"));
        assertTrue(needed.contains("minecraft:furnace"));
        assertFalse(JunkPolicy.isJunk("minecraft:cobblestone", needed));
        assertTrue(JunkPolicy.isJunk("minecraft:dirt", needed));
        assertFalse(JunkPolicy.isJunk("minecraft:diamond", needed), "only filler counts as junk");

        Set<String> later = JunkPolicy.neededItems(plan.goal(), plan.steps(), 2);
        assertEquals(Set.of("minecraft:furnace"), later);
        assertTrue(JunkPolicy.isJunk("minecraft:cobblestone", later), "spare cobblestone once nothing uses it");
    }

    /**
     * From a beta report: handed 3 cobblestone for a stone pickaxe, the run climbed out for wood on pillars of that
     * cobblestone, then mined them back for the pickaxe, and climbed on them again.
     */
    @Test
    void reservedCountsKeepWhatTheRestOfThePlanUsesUp() {
        CraftSource stonePick = new CraftSource("minecraft:stone_pickaxe", "minecraft:stone_pickaxe", 1, List.of(
                new Ingredient(List.of("minecraft:cobblestone"), 3), new Ingredient(List.of("minecraft:stick"), 2)), true);
        Plan plan = new Plan("minecraft:stone_pickaxe", 1, List.of(
                new Step.Mine(List.of("minecraft:oak_log"), "minecraft:oak_log", 2, new ToolReq("axe", 0, false), 2),
                AcquireRunTest.craft("minecraft:oak_planks", 4, 2, false, 8, "minecraft:oak_log", 1),
                AcquireRunTest.craft("minecraft:stick", 4, 1, false, 4, "minecraft:oak_planks", 2),
                AcquireRunTest.craft("minecraft:crafting_table", 1, 1, false, 1, "minecraft:oak_planks", 4),
                new Step.PlaceStation("minecraft:crafting_table"),
                new Step.Craft(stonePick, 1, List.of("minecraft:cobblestone", "minecraft:stick"), 1)
        ), List.of(), 1);
        Map<String, Integer> reserved = JunkPolicy.reservedCounts(plan.goal(), 1, plan.steps(), 0);
        assertEquals(3, reserved.get("minecraft:cobblestone"));
        assertEquals(2, reserved.get("minecraft:oak_log"));
        assertEquals(6, reserved.get("minecraft:oak_planks"));
        assertEquals(2, reserved.get("minecraft:stick"));
        assertEquals(1, reserved.get("minecraft:crafting_table"), "the one to place");
        assertEquals(1, reserved.get("minecraft:stone_pickaxe"));
        assertEquals(null, reserved.get("minecraft:dirt"));

        assertEquals(Map.of("minecraft:stone_pickaxe", 1), JunkPolicy.reservedCounts(plan.goal(), 1, plan.steps(), 6),
                "nothing left to use up once the pickaxe is made");
    }

    @Test
    void reservedCountsKeepSmeltInputAndFuel() {
        SmeltSource iron = new SmeltSource("minecraft:iron_ingot", "minecraft:iron_ingot", 1,
                new Ingredient(List.of("minecraft:raw_iron"), 1), "minecraft:furnace", 200);
        List<Step> steps = List.of(new Step.Smelt(iron, 3, "minecraft:raw_iron", "minecraft:coal", 1, 3));
        Map<String, Integer> reserved = JunkPolicy.reservedCounts("minecraft:iron_ingot", 3, steps, 0);
        assertEquals(Map.of("minecraft:iron_ingot", 3, "minecraft:raw_iron", 3, "minecraft:coal", 1), reserved);
    }

    @Test
    void junkPicksTheBiggestStack() {
        List<String> ids = Arrays.asList("minecraft:dirt", null, "minecraft:gravel", "minecraft:iron_ore", "minecraft:dirt");
        List<Integer> counts = List.of(10, 0, 40, 64, 64);
        assertEquals(4, JunkPolicy.pickStack(ids, counts, Set.of()));
        assertEquals(2, JunkPolicy.pickStack(ids, counts, Set.of("minecraft:dirt")));
        assertEquals(-1, JunkPolicy.pickStack(ids, counts, Set.of("minecraft:dirt", "minecraft:gravel")));
    }

    // ---- ClickPlan

    @Test
    void transferWholeStacksThenOneAtATime() {
        // 3 + 5 raw iron in slots 10 and 20; want 6 in slot 0.
        List<ClickPlan.Click> clicks = ClickPlan.transfer(new int[]{10, 20}, new int[]{3, 5}, 0, 6);
        assertEquals(List.of(
                new ClickPlan.Click(10, 0), new ClickPlan.Click(0, 0),
                new ClickPlan.Click(20, 0), new ClickPlan.Click(0, 1), new ClickPlan.Click(0, 1), new ClickPlan.Click(0, 1),
                new ClickPlan.Click(20, 0)
        ), clicks);
        assertEquals(6, ClickPlan.moved(new int[]{3, 5}, 6));
    }

    @Test
    void transferStopsWhenSourcesRunOut() {
        List<ClickPlan.Click> clicks = ClickPlan.transfer(new int[]{7}, new int[]{2}, 1, 5);
        assertEquals(List.of(new ClickPlan.Click(7, 0), new ClickPlan.Click(1, 0)), clicks);
        assertEquals(2, ClickPlan.moved(new int[]{2}, 5));
        assertTrue(ClickPlan.transfer(new int[]{7}, new int[]{2}, 1, 0).isEmpty());
    }

    // ---- RecipeKeys

    @Test
    void recipeKeysMatchAcrossSpellings() {
        assertEquals(2, RecipeKeys.match("minecraft:stick", "minecraft:stick"));
        assertEquals(1, RecipeKeys.match("minecraft:stick", "stick"));
        assertEquals(1, RecipeKeys.match("minecraft:oak_planks#1", "minecraft:oak_planks"));
        assertEquals(2, RecipeKeys.match("synced:12", "synced:12"));
        assertEquals(0, RecipeKeys.match("synced:12", "synced:13"));
        assertEquals(0, RecipeKeys.match("minecraft:stick", "minecraft:sticky_piston"));
        assertEquals(0, RecipeKeys.match("minecraft:stick", ""));
    }
}
