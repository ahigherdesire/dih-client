package baritone.acquire.model;

import java.util.HashMap;
import java.util.Map;

/** Item id -> count. Mutable so the planner can simulate consuming and producing items as it plans. */
public final class InventorySnapshot {
    private final Map<String, Integer> counts;
    private final Map<String, Integer> remainingUses;

    public InventorySnapshot(Map<String, Integer> counts) {
        this(counts, Map.of());
    }

    public InventorySnapshot(Map<String, Integer> counts, Map<String, Integer> actualRemainingUses) {
        this.counts = new HashMap<>(counts);
        this.remainingUses = new HashMap<>();
        for (var entry : counts.entrySet()) {
            int max = ToolDurability.maxUses(entry.getKey());
            if (max > 0) remainingUses.put(entry.getKey(), max * entry.getValue());
        }
        remainingUses.putAll(actualRemainingUses);
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
        int max = ToolDurability.maxUses(item);
        if (max > 0) remainingUses.merge(item, n * max, Integer::sum);
    }

    /** Removes up to n and returns how many were actually removed. */
    public int take(String item, int n) {
        int have = count(item);
        int taken = Math.min(have, Math.max(0, n));
        add(item, -taken);
        return taken;
    }

    public int remainingUses(String item) {
        return Math.max(0, remainingUses.getOrDefault(item, 0));
    }

    public Map<String, Integer> asMap() {
        return Map.copyOf(counts);
    }

    public InventorySnapshot copy() {
        return new InventorySnapshot(counts, remainingUses);
    }
}
