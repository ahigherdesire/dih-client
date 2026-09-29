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
import java.util.function.Predicate;

import static baritone.acquire.planner.FakeKnowledge.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1: plans that cross dimensions, from an empty inventory in the Overworld. Portal before the Nether, rods and
 * pearls before eyes, eyes before the End, every {@code #beat} phase in order for the dragon, and a plan with no
 * route says why. Every complete plan is replayed by {@link PlanSimulator}, which checks where each step happens.
 */
final class DimensionPlannerTest {
    private static final FakeKnowledge KNOWLEDGE = FakeKnowledge.withDimensions();

    private static Plan plan(Goal goal) {
        return assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
                        .plan(goal, InventorySnapshot.empty(), Location.OVERWORLD));
    }

    private static Plan valid(Goal goal) {
        Plan plan = plan(goal);
        assertTrue(plan.complete(), () -> "incomplete: " + plan.missing() + "\n" + AcquirePlanner.explain(plan));
        PlanSimulator.Result result = PlanSimulator.simulate(plan, InventorySnapshot.empty(), KNOWLEDGE, WorldView.UNKNOWN);
        if (goal instanceof Goal.ItemGoal item) {
            assertTrue(result.count(item.item()) >= item.count(), () -> "ends short:\n" + AcquirePlanner.explain(plan));
        }
        return plan;
    }

    private static int first(Plan plan, Predicate<Step> match) {
        for (int i = 0; i < plan.steps().size(); i++) if (match.test(plan.steps().get(i))) return i;
        return -1;
    }

    private static int last(Plan plan, Predicate<Step> match) {
        for (int i = plan.steps().size() - 1; i >= 0; i--) if (match.test(plan.steps().get(i))) return i;
        return -1;
    }

    private static Predicate<Step> travelTo(Location to) {
        return s -> s instanceof Step.Travel t && t.to() == to;
    }

    private static Predicate<Step> locate(Location site) {
        return s -> s instanceof Step.Locate l && l.site() == site;
    }

    private static Predicate<Step> kills(String entity) {
        return s -> s instanceof Step.Kill k && k.entity().equals(entity);
    }

    private static Predicate<Step> crafts(String item) {
        return s -> s instanceof Step.Craft c && c.recipe().output().equals(item);
    }

    private static Predicate<Step> mines(String item) {
        return s -> s instanceof Step.Mine m && m.item().equals(item);
    }

    /** Asserts {@code a} happens (its last occurrence) before {@code b} (its first), both present. */
    private static void before(Plan plan, Predicate<Step> a, String aName, Predicate<Step> b, String bName) {
        int ia = last(plan, a);
        int ib = first(plan, b);
        String explained = AcquirePlanner.explain(plan);
        assertTrue(ia >= 0, aName + " missing:\n" + explained);
        assertTrue(ib >= 0, bName + " missing:\n" + explained);
        assertTrue(ia < ib, aName + " should come before " + bName + ":\n" + explained);
    }

    @Test
    void blazeRodBuildsAPortalBeforeGoingToTheNether() {
        Plan plan = valid(new Goal.ItemGoal(BLAZE_ROD, 7));
        before(plan, mines(OBSIDIAN), "obsidian", travelTo(Location.NETHER), "the portal");
        before(plan, crafts(FLINT_AND_STEEL), "flint and steel", travelTo(Location.NETHER), "the portal");
        before(plan, travelTo(Location.NETHER), "the portal", locate(Location.FORTRESS), "the fortress");
        before(plan, locate(Location.FORTRESS), "the fortress", kills("minecraft:blaze"), "blazes");
        Step.Travel portal = (Step.Travel) plan.steps().get(first(plan, travelTo(Location.NETHER)));
        assertEquals(10, portal.consumes().get(OBSIDIAN), "a portal frame takes 10 obsidian");
        // The Nether gear checkpoint: armour, a shield and an iron sword before the portal.
        for (String gear : List.of(IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS, SHIELD, IRON_SWORD)) {
            before(plan, crafts(gear), gear, travelTo(Location.NETHER), "the portal");
        }
    }

    @Test
    void enderEyeGetsRodsAndPearlsFirst() {
        Plan plan = valid(new Goal.ItemGoal(ENDER_EYE, 12));
        before(plan, kills("minecraft:blaze"), "blazes", crafts(BLAZE_POWDER), "blaze powder");
        before(plan, crafts(BLAZE_POWDER), "blaze powder", crafts(ENDER_EYE), "eyes");
        before(plan, kills("minecraft:enderman"), "endermen", crafts(ENDER_EYE), "eyes");
        assertEquals(1, plan.steps().stream().filter(travelTo(Location.NETHER)).count(), "one trip to the Nether");
    }

    @Test
    void endStoneGoesToTheEndAfterTheEyes() {
        Plan plan = valid(new Goal.ItemGoal(END_STONE, 16));
        before(plan, crafts(ENDER_EYE), "eyes", locate(Location.STRONGHOLD), "the stronghold");
        before(plan, locate(Location.STRONGHOLD), "the stronghold", travelTo(Location.END), "the end portal");
        before(plan, travelTo(Location.END), "the end portal", mines(END_STONE), "end stone");
        Step.Travel end = (Step.Travel) plan.steps().get(first(plan, travelTo(Location.END)));
        assertEquals(12, end.consumes().get(ENDER_EYE), "the end portal takes 12 eyes");
        // The End gear checkpoint: a bow, 64 arrows and blocks to pillar with.
        before(plan, crafts(BOW), "a bow", travelTo(Location.END), "the end portal");
        before(plan, crafts(ARROW), "arrows", travelTo(Location.END), "the end portal");
    }

    @Test
    void theDragonRunsEveryPhaseInOrder() {
        Plan plan = valid(new Goal.DragonDead());
        before(plan, crafts(IRON_SWORD), "gear", travelTo(Location.NETHER), "the portal");
        before(plan, travelTo(Location.NETHER), "the portal", kills("minecraft:blaze"), "blaze rods");
        before(plan, kills("minecraft:blaze"), "blaze rods", crafts(ENDER_EYE), "eyes");
        before(plan, kills("minecraft:enderman"), "pearls", crafts(ENDER_EYE), "eyes");
        before(plan, crafts(ENDER_EYE), "eyes", locate(Location.STRONGHOLD), "the stronghold");
        before(plan, locate(Location.STRONGHOLD), "the stronghold", travelTo(Location.END), "the End");
        before(plan, travelTo(Location.END), "the End", s -> s instanceof Step.SlayDragon, "the dragon");
        assertEquals(plan.steps().size() - 1, first(plan, s -> s instanceof Step.SlayDragon), "the dragon is last");
        assertEquals(Goal.DragonDead.LABEL, plan.goal());
    }

    @Test
    void theEggComesAfterTheDragon() {
        Plan plan = valid(new Goal.ItemGoal("minecraft:dragon_egg", 1));
        before(plan, s -> s instanceof Step.SlayDragon, "the dragon", s -> s instanceof Step.CollectEgg, "the egg");
    }

    @Test
    void reachingTheNetherIsAGoalToo() {
        Plan plan = valid(new Goal.AtLocation(Location.NETHER));
        assertEquals(plan.steps().size() - 1, first(plan, travelTo(Location.NETHER)), "the portal is last:\n" + AcquirePlanner.explain(plan));
    }

    @Test
    void overworldItemsDoNotTravel() {
        Plan plan = valid(new Goal.ItemGoal(IRON_PICKAXE, 1));
        assertEquals(-1, first(plan, s -> s instanceof Step.Travel || s instanceof Step.Locate), AcquirePlanner.explain(plan));
    }

    @Test
    void inTheNetherANetherItemIsMinedWithoutAPortal() {
        InventorySnapshot inv = InventorySnapshot.empty();
        inv.add(STONE_PICKAXE, 1);
        Plan plan = new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
                .plan(new Goal.ItemGoal(QUARTZ, 8), inv, Location.NETHER);
        assertTrue(plan.complete(), plan.missing().toString());
        assertEquals(-1, first(plan, s -> s instanceof Step.Travel), AcquirePlanner.explain(plan));
        assertTrue(first(plan, mines(QUARTZ)) >= 0, AcquirePlanner.explain(plan));
    }

    @Test
    void noRouteSaysExactlyWhy() {
        FakeKnowledge noObsidian = FakeKnowledge.withDimensions(false);
        Plan plan = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> new AcquirePlanner(noObsidian, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
                        .plan(new Goal.ItemGoal(BLAZE_ROD, 1), InventorySnapshot.empty(), Location.OVERWORLD));
        assertFalse(plan.complete());
        String why = String.join("; ", plan.missing());
        assertTrue(why.contains("the Nether") && why.contains("obsidian"), why);
    }

    @Test
    void theEndHasNoWayBackBeforeTheDragon() {
        Plan plan = new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
                .plan(new Goal.ItemGoal(BLAZE_ROD, 1), InventorySnapshot.empty(), Location.END);
        assertFalse(plan.complete());
        assertTrue(String.join("; ", plan.missing()).contains("the End"), plan.missing().toString());
    }
}
