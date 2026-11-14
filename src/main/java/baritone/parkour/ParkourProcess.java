package baritone.parkour;

import baritone.Baritone;
import baritone.ai.director.JobStatus;
import baritone.ai.director.LiveDirectorHost;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.parkour.course.CourseGraph;
import baritone.parkour.exec.ParkourExecutor;
import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.LevelSnapshot;
import baritone.parkour.sim.PlayerPhysics;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.sim.TickInput;
import baritone.parkour.solve.JumpSolver;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@code #parkour}: runs a parkour course jump by jump. Each jump is solved exactly ({@link JumpSolver}) from where the
 * player stands, on a worker thread, and played by a {@link ParkourExecutor}. With a goal, the jumps follow the
 * cheapest route of the {@link CourseGraph}; without one, the course is followed forward (away from the spots
 * already visited, towards checkpoint blocks) until nothing is left to jump to. A jump the solver rules out is never
 * tried again; a jump fallen from three times stops the run with its name.
 */
public final class ParkourProcess extends BaritoneProcessHelper {
    private static final ExecutorService SOLVER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DIH parkour route");
        thread.setDaemon(true);
        return thread;
    });
    private static final int RADIUS = 20;
    private static final int MAX_FALLS = 3;

    private enum Mode { IDLE, GOTO, FOLLOW }

    private Mode mode = Mode.IDLE;
    private BlockPos goal;
    private CourseGraph graph;
    private BlockSource snapshot;
    private BlockPos at;
    private BlockPos jumpingTo;
    private CompletableFuture<JumpSolver.Result> solving;
    private PlayerState solvedFrom;
    private ParkourExecutor executor;
    private final Set<BlockPos> visited = new HashSet<>();
    private double headingX;
    private double headingZ;
    private int jumps;
    private int falls;
    /** The largest gap between predicted and real position seen in this run's jumps. */
    private double maxDrift;
    private long startedAt;
    private String last = "";

    private static boolean jobRegistered;
    /** How the last run ended, for a director waiting on the "parkour" job: null while running or never run. */
    private JobStatus outcome;

    public ParkourProcess(Baritone baritone) {
        super(baritone);
        if (!jobRegistered) {
            jobRegistered = true;
            LiveDirectorHost.registerJob("parkour", new LiveDirectorHost.ClientJob(
                this::jobStatus, () -> net.minecraft.client.Minecraft.getInstance().execute(this::stop)));
        }
    }

    public JobStatus jobStatus() {
        if (mode != Mode.IDLE) return JobStatus.running();
        return outcome == null ? JobStatus.failed("parkour isn't running") : outcome;
    }

    // ---------------------------------------------------------------- commands

    public String follow() {
        return begin(Mode.FOLLOW, null);
    }

    public String goTo(BlockPos target) {
        return begin(Mode.GOTO, target);
    }

    public String stop() {
        if (mode == Mode.IDLE) return "Not running parkour. " + last;
        end("Stopped parkour after " + jumps + " jumps.");
        return last;
    }

    public String status() {
        if (mode == Mode.IDLE) return last.isEmpty() ? "Not running parkour." : last;
        return (mode == Mode.GOTO ? "Parkour to " + goal.toShortString() : "Following the course") + ": " + jumps + " jumps, "
            + falls + " falls" + (jumpingTo != null ? ", jumping to " + jumpingTo.toShortString() : "");
    }

    public boolean running() {
        return mode != Mode.IDLE;
    }

    public int jumps() {
        return jumps;
    }

    public int falls() {
        return falls;
    }

    public double maxDrift() {
        return maxDrift;
    }

    private String begin(Mode next, BlockPos target) {
        if (ctx.player() == null || ctx.world() == null) throw new IllegalArgumentException("Join a world first.");
        if (mode != Mode.IDLE) end("");
        baritone.getPathingBehavior().cancelEverything();
        mode = next;
        outcome = null;
        goal = target;
        graph = null;
        visited.clear();
        headingX = 0;
        headingZ = 0;
        jumps = 0;
        falls = 0;
        maxDrift = 0.0;
        startedAt = System.currentTimeMillis();
        at = standingOn();
        if (at != null) visited.add(at);
        String line = next == Mode.GOTO ? "Parkour to " + target.toShortString() + "." : "Following the parkour course.";
        logDirect(line + " #parkour stop stops.");
        return line;
    }

    // ---------------------------------------------------------------- the loop

    @Override
    public boolean isActive() {
        return mode != Mode.IDLE;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (mode == Mode.IDLE) return new PathingCommand(null, PathingCommandType.DEFER);
        if (ctx.player() == null) {
            end("Stopped parkour: left the world.");
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        if (executor != null) {
            ParkourExecutor.Status status = executor.tick(ctx.player());
            switch (status) {
                case RUNNING -> {
                    return pause();
                }
                case LANDED -> {
                    jumps++;
                    maxDrift = Math.max(maxDrift, executor.maxDrift());
                    if (!executor.firstDrift().isEmpty()) System.out.println("[DIH parkour] drift: " + executor.firstDrift());
                    BlockPos from = at;
                    at = jumpingTo;
                    visited.add(at);
                    if (from != null) {
                        double dx = at.getX() - from.getX(), dz = at.getZ() - from.getZ(), len = Math.sqrt(dx * dx + dz * dz);
                        if (len > 0) {
                            headingX = dx / len;
                            headingZ = dz / len;
                        }
                    }
                    executor = null;
                    jumpingTo = null;
                    if (mode == Mode.GOTO && at.equals(goal)) {
                        end("Parkour done: reached " + goal.toShortString() + " in " + jumps + " jumps, " + falls + " falls, "
                            + (System.currentTimeMillis() - startedAt) / 1000 + " s.");
                        return new PathingCommand(null, PathingCommandType.DEFER);
                    }
                }
                case FELL, STOPPED -> {
                    falls++;
                    String why = executor.detail();
                    executor = null;
                    int onThisJump = graph == null || at == null || jumpingTo == null ? 1 : graph.fell(at, jumpingTo);
                    if (onThisJump >= MAX_FALLS) {
                        end("Stopped parkour: fell " + onThisJump + " times on the jump from " + at.toShortString() + " to "
                            + jumpingTo.toShortString() + ".");
                        return new PathingCommand(null, PathingCommandType.DEFER);
                    }
                    logDirect("Missed the jump to " + jumpingTo.toShortString() + " (" + why + "); trying again.");
                    jumpingTo = null;
                    at = null;
                }
            }
        }
        if (solving != null) {
            if (!solving.isDone()) return pause();
            // The plan starts from where the player comes to rest; wait for that.
            PlayerState now = ParkourExecutor.stateOf(ctx.player());
            if (!atRest(now)) return pause();
            JumpSolver.Result result = solving.getNow(null);
            solving = null;
            if (result instanceof JumpSolver.Found found) {
                if (Math.abs(now.x - solvedFrom.x) + Math.abs(now.y - solvedFrom.y) + Math.abs(now.z - solvedFrom.z) > 1.0E-4) {
                    jumpingTo = null;
                    planNext();
                    return pause();
                }
                executor = new ParkourExecutor(snapshot, now, found.inputs(), jumpingTo);
                executor.tick(ctx.player());
                return pause();
            }
            // Ruled out from anywhere on this platform, or follow mode would just try it again from the next block.
            if (at != null) graph.markInfeasible(at, jumpingTo);
            for (BlockPos launch : launchPlatform) graph.markInfeasible(launch, jumpingTo);
            logDirect("No way to make the jump to " + jumpingTo.toShortString() + " (" + ((JumpSolver.Impossible) result).reason() + "); looking for another.");
            jumpingTo = null;
        }
        if (!ctx.player().onGround()) return pause();
        planNext();
        return pause();
    }

    private PathingCommand pause() {
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    /** The platform the jump being solved leaves from. */
    private List<BlockPos> launchPlatform = List.of();

    /** Picks the next spot and starts solving the jump to it. */
    private void planNext() {
        BlockPos here = standingOn();
        if (here == null) return;
        if (at == null || !at.equals(here)) at = here;
        visited.add(at);
        snapshot = LevelSnapshot.around(ctx.world(), at, RADIUS, 8, 8);
        if (graph == null) graph = new CourseGraph(snapshot);
        else graph = carryOver(graph, snapshot);
        List<BlockPos> spots = graph.spots(at, RADIUS - 2, 6, 4);
        // The whole platform underfoot counts as done, so following doesn't wander round it.
        List<BlockPos> platform = graph.platform(at, spots, 64);
        visited.addAll(platform);
        launchPlatform = platform;
        BlockPos next;
        if (mode == Mode.GOTO) {
            if (at.equals(goal)) {
                end("Parkour done: already on " + goal.toShortString() + ".");
                return;
            }
            if (!spots.contains(goal)) spots.add(goal);
            List<BlockPos> route = graph.route(at, goal, spots);
            if (route.size() < 2) {
                end("Stopped parkour: no route from " + at.toShortString() + " to " + goal.toShortString() + " that the jumps can make.");
                return;
            }
            next = route.get(1);
        } else {
            next = graph.next(at, platform, headingX, headingZ, visited, spots);
            if (next == null) {
                end("Parkour done: no more jumps ahead after " + jumps + " jumps, " + falls + " falls.");
                return;
            }
        }
        jumpingTo = next;
        solvedFrom = restingState(ParkourExecutor.stateOf(ctx.player()), snapshot);
        BlockSource world = snapshot;
        PlayerState from = solvedFrom;
        BlockPos target = next;
        solving = CompletableFuture.supplyAsync(() -> JumpSolver.solve(world, from, target, JumpSolver.Limits.THOROUGH), SOLVER);
    }

    /** Whether the player has stopped: on the ground with no horizontal speed (the client zeroes tiny speeds). */
    private static boolean atRest(PlayerState s) {
        return s.onGround && s.vx == 0.0 && s.vz == 0.0;
    }

    /** Where the player comes to rest with no keys pressed, as the simulator plays it out. */
    private static PlayerState restingState(PlayerState from, BlockSource world) {
        PlayerState s = from;
        for (int i = 0; i < 40 && !atRest(s); i++) s = PlayerPhysics.step(s, TickInput.none(s.yaw), world);
        return s;
    }

    /** A graph over the new snapshot that keeps the old one's ruled-out jumps and fall counts. */
    private static CourseGraph carryOver(CourseGraph old, BlockSource snapshot) {
        return old.withWorld(snapshot);
    }

    private BlockPos standingOn() {
        if (ctx.player() == null || !ctx.player().onGround()) return null;
        PlayerState s = ParkourExecutor.stateOf(ctx.player());
        return s.support != null ? s.support : ctx.player().getOnPos();
    }

    private void end(String line) {
        if (executor != null && ctx.player() != null) executor.release(ctx.player());
        executor = null;
        if (solving != null) solving.cancel(true);
        solving = null;
        mode = Mode.IDLE;
        jumpingTo = null;
        if (!line.isEmpty()) {
            last = line;
            logDirect(line);
        }
        outcome = line.startsWith("Parkour done") ? JobStatus.done(line) : JobStatus.failed(line.isEmpty() ? "stopped" : line);
    }

    @Override
    public void onLostControl() {
        if (mode != Mode.IDLE) end("Stopped parkour.");
    }

    @Override
    public double priority() {
        return 5.0;
    }

    @Override
    public String displayName0() {
        return "Parkour: " + status();
    }
}
