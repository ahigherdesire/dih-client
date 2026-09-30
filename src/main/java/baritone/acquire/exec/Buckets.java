package baritone.acquire.exec;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Bucket work for the runners that cast obsidian: filling an empty bucket from the nearest open source, pouring into
 * a cell through a face the player can see, and scooping a source back up. The server aims a bucket from the rotation
 * in the use packet, so the rotation goes on the player in the same tick; its answer comes a tick or two later, so
 * after a use nothing else is tried for {@link #USE_WAIT_TICKS}.
 */
final class Buckets {

    static final String BUCKET = "minecraft:bucket";
    static final String WATER_BUCKET = "minecraft:water_bucket";
    static final String LAVA_BUCKET = "minecraft:lava_bucket";

    private static final int RESCAN_TICKS = 40;
    /** How many chunks out, and how many blocks up and down, sources are searched for. */
    private static final int SCAN_CHUNKS = 3;
    private static final int SCAN_HEIGHT = 24;
    private static final int TRIES = 20;
    /** Lava lakes are common below this; where to look when none is in view. */
    private static final int LAVA_LEVEL = -50;
    /** How far one pool reaches from a source of it: a pool given up on is given up on whole. */
    static final int POOL_RADIUS = 6;
    /** A bucket's result comes back from the server a tick or two later; until then the bucket isn't used again. */
    private static final int USE_WAIT_TICKS = 10;
    /**
     * Failed paths to a source waited out, a second each, before it's given up: water just poured or taken back
     * still flows round the player for a few seconds, and Baritone won't path through flowing water.
     */
    private static final int PATH_TRIES = 3;
    private static final int SETTLE_TICKS = 20;
    /** Where on a face (or in a cell) a look ray may aim, from the middle outward. */
    private static final double[] SPOTS = {0.5, 0.25, 0.75, 0.08, 0.92};

    private final ExecContext x;
    private final IPlayerContext ctx;
    private final Function<Goal, StepRunner.Result> walk;
    private final Set<BlockPos> badFluid = new HashSet<>();
    private BlockPos fluid;
    private int sinceScan = RESCAN_TICKS;
    private GoalXZ explore;
    private int tries;
    private int pending;
    private int pathFails;
    private int settling;
    /** Pools given up on for want of a path since {@link #reset}. */
    private int lostPools;

    /** {@code walk} paths to a fixed goal, as {@link RunnerBase#walk} does. */
    Buckets(ExecContext x, Function<Goal, StepRunner.Result> walk) {
        this.x = x;
        this.ctx = x.ctx;
        this.walk = walk;
    }

    /** Forgets the source being walked to and the tries at the current job; call it when the job changes. */
    void reset() {
        fluid = null;
        sinceScan = RESCAN_TICKS;
        tries = 0;
        pending = 0;
        pathFails = 0;
        settling = 0;
        lostPools = 0;
    }

    /** Pools given up on for want of a path since {@link #reset}. */
    int lostPools() {
        return lostPools;
    }

    /** True for a while after a bucket use, counting down: the server hasn't answered yet. */
    boolean waiting() {
        if (pending > 0) {
            pending--;
            return true;
        }
        return false;
    }

    /** The source last walked to or filled from. */
    BlockPos lastSource() {
        return fluid;
    }

    /** Goes to the nearest open source of {@code block} and fills an empty bucket from it. */
    StepRunner.Result fetch(Block block, TagKey<Fluid> tag, boolean calcFailed) {
        return fetch(block, tag, calcFailed, null);
    }

    /**
     * Goes to the open source of {@code block} nearest {@code by} (the player when null, or when none is in view of
     * it) and fills an empty bucket from it. A pool it finds no path to is given up on whole.
     */
    StepRunner.Result fetch(Block block, TagKey<Fluid> tag, boolean calcFailed, BlockPos by) {
        if (waiting()) return StepRunner.Result.pause();
        if (x.have(BUCKET) == 0) {
            return StepRunner.Result.fatal("need an empty bucket for the " + (tag == FluidTags.LAVA ? "lava" : "water"));
        }
        if (settling > 0) {
            settling--;
            return StepRunner.Result.pause();
        }
        if (calcFailed && fluid != null && ++pathFails <= PATH_TRIES) {
            settling = SETTLE_TICKS;
            return StepRunner.Result.pause();
        }
        if (calcFailed && fluid != null) {
            // The rest of the pool is as far out of reach: across a lake, one source at a time takes minutes.
            avoidPool(fluid, POOL_RADIUS, tag);
            lostPools++;
            fluid = null;
            sinceScan = RESCAN_TICKS;
        }
        if (++sinceScan >= RESCAN_TICKS || fluid != null && !isSource(fluid, tag)) {
            sinceScan = 0;
            BlockPos was = fluid;
            fluid = nearestSource(block, tag, by, Set.of());
            if (fluid == null && by != null) fluid = nearestSource(block, tag, null, Set.of());
            if (fluid != null && !fluid.equals(was)) {
                pathFails = 0;
                Helper.HELPER.logDebug("bucket: " + (tag == FluidTags.LAVA ? "lava" : "water") + " at " + fluid.toShortString()
                        + " from " + ctx.playerFeet().toShortString());
            }
        }
        if (fluid == null) return explore(tag == FluidTags.LAVA);
        explore = null;
        // Any source of the pool will do, whichever the ray meets first.
        StepRunner.Result scooped = scoop(fluid, tag, false);
        if (scooped != null) return scooped;
        GoalNear near = new GoalNear(fluid, 2);
        if (near.isInGoal(ctx.playerFeet()) && ++tries > TRIES * 2) {
            // Arrived but never got a clear line to it.
            badFluid.add(fluid);
            fluid = null;
            tries = 0;
            return StepRunner.Result.pause();
        }
        return walk.apply(near);
    }

    /**
     * Scoops the source at {@code pos} with an empty bucket if some part of it is in reach and in sight; null when
     * none is (walk closer). Aims at its middle first, then around it. Unless {@code exact}, another source the ray
     * meets first will do.
     */
    StepRunner.Result scoop(BlockPos pos, TagKey<Fluid> tag, boolean exact) {
        if (waiting()) return StepRunner.Result.pause();
        Rotation rot = null;
        for (double a : SPOTS) {
            for (double b : SPOTS) {
                for (double c : SPOTS) {
                    if (rot != null) break;
                    Vec3 point = new Vec3(pos.getX() + a, pos.getY() + b * 0.85, pos.getZ() + c);
                    if (ctx.playerHead().distanceTo(point) > x.reach() - 0.3) continue;
                    Rotation r = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), point, ctx.playerRotations());
                    if (clip(r, ClipContext.Fluid.SOURCE_ONLY) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                            && (!exact || hit.getBlockPos().equals(pos)) && isSource(hit.getBlockPos(), tag)) {
                        rot = r;
                    }
                }
            }
        }
        if (rot == null) return null;
        if (!hold(stack -> stack.is(Items.BUCKET))) return StepRunner.Result.fatal("need an empty bucket");
        use(rot);
        return StepRunner.Result.pause();
    }

    /**
     * Pours {@code bucket} into the cell on the {@code face} side of {@code via}, aiming just inside that face so it
     * is the first one the ray meets, at the middle of the face first, then around it. Paused while nothing on the
     * face is in sight; a failure once that has gone on for a while.
     */
    StepRunner.Result pour(BlockPos via, Direction face, Item bucket, String what) {
        if (waiting()) return StepRunner.Result.pause();
        if (++tries > TRIES) return StepRunner.Result.failed("couldn't pour the " + what + " at " + via.relative(face).toShortString());
        Rotation rot = visibleFace(via, face);
        if (rot == null) return StepRunner.Result.pause();
        x.look(rot, true);
        if (!hold(stack -> stack.is(bucket))) return StepRunner.Result.failed("no " + what + " bucket to pour");
        use(rot);
        return StepRunner.Result.pause();
    }

    /** A rotation whose look ray first meets {@code face} of {@code via}, within reach, or null. */
    Rotation visibleFace(BlockPos via, Direction face) {
        Direction.Axis axis = face.getAxis();
        for (double a : SPOTS) {
            for (double b : SPOTS) {
                double[] p = new double[3];
                int n = 0;
                for (Direction.Axis other : Direction.Axis.values()) {
                    if (other == axis) p[other.ordinal()] = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0.9 : 0.1;
                    else p[other.ordinal()] = n++ == 0 ? a : b;
                }
                Vec3 point = new Vec3(via.getX() + p[0], via.getY() + p[1], via.getZ() + p[2]);
                if (ctx.playerHead().distanceTo(point) > x.reach() - 0.3) continue;
                Rotation rot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), point, ctx.playerRotations());
                if (clip(rot, ClipContext.Fluid.NONE) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(via) && hit.getDirection() == face) {
                    return rot;
                }
            }
        }
        return null;
    }

    /** No source in view: heads down to lava level, then along the way the player faces, rescanning as it goes. */
    StepRunner.Result explore(boolean lava) {
        if (lava && ctx.playerFeet().getY() > LAVA_LEVEL + 2) return walk.apply(new GoalYLevel(LAVA_LEVEL));
        if (explore == null || explore.isInGoal(ctx.playerFeet())) {
            explore = GoalXZ.fromDirection(ctx.player().position(), ctx.player().getYRot(), 48);
        }
        return walk.apply(explore);
    }

    /**
     * The source to fill from: one with open air above (the top of a pool, not deep inside an aquifer), not in
     * {@code skip}, nearest {@code from} (the player when null) with height counted sixteen times over, since reaching
     * one above or below means digging.
     */
    BlockPos nearestSource(Block block, TagKey<Fluid> tag, BlockPos from, Set<BlockPos> skip) {
        return NearestBlock.find(ctx.world(), from == null ? ctx.playerFeet() : from, block, SCAN_CHUNKS, SCAN_HEIGHT, 16,
                p -> !skip.contains(p) && usable(p, tag));
    }

    /** Never fills from {@code pos} again (a source the runner itself poured, say). */
    void avoid(BlockPos pos) {
        badFluid.add(pos.immutable());
    }

    /** Never fills from any source of {@code tag} within {@code radius} of {@code pos} again: that whole pool. */
    void avoidPool(BlockPos pos, int radius, TagKey<Fluid> tag) {
        badFluid.addAll(pool(pos, radius, tag));
    }

    /** The sources of {@code tag} within {@code radius} of {@code pos}. */
    List<BlockPos> pool(BlockPos pos, int radius, TagKey<Fluid> tag) {
        List<BlockPos> pool = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-radius, -radius, -radius), pos.offset(radius, radius, radius))) {
            if (isSource(p, tag)) pool.add(p.immutable());
        }
        return pool;
    }

    private boolean usable(BlockPos pos, TagKey<Fluid> tag) {
        return !badFluid.contains(pos) && isSource(pos, tag) && ctx.world().getBlockState(pos.above()).isAir();
    }

    boolean isSource(BlockPos pos, TagKey<Fluid> tag) {
        FluidState state = ctx.world().getFluidState(pos);
        return state.isSource() && state.is(tag);
    }

    HitResult clip(Rotation rot, ClipContext.Fluid fluids) {
        Vec3 eye = ctx.playerHead();
        Vec3 end = eye.add(RotationUtils.calcLookDirectionFromRotation(rot).scale(x.reach()));
        return ctx.world().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, fluids, ctx.player()));
    }

    /** Uses the held bucket along {@code rot}: the rotation goes on the player, and so into the use packet. */
    private void use(Rotation rot) {
        ctx.player().setYRot(rot.getYaw());
        ctx.player().setXRot(rot.getPitch());
        ctx.playerController().processRightClick(ctx.player(), ctx.world(), InteractionHand.MAIN_HAND);
        pending = USE_WAIT_TICKS;
    }

    boolean hold(Predicate<ItemStack> want) {
        int slot = InventoryOps.toHotbar(ctx, want);
        if (slot < 0) return false;
        ctx.player().getInventory().setSelectedSlot(slot);
        return true;
    }
}
