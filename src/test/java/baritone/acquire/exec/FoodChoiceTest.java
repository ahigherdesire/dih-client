package baritone.acquire.exec;

import baritone.acquire.exec.FoodChoice.Food;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which held food a meal picks. Values are vanilla's (nutrition, saturation). */
final class FoodChoiceTest {

    static final Food STEAK = new Food("minecraft:cooked_beef", 8, 12.8F, false);
    static final Food BREAD = new Food("minecraft:bread", 5, 6.0F, false);
    static final Food CARROT = new Food("minecraft:carrot", 3, 3.6F, false);
    static final Food BERRIES = new Food("minecraft:sweet_berries", 2, 0.4F, false);
    static final Food GOLDEN_CARROT = new Food("minecraft:golden_carrot", 6, 14.4F, false);
    static final Food ROTTEN = new Food("minecraft:rotten_flesh", 4, 0.8F, true);
    static final Food SPIDER_EYE = new Food("minecraft:spider_eye", 2, 3.2F, true);
    static final Food GAPPLE = new Food("minecraft:golden_apple", 4, 9.6F, false);
    static final Food NOTCH = new Food("minecraft:enchanted_golden_apple", 4, 9.6F, false);

    private static Food pick(List<Food> held, int food) {
        return FoodChoice.choose(held, food, false, Set.of());
    }

    @Test
    void leastWasteThenMostSaturation() {
        List<Food> held = List.of(BERRIES, BREAD, STEAK, CARROT);
        assertEquals(STEAK, pick(held, 4), "16 missing: nothing wasted, steak saturates most");
        assertEquals(BREAD, pick(held, 15), "5 missing: bread fills it exactly");
        assertEquals(CARROT, pick(held, 17), "3 missing");
        assertEquals(BERRIES, pick(held, 19), "1 missing: berries waste least");
        assertEquals(GOLDEN_CARROT, pick(List.of(STEAK, GOLDEN_CARROT), 2), "no waste either way: saturation decides");
    }

    @Test
    void harmfulFoodIsSkippedUnlessStarvingWithNothingElse() {
        assertEquals(BERRIES, pick(List.of(ROTTEN, BERRIES), 2), "safe food wins even when starving");
        assertNull(pick(List.of(ROTTEN, SPIDER_EYE), 4), "not starving yet");
        assertEquals(ROTTEN, pick(List.of(ROTTEN, SPIDER_EYE), 3), "starving: the least bad harmful food");
        assertEquals(SPIDER_EYE, pick(List.of(SPIDER_EYE, GAPPLE), 0), "a golden apple is never the fallback");
    }

    @Test
    void goldenApplesAreKeptForEmergencies() {
        assertNull(pick(List.of(GAPPLE, NOTCH), 2), "not an emergency: keep them");
        assertEquals(BREAD, pick(List.of(GAPPLE, BREAD), 10));
        assertEquals(GAPPLE, FoodChoice.choose(List.of(NOTCH, BREAD, GAPPLE), 20, true, Set.of()),
                "emergency: golden apple even on a full bar, plain before enchanted");
        assertEquals(NOTCH, FoodChoice.choose(List.of(NOTCH, BREAD), 20, true, Set.of()));
        assertEquals(STEAK, FoodChoice.choose(List.of(STEAK), 12, true, Set.of()), "emergency without golden apples: any food");
        assertNull(FoodChoice.choose(List.of(STEAK), 20, true, Set.of()), "normal food can't be eaten on a full bar");
    }

    @Test
    void fullBarEatsNothingNormal() {
        assertNull(pick(List.of(STEAK, BREAD, BERRIES), 20));
    }

    @Test
    void itemsThePlanNeedsAreLeftAloneUnlessItIsAnEmergency() {
        Set<String> needed = Set.of(STEAK.id(), "minecraft:apple");
        assertEquals(BREAD, FoodChoice.choose(List.of(STEAK, BREAD), 4, false, needed));
        assertNull(FoodChoice.choose(List.of(STEAK), 4, false, needed));
        assertEquals(STEAK, FoodChoice.choose(List.of(STEAK), 4, true, needed), "emergency: health first");
        assertNull(FoodChoice.choose(List.of(STEAK, ROTTEN), 4, false, needed));
    }

    @Test
    void safeFoodExcludesGoldenHarmfulAndNeeded() {
        assertFalse(FoodChoice.hasSafeFood(List.of(), Set.of()));
        assertFalse(FoodChoice.hasSafeFood(List.of(GAPPLE, NOTCH, ROTTEN, SPIDER_EYE), Set.of()));
        assertFalse(FoodChoice.hasSafeFood(List.of(STEAK), Set.of(STEAK.id())));
        assertTrue(FoodChoice.hasSafeFood(List.of(ROTTEN, BERRIES), Set.of()));
    }

    @Test
    void denylistCoversTheRiskyFoods() {
        for (String id : List.of("rotten_flesh", "spider_eye", "poisonous_potato", "pufferfish", "chicken", "suspicious_stew")) {
            assertTrue(FoodChoice.HARMFUL.contains("minecraft:" + id), id);
        }
        assertFalse(FoodChoice.HARMFUL.contains("minecraft:cooked_chicken"));
        assertEquals(0, FoodChoice.waste(STEAK, 12));
        assertEquals(6, FoodChoice.waste(STEAK, 18));
    }
}
