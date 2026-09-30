package baritone.acquire.knowledge;

import baritone.acquire.model.BarterSource;
import baritone.acquire.model.Goal;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Location;
import baritone.acquire.model.Plan;
import baritone.acquire.model.Step;
import baritone.acquire.planner.AcquirePlanner;
import baritone.acquire.planner.PlannerOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 against the real vanilla data: Nether and End items plan with a trip, the dragon plans every phase, and every
 * item in the game either plans or says why not.
 */
final class VanillaDimensionPlanTest {
    private static VanillaKnowledge knowledge;

    @BeforeAll
    static void load() {
        knowledge = VanillaKnowledge.fromData(VanillaData.fromClasspath());
    }

    private static Plan plan(Goal goal) {
        return new AcquirePlanner(knowledge, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
                .plan(goal, InventorySnapshot.empty(), Location.OVERWORLD);
    }

    private static boolean has(Plan plan, Class<? extends Step> type) {
        return plan.steps().stream().anyMatch(type::isInstance);
    }

    @Test
    void placesAreTagged() {
        assertEquals(Location.FORTRESS, knowledge.locationOf(knowledge.sourcesFor("minecraft:blaze_rod").stream()
                .filter(s -> s instanceof baritone.acquire.model.KillSource).findFirst().orElseThrow()));
        assertEquals(null, knowledge.locationOf(knowledge.sourcesFor("minecraft:ender_pearl").stream()
                .filter(s -> s instanceof baritone.acquire.model.KillSource).findFirst().orElseThrow()), "endermen are everywhere");
        assertEquals(Location.END, knowledge.locationOf(knowledge.sourcesFor("minecraft:end_stone").stream()
                .filter(s -> s instanceof baritone.acquire.model.MineSource).findFirst().orElseThrow()));
    }

    @Test
    void piglinsBarterPearlsForGoldInTheNether() {
        BarterSource barter = knowledge.sourcesFor("minecraft:ender_pearl").stream()
                .filter(s -> s instanceof BarterSource).map(s -> (BarterSource) s).findFirst().orElseThrow();
        assertEquals("minecraft:piglin", barter.entity());
        assertEquals("minecraft:gold_ingot", barter.currency());
        // 10 of 469 barters give 2 to 4 pearls.
        assertEquals(10.0 / 469 * 3, barter.perTrade(), 1e-6);
        assertEquals(Location.NETHER, knowledge.locationOf(barter));
    }

    /** In the Nether a crafting table is made from a nether stem at hand, not from an Overworld log a portal away. */
    @Test
    void aCraftingTableInTheNetherIsMadeFromNetherStems() {
        Plan plan = new AcquirePlanner(knowledge, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
                .plan(new Goal.ItemGoal("minecraft:crafting_table", 1), InventorySnapshot.empty(), Location.NETHER);
        String explained = AcquirePlanner.explain(plan);
        assertTrue(plan.complete(), plan.missing() + "\n" + explained);
        assertFalse(has(plan, Step.Travel.class), explained);
        assertTrue(plan.steps().stream().anyMatch(s -> s instanceof Step.Mine m
                && (m.item().equals("minecraft:crimson_stem") || m.item().equals("minecraft:warped_stem"))), explained);
    }

    @Test
    void heldGoldInTheNetherIsBarteredForPearls() {
        InventorySnapshot inv = InventorySnapshot.empty();
        inv.add("minecraft:gold_ingot", 256);
        Plan plan = new AcquirePlanner(knowledge, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
                .plan(new Goal.ItemGoal("minecraft:ender_pearl", 12), inv, Location.NETHER);
        String explained = AcquirePlanner.explain(plan);
        assertTrue(plan.complete(), plan.missing() + "\n" + explained);
        assertTrue(has(plan, Step.Barter.class), explained);
        assertFalse(plan.steps().stream().anyMatch(s -> s instanceof Step.Kill), explained);
    }

    @Test
    void netherAndEndItemsPlanWithATrip() {
        for (String item : List.of("minecraft:blaze_rod", "minecraft:ender_eye", "minecraft:end_stone", "minecraft:quartz")) {
            Plan plan = plan(new Goal.ItemGoal(item, 1));
            assertTrue(plan.complete(), item + ": " + plan.missing());
            assertTrue(has(plan, Step.Travel.class), item + " needs a portal:\n" + AcquirePlanner.explain(plan));
        }
    }

    /** At a fortress (the Nether with nether bricks around), blaze rods are a kill: no portal, no search. */
    @Test
    void atAFortressBlazeRodsAreAKill() {
        Plan plan = new AcquirePlanner(knowledge, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
                .plan(new Goal.ItemGoal("minecraft:blaze_rod", 3), InventorySnapshot.empty(), Location.FORTRESS);
        String explained = AcquirePlanner.explain(plan);
        assertTrue(plan.complete(), plan.missing() + "\n" + explained);
        assertFalse(has(plan, Step.Travel.class) ||has(plan, Step.Locate.class), explained);
        assertTrue(plan.steps().stream().anyMatch(s -> s instanceof Step.Kill k && k.entity().equals("minecraft:blaze")), explained);
    }

    /** In the Nether away from a fortress, the fortress is found first, with no portal trip. */
    @Test
    void inTheNetherTheFortressIsFoundFirst() {
        Plan plan = new AcquirePlanner(knowledge, WorldView.UNKNOWN, PlannerOptions.DEFAULT)
                .plan(new Goal.ItemGoal("minecraft:blaze_rod", 3), InventorySnapshot.empty(), Location.NETHER);
        String explained = AcquirePlanner.explain(plan);
        assertTrue(plan.complete(), plan.missing() + "\n" + explained);
        assertFalse(has(plan, Step.Travel.class), explained);
        assertTrue(plan.steps().get(0) instanceof Step.Locate l && l.site() == Location.FORTRESS, explained);
    }

    @Test
    void theNetherFromNothingCastsThePortalWithBucketsAndNoDiamonds() {
        Plan plan = plan(new Goal.AtLocation(Location.NETHER));
        assertTrue(plan.complete(), plan.missing().toString());
        String explained = AcquirePlanner.explain(plan);
        List<Step> steps = plan.steps();
        int buckets = 0;
        int travel = -1;
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (step instanceof Step.Craft c && c.recipe().output().equals("minecraft:bucket") && travel < 0) {
                buckets += c.times() * c.recipe().outputCount();
            }
            if (travel < 0 && step instanceof Step.Travel t && t.to() == Location.NETHER) travel = i;
            assertFalse(step instanceof Step.Mine m && (m.item().equals("minecraft:obsidian") || m.item().equals("minecraft:diamond")),
                    "no obsidian or diamonds mined: " + step + "\n" + explained);
        }
        assertTrue(travel >= 0, explained);
        assertEquals(4, buckets, "four buckets before the portal:\n" + explained);
        Step.Travel portal = (Step.Travel) steps.get(travel);
        assertFalse(portal.consumes().containsKey("minecraft:obsidian"), explained);
    }

    @Test
    void theDragonPlansEveryPhase() {
        Plan plan = plan(new Goal.DragonDead());
        assertTrue(plan.complete(), plan.missing().toString());
        String explained = AcquirePlanner.explain(plan);
        assertTrue(has(plan, Step.Locate.class) && has(plan, Step.SlayDragon.class), explained);
        assertTrue(plan.steps().stream().anyMatch(s -> s instanceof Step.Kill k && k.entity().equals("minecraft:blaze")), explained);
        Plan egg = plan(new Goal.ItemGoal("minecraft:dragon_egg", 1));
        assertTrue(egg.complete() && has(egg, Step.CollectEgg.class), egg.missing() + "\n" + AcquirePlanner.explain(egg));
    }

    /**
     * A pickaxe crafted after the iron is mined doesn't stop more iron (for the gear) joining that mining step: the
     * stone pickaxe it had is enough. Two iron trips put the furnace down twice.
     */
    @Test
    void anIronPickaxeMinesItsIronInOneTrip() {
        Plan plan = plan(new Goal.ItemGoal("minecraft:iron_pickaxe", 1));
        String explained = AcquirePlanner.explain(plan);
        assertTrue(plan.complete(), plan.missing().toString());
        assertEquals(1, plan.steps().stream().filter(s -> s instanceof Step.Mine m && m.item().equals("minecraft:raw_iron")).count(), explained);
        assertTrue(plan.steps().stream().anyMatch(s -> s instanceof Step.Craft c && c.recipe().output().equals("minecraft:iron_helmet")), explained);
    }

    /** Every item plans, or says why not: never an empty reason, never a planner error. */
    @Test
    void everyItemPlansOrSaysWhy() {
        // Plans are independent and the knowledge is read-only: plan them in parallel.
        List<Plan> plans = new ArrayList<>(new TreeSet<>(knowledge.items())).parallelStream()
                .map(item -> plan(new Goal.ItemGoal(item, 1))).toList();
        List<String> silent = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int complete = 0;
        for (Plan plan : plans) {
            if (plan.complete()) {
                complete++;
                continue;
            }
            if (plan.missing().isEmpty() || plan.missing().stream().anyMatch(String::isBlank)) silent.add(plan.goal());
            if (plan.missing().stream().anyMatch(m -> m.startsWith("planner error") || m.startsWith("internal planner error"))) {
                errors.add(plan.goal() + ": " + plan.missing());
            }
        }
        System.out.println("[VanillaDimensionPlanTest] " + complete + " of " + knowledge.items().size() + " items plan");
        assertTrue(silent.isEmpty(), "no reason given: " + silent);
        assertTrue(errors.isEmpty(), "planner errors: " + errors);
        assertFalse(complete < knowledge.items().size() / 2, "only " + complete + " items plan");
    }
}
