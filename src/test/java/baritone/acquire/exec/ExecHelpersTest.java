package baritone.acquire.exec;

import baritone.acquire.model.Plan;
import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
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
