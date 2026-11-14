package baritone.acquire.planner;

import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.Goal;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Location;
import baritone.acquire.model.Plan;
import baritone.acquire.model.Step;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static baritone.acquire.planner.FakeKnowledge.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A big smelt split across furnaces side by side; every plan is replayed by {@link PlanSimulator}. */
final class SplitSmeltTest {
    private static final FakeKnowledge KNOWLEDGE = FakeKnowledge.withDimensions();

    private static Plan valid(Goal.ItemGoal goal, InventorySnapshot inv) {
        Plan plan = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, PlannerOptions.DEFAULT).plan(goal, inv, Location.OVERWORLD));
        assertTrue(plan.complete(), () -> "incomplete: " + plan.missing() + "\n" + AcquirePlanner.explain(plan));
        PlanSimulator.Result result = PlanSimulator.simulate(plan, inv, KNOWLEDGE, WorldView.UNKNOWN);
        assertTrue(result.count(goal.item()) >= goal.count(), () -> "ends short:\n" + AcquirePlanner.explain(plan));
        return plan;
    }

    private static List<Step.Smelt> smelts(Plan plan) {
        return plan.steps().stream().filter(s -> s instanceof Step.Smelt).map(s -> (Step.Smelt) s).toList();
    }

    /** The one smelt of all {@code times} cooks. */
    private static Step.Smelt goalSmelt(Plan plan, int times) {
        List<Step.Smelt> all = smelts(plan).stream().filter(s -> s.times() == times).toList();
        assertEquals(1, all.size(), () -> "one smelt of " + times + ":\n" + AcquirePlanner.explain(plan));
        return all.get(0);
    }

    @Test
    void sharesAreEvenTheLargerFirst() {
        assertArrayEquals(new int[]{9, 9, 8, 8}, Step.Smelt.shares(34, 4));
        assertArrayEquals(new int[]{2, 1, 1}, Step.Smelt.shares(4, 3));
        assertArrayEquals(new int[]{3}, Step.Smelt.shares(3, 1));
        assertArrayEquals(new int[]{1, 1}, Step.Smelt.shares(2, 5), "never more furnaces than cooks");
    }

    @Test
    void eachFurnaceBurnsItsOwnFuel() {
        // A plank burns 300 ticks, a cook takes 200: 9 cooks take 6 planks, 8 take 6 as well (5.33 rounds up).
        assertEquals(6, Step.Smelt.fuelFor(9, 200, 300));
        assertEquals(6, Step.Smelt.fuelFor(8, 200, 300));
        assertEquals(0, Step.Smelt.fuelFor(0, 200, 300));
        assertEquals(1, Step.Smelt.fuelFor(1, 200, 1600));
    }

    @Test
    void aBigSmeltIsSplitAcrossFurnacesWithFuelForEach() {
        InventorySnapshot inv = InventorySnapshot.empty();
        inv.add(RAW_IRON, 34);
        inv.add(COBBLESTONE, 64);
        inv.add(LOG, 16);
        inv.add(TABLE, 1);
        Plan plan = valid(new Goal.ItemGoal(IRON_INGOT, 34), inv);
        // The gear before it (a shield, a helmet) smelts a few more on its own.
        Step.Smelt smelt = goalSmelt(plan, 34);
        assertEquals(4, smelt.furnaces(), AcquirePlanner.explain(plan));
        assertTrue(smelt.describe().contains("in 4 furnaces"), smelt.describe());
    }

    @Test
    void aSmallSmeltUsesOneFurnace() {
        InventorySnapshot inv = InventorySnapshot.empty();
        inv.add(RAW_IRON, 6);
        inv.add(COBBLESTONE, 64);
        inv.add(LOG, 16);
        inv.add(TABLE, 1);
        Plan plan = valid(new Goal.ItemGoal(IRON_INGOT, 6), inv);
        for (Step.Smelt smelt : smelts(plan)) assertEquals(1, smelt.furnaces(), AcquirePlanner.explain(plan));
    }

    @Test
    void theExtraFurnacesComeBack() {
        InventorySnapshot inv = InventorySnapshot.empty();
        inv.add(RAW_IRON, 34);
        inv.add(COBBLESTONE, 64);
        inv.add(LOG, 16);
        inv.add(TABLE, 1);
        Plan plan = valid(new Goal.ItemGoal(IRON_INGOT, 34), inv);
        PlanSimulator.Result result = PlanSimulator.simulate(plan, inv, KNOWLEDGE, WorldView.UNKNOWN);
        int furnaces = goalSmelt(plan, 34).furnaces();
        assertTrue(result.count(FURNACE) >= furnaces - 1, "the extra furnaces are held at the end:\n" + AcquirePlanner.explain(plan));
    }
}
