package baritone.acquire.exec;

import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.Location;
import baritone.acquire.model.Plan;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AcquireRunTest {

    static final String LOG = "minecraft:oak_log";
    static final String PLANKS = "minecraft:oak_planks";
    static final String STICK = "minecraft:stick";
    static final String TABLE = "minecraft:crafting_table";
    static final String PICK = "minecraft:wooden_pickaxe";
    static final String FURNACE = "minecraft:furnace";

    static Step mineLogs(int until) {
        return new Step.Mine(List.of(LOG), LOG, until, ToolReq.NONE, until);
    }

    static Step craft(String output, int outCount, int times, boolean table, int until, String input, int perCraft) {
        CraftSource recipe = new CraftSource(output, output, outCount, List.of(new Ingredient(List.of(input), perCraft)), table);
        return new Step.Craft(recipe, times, List.of(input), until);
    }

    /** The wooden pickaxe chain as a planner would emit it from an empty inventory. */
    static Plan woodenPickaxe() {
        return new Plan(PICK, 1, List.of(
                mineLogs(3),
                craft(PLANKS, 4, 3, false, 12, LOG, 1),
                craft(STICK, 4, 1, false, 4, PLANKS, 2),
                craft(TABLE, 1, 1, false, 1, PLANKS, 4),
                new Step.PlaceStation(TABLE),
                craft(PICK, 1, 1, true, 1, PLANKS, 3)
        ), List.of(), 100);
    }

    static ToIntFunction<String> inv(Map<String, Integer> counts) {
        return id -> counts.getOrDefault(id, 0);
    }

    /** Logs, then the End: a plan whose second step has no runner yet. */
    static Plan logsThenEnd() {
        return new Plan("minecraft:end_stone", 1, List.of(
                mineLogs(3),
                new Step.Travel(Location.STRONGHOLD, Location.END, Map.of("minecraft:ender_eye", 12)),
                new Step.Mine(List.of("minecraft:end_stone"), "minecraft:end_stone", 1, ToolReq.NONE, 1),
                new Step.Mine(List.of("minecraft:end_stone"), "minecraft:end_stone", 1, ToolReq.NONE, 1)
        ), List.of(), 100);
    }

    @Test
    void stopsHonestlyAtTheFirstStepWithoutARunner() {
        AcquireRun run = new AcquireRun("minecraft:end_stone", 1, logsThenEnd());
        assertEquals(0, run.advance(inv(Map.of())));
        assertNull(run.blocked(), "mining logs has a runner");
        // A step with no item is never "already done": the trip is not skipped.
        assertEquals(1, run.advance(inv(Map.of(LOG, 3))));
        String blocked = run.blocked();
        assertTrue(blocked != null && blocked.contains("step 2/4") && blocked.contains("the End")
                && blocked.contains("isn't built yet"), blocked);
        assertEquals(0, run.replans(), "no re-plans burned");
    }

    @Test
    void everyNewStepSaysWhatIsMissing() {
        for (Step step : List.of(new Step.Travel(Location.STRONGHOLD, Location.END, Map.of()), new Step.Locate(Location.STRONGHOLD),
                new Step.SlayDragon(), new Step.CollectEgg(1))) {
            assertTrue(AcquireRun.unsupported(step) != null, step.describe());
            assertFalse(AcquireRun.met(step, inv(Map.of())), step.describe() + " is never already done");
        }
        assertNull(AcquireRun.unsupported(mineLogs(1)));
        assertNull(AcquireRun.unsupported(new Step.Locate(Location.FORTRESS)), "finding a fortress runs");
        assertNull(AcquireRun.unsupported(new Step.Travel(Location.OVERWORLD, Location.NETHER, Map.of("minecraft:obsidian", 10))),
                "building a portal runs");
        assertNull(AcquireRun.unsupported(new Step.Travel(Location.FORTRESS, Location.OVERWORLD, Map.of())), "going home runs");
    }

    @Test
    void walksStepsInOrderFromAnEmptyInventory() {
        AcquireRun run = new AcquireRun(PICK, 1, woodenPickaxe());
        ToIntFunction<String> empty = inv(Map.of());
        assertEquals(0, run.advance(empty));
        assertTrue(run.current() instanceof Step.Mine);
        assertEquals(1, run.advance(empty));
        assertEquals(2, run.advance(empty));
        assertEquals(3, run.advance(empty));
        assertEquals(4, run.advance(empty));
        assertTrue(run.current() instanceof Step.PlaceStation);
        assertEquals(5, run.advance(empty));
        assertEquals(-1, run.advance(empty));
        assertNull(run.current());
    }

    @Test
    void skipsStepsWhoseCountIsAlreadyMet() {
        AcquireRun run = new AcquireRun(PICK, 1, woodenPickaxe());
        Map<String, Integer> have = new HashMap<>(Map.of(LOG, 3, PLANKS, 12));
        assertEquals(2, run.advance(inv(have)), "logs and planks are already there");
        have.put(STICK, 4);
        have.put(TABLE, 1);
        assertEquals(4, run.advance(inv(have)), "sticks and table done, the station is still needed for the pickaxe");
    }

    @Test
    void stationIsSkippedWhenNothingAfterItNeedsIt() {
        AcquireRun run = new AcquireRun(PICK, 1, woodenPickaxe());
        Map<String, Integer> have = Map.of(LOG, 3, PLANKS, 12, STICK, 4, TABLE, 1, PICK, 1);
        assertEquals(-1, run.advance(inv(have)));
        assertTrue(run.goalMet(inv(have)));
    }

    @Test
    void stationLookAheadStopsAtTheNextSetUpOfTheSameStation() {
        Plan plan = new Plan(PICK, 1, List.of(
                new Step.PlaceStation(TABLE),
                craft(STICK, 4, 1, false, 4, PLANKS, 2),
                new Step.PlaceStation(TABLE),
                craft(PICK, 1, 1, true, 1, PLANKS, 3)
        ), List.of(), 1);
        AcquireRun run = new AcquireRun(PICK, 1, plan);
        assertFalse(run.stationNeeded(0, TABLE, inv(Map.of())), "the stick craft is 2x2; the pickaxe has its own set-up");
        assertTrue(run.stationNeeded(2, TABLE, inv(Map.of())));
        assertEquals(1, run.advance(inv(Map.of())));
    }

    @Test
    void furnaceStationServesSmeltSteps() {
        SmeltSource source = new SmeltSource("minecraft:iron_ingot", "minecraft:iron_ingot", 1,
                new Ingredient(List.of("minecraft:raw_iron"), 1), FURNACE, 200);
        Step smelt = new Step.Smelt(source, 3, "minecraft:raw_iron", "minecraft:coal", 1, 3);
        assertTrue(AcquireRun.usesStation(smelt, FURNACE));
        assertFalse(AcquireRun.usesStation(smelt, TABLE));
        AcquireRun run = new AcquireRun("minecraft:iron_ingot", 3, new Plan("minecraft:iron_ingot", 3,
                List.of(new Step.PlaceStation(FURNACE), smelt), List.of(), 1));
        assertEquals(0, run.advance(inv(Map.of())));
        AcquireRun done = new AcquireRun("minecraft:iron_ingot", 3, run.plan());
        assertEquals(-1, done.advance(inv(Map.of("minecraft:iron_ingot", 3))));
    }

    @Test
    void replaceStartsOverAndCounts() {
        AcquireRun run = new AcquireRun(PICK, 1, woodenPickaxe());
        run.advance(inv(Map.of()));
        run.advance(inv(Map.of()));
        assertEquals(0, run.replans());
        Plan shorter = new Plan(PICK, 1, List.of(craft(PICK, 1, 1, true, 1, PLANKS, 3)), List.of(), 1);
        run.replace(shorter);
        assertEquals(1, run.replans());
        assertEquals(-1, run.index());
        assertEquals(0, run.advance(inv(Map.of())));
        assertEquals(1, run.stepCount());
    }

    @Test
    void statusLine() {
        AcquireRun run = new AcquireRun(PICK, 1, woodenPickaxe());
        assertEquals("acquiring 1 wooden_pickaxe", run.status(inv(Map.of())));
        run.advance(inv(Map.of()));
        assertEquals("acquiring 1 wooden_pickaxe: step 1/6, mine 3 oak_log for oak_log (2/3)",
                run.status(inv(Map.of(LOG, 2))));
        run.advance(inv(Map.of(LOG, 3, PLANKS, 12, STICK, 4, TABLE, 1)));
        assertEquals("acquiring 1 wooden_pickaxe: step 5/6, set up a crafting_table", run.status(inv(Map.of())));
    }

    @Test
    void resumingAfterAFoodDetourDoesNotCountAsAReplan() {
        AcquireRun run = new AcquireRun(PICK, 1, woodenPickaxe());
        run.advance(inv(Map.of()));
        run.resume(woodenPickaxe());
        assertEquals(0, run.replans());
        assertEquals(-1, run.index());
        run.replace(woodenPickaxe());
        assertEquals(1, run.replans());
    }

    @Test
    void expectedRecoveryDoesNotSpendTheFailureBudgetUntilItRepeats() {
        AcquireRun run = new AcquireRun(PICK, 1, woodenPickaxe());
        assertTrue(run.expectedFreeAvailable());
        run.resumeExpected(woodenPickaxe());
        run.resumeExpected(woodenPickaxe());
        assertEquals(0, run.replans());
        assertFalse(run.expectedFreeAvailable());
        run.replace(woodenPickaxe());
        assertEquals(1, run.replans());
        run.markProgress();
        assertTrue(run.expectedFreeAvailable());
    }

    @Test
    void retrievesAPlacedTableEvenAfterTheGoalIsAlreadyHeld() {
        Plan plan = new Plan(PICK, 1, List.of(
                new Step.PlaceStation(TABLE),
                craft(PICK, 1, 1, true, 1, PLANKS, 3),
                new Step.RetrieveStation(TABLE)
        ), List.of(), 1);
        AcquireRun run = new AcquireRun(PICK, 1, plan);
        assertEquals(0, run.advance(inv(Map.of())));
        assertEquals(2, run.advance(inv(Map.of(PICK, 1))));
        assertTrue(run.current() instanceof Step.RetrieveStation);
    }
}
