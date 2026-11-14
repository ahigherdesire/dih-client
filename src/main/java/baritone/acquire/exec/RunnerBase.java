package baritone.acquire.exec;

import dihclient.util.DihEntities;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Walking to a block and right-clicking it, shared by the station, craft and smelt runners. */
abstract class RunnerBase implements StepRunner {

    /** Ticks spent in reach of a block without a clear line to it before giving up on it. */
    private static final int BLOCKED_TICKS = 40;

    protected final ExecContext x;
    protected final IPlayerContext ctx;
    private Goal lastGoal;
    private int arrivedBlocked;

    RunnerBase(ExecContext x) {
        this.x = x;
        this.ctx = x.ctx;
    }

    /** Path to a fixed goal. The first tick for a new goal makes Baritone drop a path to an older one. */
    protected Result walk(Goal goal) {
        PathingCommandType type = goal.equals(lastGoal) ? PathingCommandType.SET_GOAL_AND_PATH : PathingCommandType.FORCE_REVALIDATE_GOAL_AND_PATH;
        lastGoal = goal;
        return Result.running(new PathingCommand(goal, type));
    }

    /** Path toward a goal that moves every tick (a mob, a dropped item). */
    protected Result follow(Goal goal) {
        lastGoal = null;
        return Result.running(new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH));
    }

    /** Whether {@code pos} is in reach, visible, and within the crafting helper's reach (measured from the feet). */
    protected boolean inReach(BlockPos pos) {
        double reach = Math.max(4.5D, x.reach()) - 0.5D;
        return ctx.player().position().distanceToSqr(Vec3.atCenterOf(pos)) <= reach * reach && x.reachable(pos).isPresent();
    }

    /**
     * Walks until {@code pos} can be clicked. Returns null once it can; a failure when Baritone finds no
     * path or the block stays out of sight after arriving (the caller marks it unusable and tries another).
     */
    protected Result approach(BlockPos pos, boolean calcFailed) {
        if (inReach(pos)) {
            arrivedBlocked = 0;
            return null;
        }
        if (calcFailed) return Result.failed("no path to the " + blockName(pos) + " at " + pos.toShortString());
        GoalGetToBlock goal = new GoalGetToBlock(pos);
        if (goal.isInGoal(ctx.playerFeet()) && ++arrivedBlocked > BLOCKED_TICKS) {
            arrivedBlocked = 0;
            return Result.failed("can't get a clear line to the " + blockName(pos) + " at " + pos.toShortString());
        }
        return walk(goal);
    }

    /** Looks at {@code pos} and, if the look ray hits it, returns that hit to click with. */
    protected Optional<BlockHitResult> aim(BlockPos pos) {
        Optional<Rotation> rot = x.reachable(pos);
        if (rot.isEmpty()) return Optional.empty();
        x.look(rot.get(), true);
        HitResult hit = RayTraceUtils.rayTraceTowards(ctx.player(), rot.get(), x.reach());
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && block.getBlockPos().equals(pos)) {
            return Optional.of(block);
        }
        return Optional.empty();
    }

    /** Right-clicks with the main hand, swinging if the server should see an arm move. */
    protected boolean use(BlockHitResult hit, InteractionHand hand) {
        InteractionResult result = ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), hand, hit);
        if (result.consumesAction()) DihEntities.swing(ctx.player(), hand);
        return result.consumesAction();
    }

    protected String blockName(BlockPos pos) {
        return ctx.world().getBlockState(pos).getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT);
    }
}
