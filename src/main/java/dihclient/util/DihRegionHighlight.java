package dihclient.util;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.AABB;

import java.lang.ref.WeakReference;

/** A single temporary, client-side cuboid belonging to the current world. */
public final class DihRegionHighlight {
    public static final int COLOR = 0xFF3BD7FF;

    private static WeakReference<ClientLevel> level = new WeakReference<>(null);
    private static AABB box;

    private DihRegionHighlight() {}

    public static void show(ClientLevel currentLevel, int x, int y, int z, int x1, int y1, int z1) {
        level = new WeakReference<>(currentLevel);
        box = bounds(x, y, z, x1, y1, z1);
    }

    public static void clear() {
        level.clear();
        box = null;
    }

    public static AABB box(ClientLevel currentLevel) {
        if (currentLevel == null || currentLevel != level.get()) clear();
        return box;
    }

    static AABB bounds(int x, int y, int z, int x1, int y1, int z1) {
        return new AABB(Math.min(x, x1), Math.min(y, y1), Math.min(z, z1),
            (double) Math.max(x, x1) + 1.0, (double) Math.max(y, y1) + 1.0,
            (double) Math.max(z, z1) + 1.0);
    }
}
