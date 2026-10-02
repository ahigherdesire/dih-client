package baritone.acquire.exec;

import net.minecraft.core.BlockPos;

/**
 * Counts the ticks a path holds the player in place. Baritone retries a movement that keeps timing out (mining while
 * standing in water, say) for as long as the path is wanted, and the path itself never fails: after {@code limit}
 * ticks within a block of one spot, it counts as failed.
 */
final class StallWatch {
    private final int limit;
    private BlockPos at;
    private int ticks;

    StallWatch(int limit) {
        this.limit = limit;
    }

    /** Whether the player at {@code feet}, pathing to a goal it isn't in, has now been held in place for the limit. */
    boolean stalled(BlockPos feet, boolean pathing) {
        if (!pathing || at == null || feet.distManhattan(at) > 1) {
            at = pathing ? feet.immutable() : null;
            ticks = 0;
            return false;
        }
        if (++ticks < limit) return false;
        ticks = 0;
        return true;
    }

    void reset() {
        at = null;
        ticks = 0;
    }
}
