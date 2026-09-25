package baritone.acquire.exec;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Which held food to eat. Pure, so the rules are unit-tested:
 * <ul>
 *   <li>never chorus fruit (it teleports), a pufferfish, spider eye or poisonous potato (they poison);</li>
 *   <li>golden apples only at emergency health, the plain one before the enchanted one;</li>
 *   <li>outside an emergency, never food the plan still needs, nor rotten flesh or raw chicken (hunger);</li>
 *   <li>otherwise the food that wastes the fewest points, then the most filling.</li>
 * </ul>
 */
final class FoodChoice {

    /**
     * One kind of food in the inventory.
     *
     * @param alwaysEdible can be eaten on a full food bar (golden apples)
     */
    record Food(String id, int nutrition, float saturation, boolean alwaysEdible) {
    }

    static final String GOLDEN_APPLE = "minecraft:golden_apple";
    static final String ENCHANTED_GOLDEN_APPLE = "minecraft:enchanted_golden_apple";
    static final Set<String> GOLDEN = Set.of(GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE);
    /** Never eaten. */
    static final Set<String> NEVER = Set.of("minecraft:chorus_fruit", "minecraft:pufferfish",
            "minecraft:spider_eye", "minecraft:poisonous_potato", "minecraft:suspicious_stew");
    /** Only eaten in an emergency: a chance of Hunger. */
    static final Set<String> RISKY = Set.of("minecraft:rotten_flesh", "minecraft:chicken");

    private FoodChoice() {
    }

    /** The food to eat now, or null. {@code needed} is what the plan still uses. */
    static Food choose(List<Food> held, int food, boolean emergency, Set<String> needed) {
        if (emergency) {
            for (String id : List.of(GOLDEN_APPLE, ENCHANTED_GOLDEN_APPLE)) {
                for (Food f : held) {
                    if (f.id().equals(id)) return f;
                }
            }
        }
        if (food >= HealthPolicy.MAX_FOOD) return null;
        int missing = HealthPolicy.MAX_FOOD - food;
        Comparator<Food> order = Comparator.<Food>comparingInt(f -> Math.max(0, f.nutrition() - missing))
                .thenComparing(Comparator.<Food>comparingDouble(f -> f.nutrition() + f.saturation()).reversed())
                .thenComparing(Food::id);
        Food safe = held.stream().filter(f -> safe(f, needed)).min(order).orElse(null);
        if (safe != null || !emergency) return safe;
        // Emergency: anything that is not outright harmful, needed or risky or not.
        return held.stream().filter(f -> !NEVER.contains(f.id()) && !GOLDEN.contains(f.id())).min(order).orElse(null);
    }

    /** Whether there is something to eat outside an emergency. */
    static boolean hasSafeFood(List<Food> held, Set<String> needed) {
        return held.stream().anyMatch(f -> safe(f, needed));
    }

    static boolean safe(Food f, Set<String> needed) {
        return !NEVER.contains(f.id()) && !RISKY.contains(f.id()) && !GOLDEN.contains(f.id())
                && !needed.contains(f.id()) && f.nutrition() > 0;
    }
}
