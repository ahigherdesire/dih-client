package baritone.parkour.sim;

import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * One client movement tick of the local player, reimplemented from Minecraft 26.2's own code in the same order and
 * the same float/double arithmetic: {@code LocalPlayer.aiStep} (crouch, input, sprint), {@code LivingEntity.aiStep}
 * (small-velocity cut, input scaling, jump), {@code travelInAir}, {@code Entity.move} (edge back-off when sneaking,
 * collision with step-up, ground and supporting block, fall distance, bounce, block speed factor) and the pose update
 * at the end of {@code Player.tick}. Collision uses the game's own {@link Shapes#collide}.
 *
 * <p>Covers walking, sprinting, sneaking and jumping on and between solid blocks, slabs, stairs, fences, walls,
 * panes, ladders, vines, ice, slime, honey and soul sand. Not covered (the step reports {@link #unsupported}): water,
 * lava, cobwebs, powder snow, elytra, flying, effects and riding.
 */
public final class PlayerPhysics {
    static final float WIDTH = 0.6F;
    static final float HEIGHT = 1.8F;
    static final float CROUCH_HEIGHT = 1.5F;
    /** Attribute defaults, as the game reads them (the attribute is a double; some are cast to float). */
    static final double GRAVITY = 0.08;
    static final float JUMP_STRENGTH = (float) (double) 0.42F;
    static final float STEP_HEIGHT = (float) 0.6;
    static final float SNEAKING_SPEED = (float) 0.3;
    static final double MOVEMENT_SPEED = 0.1F;
    /** The sprint modifier is +30% of the total. */
    static final double SPRINT_MULTIPLIER = 0.3F;
    /** The vertical velocity of a player standing still: one tick of gravity and drag, cancelled each tick by the ground. */
    public static final double RESTING_VY = -GRAVITY * (double) (1.0F - (1.0F - 0.98F) * 1.0F);

    private PlayerPhysics() {
    }

    /** Why the last {@link #step} can't be trusted, or null. Set for fluids and other things this doesn't model. */
    public static String unsupported(PlayerState state, BlockSource world) {
        BlockPos feet = BlockPos.containing(state.x, state.y, state.z);
        AABB box = state.box();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState block = world.get(pos);
            if (!block.getFluidState().isEmpty()) return "in a fluid at " + pos.toShortString();
            if (block.is(Blocks.COBWEB) || block.is(Blocks.POWDER_SNOW) || block.is(Blocks.SWEET_BERRY_BUSH)) return "in " + block.getBlock() + " at " + pos.toShortString();
        }
        return world.get(feet).getBlock() instanceof ScaffoldingBlock ? "in scaffolding" : null;
    }

    /** The state after one tick of {@code input}. {@code state} is left as it was. */
    public static PlayerState step(PlayerState state, TickInput input, BlockSource world) {
        PlayerState s = state.copy();
        Ctx c = new Ctx(world);
        s.yaw = input.yaw();

        // ---- LocalPlayer.aiStep
        boolean wasShiftKeyDown = s.prevSneak;
        boolean hadForwardImpulse = s.prevForwardImpulse > 1.0E-5F;
        s.crouching = wasShiftKeyDown ? canFit(s, c, true) : !canFit(s, c, false) && canFit(s, c, true);
        Vec2 move = new Vec2(input.leftImpulse(), input.forwardImpulse()).normalized();
        boolean hasForwardImpulse = move.y > 1.0E-5F;
        boolean movingSlowly = s.crouching;
        if (!s.sprinting && hasForwardImpulse && !movingSlowly && input.sprint()) s.sprinting = true;
        if (s.sprinting && (!hasForwardImpulse || s.horizontalCollision && !s.minorHorizontalCollision)) s.sprinting = false;
        // (the double-tap sprint window is not modelled; sprinting comes from the sprint key)
        if (!hadForwardImpulse) {
            // no effect without double-tap sprint
        }

        // ---- LivingEntity.aiStep
        if (s.noJumpDelay > 0) s.noJumpDelay--;
        double dx = s.vx, dy = s.vy, dz = s.vz;
        if (dx * dx + dz * dz < 9.0E-6) {
            dx = 0.0;
            dz = 0.0;
        }
        if (Math.abs(dy) < 0.003) dy = 0.0;
        s.vx = dx;
        s.vy = dy;
        s.vz = dz;

        Vec2 modified = modifyInput(move, movingSlowly);
        float xxa = modified.x;
        float zza = modified.y;
        boolean jumping = input.jump();
        if (jumping) {
            if (s.onGround && s.noJumpDelay == 0) {
                jumpFromGround(s, c);
                s.noJumpDelay = 10;
            }
        } else {
            s.noJumpDelay = 0;
        }

        travelInAir(s, c, xxa, zza, jumping, input.sneak());

        // ---- end of Player.tick: pose
        boolean wantCrouch = input.sneak();
        if (canFit(s, c, wantCrouch)) s.crouchPose = wantCrouch;
        else if (!wantCrouch && canFit(s, c, true)) s.crouchPose = true;

        s.prevJump = input.jump();
        s.prevSneak = input.sneak();
        s.prevForwardImpulse = move.y;
        return s;
    }

    // ------------------------------------------------------------------ input

    private static Vec2 modifyInput(Vec2 input, boolean movingSlowly) {
        if (input.lengthSquared() == 0.0F) return input;
        Vec2 newInput = input.scale(0.98F);
        if (movingSlowly) newInput = newInput.scale(SNEAKING_SPEED);
        float length = newInput.length();
        if (length <= 0.0F) return newInput;
        Vec2 direction = newInput.scale(1.0F / length);
        float dirX = Math.abs(direction.x);
        float dirY = Math.abs(direction.y);
        float tan = dirY > dirX ? dirX / dirY : dirY / dirX;
        float distanceToUnitSquare = Mth.sqrt(1.0F + Mth.square(tan));
        float modifiedLength = Math.min(length * distanceToUnitSquare, 1.0F);
        return direction.scale(modifiedLength);
    }

    private static float speed(PlayerState s) {
        double value = MOVEMENT_SPEED;
        if (s.sprinting) value = value * (1.0 + SPRINT_MULTIPLIER);
        return (float) value;
    }

    // ------------------------------------------------------------------ jump

    private static void jumpFromGround(PlayerState s, Ctx c) {
        float jumpPower = JUMP_STRENGTH * 1.0F * blockJumpFactor(s, c);
        if (jumpPower <= 1.0E-5F) return;
        s.vy = Math.max(jumpPower, s.vy);
        if (s.sprinting) {
            float angle = s.yaw * (float) (Math.PI / 180.0);
            s.vx += -Mth.sin(angle) * 0.2;
            s.vz += Mth.cos(angle) * 0.2;
        }
    }

    // ------------------------------------------------------------------ travel

    private static float computeModifiedFriction(float friction, float modifier) {
        return Mth.clamp(1.0F - (1.0F - friction) * modifier, 0.0F, 1.0F);
    }

    private static void travelInAir(PlayerState s, Ctx c, float xxa, float zza, boolean jumping, boolean shift) {
        BlockPos posBelow = onPos(s, c, 0.500001F);
        float blockFriction = s.onGround ? computeModifiedFriction(c.world.get(posBelow).getBlock().getFriction(), 1.0F) : 1.0F;

        // handleRelativeFrictionAndCalculateMovement
        float speed;
        if (s.onGround) {
            float sp = speed(s);
            speed = blockFriction > 0.6 ? sp * (0.21600002F / (blockFriction * blockFriction * blockFriction)) : sp;
        } else {
            speed = s.sprinting ? 0.025999999F : 0.02F;
        }
        Vec3 inputVector = inputVector(new Vec3(xxa, 0.0, zza), speed, s.yaw);
        s.vx += inputVector.x;
        s.vy += inputVector.y;
        s.vz += inputVector.z;
        boolean climbable = onClimbable(s, c);
        if (climbable) {
            s.fallDistance = 0.0;
            s.vx = Mth.clamp(s.vx, -0.15F, 0.15F);
            s.vz = Mth.clamp(s.vz, -0.15F, 0.15F);
            s.vy = Math.max(s.vy, -0.15F);
            if (s.vy < 0.0 && shift && !(c.world.get(BlockPos.containing(s.x, s.y, s.z)).getBlock() instanceof ScaffoldingBlock)) s.vy = 0.0;
        }
        move(s, c, new Vec3(s.vx, s.vy, s.vz), xxa, zza, shift);
        double mx = s.vx, my = s.vy, mz = s.vz;
        if ((s.horizontalCollision || jumping) && onClimbable(s, c)) my = 0.2;

        double movementY = my - GRAVITY;
        float airDrag = computeModifiedFriction(0.91F, 1.0F);
        float friction = blockFriction * airDrag;
        float verticalFriction = computeModifiedFriction(0.98F, 1.0F);
        s.vx = mx * friction;
        s.vy = movementY * verticalFriction;
        s.vz = mz * friction;
    }

    static Vec3 inputVector(Vec3 input, float speed, float yRot) {
        double length = input.lengthSqr();
        if (length < 1.0E-7) return Vec3.ZERO;
        Vec3 movement = (length > 1.0 ? input.normalize() : input).scale(speed);
        float sin = Mth.sin(yRot * (float) (Math.PI / 180.0));
        float cos = Mth.cos(yRot * (float) (Math.PI / 180.0));
        return new Vec3(movement.x * cos - movement.z * sin, movement.y, movement.z * cos + movement.x * sin);
    }

    // ------------------------------------------------------------------ Entity.move

    private static void move(PlayerState s, Ctx c, Vec3 delta, float xxa, float zza, boolean shift) {
        Vec3 requested = maybeBackOffFromEdge(s, c, delta, shift);
        Vec3 movement = collide(s, c, requested);
        double movementLength = movement.lengthSqr();
        if (movementLength > 1.0E-7 || requested.lengthSqr() - movementLength < 1.0E-7) {
            s.x += movement.x;
            s.y += movement.y;
            s.z += movement.z;
        }
        boolean xCollision = !Mth.equal(requested.x, movement.x);
        boolean zCollision = !Mth.equal(requested.z, movement.z);
        s.horizontalCollision = xCollision || zCollision;
        s.verticalCollision = requested.y != movement.y;
        boolean verticalCollisionBelow = s.verticalCollision && requested.y < 0.0;
        s.onGround = verticalCollisionBelow;
        checkSupportingBlock(s, c, s.onGround, movement);
        s.minorHorizontalCollision = s.horizontalCollision && isHorizontalCollisionMinor(s, movement, xxa, zza);

        BlockPos effectPos = onPos(s, c, 0.2F);
        BlockState effectState = c.world.get(effectPos);
        // checkFallDamage (no water)
        if (movement.y < 0.0) s.fallDistance -= (float) movement.y;
        if (s.onGround) s.fallDistance = 0.0;

        boolean movedVertically = Math.abs(requested.y) > 0.0;
        if (movedVertically && s.verticalCollision || s.horizontalCollision) {
            restitute(s, effectState, xCollision, zCollision, movement, shift);
        }
        float blockSpeedFactor = blockSpeedFactor(s, c);
        s.vx *= blockSpeedFactor;
        s.vz *= blockSpeedFactor;
    }

    private static void restitute(PlayerState s, BlockState effectState, boolean xCollision, boolean zCollision, Vec3 movement, boolean shift) {
        double restitution = 0.0;
        double cx = s.vx, cy = s.vy, cz = s.vz;
        double nx = cx, ny = cy, nz = cz;
        if (xCollision) nx = -cx * restitution;
        if (zCollision) nz = -cz * restitution;
        if (s.verticalCollision) {
            if (s.verticalCollision && s.onGround) {
                restitution = !(-cy < GRAVITY) && !shift && !suppressesBounce(effectState)
                    ? Math.max(restitution, effectState.getBlock().getBounceRestitution())
                    : 0.0;
            }
            double gravityCompensation;
            double effectiveDrag;
            if (restitution > 0.0) {
                double portionWithMovement = movement.y / cy;
                gravityCompensation = portionWithMovement * GRAVITY;
                effectiveDrag = Mth.lerp(portionWithMovement, 1.0, computeModifiedFriction(0.98F, 1.0F));
            } else {
                gravityCompensation = 0.0;
                effectiveDrag = 1.0;
            }
            ny = (gravityCompensation - cy) * effectiveDrag * restitution;
        }
        s.vx = nx;
        s.vy = ny;
        s.vz = nz;
    }

    private static boolean suppressesBounce(BlockState state) {
        return state.is(BlockTags.SUPPRESSES_BOUNCE);
    }

    private static boolean isHorizontalCollisionMinor(PlayerState s, Vec3 movement, float xxa, float zza) {
        float yRotInRadians = s.yaw * (float) (Math.PI / 180.0);
        double yRotSin = Mth.sin(yRotInRadians);
        double yRotCos = Mth.cos(yRotInRadians);
        double globalXA = xxa * yRotCos - zza * yRotSin;
        double globalZA = zza * yRotCos + xxa * yRotSin;
        double aLengthSquared = Mth.square(globalXA) + Mth.square(globalZA);
        double movementLengthSquared = Mth.square(movement.x) + Mth.square(movement.z);
        if (!(aLengthSquared < 1.0E-5F) && !(movementLengthSquared < 1.0E-5F)) {
            double dotProduct = globalXA * movement.x + globalZA * movement.z;
            double angle = Math.acos(dotProduct / Math.sqrt(aLengthSquared * movementLengthSquared));
            return angle < 0.13962634F;
        }
        return false;
    }

    private static Vec3 maybeBackOffFromEdge(PlayerState s, Ctx c, Vec3 delta, boolean shift) {
        float maxDownStep = STEP_HEIGHT;
        if (delta.y > 0.0 || !shift || !isAboveGround(s, c, maxDownStep)) return delta;
        double deltaX = delta.x;
        double deltaZ = delta.z;
        double stepX = Math.signum(deltaX) * 0.05;
        double stepZ = Math.signum(deltaZ) * 0.05;
        while (deltaX != 0.0 && canFallAtLeast(s, c, deltaX, 0.0, maxDownStep)) {
            if (Math.abs(deltaX) <= 0.05) {
                deltaX = 0.0;
                break;
            }
            deltaX -= stepX;
        }
        while (deltaZ != 0.0 && canFallAtLeast(s, c, 0.0, deltaZ, maxDownStep)) {
            if (Math.abs(deltaZ) <= 0.05) {
                deltaZ = 0.0;
                break;
            }
            deltaZ -= stepZ;
        }
        while (deltaX != 0.0 && deltaZ != 0.0 && canFallAtLeast(s, c, deltaX, deltaZ, maxDownStep)) {
            if (Math.abs(deltaX) <= 0.05) deltaX = 0.0;
            else deltaX -= stepX;
            if (Math.abs(deltaZ) <= 0.05) deltaZ = 0.0;
            else deltaZ -= stepZ;
        }
        return new Vec3(deltaX, delta.y, deltaZ);
    }

    private static boolean isAboveGround(PlayerState s, Ctx c, float maxDownStep) {
        return s.onGround || s.fallDistance < maxDownStep && !canFallAtLeast(s, c, 0.0, 0.0, maxDownStep - s.fallDistance);
    }

    private static boolean canFallAtLeast(PlayerState s, Ctx c, double deltaX, double deltaZ, double minHeight) {
        AABB b = s.box();
        return noCollision(c, new AABB(b.minX + 1.0E-7 + deltaX, b.minY - minHeight - 1.0E-7, b.minZ + 1.0E-7 + deltaZ,
            b.maxX - 1.0E-7 + deltaX, b.minY, b.maxZ - 1.0E-7 + deltaZ));
    }

    private static Vec3 collide(PlayerState s, Ctx c, Vec3 movement) {
        AABB aabb = s.box();
        Vec3 movementStep = movement.lengthSqr() == 0.0 ? movement : collideWithShapes(movement, aabb, collisions(c, aabb.expandTowards(movement)));
        boolean xCollision = movement.x != movementStep.x;
        boolean yCollision = movement.y != movementStep.y;
        boolean zCollision = movement.z != movementStep.z;
        boolean onGroundAfterCollision = yCollision && movement.y < 0.0;
        if (STEP_HEIGHT > 0.0F && (onGroundAfterCollision || s.onGround) && (xCollision || zCollision)) {
            AABB groundedAABB = onGroundAfterCollision ? aabb.move(0.0, movementStep.y, 0.0) : aabb;
            AABB stepUpAABB = groundedAABB.expandTowards(movement.x, STEP_HEIGHT, movement.z);
            if (!onGroundAfterCollision) stepUpAABB = stepUpAABB.expandTowards(0.0, -1.0E-5F, 0.0);
            List<VoxelShape> colliders = collisions(c, stepUpAABB);
            float stepHeightToSkip = (float) movementStep.y;
            float[] candidates = candidateStepUpHeights(groundedAABB, colliders, STEP_HEIGHT, stepHeightToSkip);
            for (float candidate : candidates) {
                Vec3 stepFromGround = collideWithShapes(new Vec3(movement.x, candidate, movement.z), groundedAABB, colliders);
                if (stepFromGround.horizontalDistanceSqr() > movementStep.horizontalDistanceSqr()) {
                    double distanceToGround = aabb.minY - groundedAABB.minY;
                    return stepFromGround.subtract(0.0, distanceToGround, 0.0);
                }
            }
        }
        return movementStep;
    }

    private static float[] candidateStepUpHeights(AABB box, List<VoxelShape> colliders, float maxStepHeight, float stepHeightToSkip) {
        FloatSet candidates = new FloatArraySet(4);
        for (VoxelShape collider : colliders) {
            for (double coord : collider.getCoords(Direction.Axis.Y)) {
                float relative = (float) (coord - box.minY);
                if (!(relative < 0.0F) && relative != stepHeightToSkip) {
                    if (relative > maxStepHeight) break;
                    candidates.add(relative);
                }
            }
        }
        float[] sorted = candidates.toFloatArray();
        FloatArrays.unstableSort(sorted);
        return sorted;
    }

    private static Vec3 collideWithShapes(Vec3 movement, AABB box, List<VoxelShape> shapes) {
        if (shapes.isEmpty()) return movement;
        Vec3 resolved = Vec3.ZERO;
        for (Direction.Axis axis : Direction.axisStepOrder(movement)) {
            double axisMovement = movement.get(axis);
            if (axisMovement != 0.0) {
                double collision = Shapes.collide(axis, box.move(resolved), shapes, axisMovement);
                resolved = resolved.with(axis, collision);
            }
        }
        return resolved;
    }

    // ------------------------------------------------------------------ ground

    private static void checkSupportingBlock(PlayerState s, Ctx c, boolean onGround, Vec3 movement) {
        if (onGround) {
            AABB b = s.box();
            AABB testArea = new AABB(b.minX, b.minY - 1.0E-6, b.minZ, b.maxX, b.minY, b.maxZ);
            BlockPos supporting = findSupportingBlock(s, c, testArea);
            if (supporting != null || s.onGroundNoBlocks) {
                s.support = supporting;
            } else if (movement != null) {
                supporting = findSupportingBlock(s, c, testArea.move(-movement.x, 0.0, -movement.z));
                s.support = supporting;
            }
            s.onGroundNoBlocks = supporting == null;
        } else {
            s.onGroundNoBlocks = false;
            s.support = null;
        }
    }

    private static BlockPos findSupportingBlock(PlayerState s, Ctx c, AABB box) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : collidingPositions(c, box)) {
            double distance = pos.distToCenterSqr(s.x, s.y, s.z);
            if (distance < bestDistance || distance == bestDistance && (best == null || best.compareTo(pos) < 0)) {
                best = pos;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** {@code Entity.getOnPos(offset)}: the block under the player that an effect is read from. */
    static BlockPos onPos(PlayerState s, Ctx c, float offset) {
        if (s.support != null) {
            if (!(offset > 1.0E-5F)) return s.support;
            BlockState below = c.world.get(s.support);
            return !(offset <= 0.5) || !extendsVertically(below) ? s.support.atY(Mth.floor(s.y - offset)) : s.support;
        }
        return new BlockPos(Mth.floor(s.x), Mth.floor(s.y - offset), Mth.floor(s.z));
    }

    private static boolean extendsVertically(BlockState state) {
        Block block = state.getBlock();
        return state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || block instanceof FenceBlock || block instanceof WallBlock
            || block instanceof FenceGateBlock;
    }

    private static float blockJumpFactor(PlayerState s, Ctx c) {
        float here = c.world.get(BlockPos.containing(s.x, s.y, s.z)).getBlock().getJumpFactor();
        float below = c.world.get(onPos(s, c, 0.500001F)).getBlock().getJumpFactor();
        return here == 1.0 ? below : here;
    }

    private static float blockSpeedFactor(PlayerState s, Ctx c) {
        BlockState state = c.world.get(BlockPos.containing(s.x, s.y, s.z));
        float here = state.getBlock().getSpeedFactor();
        if (state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN)) return here;
        return here == 1.0 ? c.world.get(onPos(s, c, 0.500001F)).getBlock().getSpeedFactor() : here;
    }

    /** Whether the player at {@code s} is on a ladder, vine or other climbable block. */
    public static boolean climbing(PlayerState s, BlockSource world) {
        return onClimbable(s, new Ctx(world));
    }

    /** Whether {@code state} is a climbable block (by type too: tags aren't bound outside a running game). */
    public static boolean climbable(BlockState state) {
        Block block = state.getBlock();
        return state.is(BlockTags.CLIMBABLE) || block instanceof LadderBlock || block instanceof VineBlock || block instanceof ScaffoldingBlock
            || block == Blocks.TWISTING_VINES || block == Blocks.TWISTING_VINES_PLANT || block == Blocks.WEEPING_VINES
            || block == Blocks.WEEPING_VINES_PLANT || block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT;
    }

    static boolean onClimbable(PlayerState s, Ctx c) {
        BlockPos pos = BlockPos.containing(s.x, s.y, s.z);
        BlockState state = c.world.get(pos);
        Block block = state.getBlock();
        if (climbable(state)) return true;
        if (block instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN)) {
            BlockState below = c.world.get(pos.below());
            return below.getBlock() instanceof LadderBlock && below.getValue(LadderBlock.FACING) == state.getValue(TrapDoorBlock.FACING);
        }
        return false;
    }

    // ------------------------------------------------------------------ pose

    static AABB box(double x, double y, double z, boolean crouch) {
        float w = WIDTH / 2.0F;
        float h = crouch ? CROUCH_HEIGHT : HEIGHT;
        return new AABB(x - w, y, z - w, x + w, y + h, z + w);
    }

    private static boolean canFit(PlayerState s, Ctx c, boolean crouch) {
        return noCollision(c, box(s.x, s.y, s.z, crouch).deflate(1.0E-7));
    }

    // ------------------------------------------------------------------ block collisions

    /** {@code BlockCollisions}: the collision shapes of the blocks that touch {@code box}, placed in the world. */
    static List<VoxelShape> collisions(Ctx c, AABB box) {
        List<VoxelShape> out = new ArrayList<>();
        scan(c, box, (pos, shape) -> {
            out.add(shape);
            return false;
        });
        return out;
    }

    private static List<BlockPos> collidingPositions(Ctx c, AABB box) {
        List<BlockPos> out = new ArrayList<>();
        scan(c, box, (pos, shape) -> {
            out.add(pos.immutable());
            return false;
        });
        return out;
    }

    static boolean noCollision(Ctx c, AABB box) {
        boolean[] hit = {false};
        scan(c, box, (pos, shape) -> {
            hit[0] = true;
            return true;
        });
        return !hit[0];
    }

    private interface Hit {
        /** Returns true to stop scanning. */
        boolean accept(BlockPos.MutableBlockPos pos, VoxelShape shape);
    }

    private static void scan(Ctx c, AABB box, Hit hit) {
        int x0 = Mth.floor(box.minX - 1.0E-7) - 1;
        int x1 = Mth.floor(box.maxX + 1.0E-7) + 1;
        int y0 = Mth.floor(box.minY - 1.0E-7) - 1;
        int y1 = Mth.floor(box.maxY + 1.0E-7) + 1;
        int z0 = Mth.floor(box.minZ - 1.0E-7) - 1;
        int z1 = Mth.floor(box.maxZ + 1.0E-7) + 1;
        VoxelShape entityShape = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    int faces = (x == x0 || x == x1 ? 1 : 0) + (y == y0 || y == y1 ? 1 : 0) + (z == z0 || z == z1 ? 1 : 0);
                    if (faces == 3) continue;
                    pos.set(x, y, z);
                    BlockState state = c.world.get(x, y, z);
                    if (state.isAir()) continue;
                    if (faces == 1 && !state.hasLargeCollisionShape()) continue;
                    if (faces == 2 && !state.is(Blocks.MOVING_PISTON)) continue;
                    VoxelShape blockShape = c.shape(state, pos);
                    if (blockShape == Shapes.block()) {
                        if (box.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0) && hit.accept(pos, blockShape.move(pos))) return;
                    } else if (!blockShape.isEmpty()) {
                        VoxelShape shape = blockShape.move(pos);
                        if (entityShape == null) entityShape = Shapes.create(box);
                        if (Shapes.joinIsNotEmpty(shape, entityShape, BooleanOp.AND) && hit.accept(pos, shape)) return;
                    }
                }
            }
        }
    }

    /** Per-step context: the world and a {@link BlockGetter} view of it for block shape lookups. */
    static final class Ctx {
        final BlockSource world;
        final BlockGetter getter;

        Ctx(BlockSource world) {
            this.world = world;
            this.getter = new SourceGetter(world);
        }

        /** A block's collision shape; cached by the source when it can (shapes of a snapshot never change). */
        VoxelShape shape(BlockState state, BlockPos pos) {
            if (world instanceof BlockSource.ShapeCache cache) return cache.shape(state, pos, getter);
            return state.getCollisionShape(getter, pos, CollisionContext.empty());
        }
    }

    /** The smallest {@link BlockGetter} block shapes need. */
    private record SourceGetter(BlockSource source) implements BlockGetter {
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return source.get(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return source.get(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 4064;
        }

        @Override
        public int getMinY() {
            return -2032;
        }
    }
}
