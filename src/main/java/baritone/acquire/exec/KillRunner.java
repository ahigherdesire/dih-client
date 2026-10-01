package baritone.acquire.exec;

import baritone.acquire.model.Step;
import baritone.acquire.planner.AcquirePlanner;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import baritone.combat.CombatRunner;
import baritone.api.pathing.goals.Goal;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link Step.Kill}: hunt the nearest mob of the step's type, fight it with the {@link CombatRunner} (critical
 * hits, shield, bow, backing off a creeper), then walk over the drops near where it died. Only that mob type; never players, babies (they drop
 * nothing), named mobs or tamed pets. Endermen spawn thinly anywhere, so with none in view it goes looking: in the
 * Nether toward a warped forest, where they crowd, elsewhere straight on, turning when the way is blocked.
 */
final class KillRunner extends RunnerBase {

    private enum State { SEEK, FIGHT, LOOT }

    /** Give up when no target shows up for this long. */
    private static final int NO_TARGET_TICKS = 200;
    /** A fight not won in this long (a mob out of reach, a way that doesn't get there) moves on to another mob. */
    private static final int FIGHT_TICKS = 1200;
    private static final int LOOT_TICKS = 100;
    /** Drops appear a tick or two after the kill; stop looting sooner if there are none. */
    private static final int NO_DROP_TICKS = 20;
    private static final double LOOT_RADIUS_SQ = 6 * 6;
    private static final double MAX_TARGET_DISTANCE_SQ = 64 * 64;
    /** Mobs looked for when none is in view, rather than given up on. */
    private static final Set<String> ROAMING = Set.of("minecraft:enderman");
    /** Mobs that come out of spawners (blazes, in a fortress): with none in view, it waits by the nearest spawner. */
    private static final Set<String> SPAWNED = Set.of("minecraft:blaze");
    /** A spawner spawns every 10 to 40 seconds while a player is within 16 blocks: two slow spawns and a bit. */
    private static final int CAMP_TICKS = 1800;
    /** Close enough to keep it spawning, far enough not to stand in its fire. */
    private static final int CAMP_DISTANCE = 6;
    /** Legs walked through a fortress looking for a spawner before giving up. */
    private static final int EXPLORE_LEGS = 10;
    /** Longest leg (blocks): within the loaded chunks, and a fresh look around at every end. */
    private static final int EXPLORE_LEG = 40;
    /** Longest rest before a fight with a spawned mob, per stretch of being hurt. */
    private static final int REST_TICKS = 1200;
    /** A spawned mob this close that can see the player is shooting at it: no resting then. */
    private static final double SEEN_DISTANCE = 32;
    /** How long to look for a roaming mob before giving up. */
    private static final int ROAM_TICKS = 6000;
    /** A warped forest this far off or nearer counts as reached. */
    private static final int FOREST_REACHED = 12;

    private final Step.Kill step;
    private final EntityType<?> type;
    private final Item drop;
    private final Set<Integer> skipped = new HashSet<>();
    private final Set<BlockPos> badSpawners = new HashSet<>();
    private BlockPos spawner;
    /** Fortress floor walked to while looking for a spawner, the start among it. */
    private final java.util.List<BlockPos> walked = new java.util.ArrayList<>();
    private BlockPos walkTo;
    private int legs;
    private final CombatRunner combat;
    private State state = State.SEEK;
    private LivingEntity target;
    private Vec3 killSpot;
    private int ticks;
    private int rested;
    private Goal roamGoal;
    private float roamTurn;

    KillRunner(ExecContext x, Step.Kill step) {
        super(x);
        this.step = step;
        Identifier key = Identifier.tryParse(step.entity());
        this.type = key == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        this.drop = InventoryReader.itemOf(step.item());
        this.combat = CombatRunner.of(x.baritone);
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        if (type == null || AcquirePlanner.NEVER_KILL.contains(step.entity())) return Result.failed("can't hunt " + step.entity());
        if (x.have(step.item()) >= step.untilCount()) return Result.done();
        Result full = x.checkRoom(step.item());
        if (full != null) return full;
        for (int guard = 0; guard < 4; guard++) {
            switch (state) {
                case SEEK -> {
                    if (rest()) return Result.pause();
                    target = nearestTarget();
                    if (target == null) {
                        if (roams(step.entity())) {
                            if (++ticks > ROAM_TICKS) return Result.failed("no " + Step.shortId(step.entity()) + " found");
                            return roam(calcFailed);
                        }
                        if (SPAWNED.contains(step.entity())) {
                            Result camp = camp(calcFailed);
                            if (camp != null) return camp;
                        }
                        if (++ticks > NO_TARGET_TICKS) return Result.failed("no " + Step.shortId(step.entity()) + " nearby");
                        return Result.pause();
                    }
                    ticks = 0;
                    roamGoal = null;
                    state = State.FIGHT;
                }
                case FIGHT -> {
                    // Gone while the Guardian had the fight (it kills what it fights too): loot as after our own kill.
                    if (target.isDeadOrDying() || target.isRemoved() && !calcFailed) {
                        combat.release();
                        killSpot = target.position();
                        state = State.LOOT;
                        ticks = 0;
                        continue;
                    }
                    if (target.isRemoved() || calcFailed) {
                        combat.release();
                        if (calcFailed) skipped.add(target.getId());
                        state = State.SEEK;
                        return Result.pause();
                    }
                    if (++ticks > FIGHT_TICKS) {
                        combat.release();
                        skipped.add(target.getId());
                        state = State.SEEK;
                        ticks = 0;
                        return Result.pause();
                    }
                    Goal goal = combat.tick(target);
                    return goal == null ? Result.pause() : follow(goal);
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
        combat.release();
    }

    /**
     * Before going for a spawned mob (blazes at their spawner), waits while hurt and healing, up to
     * {@link #REST_TICKS}; the acquire's eating runs meanwhile, and the Guardian if one comes over. Never with one in
     * sight: a rest under its fire only takes the shots.
     */
    private boolean rest() {
        LocalPlayer player = ctx.player();
        if (!SPAWNED.contains(step.entity()) || seen(player) || !HealthPolicy.restBeforeFight(player.getHealth(),
                player.getFoodData().getFoodLevel(), FoodChoice.choose(Foods.held(player), player.getFoodData().getFoodLevel(),
                        false, Set.of()) != null)) {
            rested = 0;
            return false;
        }
        return ++rested <= REST_TICKS;
    }

    /** Whether a live mob of the step's type within {@link #SEEN_DISTANCE} has the player in its sight. */
    private boolean seen(LocalPlayer player) {
        return ctx.entitiesStream().anyMatch(e -> e.getType() == type && e instanceof net.minecraft.world.entity.Mob mob
                && mob.isAlive() && e.distanceToSqr(player) <= SEEN_DISTANCE * SEEN_DISTANCE && mob.hasLineOfSight(player));
    }

    /** Whether {@code entity} is looked for when none is in view. */
    static boolean roams(String entity) {
        return ROAMING.contains(entity);
    }

    /**
     * Goes to the nearest spawner (one with no way to it is skipped) and waits by it for the next mob; null with no
     * spawner in view.
     */
    private Result camp(boolean calcFailed) {
        if (calcFailed && spawner != null) {
            badSpawners.add(spawner);
            spawner = null;
        }
        if (spawner == null) {
            spawner = NearestBlock.find(ctx.world(), ctx.playerFeet(), Blocks.SPAWNER, 4, 48, 2, p -> !badSpawners.contains(p));
            if (spawner == null) return explore(calcFailed);
            walkTo = null;
            ticks = 0;
        }
        GoalNear near = new GoalNear(spawner, CAMP_DISTANCE);
        if (!near.isInGoal(ctx.playerFeet())) return walk(near);
        if (++ticks > CAMP_TICKS) return Result.failed("no " + Step.shortId(step.entity()) + " came out of the spawner at " + spawner.toShortString());
        return Result.pause();
    }

    /**
     * No spawner in view: walks the fortress to its far halls, each leg to the nether-brick floor furthest from where it
     * has been; null once {@link #EXPLORE_LEGS} legs are walked or no floor is left to go to.
     */
    private Result explore(boolean calcFailed) {
        BlockPos feet = ctx.playerFeet();
        if (walked.isEmpty()) walked.add(feet);
        if (walkTo != null && (calcFailed || walkTo.closerThan(feet, 3))) {
            walked.add(walkTo);
            walkTo = null;
        }
        if (walkTo == null) {
            if (legs >= EXPLORE_LEGS) return null;
            Level level = ctx.world();
            walkTo = NearestBlock.best(level, feet, Blocks.NETHER_BRICKS, 4, 16,
                    p -> FortressWalk.score(p, feet, walked, EXPLORE_LEG),
                    p -> level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir());
            if (walkTo == null) return null;
            walkTo = walkTo.above();
            legs++;
        }
        return walk(new GoalNear(walkTo, 2));
    }

    /** Walks on looking for the mob: to a warped forest in the Nether if one is in view, else straight on. */
    private Result roam(boolean calcFailed) {
        if (calcFailed) {
            roamGoal = null;
            roamTurn += 90;
        }
        BlockPos feet = ctx.playerFeet();
        if (roamGoal == null || roamGoal.isInGoal(feet)) {
            Level level = ctx.world();
            BlockPos forest = level.dimension() == Level.NETHER
                    ? NearestBlock.find(level, feet, Blocks.WARPED_NYLIUM, 4, 32, 4, p -> level.getBlockState(p.above()).isAir())
                    : null;
            if (forest != null && !forest.closerThan(feet, FOREST_REACHED)) {
                roamGoal = new GoalNear(forest.above(), 3);
            } else {
                LocalPlayer player = ctx.player();
                roamGoal = GoalXZ.fromDirection(player.position(), player.getYRot() + roamTurn, 48);
            }
        }
        return walk(roamGoal);
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
}
