package baritone.parkour.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A copy of the non-air blocks in a box of the level, taken on the client thread, so the simulator and the solver can
 * run on a worker thread without touching the live world.
 */
public final class LevelSnapshot {
    private LevelSnapshot() {
    }

    /** The blocks from {@code min} to {@code max} (corners included). Air is left out; the source reads it as air. */
    public static BlockSource.MapSource capture(BlockGetter level, BlockPos min, BlockPos max) {
        BlockSource.MapSource source = new BlockSource.MapSource();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.min(min.getX(), max.getX()); x <= Math.max(min.getX(), max.getX()); x++) {
            for (int y = Math.min(min.getY(), max.getY()); y <= Math.max(min.getY(), max.getY()); y++) {
                for (int z = Math.min(min.getZ(), max.getZ()); z <= Math.max(min.getZ(), max.getZ()); z++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.isAir()) source.set(x, y, z, state);
                }
            }
        }
        return source;
    }

    /** The blocks within {@code radius} horizontally and {@code below}/{@code above} vertically of {@code center}. */
    public static BlockSource.MapSource around(BlockGetter level, BlockPos center, int radius, int below, int above) {
        return capture(level, center.offset(-radius, -below, -radius), center.offset(radius, above, radius));
    }
}
