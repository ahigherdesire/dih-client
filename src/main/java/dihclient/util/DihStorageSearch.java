package dihclient.util;

import baritone.cache.ChestMemory.SearchResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.List;

/** Temporary search state and selection rules; contents themselves belong to ChestMemory. */
public final class DihStorageSearch {
    public static final int MAX_MARKERS = 64;
    public static final double RADIUS = 128;

    private WeakReference<Object> level = new WeakReference<>(null);
    private String query;
    private BlockPos selected;

    public void start(Object currentLevel, String query, BlockPos selected) {
        clear();
        if (currentLevel == null || query == null || query.isBlank()) return;
        this.level = new WeakReference<>(currentLevel);
        this.query = query.strip();
        this.selected = selected == null ? null : selected.immutable();
    }

    public void clear() {
        level.clear();
        query = null;
        selected = null;
    }

    public String query(Object currentLevel) {
        if (currentLevel == null || currentLevel != level.get()) clear();
        return query;
    }

    public BlockPos selected(Object currentLevel) {
        query(currentLevel);
        return selected;
    }

    /** Never mutates the remembered search results. Distances include Y and use block centres. */
    public static List<SearchResult> select(List<SearchResult> hits, String world, String dimension,
                                            Vec3 player, double radius, int limit) {
        return select(hits, world, dimension, player, radius, limit, null);
    }

    public static List<SearchResult> select(List<SearchResult> hits, String world, String dimension,
                                            Vec3 player, double radius, int limit, BlockPos selected) {
        if (limit <= 0 || radius < 0) return List.of();
        double maxSq = radius * radius;
        return hits.stream()
            .filter(hit -> world.equals(hit.record.world) && dimension.equals(hit.record.dimension))
            .filter(hit -> selected == null || selected.equals(hit.record.pos))
            .filter(hit -> hit.record.pos.distToCenterSqr(player) <= maxSq)
            .sorted(Comparator.comparingDouble((SearchResult hit) -> hit.record.pos.distToCenterSqr(player))
                .thenComparingInt(hit -> hit.record.pos.getX())
                .thenComparingInt(hit -> hit.record.pos.getY())
                .thenComparingInt(hit -> hit.record.pos.getZ()))
            .limit(limit).toList();
    }

    public static String age(long timestamp, long now) {
        if (timestamp <= 0) return "unknown";
        long seconds = Math.max(0, (now - timestamp) / 1_000);
        if (seconds < 5) return "just now";
        if (seconds < 60) return seconds + "s ago";
        if (seconds < 3_600) return seconds / 60 + "m ago";
        if (seconds < 86_400) return seconds / 3_600 + "h ago";
        return seconds / 86_400 + "d ago";
    }
}
