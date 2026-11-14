package baritone.acquire.planner;

import baritone.acquire.exec.FoodChoice;
import baritone.acquire.exec.FoodGoal;
import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.KillSource;
import baritone.acquire.model.MineSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Source;
import baritone.acquire.model.ToolReq;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link FoodGoal}: the food with the cheapest complete plan per food point, planned by the real planner. */
final class FoodGoalPlannerTest {

    static final String COW = "minecraft:cow";
    static final String BEEF = "minecraft:beef";
    static final String STEAK = "minecraft:cooked_beef";
    static final String BERRIES = "minecraft:sweet_berries";
    static final String BUSH = "minecraft:sweet_berry_bush";

    /** {@link FakeKnowledge} plus cows, steak and sweet berries. */
    static final class FoodKnowledge implements Knowledge {
        private final FakeKnowledge base = new FakeKnowledge();

        @Override
        public List<Source> sourcesFor(String item) {
            List<Source> out = new ArrayList<>(base.sourcesFor(item));
            switch (item) {
                case BEEF -> out.add(new KillSource(COW, BEEF, 1.0, false));
                case STEAK -> out.add(new SmeltSource("minecraft:cooked_beef", STEAK, 1, new Ingredient(List.of(BEEF), 1), FakeKnowledge.FURNACE, 200));
                case BERRIES -> out.add(new MineSource(BUSH, BERRIES, 2.0, ToolReq.NONE, false));
                default -> { }
            }
            return out;
        }

        @Override
        public String toolType(String item) {
            return base.toolType(item);
        }

        @Override
        public int toolTier(String item) {
            return base.toolTier(item);
        }

        @Override
        public List<String> toolsOf(String type, int minTier) {
            return base.toolsOf(type, minTier);
        }

        @Override
        public Map<String, Integer> fuels() {
            return base.fuels();
        }

        @Override
        public boolean isItem(String item) {
            return base.isItem(item) || Set.of(BEEF, STEAK, BERRIES, BUSH).contains(item);
        }

        @Override
        public Optional<String> resolveItem(String userText) {
            return base.resolveItem(userText);
        }

        @Override
        public List<String> suggest(String userText, int limit) {
            return List.of();
        }
    }

    private static final Knowledge KNOWLEDGE = new FoodKnowledge();
    /** Cows 10 blocks away, berry bushes 60. */
    private static final FakeWorld WORLD = new FakeWorld().entity(COW, 10).block(BUSH, 60);

    private static FoodGoal.Choice choose(InventorySnapshot inv, int points, int items, Set<String> exclude) {
        return FoodGoal.choose(new AcquirePlanner(KNOWLEDGE, WORLD, PlannerOptions.DEFAULT), inv, points, items, exclude);
    }

    @Test
    void cheapestPerFoodPointWins() {
        // 20 points: 10 berries from 5 bushes (60 blocks) beat 7 beef from 7 cows (10 blocks) and 3 steak.
        FoodGoal.Choice choice = choose(InventorySnapshot.empty(), 20, 0, Set.of());
        assertEquals(BERRIES, choice.item(), AcquirePlanner.explain(choice.plan()));
        assertEquals(10, choice.extra());
        assertEquals(10, choice.count());
        assertTrue(choice.plan().complete());
    }

    @Test
    void excludedFoodIsSkipped() {
        FoodGoal.Choice choice = choose(InventorySnapshot.empty(), 20, 0, Set.of(BERRIES));
        assertEquals(BEEF, choice.item(), AcquirePlanner.explain(choice.plan()));
        assertEquals(7, choice.extra(), "ceil(20 / 3)");
    }

    @Test
    void heldFoodCountsTowardTheTotal() {
        InventorySnapshot inv = InventorySnapshot.empty();
        inv.add(BEEF, 2);
        FoodGoal.Choice choice = choose(inv, 20, 0, Set.of(BERRIES));
        assertEquals(BEEF, choice.item());
        assertEquals(9, choice.count(), "2 held + 7 more");
        assertEquals(2, inv.count(BEEF), "the inventory is not touched");
    }

    @Test
    void aFixedItemCountIsComparedPerFoodPoint() {
        // 4 of each: 4 beef (12 points) cost 4 kills near by; 4 berries (8 points) a long walk for 2 bushes.
        FoodGoal.Choice choice = choose(InventorySnapshot.empty(), 20, 4, Set.of());
        assertEquals(BEEF, choice.item(), AcquirePlanner.explain(choice.plan()));
        assertEquals(4, choice.count());
    }

    @Test
    void nothingPlannableIsNull() {
        Set<String> all = new HashSet<>();
        for (FoodGoal.Candidate c : FoodGoal.CANDIDATES) all.add(c.item());
        assertNull(choose(InventorySnapshot.empty(), 20, 0, all));

        // Only the rare apple from leaves is left without cows, bushes or the kill option: still a plan, just a long one.
        Knowledge plain = new FakeKnowledge();
        AcquirePlanner noKilling = new AcquirePlanner(plain, WorldView.UNKNOWN, new PlannerOptions(true, false, 24, 200));
        FoodGoal.Choice apple = FoodGoal.choose(noKilling, InventorySnapshot.empty(), 20, 0, Set.of());
        assertEquals(FakeKnowledge.APPLE, apple.item());
    }

    @Test
    void candidatesAreSafeFoods() {
        for (FoodGoal.Candidate c : FoodGoal.CANDIDATES) {
            assertTrue(c.nutrition() > 0, c.item());
            assertTrue(!FoodChoice.HARMFUL.contains(c.item()), c.item());
        }
        assertTrue(FoodGoal.isFoodWord(" Food "));
    }
}
