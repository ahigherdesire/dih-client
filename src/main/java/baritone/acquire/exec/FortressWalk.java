package baritone.acquire.exec;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Where to walk next through a fortress with no spawner in view: the nether-brick floor furthest from every spot
 * already walked to, within one leg. Spawners sit in the fortress's far halls, often beyond the loaded chunks of where
 * the fortress was first reached.
 */
final class FortressWalk {

    /** A spot nearer than this is no step on. */
    static final int MIN_LEG = 12;

    private FortressWalk() {}

    /**
     * How good {@code spot} is as the next leg's end: its horizontal distance to the nearest spot already walked to
     * (the start among them), or -1 when it is not a leg from {@code feet} (too near or beyond {@code maxLeg}).
     */
    static double score(BlockPos spot, BlockPos feet, List<BlockPos> walked, int maxLeg) {
        double leg = horizontal(spot, feet);
        if (leg < MIN_LEG || leg > maxLeg) return -1;
        double nearest = leg;
        for (BlockPos p : walked) nearest = Math.min(nearest, horizontal(spot, p));
        return nearest;
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
