package baritone.acquire.exec;

import baritone.acquire.knowledge.WorldView;
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

    /** Chunk radius of the loaded-chunk scan (about 80 blocks). */
    private static final int SCAN_CHUNKS = 6;
    /** Enough hits to find a close one; the scan goes nearest chunk first. */
    private static final int SCAN_MAX = 64;

    private final IPlayerContext ctx;
    private final StationFinder stations;
    private final int stationRadius;
    private final Map<String, Double> blocks = new HashMap<>();
    private final Map<String, Double> entities = new HashMap<>();
    private final Map<String, Boolean> stationCache = new HashMap<>();

    BaritoneWorldView(IPlayerContext ctx, StationFinder stations, int stationRadius) {
        this.ctx = ctx;
        this.stations = stations;
        this.stationRadius = stationRadius;
    }

    @Override
    public double distanceToBlock(String block) {
        return onGameThread(() -> blocks.computeIfAbsent(block, this::scanBlock));
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
        if (block == null || ctx.player() == null || ctx.world() == null) return Double.POSITIVE_INFINITY;
        Vec3 from = ctx.player().position();
        double best = Double.POSITIVE_INFINITY;
        for (BlockPos pos : BaritoneAPI.getProvider().getWorldScanner().scanChunkRadius(
                ctx, new BlockOptionalMetaLookup(block), SCAN_MAX, -1, SCAN_CHUNKS)) {
            best = Math.min(best, Vec3.atCenterOf(pos).distanceTo(from));
        }
        if (CachedChunk.BLOCKS_TO_KEEP_TRACK_OF.contains(block)) {
            IWorldData data = ctx.worldData();
            if (data != null) {
                BlockPos feet = ctx.playerFeet();
                for (BlockPos pos : data.getCachedWorld().getLocationsOf(BlockUtils.blockToString(block), SCAN_MAX, feet.getX(), feet.getZ(), 2)) {
                    best = Math.min(best, Vec3.atCenterOf(pos).distanceTo(from));
                }
            }
        }
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
