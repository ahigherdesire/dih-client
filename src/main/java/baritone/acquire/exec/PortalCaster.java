package baritone.acquire.exec;

import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.Helper;
import baritone.api.process.IBuilderProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Casts a {@link PortalCast} frame next to a lava pool and lights it, for a first trip to the Nether with no obsidian
 * (the speedrunner's bucket portal: iron for the buckets, no diamonds). Fills one bucket with water, picks a site
 * beside the nearest lava pool with room for the frame, digs it out and puts up the mould wall if it has to, then
 * casts the ten cells a lava bucket each, filling every other bucket at the pool each trip: water beside the cell,
 * lava into it, water back into the bucket. Then it waits for the opening to drain and strikes the flint and steel.
 * {@link #portal()} is the lit portal once it is done. A re-plan's caster picks up the frame where this one left it.
 */
final class PortalCaster extends RunnerBase {

    private enum State { WATER, LAVA, SITE, DIG, MOULD, POUR_WATER, POUR_LAVA, SCOOP, DRAIN, LIGHT }

    /** How far from the lava pool the site may be. */
    private static final int SITE_RADIUS = 12;
    private static final int MAX_DIGS = 30;
    private static final int TRIES = 20;
    /** Lava pools tried for a site before giving up. */
    private static final int POOLS = 8;
    /** Pools by a site found out of reach before the site, if nothing is cast on it yet, is given up. */
    private static final int LOST_POOLS = 2;
    private static final int DRAIN_TICKS = 200;
    /** Failed paths to a stand waited out (each for a second) before the site is given up. */
    private static final int STAND_TRIES = 8;
    private static final int SETTLE_TICKS = 20;
    /** Blocks the wall may be made of, most common first. */
    static final Set<String> MOULD = Set.of("minecraft:cobblestone", "minecraft:cobbled_deepslate",
            "minecraft:netherrack", "minecraft:dirt", "minecraft:stone", "minecraft:andesite", "minecraft:diorite",
            "minecraft:granite", "minecraft:tuff", "minecraft:deepslate", "minecraft:blackstone", "minecraft:basalt");

    private final Buckets buckets;
    private final Set<BlockPos> leftAlone = new HashSet<>();
    private State state;
    private PortalCast cast;
    private PortalCast.Cell cell;
    /** What {@link State#SCOOP} takes back, and which fluid it is. */
    private BlockPos scoopAt;
    private TagKey<Fluid> scoopTag;
    private boolean digging;
    /** The block last given to the builder. */
    private BlockPos dug;
    private int tries;
    private int standFails;
    private int settling;
    /** Lava pools turned down for want of room. */
    private int pools;
    private BlockPos portal;

    PortalCaster(ExecContext x) {
        super(x);
        this.buckets = new Buckets(x, this::walk);
        if (x.castSite != null && !x.badCastSites.contains(x.castSite.origin())) cast = x.castSite;
    }

    /** Whether the inventory has what casting takes: two buckets of any kind, the mould blocks and a flint and steel. */
    static boolean supports(int buckets, int mould, int flintAndSteel) {
        return buckets >= 2 && mould >= PortalCast.WALL_BLOCKS && flintAndSteel >= 1;
    }

    /** The lit portal, once {@link #tick} is done. */
    BlockPos portal() {
        return portal;
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        if (TravelRunner.dimensionId(ctx.world()).equals("the_nether")) {
            return Result.fatal("water boils in the Nether, so a portal is cast in the Overworld");
        }
        if (state == null) next();
        if (buckets.waiting()) return Result.pause();
        Level level = ctx.world();
        return switch (state) {
            case WATER -> {
                if (x.have(Buckets.WATER_BUCKET) > 0) yield again();
                yield buckets.fetch(Blocks.WATER, FluidTags.WATER, calcFailed);
            }
            case LAVA -> {
                int lava = x.have(Buckets.LAVA_BUCKET);
                // Every empty bucket but none past the cells left, unless the pool runs out of reach with some filled.
                if (lava > 0 && (x.have(Buckets.BUCKET) == 0 || lava >= cellsLeft() || buckets.lostPools() > 0)) yield again();
                if (buckets.lostPools() >= LOST_POOLS && cast.frame().stream().noneMatch(p -> level.getBlockState(p).is(Blocks.OBSIDIAN))) {
                    // The lava by the site is out of reach from here, and so, most likely, is the site.
                    logDebug("portal cast: no way to the lava by the site " + cast.origin().toShortString());
                    x.badCastSites.add(cast.origin());
                    cast = null;
                    yield again();
                }
                // From the pool by the site, which each bucket goes back to, not whichever is nearest on the way there.
                yield buckets.fetch(Blocks.LAVA, FluidTags.LAVA, calcFailed, cast.stand(false));
            }
            case SITE -> site();
            case DIG -> dig();
            case MOULD -> mould(calcFailed);
            case POUR_WATER -> {
                if (buckets.isSource(cell.water(), FluidTags.WATER)) {
                    to(State.POUR_LAVA);
                    yield Result.pause();
                }
                if (!solid(cell.waterVia())) yield mouldAgain();
                Result away = toStand(cell.stand(), calcFailed);
                if (away != null) yield away;
                yield buckets.pour(cell.waterVia(), cell.waterFace(), Items.WATER_BUCKET, "water");
            }
            case POUR_LAVA -> {
                if (level.getBlockState(cell.lava()).is(Blocks.OBSIDIAN)) {
                    scoop(cell.water(), FluidTags.WATER);
                    yield Result.pause();
                }
                if (buckets.isSource(cell.lava(), FluidTags.LAVA)) {
                    // It didn't set: take it back before it flows.
                    scoop(cell.lava(), FluidTags.LAVA);
                    yield Result.pause();
                }
                if (!buckets.isSource(cell.water(), FluidTags.WATER)) {
                    to(State.POUR_WATER);
                    yield Result.pause();
                }
                if (!solid(cell.lavaVia())) yield mouldAgain();
                Result away = toStand(cell.stand(), calcFailed);
                if (away != null) yield away;
                yield buckets.pour(cell.lavaVia(), cell.lavaFace(), Items.LAVA_BUCKET, "lava");
            }
            case SCOOP -> {
                if (!buckets.isSource(scoopAt, scoopTag)) yield again();
                if (x.have(Buckets.BUCKET) == 0) {
                    if (scoopTag == FluidTags.LAVA) yield Result.failed("no empty bucket to take back the lava at " + scoopAt.toShortString());
                    leftAlone.add(scoopAt);
                    yield again();
                }
                Result scooped = buckets.scoop(scoopAt, scoopTag, true);
                if (scooped != null) yield scooped;
                Result away = toStand(standFor(scoopAt), calcFailed);
                if (away != null) yield away;
                if (++tries > TRIES) {
                    if (scoopTag == FluidTags.LAVA) yield Result.failed("couldn't take back the lava at " + scoopAt.toShortString());
                    leftAlone.add(scoopAt);
                    yield again();
                }
                yield Result.pause();
            }
            case DRAIN -> {
                boolean dry = cast.interior().stream().allMatch(p -> level.getBlockState(p).isAir());
                if (dry) {
                    to(State.LIGHT);
                    yield Result.pause();
                }
                if (++tries > DRAIN_TICKS) yield Result.failed("the portal opening at " + cast.ignite().above().toShortString() + " never drained");
                yield Result.pause();
            }
            case LIGHT -> {
                BlockPos inside = cast.interior().get(0);
                if (level.getBlockState(inside).is(Blocks.NETHER_PORTAL)) {
                    portal = inside;
                    yield Result.done();
                }
                if (++tries > TRIES) yield Result.failed("the cast portal frame at " + cast.ignite().toShortString() + " didn't light");
                if (!buckets.hold(stack -> stack.is(Items.FLINT_AND_STEEL))) yield Result.failed("no flint and steel to light the portal");
                // Strike, then give the server a moment to fill the frame.
                if (tries % 10 == 1) click(cast.ignite(), Direction.UP);
                yield Result.pause();
            }
        };
    }

    private static void logDebug(String message) {
        Helper.HELPER.logDebug(message);
    }

    /** Finishes the current job and picks the next. */
    private Result again() {
        next();
        return Result.pause();
    }

    /** A wall block went missing (the pathing dug through it, say): put it back, then pick up where it left off. */
    private Result mouldAgain() {
        to(State.MOULD);
        return Result.pause();
    }

    /**
     * Picks the next job from the world and the inventory: anything poured that shouldn't be there any more comes
     * back first, then water and lava in the buckets, a site, the mould, and the first cell not cast yet.
     */
    private void next() {
        if (cast != null) {
            for (PortalCast.Cell c : cast.cells()) {
                if (buckets.isSource(c.lava(), FluidTags.LAVA) && !leftAlone.contains(c.lava())) {
                    scoop(c.lava(), FluidTags.LAVA);
                    return;
                }
            }
            for (PortalCast.Cell c : cast.cells()) {
                if (buckets.isSource(c.water(), FluidTags.WATER) && x.have(Buckets.BUCKET) > 0 && !leftAlone.contains(c.water())) {
                    scoop(c.water(), FluidTags.WATER);
                    return;
                }
            }
        }
        if (cast != null && cell() == null) {
            to(State.DRAIN);
        } else if (x.have(Buckets.WATER_BUCKET) == 0) {
            to(State.WATER);
        } else if (cast == null) {
            to(State.SITE);
        } else if (x.have(Buckets.LAVA_BUCKET) == 0) {
            to(State.LAVA);
        } else if (toDig() != null) {
            to(State.DIG);
        } else if (missingWall() != null) {
            to(State.MOULD);
        } else {
            cell = cell();
            to(State.POUR_WATER);
        }
    }

    /** The first cell not cast yet, or null when the frame is done. */
    private PortalCast.Cell cell() {
        for (PortalCast.Cell c : cast.cells()) if (!ctx.world().getBlockState(c.lava()).is(Blocks.OBSIDIAN)) return c;
        return null;
    }

    private int cellsLeft() {
        return (int) cast.frame().stream().filter(p -> !ctx.world().getBlockState(p).is(Blocks.OBSIDIAN)).count();
    }

    private void scoop(BlockPos at, TagKey<Fluid> tag) {
        to(State.SCOOP);
        scoopAt = at;
        scoopTag = tag;
    }

    private void to(State next) {
        if (next != state) {
            logDebug("portal cast: " + next + (cast == null ? "" : " (site " + cast.origin().toShortString() + " facing "
                    + cast.front() + ")") + (next == State.POUR_WATER && cell != null ? " cell " + cell.lava().toShortString() : ""));
        }
        state = next;
        tries = 0;
        buckets.reset();
    }

    /**
     * Picks the site beside the lava pool it will be filled from: the nearest open pool with room for the frame near
     * it. A pool with no room is never looked at for a site again in this run, even by a re-plan's caster (it may still
     * be filled from), and the next nearest is tried; with none in view it goes looking for lava.
     */
    private Result site() {
        if (cast != null) return again();
        BlockPos lava = buckets.nearestSource(Blocks.LAVA, FluidTags.LAVA, null, x.noRoomLava);
        if (lava == null) {
            if (tries++ % 100 == 0) logDebug("portal cast: no lava in view from " + ctx.playerFeet().toShortString() + ", looking");
            return buckets.explore(true);
        }
        PortalCast.Found found = PortalCast.find(lava, SITE_RADIUS, MAX_DIGS, x.badCastSites, terrain());
        if (found != null) {
            cast = found.cast();
            x.castSite = cast;
            logDebug("portal cast: site " + cast.origin().toShortString() + " by the lava at " + lava.toShortString()
                    + " (" + found.digs() + " to dig, " + found.places() + " to place)");
            return again();
        }
        logDebug("portal cast: no room by the lava at " + lava.toShortString());
        x.noRoomLava.addAll(buckets.pool(lava, Buckets.POOL_RADIUS, FluidTags.LAVA));
        if (++pools > POOLS) return Result.failed("no spot near the lava at " + lava.toShortString() + " to cast a portal on");
        return Result.pause();
    }

    /**
     * Digs the site out with Baritone's builder, which gets the tick while it works. It's given one block at a time,
     * never a box: a box takes in the cells already cast, which the builder would mine by hand, and any water left
     * flowing, which it fills and breaks block by block.
     */
    private Result dig() {
        IBuilderProcess builder = x.baritone.getBuilderProcess();
        if (digging && builder.isActive()) return Result.defer();
        BlockPos block = toDig();
        if (block == null) return again();
        if (digging) {
            digging = false;
            if (!block.equals(dug)) {
                tries = 0;
            } else if (++tries > 2) {
                x.badCastSites.add(cast.origin());
                cast = null;
                return Result.failed("couldn't dig out the portal site at " + block.toShortString());
            }
        }
        dug = block;
        builder.clearArea(block, block);
        digging = true;
        return Result.defer();
    }

    /** Places the missing wall blocks bottom-up, each against the one under it, from the left stand. */
    private Result mould(boolean calcFailed) {
        BlockPos missing = missingWall();
        if (missing == null) return again();
        Result away = toStand(cast.stand(false), calcFailed);
        if (away != null) return away;
        if (++tries > TRIES * 4) return Result.failed("couldn't put up the mould wall at " + missing.toShortString());
        if (!ctx.world().getBlockState(missing).canBeReplaced()) {
            x.badCastSites.add(cast.origin());
            cast = null;
            return Result.failed("something is in the way of the portal's mould wall at " + missing.toShortString());
        }
        if (!buckets.hold(this::mouldBlock)) return Result.failed("ran out of blocks for the portal's mould wall");
        click(missing.below(), Direction.UP);
        return Result.pause();
    }

    /** Walks to {@code stand}; null once the player is on it. */
    private Result toStand(BlockPos stand, boolean calcFailed) {
        if (ctx.playerFeet().equals(stand) && ctx.player().onGround()) {
            standFails = 0;
            return null;
        }
        if (settling > 0) {
            settling--;
            return Result.pause();
        }
        if (calcFailed && ++standFails <= STAND_TRIES) {
            // Water just poured or taken back still flows over the stand rows for a few seconds, and Baritone won't
            // path through flowing water: let it drain before the site counts as out of reach.
            settling = SETTLE_TICKS;
            return Result.pause();
        }
        if (calcFailed) {
            x.badCastSites.add(cast.origin());
            cast = null;
            state = null;
            return Result.failed("no path to the portal site at " + stand.toShortString());
        }
        return walk(new GoalBlock(stand));
    }

    /** The stand in front of the half of the frame {@code pos} is in. */
    private BlockPos standFor(BlockPos pos) {
        for (PortalCast.Cell c : cast.cells()) if (c.lava().equals(pos) || c.water().equals(pos)) return c.stand();
        return cast.stand(false);
    }

    /**
     * The first block in the way of the cast, or null: not the cells cast, nor fluid, which drains. The pathing can
     * put one there after the site is dug, bridging out of the water poured.
     */
    private BlockPos toDig() {
        for (BlockPos p : cast.open()) {
            BlockState state = ctx.world().getBlockState(p);
            if (!state.isAir() && !state.is(Blocks.OBSIDIAN) && state.getFluidState().isEmpty()) return p;
        }
        return null;
    }

    private BlockPos missingWall() {
        for (BlockPos p : cast.wall()) if (!solid(p)) return p;
        return null;
    }

    private boolean solid(BlockPos pos) {
        Level level = ctx.world();
        BlockState state = level.getBlockState(pos);
        return state.isCollisionShapeFullBlock(level, pos) && state.getFluidState().isEmpty()
                && !(state.getBlock() instanceof FallingBlock) && !state.is(Blocks.MAGMA_BLOCK);
    }

    private boolean mouldBlock(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem
                && MOULD.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private PortalCast.Terrain terrain() {
        Level level = ctx.world();
        return new PortalCast.Terrain() {
            @Override
            public boolean floor(BlockPos pos) {
                return solid(pos);
            }

            @Override
            public boolean air(BlockPos pos) {
                return level.getBlockState(pos).isAir();
            }

            @Override
            public boolean diggable(BlockPos pos) {
                BlockState state = level.getBlockState(pos);
                return state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0
                        && !state.is(Blocks.OBSIDIAN) && !state.is(Blocks.CRYING_OBSIDIAN) && !state.hasBlockEntity();
            }

            @Override
            public boolean placeable(BlockPos pos) {
                BlockState state = level.getBlockState(pos);
                return state.canBeReplaced() && state.getFluidState().isEmpty();
            }

            @Override
            public boolean falls(BlockPos pos) {
                return level.getBlockState(pos).getBlock() instanceof FallingBlock;
            }

            @Override
            public boolean lava(BlockPos pos) {
                return level.getFluidState(pos).is(FluidTags.LAVA);
            }
        };
    }

    /** Looks at the middle of {@code face} of {@code block} and right-clicks it. */
    private void click(BlockPos block, Direction face) {
        Vec3 point = Vec3.atCenterOf(block).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        x.lookAt(point, true);
        use(new BlockHitResult(point, face, block, false), InteractionHand.MAIN_HAND);
    }

    @Override
    public void cancel() {
        IBuilderProcess builder = x.baritone.getBuilderProcess();
        if (digging && builder.isActive()) builder.onLostControl();
        digging = false;
    }
}
