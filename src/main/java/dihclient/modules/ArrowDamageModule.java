package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Fires bow arrows at sniper speed. An arrow leaves with the shooter's speed added to its own and deals damage in
 * proportion to how fast it flies, so as a shot is released the module tells the server the player is moving
 * {@code boost} blocks a tick along the aim (see {@link DihVelocitySpoof}), then steps straight back. A full-draw
 * arrow normally flies 3 blocks a tick; at the default boost it flies 22 and hits about seven times as hard, and
 * flies almost straight, so aim at the target itself rather than above it.
 *
 * <p>Crossbows aim their bolts without the shooter's speed, so they are left alone.
 */
public final class ArrowDamageModule extends Module {
    /** Highest boost: back and forward at the five-packet allowance. */
    static final double MAX_BOOST = 42.0D;
    /** Below this much free room a shot goes out unboosted. */
    static final double MIN_BOOST = 1.0D;

    private int planTick = Integer.MIN_VALUE;
    private double plannedBoost;

    public ArrowDamageModule() {
        super("arrow-damage", "ArrowDamage", ModuleCategory.COMBAT,
            "Fires arrows at sniper speed so they one-shot players and mobs. Will flag anticheats.");
        add(new DoubleSetting("boost", "Boost", 19.0D, 2.0D, MAX_BOOST, 1.0D)
            .group("General")
            .description("Blocks a tick added to each arrow (a full draw flies 3). Above 19 takes more packets and "
                + "can rubber-band you on a laggy server."));
        add(new DoubleSetting("min-boost", "Min Boost", MIN_BOOST, MIN_BOOST, 20.0D, 1.0D)
            .group("General")
            .description("Send the shot unboosted when the free room around you allows less than this."));
        add(new BoolSetting("full-draw", "Full Draw Only", true)
            .group("General")
            .description("Only boost arrows from a fully drawn bow."));
        add(new IntSetting("min-draw", "Min Draw", 0, 0, 100, 5).unit("%")
            .group("General")
            .description("Only boost arrows drawn at least this far.")
            .visibleWhen(() -> !bool("full-draw")));

        add(new BoolSetting("only-at-targets", "Only At Targets", false)
            .group("Targets")
            .description("Only boost shots aimed at a target, so stray shots don't send the burst."));
        add(new ChoiceSetting("targets", "Targets", "Players", "Players", "Players + Hostile", "All Living")
            .group("Targets")
            .description("What counts as a target.")
            .visibleWhen(() -> bool("only-at-targets")));
        add(new IntSetting("target-angle", "Target Angle", 10, 1, 45, 1).unit("deg")
            .group("Targets")
            .description("How far off your aim a target may be.")
            .visibleWhen(() -> bool("only-at-targets")));
        add(new IntSetting("target-range", "Target Range", 120, 8, 256, 8).unit("blocks")
            .group("Targets")
            .description("How far away a target may be.")
            .visibleWhen(() -> bool("only-at-targets")));
    }

    @Override
    public String info() {
        return String.valueOf((int) decimal("boost"));
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (!(packet instanceof ServerboundPlayerActionPacket action)
            || action.getAction() != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) return false;
        LocalPlayer player = MC.player;
        if (!drawingBow(player)) return false;
        Vec3 dir = aim(player);
        if (!aimedAtTarget(player, dir)) return false;
        DihVelocitySpoof.Plan plan = plan(player, dir);
        if (plan.boost() < minBoost()) return false;
        DihVelocitySpoof.send(player, player.position(), dir, player.getYRot(), player.getXRot(), plan, packet);
        return true;
    }

    /**
     * The extra speed, in blocks a tick along the aim, the next arrow will get: for trajectory previews and aim
     * assist. Zero while the module is off or the player isn't drawing a bow.
     */
    public static double expectedBoost(LocalPlayer player) {
        if (!(ModuleRegistry.get("arrow-damage") instanceof ArrowDamageModule module) || !module.isEnabled()) return 0.0D;
        if (!module.drawingBow(player)) return 0.0D;
        if (module.planTick != player.tickCount) {
            module.planTick = player.tickCount;
            Vec3 dir = aim(player);
            DihVelocitySpoof.Plan plan = module.aimedAtTarget(player, dir) ? module.plan(player, dir) : null;
            module.plannedBoost = plan == null || plan.boost() < module.minBoost() ? 0.0D : plan.boost();
        }
        return module.plannedBoost;
    }

    private boolean drawingBow(LocalPlayer player) {
        if (!DihVelocitySpoof.usable(player) || !player.isUsingItem()) return false;
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof BowItem)) return false;
        float power = BowItem.getPowerForTime(player.getTicksUsingItem());
        return bool("full-draw") ? power >= 1.0F : power >= integer("min-draw") / 100.0F;
    }

    private double minBoost() {
        return Math.max(MIN_BOOST, decimal("min-boost"));
    }

    /** With Only At Targets on, whether a target sits within the set angle of the aim and in range. */
    private boolean aimedAtTarget(LocalPlayer player, Vec3 dir) {
        if (!bool("only-at-targets")) return true;
        if (MC.level == null) return false;
        double range = integer("target-range");
        double maxAngle = integer("target-angle");
        Vec3 eyes = player.getEyePosition();
        for (Entity entity : MC.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == player || !living.isAlive()) continue;
            if (!isTarget(living) || DihAntiBot.suppress(living) || TeamsModule.isFriendOrTeam(living)) continue;
            Vec3 to = living.getBoundingBox().getCenter().subtract(eyes);
            if (to.lengthSqr() > range * range) continue;
            if (CombatFilters.angleBetween(dir, to) <= maxAngle) return true;
        }
        return false;
    }

    private boolean isTarget(LivingEntity entity) {
        return switch (choice("targets")) {
            case "All Living" -> true;
            case "Players + Hostile" -> entity instanceof Player || entity instanceof Enemy;
            default -> entity instanceof Player;
        };
    }

    private DihVelocitySpoof.Plan plan(LocalPlayer player, Vec3 dir) {
        double boost = decimal("boost");
        double perPacket = DihVelocitySpoof.perPacket(player);
        double cap = DihVelocitySpoof.reach(DihVelocitySpoof.MAX_PACKETS, perPacket);
        return DihVelocitySpoof.through(boost,
            DihVelocitySpoof.clearDistance(player, dir.scale(-1.0D), cap),
            DihVelocitySpoof.clearDistance(player, dir, cap),
            perPacket);
    }

    /** The way the arrow will fly: the burst sends the view's rotation, and the server shoots along it. */
    private static Vec3 aim(LocalPlayer player) {
        return Vec3.directionFromRotation(player.getXRot(), player.getYRot());
    }
}
