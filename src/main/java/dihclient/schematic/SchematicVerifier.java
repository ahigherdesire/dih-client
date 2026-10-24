package dihclient.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.EnumMap;
import java.util.Map;

/**
 * Compares what a placement wants with what the world has, block by block, the way Litematica's overlay does
 * ({@code fi.dy.masa.litematica.util.OverlayType} and its schematic verifier): a block where the schematic has air is
 * extra, air where it has a block is missing, another block is a wrong block, and the right block in another state
 * (facing, half, ...) is a wrong state.
 */
public final class SchematicVerifier {

    /**
     * What is off at one position, with Litematica's draw priority: where two placements overlap, the higher wins.
     */
    public enum Mismatch {
        NONE(0),
        MISSING(1),
        EXTRA(2),
        WRONG_STATE(3),
        WRONG_BLOCK(4);

        private final int priority;

        Mismatch(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return this.priority;
        }
    }

    private SchematicVerifier() {}

    /**
     * @param expected what the placement wants (null where it wants nothing)
     * @param actual   what the world has
     */
    public static Mismatch classify(BlockState expected, BlockState actual) {
        if (expected == null || expected == actual) {
            return Mismatch.NONE;
        }
        boolean wantAir = expected.isAir();
        boolean haveAir = actual.isAir();
        if (wantAir && haveAir) {
            return Mismatch.NONE; // air and cave air
        }
        if (wantAir) {
            return Mismatch.EXTRA;
        }
        if (haveAir) {
            return Mismatch.MISSING;
        }
        return expected.getBlock() != actual.getBlock() ? Mismatch.WRONG_BLOCK : Mismatch.WRONG_STATE;
    }

    /**
     * How many of a placement's positions are in each state.
     */
    public record Counts(Map<Mismatch, Integer> byType, int total) {

        public int of(Mismatch type) {
            return this.byType.getOrDefault(type, 0);
        }

        /**
         * @return positions that hold what the placement wants, blocks and air alike
         */
        public int correct() {
            return of(Mismatch.NONE);
        }

        public boolean complete() {
            return correct() == this.total;
        }
    }

    /**
     * Checks every position of a placement against {@code world}.
     */
    public static Counts verify(SchematicPlacement placement, BlockGetter world) {
        Map<Mismatch, Integer> counts = new EnumMap<>(Mismatch.class);
        BoundingBox box = placement.bounds();
        int total = 0;
        if (box != null) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        BlockState want = placement.expected(x, y, z);
                        if (want == null) {
                            continue;
                        }
                        total++;
                        counts.merge(classify(want, world.getBlockState(pos.set(x, y, z))), 1, Integer::sum);
                    }
                }
            }
        }
        return new Counts(counts, total);
    }
}
