package baritone.parkour.course;

import baritone.parkour.sim.BlockSource;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Follow mode's sense of direction: which spot of a course to jump to next, given where the player has been. Kept apart
 * from the game so whole courses can be followed in tests.
 *
 * <p>The course is taken to start the way the player faces, and after that to go on the way the last jump went; it may
 * turn (a corner, a switchback) but never doubles back onto spots already done.
 */
public final class CourseFollower {
    /** How far round the player spots are looked for. */
    static final int RADIUS = 18;

    private CourseGraph graph;
    private final Set<BlockPos> visited = new HashSet<>();
    private BlockPos at;
    private double headingX;
    private double headingZ;

    /** {@code startYaw}: where the player faces as the run starts, taken as the way the course goes. */
    public CourseFollower(BlockSource world, float startYaw) {
        this.graph = new CourseGraph(world);
        double rad = Math.toRadians(startYaw);
        this.headingX = -Math.sin(rad);
        this.headingZ = Math.cos(rad);
    }

    private CourseFollower(CourseGraph graph) {
        this.graph = graph;
    }

    /** An independent copy (for planning ahead on another thread): same place, heading, visited spots and ruled-out jumps. */
    public CourseFollower copy() {
        CourseFollower c = new CourseFollower(graph.withWorld(graph.world()));
        c.visited.addAll(visited);
        c.at = at;
        c.headingX = headingX;
        c.headingZ = headingZ;
        return c;
    }

    /** Swaps in a fresh snapshot of the world; ruled-out jumps and falls carry over. */
    public void world(BlockSource world) {
        graph = graph.withWorld(world);
    }

    public CourseGraph graph() {
        return graph;
    }

    /** The spot to jump to next from {@code here}, or null when the course has nothing left ahead. */
    public BlockPos next(BlockPos here) {
        at = here;
        visited.add(here);
        List<BlockPos> spots = graph.spots(here, RADIUS, 6, 4);
        // The whole platform underfoot counts as done, so following doesn't wander round it.
        List<BlockPos> platform = graph.platform(here, spots, 64);
        visited.addAll(platform);
        return graph.next(here, platform, headingX, headingZ, visited, spots);
    }

    /**
     * With nothing to jump to: the block of the platform underfoot furthest on along the course's heading, at least
     * {@code min} blocks on from {@code here}, or null. Walking there brings what comes next into reach, or makes a
     * course that builds itself as the player goes build the next part.
     */
    public BlockPos furthestAlong(BlockPos here, double min) {
        List<BlockPos> platform = graph.platform(here, graph.spots(here, RADIUS, 6, 4), 256);
        BlockPos best = null;
        double bestAlong = min;
        for (BlockPos p : platform) {
            double along = (p.getX() - here.getX()) * headingX + (p.getZ() - here.getZ()) * headingZ;
            double side = Math.abs((p.getX() - here.getX()) * headingZ - (p.getZ() - here.getZ()) * headingX);
            // Straight on beats a little further but off to the side.
            double score = along - 0.5 * side;
            if (score > bestAlong) {
                bestAlong = score;
                best = p;
            }
        }
        return best;
    }

    /**
     * The spot {@link #next} will pick once the player lands on {@code to} from where it is now, without changing
     * anything: for solving the jump after next while this one is in the air.
     */
    public BlockPos peekAfter(BlockPos to) {
        double hx = headingX, hz = headingZ;
        if (at != null) {
            double dx = to.getX() - at.getX(), dz = to.getZ() - at.getZ(), len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) {
                hx = dx / len;
                hz = dz / len;
            }
        }
        List<BlockPos> spots = graph.spots(to, RADIUS, 6, 4);
        List<BlockPos> platform = graph.platform(to, spots, 64);
        Set<BlockPos> seen = new HashSet<>(visited);
        seen.add(to);
        seen.addAll(platform);
        return graph.next(to, platform, hx, hz, seen, spots);
    }

    /** Where the last {@link #next} pick is jumped from: the nearest block of the platform that reaches it. */
    public BlockPos launch() {
        return graph.lastLaunch();
    }

    /** The jump from {@code from} to {@code to} can't be made, from there or anywhere else on that platform. */
    public void infeasible(BlockPos from, BlockPos to) {
        if (from == null || to == null) return;
        graph.markInfeasible(from, to);
        for (BlockPos p : graph.platform(from, graph.spots(from, RADIUS, 6, 4), 64)) graph.markInfeasible(p, to);
    }

    /** The player made it onto {@code to}. */
    public void landed(BlockPos to) {
        if (at != null) {
            double dx = to.getX() - at.getX(), dz = to.getZ() - at.getZ(), len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) {
                headingX = dx / len;
                headingZ = dz / len;
            }
        }
        at = to;
        visited.add(to);
        // A chained jump goes on without asking for the next spot from here, so mark the platform done now.
        visited.addAll(graph.platform(to, graph.spots(to, RADIUS, 6, 4), 64));
    }
}
