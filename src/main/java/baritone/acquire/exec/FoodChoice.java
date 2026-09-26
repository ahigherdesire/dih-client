package baritone.acquire.exec;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which held food to eat. Pure (foods as plain values) so it is unit-tested; {@link Foods} reads them
 * from the inventory.
 *
 * <ul>
 *   <li>Golden apples are kept for emergencies, where they are eaten even at full food (plain before
 *       enchanted).</li>
 *   <li>Harmful foods (a harmful on-eat effect, or {@link #HARMFUL}) are eaten only when starving and
 *       nothing else is held, the least harmful first (hunger before poison).</li>
 *   <li>Items the running plan still needs are skipped, except in an emergency.</li>
 *   <li>Otherwise: the least wasted food points first, then the highest saturation.</li>
 * </ul>
 */
public final class FoodChoice {

    /**
     * Held food, one entry per item.
     *
     * @param harmful a harmful on-eat effect (hunger, poison, ...) or on the {@link #HARMFUL} list
     */
    public record Food(String id, int nutrition, float saturation, boolean harmful) {
        boolean golden() {
            return GOLDEN.contains(id);
        }
    }

    /**
     * Harmful to eat even where the effect data says otherwise (a chance, a random stew, a teleport), least
     * harmful first: a chance of hunger, then the rest, then poison.
     */
    public static final List<String> HARMFUL = List.of(
            "minecraft:chicken", "minecraft:rotten_flesh", "minecraft:suspicious_stew", "minecraft:chorus_fruit",
            "minecraft:poisonous_potato", "minecraft:spider_eye", "minecraft:pufferfish");

    /** Eaten only in an emergency, in this order. */
    public static final List<String> GOLDEN = List.of("minecraft:golden_apple", "minecraft:enchanted_golden_apple");

    private FoodChoice() {
    }

    /**
     * The food to eat now, or null.
     *
     * @param food      the current food level
     * @param emergency health is at or below the emergency threshold
     * @param needed    items the running plan still uses (ignored in an emergency)
     */
    public static Food choose(Collection<Food> held, int food, boolean emergency, Set<String> needed) {
        if (emergency) {
            for (String id : GOLDEN) {
                for (Food f : held) if (f.id().equals(id)) return f;
            }
        }
        if (food >= HealthPolicy.MAX_FOOD) return null; // normal food can't be eaten at full food
        Set<String> skip = emergency || needed == null ? Set.of() : needed;
        Food best = best(held, food, f -> !f.golden() && !f.harmful() && !skip.contains(f.id()), false);
        if (best == null && food <= HealthPolicy.STARVING_FOOD) {
            best = best(held, food, f -> !f.golden() && !skip.contains(f.id()), true);
        }
        return best;
    }

    /** Whether any food is held that a normal (non-emergency) meal may use: not golden, not harmful, not needed. */
    public static boolean hasSafeFood(Collection<Food> held, Set<String> needed) {
        for (Food f : held) {
            if (!f.golden() && !f.harmful() && (needed == null || !needed.contains(f.id()))) return true;
        }
        return false;
    }

    /** Wasted food points if {@code f} is eaten at food level {@code food}. */
    static int waste(Food f, int food) {
        return Math.max(0, f.nutrition() - (HealthPolicy.MAX_FOOD - food));
    }

    /** Rank of a harmful food, least harmful first; one only the effect data flags comes last. */
    static int harm(Food f) {
        int i = HARMFUL.indexOf(f.id());
        return i >= 0 ? i : HARMFUL.size();
    }

    private static Food best(Collection<Food> held, int food, Predicate<Food> allowed, boolean leastHarmFirst) {
        return held.stream()
                .filter(allowed)
                .min(Comparator.<Food>comparingInt(f -> leastHarmFirst ? harm(f) : 0)
                        .thenComparingInt(f -> waste(f, food))
                        .thenComparing(Food::saturation, Comparator.reverseOrder())
                        .thenComparing(Food::nutrition, Comparator.reverseOrder())
                        .thenComparing(Food::id))
                .orElse(null);
    }
}
