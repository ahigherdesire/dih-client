package dihclient.util;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.lang.ref.WeakReference;

/** A single temporary, client-side cuboid belonging to the current world. */
public final class DihRegionHighlight {
    public static final int COLOR = 0xFF3BD7FF;
    /** Marker colour of the first corner typed ({@code x y z}). */
    public static final int FIRST_CORNER_COLOR = 0xFF55FF55;
    /** Marker colour of the second corner typed ({@code x1 y1 z1}). */
    public static final int SECOND_CORNER_COLOR = 0xFFFFAA33;

    /** The highlighted cuboid and the two corner blocks it was built from. */
    public record Region(AABB box, BlockPos first, BlockPos second) {}

    private static WeakReference<ClientLevel> level = new WeakReference<>(null);
    private static Region region;

    private DihRegionHighlight() {}

    public static void show(ClientLevel currentLevel, int x, int y, int z, int x1, int y1, int z1) {
        level = new WeakReference<>(currentLevel);
        region = new Region(bounds(x, y, z, x1, y1, z1), new BlockPos(x, y, z), new BlockPos(x1, y1, z1));
    }

    public static void clear() {
        level.clear();
        region = null;
    }

    /** The region shown in {@code currentLevel}, or null; a region from another world is dropped. */
    public static Region region(ClientLevel currentLevel) {
        if (currentLevel == null || currentLevel != level.get()) clear();
        return region;
    }

    public static AABB box(ClientLevel currentLevel) {
        Region current = region(currentLevel);
        return current == null ? null : current.box();
    }

    static AABB bounds(int x, int y, int z, int x1, int y1, int z1) {
        return new AABB(Math.min(x, x1), Math.min(y, y1), Math.min(z, z1),
            (double) Math.max(x, x1) + 1.0, (double) Math.max(y, y1) + 1.0,
            (double) Math.max(z, z1) + 1.0);
    }
}
