package baritone.acquire.knowledge;

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
    void netherAndEndItemsPlanWithATrip() {
        for (String item : List.of("minecraft:blaze_rod", "minecraft:ender_eye", "minecraft:end_stone", "minecraft:quartz")) {
            Plan plan = plan(new Goal.ItemGoal(item, 1));
            assertTrue(plan.complete(), item + ": " + plan.missing());
            assertTrue(has(plan, Step.Travel.class), item + " needs a portal:\n" + AcquirePlanner.explain(plan));
        }
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
