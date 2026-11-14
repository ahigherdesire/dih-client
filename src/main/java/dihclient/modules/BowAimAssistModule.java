package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Turns your view toward where an arrow (or crossbow bolt, or trident) has to go to hit the target in front of you:
 * it solves the arc for the projectile's speed, gravity and drag, leads a moving target by the flight time, and eases
 * the view there rather than snapping. Works only while you draw a bow, hold a loaded crossbow or ready a trident.
 */
public final class BowAimAssistModule extends Module {
    /** Most ticks of flight considered. */
    static final int MAX_TICKS = 200;

    private float targetYaw = Float.NaN;
    private float targetPitch = Float.NaN;
    private long lastFrameNanos;
    private int lockedTargetId = -1;

    public BowAimAssistModule() {
        super("bow-aim-assist", "BowAimAssist", ModuleCategory.COMBAT, "Aims bows, crossbows and tridents for you.");
        add(new ChoiceSetting("targets", "Targets", "Players", "Players", "Players + Hostile", "All Living")
            .group("General")
            .description("What to aim at."));
        add(new IntSetting("speed", "Speed", 40, 1, 100, 1).unit("%")
            .group("General")
            .description("How fast the view turns to the aim."));
        add(new ChoiceSetting("axis", "Axis", "Both", "Both", "Horizontal", "Vertical")
            .group("General")
            .description("Which way the view is turned: Horizontal leaves your pitch to you."));
        add(new BoolSetting("predict", "Predict Movement", true)
            .group("General")
            .description("Lead moving targets by the flight time."));

        add(new ChoiceSetting("aim-at", "Aim At", "Body", "Head", "Body", "Feet")
            .group("Aim")
            .description("Which part of the target the shot is aimed at."));
        add(new DoubleSetting("aim-offset", "Aim Offset", 0.0D, -0.5D, 0.5D, 0.05D)
            .group("Aim")
            .description("Moves the aim up or down, as a share of the target's height."));

        add(new BoolSetting("bows", "Bows", true)
            .group("Weapons")
            .description("Aim while drawing a bow."));
        add(new IntSetting("min-draw", "Min Draw", 0, 0, 100, 5).unit("%")
            .group("Weapons")
            .description("Start aiming once the bow is drawn this far.")
            .visibleWhen(() -> bool("bows")));
        add(new BoolSetting("crossbows", "Crossbows", true)
            .group("Weapons")
            .description("Aim while holding a loaded crossbow."));
        add(new BoolSetting("tridents", "Tridents", true)
            .group("Weapons")
            .description("Aim while readying a trident."));

        add(new IntSetting("fov", "FOV", 60, 5, 180, 5).unit("deg")
            .group("Targets")
            .description("Only targets this close to your crosshair."));
        add(new IntSetting("range", "Range", 80, 8, 160, 4).unit("blocks")
            .group("Targets")
            .description("Farthest target to aim at."));
        add(new ChoiceSetting("priority", "Priority", "Crosshair", "Crosshair", "Distance", "Health")
            .group("Targets")
            .description("Which target wins when several are in the cone."));
        add(new BoolSetting("lock-target", "Lock Target", false)
            .group("Targets")
            .description("Stay on the same target while it stays in range and in the cone."));
        add(new BoolSetting("need-sight", "Need Line Of Sight", false)
            .group("Targets")
            .description("Skip targets you can't see."));
        add(new BoolSetting("ignore-invisible", "Ignore Invisible", false)
            .group("Targets")
            .description("Skip invisible entities."));
        add(new BoolSetting("ignore-naked", "Ignore Naked", false)
            .group("Targets")
            .description("Skip players wearing no armor."));
    }

    @Override
    public void onDisable() {
        targetYaw = Float.NaN;
        lockedTargetId = -1;
    }

    @Override
    public void tick() {
        targetYaw = Float.NaN;
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.gui.screen() != null) return;
        ItemStack stack = aimingStack(player);
        if (stack == null) return;
        List<TrajectorySim.Shot> shots = TrajectorySim.shotsFor(player, stack, false, false);
        if (shots.isEmpty()) return;
        TrajectorySim.Info info = shots.getFirst().info();
        Vec3 launch = new Vec3(player.getX(), player.getEyeY() - 0.1D, player.getZ());
        LivingEntity target = pickTarget(player);
        lockedTargetId = target == null ? -1 : target.getId();
        if (target == null) return;

        Vec3 relative = bool("predict")
            ? target.position().subtract(target.xOld, target.yOld, target.zOld).subtract(TrajectorySim.inheritedVelocity(player))
            : Vec3.ZERO;
        Vec3 aim = aimPoint(target);
        Solution solution = null;
        // Lead the target: solve, move the aim by the flight time, solve again.
        for (int i = 0; i < 3; i++) {
            Vec3 point = solution == null ? aim : aim.add(relative.scale(solution.ticks()));
            double dx = point.x - launch.x, dz = point.z - launch.z;
            Solution next = solve(Math.sqrt(dx * dx + dz * dz), point.y - launch.y,
                info.initialVelocity(), info.gravity(), info.drag());
            if (next == null) break;
            solution = next;
            if (!bool("predict")) break;
        }
        if (solution == null) return;
        Vec3 point = aim.add(relative.scale(bool("predict") ? solution.ticks() : 0));
        String axis = choice("axis");
        targetYaw = "Vertical".equals(axis) ? player.getYRot()
            : (float) Math.toDegrees(Math.atan2(point.z - launch.z, point.x - launch.x)) - 90.0F;
        targetPitch = "Horizontal".equals(axis) ? player.getXRot() : solution.pitch();
    }

    private ItemStack aimingStack(LocalPlayer player) {
        if (player.isUsingItem()) {
            ItemStack using = player.getUseItem();
            if (using.getItem() instanceof BowItem && bool("bows")
                && BowItem.getPowerForTime(player.getTicksUsingItem()) >= integer("min-draw") / 100.0F) {
                return using;
            }
            if (using.getItem() instanceof TridentItem && bool("tridents")) return using;
        }
        if (!bool("crossbows")) return null;
        for (ItemStack held : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            if (held.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(held)) return held;
        }
        return null;
    }

    private LivingEntity pickTarget(LocalPlayer player) {
        double range = integer("range");
        double fov = integer("fov") / 2.0D;
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        if (bool("lock-target") && MC.level.getEntity(lockedTargetId) instanceof LivingEntity locked
            && valid(player, locked) && coneAngle(locked, eyes, look, range) <= fov) {
            return locked;
        }
        String priority = choice("priority");
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : MC.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !valid(player, living)) continue;
            double angle = coneAngle(living, eyes, look, range);
            if (angle > fov) continue;
            double score = switch (priority) {
                case "Distance" -> living.getBoundingBox().getCenter().distanceTo(eyes);
                case "Health" -> living.getHealth() + living.getAbsorptionAmount();
                default -> angle;
            };
            if (score >= bestScore) continue;
            best = living;
            bestScore = score;
        }
        return best;
    }

    /** Degrees between the look and {@code target}, or infinity when it is out of range or too close. */
    private static double coneAngle(LivingEntity target, Vec3 eyes, Vec3 look, double range) {
        Vec3 to = target.getBoundingBox().getCenter().subtract(eyes);
        double distance = to.length();
        if (distance > range || distance < 1.0D) return Double.POSITIVE_INFINITY;
        return Math.toDegrees(Math.acos(Mth.clamp(to.normalize().dot(look), -1.0D, 1.0D)));
    }

    private boolean valid(LocalPlayer player, LivingEntity living) {
        if (living == player || !living.isAlive()) return false;
        if (!isTarget(living) || DihAntiBot.suppress(living) || TeamsModule.isFriendOrTeam(living)) return false;
        if (bool("ignore-invisible") && living.isInvisible()) return false;
        if (bool("ignore-naked") && living instanceof Player && CombatFilters.wearsNoArmor(living)) return false;
        return !bool("need-sight") || player.hasLineOfSight(living);
    }

    private boolean isTarget(Entity entity) {
        return switch (choice("targets")) {
            case "All Living" -> true;
            case "Players + Hostile" -> entity instanceof Player || entity instanceof Enemy;
            default -> entity instanceof Player;
        };
    }

    private Vec3 aimPoint(LivingEntity target) {
        double fraction = switch (choice("aim-at")) {
            case "Head" -> 0.85D;
            case "Feet" -> 0.15D;
            default -> 0.55D;
        };
        fraction = Mth.clamp(fraction + decimal("aim-offset"), 0.0D, 1.0D);
        var box = target.getBoundingBox();
        return new Vec3((box.minX + box.maxX) / 2.0D, box.minY + box.getYsize() * fraction, (box.minZ + box.maxZ) / 2.0D);
    }

    /** Vanilla pitch (negative is up) and flight ticks for a shot. */
    record Solution(float pitch, double ticks) {
    }

    /**
     * The flattest pitch whose arc passes {@code dy} above the launch point at {@code horizontal} blocks away, for a
     * projectile that moves, then slows by {@code drag} and falls by {@code gravity} each tick; null when out of reach.
     */
    static Solution solve(double horizontal, double dy, double speed, double gravity, double drag) {
        double previous = Double.NaN;
        for (int angle = -80; angle <= 80; angle++) {
            double[] at = heightAt(angle, horizontal, speed, gravity, drag);
            if (at == null) {
                previous = Double.NaN;
                continue;
            }
            if (at[0] >= dy) {
                if (Double.isNaN(previous)) return new Solution(-angle, at[1]);
                // Refine between the last angle below the target and this one.
                double low = angle - 1, high = angle;
                double[] best = at;
                for (int i = 0; i < 20; i++) {
                    double mid = (low + high) / 2.0D;
                    double[] m = heightAt(mid, horizontal, speed, gravity, drag);
                    if (m != null && m[0] >= dy) {
                        high = mid;
                        best = m;
                    } else {
                        low = mid;
                    }
                }
                return new Solution((float) -high, best[1]);
            }
            previous = at[0];
        }
        return null;
    }

    /** Height and tick at which a shot at {@code angle} degrees above level reaches {@code horizontal}, or null. */
    static double[] heightAt(double angle, double horizontal, double speed, double gravity, double drag) {
        double rad = Math.toRadians(angle);
        double vx = speed * Math.cos(rad), vy = speed * Math.sin(rad);
        double x = 0.0D, y = 0.0D;
        for (int tick = 1; tick <= MAX_TICKS; tick++) {
            double nx = x + vx, ny = y + vy;
            if (nx >= horizontal) {
                double t = (horizontal - x) / Math.max(1.0E-9D, nx - x);
                return new double[] {y + (ny - y) * t, tick - 1 + t};
            }
            x = nx;
            y = ny;
            vx *= drag;
            vy = vy * drag - gravity;
            if (vx < 1.0E-4D) return null;
        }
        return null;
    }

    @Override
    public void onRenderLevel(float partialTick) {
        LocalPlayer player = MC.player;
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.0D : Math.min(0.1D, (now - lastFrameNanos) / 1.0E9D);
        lastFrameNanos = now;
        if (player == null || Float.isNaN(targetYaw) || dt <= 0.0D) return;
        // Ease toward the aim: a set share of the remaining angle per second, the same at any frame rate.
        double share = 1.0D - Math.exp(-dt * integer("speed") / 10.0D);
        float yawDiff = Mth.wrapDegrees(targetYaw - player.getYRot());
        float pitchDiff = targetPitch - player.getXRot();
        player.setYRot(player.getYRot() + (float) (yawDiff * share));
        player.setXRot(Mth.clamp(player.getXRot() + (float) (pitchDiff * share), -90.0F, 90.0F));
    }
}
