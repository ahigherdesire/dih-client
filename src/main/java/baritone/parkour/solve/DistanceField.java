package baritone.parkour.solve;

import baritone.parkour.sim.BlockSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;
import java.util.PriorityQueue;

/**
 * Horizontal distance to the target going round walls, on a grid of {@link #CELL}-block cells. A cell is blocked when
 * the player's box (0.6 wide) standing there would hit a block at body height, between {@code bandLow} and
 * {@code bandHigh}; floors and gaps don't block (the player can jump over them). Used as the solver's heuristic and to
 * aim: the aim follows the shortest path, so going round a wall (a neo) is the obvious line, not a detour to search for.
 */
final class DistanceField {
    static final double CELL = 0.2;
    /** The player's half width plus a little room, so the path doesn't graze walls (grazing kills the speed). */
    private static final double HALF_WIDTH = 0.3 + 0.08;

    private final double originX;
    private final double originZ;
    private final int width;
    private final int depth;
    private final boolean[] blocked;
    private final float[] distance;

    DistanceField(BlockSource world, double minX, double minZ, double maxX, double maxZ, double bandLow, double bandHigh,
                  double targetX, double targetZ, BlockPos target) {
        this.originX = minX;
        this.originZ = minZ;
        this.width = Math.max(1, (int) Math.ceil((maxX - minX) / CELL) + 1);
        this.depth = Math.max(1, (int) Math.ceil((maxZ - minZ) / CELL) + 1);
        this.blocked = new boolean[width * depth];
        this.distance = new float[width * depth];
        markBlocked(world, bandLow, bandHigh, target);
        fill(targetX, targetZ);
    }

    private void markBlocked(BlockSource world, double bandLow, double bandHigh, BlockPos target) {
        int bx0 = Mth.floor(originX - 1), bx1 = Mth.floor(originX + width * CELL + 1);
        int bz0 = Mth.floor(originZ - 1), bz1 = Mth.floor(originZ + depth * CELL + 1);
        int by0 = Mth.floor(bandLow), by1 = Mth.floor(bandHigh);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int bx = bx0; bx <= bx1; bx++) {
            for (int bz = bz0; bz <= bz1; bz++) {
                for (int by = by0 - 1; by <= by1; by++) {
                    pos.set(bx, by, bz);
                    if (pos.equals(target)) continue;
                    BlockState state = world.get(pos);
                    if (state.isAir()) continue;
                    VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos);
                    for (AABB box : shape.toAabbs()) {
                        double y0 = by + box.minY, y1 = by + box.maxY;
                        if (y1 <= bandLow || y0 >= bandHigh) continue;
                        // Inflate by the player's half width: a centre inside this rectangle hits the block.
                        double x0 = bx + box.minX - HALF_WIDTH, x1 = bx + box.maxX + HALF_WIDTH;
                        double z0 = bz + box.minZ - HALF_WIDTH, z1 = bz + box.maxZ + HALF_WIDTH;
                        int i0 = Math.max(0, (int) Math.ceil((x0 - originX) / CELL + 1e-9));
                        int i1 = Math.min(width - 1, (int) Math.floor((x1 - originX) / CELL - 1e-9));
                        int k0 = Math.max(0, (int) Math.ceil((z0 - originZ) / CELL + 1e-9));
                        int k1 = Math.min(depth - 1, (int) Math.floor((z1 - originZ) / CELL - 1e-9));
                        for (int i = i0; i <= i1; i++) {
                            for (int k = k0; k <= k1; k++) blocked[k * width + i] = true;
                        }
                    }
                }
            }
        }
    }

    /** Dijkstra over the 8-connected grid from the target's cell. */
    private void fill(double targetX, double targetZ) {
        Arrays.fill(distance, Float.MAX_VALUE);
        int ti = Mth.clamp((int) Math.round((targetX - originX) / CELL), 0, width - 1);
        int tk = Mth.clamp((int) Math.round((targetZ - originZ) / CELL), 0, depth - 1);
        PriorityQueue<long[]> open = new PriorityQueue<>((a, b) -> Float.compare(Float.intBitsToFloat((int) a[1]), Float.intBitsToFloat((int) b[1])));
        int start = tk * width + ti;
        distance[start] = 0.0F;
        blocked[start] = false;
        open.add(new long[]{start, Float.floatToIntBits(0.0F)});
        float diag = (float) (CELL * Math.sqrt(2.0));
        while (!open.isEmpty()) {
            long[] entry = open.poll();
            int cell = (int) entry[0];
            float d = Float.intBitsToFloat((int) entry[1]);
            if (d > distance[cell]) continue;
            int ci = cell % width, ck = cell / width;
            for (int di = -1; di <= 1; di++) {
                for (int dk = -1; dk <= 1; dk++) {
                    if (di == 0 && dk == 0) continue;
                    int ni = ci + di, nk = ck + dk;
                    if (ni < 0 || nk < 0 || ni >= width || nk >= depth) continue;
                    int next = nk * width + ni;
                    if (blocked[next]) continue;
                    if (di != 0 && dk != 0 && (blocked[ck * width + ni] || blocked[nk * width + ci])) continue;
                    float nd = d + (di != 0 && dk != 0 ? diag : (float) CELL);
                    if (nd < distance[next]) {
                        distance[next] = nd;
                        open.add(new long[]{next, Float.floatToIntBits(nd)});
                    }
                }
            }
        }
    }

    /** Whether the straight segment from (x0, z0) to (x1, z1) crosses no blocked cell. */
    private boolean clear(double x0, double z0, double x1, double z1) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        int samples = (int) Math.ceil(len / (CELL * 0.5));
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            int c = cellOf(x0 + (x1 - x0) * t, z0 + (z1 - z0) * t);
            if (c >= 0 && blocked[c]) return false;
        }
        return true;
    }

    private int cellOf(double x, double z) {
        int i = (int) Math.round((x - originX) / CELL);
        int k = (int) Math.round((z - originZ) / CELL);
        if (i < 0 || k < 0 || i >= width || k >= depth) return -1;
        return k * width + i;
    }

    /** Distance to the target round walls from (x, z), or the straight-line distance outside the grid. */
    double distance(double x, double z, double targetX, double targetZ) {
        int cell = cellOf(x, z);
        double straight = Math.hypot(targetX - x, targetZ - z);
        if (cell < 0) return straight;
        float d = distance[cell];
        if (d == Float.MAX_VALUE) return blocked[cell] ? nearestFree(x, z, targetX, targetZ) : straight + 50.0;
        return Math.max(straight, d);
    }

    private double nearestFree(double x, double z, double targetX, double targetZ) {
        int ci = (int) Math.round((x - originX) / CELL), ck = (int) Math.round((z - originZ) / CELL);
        double best = Double.MAX_VALUE;
        for (int r = 1; r <= 3; r++) {
            for (int di = -r; di <= r; di++) {
                for (int dk = -r; dk <= r; dk++) {
                    int ni = ci + di, nk = ck + dk;
                    if (ni < 0 || nk < 0 || ni >= width || nk >= depth) continue;
                    float d = distance[nk * width + ni];
                    if (d != Float.MAX_VALUE) best = Math.min(best, d + Math.hypot(di, dk) * CELL);
                }
            }
            if (best != Double.MAX_VALUE) return best;
        }
        return Math.hypot(targetX - x, targetZ - z) + 50.0;
    }

    /**
     * The yaw to head along the shortest path from (x, z): towards the furthest cell up to {@code lookahead} blocks down
     * the path that's in a straight line of sight (so headings aren't stuck to the grid's eight directions), or straight
     * at the target outside the grid.
     */
    float aimYaw(double x, double z, double targetX, double targetZ, double lookahead) {
        int cell = cellOf(x, z);
        double tx = targetX, tz = targetZ;
        if (cell >= 0 && distance[cell] != Float.MAX_VALUE) {
            int[] path = new int[64];
            int n = 0;
            int at = cell;
            double walked = 0.0;
            while (walked < lookahead && distance[at] > 0.0F && n < path.length) {
                int ci = at % width, ck = at / width;
                int best = at;
                float bestD = distance[at];
                for (int di = -1; di <= 1; di++) {
                    for (int dk = -1; dk <= 1; dk++) {
                        int ni = ci + di, nk = ck + dk;
                        if (ni < 0 || nk < 0 || ni >= width || nk >= depth) continue;
                        int next = nk * width + ni;
                        if (distance[next] < bestD) {
                            bestD = distance[next];
                            best = next;
                        }
                    }
                }
                if (best == at) break;
                walked += CELL * ((best % width != ci && best / width != ck) ? Math.sqrt(2.0) : 1.0);
                at = best;
                path[n++] = at;
            }
            if (n > 0 && distance[path[n - 1]] == 0.0F && clear(x, z, targetX, targetZ)) {
                // the target itself is in sight
            } else {
                int pick = n > 0 ? path[0] : cell;
                for (int i = n - 1; i > 0; i--) {
                    double px = originX + (path[i] % width) * CELL, pz = originZ + (path[i] / width) * CELL;
                    if (clear(x, z, px, pz)) {
                        pick = path[i];
                        break;
                    }
                }
                if (pick != cell) {
                    tx = originX + (pick % width) * CELL;
                    tz = originZ + (pick / width) * CELL;
                }
            }
        }
        double dx = tx - x, dz = tz - z;
        if (dx * dx + dz * dz < 1e-8) {
            dx = targetX - x;
            dz = targetZ - z;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
