package baritone.acquire.exec;

import baritone.acquire.model.Step;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link Step.Kill}: hunt the nearest mob of the step's type, hit it with a full attack cooldown, then
 * walk over the drops near where it died. Only that mob type; never players, babies (they drop
 * nothing), named mobs or tamed pets.
 */
final class KillRunner extends RunnerBase {

    private enum State { SEEK, FIGHT, LOOT }

    /** Give up when no target shows up for this long. */
    private static final int NO_TARGET_TICKS = 200;
    private static final int LOOT_TICKS = 100;
    /** Drops appear a tick or two after the kill; stop looting sooner if there are none. */
    private static final int NO_DROP_TICKS = 20;
    private static final double LOOT_RADIUS_SQ = 6 * 6;
    private static final double MAX_TARGET_DISTANCE_SQ = 64 * 64;

    private final Step.Kill step;
    private final EntityType<?> type;
    private final Item drop;
    private final Set<Integer> skipped = new HashSet<>();
    private State state = State.SEEK;
    private LivingEntity target;
    private Vec3 killSpot;
    private int ticks;
    private int lookTicks;

    KillRunner(ExecContext x, Step.Kill step) {
        super(x);
        this.step = step;
        Identifier key = Identifier.tryParse(step.entity());
        this.type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        this.drop = InventoryReader.itemOf(step.item());
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        if (type == null || "minecraft:player".equals(step.entity())) return Result.failed("can't hunt " + step.entity());
        if (x.have(step.item()) >= step.untilCount()) return Result.done();
        Result full = x.checkRoom(step.item());
        if (full != null) return full;
        LocalPlayer player = ctx.player();
        for (int guard = 0; guard < 4; guard++) {
            switch (state) {
                case SEEK -> {
                    target = nearestTarget();
                    if (target == null) {
                        if (++ticks > NO_TARGET_TICKS) return Result.failed("no " + Step.shortId(step.entity()) + " nearby");
                        return Result.pause();
                    }
                    ticks = 0;
                    lookTicks = 0;
                    selectWeapon();
                    state = State.FIGHT;
                }
                case FIGHT -> {
                    if (target.isDeadOrDying()) {
                        killSpot = target.position();
                        state = State.LOOT;
                        ticks = 0;
                        continue;
                    }
                    if (target.isRemoved() || calcFailed) {
                        if (calcFailed) skipped.add(target.getId());
                        state = State.SEEK;
                        return Result.pause();
                    }
                    if (player.isWithinEntityInteractionRange(target, -0.25D)) {
                        x.lookAt(target.getBoundingBox().getCenter(), false);
                        if (++lookTicks >= 2 && player.getAttackStrengthScale(0.5F) >= 0.95F) {
                            ctx.minecraft().gameMode.attack(player, target);
                            player.swing(InteractionHand.MAIN_HAND);
                        }
                        return Result.pause();
                    }
                    lookTicks = 0;
                    return follow(new GoalNear(target.blockPosition(), 1));
                }
                case LOOT -> {
                    ItemEntity item = nearestDrop();
                    ticks++;
                    if (item == null && ticks > NO_DROP_TICKS || ticks > LOOT_TICKS || calcFailed) {
                        state = State.SEEK;
                        ticks = 0;
                        continue;
                    }
                    if (item == null) return Result.pause();
                    return follow(new GoalBlock(item.blockPosition()));
                }
            }
        }
        return Result.pause();
    }

    @Override
    public void cancel() {
    }

    private LivingEntity nearestTarget() {
        LocalPlayer player = ctx.player();
        return ctx.entitiesStream()
                .filter(e -> e.getType() == type && e instanceof LivingEntity && huntable((LivingEntity) e))
                .filter(e -> !skipped.contains(e.getId()) && e.distanceToSqr(player) <= MAX_TARGET_DISTANCE_SQ)
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .map(e -> (LivingEntity) e)
                .orElse(null);
    }

    static boolean huntable(LivingEntity entity) {
        return entity.isAlive()
                && !(entity instanceof Player)
                && !entity.isBaby()
                && !entity.hasCustomName()
                && !(entity instanceof TamableAnimal pet && pet.isTame());
    }

    private ItemEntity nearestDrop() {
        if (drop == null || killSpot == null) return null;
        LocalPlayer player = ctx.player();
        return ctx.entitiesStream()
                .filter(e -> e instanceof ItemEntity item && item.isAlive() && item.getItem().is(drop))
                .filter(e -> e.position().distanceToSqr(killSpot) <= LOOT_RADIUS_SQ)
                .min(Comparator.comparingDouble((Entity e) -> e.distanceToSqr(player)))
                .map(e -> (ItemEntity) e)
                .orElse(null);
    }

    /** Holds a sword, else an axe, if one is already on the hotbar. Never shuffles the inventory for it. */
    private void selectWeapon() {
        List<ItemStack> main = ctx.player().getInventory().getNonEquipmentItems();
        int axe = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = main.get(i);
            if (stack.is(ItemTags.SWORDS)) {
                ctx.player().getInventory().setSelectedSlot(i);
                return;
            }
            if (axe < 0 && stack.is(ItemTags.AXES)) axe = i;
        }
        if (axe >= 0) ctx.player().getInventory().setSelectedSlot(axe);
    }
}
