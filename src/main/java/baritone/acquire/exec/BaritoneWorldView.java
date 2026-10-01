package baritone.acquire.exec;

import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.Location;
import baritone.api.BaritoneAPI;
import baritone.api.cache.IWorldData;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.api.utils.BlockUtils;
import baritone.api.utils.IPlayerContext;
import baritone.cache.CachedChunk;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@link WorldView} over Baritone's cached world and the loaded chunks and entities around the player.
 * Answers are memoised for the life of the instance (one plan), so the planner can ask freely.
 * Reads the world on the game thread; a call from another thread waits for the game thread.
 */
public final class BaritoneWorldView implements WorldView {

    /** The dimension of {@code level} as a plan location (the Overworld for anything unknown or modded). */
    public static Location dimension(net.minecraft.world.level.Level level) {
        if (level == null) return Location.OVERWORLD;
        String path = level.dimension().identifier().getPath();
        return switch (path) {
            case "the_nether" -> Location.NETHER;
            case "the_end" -> Location.END;
            default -> Location.OVERWORLD;
        };
    }

    /** Nether bricks within this many chunks mean a fortress is here. */
    private static final int FORTRESS_CHUNKS = 3;
    /** More than a few stray blocks: a fortress's bridges and halls have thousands. */
    private static final int FORTRESS_BRICKS = 24;

    /** Where the player is for the planner: the dimension, or {@link Location#FORTRESS} in the Nether among nether bricks. */
    public static Location here(IPlayerContext ctx) {
        Location dimension = dimension(ctx.world());
        if (dimension != Location.NETHER || ctx.player() == null) return dimension;
        int bricks = BaritoneAPI.getProvider().getWorldScanner().scanChunkRadius(
                ctx, new BlockOptionalMetaLookup(Blocks.NETHER_BRICKS), FORTRESS_BRICKS, -1, FORTRESS_CHUNKS).size();
        return bricks >= FORTRESS_BRICKS ? Location.FORTRESS : dimension;
    }


    /** Chunk radius of the loaded-chunk scan (about 80 blocks). */
    private static final int SCAN_CHUNKS = 6;
    /** Enough hits to find a close one; the scan goes nearest chunk first. */
    private static final int SCAN_MAX = 64;

    private final IPlayerContext ctx;
    private final StationFinder stations;
    private final int stationRadius;
    private final Map<String, Double> blocks = new HashMap<>();
    private final Map<String, Double> vertical = new HashMap<>();
    private final Map<String, Double> entities = new HashMap<>();
    private final Map<String, Boolean> stationCache = new HashMap<>();

    /** Blocks counted as not there (no way to them this run). */
    private final java.util.Set<String> skipped;

    BaritoneWorldView(IPlayerContext ctx, StationFinder stations, int stationRadius) {
        this(ctx, stations, stationRadius, java.util.Set.of());
    }

    BaritoneWorldView(IPlayerContext ctx, StationFinder stations, int stationRadius, java.util.Set<String> skipped) {
        this.ctx = ctx;
        this.stations = stations;
        this.stationRadius = stationRadius;
        this.skipped = skipped;
    }

    @Override
    public double distanceToBlock(String block) {
        return onGameThread(() -> blocks.computeIfAbsent(block, this::scanBlock));
    }

    double verticalDistanceToBlock(String block) {
        distanceToBlock(block);
        return vertical.getOrDefault(block, 0.0);
    }

    @Override
    public double distanceToEntity(String entity) {
        return onGameThread(() -> entities.computeIfAbsent(entity, this::scanEntity));
    }

    @Override
    public boolean stationNearby(String station) {
        return onGameThread(() -> stationCache.computeIfAbsent(station, s -> !stations.find(s, stationRadius).isEmpty()));
    }

    private double scanBlock(String id) {
        Block block = StationFinder.block(id);
        if (block == null || skipped.contains(id) || ctx.player() == null || ctx.world() == null) return Double.POSITIVE_INFINITY;
        Vec3 from = ctx.player().position();
        double best = Double.POSITIVE_INFINITY;
        double depth = 0;
        for (BlockPos pos : BaritoneAPI.getProvider().getWorldScanner().scanChunkRadius(
                ctx, new BlockOptionalMetaLookup(block), SCAN_MAX, -1, SCAN_CHUNKS)) {
            double distance = Vec3.atCenterOf(pos).distanceTo(from);
            if (distance < best) {
                best = distance;
                depth = Math.abs(pos.getY() - from.y);
            }
        }
        if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
            IWorldData data = ctx.worldData();
            if (data != null) {
                BlockPos feet = ctx.playerFeet();
                for (BlockPos pos : data.getCachedWorld().getLocationsOf(BlockUtils.blockToString(block), SCAN_MAX, feet.getX(), feet.getZ(), 2)) {
                    double distance = Vec3.atCenterOf(pos).distanceTo(from);
                    if (distance < best) {
                        best = distance;
                        depth = Math.abs(pos.getY() - from.y);
                    }
                }
            }
        }
        vertical.put(id, depth);
        return best;
    }

    private double scanEntity(String id) {
        Identifier key = Identifier.tryParse(id);
        EntityType<?> type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        if (type == null || ctx.player() == null || ctx.world() == null) return Double.POSITIVE_INFINITY;
        double best = Double.POSITIVE_INFINITY;
        for (Entity entity : ctx.entities()) {
            if (entity.getType() != type || !(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            best = Math.min(best, Math.sqrt(entity.distanceToSqr(ctx.player())));
        }
        return best;
    }

    private static <T> T onGameThread(Supplier<T> task) {
        Minecraft mc = Minecraft.getInstance();
        return mc.isSameThread() ? task.get() : mc.submit(task).join();
    }
}
