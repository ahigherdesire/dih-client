package baritone.acquire.exec;

import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.knowledge.VanillaKnowledge;
import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.planner.AcquirePlanner;
import baritone.acquire.planner.PlannerOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Food-goal planning with vanilla knowledge; ItemStack component checks run in the client game test. */
final class FoodsVanillaTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void foodGoalPlansFromAnEmptyInventory() {
        Knowledge knowledge = VanillaKnowledge.get();
        long start = System.nanoTime();
        FoodGoal.Choice choice = FoodGoal.choose(new AcquirePlanner(knowledge, WorldView.UNKNOWN, PlannerOptions.DEFAULT),
                InventorySnapshot.empty(), 20, 0, Set.of());
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertNotNull(choice, "some food is always plannable in vanilla");
        System.out.println("Food goal from nothing: " + choice.extra() + " " + choice.item() + " ("
                + choice.plan().steps().size() + " steps) in " + millis + " ms");
        assertTrue(choice.plan().complete());
        assertTrue(millis < 20_000, "took " + millis + " ms");
    }
}
