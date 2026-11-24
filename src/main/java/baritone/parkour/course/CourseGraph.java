package baritone.parkour.course;

import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.PlayerPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The spots of a parkour course near the player and the jumps between them. A spot is a block whose top the player
 * can stand on (a full block, slab, fence, pane, stair, ladder top...) with room for the player above it. A jump
 * between two spots is assumed possible when it is within the reach table for its height difference; the solver
 * checks a jump only when a route wants it, and a jump it rules out is never offered again.
 */
public final class CourseGraph {
    /** Furthest centre-to-centre horizontal distance a jump can cover, by height change (index = dy + 4, dy from -4 to +1). */
    static final double[] REACH = {6.2, 6.0, 5.8, 5.4, 5.2, 4.3};
    /** Cost in ticks of a jump over a horizontal distance (roughly the airtime plus a run-up). */
    static double cost(double distance, int dy) {
        return 6.0 + distance * 4.0 + (dy > 0 ? 4.0 : 0.0);
    }

    private final BlockSource world;
    private final Set<Long> infeasible = new HashSet<>();
    private final Map<Long, Integer> failures = new HashMap<>();

    public CourseGraph(BlockSource world) {
        this.world = world;
    }

    public BlockSource world() {
        return world;
    }

    /** The same graph over a fresh snapshot: the ruled-out jumps and fall counts carry over. */
    public CourseGraph withWorld(BlockSource newWorld) {
        CourseGraph next = new CourseGraph(newWorld);
        next.infeasible.addAll(infeasible);
        next.failures.putAll(failures);
        return next;
    }

    /** Whether the player can stand on top of {@code pos}: something solid to stand on and 1.8 blocks of room above. */
    public boolean standable(BlockPos pos) {
        BlockState state = world.get(pos);
        VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos);
        if (shape.isEmpty()) return false;
        if (!state.getFluidState().isEmpty() || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)) return false;
        // A ladder's thin plate isn't somewhere to stand; ladders are climbed on the way to a spot (see byLadder).
        if (climbable(state)) return false;
        double top = pos.getY() + shape.max(Direction.Axis.Y);
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos above = pos.above(dy);
            if (above.getY() >= top + 1.8) break;
            VoxelShape room = world.get(above).getCollisionShape(EmptyBlockGetter.INSTANCE, above);
            if (!room.isEmpty() && above.getY() + room.min(Direction.Axis.Y) < top + 1.8) return false;
        }
        return true;
    }

    /** The spots within {@code radius} of {@code center}. */
    public List<BlockPos> spots(BlockPos center, int radius, int below, int above) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -below, -radius), center.offset(radius, above, radius))) {
            if (standable(pos)) out.add(pos.immutable());
        }
        return out;
    }

    /**
     * The longest jump, as distance flown between the two blocks' near edges (the player can overhang each by 0.3), by
     * height change (index = dy + 4, dy from -4 to +1): the Minecraft Parkour Wiki's "Longest Jumps" table, per tier.
     * Measured edge to edge, a 4x4 diagonal (a 3x3 gap) is a shorter jump than a straight 5-block gap.
     */
    static final double[] MAX_FLIGHT = {5.89, 5.58, 5.27, 4.96, 4.33, 3.36};

    public static boolean inReach(BlockPos from, BlockPos to) {
        int dy = to.getY() - from.getY();
        if (dy > 1 || dy < -4) return false;
        int dx = Math.abs(to.getX() - from.getX()), dz = Math.abs(to.getZ() - from.getZ());
        if (dx == 0 && dz == 0) return false;
        return flight(dx, dz) <= MAX_FLIGHT[dy + 4];
    }

    /** The wiki's jump distance between blocks {@code dx} and {@code dz} apart (centre to centre): the gap less 0.6 each way. */
    static double flight(int dx, int dz) {
        double fx = Math.max(0.0, dx - 1.6), fz = Math.max(0.0, dz - 1.6);
        return Math.sqrt(fx * fx + fz * fz);
    }

    public void markInfeasible(BlockPos from, BlockPos to) {
        infeasible.add(edgeKey(from, to));
    }

    /** Counts a fall on this jump; returns the falls so far. */
    public int fell(BlockPos from, BlockPos to) {
        return failures.merge(edgeKey(from, to), 1, Integer::sum);
    }

    public boolean usable(BlockPos from, BlockPos to) {
        return (inReach(from, to) || byLadder(from, to)) && !infeasible.contains(edgeKey(from, to));
    }

    /**
     * A spot too high to jump to, but with a ladder or vine up the side of its column within jumping reach: a jump onto
     * the ladder, a climb and a step off at the top. The solver checks it like any other jump.
     */
    public boolean byLadder(BlockPos from, BlockPos to) {
        int dy = to.getY() - from.getY();
        if (dy < 2 || dy > 5) return false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos column = to.relative(side);
            for (int k = 0; k <= dy; k++) {
                BlockPos ladder = column.below(k);
                if (!climbable(world.get(ladder))) continue;
                // Catching it means getting the feet into it: up to one block above the feet, like a +1 jump.
                int rise = ladder.getY() - (from.getY() + 1);
                if (rise > 1) continue;
                double dx = ladder.getX() - from.getX(), dz = ladder.getZ() - from.getZ();
                if (Math.sqrt(dx * dx + dz * dz) <= REACH[4 + Math.max(-4, rise)]) return true;
            }
        }
        return false;
    }

    private static boolean climbable(BlockState state) {
        return PlayerPhysics.climbable(state);
    }

    private static long edgeKey(BlockPos from, BlockPos to) {
        return from.asLong() * 31 + to.asLong();
    }

    /** The cheapest chain of spots from {@code start} to {@code goal} (both included), or an empty list. */
    public List<BlockPos> route(BlockPos start, BlockPos goal, List<BlockPos> spots) {
        Map<BlockPos, Double> best = new HashMap<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        record Entry(BlockPos pos, double cost) {
        }
        PriorityQueue<Entry> open = new PriorityQueue<>((a, b) -> Double.compare(a.cost(), b.cost()));
        best.put(start, 0.0);
        open.add(new Entry(start, 0.0));
        while (!open.isEmpty()) {
            Entry entry = open.poll();
            if (entry.cost() > best.getOrDefault(entry.pos(), Double.MAX_VALUE)) continue;
            if (entry.pos().equals(goal)) break;
            for (BlockPos next : spots) {
                if (!usable(entry.pos(), next)) continue;
                double dx = next.getX() - entry.pos().getX(), dz = next.getZ() - entry.pos().getZ();
                double c = entry.cost() + cost(Math.sqrt(dx * dx + dz * dz), next.getY() - entry.pos().getY())
                    + 20.0 * failures.getOrDefault(edgeKey(entry.pos(), next), 0);
                if (c < best.getOrDefault(next, Double.MAX_VALUE)) {
                    best.put(next, c);
                    parent.put(next, entry.pos());
                    open.add(new Entry(next, c));
                }
            }
        }
        if (!best.containsKey(goal)) return List.of();
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos at = goal; at != null; at = parent.get(at)) path.addFirst(at);
        return path;
    }

    /**
     * Where a course goes next with no goal: the nearest reachable unvisited spot that doesn't go back against
     * {@code heading} (a unit vector in x/z; a turn to the side is fine), favouring spots straight on, spots that don't
     * drop, and checkpoint-looking blocks and pressure plates. The ground under a course (a wide area lower than the
     * player) is never taken for its next spot. Null when nothing is left.
     */
    public BlockPos next(BlockPos from, double headingX, double headingZ, Set<BlockPos> visited, List<BlockPos> spots) {
        return next(from, List.of(from), headingX, headingZ, visited, spots);
    }

    /**
     * The next spot to jump to from anywhere on {@code launch} (the platform the player is on: it can walk to its edge
     * first), measured from the nearest launch block that reaches it.
     */
    public BlockPos next(BlockPos from, List<BlockPos> launch, double headingX, double headingZ, Set<BlockPos> visited, List<BlockPos> spots) {
        BlockPos best = null;
        BlockPos bestOrigin = null;
        double bestScore = -Double.MAX_VALUE;
        for (BlockPos spot : spots) {
            if (visited.contains(spot)) continue;
            BlockPos origin = null;
            double nearest = Double.MAX_VALUE;
            for (BlockPos p : launch) {
                if (!usable(p, spot)) continue;
                double d = p.distSqr(spot);
                if (d < nearest) {
                    nearest = d;
                    origin = p;
                }
            }
            if (origin == null) continue;
            double dx = spot.getX() - origin.getX(), dz = spot.getZ() - origin.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            double along = (dx * headingX + dz * headingZ) / distance;
            // Courses turn, but never double back: anything behind is where the player came from, or scenery. Behind
            // the player, that is, not just behind the launch block: a platform can reach back a long way (a lobby
            // floor), and scenery near its far end is "ahead" of that end.
            if ((headingX != 0.0 || headingZ != 0.0) && along < -0.3) continue;
            double fx = spot.getX() - from.getX(), fz = spot.getZ() - from.getZ(), fd = Math.sqrt(fx * fx + fz * fz);
            if ((headingX != 0.0 || headingZ != 0.0) && fd > 0.0 && (fx * headingX + fz * headingZ) / fd < -0.3) continue;
            int dy = spot.getY() - origin.getY();
            if (dy < 0 && isGround(spot, spots)) continue;
            // The nearest spot that carries on: courses are made to be done jump by jump.
            double score = -distance + 1.5 * along + (dy < 0 ? 1.5 * dy : 0.1 * dy);
            if (isCheckpoint(world.get(spot)) || isCheckpoint(world.get(spot.above()))) score += 3.0;
            if (score > bestScore) {
                bestScore = score;
                best = spot;
                bestOrigin = origin;
            }
        }
        lastLaunch = bestOrigin;
        return best;
    }

    /** The block of the launch platform the last {@link #next} measured its pick from: where to jump from. */
    private BlockPos lastLaunch;

    public BlockPos lastLaunch() {
        return lastLaunch;
    }

    /** The spots joined to {@code at} at its height (4-connected), at most {@code max}: the platform it's part of. */
    public List<BlockPos> platform(BlockPos at, List<BlockPos> spots, int max) {
        Set<BlockPos> all = new HashSet<>(spots);
        List<BlockPos> out = new ArrayList<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(at);
        seen.add(at);
        while (!queue.isEmpty() && out.size() < max) {
            BlockPos pos = queue.poll();
            out.add(pos);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(direction);
                if (all.contains(next) && seen.add(next)) queue.add(next);
            }
        }
        return out;
    }

    /** The most blocks a platform of a course has; more than this joined at one height is the ground (or a floor). */
    static final int GROUND_SIZE = 40;

    /** Whether {@code spot} is part of a wide area of spots at its height: the ground, not a course block. */
    boolean isGround(BlockPos spot, List<BlockPos> spots) {
        return platform(spot, spots, GROUND_SIZE + 1).size() > GROUND_SIZE;
    }

    /** Whether {@code spot} looks like a checkpoint or finish: the block, or a plate on it. */
    public boolean onCheckpoint(BlockPos spot) {
        return isCheckpoint(world.get(spot)) || isCheckpoint(world.get(spot.above()));
    }

    static boolean isCheckpoint(BlockState state) {
        return state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.EMERALD_BLOCK) || state.is(Blocks.DIAMOND_BLOCK) || state.is(Blocks.BEACON)
            || state.getBlock() instanceof net.minecraft.world.level.block.PressurePlateBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.WeightedPressurePlateBlock;
    }
}
