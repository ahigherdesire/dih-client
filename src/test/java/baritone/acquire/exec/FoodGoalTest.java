package baritone.acquire.exec;

import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.KillSource;
import baritone.acquire.model.MineSource;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Source;
import baritone.acquire.model.ToolReq;
import baritone.acquire.planner.AcquirePlanner;
import baritone.acquire.planner.PlannerOptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FoodGoalTest {

    static final String BERRIES = "minecraft:sweet_berries";
    static final String BEEF = "minecraft:beef";
    static final String STEAK = "minecraft:cooked_beef";
    static final String COAL = "minecraft:coal";

    /** Berries from a bush, beef from cows, steak from beef in a furnace. */
    static final class FoodKnowledge implements Knowledge {
        final Map<String, List<Source>> sources = new HashMap<>();

        FoodKnowledge() {
            add(new MineSource("minecraft:sweet_berry_bush", BERRIES, 2, ToolReq.NONE, false));
            add(new KillSource("minecraft:cow", BEEF, 2, false));
            add(new SmeltSource("minecraft:cooked_beef", STEAK, 1, new Ingredient(List.of(BEEF), 1), "minecraft:furnace", 200));
        }

        void add(Source s) {
            sources.computeIfAbsent(s.output(), k -> new ArrayList<>()).add(s);
        }

        @Override public List<Source> sourcesFor(String item) { return sources.getOrDefault(item, List.of()); }
        @Override public String toolType(String item) { return null; }
        @Override public int toolTier(String item) { return 0; }
        @Override public List<String> toolsOf(String type, int minTier) { return List.of(); }
        @Override public Map<String, Integer> fuels() { return Map.of(COAL, 1600); }
        @Override public boolean isItem(String item) { return sources.containsKey(item) || item.equals(COAL); }
        @Override public Optional<String> resolveItem(String userText) { return Optional.empty(); }
        @Override public List<String> suggest(String userText, int limit) { return List.of(); }
    }

    static WorldView world(double bush, double cow) {
        return new WorldView() {
            @Override public double distanceToBlock(String block) { return block.equals("minecraft:sweet_berry_bush") ? bush : Double.POSITIVE_INFINITY; }
            @Override public double distanceToEntity(String entity) { return entity.equals("minecraft:cow") ? cow : Double.POSITIVE_INFINITY; }
            @Override public boolean stationNearby(String station) { return station.equals("minecraft:furnace"); }
        };
    }

    static AcquirePlanner planner(WorldView world) {
        return new AcquirePlanner(new FoodKnowledge(), world, PlannerOptions.DEFAULT);
    }

    /** No hunting: only the berries can be planned. */
    static AcquirePlanner noKills(WorldView world) {
        PlannerOptions d = PlannerOptions.DEFAULT;
        return new AcquirePlanner(new FoodKnowledge(), world, new PlannerOptions(d.allowPlaceStations(), false, d.maxDepth(), d.maxSteps()));
    }

    @Test
    void recognisesFoodWords() {
        assertTrue(FoodGoal.isFoodWord("food"));
        assertTrue(FoodGoal.isFoodWord(" Food "));
        assertTrue(FoodGoal.isFoodWord("any food"));
        assertFalse(FoodGoal.isFoodWord("bread"));
        assertFalse(FoodGoal.isFoodWord(null));
    }

    @Test
    void getsEnoughPointsOfAPlannableFood() {
        FoodGoal.Choice choice = FoodGoal.choose(planner(world(8, 8)), new InventorySnapshot(Map.of(COAL, 8)), 14, 0, Set.of());
        assertNotNull(choice);
        assertTrue(choice.plan().complete());
        int points = FoodGoal.CANDIDATES.get(choice.item());
        assertTrue(choice.extra() * points >= 14, choice.toString());
        assertTrue((choice.extra() - 1) * points < 14, "no more than it needs: " + choice);
        assertEquals(choice.extra(), choice.count(), "none held yet");
    }

    @Test
    void countsWhatIsHeldAndHonoursAnItemCount() {
        FoodGoal.Choice choice = FoodGoal.choose(noKills(world(8, 8)),
                new InventorySnapshot(Map.of(BERRIES, 3)), 0, 5, Set.of());
        assertNotNull(choice);
        assertEquals(BERRIES, choice.item());
        assertEquals(5, choice.extra());
        assertEquals(8, choice.count());
    }

    @Test
    void skipsExcludedFoodAndGivesUpWithNoPlan() {
        FoodGoal.Choice choice = FoodGoal.choose(planner(world(8, 8)), new InventorySnapshot(Map.of(COAL, 8)), 10, 0, Set.of(BERRIES));
        assertNotNull(choice);
        assertFalse(choice.item().equals(BERRIES), choice.toString());
        assertNull(FoodGoal.choose(noKills(world(8, 8)), InventorySnapshot.empty(), 10, 0, Set.of(BERRIES)));
    }

    @Test
    void candidatesAreSafeToEat() {
        for (String id : FoodGoal.CANDIDATES.keySet()) {
            assertTrue(FoodChoice.safe(new FoodChoice.Food(id, FoodGoal.CANDIDATES.get(id), 1f, false), Set.of()), id);
        }
    }
}
