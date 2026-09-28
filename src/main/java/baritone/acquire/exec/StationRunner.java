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
                    if (!findSpot()) return Result.failed("no free spot next to you to place a " + name());
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
     * Where the station goes: an air (or replaceable) block near the player, not inside the player or a mob, placed
     * against a sturdy, non-interactive face the player can see. A floor is preferred; underground a wall or ceiling
     * face will do, and in a shaft dug straight down the space above the head is the only free spot. Sets
     * {@link #placeAt}, the hit and rotation.
     */
    private boolean findSpot() {
        for (Direction support : SUPPORTS) {
            for (int r = 1; r <= 2; r++) {
                for (int dy : new int[]{0, -1, 1}) {
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) == r && trySpot(dx, dy, dz, support)) return true;
                        }
                    }
                }
            }
            if (trySpot(0, 2, 0, support)) return true;
        }
        return false;
    }

    /** Faces to place against, most natural first: the floor, then walls, then the ceiling. */
    private static final Direction[] SUPPORTS = {
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP
    };

    /** Whether the block at feet + (dx, dy, dz) can take the station against its {@code side} neighbour. */
    private boolean trySpot(int dx, int dy, int dz, Direction side) {
        Level level = ctx.world();
        BlockPos pos = ctx.playerFeet().offset(dx, dy, dz);
        BlockState here = level.getBlockState(pos);
        if (!here.isAir() && !(here.canBeReplaced() && here.getFluidState().isEmpty())) return false;
        BlockPos support = pos.relative(side);
        Direction face = side.getOpposite();
        BlockState against = level.getBlockState(support);
        if (!against.isFaceSturdy(level, support, face)) return false;
        if (against.hasBlockEntity() || against.getMenuProvider(level, support) != null) return false;
        AABB box = new AABB(pos);
        if (ctx.player().getBoundingBox().intersects(box)) return false;
        if (!level.getEntities(ctx.player(), box, e -> !(e instanceof ItemEntity) && !e.isSpectator()).isEmpty()) return false;
        Vec3 aim = Vec3.atCenterOf(support).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        if (ctx.playerHead().distanceTo(aim) > x.reach() - 0.3) return false;
        Rotation rot = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), aim, ctx.playerRotations());
        HitResult hit = RayTraceUtils.rayTraceTowards(ctx.player(), rot, x.reach());
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return false;
        boolean onFace = blockHit.getBlockPos().equals(support) && blockHit.getDirection() == face;
        boolean onSpot = blockHit.getBlockPos().equals(pos); // e.g. short grass, which the placement replaces
        if (!onFace && !onSpot) return false;
        placeAt = pos.immutable();
        placeHit = blockHit;
        placeRot = rot;
        return true;
    }

    private String name() {
        return Step.shortId(step.station()).replace('_', ' ');
    }
}
