package baritone.acquire.model;

import java.util.HashMap;
import java.util.Map;

/** Item id -> count. Mutable so the planner can simulate consuming and producing items as it plans. */
public final class InventorySnapshot {
    private final Map<String, Integer> counts;

    public InventorySnapshot(Map<String, Integer> counts) {
        this.counts = new HashMap<>(counts);
    }

    public static InventorySnapshot empty() {
        return new InventorySnapshot(Map.of());
    }

    public int count(String item) {
        return counts.getOrDefault(item, 0);
    }

    public void add(String item, int n) {
        if (n == 0) return;
        int next = count(item) + n;
        if (next <= 0) counts.remove(item);
        else counts.put(item, next);
    }

    /** Removes up to n and returns how many were actually removed. */
    public int take(String item, int n) {
        int have = count(item);
        int taken = Math.min(have, Math.max(0, n));
        add(item, -taken);
        return taken;
    }

    public Map<String, Integer> asMap() {
        return Map.copyOf(counts);
    }

    public InventorySnapshot copy() {
        return new InventorySnapshot(counts);
    }
}
