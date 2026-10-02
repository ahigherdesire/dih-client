package baritone.acquire.exec;

import baritone.acquire.model.Step;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link Step.Mine} for obsidian nobody has seen: casts it one block at a time. Fills a bucket with water, scoops lava
 * with the other, finds a {@link CastSite} away from the lava, pours the lava and then the water beside it (the lava
 * source turns to obsidian), takes the water back, breaks the obsidian and picks it up. Buckets are used with the
 * rotation set on the player in the same tick, because the server aims the bucket from the rotation in the use packet.
 */
final class ObsidianRunner extends RunnerBase {

    private enum State { WATER, LAVA, SITE, POUR_LAVA, POUR_WATER, TAKE_WATER, BREAK, COLLECT }

    static final String OBSIDIAN = "minecraft:obsidian";
    static final String BUCKET = Buckets.BUCKET;
    static final String WATER_BUCKET = Buckets.WATER_BUCKET;
    static final String LAVA_BUCKET = Buckets.LAVA_BUCKET;

    private static final int SITE_RADIUS = 8;
    private static final int TRIES = 20;
    private static final int BREAK_TICKS = 400;
    private static final int COLLECT_TICKS = 100;

    private final Step.Mine step;
    private final Buckets buckets;
    private State state;
    private final Set<BlockPos> badSite = new HashSet<>();
    private CastSite site;
    private int tries;
    private int before;
    private boolean breaking;

    ObsidianRunner(ExecContext x, Step.Mine step) {
        super(x);
        this.step = step;
        this.buckets = new Buckets(x, this::walk);
    }

    /** Casting needs two buckets between the empty, water and lava ones; with fewer, obsidian is mined where it lies. */
    static boolean supports(Step.Mine mine, int buckets, int water, int lava) {
        return mine.item().equals(OBSIDIAN) && buckets + water + lava >= 2;
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        if (x.have(OBSIDIAN) >= step.untilCount()) {
            cancel();
            return Result.done();
        }
        if (TravelRunner.dimensionId(ctx.world()).equals("the_nether")) {
            return Result.fatal("water boils in the Nether, so obsidian is cast in the Overworld");
        }
        if (!x.hasTool(step.tool())) {
            cancel();
            return Result.failed("need a " + ExecContext.describeTool(step.tool()) + " to break the cast obsidian");
        }
        Result full = x.checkRoom(OBSIDIAN);
        if (full != null) {
            cancel();
            return full;
        }
        if (state == null) next();
        Level level = ctx.world();
        return switch (state) {
            case WATER -> fetch(Blocks.WATER, FluidTags.WATER, WATER_BUCKET, calcFailed);
            case LAVA -> fetch(Blocks.LAVA, FluidTags.LAVA, LAVA_BUCKET, calcFailed);
            case SITE -> toSite(calcFailed);
            case POUR_LAVA -> {
                if (level.getFluidState(site.cast()).is(FluidTags.LAVA)) {
                    to(State.POUR_WATER);
                    yield Result.pause();
                }
                yield pour(site.cast(), Items.LAVA_BUCKET, "lava");
            }
            case POUR_WATER -> {
                if (level.getBlockState(site.cast()).is(Blocks.OBSIDIAN)) {
                    to(State.TAKE_WATER);
                    yield Result.pause();
                }
                if (level.getFluidState(site.water()).is(FluidTags.WATER)) {
                    // Water is down but the lava didn't set: something about the site is off.
                    if (++tries > 10) yield Result.failed("the lava at " + site.cast().toShortString() + " didn't turn to obsidian");
                    yield Result.pause();
                }
                yield pour(site.water(), Items.WATER_BUCKET, "water");
            }
            case TAKE_WATER -> {
                FluidState water = level.getFluidState(site.water());
                if (x.have(WATER_BUCKET) > 0 || !water.isSource()) {
                    to(State.BREAK);
                    yield Result.pause();
                }
                Result scooped = buckets.scoop(site.water(), FluidTags.WATER, true);
                if (scooped != null) yield scooped;
                // Out of reach or out of sight: leave the water; it drains once the source goes.
                if (++tries > TRIES) to(State.BREAK);
                yield Result.pause();
            }
            case BREAK -> breakCast();
            case COLLECT -> {
                if (x.have(OBSIDIAN) > before || ++tries > COLLECT_TICKS) {
                    next();
                    yield Result.pause();
                }
                if (calcFailed) {
                    next();
                    yield Result.pause();
                }
                yield walk(new GoalBlock(site.cast()));
            }
        };
    }

    /** Goes to the nearest source of {@code block} in view and fills an empty bucket from it. */
    private Result fetch(Block block, TagKey<Fluid> tag, String filled, boolean calcFailed) {
        if (x.have(filled) > before) {
            next();
            return Result.pause();
        }
        return buckets.fetch(block, tag, calcFailed);
    }

    /** Pours {@code bucket} into {@code cell} through the top of the floor under it. */
    private Result pour(BlockPos cell, Item bucket, String what) {
        // Once the lava is down the water has to follow it wherever the player stands.
        if (bucket == Items.LAVA_BUCKET && !ctx.playerFeet().equals(site.stand())) {
            to(State.SITE);
            return Result.pause();
        }
        return buckets.pour(cell.below(), Direction.UP, bucket, what);
    }

    private Result toSite(boolean calcFailed) {
        if (site == null || !site.ok(terrain())) {
            site = CastSite.find(ctx.playerFeet(), SITE_RADIUS, terrain());
            if (site == null) {
                if (++tries > TRIES) return Result.failed("no flat open row of 3 blocks away from lava to cast obsidian on");
                return buckets.explore(false, calcFailed);
            }
        }
        if (calcFailed) {
            badSite.add(site.stand());
            site = null;
            return Result.pause();
        }
        if (ctx.playerFeet().equals(site.stand()) && ctx.player().onGround()) {
            to(State.POUR_LAVA);
            return Result.pause();
        }
        return walk(new GoalBlock(site.stand()));
    }

    private Result breakCast() {
        BlockPos cast = site.cast();
        if (!ctx.world().getBlockState(cast).is(Blocks.OBSIDIAN)) {
            stopBreaking();
            to(State.COLLECT);
            return Result.pause();
        }
        if (++tries > BREAK_TICKS) {
            stopBreaking();
            return Result.failed("couldn't break the cast obsidian at " + cast.toShortString());
        }
        int slot = InventoryOps.toHotbar(ctx, stack -> x.toolMatcher(step.tool()).test(stack)
                && (!stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue()
                > Math.ceil(stack.getMaxDamage() * 0.10)));
        if (slot < 0) {
            stopBreaking();
            return Result.failed("the " + ExecContext.describeTool(step.tool()) + " is worn below 10%");
        }
        ctx.player().getInventory().setSelectedSlot(slot);
        x.baritone.getInputOverrideHandler().clearAllKeys();
        var rot = x.reachable(cast);
        if (rot.isEmpty()) {
            stopBreaking();
            return walk(new GoalNear(cast, 2));
        }
        x.look(rot.get(), true);
        if (ctx.isLookingAt(cast)) {
            x.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
            breaking = true;
        }
        return Result.pause();
    }

    private CastSite.Terrain terrain() {
        Level level = ctx.world();
        return new CastSite.Terrain() {
            @Override
            public boolean floor(BlockPos pos) {
                BlockState state = level.getBlockState(pos);
                return state.isCollisionShapeFullBlock(level, pos) && state.getFluidState().isEmpty()
                        && !(state.getBlock() instanceof FallingBlock) && !state.is(Blocks.MAGMA_BLOCK);
            }

            @Override
            public boolean open(BlockPos pos) {
                return !badSite.contains(pos) && level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty();
            }

            @Override
            public boolean lava(BlockPos pos) {
                return level.getFluidState(pos).is(FluidTags.LAVA);
            }
        };
    }

    /** Picks the next state from what the buckets hold. */
    private void next() {
        if (x.have(WATER_BUCKET) == 0) {
            to(State.WATER);
            before = x.have(WATER_BUCKET);
        } else if (x.have(LAVA_BUCKET) == 0) {
            to(State.LAVA);
            before = x.have(LAVA_BUCKET);
        } else {
            to(State.SITE);
        }
    }

    private void to(State next) {
        if (next == State.COLLECT) before = x.have(OBSIDIAN);
        state = next;
        tries = 0;
        buckets.reset();
    }

    private void stopBreaking() {
        if (breaking) {
            x.baritone.getInputOverrideHandler().clearAllKeys();
            breaking = false;
        }
    }

    @Override
    public void cancel() {
        stopBreaking();
    }
}
