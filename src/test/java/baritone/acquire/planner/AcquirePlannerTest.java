package baritone.acquire.planner;

import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Plan;
import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static baritone.acquire.planner.FakeKnowledge.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The planner against hand-written vanilla-like knowledge; every complete plan is replayed by {@link PlanSimulator}. */
final class AcquirePlannerTest {
    private static final FakeKnowledge KNOWLEDGE = new FakeKnowledge();
    private static final PlannerOptions NO_STATIONS = new PlannerOptions(false, true, 24, 200);
    private static final PlannerOptions NO_KILL = new PlannerOptions(true, false, 24, 200);

    private static AcquirePlanner planner() {
        return new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, PlannerOptions.DEFAULT);
    }

    private static InventorySnapshot inv(Object... itemThenCount) {
        InventorySnapshot inv = InventorySnapshot.empty();
        for (int i = 0; i < itemThenCount.length; i += 2) inv.add((String) itemThenCount[i], (Integer) itemThenCount[i + 1]);
        return inv;
    }

    /** Asserts the plan is complete and runs, and that it ends with at least the goal count. */
    private static PlanSimulator.Result valid(Plan plan, InventorySnapshot start, WorldView world) {
        assertTrue(plan.complete(), () -> "incomplete: " + plan.missing() + "\n" + AcquirePlanner.explain(plan));
        PlanSimulator.Result result = PlanSimulator.simulate(plan, start, KNOWLEDGE, world);
        assertTrue(result.count(plan.goal()) >= plan.count(), () -> "ends short:\n" + AcquirePlanner.explain(plan));
        return result;
    }

    private static int indexOf(Plan plan, Predicate<Step> match) {
        for (int i = 0; i < plan.steps().size(); i++) if (match.test(plan.steps().get(i))) return i;
        return -1;
    }

    private static Predicate<Step> crafts(String item) {
        return s -> s instanceof Step.Craft c && c.recipe().output().equals(item);
    }

    private static Predicate<Step> mines(String item) {
        return s -> s instanceof Step.Mine m && m.item().equals(item);
    }

    private static Predicate<Step> places(String station) {
        return s -> s instanceof Step.PlaceStation p && p.station().equals(station);
    }

    @Test
    void emptyInventoryToStonePickaxe() {
        Plan plan = planner().plan(STONE_PICKAXE, 1, InventorySnapshot.empty());
        PlanSimulator.Result result = valid(plan, InventorySnapshot.empty(), WorldView.UNKNOWN);

        assertEquals(3, result.mined(LOG), AcquirePlanner.explain(plan));
        assertEquals(3, result.mined(COBBLESTONE));
        assertTrue(indexOf(plan, crafts(TABLE)) >= 0);
        assertTrue(indexOf(plan, places(TABLE)) > indexOf(plan, crafts(TABLE)));
        int woodenPick = indexOf(plan, crafts(WOODEN_PICKAXE));
        assertTrue(woodenPick >= 0 && woodenPick < indexOf(plan, mines(COBBLESTONE)));
        assertTrue(crafts(STONE_PICKAXE).test(plan.steps().get(plan.steps().size() - 1)));

        // The chain from the feature plan, with repeated crafts merged.
        assertEquals(List.of(LOG, PLANKS, STICK, TABLE, TABLE, WOODEN_PICKAXE, COBBLESTONE, TABLE, STONE_PICKAXE),
                plan.steps().stream().map(Step::item).toList(), AcquirePlanner.explain(plan));
    }

    @Test
    void heldWoodenPickaxeIsNotCraftedAgain() {
        InventorySnapshot start = inv(WOODEN_PICKAXE, 1);
        Plan plan = planner().plan(STONE_PICKAXE, 1, start);
        valid(plan, start, WorldView.UNKNOWN);
        assertEquals(-1, indexOf(plan, crafts(WOODEN_PICKAXE)), AcquirePlanner.explain(plan));
    }

    @Test
    void alreadyHeldIsDone() {
        Plan plan = planner().plan(STONE_PICKAXE, 1, inv(STONE_PICKAXE, 2));
        assertTrue(plan.alreadyDone());
        assertTrue(plan.steps().isEmpty());
    }

    @Test
    void emptyInventoryToIronPickaxe() {
        Plan plan = planner().plan(IRON_PICKAXE, 1, InventorySnapshot.empty());
        valid(plan, InventorySnapshot.empty(), WorldView.UNKNOWN);

        String explained = AcquirePlanner.explain(plan);
        assertTrue(indexOf(plan, crafts(FURNACE)) >= 0, explained);
        assertTrue(indexOf(plan, places(FURNACE)) > indexOf(plan, crafts(FURNACE)), explained);
        int smelt = indexOf(plan, s -> s instanceof Step.Smelt);
        assertTrue(smelt > indexOf(plan, places(FURNACE)), explained);
        Step.Smelt step = (Step.Smelt) plan.steps().get(smelt);
        assertEquals(3, step.times());
        assertTrue(step.fuelCount() > 0 && KNOWLEDGE.fuels().containsKey(step.fuel()), explained);
        int stonePick = indexOf(plan, crafts(STONE_PICKAXE));
        assertTrue(stonePick >= 0 && stonePick < indexOf(plan, mines(RAW_IRON)), explained);
        assertTrue(crafts(IRON_PICKAXE).test(plan.steps().get(plan.steps().size() - 1)), explained);
        // Neither the iron_block -> 9 ingots recipe nor the 0.0083 zombie drop is worth it here.
        assertEquals(-1, indexOf(plan, crafts(IRON_BLOCK)));
        assertEquals(-1, indexOf(plan, s -> s instanceof Step.Kill), explained);
        // The furnace is crafted, not hunted for as a placed block.
        assertEquals(-1, indexOf(plan, mines(FURNACE)), explained);
    }

    @Test
    void unknownItemIsMissing() {
        Plan plan = planner().plan("minecraft:elytra", 1, InventorySnapshot.empty());
        assertFalse(plan.complete());
        assertTrue(plan.steps().isEmpty());
        assertEquals(List.of("unknown item minecraft:elytra"), plan.missing());
    }

    @Test
    void itemWithoutSourceIsMissing() {
        Plan plan = planner().plan(BEDROCK, 1, InventorySnapshot.empty());
        assertFalse(plan.complete());
        assertEquals(List.of("no known way to get " + BEDROCK), plan.missing());
    }

    @Test
    void ironBlockCycleTerminates() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            Plan block = planner().plan(IRON_BLOCK, 1, InventorySnapshot.empty());
            PlanSimulator.Result result = valid(block, InventorySnapshot.empty(), WorldView.UNKNOWN);
            assertEquals(9, result.mined(RAW_IRON));

            Plan ingots = planner().plan(IRON_INGOT, 2, InventorySnapshot.empty());
            valid(ingots, InventorySnapshot.empty(), WorldView.UNKNOWN);
            assertEquals(-1, indexOf(ingots, crafts(IRON_BLOCK)));
        });
    }

    @Test
    void heldIronBlockIsUsedForIngots() {
        InventorySnapshot start = inv(IRON_BLOCK, 1);
        Plan plan = planner().plan(IRON_INGOT, 5, start);
        valid(plan, start, WorldView.UNKNOWN);
        assertEquals(1, plan.steps().size(), AcquirePlanner.explain(plan));
        assertTrue(crafts(IRON_INGOT).test(plan.steps().get(0)));
    }

    @Test
    void noTableAndNoPlacingIsMissing() {
        Plan plan = new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, NO_STATIONS)
                .plan(STONE_PICKAXE, 1, InventorySnapshot.empty());
        assertFalse(plan.complete());
        assertTrue(plan.missing().contains("no crafting table nearby and placing stations is off"), plan.missing().toString());
    }

    @Test
    void nearbyTableIsUsedInsteadOfCrafted() {
        FakeWorld world = new FakeWorld().station(TABLE);
        Plan plan = new AcquirePlanner(KNOWLEDGE, world, NO_STATIONS).plan(STONE_PICKAXE, 1, InventorySnapshot.empty());
        valid(plan, InventorySnapshot.empty(), world);
        assertEquals(-1, indexOf(plan, crafts(TABLE)), AcquirePlanner.explain(plan));
        assertTrue(indexOf(plan, places(TABLE)) >= 0);
        assertEquals(2, plan.steps().stream().filter(places(TABLE)).count(), "once per batch of table crafts");
    }

    @Test
    void sixtyFourTorches() {
        Plan plan = planner().plan(TORCH, 64, InventorySnapshot.empty());
        PlanSimulator.Result result = valid(plan, InventorySnapshot.empty(), WorldView.UNKNOWN);
        Step.Craft torch = (Step.Craft) plan.steps().get(indexOf(plan, crafts(TORCH)));
        assertEquals(16, torch.times(), "16 coal and 16 sticks");
        assertEquals(List.of(COAL, STICK), torch.inputs());
        assertEquals(64, torch.untilCount());
        assertEquals(16, result.mined(COAL));
        assertTrue(result.crafted(STICK) >= 18, "16 for torches, 2 for the wooden pickaxe");
        assertEquals(64, result.count(TORCH));
        assertEquals(-1, indexOf(plan, mines(TORCH)), "placed torches are not hunted for");
    }

    @Test
    void placedBlocksAreCraftedUnlessKnownNearby() {
        Plan crafted = planner().plan(TABLE, 1, InventorySnapshot.empty());
        valid(crafted, InventorySnapshot.empty(), WorldView.UNKNOWN);
        assertEquals(-1, indexOf(crafted, mines(TABLE)), AcquirePlanner.explain(crafted));
        assertTrue(indexOf(crafted, crafts(TABLE)) >= 0);

        FakeWorld world = new FakeWorld().block("minecraft:crafting_table", 4);
        Plan mined = new AcquirePlanner(KNOWLEDGE, world, PlannerOptions.DEFAULT).plan(TABLE, 1, InventorySnapshot.empty());
        valid(mined, InventorySnapshot.empty(), world);
        assertEquals(List.of(new Step.Mine(List.of("minecraft:crafting_table"), TABLE, 1, new ToolReq("axe", 0, false), 1)),
                mined.steps());
    }

    @Test
    void rareDropsOnlyWhenNothingElseWorks() {
        Plan plan = planner().plan(APPLE, 1, InventorySnapshot.empty());
        valid(plan, InventorySnapshot.empty(), WorldView.UNKNOWN);
        Step.Mine mine = (Step.Mine) plan.steps().get(0);
        assertEquals(List.of("minecraft:oak_leaves"), mine.blocks());
        assertEquals(200, mine.expectedBlocks());
    }

    @Test
    void heldLavaBucketIsNotBurned() {
        InventorySnapshot start = inv(RAW_IRON, 3, FURNACE, 1, LAVA_BUCKET, 1);
        Plan plan = planner().plan(IRON_INGOT, 3, start);
        PlanSimulator.Result result = valid(plan, start, WorldView.UNKNOWN);
        Step.Smelt smelt = (Step.Smelt) plan.steps().get(indexOf(plan, s -> s instanceof Step.Smelt));
        assertEquals(PLANKS, smelt.fuel(), AcquirePlanner.explain(plan));
        assertEquals(1, result.count(LAVA_BUCKET));
    }

    @Test
    void flintBudgetsForTheDropRate() {
        Plan plan = planner().plan(FLINT, 1, InventorySnapshot.empty());
        valid(plan, InventorySnapshot.empty(), WorldView.UNKNOWN);
        assertEquals(1, plan.steps().size());
        Step.Mine mine = (Step.Mine) plan.steps().get(0);
        assertEquals(List.of("minecraft:gravel"), mine.blocks());
        assertEquals(10, mine.expectedBlocks());
        assertEquals(1, mine.untilCount());
    }

    @Test
    void stringNeedsKilling() {
        Plan off = new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, NO_KILL).plan(STRING, 3, InventorySnapshot.empty());
        assertFalse(off.complete());
        assertEquals(List.of("no known way to get " + STRING + " (killing mobs is off)"), off.missing());

        Plan on = planner().plan(STRING, 3, InventorySnapshot.empty());
        valid(on, InventorySnapshot.empty(), WorldView.UNKNOWN);
        assertEquals(List.of(new Step.Kill("minecraft:spider", STRING, 3, 3)), on.steps());
    }

    @Test
    void neverHuntsGolemsOrPets() {
        FakeWorld world = new FakeWorld().entity("minecraft:iron_golem", 4).entity("minecraft:cat", 2);
        for (String item : List.of(IRON_INGOT, STRING)) {
            Plan plan = new AcquirePlanner(KNOWLEDGE, world, PlannerOptions.DEFAULT).plan(item, 3, InventorySnapshot.empty());
            valid(plan, InventorySnapshot.empty(), world);
            for (Step step : plan.steps()) {
                if (step instanceof Step.Kill kill) assertFalse(AcquirePlanner.NEVER_KILL.contains(kill.entity()), AcquirePlanner.explain(plan));
            }
        }
    }

    @Test
    void oreBlocksAreGroupedNearestFirst() {
        FakeWorld world = new FakeWorld().block("minecraft:iron_ore", 40).block("minecraft:deepslate_iron_ore", 12);
        InventorySnapshot start = inv(STONE_PICKAXE, 1);
        Plan plan = new AcquirePlanner(KNOWLEDGE, world, PlannerOptions.DEFAULT).plan(RAW_IRON, 5, start);
        valid(plan, start, world);
        Step.Mine mine = (Step.Mine) plan.steps().get(0);
        assertEquals(List.of("minecraft:deepslate_iron_ore", "minecraft:iron_ore"), mine.blocks());
        assertEquals(5, mine.expectedBlocks());
    }

    @Test
    void heldFuelIsBurnedAndNeverTheInput() {
        InventorySnapshot start = inv(RAW_IRON, 8, COAL, 1, FURNACE, 1);
        Plan plan = planner().plan(IRON_INGOT, 8, start);
        valid(plan, start, WorldView.UNKNOWN);
        assertEquals(List.of(new Step.PlaceStation(FURNACE)), plan.steps().subList(0, 1), AcquirePlanner.explain(plan));
        Step.Smelt smelt = (Step.Smelt) plan.steps().get(1);
        assertEquals(COAL, smelt.fuel());
        assertEquals(1, smelt.fuelCount());
        assertNotEquals(RAW_IRON, smelt.fuel());
    }

    @Test
    void goalStockAndLeftoversCount() {
        InventorySnapshot start = inv(STICK, 3, PLANKS, 2);
        Plan plan = planner().plan(STICK, 5, start);
        PlanSimulator.Result result = valid(plan, start, WorldView.UNKNOWN);
        assertEquals(1, plan.steps().size(), AcquirePlanner.explain(plan));
        assertEquals(7, result.count(STICK));
        assertEquals(0, result.mined(LOG));
    }

    @Test
    void depthAndStepLimitsBecomeMissing() {
        Plan shallow = new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, new PlannerOptions(true, true, 3, 200))
                .plan(STONE_PICKAXE, 1, InventorySnapshot.empty());
        assertFalse(shallow.complete());
        assertTrue(shallow.missing().stream().anyMatch(m -> m.contains("sub-goals deep")), shallow.missing().toString());

        Plan short_ = new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, new PlannerOptions(true, true, 24, 3))
                .plan(STONE_PICKAXE, 1, InventorySnapshot.empty());
        assertFalse(short_.complete());
        assertTrue(short_.missing().stream().anyMatch(m -> m.contains("more than 3 steps")), short_.missing().toString());
    }

    @Test
    void inventoryIsUntouchedAndPlansAreDeterministic() {
        InventorySnapshot start = inv(PLANKS, 5, COAL, 2);
        Map<String, Integer> before = start.asMap();
        Plan first = planner().plan(IRON_PICKAXE, 1, start);
        Plan second = planner().plan(IRON_PICKAXE, 1, start);
        assertEquals(before, start.asMap());
        assertEquals(first, second);
        valid(first, start, WorldView.UNKNOWN);
    }

    @Test
    void explainNumbersTheSteps() {
        Plan plan = planner().plan(WOODEN_PICKAXE, 1, InventorySnapshot.empty());
        List<String> lines = plan.explain();
        assertEquals(plan.steps().size(), lines.size());
        assertTrue(lines.get(0).startsWith("1. mine "), lines.get(0));
        assertEquals("already have 1 stick", AcquirePlanner.explain(planner().plan(STICK, 1, inv(STICK, 1))));
    }
}
