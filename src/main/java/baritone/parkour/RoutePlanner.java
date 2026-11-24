package baritone.parkour;

import baritone.parkour.course.CourseFollower;
import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.LevelSnapshot;
import baritone.parkour.sim.PlayerPhysics;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.sim.TickInput;
import baritone.parkour.solve.JumpChain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Plans the course ahead of the player, many jumps deep, on a worker thread: each jump is solved from the moment the one
 * before it touches down, still moving, so the whole route is one run with no stops. The player takes each jump at the
 * touchdown it was solved for ({@link #take}); a real touchdown that doesn't match (drift, a server correction) gets no
 * jump, the current one stops the player as planned, and the run is planned again from there.
 *
 * <p>The worker reads the level as it goes (as Baritone's own path search does) and plans only into loaded chunks; past
 * them it waits for the player to get closer.
 */
final class RoutePlanner {
    /** The most jumps planned ahead of the one being made. */
    static final int MAX_AHEAD = 100;
    private static final int RADIUS = 20;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DIH parkour route ahead");
        thread.setDaemon(true);
        return thread;
    });

    /** The next spot after landing on a spot, in the world round it; null ends the route. */
    interface Chooser {
        BlockPos after(BlockPos landing, BlockSource world);
    }

    /** One planned jump: where it takes over, its inputs, the world it was solved in, and its predicted feet path. */
    record Leg(JumpChain.Link link, BlockSource world, List<Vec3> path) {
    }

    private final ConcurrentLinkedDeque<Leg> ahead = new ConcurrentLinkedDeque<>();
    /** The spot after the last planned one, picked already (to plan that one with it in view). */
    private BlockPos upcoming;
    /** Set when the spot after next couldn't be picked yet (the course ended, or it's past the loaded chunks). */
    private boolean thenUnknown;
    private volatile boolean cancelled;
    private volatile String stoppedBecause = "";
    private Future<?> task;

    private RoutePlanner() {
    }

    /**
     * Starts planning past the jump being made now: from {@code from}, {@code plan} goes to {@code target}. The chooser
     * runs on the worker thread only, so it must own its state (a copy of the run's follower, say).
     */
    static RoutePlanner start(Level level, PlayerState from, List<TickInput> plan, BlockPos target, Chooser chooser) {
        RoutePlanner planner = new RoutePlanner();
        planner.task = WORKER.submit(() -> planner.run(level, from, plan, target, chooser));
        return planner;
    }

    private void run(Level level, PlayerState from, List<TickInput> plan, BlockPos target, Chooser chooser) {
        try {
            while (!cancelled && ahead.size() < MAX_AHEAD) {
                if (!loaded(level, target)) {
                    // Past the loaded chunks: wait for the player to get closer.
                    Thread.sleep(250);
                    continue;
                }
                BlockSource world = LevelSnapshot.around(level, target, RADIUS, 8, 8);
                // The spots are picked in order, each once; the one after next is kept for the next round.
                BlockPos next = upcoming != null ? upcoming : chooser.after(target, world);
                upcoming = null;
                if (next == null) {
                    stoppedBecause = "the course ends at " + target.toShortString();
                    return;
                }
                BlockPos then = loaded(level, next) ? chooser.after(next, LevelSnapshot.around(level, next, RADIUS, 8, 8)) : null;
                upcoming = then;
                if (then == null) thenUnknown = true;
                JumpChain.Link link = JumpChain.link(world, from, plan, target, next, then, JumpChain.LIMITS);
                if (link == null) {
                    stoppedBecause = "no jump without stopping from " + target.toShortString() + " to " + next.toShortString();
                    return;
                }
                ahead.addLast(new Leg(link, world, path(world, link.from(), link.inputs())));
                from = link.from();
                plan = link.inputs();
                target = link.target();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // A chunk changing under the reader, say: the route just stops here and is planned again later.
            stoppedBecause = "planning failed: " + e;
        }
    }

    private static boolean loaded(Level level, BlockPos center) {
        for (int cx = (center.getX() - RADIUS) >> 4; cx <= (center.getX() + RADIUS) >> 4; cx++) {
            for (int cz = (center.getZ() - RADIUS) >> 4; cz <= (center.getZ() + RADIUS) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }

    private static List<Vec3> path(BlockSource world, PlayerState from, List<TickInput> inputs) {
        List<Vec3> out = new ArrayList<>(inputs.size() + 1);
        PlayerState s = from;
        out.add(new Vec3(s.x, s.y, s.z));
        for (TickInput in : inputs) {
            s = PlayerPhysics.step(s, in, world);
            out.add(new Vec3(s.x, s.y, s.z));
        }
        return out;
    }

    /** The next planned jump if the player, as it is now, is where it takes over; it is then taken off the route. */
    Leg take(PlayerState real) {
        Leg head = ahead.peekFirst();
        if (head == null || !JumpChain.matches(real, head.link().from())) return null;
        ahead.pollFirst();
        return head;
    }

    /** The planned jumps after the current one, nearest first. */
    List<Leg> ahead() {
        return List.copyOf(ahead);
    }

    int size() {
        return ahead.size();
    }

    String stoppedBecause() {
        return stoppedBecause;
    }

    void cancel() {
        cancelled = true;
        if (task != null) task.cancel(true);
        ahead.clear();
    }
}
