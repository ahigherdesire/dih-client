package baritone.acquire.exec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * A nether portal frame built on flat ground, in the X–Y plane: 10 obsidian around a 2×3 opening, one block above the
 * ground. The four corners don't have to be obsidian; they are any spare block, placed first so every obsidian block
 * has something to be placed against (a player can't place a block in mid-air).
 *
 * <p>All positions are relative to {@code ground}, the ground block under the frame's west corner. The player builds
 * from {@link #stand}, two blocks south of the frame, and lights it by clicking the top of a bottom block.
 */
final class PortalFrame {

    /** One block to place: where, whether it is obsidian (else a corner), and the side its support is on. */
    record Placement(BlockPos pos, boolean obsidian, Direction against) {
    }

    private PortalFrame() {
    }

    /** In placing order: bottom corners, bottom row, the two sides, top corners, top row. */
    static List<Placement> placements(BlockPos ground) {
        List<Placement> out = new ArrayList<>();
        out.add(new Placement(ground.offset(0, 1, 0), false, Direction.DOWN));
        out.add(new Placement(ground.offset(3, 1, 0), false, Direction.DOWN));
        out.add(new Placement(ground.offset(1, 1, 0), true, Direction.DOWN));
        out.add(new Placement(ground.offset(2, 1, 0), true, Direction.DOWN));
        for (int dy = 2; dy <= 4; dy++) {
            out.add(new Placement(ground.offset(0, dy, 0), true, Direction.DOWN));
            out.add(new Placement(ground.offset(3, dy, 0), true, Direction.DOWN));
        }
        out.add(new Placement(ground.offset(0, 5, 0), false, Direction.DOWN));
        out.add(new Placement(ground.offset(3, 5, 0), false, Direction.DOWN));
        out.add(new Placement(ground.offset(1, 5, 0), true, Direction.WEST));
        out.add(new Placement(ground.offset(2, 5, 0), true, Direction.EAST));
        return out;
    }

    /** The opening, where the portal forms. */
    static List<BlockPos> interior(BlockPos ground) {
        List<BlockPos> out = new ArrayList<>();
        for (int dy = 2; dy <= 4; dy++) for (int dx = 1; dx <= 2; dx++) out.add(ground.offset(dx, dy, 0));
        return out;
    }

    /** The bottom block whose top face the flint and steel clicks. */
    static BlockPos ignite(BlockPos ground) {
        return ground.offset(1, 1, 0);
    }

    /** Where the player stands (feet) while building. */
    static BlockPos stand(BlockPos ground) {
        return ground.offset(1, 1, 2);
    }

    /** Blocks that must be solid ground: under the frame and under the two rows in front of it. */
    static List<BlockPos> ground(BlockPos ground) {
        List<BlockPos> out = new ArrayList<>();
        for (int dz = 0; dz <= 2; dz++) for (int dx = 0; dx < 4; dx++) out.add(ground.offset(dx, 0, dz));
        return out;
    }

    /** Blocks that must be open: the whole frame and opening, and head room over the two rows in front. */
    static List<BlockPos> clearance(BlockPos ground) {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = 0; dx < 4; dx++) {
            for (int dy = 1; dy <= 5; dy++) out.add(ground.offset(dx, dy, 0));
            for (int dz = 1; dz <= 2; dz++) for (int dy = 1; dy <= 3; dy++) out.add(ground.offset(dx, dy, dz));
        }
        return out;
    }
}
