package dihclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Block-breaking arithmetic shared by Nuker and PacketMine. */
public final class DihBreakMath {
    private DihBreakMath() {
    }

    /**
     * Breaking progress per tick on {@code state} holding {@code stack}. Vanilla's own figure for the held item keeps
     * haste, fatigue, water and being off the ground in; it is then scaled by the tool speed (with Efficiency) and the
     * correct-tool divisor (30 instead of 100) of {@code stack} against the held item.
     */
    public static float progressPerTick(Minecraft mc, LocalPlayer player, BlockState state, BlockPos pos, ItemStack stack) {
        float held = state.getDestroyProgress(player, mc.level, pos);
        ItemStack heldStack = player.getMainHandItem();
        if (stack == null || ItemStack.matches(stack, heldStack)) return held;
        return scaled(held, toolSpeed(mc, heldStack, state), toolSpeed(mc, stack, state),
            correctTool(heldStack, state), correctTool(stack, state));
    }

    /** {@code heldProgress} for another tool: speed ratio times the ratio of the correct-tool divisors. */
    public static float scaled(float heldProgress, float heldSpeed, float speed, boolean heldCorrect, boolean correct) {
        if (heldProgress <= 0.0F || heldSpeed <= 0.0F) return heldProgress;
        float divisorRatio = (heldCorrect ? 30.0F : 100.0F) / (correct ? 30.0F : 100.0F);
        return heldProgress * (speed / heldSpeed) * divisorRatio;
    }

    private static float toolSpeed(Minecraft mc, ItemStack stack, BlockState state) {
        if (stack == null || stack.isEmpty()) return 1.0F;
        return Math.max(1.0F, DihAutoTool.destroySpeed(mc, stack, state));
    }

    private static boolean correctTool(ItemStack stack, BlockState state) {
        return !state.requiresCorrectToolForDrops() || stack != null && stack.isCorrectToolForDrops(state);
    }

    /** Squared distance from {@code eyes} to the nearest point of the block's outline. */
    public static double distanceSq(Minecraft mc, Vec3 eyes, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(mc.level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        double x = Math.max(box.minX, Math.min(eyes.x, box.maxX));
        double y = Math.max(box.minY, Math.min(eyes.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(eyes.z, box.maxZ));
        return eyes.distanceToSqr(x, y, z);
    }

    /** The face of the block that looks toward {@code eyes}. */
    public static Direction faceToward(Vec3 eyes, BlockPos pos) {
        return Direction.getApproximateNearest(eyes.subtract(Vec3.atCenterOf(pos)));
    }

    /** Yaw and pitch from {@code eyes} to the centre of {@code pos}. */
    public static float[] rotationTo(Vec3 eyes, BlockPos pos) {
        Vec3 delta = Vec3.atCenterOf(pos).subtract(eyes);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        return new float[] {yaw, pitch};
    }
}
