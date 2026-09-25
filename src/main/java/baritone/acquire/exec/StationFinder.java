package baritone.acquire.exec;

import baritone.api.BaritoneAPI;
import baritone.api.cache.IWorldData;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.BlockUtils;
import baritone.api.utils.IPlayerContext;
import baritone.cache.CachedChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds usable station blocks (crafting table, furnace, ...) near the player: the ones acquire placed
 * itself, loaded chunks, and Baritone's cached world for blocks it tracks (furnaces). Remembers what it
 * placed, per dimension, and skips stations marked unusable (busy furnace, unreachable table).
 */
final class StationFinder {

    private final IPlayerContext ctx;
    /** "dimension|station" -> positions acquire placed. Kept across runs; stale entries fail the block check. */
    private final Map<String, Set<BlockPos>> placed = new HashMap<>();
    private final Set<BlockPos> unusable = new HashSet<>();

    StationFinder(IPlayerContext ctx) {
        this.ctx = ctx;
    }

    /** Block for a station id, or null if it is not a block. */
    static Block block(String station) {
        return station == null ? null : BlockUtils.stringToBlockNullable(station);
    }

    /** Usable {@code station} blocks within {@code radius} blocks, nearest first. Game thread only. */
    List<BlockPos> find(String station, int radius) {
        Block block = block(station);
        Level level = ctx.world();
        if (block == null || level == null || ctx.player() == null) return List.of();
        Vec3 from = ctx.player().position();
        double maxSq = (double) radius * radius;

        Set<BlockPos> candidates = new LinkedHashSet<>(placed.getOrDefault(key(station), Set.of()));
        candidates.addAll(BaritoneAPI.getProvider().getWorldScanner().scanChunkRadius(
                ctx, new BlockOptionalMetaLookup(block), 64, -1, (radius >> 4) + 2));
        if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
            IWorldData data = ctx.worldData();
            if (data != null) {
                BlockPos feet = ctx.playerFeet();
                candidates.addAll(data.getCachedWorld().getLocationsOf(BlockUtils.blockToString(block), 16, feet.getX(), feet.getZ(), 1));
            }
        }

        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : candidates) {
            if (unusable.contains(pos)) continue;
            if (Vec3.atCenterOf(pos).distanceToSqr(from) > maxSq) continue;
            if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (!level.getBlockState(pos).is(block)) continue;
            out.add(pos.immutable());
        }
        out.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(from)));
        return out;
    }

    void remember(String station, BlockPos pos) {
        placed.computeIfAbsent(key(station), k -> new LinkedHashSet<>()).add(pos.immutable());
    }

    /** Skip this station for the rest of the run (busy, unreachable, or it would not open). */
    void markUnusable(BlockPos pos) {
        unusable.add(pos.immutable());
    }

    /** Called when a new acquire starts: a furnace that was busy last time may be free now. */
    void newRun() {
        unusable.clear();
    }

    private String key(String station) {
        Level level = ctx.world();
        String dim = level == null ? "?" : level.dimension().identifier().toString();
        return dim + "|" + station;
    }
}
