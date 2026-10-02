package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.util.DihRotationUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * Rushes a charging spear into its target. A charge's damage grows with how fast the holder closes on the target, and
 * the server takes that speed from the holder's last moves, so the module carries the player toward the target at
 * {@code speed} blocks a tick for as long as the charge can still deal damage, stopping inside the spear's reach rather
 * than running past. The server lets one move cover about 10 blocks.
 */
public final class SpearKillModule extends Module {
    private int targetId = -1;

    public SpearKillModule() {
        super("spear-kill", "SpearKill", ModuleCategory.COMBAT,
            "Rushes a charging spear into its target for a full-speed hit. Will flag anticheats.");
        add(new DoubleSetting("speed", "Speed", 3.0D, 0.5D, 9.5D, 0.5D)
            .description("Blocks a tick to rush at the target."));
        add(new DoubleSetting("range", "Range", 12.0D, 2.0D, 32.0D, 1.0D)
            .description("How far away a target can be picked."));
        add(new IntSetting("fov", "FOV", 90, 10, 360, 5)
            .description("How far off the crosshair a target can be picked."));
        add(new DoubleSetting("stop-at", "Stop At", 1.5D, 0.5D, 3.0D, 0.25D)
            .description("Blocks short of the target to stop, inside the spear's reach."));
        add(new BoolSetting("face", "Face Target", true)
            .description("Turn toward the target: the charge only hits what it points at."));
    }

    @Override
    public void onDisable() {
        targetId = -1;
    }

    @Override
    public void preMovementTick() {
        LocalPlayer player = MC.player;
        LivingEntity target = charging(player) ? target(player) : null;
        targetId = target == null ? -1 : target.getId();
        if (target != null && bool("face")) {
            DihRotationUtil.apply(player,
                DihRotationUtil.lookingAt(target.getBoundingBox().getCenter(), player.getEyePosition()), false);
        }
    }

    @Override
    public Vec3 onPlayerMove(MoverType type, Vec3 movement) {
        if (type != MoverType.SELF || targetId < 0 || MC.player == null || MC.level == null) return movement;
        if (!(MC.level.getEntity(targetId) instanceof LivingEntity target) || !charging(MC.player)) return movement;
        Vec3 rush = rush(MC.player.position(), target.position(), decimal("speed"),
            decimal("stop-at") + target.getBbWidth() * 0.5D);
        if (rush == Vec3.ZERO) return movement;
        return new Vec3(rush.x, movement.y, rush.z);
    }

    /** Whether the player holds a spear charge that can still deal damage. */
    private static boolean charging(LocalPlayer player) {
        if (player == null || !player.isUsingItem() || player.isPassenger()) return false;
        ItemStack stack = player.getUseItem();
        KineticWeapon weapon = stack.get(DataComponents.KINETIC_WEAPON);
        if (weapon == null) return false;
        int maxDuration = weapon.damageConditions().map(KineticWeapon.Condition::maxDurationTicks).orElse(-1);
        return damaging(player.getTicksUsingItem(), weapon.delayTicks(), maxDuration);
    }

    /** The locked target while it stays valid, else the crosshair's entity, else the one nearest the crosshair. */
    private LivingEntity target(LocalPlayer player) {
        double range = decimal("range");
        if (MC.level.getEntity(targetId) instanceof LivingEntity locked && valid(player, locked, range)) return locked;
        if (MC.hitResult instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY
            && hit.getEntity() instanceof LivingEntity looked && valid(player, looked, range)) {
            return looked;
        }
        Vec3 eyes = player.getEyePosition();
        DihRotationUtil.Rotation facing = DihRotationUtil.playerRotation(player);
        float halfFov = integer("fov") * 0.5F;
        return MC.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                e -> valid(player, e, range)
                    && DihRotationUtil.rotationAngleTo(facing,
                        DihRotationUtil.lookingAt(e.getBoundingBox().getCenter(), eyes)) <= halfFov)
            .stream()
            .min(Comparator.comparingDouble(e -> DihRotationUtil.rotationAngleTo(facing,
                DihRotationUtil.lookingAt(e.getBoundingBox().getCenter(), eyes))))
            .orElse(null);
    }

    private static boolean valid(LocalPlayer player, LivingEntity entity, double range) {
        if (entity == player || !entity.isAlive() || entity.isRemoved()) return false;
        if (!EntitySelector.CAN_BE_PICKED.test(entity) || entity.hasPassenger(player)) return false;
        if (entity instanceof Player other && (other.isSpectator() || other.isSleeping())) return false;
        if (DihAntiBot.suppress(entity) || TeamsModule.combatExcluded(entity, "killaura")) return false;
        return player.distanceToSqr(entity) <= range * range;
    }

    /**
     * Whether a charge held {@code used} ticks deals damage: it starts after {@code delay} ticks and lasts
     * {@code maxDuration} more ({@code -1} for a weapon whose charge deals none).
     */
    static boolean damaging(int used, int delay, int maxDuration) {
        return maxDuration >= 0 && used >= delay && used - delay <= maxDuration;
    }

    /**
     * The flat move from {@code from} toward {@code to}: {@code speed} blocks, or less to stop {@code stopAt} short of
     * it, and none when already that close.
     */
    static Vec3 rush(Vec3 from, Vec3 to, double speed, double stopAt) {
        double dx = to.x - from.x, dz = to.z - from.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double step = Math.min(speed, distance - stopAt);
        if (step <= 1.0E-3D) return Vec3.ZERO;
        return new Vec3(dx / distance * step, 0.0D, dz / distance * step);
    }
}
