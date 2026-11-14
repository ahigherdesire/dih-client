package baritone.combat;

import dihclient.util.DihEntities;
import baritone.Baritone;
import baritone.acquire.exec.InventoryOps;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.combat.CombatTactics.Foe;
import baritone.combat.CombatTactics.Move;
import baritone.combat.CombatTactics.Situation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fights one mob, a tick at a time: reads the fight into a {@link Situation}, asks {@link CombatTactics} for the
 * move, and carries it out with normal inputs (look, attack when the cooldown is full, jump, hold the use key for the
 * shield or bow, sprint). Used by {@code #acquire}'s kill steps and by the Guardian, which share one instance per
 * player so the keys it holds are never left down by the other.
 *
 * <p>{@link #tick} returns a goal when the move is a walk (Baritone paths it) and null when the move is made with
 * inputs this tick (the caller pauses pathing). {@link #release} lets go of every key it holds; callers call it when
 * they stop fighting.
 */
public final class CombatRunner {

    private static final double BACK_OFF_DISTANCE = 7;
    /** Aim is good enough to loose once the look is this close to the solved angles (degrees). */
    private static final float AIM_TOLERANCE = 1.5f;
    /** Shots flying at the player from this close get the shield. */
    public static final double SHOT_RADIUS = 8;

    private static final Map<Baritone, CombatRunner> RUNNERS = new WeakHashMap<>();

    private final Baritone baritone;
    private final IPlayerContext ctx;
    private boolean holdingUse;
    private boolean forcingMove;
    private boolean jumping;
    private Move last;
    private int lookTicks;
    private int shots;
    /** The game time of the last {@link #tick}. */
    private long lastFight = -100;
    /** The shot flying at the player this tick, or null. */
    private Projectile shot;

    private CombatRunner(Baritone baritone) {
        this.baritone = baritone;
        this.ctx = baritone.getPlayerContext();
    }

    /** The one runner for this player. */
    public static synchronized CombatRunner of(Baritone baritone) {
        return RUNNERS.computeIfAbsent(baritone, CombatRunner::new);
    }

    /** The last move made, for status lines; null before the first. */
    public Move lastMove() {
        return last;
    }

    /** Arrows loosed since the game started. */
    public int shots() {
        return shots;
    }

    public static Foe foeOf(Entity e) {
        if (e instanceof Creeper) return Foe.CREEPER;
        if (e instanceof EnderMan) return Foe.ENDERMAN;
        if (e instanceof Blaze || e instanceof Ghast) return Foe.BLAZE;
        if (e instanceof RangedAttackMob) return Foe.RANGED;
        return Foe.MELEE;
    }

    /** Whether a fight is under way: {@link #tick} ran this tick or the one before. */
    public boolean fighting() {
        Level level = ctx.world();
        return level != null && level.getGameTime() - lastFight <= 2;
    }

    /** One tick against {@code target}: a goal to path toward, or null when this tick's move is made by hand. */
    public Goal tick(LivingEntity target) {
        LocalPlayer player = ctx.player();
        lastFight = ctx.world().getGameTime();
        shot = incomingShot(ctx, SHOT_RADIUS);
        Foe foe = foeOf(target);
        ItemStack bow = bow(player);
        boolean hasBow = bow != null && !player.getProjectile(bow).isEmpty();
        int drawTicks = player.isUsingItem() && player.getUseItem().is(Items.BOW) ? player.getTicksUsingItem() : 0;
        BowMath.Aim aim = hasBow && foe != Foe.ENDERMAN ? aimAt(player, target) : null;
        boolean sight = player.hasLineOfSight(target);
        Situation s = Situation.builder(foe)
                .distance(player.distanceTo(target))
                .heightAbove(target.getY() - player.getY())
                .inReach(player.isWithinEntityInteractionRange(target, -0.25D) && sight)
                .lineOfSight(sight)
                .cooldown(player.getAttackStrengthScale(0.5F))
                .onGround(player.onGround())
                .falling(!player.onGround() && player.getDeltaMovement().y < 0)
                .canJump(headroom(player))
                .inWater(player.isInWater() || player.isInLava())
                .hasShield(player.getOffhandItem().is(Items.SHIELD))
                .hasBow(hasBow)
                .drawTicks(drawTicks)
                .aimSolved(aim != null && aimedAt(player, aim))
                .foeCharging(charging(target))
                .clearRun(clearRun(ctx.world(), player.position(), target.position()))
                .shotIncoming(shot != null)
                .build();
        Move move = CombatTactics.decide(s);
        if (move != last) lookTicks = 0;
        last = move;
        return apply(player, target, move, aim);
    }

    /** Lets go of every key the fight holds. */
    public void release() {
        releaseUse();
        releaseMove();
        releaseJump();
        lookTicks = 0;
        last = null;
    }

    private Goal apply(LocalPlayer player, LivingEntity target, Move move, BowMath.Aim aim) {
        releaseJump();
        if (move != Move.CHARGE && move != Move.SHIELD_APPROACH) releaseMove();
        if (move != Move.SHIELD && move != Move.SHIELD_APPROACH && move != Move.DRAW) releaseUse();
        Vec3 centre = target.getBoundingBox().getCenter();
        switch (move) {
            case STRIKE -> {
                selectWeapon(player);
                player.setSprinting(false);
                look(centre);
                if (++lookTicks >= 2 && player.getAttackStrengthScale(0.5F) >= CombatTactics.READY) {
                    ctx.minecraft().gameMode.attack(player, target);
                    DihEntities.swing(player, InteractionHand.MAIN_HAND);
                }
                return null;
            }
            case JUMP -> {
                selectWeapon(player);
                look(centre);
                baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                jumping = true;
                return null;
            }
            case WAIT -> {
                selectWeapon(player);
                look(centre);
                return null;
            }
            case SHIELD -> {
                if (!selectWeapon(player)) return null;
                look(shot != null ? shot.position() : target.getEyePosition());
                holdUse();
                return null;
            }
            case SHIELD_APPROACH -> {
                if (selectWeapon(player)) holdUse();
                return approach(player, target);
            }
            case CHARGE -> {
                selectWeapon(player);
                look(centre);
                forceMove();
                return null;
            }
            case APPROACH -> {
                selectWeapon(player);
                return approach(player, target);
            }
            case BACK_OFF -> {
                return new GoalRunAway(BACK_OFF_DISTANCE, target.blockPosition());
            }
            case DRAW -> {
                int slot = InventoryOps.toHotbar(ctx, stack -> stack.is(Items.BOW));
                if (slot < 0) return null;
                if (player.getInventory().getSelectedSlot() != slot) {
                    releaseUse();
                    player.getInventory().setSelectedSlot(slot);
                }
                if (aim != null) look(new Rotation(aim.yaw(), aim.pitch()));
                else look(centre);
                holdUse();
                return null;
            }
            case LOOSE -> {
                if (aim != null) look(new Rotation(aim.yaw(), aim.pitch()));
                Minecraft mc = ctx.minecraft();
                if (mc.options != null) mc.options.keyUse.setDown(false);
                if (player.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(player);
                holdingUse = false;
                shots++;
                return null;
            }
        }
        return null;
    }

    /**
     * Next to a walking mob, up on its ledge if need be; under a flying one that hovers out of reach. (Under a pig on a
     * ledge the goal was already reached with the pig still out of reach, and the fight stood there for good.)
     */
    private static Goal approach(LocalPlayer player, LivingEntity target) {
        if (foeOf(target) == Foe.BLAZE && target.getY() - player.getY() > CombatTactics.REACH_HEIGHT) {
            return new GoalXZ(target.getBlockX(), target.getBlockZ());
        }
        return new GoalNear(target.blockPosition(), 1);
    }

    /** The bow, aimed from where the arrow leaves (0.1 below the eye) at the middle of the mob's hitbox, with lead. */
    private static BowMath.Aim aimAt(LocalPlayer player, LivingEntity target) {
        Vec3 from = player.getEyePosition().subtract(0, 0.1, 0);
        Vec3 velocity = target.position().subtract(target.xo, target.yo, target.zo);
        return BowMath.aim(from, target.getBoundingBox().getCenter(), velocity, BowMath.FULL_SPEED);
    }

    private static boolean aimedAt(LocalPlayer player, BowMath.Aim aim) {
        float yaw = net.minecraft.util.Mth.wrapDegrees(player.getYRot() - aim.yaw());
        return Math.abs(yaw) <= AIM_TOLERANCE && Math.abs(player.getXRot() - aim.pitch()) <= AIM_TOLERANCE;
    }

    /** The nearest shot within {@code radius} flying at the player (not one of its own), or null. */
    public static Projectile incomingShot(IPlayerContext ctx, double radius) {
        LocalPlayer player = ctx.player();
        Projectile nearest = null;
        double best = radius;
        for (Entity e : ctx.entitiesStream().toList()) {
            if (!(e instanceof Projectile p) || !e.isAlive() || p.getOwner() == player) continue;
            double distance = e.distanceTo(player);
            if (distance <= best && incoming(p, player)) {
                nearest = p;
                best = distance;
            }
        }
        return nearest;
    }

    /** Moving, and headed at the middle of {@code player}. */
    public static boolean incoming(Projectile shot, LivingEntity player) {
        Vec3 velocity = shot.getDeltaMovement();
        if (velocity.lengthSqr() < 0.01) return false; // stuck in the ground
        Vec3 toPlayer = player.getBoundingBox().getCenter().subtract(shot.position());
        return velocity.normalize().dot(toPlayer.normalize()) > 0.8;
    }

    /** A creeper hissing, a skeleton drawing, a blaze lit up to fire or a ghast opening its mouth. */
    static boolean charging(LivingEntity target) {
        if (target instanceof Creeper creeper) return creeper.getSwellDir() > 0 || creeper.isIgnited();
        if (target instanceof Blaze blaze) return blaze.isOnFire();
        if (target instanceof Ghast ghast) return ghast.isCharging();
        if (target instanceof RangedAttackMob) return target.isUsingItem();
        return false;
    }

    /** Room overhead for a jump. */
    private boolean headroom(LocalPlayer player) {
        BlockPos above = player.blockPosition().above(2);
        return ctx.world().getBlockState(above).getCollisionShape(ctx.world(), above).isEmpty();
    }

    /** Solid ground, open air and no fluid, fire or magma every half block along the straight line. */
    public static boolean clearRun(Level level, Vec3 from, Vec3 to) {
        if (Math.abs(to.y - from.y) > 1.1) return false;
        double length = from.distanceTo(to);
        for (double t = 0.5; t < length; t += 0.5) {
            Vec3 at = from.lerp(to, t / length);
            BlockPos feet = BlockPos.containing(at.x, from.y + 0.1, at.z);
            if (!solid(level, feet.below()) || solid(level, feet) || solid(level, feet.above())) return false;
            if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty()) return false;
            BlockState ground = level.getBlockState(feet.below());
            if (ground.is(Blocks.MAGMA_BLOCK) || ground.is(BlockTags.FIRE) || level.getBlockState(feet).is(BlockTags.FIRE)) return false;
        }
        return true;
    }

    private static boolean solid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Holds the best sword, else the best axe, when one is in the inventory. True when the main hand now holds
     * something the use key won't use (so it raises the shield, not places a block or eats).
     */
    public boolean selectWeapon(LocalPlayer player) {
        int slot = InventoryOps.toHotbar(ctx, s -> s.is(ItemTags.SWORDS) && best(player, ItemTags.SWORDS) == s);
        if (slot < 0) slot = InventoryOps.toHotbar(ctx, s -> s.is(ItemTags.AXES) && best(player, ItemTags.AXES) == s);
        if (slot >= 0 && player.getInventory().getSelectedSlot() != slot) {
            releaseUse();
            player.getInventory().setSelectedSlot(slot);
        }
        ItemStack held = player.getMainHandItem();
        return held.isEmpty() || held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES);
    }

    /** The one of a tag with the most durability, a rough "best tier" (diamond > iron > stone > wood). */
    private static ItemStack best(LocalPlayer player, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        ItemStack best = null;
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (s.isEmpty() || !s.is(tag)) continue;
            if (best == null || s.getMaxDamage() > best.getMaxDamage()) best = s;
        }
        return best;
    }

    private static ItemStack bow(LocalPlayer player) {
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) if (s.is(Items.BOW)) return s;
        return null;
    }

    private void look(Vec3 point) {
        look(RotationUtils.calcRotationFromVec3d(ctx.playerHead(), point, ctx.playerRotations()));
    }

    private void look(Rotation rotation) {
        baritone.getLookBehavior().updateTarget(rotation, true);
    }

    private void holdUse() {
        Minecraft mc = ctx.minecraft();
        if (mc.options == null) return;
        mc.options.keyUse.setDown(true);
        holdingUse = true;
    }

    private void releaseUse() {
        if (!holdingUse) return;
        Minecraft mc = ctx.minecraft();
        if (mc.options != null) mc.options.keyUse.setDown(false);
        LocalPlayer player = ctx.player();
        if (player != null && player.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(player);
        holdingUse = false;
    }

    private void forceMove() {
        var input = baritone.getInputOverrideHandler();
        input.setInputForceState(Input.MOVE_FORWARD, true);
        input.setInputForceState(Input.SPRINT, true);
        forcingMove = true;
    }

    private void releaseMove() {
        if (!forcingMove) return;
        var input = baritone.getInputOverrideHandler();
        input.setInputForceState(Input.MOVE_FORWARD, false);
        input.setInputForceState(Input.SPRINT, false);
        forcingMove = false;
    }

    private void releaseJump() {
        if (!jumping) return;
        baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
        jumping = false;
    }
}
