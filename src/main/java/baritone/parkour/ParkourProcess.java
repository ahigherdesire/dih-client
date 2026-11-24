package baritone.parkour;

import baritone.Baritone;
import baritone.ai.director.JobStatus;
import baritone.ai.director.LiveDirectorHost;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.parkour.course.CourseFollower;
import baritone.parkour.course.CourseGraph;
import baritone.parkour.exec.ParkourExecutor;
import baritone.parkour.record.CourseRecorder;
import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.LevelSnapshot;
import baritone.parkour.sim.PlayerPhysics;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.sim.TickInput;
import baritone.parkour.solve.JumpChain;
import baritone.parkour.solve.JumpSolver;
import baritone.parkour.solve.RunUpSearch;
import baritone.utils.BaritoneProcessHelper;
import baritone.api.pathing.goals.GoalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
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
public final class ParkourProcess extends BaritoneProcessHelper implements AbstractGameEventListener {
    private static final ExecutorService SOLVER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DIH parkour route");
        thread.setDaemon(true);
        return thread;
    });
    private static final int RADIUS = 20;
    private static final int MAX_FALLS = 3;
    /** How long follow mode waits at the end of what it can see for more of the course to appear (ticks). */
    private static final int IDLE_TICKS = 40;
    private int idleTicks;

    private enum Mode { IDLE, GOTO, FOLLOW }

    private Mode mode = Mode.IDLE;
    private BlockPos goal;
    /** Which spot comes next, and the jumps ruled out so far; made on the first snapshot of a run. */
    private CourseFollower follower;
    private BlockSource snapshot;
    private BlockPos at;
    private BlockPos jumpingTo;
    private CompletableFuture<JumpSolver.Result> solving;
    private PlayerState solvedFrom;
    private ParkourExecutor executor;
    private int jumps;
    /** This run's jumps taken straight on from a touchdown, jumps solved from a stop, bigger second searches, walks. */
    private int chained, fromRest, retries, walks;
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
        baritone.getGameEventHandler().registerEventListener(this);
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
        int planned = route == null ? 0 : route.size();
        return (mode == Mode.GOTO ? "Parkour to " + goal.toShortString() : "Following the course") + ": " + jumps + " jumps"
            + (checkpoints > 0 ? ", " + checkpoints + " checkpoints" : "") + ", " + planned + " planned ahead, " + falls + " falls, "
            + (System.currentTimeMillis() - startedAt) / 1000 + " s" + (walkingTo != null ? ", walking on" : "")
            + (jumpingTo != null ? ", jumping to " + jumpingTo.toShortString() : "");
    }

    /** Checkpoint-looking blocks (gold, plates...) landed on this run, and the last one, so it's counted once. */
    private int checkpoints;
    private BlockPos lastCheckpoint;

    /** Counts and announces a landing on a checkpoint-looking block (a gold block, a pressure plate...). */
    private void landedOn(BlockPos block) {
        if (follower == null || block == null || block.equals(lastCheckpoint) || !follower.graph().onCheckpoint(block)) return;
        lastCheckpoint = block;
        checkpoints++;
        logDirect(String.format(java.util.Locale.ROOT, "Checkpoint %d at %s: %d jumps, %.1f s.", checkpoints, block.toShortString(), jumps,
            (System.currentTimeMillis() - startedAt) / 1000.0));
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
        follower = null;
        checkpoints = 0;
        lastCheckpoint = null;
        idleTicks = 0;
        jumps = 0;
        chained = fromRest = retries = walks = runUps = 0;
        falls = 0;
        maxDrift = 0.0;
        startedAt = System.currentTimeMillis();
        at = standingOn();
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
        if (executor != null && chainOn()) {
            PathingCommand chained = takeLink();
            if (chained != null) return chained;
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
                    at = jumpingTo;
                    if (follower != null) follower.landed(at);
                    landedOn(at);
                    executor = null;
                    jumpingTo = null;
                    cancelLink();
                    if (mode == Mode.GOTO && at.equals(goal)) {
                        end("Parkour done: reached " + goal.toShortString() + " in " + jumps + " jumps, " + falls + " falls, "
                            + (System.currentTimeMillis() - startedAt) / 1000 + " s" + tally() + ".");
                        return new PathingCommand(null, PathingCommandType.DEFER);
                    }
                }
                case FELL, STOPPED -> {
                    falls++;
                    String why = executor.detail();
                    executor = null;
                    cancelLink();
                    int onThisJump = follower == null || at == null || jumpingTo == null ? 1 : follower.graph().fell(at, jumpingTo);
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
            boolean wasRunUp = runningUp;
            runningUp = false;
            runUpFor = null;
            if (result instanceof JumpSolver.Found found) {
                if (Math.abs(now.x - solvedFrom.x) + Math.abs(now.y - solvedFrom.y) + Math.abs(now.z - solvedFrom.z) > 1.0E-4) {
                    jumpingTo = null;
                    planNext();
                    return pause();
                }
                fromRest++;
                start(snapshot, now, found.inputs(), jumpingTo);
                return pause();
            }
            String reason = ((JumpSolver.Impossible) result).reason();
            // Out of time or nodes isn't proof it can't be done: look harder once before giving up on it.
            if (reason.contains("budget") && !retrying) {
                retrying = true;
                retries++;
                BlockSource world = snapshot;
                PlayerState from = solvedFrom;
                BlockPos target = jumpingTo;
                solving = CompletableFuture.supplyAsync(() -> JumpSolver.solve(world, from, target, HARD_LOOK), SOLVER);
                return pause();
            }
            // Not from a standstill: maybe with a run-up from the back of this platform (head-hitter momentum, a neo).
            if (!wasRunUp) {
                BlockPos back = runUpStart(snapshot, at, jumpingTo);
                if (back != null) {
                    runUpFor = jumpingTo;
                    runUps++;
                    logDirect("Can't make the jump to " + jumpingTo.toShortString() + " from a standstill; taking a run-up from "
                        + back.toShortString() + ".");
                    jumpingTo = null;
                    if (back.equals(at)) planNext();
                    else walkTo(back, runUpFor);
                    if (walkingTo != null) {
                        PathingCommand walk = walk();
                        if (walk != null) return walk;
                    }
                    return pause();
                }
            }
            // Ruled out from anywhere on this platform, or follow mode would just try it again from the next block.
            follower.infeasible(at, jumpingTo);
            logDirect("No way to make the jump to " + jumpingTo.toShortString() + " (" + reason + "); looking for another.");
            jumpingTo = null;
        }
        if (walkingTo != null) {
            PathingCommand walk = walk();
            if (walk != null) return walk;
        }
        if (!ctx.player().onGround()) return pause();
        planNext();
        if (walkingTo != null) {
            PathingCommand walk = walk();
            if (walk != null) return walk;
        }
        return pause();
    }

    // ---------------------------------------------------------------- walking to the take-off

    /** A second, bigger search for a jump the first one ran out of budget on. */
    private static final JumpSolver.Limits HARD_LOOK = new JumpSolver.Limits(2_000_000, 3_000_000_000L, 90, true);
    /** Whether the jump being solved is already the bigger second search. */
    private boolean retrying;
    /** How far (blocks) the take-off may be before the player walks there first rather than solving the walk too. */
    private static final double WALK_FIRST = 6.0;
    private static final long WALK_TIMEOUT_MS = 20_000;
    /** The block to walk to (with Baritone's pathing) before the next jump; null when not walking. */
    private BlockPos walkingTo;
    /** The spot the walk is for (ruled out if the walk never gets there), or null for a walk on along the platform. */
    private BlockPos walkingFor;
    private long walkStarted;

    /**
     * Walks to {@link #walkingTo} with Baritone's own pathing: a long walk across a platform is no jump, and the jump
     * solver is slow at it. Null once there (the jump is planned next) or given up.
     */
    private PathingCommand walk() {
        Vec3 feet = ctx.player().position();
        double dx = walkingTo.getX() + 0.5 - feet.x, dz = walkingTo.getZ() + 0.5 - feet.z;
        BlockPos here = standingOn();
        if (here != null && (here.equals(walkingTo) || dx * dx + dz * dz < 0.6 * 0.6)) {
            walkDone();
            // Only the walk's path: cancelEverything would stop this process too.
            ((baritone.behavior.PathingBehavior) baritone.getPathingBehavior()).softCancelIfSafe();
            return null;
        }
        if (System.currentTimeMillis() - walkStarted > WALK_TIMEOUT_MS) {
            if (walkingFor != null && at != null) follower.infeasible(at, walkingFor);
            logDirect("Couldn't walk to " + walkingTo.toShortString() + "; looking for another way on.");
            walkDone();
            // Only the walk's path: cancelEverything would stop this process too.
            ((baritone.behavior.PathingBehavior) baritone.getPathingBehavior()).softCancelIfSafe();
            return null;
        }
        return new PathingCommand(new GoalBlock(walkingTo.above()), PathingCommandType.SET_GOAL_AND_PATH);
    }

    private void walkTo(BlockPos block, BlockPos forSpot) {
        walkingTo = block;
        walkingFor = forSpot;
        walkStarted = System.currentTimeMillis();
        walks++;
        // Baritone's walking sets the aim each tick; smooth look eases the camera over a few ticks (the movement
        // still goes by the exact aim). Only while parkour walks; the player's own setting comes back after.
        if (smoothLookBefore == null) {
            smoothLookBefore = Baritone.settings().smoothLook.value;
            Baritone.settings().smoothLook.value = true;
        }
    }

    /** The player's smoothLook setting while a parkour walk has it on; null when not overridden. */
    private Boolean smoothLookBefore;

    private void walkDone() {
        walkingTo = null;
        walkingFor = null;
        if (smoothLookBefore != null) {
            Baritone.settings().smoothLook.value = smoothLookBefore;
            smoothLookBefore = null;
        }
    }

    /** How the run went: jumps chained on, jumps from a stop, second searches, walks. */
    private String tally() {
        return " (" + chained + " chained, " + fromRest + " from a stop, " + retries + " retried, " + walks + " walks" + (runUps > 0 ? ", " + runUps + " run-ups" : "") + ")";
    }

    private PathingCommand pause() {
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    // ---------------------------------------------------------------- the route ahead

    /** The jumps planned past the current one, each from the last one's touchdown; null between runs of jumps. */
    private RoutePlanner route;

    private static boolean chainOn() {
        return Baritone.settings().parkourChainJumps.value;
    }

    /** Plays {@code plan} to {@code target} from {@code from}; the first jump of a run also starts planning the route on. */
    private void start(BlockSource world, PlayerState from, List<TickInput> plan, BlockPos target) {
        snapshot = world;
        jumpingTo = target;
        executor = new ParkourExecutor(world, from, plan, target);
        if (chainOn() && route == null && follower != null) {
            route = RoutePlanner.start(ctx.world(), from, plan, target, chooser(follower.copy()));
        }
        executor.tick(ctx.player());
    }

    /** Picks the spots of the route ahead, on the planner's thread, with its own copy of the follower. */
    private RoutePlanner.Chooser chooser(CourseFollower planning) {
        if (mode == Mode.GOTO) {
            BlockPos target = goal;
            return (landing, world) -> {
                if (landing.equals(target)) return null;
                planning.world(world);
                planning.landed(landing);
                CourseGraph graph = planning.graph();
                List<BlockPos> spots = graph.spots(landing, RADIUS - 2, 6, 4);
                if (!spots.contains(target)) spots.add(target);
                List<BlockPos> path = graph.route(landing, target, spots);
                return path.size() < 2 ? null : path.get(1);
            };
        }
        return (landing, world) -> {
            planning.world(world);
            planning.landed(landing);
            return planning.next(landing);
        };
    }

    private void cancelLink() {
        if (route != null) route.cancel();
        route = null;
        ParkourExecutor.setRoute(List.of());
    }

    /**
     * At the touchdown the next planned jump was solved for, goes straight on into it; the current jump's last ticks
     * (braking to a stop) are dropped. Null when nothing is planned, or this isn't its touchdown.
     */
    private PathingCommand takeLink() {
        if (route == null) return null;
        PlayerState real = ParkourExecutor.stateOf(ctx.player());
        RoutePlanner.Leg leg = route.take(real);
        if (leg == null) return null;
        jumps++;
        maxDrift = Math.max(maxDrift, executor.maxDrift());
        executor.release(ctx.player());
        at = jumpingTo;
        if (follower != null) follower.landed(at);
        landedOn(at);
        chained++;
        start(leg.world(), real, leg.link().inputs(), leg.link().target());
        return pause();
    }

    /** Publishes the planned route for the world overlay: each leg up to where the next one takes over. */
    private void publishRoute() {
        if (route == null) return;
        List<RoutePlanner.Leg> legs = route.ahead();
        List<List<net.minecraft.world.phys.Vec3>> paths = new java.util.ArrayList<>(legs.size());
        for (int i = 0; i < legs.size(); i++) {
            List<net.minecraft.world.phys.Vec3> path = legs.get(i).path();
            int end = i + 1 < legs.size() ? Math.min(path.size(), legs.get(i + 1).link().index() + 1) : path.size();
            paths.add(path.subList(0, end));
        }
        ParkourExecutor.setRoute(paths);
    }

    @Override
    public void onPostTick(TickEvent event) {
        CourseRecorder.tick(ctx.player(), ctx.world());
        if (executor != null && ctx.player() != null) executor.afterTick(ctx.player());
        publishRoute();
    }

    /** Picks the next spot and starts solving the jump to it. */
    private void planNext() {
        BlockPos here = standingOn();
        if (here == null) return;
        if (at == null || !at.equals(here)) at = here;
        snapshot = LevelSnapshot.around(ctx.world(), at, RADIUS, 8, 8);
        // The course is taken to go the way the player faces when the run starts.
        if (follower == null) follower = new CourseFollower(snapshot, ctx.player().getYRot());
        else follower.world(snapshot);
        BlockPos next;
        if (runUpFor != null) {
            next = runUpFor;
        } else if (mode == Mode.GOTO) {
            if (at.equals(goal)) {
                end("Parkour done: already on " + goal.toShortString() + ".");
                return;
            }
            CourseGraph graph = follower.graph();
            List<BlockPos> spots = graph.spots(at, RADIUS - 2, 6, 4);
            if (!spots.contains(goal)) spots.add(goal);
            List<BlockPos> route = graph.route(at, goal, spots);
            if (route.size() < 2) {
                end("Stopped parkour: no route from " + at.toShortString() + " to " + goal.toShortString() + " that the jumps can make.");
                return;
            }
            next = route.get(1);
        } else {
            next = follower.next(at);
            if (next == null) {
                // Nothing in reach: walk on to the far end of this platform (what's next may come into reach, or a
                // course that builds itself as the player goes builds it), then give new blocks a few seconds to appear.
                BlockPos on = follower.furthestAlong(at, 2.0);
                if (on != null) {
                    idleTicks = 0;
                    walkTo(on, null);
                    return;
                }
                // On a finish (a gold block, a pressure plate...) the course is over; elsewhere, wait for more of it.
                if (!follower.graph().onCheckpoint(at) && ++idleTicks < IDLE_TICKS) return;
                end("Parkour done: no more jumps ahead after " + jumps + " jumps, " + falls + " falls" + tally() + ".");
                return;
            }
            idleTicks = 0;
            // The jump is made from the nearest block of this platform that reaches it: walk there first if it's far.
            BlockPos launch = follower.launch();
            if (launch != null && !launch.equals(at)) {
                Vec3 feet = ctx.player().position();
                double dx = launch.getX() + 0.5 - feet.x, dz = launch.getZ() + 0.5 - feet.z;
                if (dx * dx + dz * dz > WALK_FIRST * WALK_FIRST) {
                    walkTo(launch, next);
                    return;
                }
            }
        }
        retrying = false;
        jumpingTo = next;
        solvedFrom = restingState(ParkourExecutor.stateOf(ctx.player()), snapshot);
        BlockSource world = snapshot;
        PlayerState from = solvedFrom;
        BlockPos target = next;
        if (runUpFor != null) {
            runningUp = true;
            long seed = next.asLong() ^ runUps;
            solving = CompletableFuture.supplyAsync(() -> RunUpSearch.solve(world, from, target, RUN_UP_NANOS, seed), SOLVER);
            return;
        }
        // Land so the jump after this one can be made straight on, when there's a landing like that.
        BlockPos then = mode == Mode.FOLLOW && chainOn() ? follower.peekAfter(next) : null;
        solving = CompletableFuture.supplyAsync(() -> {
            if (then != null) {
                JumpSolver.Result r = JumpSolver.solve(world, from, target, JumpSolver.Limits.THOROUGH,
                    touch -> JumpSolver.solve(world, touch, then, JumpChain.LOOK_AHEAD) instanceof JumpSolver.Found);
                if (r instanceof JumpSolver.Found) return r;
            }
            return JumpSolver.solve(world, from, target, JumpSolver.Limits.THOROUGH);
        }, SOLVER);
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

    /** How long the run-up search may look for a jump that can't be made from a standstill. */
    private static final long RUN_UP_NANOS = 8_000_000_000L;
    /** The most blocks of run-up taken. */
    private static final int MAX_RUN_UP = 8;
    /** The jump a run-up is being taken for (walking back, then solving); null otherwise. */
    private BlockPos runUpFor;
    /** Whether the jump being solved is a run-up search. */
    private boolean runningUp;
    private int runUps;

    /**
     * The block to take a run-up from for the jump from {@code from} to {@code target}: straight back from the target
     * through {@code from}, as far as this platform goes (up to {@link #MAX_RUN_UP} blocks), with room to stand. Null
     * when there's less than three blocks of it.
     */
    static BlockPos runUpStart(BlockSource world, BlockPos from, BlockPos target) {
        double dx = from.getX() - target.getX(), dz = from.getZ() - target.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-6) return null;
        dx /= len;
        dz /= len;
        BlockPos best = null;
        int run = 0;
        for (int k = 1; k <= MAX_RUN_UP; k++) {
            BlockPos p = new BlockPos(from.getX() + (int) Math.round(dx * k), from.getY(), from.getZ() + (int) Math.round(dz * k));
            if (!standable(world, p)) break;
            best = p;
            run = k;
        }
        return run >= 3 ? best : null;
    }

    private static boolean standable(BlockSource world, BlockPos p) {
        net.minecraft.world.level.block.state.BlockState below = world.get(p);
        if (below.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, p).isEmpty()) return false;
        if (Math.abs(JumpSolver.topOf(world, p) - (p.getY() + 1)) > 1.0E-6) return false;
        for (int up = 1; up <= 2; up++) {
            BlockPos q = p.above(up);
            if (!world.get(q).getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, q).isEmpty()) return false;
        }
        return true;
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
        cancelLink();
        walkDone();
        runUpFor = null;
        runningUp = false;
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
