package baritone.acquire.exec;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans the container clicks that move an exact number of items into one slot, the way a player
 * does it: pick up a stack, drop it whole, or right-click one item at a time and put the rest back.
 * Pure, so it is unit-tested; the caller sends each click as {@code PICKUP} with the given button.
 */
final class ClickPlan {

    /** One {@code PICKUP} click on a menu slot. {@code button} 0 is left (whole stack), 1 is right (one item). */
    record Click(int slot, int button) {
    }

    private ClickPlan() {
    }

    /**
     * Clicks that move {@code amount} items from {@code sourceSlots} (with {@code sourceCounts} items each,
     * used in order) into {@code target}. The target must be empty or hold the same item with room for
     * {@code amount}. Moves less when the sources run out. The cursor is empty afterwards.
     */
    static List<Click> transfer(int[] sourceSlots, int[] sourceCounts, int target, int amount) {
        List<Click> clicks = new ArrayList<>();
        int left = amount;
        for (int i = 0; i < sourceSlots.length && left > 0; i++) {
            int have = sourceCounts[i];
            if (have <= 0) continue;
            int slot = sourceSlots[i];
            clicks.add(new Click(slot, 0));
            if (have <= left) {
                clicks.add(new Click(target, 0));
                left -= have;
            } else {
                for (int k = 0; k < left; k++) clicks.add(new Click(target, 1));
                clicks.add(new Click(slot, 0));
                left = 0;
            }
        }
        return clicks;
    }

    /** How many items {@link #transfer} moves for these arguments. */
    static int moved(int[] sourceCounts, int amount) {
        int total = 0;
        for (int n : sourceCounts) total += Math.max(0, n);
        return Math.min(total, Math.max(0, amount));
    }
}
