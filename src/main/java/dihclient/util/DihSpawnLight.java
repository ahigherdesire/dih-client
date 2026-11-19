package dihclient.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The game-free half of the Lighting module: how dark a spawn spot is, the order the scan visits
 * cells in, and which spots survive the marker cap. Everything that touches the world lives in
 * {@link DihSpawnLightScanner}.
 */
public final class DihSpawnLight {
    /** Vanilla's monster light test samples 0..7 in the overworld; a spot brighter than its max never spawns. */
    public static final int DEFAULT_MAX_SPAWN_BRIGHTNESS = 7;

    public enum Darkness {
        /** Too bright for hostile mobs. */
        NONE,
        /** No block light, but the sky lights it during the day: mobs spawn here at night or in storms. */
        NIGHT_ONLY,
        /** Dark enough at any time of day: caves, under roofs, covered spots. */
        ALWAYS
    }

    /** A dark spawn position (the feet block; the marker sits on the floor block below it). */
    public record Spot(int x, int y, int z, Darkness darkness) {}

    private DihSpawnLight() {}

    /**
     * Classifies a spawn position by its light.
     *
     * <p>Mirrors {@code Monster.isDarkEnoughToSpawn}: block light above the dimension's limit always
     * blocks spawning (0 in the overworld, the modern rule), then the raw brightness, the max of
     * block light and sky light less the sky darkening, has to be at or below the light test's
     * sample. The sky never darkens during clear daytime, so a spot whose sky light is above the
     * test's max only spawns once it gets dark.
     *
     * @param blockLight          block light at the spawn position, 0..15
     * @param skyLight            sky light at the spawn position, 0..15 (0 in dimensions without a sky)
     * @param blockLightLimit     the dimension's monster spawn block light limit
     * @param maxSpawnBrightness  the largest value the dimension's monster light test can sample
     */
    public static Darkness classify(int blockLight, int skyLight, int blockLightLimit, int maxSpawnBrightness) {
        if (blockLight > blockLightLimit) return Darkness.NONE;
        if (blockLight > maxSpawnBrightness) return Darkness.NONE;
        return skyLight > maxSpawnBrightness ? Darkness.NIGHT_ONLY : Darkness.ALWAYS;
    }

    /** Number of cells in a scan box of the given radii (each axis spans {@code 2r + 1}). */
    public static int cellCount(int horizontal, int vertical) {
        int side = horizontal * 2 + 1;
        return side * side * (vertical * 2 + 1);
    }

    /**
     * The offset of cell {@code index} from the scan centre, as {@code {dx, dy, dz}}. Cells run
     * column by column (x, then z) and top to bottom inside a column, so every index in
     * {@code [0, cellCount)} maps to exactly one offset in the box.
     */
    public static int[] offset(int index, int horizontal, int vertical) {
        int side = horizontal * 2 + 1;
        int height = vertical * 2 + 1;
        int column = index / height;
        int dy = vertical - index % height;
        int dx = column % side - horizontal;
        int dz = column / side - horizontal;
        return new int[] {dx, dy, dz};
    }

    /**
     * The {@code cap} spots closest to the centre, nearest first, so a cap trims the far edge of the
     * scan rather than an arbitrary corner of it.
     */
    public static List<Spot> nearest(List<Spot> spots, int cap, double cx, double cy, double cz) {
        if (cap <= 0 || spots.isEmpty()) return List.of();
        List<Spot> sorted = new ArrayList<>(spots);
        sorted.sort(Comparator.comparingDouble(s -> distanceSq(s, cx, cy, cz)));
        return sorted.size() <= cap ? List.copyOf(sorted) : List.copyOf(sorted.subList(0, cap));
    }

    static double distanceSq(Spot spot, double cx, double cy, double cz) {
        double dx = spot.x() + 0.5 - cx;
        double dy = spot.y() - cy;
        double dz = spot.z() + 0.5 - cz;
        return dx * dx + dy * dy + dz * dz;
    }
}
