package baritone.acquire.planner;

import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.Goal;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Location;
import baritone.acquire.model.Plan;
import baritone.acquire.model.Step;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static Predicate<Step> barters(String item) {
        return s -> s instanceof Step.Barter b && b.item().equals(item);
    }

    /** A complete plan for {@code goal} from {@code inv}, standing in {@code at}, replayed by the simulator. */
    private static Plan validFrom(Goal goal, InventorySnapshot inv, Location at) {
        Plan plan = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, PlannerOptions.DEFAULT).plan(goal, inv, at));
        assertTrue(plan.complete(), () -> "incomplete: " + plan.missing() + "\n" + AcquirePlanner.explain(plan));
        PlanSimulator.Result result = PlanSimulator.simulate(plan, inv, KNOWLEDGE, WorldView.UNKNOWN, at);
        if (goal instanceof Goal.ItemGoal item) {
            assertTrue(result.count(item.item()) >= item.count(), () -> "ends short:\n" + AcquirePlanner.explain(plan));
        }
        return plan;
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
        before(plan, crafts(BUCKET), "the buckets", travelTo(Location.NETHER), "the portal");
        before(plan, crafts(FLINT_AND_STEEL), "flint and steel", travelTo(Location.NETHER), "the portal");
        before(plan, travelTo(Location.NETHER), "the portal", locate(Location.FORTRESS), "the fortress");
        before(plan, locate(Location.FORTRESS), "the fortress", kills("minecraft:blaze"), "blazes");
        Step.Travel portal = (Step.Travel) plan.steps().get(first(plan, travelTo(Location.NETHER)));
        assertEquals(20, portal.consumes().get(COBBLESTONE), "the cast frame's mould wall takes 20 blocks");
        assertFalse(portal.consumes().containsKey(OBSIDIAN), "the frame is cast, not built from obsidian");
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
    void theFirstPortalIsCastFromLavaWithNoDiamonds() {
        Plan plan = valid(new Goal.AtLocation(Location.NETHER));
        before(plan, crafts(BUCKET), "the buckets", travelTo(Location.NETHER), "the portal");
        assertEquals(-1, first(plan, mines(OBSIDIAN)), AcquirePlanner.explain(plan));
        assertEquals(-1, first(plan, mines(DIAMOND)), "a bucket portal needs no diamonds:\n" + AcquirePlanner.explain(plan));
        assertEquals(-1, first(plan, crafts(DIAMOND_PICKAXE)), AcquirePlanner.explain(plan));
        int buckets = plan.steps().stream().filter(crafts(BUCKET))
                .mapToInt(s -> ((Step.Craft) s).times() * ((Step.Craft) s).recipe().outputCount()).sum();
        assertEquals(4, buckets, "one for water, three to carry lava:\n" + AcquirePlanner.explain(plan));
    }

    @Test
    void tenObsidianInHandMakeTheFrameInstead() {
        InventorySnapshot inv = InventorySnapshot.empty();
        inv.add(OBSIDIAN, 10);
        inv.add(FLINT_AND_STEEL, 1);
        Plan plan = new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
                .plan(new Goal.AtLocation(Location.NETHER), inv, Location.OVERWORLD);
        assertTrue(plan.complete(), plan.missing().toString());
        Step.Travel portal = (Step.Travel) plan.steps().get(first(plan, travelTo(Location.NETHER)));
        assertEquals(10, portal.consumes().get(OBSIDIAN), "a portal frame takes 10 obsidian");
        assertEquals(-1, first(plan, crafts(BUCKET)), AcquirePlanner.explain(plan));
        PlanSimulator.simulate(plan, inv, KNOWLEDGE, WorldView.UNKNOWN);
    }

    @Test
    void obsidianInSightStillCastsThePortal() {
        // Seen obsidian says nothing of how much: the rest would be cast a block at a time, a lava trip each.
        InventorySnapshot inv = castKit(131);
        inv.add(DIAMOND_PICKAXE, 1);
        inv.add(OBSIDIAN, 1);
        Plan plan = new AcquirePlanner(KNOWLEDGE, new FakeWorld().block(OBSIDIAN, 40), PlannerOptions.DEFAULT)
                .plan(new Goal.AtLocation(Location.NETHER), inv, Location.OVERWORLD);
        assertTrue(plan.complete(), plan.missing().toString());
        Step.Travel portal = (Step.Travel) plan.steps().get(first(plan, travelTo(Location.NETHER)));
        assertFalse(portal.consumes().containsKey(OBSIDIAN), "the frame is cast:\n" + AcquirePlanner.explain(plan));
        assertEquals(-1, first(plan, mines(OBSIDIAN)), AcquirePlanner.explain(plan));
    }

    /** Everything a cast portal and the Nether gear take, and a stone pickaxe with {@code uses} left. */
    private static InventorySnapshot castKit(int uses) {
        Map<String, Integer> counts = new HashMap<>();
        for (String gear : List.of(IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS, SHIELD, IRON_SWORD,
                FLINT_AND_STEEL, STONE_PICKAXE)) counts.put(gear, 1);
        counts.put(BUCKET, 2);
        counts.put(COBBLESTONE, 20);
        return new InventorySnapshot(counts, Map.of(STONE_PICKAXE, uses));
    }

    private static Predicate<Step> craftsAPickaxe() {
        return s -> s instanceof Step.Craft c && c.recipe().output().endsWith("_pickaxe");
    }

    @Test
    void aWornPickaxeIsReplacedBeforeThePortalIsCast() {
        // The cast digs its site out and the Nether is dug through: a pickaxe near the end of its life won't last.
        Plan plan = validFrom(new Goal.AtLocation(Location.NETHER), castKit(30), Location.OVERWORLD);
        before(plan, craftsAPickaxe(), "a new pickaxe", travelTo(Location.NETHER), "the portal");
    }

    @Test
    void aFreshStonePickaxeLastsTheCastAndTheTrip() {
        Plan plan = validFrom(new Goal.AtLocation(Location.NETHER), castKit(131), Location.OVERWORLD);
        assertEquals(-1, first(plan, craftsAPickaxe()), AcquirePlanner.explain(plan));
    }

    @Test
    void obsidianNobodyHasSeenIsCastFromLavaWithTwoBuckets() {
        Plan plan = new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
                .plan(new Goal.ItemGoal(OBSIDIAN, 4), InventorySnapshot.empty(), Location.OVERWORLD);
        assertTrue(plan.complete(), plan.missing().toString());
        before(plan, crafts(BUCKET), "the buckets", mines(OBSIDIAN), "obsidian");
        int buckets = plan.steps().stream().filter(crafts(BUCKET))
                .mapToInt(s -> ((Step.Craft) s).times() * ((Step.Craft) s).recipe().outputCount()).sum();
        assertEquals(2, buckets, AcquirePlanner.explain(plan));
    }

    @Test
    void obsidianInSightIsMinedWithoutABucket() {
        Plan plan = new AcquirePlanner(KNOWLEDGE, new FakeWorld().block(OBSIDIAN, 40), PlannerOptions.DEFAULT)
                .plan(new Goal.ItemGoal(OBSIDIAN, 4), InventorySnapshot.empty(), Location.OVERWORLD);
        assertTrue(plan.complete(), plan.missing().toString());
        assertTrue(first(plan, mines(OBSIDIAN)) >= 0, AcquirePlanner.explain(plan));
        assertEquals(-1, first(plan, crafts(BUCKET)), AcquirePlanner.explain(plan));
    }

    @Test
    void aWaterBucketAndAnEmptyOneAreEnoughToMakeObsidian() {
        InventorySnapshot inv = InventorySnapshot.empty();
        inv.add(WATER_BUCKET, 1);
        inv.add(BUCKET, 1);
        Plan plan = new AcquirePlanner(KNOWLEDGE, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
                .plan(new Goal.AtLocation(Location.NETHER), inv, Location.OVERWORLD);
        assertTrue(plan.complete(), plan.missing().toString());
        assertEquals(-1, first(plan, crafts(BUCKET)), AcquirePlanner.explain(plan));
    }

    @Test
    void withNoGoldThePearlsComeFromEndermen() {
        Plan plan = validFrom(new Goal.ItemGoal(ENDER_PEARL, 12), InventorySnapshot.empty(), Location.NETHER);
        assertTrue(first(plan, kills("minecraft:enderman")) >= 0, AcquirePlanner.explain(plan));
        assertEquals(-1, first(plan, barters(ENDER_PEARL)), AcquirePlanner.explain(plan));
    }

    @Test
    void goldInTheNetherIsBarteredForPearlsWearingAGoldPiece() {
        InventorySnapshot inv = InventorySnapshot.empty();
        inv.add(GOLD_INGOT, 256);
        // The boots take a table, and this world has no wood in the Nether: without one it's a trip home first.
        inv.add(TABLE, 1);
        Plan plan = validFrom(new Goal.ItemGoal(ENDER_PEARL, 12), inv, Location.NETHER);
        String explained = AcquirePlanner.explain(plan);
        assertEquals(-1, first(plan, kills("minecraft:enderman")), "bartering held gold beats hunting endermen:\n" + explained);
        Step.Barter barter = (Step.Barter) plan.steps().get(first(plan, barters(ENDER_PEARL)));
        assertEquals(PIGLIN, barter.entity(), explained);
        assertEquals(GOLD_INGOT, barter.currency(), explained);
        assertEquals((int) Math.ceil(12 / PEARLS_PER_INGOT), barter.trades(), explained);
        // Piglins attack a player with no gold on: the cheapest piece is made first, from the same gold.
        before(plan, crafts(GOLDEN_BOOTS), "the gold boots", barters(ENDER_PEARL), "bartering");
        assertTrue(barter.describe().contains("piglin"), barter.describe());
    }

    @Test
    void aGoldPieceAlreadyHeldIsWornInsteadOfMakingOne() {
        InventorySnapshot inv = InventorySnapshot.empty();
        inv.add(GOLD_INGOT, 256);
        inv.add(GOLDEN_HELMET, 1);
        Plan plan = validFrom(new Goal.ItemGoal(ENDER_PEARL, 12), inv, Location.NETHER);
        assertTrue(first(plan, barters(ENDER_PEARL)) >= 0, AcquirePlanner.explain(plan));
        assertEquals(-1, first(plan, crafts(GOLDEN_BOOTS)), AcquirePlanner.explain(plan));
    }

    @Test
    void tooLittleGoldToBarterEnoughHuntsEndermen() {
        InventorySnapshot inv = InventorySnapshot.empty();
        inv.add(GOLD_INGOT, 40);
        Plan plan = validFrom(new Goal.ItemGoal(ENDER_PEARL, 12), inv, Location.NETHER);
        assertTrue(first(plan, kills("minecraft:enderman")) >= 0, AcquirePlanner.explain(plan));
        assertEquals(-1, first(plan, barters(ENDER_PEARL)), AcquirePlanner.explain(plan));
    }

    @Test
    void noRouteSaysExactlyWhy() {
        FakeKnowledge noPortal = FakeKnowledge.withDimensions(false);
        Plan plan = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> new AcquirePlanner(noPortal, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
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
