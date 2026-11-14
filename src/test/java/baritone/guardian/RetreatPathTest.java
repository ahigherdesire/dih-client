package baritone.guardian;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class RetreatPathTest {
    private static final double AWAY = 12;
    /** A straight path walked east along x, oldest first; the threat is at the east end. */
    private static final List<BlockPos> PATH = List.of(new BlockPos(0, 64, 0), new BlockPos(4, 64, 0), new BlockPos(8, 64, 0),
            new BlockPos(12, 64, 0), new BlockPos(16, 64, 0), new BlockPos(20, 64, 0));
    private static final BlockPos DANGER = new BlockPos(28, 64, 0);

    @Test
    void startsAtTheNewestCrumbFarEnoughFromTheThreat() {
        assertEquals(new BlockPos(16, 64, 0), RetreatPath.next(PATH, null, new BlockPos(21, 64, 0), DANGER, AWAY));
    }

    /**
     * From a blaze-rod game-test death: chosen afresh each tick, the target flipped between two crumbs as each came
     * within 3 blocks, and the player stood still under fire. A target is kept until reached.
     */
    @Test
    void keepsItsTargetUntilReachedThenGoesFurtherBack() {
        BlockPos target = new BlockPos(16, 64, 0);
        assertEquals(target, RetreatPath.next(PATH, target, new BlockPos(18, 64, 0), DANGER, AWAY), "nearly there: keep going");
        assertEquals(new BlockPos(12, 64, 0), RetreatPath.next(PATH, target, new BlockPos(16, 64, 0), DANGER, AWAY), "reached: on back");
    }

    @Test
    void leavesATargetTheThreatCameOverTo() {
        BlockPos target = new BlockPos(16, 64, 0);
        assertEquals(new BlockPos(8, 64, 0), RetreatPath.next(PATH, target, new BlockPos(20, 64, 0), new BlockPos(20, 64, 0), AWAY));
    }

    @Test
    void noCrumbFarEnoughMeansNone() {
        assertNull(RetreatPath.next(PATH, null, new BlockPos(1, 64, 0), DANGER, 40), "every crumb is within 40 of the threat");
    }
}
