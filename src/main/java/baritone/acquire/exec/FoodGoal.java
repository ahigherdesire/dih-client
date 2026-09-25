package baritone.acquire.exec;

import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Plan;
import baritone.acquire.planner.AcquirePlanner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code #acquire food} and the food detour: plan each safe vanilla food and take the cheapest complete
 * plan. Pure apart from the planner it is given, so it is unit-tested.
 */
final class FoodGoal {

    /** Safe foods worth fetching, with their food points. Golden food, stews that need rare parts and anything risky are left out. */
    static final Map<String, Integer> CANDIDATES = candidates();

    private static final Set<String> WORDS = Set.of("food", "foods", "any_food", "any food", "something to eat", "minecraft:food");

    /**
     * @param item  the food
     * @param count how many to have in the inventory when done (what is held plus {@code extra})
     * @param plan  the plan for {@code count}
     * @param extra how many more that is
     */
    record Choice(String item, int count, Plan plan, int extra) {
    }

    private FoodGoal() {
    }

    /** Whether the user asked for food in general rather than an item. */
    static boolean isFoodWord(String text) {
        return text != null && WORDS.contains(text.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * The cheapest food to get: {@code items} more of it when above 0, else enough for {@code points}
     * food points. Never one of {@code exclude}. Null when no candidate has a complete plan.
     */
    static Choice choose(AcquirePlanner planner, InventorySnapshot inventory, int points, int items, Set<String> exclude) {
        Choice best = null;
        for (Map.Entry<String, Integer> candidate : CANDIDATES.entrySet()) {
            String item = candidate.getKey();
            if (exclude.contains(item)) continue;
            int extra = items > 0 ? items : ceilDiv(Math.max(1, points), candidate.getValue());
            int count = inventory.count(item) + extra;
            Plan plan = planner.plan(item, count, inventory);
            if (!plan.complete() || plan.steps().isEmpty()) continue;
            if (best == null || cheaper(plan, best.plan())) best = new Choice(item, count, plan, extra);
        }
        return best;
    }

    private static boolean cheaper(Plan a, Plan b) {
        int byCost = Double.compare(a.cost(), b.cost());
        return byCost != 0 ? byCost < 0 : a.steps().size() < b.steps().size();
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    private static Map<String, Integer> candidates() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("minecraft:cooked_beef", 8);
        m.put("minecraft:cooked_porkchop", 8);
        m.put("minecraft:cooked_mutton", 6);
        m.put("minecraft:cooked_chicken", 6);
        m.put("minecraft:cooked_salmon", 6);
        m.put("minecraft:cooked_cod", 5);
        m.put("minecraft:cooked_rabbit", 5);
        m.put("minecraft:bread", 5);
        m.put("minecraft:baked_potato", 5);
        m.put("minecraft:apple", 4);
        m.put("minecraft:beef", 3);
        m.put("minecraft:porkchop", 3);
        m.put("minecraft:carrot", 3);
        m.put("minecraft:mutton", 2);
        m.put("minecraft:sweet_berries", 2);
        m.put("minecraft:melon_slice", 2);
        m.put("minecraft:dried_kelp", 1);
        return Collections.unmodifiableMap(m);
    }
}
