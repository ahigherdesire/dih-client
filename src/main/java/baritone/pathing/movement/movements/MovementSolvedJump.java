package baritone.pathing.movement.movements;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.BetterBlockPos;
import baritone.parkour.course.CourseGraph;
import baritone.parkour.exec.ParkourExecutor;
import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.LevelSnapshot;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.solve.JumpSolver;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.utils.pathing.MutableMoveResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A jump Baritone's own parkour can't do (diagonal, odd-angled, round a wall, onto odd blocks), handed to the exact
 * solver. During A* it is offered on optimistic terms: the landing must be standable and within the reach table for its
 * height change. When the path reaches it, the jump is solved from where the player stands; a jump the solver rules
 * out is remembered as impossible and the path is recalculated without it. Needs {@code allowParkour} and
 * {@code allowHardParkour}.
 */
public final class MovementSolvedJump extends Movement {
    /** Offsets the planner tries, beyond what {@link MovementParkour} covers (straight jumps get these only round walls). */
    public static final int[][] OFFSETS = buildOffsets();
    /** Extra cost so A* still prefers ordinary moves when they work. */
    private static final double PENALTY = 12.0;
    /** Jumps the solver ruled out, as src/dest long pairs; never offered again this session. */
    private static final Set<Long> INFEASIBLE = ConcurrentHashMap.newKeySet();

    private CompletableFuture<JumpSolver.Result> solving;
    private PlayerState solvedFrom;
    private BlockSource snapshot;
    private ParkourExecutor executor;

    private MovementSolvedJump(IBaritone baritone, BetterBlockPos src, BetterBlockPos dest) {
        super(baritone, src, dest, new BetterBlockPos[0]);
    }

    private static int[][] buildOffsets() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int ax = Math.abs(dx), az = Math.abs(dz);
                if (ax <= 1 && az <= 1) continue;
                if (dx * dx + dz * dz > 26) continue;
                out.add(new int[]{dx, dz});
            }
        }
        return out.toArray(new int[0][]);
    }

    public static MovementSolvedJump of(CalculationContext context, BetterBlockPos src, int dx, int dz) {
        MutableMoveResult res = new MutableMoveResult();
        cost(context, src.x, src.y, src.z, dx, dz, res);
        return new MovementSolvedJump(context.getBaritone(), src, new BetterBlockPos(res.x, res.y, res.z));
    }

    /** The optimistic cost of jumping by (dx, dz) from feet (x, y, z): the landing height is found, the reach checked. */
    public static void cost(CalculationContext context, int x, int y, int z, int dx, int dz, MutableMoveResult res) {
        res.x = x + dx;
        res.y = y;
        res.z = z + dz;
        res.cost = COST_INF;
        if (!context.allowParkour || !Baritone.settings().allowHardParkour.value) return;
        if (!MovementHelper.fullyPassable(context, x, y + 2, z)) return;
        BlockState standingOn = context.get(x, y - 1, z);
        if (!MovementHelper.canWalkOn(context, x, y - 1, z, standingOn)) return;
        if (!context.get(x, y, z).getFluidState().isEmpty()) return;
        boolean straight = dx == 0 || dz == 0;
        for (int dy = 1; dy >= -4; dy--) {
            int tx = x + dx, ty = y + dy, tz = z + dz;
            if (!MovementHelper.fullyPassable(context, tx, ty, tz) || !MovementHelper.fullyPassable(context, tx, ty + 1, tz)) {
                if (dy <= 0) return; // a wall where the feet would land: nothing lower to land on in this column
                continue;
            }
            BlockState landing = context.get(tx, ty - 1, tz);
            if (!MovementHelper.canWalkOn(context, tx, ty - 1, tz, landing)) continue;
            if (MovementHelper.avoidWalkingInto(landing)) return;
            BlockPos from = new BlockPos(x, y - 1, z), to = new BlockPos(tx, ty - 1, tz);
            if (!CourseGraph.inReach(from, to)) return;
            if (INFEASIBLE.contains(key(from, to))) return;
            // Straight jumps are MovementParkour's unless a wall is in the way (a neo).
            if (straight && !blockedBetween(context, x, y, z, dx, dz)) return;
            double distance = Math.sqrt(dx * dx + dz * dz);
            res.y = ty;
            res.cost = (6.0 + 4.0 * distance + (dy > 0 ? 4.0 : 0.0)) + PENALTY;
            return;
        }
    }

    /** Whether something solid stands at body height on the straight line from the start to the landing. */
    private static boolean blockedBetween(CalculationContext context, int x, int y, int z, int dx, int dz) {
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        for (int i = 1; i < steps; i++) {
            int bx = x + Integer.signum(dx) * i, bz = z + Integer.signum(dz) * i;
            if (!MovementHelper.fullyPassable(context, bx, y, bz) || !MovementHelper.fullyPassable(context, bx, y + 1, bz)) return true;
        }
        return false;
    }

    private static long key(BlockPos from, BlockPos to) {
        return from.asLong() * 31 + to.asLong();
    }

    @Override
    public double calculateCost(CalculationContext context) {
        MutableMoveResult res = new MutableMoveResult();
        cost(context, src.x, src.y, src.z, dest.x - src.x, dest.z - src.z, res);
        if (res.x != dest.x || res.y != dest.y || res.z != dest.z) return COST_INF;
        return res.cost;
    }

    @Override
    protected Set<BetterBlockPos> calculateValidPositions() {
        Set<BetterBlockPos> set = new HashSet<>();
        int x0 = Math.min(src.x, dest.x) - 2, x1 = Math.max(src.x, dest.x) + 2;
        int z0 = Math.min(src.z, dest.z) - 2, z1 = Math.max(src.z, dest.z) + 2;
        int y0 = Math.min(src.y, dest.y) - 1, y1 = Math.max(src.y, dest.y) + 2;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) set.add(new BetterBlockPos(x, y, z));
            }
        }
        return set;
    }

    @Override
    public boolean safeToCancel(MovementState state) {
        return executor == null;
    }

    /** Whether a solved jump is being played right now: PathExecutor leaves sprinting to it. */
    public boolean playing() {
        return executor != null;
    }

    @Override
    public MovementState updateState(MovementState state) {
        super.updateState(state);
        if (state.getStatus() != MovementStatus.RUNNING) return state;
        BlockPos target = dest.below();
        if (executor == null) {
            if (solving == null) {
                // Solve from rest, as #parkour does: a plan from a moving start is stale by the time it's ready.
                PlayerState here = ParkourExecutor.stateOf(ctx.player());
                if (!here.onGround || here.vx != 0.0 || here.vz != 0.0) return state;
                solvedFrom = here;
                snapshot = LevelSnapshot.around(ctx.world(), target, 10, 8, 8);
                BlockSource world = snapshot;
                PlayerState from = solvedFrom;
                solving = CompletableFuture.supplyAsync(() -> JumpSolver.solve(world, from, target, JumpSolver.Limits.THOROUGH));
                return state;
            }
            if (!solving.isDone()) return state;
            JumpSolver.Result result = solving.getNow(null);
            if (!(result instanceof JumpSolver.Found found)) {
                System.out.println("[DIH parkour] no solved jump from " + solvedFrom + " to " + target.toShortString() + ": " + result);
                INFEASIBLE.add(key(src.below(), target));
                logDebug("Hard parkour jump to " + target.toShortString() + " ruled out; recalculating");
                return state.setStatus(MovementStatus.UNREACHABLE);
            }
            PlayerState now = ParkourExecutor.stateOf(ctx.player());
            if (Math.abs(now.x - solvedFrom.x) + Math.abs(now.z - solvedFrom.z) > 1.0E-4) {
                solving = null; // moved while solving: solve again from here
                return state;
            }
            executor = new ParkourExecutor(snapshot, now, found.inputs(), target);
        }
        ParkourExecutor.Status status = executor.tick(ctx.player());
        switch (status) {
            case LANDED -> {
                executor = null;
                return state.setStatus(MovementStatus.SUCCESS);
            }
            case FELL, STOPPED -> {
                System.out.println("[DIH parkour] solved jump to " + target.toShortString() + " " + status + " (" + executor.detail() + ") at "
                    + ParkourExecutor.stateOf(ctx.player()) + (executor.firstDrift().isEmpty() ? "" : "; drift: " + executor.firstDrift()));
                executor.release(ctx.player());
                executor = null;
                return state.setStatus(MovementStatus.FAILED);
            }
            default -> {
                return state;
            }
        }
    }

    @Override
    public void reset() {
        super.reset();
        if (executor != null) executor.release(ctx.player());
        executor = null;
        solving = null;
    }
}
