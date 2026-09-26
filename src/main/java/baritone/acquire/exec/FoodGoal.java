package baritone.acquire.exec;

import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Plan;
import baritone.acquire.planner.AcquirePlanner;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * "Some food": the food whose complete plan costs least per food point. Used by {@code #acquire food}
 * and by the food detour {@link AcquireProcess} runs when health is low and no food is held. Pure (the
 * planner is), so it is unit-tested.
 */
public final class FoodGoal {

    /** The goal word: {@code #acquire food}. */
    public static final String WORD = "food";
    /** Everything else a user might type for it (5.1-beta.2 accepted all of these). */
    private static final Set<String> WORDS = Set.of(WORD, "foods", "any_food", "any food", "something to eat", "minecraft:food");

    /** A food worth getting, with its vanilla food points. */
    public record Candidate(String item, int nutrition) {
    }

    /** Safe foods, roughly best first; ties in cost go to the earlier one. */
    public static final List<Candidate> CANDIDATES = List.of(
            new Candidate("minecraft:cooked_beef", 8),
            new Candidate("minecraft:cooked_porkchop", 8),
            new Candidate("minecraft:cooked_mutton", 6),
            new Candidate("minecraft:cooked_chicken", 6),
            new Candidate("minecraft:bread", 5),
            new Candidate("minecraft:baked_potato", 5),
            new Candidate("minecraft:cooked_cod", 5),
            new Candidate("minecraft:cooked_salmon", 6),
            new Candidate("minecraft:beef", 3),
            new Candidate("minecraft:porkchop", 3),
            new Candidate("minecraft:mutton", 2),
            new Candidate("minecraft:apple", 4),
            new Candidate("minecraft:carrot", 3),
            new Candidate("minecraft:potato", 1),
            new Candidate("minecraft:sweet_berries", 2),
            new Candidate("minecraft:melon_slice", 2),
            new Candidate("minecraft:glow_berries", 2),
            new Candidate("minecraft:dried_kelp", 1));

    /**
     * The chosen food.
     *
     * @param extra how many more to get
     * @param count the inventory count to plan for (held + extra)
     */
    public record Choice(String item, int extra, int count, Plan plan) {
    }

    private FoodGoal() {
    }

    public static boolean isFoodWord(String text) {
        return text != null && WORDS.contains(text.trim().toLowerCase(Locale.ROOT));
    }

    /** How many of a food with {@code nutrition} points make up {@code points}. */
    static int countFor(int points, int nutrition) {
        return Math.max(1, (points + nutrition - 1) / Math.max(1, nutrition));
    }

    /**
     * Plans every candidate not in {@code exclude} and returns the complete plan with the lowest cost per
     * food point gained, or null when none is complete.
     *
     * @param points food points to get; each candidate gets {@link #countFor} of itself
     * @param items  when above 0, get this many items of the chosen food instead
     */
    public static Choice choose(AcquirePlanner planner, InventorySnapshot inventory, int points, int items, Set<String> exclude) {
        Choice best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (Candidate c : CANDIDATES) {
            if (exclude != null && exclude.contains(c.item())) continue;
            int extra = items > 0 ? items : countFor(points, c.nutrition());
            int count = inventory.count(c.item()) + extra;
            Plan plan = planner.plan(c.item(), count, inventory);
            if (!plan.complete() || plan.alreadyDone()) continue;
            double perPoint = plan.cost() / ((double) extra * c.nutrition());
            if (perPoint < bestCost) {
                bestCost = perPoint;
                best = new Choice(c.item(), extra, count, plan);
            }
        }
        return best;
    }
}
