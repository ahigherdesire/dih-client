package baritone.acquire.exec;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link HealthPolicy} and {@link FoodChoice}: when to eat and what. */
final class HealingPolicyTest {

    static final FoodChoice.Food BREAD = new FoodChoice.Food("minecraft:bread", 5, 6f, false);
    static final FoodChoice.Food STEAK = new FoodChoice.Food("minecraft:cooked_beef", 8, 12.8f, false);
    static final FoodChoice.Food BERRIES = new FoodChoice.Food("minecraft:sweet_berries", 2, 0.4f, false);
    static final FoodChoice.Food GAPPLE = new FoodChoice.Food(FoodChoice.GOLDEN_APPLE, 4, 9.6f, true);
    static final FoodChoice.Food NOTCH = new FoodChoice.Food(FoodChoice.ENCHANTED_GOLDEN_APPLE, 4, 9.6f, true);
    static final FoodChoice.Food FLESH = new FoodChoice.Food("minecraft:rotten_flesh", 4, 0.8f, false);
    static final FoodChoice.Food CHORUS = new FoodChoice.Food("minecraft:chorus_fruit", 4, 2.4f, true);

    @Test
    void needFollowsHealthAndFood() {
        assertEquals(HealthPolicy.Need.NONE, HealthPolicy.need(20, 0, 20, 20, 6));
        assertEquals(HealthPolicy.Need.NONE, HealthPolicy.need(10, 0, 20, 18, 6), "18 food regenerates by itself");
        assertEquals(HealthPolicy.Need.EAT, HealthPolicy.need(10, 0, 20, 17, 6), "hurt and too hungry to regenerate");
        assertEquals(HealthPolicy.Need.EAT, HealthPolicy.need(20, 0, 20, 6, 6), "hungry at full health");
        assertEquals(HealthPolicy.Need.NONE, HealthPolicy.need(20, 0, 20, 7, 6));
        assertEquals(HealthPolicy.Need.EMERGENCY, HealthPolicy.need(6, 0, 20, 20, 6));
        assertEquals(HealthPolicy.Need.EAT, HealthPolicy.need(6, 4, 20, 10, 6), "absorption counts towards the emergency line");
    }

    @Test
    void fetchesFoodOnlyWithNothingSafeToEat() {
        assertTrue(HealthPolicy.wantsFood(12, 20, 12, false));
        assertTrue(HealthPolicy.wantsFood(20, 6, 12, false));
        assertFalse(HealthPolicy.wantsFood(13, 7, 12, false));
        assertFalse(HealthPolicy.wantsFood(4, 2, 12, true));
    }

    @Test
    void detourFetchesWhatTheBarMissesButAtLeastTen() {
        assertEquals(20, HealthPolicy.detourPoints(0));
        assertEquals(14, HealthPolicy.detourPoints(6));
        assertEquals(10, HealthPolicy.detourPoints(19));
        assertEquals(10, HealthPolicy.detourPoints(20));
    }

    @Test
    void heartsForChat() {
        assertEquals("3.5 hearts", HealthPolicy.hearts(7));
        assertEquals("1 heart", HealthPolicy.hearts(2));
        assertEquals("10 hearts", HealthPolicy.hearts(20));
        assertEquals("0 hearts", HealthPolicy.hearts(-1));
    }

    @Test
    void picksTheFoodThatWastesLeast() {
        List<FoodChoice.Food> held = List.of(STEAK, BREAD, BERRIES);
        assertEquals(STEAK, FoodChoice.choose(held, 10, false, Set.of()), "8 of 10 missing points");
        assertEquals(BREAD, FoodChoice.choose(held, 15, false, Set.of()), "5 fits exactly, steak wastes 3");
        assertEquals(BERRIES, FoodChoice.choose(held, 18, false, Set.of()));
        assertNull(FoodChoice.choose(held, 20, false, Set.of()), "can't eat on a full bar");
    }

    @Test
    void savesGoldenApplesForEmergencies() {
        List<FoodChoice.Food> held = List.of(NOTCH, GAPPLE, BREAD);
        assertEquals(BREAD, FoodChoice.choose(held, 10, false, Set.of()));
        assertNull(FoodChoice.choose(List.of(GAPPLE), 10, false, Set.of()));
        assertEquals(GAPPLE, FoodChoice.choose(held, 20, true, Set.of()), "plain before enchanted, even on a full bar");
        assertEquals(NOTCH, FoodChoice.choose(List.of(NOTCH, BREAD), 20, true, Set.of()));
    }

    @Test
    void leavesPlanFoodAndRiskyFoodForEmergencies() {
        Set<String> needed = Set.of(BREAD.id());
        assertNull(FoodChoice.choose(List.of(BREAD, FLESH), 10, false, needed));
        assertFalse(FoodChoice.hasSafeFood(List.of(BREAD, FLESH, GAPPLE), needed));
        assertTrue(FoodChoice.hasSafeFood(List.of(BREAD), Set.of()));
        assertEquals(BREAD, FoodChoice.choose(List.of(BREAD, FLESH), 10, true, needed), "an emergency may eat what the plan needs");
        assertEquals(FLESH, FoodChoice.choose(List.of(FLESH), 10, true, Set.of()));
    }

    @Test
    void neverEatsChorusFruit() {
        assertNull(FoodChoice.choose(List.of(CHORUS), 4, false, Set.of()));
        assertNull(FoodChoice.choose(List.of(CHORUS), 4, true, Set.of()));
        assertFalse(FoodChoice.hasSafeFood(List.of(CHORUS), Set.of()));
    }
}
