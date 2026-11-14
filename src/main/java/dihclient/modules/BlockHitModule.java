package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.util.DihKeyMappingBridge;
import dihclient.util.DihRotationUtil;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * Blocks between hits. Since 1.9 swords cannot block, so this uses whatever can (a shield, or any item a server gives
 * the blocks-attacks component): it holds block while your swing recharges and lets go just before it is ready,
 * because nothing attacks while an item is in use.
 */
public final class BlockHitModule extends Module {
    private boolean holding;
    private int sinceAttack = Integer.MAX_VALUE;

    public BlockHitModule() {
        super("block-hit", "BlockHit", ModuleCategory.COMBAT, "Blocks with your shield between hits.");
        add(new DoubleSetting("unblock-at", "Unblock At", 0.85D, 0.3D, 1.0D, 0.05D)
            .group("General")
            .description("Lower the shield once the swing is this charged, ready for the next hit."));
        add(new ChoiceSetting("block-hand", "Block Hand", "Any", "Any", "Offhand", "Main Hand")
            .group("General")
            .description("Which hand's blocking item to raise: Any tries the main hand, then the offhand."));
        add(new BoolSetting("after-hits", "Only After Hits", true)
            .group("General")
            .description("Block only in the fight you started, for a while after each hit."));
        add(new IntSetting("after-hit-time", "After Hit Time", 30, 5, 200, 5).unit("ticks")
            .group("General")
            .description("How long after your last hit blocking stays allowed.")
            .visibleWhen(() -> bool("after-hits")));
        add(new BoolSetting("only-on-ground", "Only On Ground", false)
            .group("General")
            .description("Don't block in the air, where a raised shield costs you speed."));

        add(new ChoiceSetting("targets", "Targets", "Players", "Players", "Players + Hostile", "All Living")
            .group("Enemies")
            .description("What counts as an enemy worth blocking."));
        add(new DoubleSetting("range", "Range", 4.5D, 2.0D, 8.0D, 0.5D).unit("blocks")
            .group("Enemies")
            .description("Only block with an enemy this close."));
        add(new IntSetting("fov", "FOV", 360, 30, 360, 10).unit("deg")
            .group("Enemies")
            .description("Only enemies inside this cone in front of you; 360 counts everyone around."));
        add(new BoolSetting("only-targeted", "Only When Targeted", false)
            .group("Enemies")
            .description("Only block an enemy that is looking at you."));
        add(new IntSetting("targeted-angle", "Targeted Angle", 30, 5, 90, 5).unit("deg")
            .group("Enemies")
            .description("How far off you an enemy's look may point and still count as aiming at you.")
            .visibleWhen(() -> bool("only-targeted")));
        add(new BoolSetting("ignore-invisible", "Ignore Invisible", false)
            .group("Enemies")
            .description("Don't block for invisible enemies."));
    }

    @Override
    public void onDisable() {
        release();
        sinceAttack = Integer.MAX_VALUE;
    }

    @Override
    public void onGameLeft() {
        holding = false;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (packet instanceof ServerboundAttackPacket) sinceAttack = 0;
        return false;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.options == null) return;
        if (sinceAttack < Integer.MAX_VALUE) sinceAttack++;
        boolean want = MC.gui.screen() == null && canBlock(player, choice("block-hand")) && enemyNear(player)
            && (!bool("only-on-ground") || player.onGround())
            && (!bool("after-hits") || sinceAttack <= integer("after-hit-time"))
            && player.getAttackStrengthScale(0.0F) < decimal("unblock-at");
        if (want) {
            MC.options.keyUse.setDown(true);
            holding = true;
        } else {
            release();
        }
    }

    private void release() {
        if (!holding || MC.options == null) return;
        KeyMapping use = MC.options.keyUse;
        use.setDown(DihKeyMappingBridge.of(use).dih$isActuallyDown());
        holding = false;
    }

    /**
     * Use would raise a blocking item from the chosen hand: the main hand blocks, or it does nothing and the offhand
     * blocks.
     */
    private static boolean canBlock(LocalPlayer player, String hand) {
        ItemStack main = player.getMainHandItem();
        boolean mainBlocks = main.has(DataComponents.BLOCKS_ATTACKS);
        if ("Main Hand".equals(hand)) return mainBlocks;
        if (mainBlocks) return "Any".equals(hand);
        boolean mainUsable = !main.isEmpty() && main.getUseAnimation() != ItemUseAnimation.NONE;
        return !mainUsable && player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
    }

    private boolean enemyNear(LocalPlayer player) {
        double range = decimal("range");
        Iterable<? extends LivingEntity> candidates = "Players".equals(choice("targets"))
            ? MC.level.players()
            : MC.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range));
        for (LivingEntity other : candidates) {
            if (other == player || !other.isAlive() || other.isSpectator()) continue;
            if (!isEnemyType(other)) continue;
            if (bool("ignore-invisible") && other.isInvisible()) continue;
            if (DihAntiBot.suppress(other) || TeamsModule.isFriendOrTeam(other)) continue;
            if (other.distanceToSqr(player) > range * range) continue;
            if (!inFov(player, other)) continue;
            if (bool("only-targeted") && !aimsAt(other, player, integer("targeted-angle"))) continue;
            return true;
        }
        return false;
    }

    private boolean isEnemyType(LivingEntity entity) {
        return switch (choice("targets")) {
            case "All Living" -> true;
            case "Players + Hostile" -> entity instanceof Player || entity instanceof Enemy;
            default -> entity instanceof Player;
        };
    }

    private boolean inFov(LocalPlayer player, LivingEntity other) {
        int fov = integer("fov");
        if (fov >= 360) return true;
        DihRotationUtil.Rotation toward =
            DihRotationUtil.lookingAt(other.getBoundingBox().getCenter(), player.getEyePosition());
        return DihRotationUtil.angleTo(DihRotationUtil.playerRotation(player), toward) <= fov * 0.5F;
    }

    /** Whether {@code attacker}'s look points within {@code maxAngle} degrees of {@code victim}'s body. */
    private static boolean aimsAt(LivingEntity attacker, LivingEntity victim, double maxAngle) {
        Vec3 eyes = attacker.getEyePosition();
        Vec3 toVictim = victim.getBoundingBox().getCenter().subtract(eyes);
        return CombatFilters.angleBetween(attacker.getViewVector(1.0F), toVictim) <= maxAngle;
    }
}
