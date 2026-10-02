package baritone.acquire.exec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.function.Predicate;

/**
 * The nearest loaded block of a kind, searched section by section: a section whose palette doesn't have the block is
 * skipped at once, so a wide search is cheap. Height counts {@code verticalWeight} times over, since reaching a block
 * above or below means climbing or digging. (The chunk scanner stops after its first hits, which are not the nearest.)
 */
final class NearestBlock {

    private NearestBlock() {
    }

    /**
     * The nearest {@code block} within {@code chunks} chunks and {@code height} blocks up or down of {@code from} that
     * {@code ok} accepts, or null.
     */
    /**
     * The {@code block} within {@code chunks} chunks and {@code height} blocks up or down of {@code from} with the
     * highest {@code score} (negative scores are left out) that {@code ok} accepts, or null.
     */
    static BlockPos best(Level level, BlockPos from, Block block, int chunks, int height,
                         java.util.function.ToDoubleFunction<BlockPos> score, Predicate<BlockPos> ok) {
        BlockPos best = null;
        double bestScore = 0;
        int minSection = Math.max(level.getMinSectionY(), (from.getY() - height) >> 4);
        int maxSection = Math.min(level.getMaxSectionY(), (from.getY() + height) >> 4);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cx = (from.getX() >> 4) - chunks; cx <= (from.getX() >> 4) + chunks; cx++) {
            for (int cz = (from.getZ() >> 4) - chunks; cz <= (from.getZ() >> 4) + chunks; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (int sy = minSection; sy <= maxSection; sy++) {
                    LevelChunkSection section = chunk.getSections()[level.getSectionIndexFromSectionY(sy)];
                    if (section.hasOnlyAir() || !section.getStates().maybeHas(state -> state.is(block))) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (!section.getBlockState(x, y, z).is(block)) continue;
                                p.set((cx << 4) + x, (sy << 4) + y, (cz << 4) + z);
                                if (Math.abs(p.getY() - from.getY()) > height) continue;
                                double s = score.applyAsDouble(p);
                                if (s >= 0 && (best == null || s > bestScore) && ok.test(p)) {
                                    best = p.immutable();
                                    bestScore = s;
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    static BlockPos find(Level level, BlockPos from, Block block, int chunks, int height, double verticalWeight,
                         Predicate<BlockPos> ok) {
        BlockPos best = null;
        double bestCost = Double.MAX_VALUE;
        int minSection = Math.max(level.getMinSectionY(), (from.getY() - height) >> 4);
        int maxSection = Math.min(level.getMaxSectionY(), (from.getY() + height) >> 4);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int cx = (from.getX() >> 4) - chunks; cx <= (from.getX() >> 4) + chunks; cx++) {
            for (int cz = (from.getZ() >> 4) - chunks; cz <= (from.getZ() >> 4) + chunks; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;
                for (int sy = minSection; sy <= maxSection; sy++) {
                    LevelChunkSection section = chunk.getSections()[level.getSectionIndexFromSectionY(sy)];
                    if (section.hasOnlyAir() || !section.getStates().maybeHas(state -> state.is(block))) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (!section.getBlockState(x, y, z).is(block)) continue;
                                p.set((cx << 4) + x, (sy << 4) + y, (cz << 4) + z);
                                double dx = p.getX() - from.getX(), dy = p.getY() - from.getY(), dz = p.getZ() - from.getZ();
                                double cost = dx * dx + verticalWeight * dy * dy + dz * dz;
                                if (cost < bestCost && ok.test(p)) {
                                    best = p.immutable();
                                    bestCost = cost;
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }
}
