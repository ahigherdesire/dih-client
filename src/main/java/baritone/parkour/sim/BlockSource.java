package baritone.parkour.sim;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The blocks the simulator moves through: a block state for every position, air where nothing is set. The game
 * adapter is a snapshot of the level taken on the client thread; tests build one by hand.
 */
public interface BlockSource {

    BlockState get(int x, int y, int z);

    default BlockState get(BlockPos pos) {
        return get(pos.getX(), pos.getY(), pos.getZ());
    }

    /** A source that remembers collision shapes, for sources whose blocks don't change during a search. */
    interface ShapeCache {
        VoxelShape shape(BlockState state, BlockPos pos, BlockGetter getter);
    }

    /** A source backed by a map, for tests and for snapshots. Caches collision shapes. */
    final class MapSource implements BlockSource, ShapeCache {
        private static final BlockState AIR = Blocks.AIR.defaultBlockState();
        private final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<VoxelShape> shapes = new Long2ObjectOpenHashMap<>();

        public MapSource set(int x, int y, int z, BlockState state) {
            blocks.put(BlockPos.asLong(x, y, z), state);
            shapes.remove(BlockPos.asLong(x, y, z));
            return this;
        }

        @Override
        public synchronized VoxelShape shape(BlockState state, BlockPos pos, BlockGetter getter) {
            long key = pos.asLong();
            VoxelShape shape = shapes.get(key);
            if (shape == null) {
                shape = state.getCollisionShape(getter, pos, CollisionContext.empty());
                shapes.put(key, shape);
            }
            return shape;
        }

        /** Fills the box from (x0, y0, z0) to (x1, y1, z1), both corners included. */
        public MapSource fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, state);
                }
            }
            return this;
        }

        @Override
        public BlockState get(int x, int y, int z) {
            BlockState state = blocks.get(BlockPos.asLong(x, y, z));
            return state == null ? AIR : state;
        }

        public int size() {
            return blocks.size();
        }
    }
}
