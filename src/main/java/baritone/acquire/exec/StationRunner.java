package baritone.acquire.exec;

import baritone.Baritone;
import baritone.acquire.model.Step;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * {@link Step.PlaceStation}: walk to a usable station within {@code acquireStationRadius}, or, when
 * there is none and {@code acquirePlaceStations} is on, place the station item from the inventory on
 * solid ground next to the player. A placed station is remembered so later steps find it again.
 */
final class StationRunner extends RunnerBase {

    private enum State { FIND, WALK, PREPARE, LOOK, VERIFY }

    private static final int MAX_PLACE_ATTEMPTS = 3;
    private static final int VERIFY_TICKS = 20;

    private final Step.PlaceStation step;
    private final Block block;
    private State state = State.FIND;
    private BlockPos target;
    private BlockPos placeAt;
    private BlockHitResult placeHit;
    private Rotation placeRot;
    private InteractionHand hand = InteractionHand.MAIN_HAND;
    private int ticks;
    private int attempts;

    StationRunner(ExecContext x, Step.PlaceStation step) {
        super(x);
        this.step = step;
        this.block = StationFinder.block(step.station());
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        if (block == null) return Result.failed("unknown station block " + step.station());
        for (int guard = 0; guard < 4; guard++) {
            switch (state) {
                case FIND -> {
                    List<BlockPos> found = x.stations.find(step.station(), x.stationRadius());
                    if (!found.isEmpty()) {
                        target = found.get(0);
                        state = State.WALK;
                        continue;
                    }
                    if (!Baritone.settings().acquirePlaceStations.value) {
                        return Result.failed("no " + name() + " within " + x.stationRadius() + " blocks, and acquirePlaceStations is off");
                    }
                    if (x.have(step.station()) <= 0) return Result.failed("no " + name() + " nearby and none in the inventory to place");
                    state = State.PREPARE;
                }
                case WALK -> {
                    if (!ctx.world().getBlockState(target).is(block)) {
                        state = State.FIND;
                        continue;
                    }
                    Result walking = approach(target, calcFailed);
                    if (walking == null) return Result.done();
                    if (walking.kind() == Result.Kind.FAILED) {
                        x.stations.markUnusable(target);
                        state = State.FIND;
                        return Result.pause();
                    }
                    return walking;
                }
                case PREPARE -> {
                    if (!safeToCancel || !ctx.player().onGround()) return Result.pause();
                    if (!InventoryOps.inventoryMenuOpen(ctx.player())) ctx.player().closeContainer();
                    Item item = InventoryReader.itemOf(step.station());
                    int slot = InventoryOps.toHotbar(ctx, stack -> stack.is(item));
                    if (slot >= 0) {
                        ctx.player().getInventory().setSelectedSlot(slot);
                        hand = InteractionHand.MAIN_HAND;
                    } else if (ctx.player().getOffhandItem().is(item)) {
                        hand = InteractionHand.OFF_HAND;
                    } else {
                        return Result.failed("can't get the " + name() + " onto the hotbar");
                    }
                    if (!findSpot()) return Result.failed("no free spot on solid ground next to you to place a " + name());
                    state = State.LOOK;
                    ticks = 0;
                }
                case LOOK -> {
                    x.look(placeRot, true);
                    if (++ticks < 3) return Result.pause();
                    use(placeHit, hand);
                    state = State.VERIFY;
                    ticks = 0;
                    return Result.pause();
                }
                case VERIFY -> {
                    if (ctx.world().getBlockState(placeAt).is(block)) {
                        x.stations.remember(step.station(), placeAt);
                        return Result.done();
                    }
                    if (++ticks < VERIFY_TICKS) return Result.pause();
                    if (++attempts >= MAX_PLACE_ATTEMPTS) return Result.failed("couldn't place the " + name() + " (the server kept undoing it?)");
                    state = State.PREPARE;
                    return Result.pause();
                }
            }
        }
        return Result.pause();
    }

    @Override
    public void cancel() {
    }

    /**
     * An air (or replaceable) block next to the player with a solid, non-interactive floor, not inside the
     * player or a mob, whose floor top face the player can see. Sets {@link #placeAt}, the hit and rotation.
     */
    private boolean findSpot() {
        Level level = ctx.world();
        BlockPos feet = ctx.playerFeet();
        AABB self = ctx.player().getBoundingBox();
        double reach = x.reach() - 0.3;
        for (int r = 1; r <= 2; r++) {
            for (int dy : new int[]{0, -1, 1}) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos pos = feet.offset(dx, dy, dz);
                        BlockState here = level.getBlockState(pos);
                        if (!here.isAir() && !(here.canBeReplaced() && here.getFluidState().isEmpty())) continue;
                        BlockPos floor = pos.below();
                        BlockState under = level.getBlockState(floor);
                        if (!under.isFaceSturdy(level, floor, Direction.UP)) continue;
                        if (under.hasBlockEntity() || under.getMenuProvider(level, floor) != null) continue;
                        AABB box = new AABB(pos);
                        if (self.intersects(box)) continue;
                        if (!level.getEntities(ctx.player(), box, e -> !(e instanceof ItemEntity) && !e.isSpectator()).isEmpty()) continue;
                        Vec3 face = new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5);
                        if (ctx.playerHead().distanceTo(face) > reach) continue;
                        Rotation rot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), face, ctx.playerRotations());
                        HitResult hit = RayTraceUtils.rayTraceTowards(ctx.player(), rot, x.reach());
                        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) continue;
                        boolean onFloor = blockHit.getBlockPos().equals(floor) && blockHit.getDirection() == Direction.UP;
                        boolean onSpot = blockHit.getBlockPos().equals(pos); // e.g. short grass, which the placement replaces
                        if (!onFloor && !onSpot) continue;
                        placeAt = pos.immutable();
                        placeHit = blockHit;
                        placeRot = rot;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String name() {
        return Step.shortId(step.station()).replace('_', ' ');
    }
}
