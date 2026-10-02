package baritone.acquire.exec;

import baritone.acquire.model.Location;
import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;
import baritone.ai.AiBrain;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.utils.BlockOptionalMetaLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * {@link Step.Travel} between the Overworld and the Nether. Goes through the portal remembered for this dimension
 * ({@link PortalMemory}) or the nearest one in view. With none, on the plan's "build and light" trip, it builds one:
 * with 10 obsidian in hand, a {@link PortalFrame} on flat ground nearby; otherwise, with two buckets and blocks for a
 * mould, it casts one in place next to lava ({@link PortalCaster}). It lights the frame with flint and steel and steps
 * in. On arrival it remembers the portals on both sides and steps out of the portal so it doesn't carry the player
 * straight back.
 */
final class TravelRunner extends RunnerBase {

    private enum State { FIND, TO_SITE, BUILD, LIGHT, CAST, ENTER, STEP_OUT }

    private static final int SITE_RADIUS = 10;
    private static final int PLACE_TRIES = 20;
    /** Standing in a portal takes 4 seconds to carry the player; wait a little longer before trying again. */
    private static final int IN_PORTAL_TICKS = 160;
    /**
     * Ticks after arriving before the step-out looks at the portal: the new dimension's chunks come in over the first
     * ticks, and until then the portal the player stands in reads as air.
     */
    private static final int ARRIVE_TICKS = 20;
    /**
     * Ticks out of a portal before it will carry the player again. A player comes out on a 10-tick cooldown that
     * starts over every tick it stands in a portal, so walking back in too soon leaves it standing there for good.
     */
    private static final int COOLDOWN_TICKS = 20;
    /** Times it steps out of a portal that didn't carry it and goes back in before giving up. */
    private static final int ENTRIES = 3;
    /** Spare blocks that make good frame corners, most common first. */
    /** Casting digs the site out and paths through stone to water and lava. */
    private static final ToolReq PICKAXE = new ToolReq("pickaxe", 1, true);
    private static final Set<String> CORNERS = Set.of("minecraft:cobblestone", "minecraft:cobbled_deepslate",
            "minecraft:netherrack", "minecraft:dirt", "minecraft:stone", "minecraft:andesite", "minecraft:diorite",
            "minecraft:granite", "minecraft:tuff", "minecraft:deepslate", "minecraft:blackstone", "minecraft:basalt");

    private final Step.Travel step;
    private final String world = AiBrain.currentWorldKey();
    private State state = State.FIND;
    private BlockPos portal;
    private BlockPos site;
    private int placed;
    private int tries;
    private int inPortal;
    private int entries;
    /** Ticks still to spend out of the portal before going back in, while above zero. */
    private int outOfPortal;
    /** Ticks clear of the portal after the step-out. */
    private int clear;
    private int sinceArrival;
    private BlockPos arrivedAt;
    private String fromDimension;
    private BlockPos fromPortal;
    private PortalCaster caster;
    private boolean hadPickaxe;

    TravelRunner(ExecContext x, Step.Travel step) {
        super(x);
        this.step = step;
    }

    /** Whether this runner can make the trip (the End comes with WP 12). */
    static boolean supports(Step.Travel travel) {
        return travel.to() != Location.END && travel.from().dimension() != Location.END;
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        if (!supports(step)) return Result.failed("going to " + step.to().label() + " isn't built yet");
        Level level = ctx.world();
        String here = dimensionId(level);
        if (fromDimension == null) fromDimension = here;
        if (BaritoneWorldView.dimension(level) == step.to().dimension() && state != State.STEP_OUT) {
            arrived(here);
            state = State.STEP_OUT;
        }
        switch (state) {
            case FIND -> {
                portal = knownPortal(here);
                if (portal != null) {
                    state = State.ENTER;
                    return Result.pause();
                }
                if (step.consumes().isEmpty()) {
                    return Result.fatal("no nether portal known here to go back through");
                }
                int flint = x.have("minecraft:flint_and_steel");
                boolean frame = x.have("minecraft:obsidian") >= 10 && flint > 0 && cornerBlocks() >= 4;
                if (!frame && PortalCaster.supports(x.have(Buckets.BUCKET) + x.have(Buckets.WATER_BUCKET)
                        + x.have(Buckets.LAVA_BUCKET), mouldBlocks(), flint)) {
                    caster = new PortalCaster(x);
                    hadPickaxe = x.hasTool(PICKAXE);
                    state = State.CAST;
                    return Result.pause();
                }
                if (x.have("minecraft:obsidian") < 10 || flint < 1) {
                    return Result.failed("building a portal needs 10 obsidian and a flint and steel, or two buckets, "
                            + PortalCast.WALL_BLOCKS + " blocks and a flint and steel to cast one");
                }
                if (cornerBlocks() < 4) return Result.fatal("building a portal needs 4 spare blocks for its corners (cobblestone, dirt...)");
                site = findSite();
                if (site == null) return Result.fatal("no flat open 4x3 spot within " + SITE_RADIUS + " blocks to build a portal");
                state = State.TO_SITE;
                return Result.pause();
            }
            case TO_SITE -> {
                BlockPos stand = PortalFrame.stand(site);
                if (ctx.playerFeet().equals(stand) && ctx.player().onGround()) {
                    state = State.BUILD;
                    return Result.pause();
                }
                if (calcFailed) return Result.failed("no path to the portal site at " + site.toShortString());
                return walk(new GoalBlock(stand));
            }
            case BUILD -> {
                List<PortalFrame.Placement> order = PortalFrame.placements(site);
                while (placed < order.size() && !level.getBlockState(order.get(placed).pos()).canBeReplaced()) {
                    placed++;
                    tries = 0;
                }
                if (placed >= order.size()) {
                    state = State.LIGHT;
                    tries = 0;
                    return Result.pause();
                }
                if (++tries > PLACE_TRIES) return Result.failed("couldn't place the portal block at " + order.get(placed).pos().toShortString());
                PortalFrame.Placement p = order.get(placed);
                if (!hold(p.obsidian() ? stack -> stack.is(Items.OBSIDIAN) : this::corner)) {
                    return Result.failed("ran out of " + (p.obsidian() ? "obsidian" : "corner blocks") + " for the portal");
                }
                BlockPos support = p.pos().relative(p.against());
                Direction face = p.against().getOpposite();
                click(support, face);
                return Result.pause();
            }
            case LIGHT -> {
                if (level.getBlockState(PortalFrame.interior(site).get(0)).is(Blocks.NETHER_PORTAL)) {
                    portal = PortalFrame.interior(site).get(0);
                    state = State.ENTER;
                    return Result.pause();
                }
                if (++tries > PLACE_TRIES) return Result.failed("the portal frame at " + site.toShortString() + " didn't light");
                if (!hold(stack -> stack.is(Items.FLINT_AND_STEEL))) return Result.failed("no flint and steel to light the portal");
                click(PortalFrame.ignite(site), Direction.UP);
                return Result.pause();
            }
            case CAST -> {
                // With no pickaxe the digging goes on by hand for ever: plan a new one instead.
                if (!x.hasTool(PICKAXE)) {
                    return Result.failed((hadPickaxe ? "your tool broke, need a " : "need a ") + "pickaxe to cast the portal");
                }
                Result cast = caster.tick(calcFailed, safeToCancel);
                if (cast.kind() != Result.Kind.DONE) return cast;
                portal = caster.portal();
                state = State.ENTER;
                return Result.pause();
            }
            case ENTER -> {
                if (level.isLoaded(portal) && !level.getBlockState(portal).is(Blocks.NETHER_PORTAL)) {
                    // It went out (or was never lit): forget it and look again.
                    portal = null;
                    state = State.FIND;
                    return Result.pause();
                }
                boolean in = level.getBlockState(ctx.playerFeet()).is(Blocks.NETHER_PORTAL);
                if (outOfPortal > 0) {
                    if (in) return walk(new GoalRunAway(3, portal));
                    outOfPortal--;
                    return Result.pause();
                }
                if (in) {
                    if (++inPortal > IN_PORTAL_TICKS) {
                        if (++entries > ENTRIES) return Result.failed("stood in the portal at " + portal.toShortString() + " but it didn't carry me");
                        // Most likely on the cooldown of the trip just made: step out, let it run out, and go back in.
                        inPortal = 0;
                        outOfPortal = COOLDOWN_TICKS;
                        return walk(new GoalRunAway(3, portal));
                    }
                    fromPortal = ctx.playerFeet();
                    return Result.pause();
                }
                inPortal = 0;
                if (calcFailed) return Result.failed("no path into the portal at " + portal.toShortString());
                return walk(new GoalTwoBlocks(portal));
            }
            case STEP_OUT -> {
                BlockPos feet = ctx.playerFeet();
                if (!level.isLoaded(feet) || ++sinceArrival < ARRIVE_TICKS) return Result.pause();
                if (sinceArrival == ARRIVE_TICKS) {
                    BlockPos landed = nearestPortal();
                    if (landed != null) PortalMemory.put(world, here, landed);
                }
                if (!level.getBlockState(feet).is(Blocks.NETHER_PORTAL) && !level.getBlockState(feet.above()).is(Blocks.NETHER_PORTAL)) {
                    // Stand clear until the cooldown runs out, so a trip straight back goes through.
                    return ++clear >= COOLDOWN_TICKS ? Result.done() : Result.pause();
                }
                clear = 0;
                // A failed path only counts once the walk out is under way: one from before the trip may still come in.
                if (calcFailed && arrivedAt != null) return Result.done();
                if (arrivedAt == null) arrivedAt = feet;
                return walk(new GoalRunAway(3, arrivedAt));
            }
        }
        return Result.pause();
    }

    @Override
    public void cancel() {
        if (caster != null) caster.cancel();
    }

    /** Whether the player is in the dimension the trip goes to. */
    boolean landed() {
        return BaritoneWorldView.dimension(ctx.world()) == step.to().dimension();
    }

    /** Remembers the portal just left; the one landed in is remembered once its chunks are in. */
    private void arrived(String here) {
        BlockPos left = fromPortal != null ? fromPortal : portal;
        if (left != null && fromDimension != null && !fromDimension.equals(here)) PortalMemory.put(world, fromDimension, left);
    }

    private BlockPos knownPortal(String here) {
        BlockPos remembered = PortalMemory.get(world, here);
        if (remembered != null && ctx.world().isLoaded(remembered)
                && ctx.world().getBlockState(remembered).is(Blocks.NETHER_PORTAL)) {
            return remembered;
        }
        BlockPos near = nearestPortal();
        if (near != null) return near;
        // Remembered but not loaded yet: walk toward it; ENTER re-checks once it is in view.
        return remembered;
    }

    private BlockPos nearestPortal() {
        List<BlockPos> found = BaritoneAPI.getProvider().getWorldScanner().scanChunkRadius(
                ctx, new BlockOptionalMetaLookup(Blocks.NETHER_PORTAL), 32, -1, 6);
        BlockPos feet = ctx.playerFeet();
        return found.stream().min(Comparator.comparingDouble(p -> p.distSqr(feet))).orElse(null);
    }

    /** The nearest spot within {@link #SITE_RADIUS} where the frame, its opening and the way in are all flat and open. */
    private BlockPos findSite() {
        BlockPos feet = ctx.playerFeet();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -SITE_RADIUS; dx <= SITE_RADIUS; dx++) {
                for (int dz = -SITE_RADIUS; dz <= SITE_RADIUS; dz++) {
                    BlockPos ground = feet.offset(dx, dy - 1, dz);
                    double distance = PortalFrame.stand(ground).distSqr(feet);
                    if (distance >= bestDistance || !siteOk(ground)) continue;
                    best = ground;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private boolean siteOk(BlockPos ground) {
        Level level = ctx.world();
        for (BlockPos pos : PortalFrame.ground(ground)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isCollisionShapeFullBlock(level, pos) || !state.getFluidState().isEmpty()
                    || state.getBlock() instanceof FallingBlock || state.is(Blocks.MAGMA_BLOCK)) {
                return false;
            }
        }
        for (BlockPos pos : PortalFrame.clearance(ground)) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() || !level.getFluidState(pos).isEmpty()) return false;
            for (Direction d : Direction.values()) if (level.getFluidState(pos.relative(d)).is(net.minecraft.tags.FluidTags.LAVA)) return false;
        }
        return true;
    }

    private int cornerBlocks() {
        int n = 0;
        for (ItemStack stack : ctx.player().getInventory().getNonEquipmentItems()) {
            if (!stack.is(Items.OBSIDIAN) && corner(stack)) n += stack.getCount();
        }
        // Obsidian beyond the 10 the frame needs can be corners too.
        return n + Math.max(0, x.have("minecraft:obsidian") - 10);
    }

    private int mouldBlocks() {
        int n = 0;
        for (ItemStack stack : ctx.player().getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && PortalCaster.MOULD.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) n += stack.getCount();
        }
        return n;
    }

    private boolean corner(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) return false;
        if (stack.is(Items.OBSIDIAN)) return x.have("minecraft:obsidian") > 10;
        return CORNERS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private boolean hold(java.util.function.Predicate<ItemStack> want) {
        int slot = InventoryOps.toHotbar(ctx, want);
        if (slot < 0) return false;
        ctx.player().getInventory().setSelectedSlot(slot);
        return true;
    }

    /** Looks at the middle of {@code face} of {@code block} and right-clicks it. */
    private void click(BlockPos block, Direction face) {
        Vec3 point = Vec3.atCenterOf(block).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        x.lookAt(point, true);
        use(new BlockHitResult(point, face, block, false), InteractionHand.MAIN_HAND);
    }

    static String dimensionId(Level level) {
        return level.dimension().identifier().getPath();
    }
}
