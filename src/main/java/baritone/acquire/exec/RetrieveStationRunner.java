package baritone.acquire.exec;

import baritone.acquire.model.Step;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.RayTraceUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/** Breaks and collects only a station placed by this acquire. */
final class RetrieveStationRunner extends RunnerBase {
    private static final int PICKUP_TIMEOUT = 100;
    private final Step.RetrieveStation step;
    private final Block block;
    private final Item item;
    private BlockPos target;
    private int expected;
    private int ticks;
    private boolean breaking;

    RetrieveStationRunner(ExecContext x, Step.RetrieveStation step) {
        super(x);
        this.step = step;
        this.block = StationFinder.block(step.station());
        this.item = InventoryReader.itemOf(step.station());
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        if (block == null || item == null) return Result.failed("unknown station " + step.station());
        if (target == null) {
            var owned = x.stations.findOwned(step.station(), x.stationRadius());
            if (owned.isEmpty()) {
                if (x.have(step.station()) > 0) return Result.done();
                return Result.failed("no placed " + Step.shortId(step.station()) + " to retrieve");
            }
            target = owned.get(0);
            expected = x.have(step.station()) + 1;
        }
        if (x.have(step.station()) >= expected) {
            x.stations.forgetOwned(step.station(), target);
            cancel();
            return Result.done();
        }
        if (ctx.world().getBlockState(target).is(block)) {
            if (!safeToCancel) return Result.pause();
            Result walking = approach(target, calcFailed);
            if (walking != null) return walking;
            var rotation = x.reachable(target);
            if (rotation.isEmpty()) return Result.failed("cannot reach placed " + Step.shortId(step.station()));
            x.look(rotation.get(), true);
            HitResult trace = RayTraceUtils.rayTraceTowards(ctx.player(), rotation.get(), x.reach());
            if (!(trace instanceof BlockHitResult hit) || !hit.getBlockPos().equals(target)) return Result.pause();
            if (!breaking) {
                if (!holdTool(ctx.world().getBlockState(target))) {
                    return Result.failed("no tool that picks up the " + Step.shortId(step.station()).replace('_', ' '));
                }
                ctx.playerController().clickBlock(target, hit.getDirection());
                breaking = true;
            } else {
                ctx.playerController().onPlayerDamageBlock(target, hit.getDirection());
            }
            ctx.player().swing(InteractionHand.MAIN_HAND);
            return Result.pause();
        }
        if (breaking) {
            ctx.playerController().resetBlockRemoving();
            breaking = false;
        }
        if (++ticks > PICKUP_TIMEOUT) return Result.failed("could not collect the retrieved " + Step.shortId(step.station()));
        ItemEntity drop = ctx.entitiesStream()
                .filter(entity -> entity instanceof ItemEntity e && e.isAlive() && e.getItem().is(item))
                .filter(entity -> entity.position().distanceToSqr(Vec3.atCenterOf(target)) < 9 * 9)
                .map(entity -> (ItemEntity) entity)
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(ctx.player())))
                .orElse(null);
        return drop == null ? Result.pause() : follow(new GoalBlock(drop.blockPosition()));
    }

    /** Holds a tool the station drops for: a furnace broken by hand is gone. */
    private boolean holdTool(BlockState state) {
        if (!state.requiresCorrectToolForDrops() || ctx.player().getMainHandItem().isCorrectToolForDrops(state)) return true;
        int slot = InventoryOps.toHotbar(ctx, stack -> stack.isCorrectToolForDrops(state));
        if (slot < 0) return false;
        ctx.player().getInventory().setSelectedSlot(slot);
        return true;
    }

    @Override
    public void cancel() {
        if (breaking) {
            ctx.playerController().resetBlockRemoving();
            breaking = false;
        }
    }
}
