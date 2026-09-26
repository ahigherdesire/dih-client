package dihclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
//? if >=26.3 {
/*import net.minecraft.core.PositionAndRotation;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.item.component.SwingAnimation;
*///?}

/** Entity calls whose signature differs between Minecraft 26.2 and 26.3. */
public final class DihEntities {

    private DihEntities() {
    }

    /** Swings {@code entity}'s arm (animation only; packets go through {@link DihPackets}). */
    public static void swing(LivingEntity entity, InteractionHand hand) {
        //? if >=26.3 {
        /*entity.swing(hand, SwingAnimation.DEFAULT, false);
        *///?} else {
        entity.swing(hand);
        //?}
    }

    /** Drops the selected item (the whole stack when {@code fullStack}), as the drop key does. */
    public static void dropSelected(Minecraft mc, boolean fullStack) {
        if (mc.player == null || mc.gameMode == null) return;
        //? if >=26.3 {
        /*mc.gameMode.dropItem(mc.player, fullStack);
        *///?} else {
        mc.player.drop(fullStack);
        //?}
    }

    /** Ticks left of {@code entity}'s hurt cooldown. */
    public static int invulnerableTime(Entity entity) {
        //? if >=26.3 {
        /*return entity.getInvulnerableTime();
        *///?} else {
        return entity.invulnerableTime;
        //?}
    }

    /** Where {@code entity}'s client interpolation is heading (its position when there is no target). */
    public static Vec3 interpolationTarget(Entity entity) {
        //? if >=26.3 {
        /*PositionAndRotation target = entity.getInterpolation().target();
        return target != null ? target.position() : entity.position();
        *///?} else {
        return entity.getInterpolation().position();
        //?}
    }

    public static float interpolationYRot(Entity entity) {
        //? if >=26.3 {
        /*PositionAndRotation target = entity.getInterpolation().target();
        return target != null ? target.yRot() : entity.getYRot();
        *///?} else {
        return entity.getInterpolation().yRot();
        //?}
    }

    public static float interpolationXRot(Entity entity) {
        //? if >=26.3 {
        /*PositionAndRotation target = entity.getInterpolation().target();
        return target != null ? target.xRot() : entity.getXRot();
        *///?} else {
        return entity.getInterpolation().xRot();
        //?}
    }

    /** Interpolates {@code entity} to a pose; {@code steps} only applies on 26.2 (26.3 sets its own). */
    public static void interpolateTo(Entity entity, Vec3 position, float yRot, float xRot, int steps) {
        //? if >=26.3 {
        /*entity.getInterpolation().interpolateTo(PositionPath.of(position), yRot, xRot, false);
        *///?} else {
        entity.getInterpolation().setInterpolationLength(steps);
        entity.getInterpolation().interpolateTo(position, yRot, xRot);
        //?}
    }

    /** Back to vanilla's interpolation length (26.2; 26.3 has no length to reset). */
    public static void resetInterpolationLength(Entity entity) {
        //? if <26.3 {
        entity.getInterpolation().setInterpolationLength(net.minecraft.world.entity.InterpolationHandler.DEFAULT_INTERPOLATION_STEPS);
        //?}
    }
}
