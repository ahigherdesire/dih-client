package baritone.acquire.exec;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StallWatchTest {
    private static final BlockPos AT = new BlockPos(5, -52, 79);

    @Test
    void aPathHoldingThePlayerInPlaceFailsAfterTheLimit() {
        StallWatch watch = new StallWatch(3);
        assertFalse(watch.stalled(AT, true));
        assertFalse(watch.stalled(AT, true));
        assertFalse(watch.stalled(AT.east(), true));
        assertTrue(watch.stalled(AT, true));
        // Counted again from nothing, so a stall fails the path once per limit, not every tick after.
        assertFalse(watch.stalled(AT, true));
    }

    @Test
    void movingOnStartsTheCountAgain() {
        StallWatch watch = new StallWatch(3);
        watch.stalled(AT, true);
        watch.stalled(AT, true);
        assertFalse(watch.stalled(AT.east(2), true));
        assertFalse(watch.stalled(AT.east(2), true));
        assertFalse(watch.stalled(AT.east(2), true));
        assertTrue(watch.stalled(AT.east(2), true));
    }

    @Test
    void standingStillWithoutAPathIsNoStall() {
        StallWatch watch = new StallWatch(3);
        watch.stalled(AT, true);
        watch.stalled(AT, true);
        assertFalse(watch.stalled(AT, false));
        assertFalse(watch.stalled(AT, true));
        assertFalse(watch.stalled(AT, true));
        assertFalse(watch.stalled(AT, true));
        assertTrue(watch.stalled(AT, true));
    }
}
