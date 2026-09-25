package baritone.acquire.planner;

import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The planner's virtual world while it plans: inventory, items held back for pending steps, drafted
 * steps, station status and cost so far. Every trial of an alternative runs on its own {@link #copy()}.
 */
final class PlanState {

    /**
     * A drafted step. {@code gained} is how many items a Mine or Kill step adds; the final
     * {@code untilCount}s are filled in by {@link PlanReplay} once the plan is done.
     */
    record Entry(Step step, int gained) {
    }

    /** Thrown when a trial gets more expensive than {@link #limit}: a cheaper complete option already exists. */
    static final class OverBudget extends RuntimeException {
        private static final long serialVersionUID = 1L;
        static final OverBudget INSTANCE = new OverBudget();

        private OverBudget() {
            super("over budget", null, false, false);
        }
    }

    InventorySnapshot inv;
    /** Items obtained for a step that has not run yet; sub-goals must not use them. */
    Map<String, Integer> reserved;
    List<Entry> steps;
    Set<String> missing;
    /** Stations that need no item any more: known nearby, or placed earlier in this plan. */
    Set<String> ready;
    /** Stations whose item is obtained and held back for their first set-up. */
    Set<String> pending;
    /** Stations set up since the last Mine or Kill step; crafting or smelting there needs no new set-up. */
    Set<String> active;
    double cost;
    /** Trials that get more expensive than this are abandoned (branch and bound). Not taken over by {@link #become}. */
    double limit = Double.POSITIVE_INFINITY;

    PlanState(InventorySnapshot inv) {
        this(inv, new HashMap<>(), new ArrayList<>(), new LinkedHashSet<>(), new HashSet<>(), new HashSet<>(),
                new HashSet<>(), 0);
    }

    private PlanState(InventorySnapshot inv, Map<String, Integer> reserved, List<Entry> steps, Set<String> missing,
                      Set<String> ready, Set<String> pending, Set<String> active, double cost) {
        this.inv = inv;
        this.reserved = reserved;
        this.steps = steps;
        this.missing = missing;
        this.ready = ready;
        this.pending = pending;
        this.active = active;
        this.cost = cost;
    }

    PlanState copy() {
        PlanState c = new PlanState(inv.copy(), new HashMap<>(reserved), new ArrayList<>(steps),
                new LinkedHashSet<>(missing), new HashSet<>(ready), new HashSet<>(pending), new HashSet<>(active), cost);
        c.limit = limit;
        return c;
    }

    /** Adopts a trial's outcome. Keeps this state's own {@link #limit}. */
    void become(PlanState o) {
        inv = o.inv;
        reserved = o.reserved;
        steps = o.steps;
        missing = o.missing;
        ready = o.ready;
        pending = o.pending;
        active = o.active;
        cost = o.cost;
    }

    /** Held and not reserved for anything. */
    int available(String item) {
        return Math.max(0, inv.count(item) - reserved.getOrDefault(item, 0));
    }

    void reserve(String item, int n) {
        if (n > 0) reserved.merge(item, n, Integer::sum);
    }

    void release(String item, int n) {
        if (n > 0) reserved.computeIfPresent(item, (k, v) -> v > n ? v - n : null);
    }

    /** Releases and removes {@code n} reserved items (the step that needed them runs now). */
    void consume(String item, int n) {
        if (n <= 0) return;
        release(item, n);
        inv.take(item, n);
    }

    void addCost(double ticks) {
        cost += ticks;
        if (cost > limit) throw OverBudget.INSTANCE;
    }

    /** Records why the plan is incomplete; always returns false so callers can {@code return s.fail(...)}. */
    boolean fail(String reason) {
        missing.add(reason);
        return false;
    }
}
