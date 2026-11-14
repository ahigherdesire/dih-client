package baritone.acquire.exec;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The nether portals this world's trips went through, one per dimension ("overworld", "the_nether"), so the way home
 * is known. {@code #beat} copies it into its saved campaign and back, so it survives a restart. Thread-safe.
 */
public final class PortalMemory {

    private static final Map<String, BlockPos> PORTALS = new LinkedHashMap<>();
    private static String world = "";

    private PortalMemory() {
    }

    /** The portal remembered in {@code dimension} ("overworld", "the_nether"), or null. */
    public static synchronized BlockPos get(String world, String dimension) {
        return world.equals(PortalMemory.world) ? PORTALS.get(dimension) : null;
    }

    public static synchronized void put(String world, String dimension, BlockPos portal) {
        if (!world.equals(PortalMemory.world)) {
            PORTALS.clear();
            PortalMemory.world = world;
        }
        PORTALS.put(dimension, portal.immutable());
    }

    /** Every remembered portal in {@code world}, dimension to position. */
    public static synchronized Map<String, BlockPos> all(String world) {
        return world.equals(PortalMemory.world) ? Map.copyOf(PORTALS) : Map.of();
    }

    public static synchronized void clear() {
        PORTALS.clear();
        world = "";
    }
}
