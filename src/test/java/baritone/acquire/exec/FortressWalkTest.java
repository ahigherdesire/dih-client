package baritone.acquire.exec;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * From a blaze-rod game test: at the fortress with no spawner in the loaded chunks and no blaze in view, it gave up in
 * place eight times. It walks the fortress instead, each leg to the floor furthest from where it has been.
 */
final class FortressWalkTest {
    private static final BlockPos START = new BlockPos(0, 64, 0);

    @Test
    void aLegIsNeitherTooShortNorTooLong() {
        assertEquals(-1, FortressWalk.score(new BlockPos(5, 64, 0), START, List.of(START), 40), "too near");
        assertEquals(-1, FortressWalk.score(new BlockPos(50, 64, 0), START, List.of(START), 40), "beyond a leg");
        assertEquals(30, FortressWalk.score(new BlockPos(30, 64, 0), START, List.of(START), 40), 1e-9);
    }

    @Test
    void itHeadsAwayFromWhereItHasBeen() {
        BlockPos feet = new BlockPos(30, 64, 0);
        List<BlockPos> walked = List.of(START, feet);
        double back = FortressWalk.score(new BlockPos(0, 64, 5), feet, walked, 40);
        double on = FortressWalk.score(new BlockPos(60, 64, 0), feet, walked, 40);
        assertTrue(on > back, "on " + on + " back " + back);
    }

    @Test
    void withNoFloorInALegItHeadsForTheNearestFurtherOut() {
        // From the seed 20260929 test: reached at 180, 46, 96 by the brick count, the nearest floor 51 blocks off.
        BlockPos feet = new BlockPos(180, 46, 96);
        assertEquals(-1, FortressWalk.score(new BlockPos(217, 54, 131), feet, List.of(feet), 40));
        double near = FortressWalk.nearest(new BlockPos(217, 54, 131), feet);
        double far = FortressWalk.nearest(new BlockPos(236, 54, 160), feet);
        assertTrue(near > far, "near " + near + " far " + far);
        assertEquals(-1, FortressWalk.nearest(new BlockPos(185, 46, 96), feet), "too near");
    }
}
