package baritone.acquire.exec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where to cast one obsidian block: three open cells in a row on solid floor. Lava is poured into {@code cast}, water
 * right after into {@code water} beside it, and the lava source turns to obsidian before it can flow (lava waits 30
 * ticks in the Overworld). The player stands at {@code stand}, one further along, so the water sits between them and
 * the lava. The obsidian has floor under it, so its drop can't fall into anything.
 */
record CastSite(BlockPos cast, BlockPos water, BlockPos stand, Direction toward) {

    /** What the finder needs to know about the world. */
    interface Terrain {
        /** A full solid block that can be stood on and poured onto. */
        boolean floor(BlockPos pos);

        /** Air with no fluid. */
        boolean open(BlockPos pos);

        /** Any lava, source or flowing. */
        boolean lava(BlockPos pos);
    }

    /** The site nearest {@code near} whose stand is within {@code radius} blocks and at most 3 up or down, or null. */
    static CastSite find(BlockPos near, int radius, Terrain t) {
        CastSite best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos stand = near.offset(dx, dy, dz);
                    double distance = stand.distSqr(near);
                    if (distance >= bestDistance) continue;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos water = stand.relative(d);
                        CastSite site = new CastSite(water.relative(d), water, stand, d.getOpposite());
                        if (site.ok(t)) {
                            best = site;
                            bestDistance = distance;
                            break;
                        }
                    }
                }
            }
        }
        return best;
    }

    boolean ok(Terrain t) {
        for (BlockPos cell : new BlockPos[]{cast, water, stand}) {
            if (!t.floor(cell.below()) || !t.open(cell) || !t.open(cell.above())) return false;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) if (t.lava(cell.offset(x, y, z))) return false;
                }
            }
        }
        return true;
    }
}
