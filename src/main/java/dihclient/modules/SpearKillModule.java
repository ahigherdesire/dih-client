package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.util.DihRotationUtil;
import dihclient.util.DihServerRotationView;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

/**
 * Makes a charging spear hit with a full-speed charge. A charge's damage grows with how fast the holder closes on the
 * target, and the server takes that speed from the holder's last move packet.
 * <ul>
 *   <li><b>Poke</b> leaves the player where they are: each tick a target sits inside the spear's reach, it tells the
 *   server the player just stepped {@code boost} blocks toward it (see {@link DihVelocitySpoof}). 10 blocks a tick is
 *   200 blocks a second, which an iron spear turns into about 180 damage per hit.</li>
 *   <li><b>Rush</b> really carries the player toward the target at {@code speed} blocks a tick for as long as the
 *   charge can still deal damage, stopping inside the spear's reach rather than running past. The server lets one
 *   move cover about 10 blocks.</li>
 * </ul>
 */
public final class SpearKillModule extends Module {
    private int targetId = -1;

    public SpearKillModule() {
        super("spear-kill", "SpearKill", ModuleCategory.COMBAT,
            "Makes a charging spear hit at full speed: Poke from where you stand, or Rush the target. Will flag anticheats.");
        add(new ChoiceSetting("mode", "Mode", "Poke", "Poke", "Rush")
            .group("General")
            .description("Poke: huge hits at your normal speed. Rush: really fly at the target."));
        add(new BoolSetting("face", "Face Target", true)
            .group("General")
            .description("Turn toward the target: the charge only hits what it points at."));

        add(new DoubleSetting("boost", "Boost", 9.5D, 1.0D, 22.0D, 0.5D)
            .group("Poke")
            .description("Blocks a tick the server thinks you charge at. Above 9.5 takes more packets and can miss "
                + "on a laggy server.")
            .visibleWhen(() -> poke()));
        add(new DoubleSetting("speed", "Speed", 3.0D, 0.5D, 9.5D, 0.5D)
            .group("Rush")
            .description("Blocks a tick to rush at the target.")
            .visibleWhen(() -> !poke()));
        add(new DoubleSetting("stop-at", "Stop At", 1.5D, 0.5D, 3.0D, 0.25D)
            .group("Rush")
            .description("Blocks short of the target to stop, inside the spear's reach.")
            .visibleWhen(() -> !poke()));

        add(new ChoiceSetting("targets", "Targets", "All Living", "Players", "Players + Hostile", "All Living")
            .group("Targets")
            .description("What the charge may be aimed at."));
        add(new DoubleSetting("range", "Range", 12.0D, 2.0D, 32.0D, 1.0D)
            .group("Targets")
            .description("How far away a target can be picked."));
        add(new IntSetting("fov", "FOV", 90, 10, 360, 5)
            .group("Targets")
            .description("How far off the crosshair a target can be picked."));
        add(new ChoiceSetting("priority", "Priority", "Crosshair", "Crosshair", "Distance", "Health")
            .group("Targets")
            .description("Which target wins when several are in the cone."));
        add(new BoolSetting("crosshair-first", "Crosshair First", true)
            .group("Targets")
            .description("Take the entity under your crosshair before anything else."));
        add(new BoolSetting("lock-target", "Lock Target", true)
            .group("Targets")
            .description("Keep charging the same target while it stays valid."));
        add(new BoolSetting("need-sight", "Need Line Of Sight", false)
            .group("Targets")
            .description("Skip targets behind blocks."));
        add(new BoolSetting("ignore-invisible", "Ignore Invisible", false)
            .group("Targets")
            .description("Skip invisible entities."));
        add(new BoolSetting("ignore-sleeping", "Ignore Sleeping", true)
            .group("Targets")
            .description("Skip players in bed."));
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
    public String info() {
        return choice("mode");
    }

    @Override
    public void onNetworkMovementTickPost() {
        if (!poke() || targetId < 0 || MC.level == null) return;
        LocalPlayer player = MC.player;
        if (!DihVelocitySpoof.usable(player) || !charging(player)) return;
        if (!(MC.level.getEntity(targetId) instanceof LivingEntity target)) return;
        // The server reads the look this tick's movement packet carried; the step has to go the same way.
        DihServerRotationView.WireSnapshot wire = DihServerRotationView.snapshot();
        float yaw = wire.initialized() ? wire.currentYaw() : player.getYRot();
        float pitch = wire.initialized() ? wire.currentPitch() : player.getXRot();
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        if (!inReach(player, player.getUseItem(), target, look)) return;
        Vec3 dir = flat(yaw);
        double perPacket = DihVelocitySpoof.perPacket(player);
        double room = DihVelocitySpoof.clearDistance(player, dir.scale(-1.0D),
            DihVelocitySpoof.reach(DihVelocitySpoof.MAX_PACKETS, perPacket));
        DihVelocitySpoof.Plan plan = DihVelocitySpoof.returning(decimal("boost"), room, perPacket);
        if (plan.boost() < 1.0D) return;
        DihVelocitySpoof.send(player, player.position(), dir, yaw, pitch, plan, null);
    }

    @Override
    public Vec3 onPlayerMove(MoverType type, Vec3 movement) {
        if (poke() || type != MoverType.SELF || targetId < 0 || MC.player == null || MC.level == null) return movement;
        if (!(MC.level.getEntity(targetId) instanceof LivingEntity target) || !charging(MC.player)) return movement;
        Vec3 rush = rush(MC.player.position(), target.position(), decimal("speed"),
            decimal("stop-at") + target.getBbWidth() * 0.5D);
        if (rush == Vec3.ZERO) return movement;
        return new Vec3(rush.x, movement.y, rush.z);
    }

    private boolean poke() {
        return "Poke".equals(choice("mode"));
    }

    /**
     * Whether the charge's hit ray would find {@code target}: inside the weapon's reach and on the line the server
     * looks along.
     */
    private static boolean inReach(LocalPlayer player, ItemStack stack, LivingEntity target, Vec3 look) {
        AttackRange range = player.getAttackRangeWith(stack);
        Vec3 eyes = player.getEyePosition();
        double reach = range.effectiveMaxRange(player);
        AABB box = target.getBoundingBox().inflate(range.hitboxMargin());
        return box.contains(eyes) || box.clip(eyes, eyes.add(look.scale(reach))).isPresent();
    }

    /** The level unit vector facing {@code yaw}: the poke steps stay on the ground, so they never build fall damage. */
    static Vec3 flat(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vec3(-Math.sin(rad), 0.0D, Math.cos(rad));
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

    /** The locked target while it stays valid, else the crosshair's entity, else the best one in the cone. */
    private LivingEntity target(LocalPlayer player) {
        double range = decimal("range");
        if (bool("lock-target") && MC.level.getEntity(targetId) instanceof LivingEntity locked
            && valid(player, locked, range)) {
            return locked;
        }
        if (bool("crosshair-first") && MC.hitResult instanceof EntityHitResult hit
            && hit.getType() == HitResult.Type.ENTITY
            && hit.getEntity() instanceof LivingEntity looked && valid(player, looked, range)) {
            return looked;
        }
        Vec3 eyes = player.getEyePosition();
        DihRotationUtil.Rotation facing = DihRotationUtil.playerRotation(player);
        float halfFov = integer("fov") * 0.5F;
        Comparator<LivingEntity> byAngle = Comparator.comparingDouble(e -> DihRotationUtil.rotationAngleTo(facing,
            DihRotationUtil.lookingAt(e.getBoundingBox().getCenter(), eyes)));
        Comparator<LivingEntity> order = switch (choice("priority")) {
            case "Distance" -> Comparator.comparingDouble(player::distanceToSqr);
            case "Health" -> Comparator.comparingDouble(e -> e.getHealth() + e.getAbsorptionAmount());
            default -> byAngle;
        };
        return MC.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                e -> valid(player, e, range)
                    && DihRotationUtil.rotationAngleTo(facing,
                        DihRotationUtil.lookingAt(e.getBoundingBox().getCenter(), eyes)) <= halfFov)
            .stream()
            .min(order.thenComparing(byAngle))
            .orElse(null);
    }

    private boolean valid(LocalPlayer player, LivingEntity entity, double range) {
        if (entity == player || !entity.isAlive() || entity.isRemoved()) return false;
        if (!EntitySelector.CAN_BE_PICKED.test(entity) || entity.hasPassenger(player)) return false;
        if (entity instanceof Player other && other.isSpectator()) return false;
        if (bool("ignore-sleeping") && entity instanceof Player && entity.isSleeping()) return false;
        if (bool("ignore-invisible") && entity.isInvisible()) return false;
        if (!isTargetType(entity)) return false;
        if (DihAntiBot.suppress(entity) || TeamsModule.combatExcluded(entity, "killaura")) return false;
        if (player.distanceToSqr(entity) > range * range) return false;
        return !bool("need-sight") || player.hasLineOfSight(entity);
    }

    private boolean isTargetType(LivingEntity entity) {
        return switch (choice("targets")) {
            case "Players" -> entity instanceof Player;
            case "Players + Hostile" -> entity instanceof Player || entity instanceof Enemy;
            default -> true;
        };
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
