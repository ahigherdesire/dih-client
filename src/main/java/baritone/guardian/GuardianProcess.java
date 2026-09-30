package baritone.guardian;

import baritone.Baritone;
import baritone.acquire.exec.FoodChoice;
import baritone.acquire.exec.Foods;
import baritone.acquire.exec.InventoryOps;
import baritone.api.Settings;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.combat.CombatRunner;
import baritone.guardian.ThreatRanking.Decision;
import baritone.guardian.ThreatRanking.Mob;
import baritone.guardian.ThreatRanking.Response;
import baritone.guardian.ThreatRanking.Sense;
import baritone.utils.BaritoneProcessHelper;
import dihclient.modules.TeamsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The Guardian: an always-on safety layer over Baritone jobs ({@code #acquire}, {@code #mine}, goals, and later the
 * AI and {@code #beat}). While a job runs, it watches for threats every tick ({@link ThreatRanking}); when one
 * appears it takes control, deals with it (fight, back off, retreat, step out of lava, water-bucket a fall, raise the
 * shield, swim up, eat), and hands control back after {@link #QUIET_TICKS} without a threat. The job resumes from
 * where it was: this process is temporary, so the job is paused, not cancelled, and nothing counts as a re-plan.
 * A stranger coming near ends the job instead and gives control back to the player.
 *
 * <p>It never acts while no job runs: when you play by hand, it stays out of the way.
 *
 * <p>For UIs and the AI (WP 08/09): {@link #status()} is a one-line summary of what it is doing now, and
 * {@link #log()} holds the last {@link GuardianLog#CAPACITY} decisions with a short reason each. Both are safe to
 * read from any thread.
 *
 * <p>Fighting goes through the {@link CombatRunner} (critical hits, shield, bow, backing off a creeper), with normal
 * human timing: it looks at the hitbox centre and attacks only when the attack cooldown is full, through the normal
 * client attack path.
 */
public final class GuardianProcess extends BaritoneProcessHelper {

    /** Hand control back after this many ticks without a threat. */
    static final int QUIET_TICKS = 40;
    private static final int CRUMB_EVERY = 10;
    private static final int CRUMBS = 64;
    private static final double RETREAT_DISTANCE = 12;
    private static final double BACK_OFF_DISTANCE = 8;
    private static final int SCAN_RADIUS = 16;
    private static final int FALL_SCAN = 64;
    /** Place the clutch water when the ground is this close below the feet. */
    private static final double CLUTCH_HEIGHT = 4;
    /** How far to look for water to put out a fire. */
    private static final int WATER_SEARCH = 8;
    /** How far to look for cover from a shooter, and how often to look again. */
    private static final int COVER_RADIUS = 6;
    private static final int COVER_EVERY = 10;

    private final GuardianLog log = new GuardianLog();
    /** Fights for {@link Response#FIGHT}; shared with {@code #acquire}'s kill steps. */
    private final CombatRunner combat;
    private final Deque<BlockPos> crumbs = new ArrayDeque<>();

    private volatile String status = "Idle";
    private int senseTick = Integer.MIN_VALUE;
    private Decision decision;
    private boolean engaged;
    private int quiet;
    private BlockPos anchor;
    private float healthAtStart;
    private Response lastResponse;
    private String lastWhat;
    private int lookTicks;
    private boolean holdingUse;
    private boolean forcingMove;
    /** A stranger came near: stop every job on the next tick, outside the control manager's loop. */
    private volatile boolean stopPending;
    /** Water placed by a clutch that should be picked back up once landed. */
    private BlockPos clutchWater;
    private int crumbTicks;
    /** While digging in: where the seal goes (the block the player stood on when it started). */
    private BlockPos shelterSeal;
    /** The block being dug for a shelter, to continue rather than restart the dig. */
    private BlockPos shelterDigging;
    /** A pit that couldn't be finished here; don't try again at this spot. */
    private BlockPos shelterFailedAt;
    private int shelterRestarts;
    /** The nearest spot hidden from every aiming shooter, recomputed every {@link #COVER_EVERY} ticks while hurt. */
    private BlockPos coverSpot;
    private int coverTick = Integer.MIN_VALUE;

    public GuardianProcess(Baritone baritone) {
        super(baritone);
        this.combat = CombatRunner.of(baritone);
        baritone.getGameEventHandler().registerEventListener(new AbstractGameEventListener() {
            @Override
            public void onTick(TickEvent event) {
                if (!stopPending || event.getType() != TickEvent.Type.IN) return;
                stopPending = false;
                baritone.getPathingBehavior().cancelEverything();
            }
        });
    }

    // ---------------------------------------------------------------- public view

    /** What the Guardian is doing now, e.g. "Fighting zombie (3 blocks)", "Guarding #acquire", "Off". */
    public String status() {
        return status;
    }

    /** The recent decisions, with reasons. */
    public GuardianLog log() {
        return log;
    }

    // ---------------------------------------------------------------- IBaritoneProcess

    @Override
    public boolean isActive() {
        LocalPlayer player = ctx.player();
        if (player == null || ctx.world() == null || !Baritone.settings().guardianEnabled.value) {
            if (engaged) disengage("Guardian turned off");
            status = Baritone.settings().guardianEnabled.value ? "Idle" : "Off";
            return false;
        }
        boolean job = baritone.getPathingControlManager().hasActiveJob(this);
        if (!job && !engaged) {
            crumbs.clear();
            status = "Idle";
            return clutchWater != null;
        }
        sense(player);
        if (decision == null && !engaged) {
            status = "Guarding";
            return clutchWater != null;
        }
        return true;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        LocalPlayer player = ctx.player();
        sense(player);
        Decision d = decision;
        if (d == null) {
            releaseInputs();
            if (clutchWater != null && pickUpClutchWater(player)) return pause();
            if (!engaged) return defer();
            if (++quiet < QUIET_TICKS) {
                status = "Clear, resuming shortly";
                return pause();
            }
            disengage(null);
            return defer();
        }
        quiet = 0;
        if (!engaged) {
            engaged = true;
            anchor = player.blockPosition();
            healthAtStart = player.getHealth();
        }
        // A new line when the response, the threat, or the phase of a shelter (digging, then inside) changes.
        String what = d.threat().what() + (d.reason().startsWith("sheltering") ? " (inside)" : "");
        if (d.response() != lastResponse || !what.equals(lastWhat)) {
            log.add(d.reason());
            lastResponse = d.response();
            lastWhat = what;
            lookTicks = 0;
        }
        status = capitalize(d.reason());
        if (d.response() != Response.FIGHT) combat.release();
        if (d.response() != Response.SHIELD) releaseUse();
        if (d.response() != Response.ESCAPE_HAZARD && d.response() != Response.SURFACE) releaseMove();
        return switch (d.response()) {
            case ESCAPE_HAZARD -> escapeHazard(player);
            case EXTINGUISH -> extinguish(player);
            case WATER_CLUTCH -> clutch(player);
            case BACK_OFF -> runFrom(entity(d), BACK_OFF_DISTANCE);
            case FIGHT -> fight(player, entity(d));
            case RETREAT -> retreat(player, entity(d));
            case SHIELD -> shield(player);
            case SURFACE -> surface();
            case EAT -> eat(player);
            case HAND_BACK -> handBack(d);
            case SHELTER -> shelter(player);
            case COVER -> cover(player);
        };
    }

    @Override
    public boolean isTemporary() {
        // Pausing a job, not replacing it: the job keeps its state and resumes afterwards.
        return true;
    }

    @Override
    public void onLostControl() {
        releaseInputs();
    }

    @Override
    public double priority() {
        // Above #acquire (-0.5) and the eat pauser (-0.25); below the #pause process (0).
        return -0.1;
    }

    @Override
    public String displayName0() {
        return "Guardian: " + status;
    }

    // ---------------------------------------------------------------- sensing

    /** Recomputes {@link #decision} once per game tick. */
    private void sense(LocalPlayer player) {
        if (player.tickCount == senseTick) return;
        senseTick = player.tickCount;
        if (anchor == null || !engaged) {
            if (++crumbTicks >= CRUMB_EVERY && player.onGround()) {
                crumbTicks = 0;
                BlockPos here = player.blockPosition();
                if (crumbs.isEmpty() || !crumbs.peekLast().closerThan(here, 2)) {
                    crumbs.addLast(here);
                    if (crumbs.size() > CRUMBS) crumbs.removeFirst();
                }
            }
        }
        Settings s = Baritone.settings();
        decision = ThreatRanking.decide(senseOf(player), new ThreatRanking.Config(s.guardianFleeHealth.value,
                s.guardianStopForPlayers.value));
    }

    private Sense senseOf(LocalPlayer player) {
        Level level = ctx.world();
        BlockPos feet = player.blockPosition();
        BlockPos from = engaged && anchor != null ? anchor : feet;
        List<Mob> mobs = new ArrayList<>();
        boolean projectile = false;
        double stranger = Double.POSITIVE_INFINITY;
        for (Entity e : ctx.entitiesStream().toList()) {
            if (e == player || !e.isAlive()) continue;
            double distance = e.distanceTo(player);
            if (distance > SCAN_RADIUS) continue;
            if (e instanceof Player other) {
                if (!other.isSpectator() && !TeamsModule.isFriendOrTeam(other)) stranger = Math.min(stranger, distance);
            } else if (e instanceof Creeper creeper) {
                mobs.add(new Mob(e.getId(), typeId(e), distance, Math.sqrt(e.distanceToSqr(Vec3.atCenterOf(from))),
                        false, false, true, creeper.getSwellDir() > 0 || creeper.getSwelling(1.0F) > 0));
            } else if (e instanceof Enemy && e instanceof net.minecraft.world.entity.Mob mob) {
                // Piglins leave a player in gold armour alone (and all of them turn on one who hits one).
                boolean neutral = e instanceof NeutralMob || e instanceof Piglin && PiglinAi.isWearingSafeArmor(player);
                mobs.add(new Mob(e.getId(), typeId(e), distance, Math.sqrt(e.distanceToSqr(Vec3.atCenterOf(from))),
                        mob.isAggressive(), neutral, false, false));
            } else if (e instanceof Projectile shot && distance <= ThreatRanking.PROJECTILE_RADIUS
                    && shot.getOwner() != player && CombatRunner.incoming(shot, player)) {
                // A fight under way raises the shield at shots itself, between its own moves.
                if (!combat.fighting()) projectile = true;
            }
        }
        List<Entity> shooters = shooters(player);
        boolean inCover = !shooters.isEmpty() && hidden(level, player.position(), shooters);
        boolean hurt = player.getHealth() <= Baritone.settings().guardianFleeHealth.value + ThreatRanking.CROWD_HEALTH_MARGIN;
        if (shooters.isEmpty() || inCover || !hurt) {
            coverSpot = null;
        } else if (player.tickCount - coverTick >= COVER_EVERY) {
            coverTick = player.tickCount;
            coverSpot = findCover(player, shooters);
        }
        boolean coverNearby = coverSpot != null;
        BlockState at = level.getBlockState(feet);
        boolean inFire = at.is(BlockTags.FIRE) || level.getBlockState(feet.above()).is(BlockTags.FIRE);
        boolean falling = !player.onGround() && !player.isInWater() && !player.isFallFlying()
                && !player.getAbilities().flying && player.getDeltaMovement().y < -0.1;
        double predicted = falling ? player.fallDistance + dropBelow(player) : 0;
        FoodChoice.Food food = FoodChoice.choose(Foods.held(player), player.getFoodData().getFoodLevel(),
                player.getHealth() <= Baritone.settings().acquireEmergencyHealth.value, Set.of());
        boolean burning = player.isOnFire() && !player.fireImmune();
        return new Sense(player.isInLava(), inFire, burning, burning && nearestWater(player) != null, falling, predicted, player.getHealth(), player.getAirSupply(),
                player.getMaxAirSupply(), player.isUnderWater(), food != null, mobs, projectile, stranger,
                player.getOffhandItem().is(Items.SHIELD), has(player, s -> s.is(Items.WATER_BUCKET)),
                shelterSeal != null || canShelter(player), sheltered(player), daylight(level),
                player.getFoodData().getFoodLevel() >= 18, shelterSeal != null, inCover, coverNearby, boxedIn(player));
    }

    /** The nearest water block within {@link #WATER_SEARCH} blocks the player can stand in, or null. */
    private BlockPos nearestWater(LocalPlayer player) {
        Level level = ctx.world();
        BlockPos feet = player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-WATER_SEARCH, -2, -WATER_SEARCH), feet.offset(WATER_SEARCH, 2, WATER_SEARCH))) {
            if (!level.getFluidState(pos).is(FluidTags.WATER)) continue;
            double dist = pos.distSqr(feet);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
            }
        }
        return best;
    }

    /** Blocks between the feet and whatever will stop the fall; water ends a fall harmlessly (0). */
    private double dropBelow(LocalPlayer player) {
        Level level = ctx.world();
        BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
        double start = player.getY();
        for (int i = 0; i < FALL_SCAN; i++) {
            pos.move(0, -1, 0);
            BlockState state = level.getBlockState(pos);
            if (state.getFluidState().is(FluidTags.WATER)) return 0;
            if (!state.getCollisionShape(level, pos).isEmpty()) return Math.max(0, start - (pos.getY() + 1));
        }
        return FALL_SCAN;
    }

    // ---------------------------------------------------------------- responses

    private PathingCommand fight(LocalPlayer player, Entity target) {
        if (!(target instanceof LivingEntity mob)) return pause();
        boolean ranged = ThreatRanking.RANGED.contains(typeId(target));
        if (!ranged && anchor != null && target.blockPosition().distSqr(anchor) > ThreatRanking.CHASE_LIMIT * ThreatRanking.CHASE_LIMIT
                && !player.isWithinEntityInteractionRange(target, -0.25D)) {
            // Beyond the chase limit: hold the ground and let it come (a shooter never would, so those are chased).
            combat.release();
            lookAt(target.getBoundingBox().getCenter());
            return pause();
        }
        Goal goal = combat.tick(mob);
        return goal == null ? pause() : new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
    }

    private PathingCommand runFrom(Entity threat, double distance) {
        if (threat == null) return pause();
        return new PathingCommand(new GoalRunAway(distance, threat.blockPosition()), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
    }

    /** Back along the path already walked, to a spot well away from the threat; else straight away from it. */
    private PathingCommand retreat(LocalPlayer player, Entity threat) {
        if (threat == null) return pause();
        BlockPos danger = threat.blockPosition();
        Iterator<BlockPos> newest = crumbs.descendingIterator();
        while (newest.hasNext()) {
            BlockPos crumb = newest.next();
            if (crumb.distSqr(danger) >= RETREAT_DISTANCE * RETREAT_DISTANCE && !crumb.closerThan(player.blockPosition(), 3)) {
                return new PathingCommand(new GoalNear(crumb, 1), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
            }
        }
        return new PathingCommand(new GoalRunAway(RETREAT_DISTANCE + 4, danger), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
    }

    /** Faces the nearest safe standing spot and walks, sprints and jumps onto it. */
    private PathingCommand escapeHazard(LocalPlayer player) {
        BlockPos safe = nearestSafeSpot(player);
        if (safe != null) {
            Vec3 target = Vec3.atBottomCenterOf(safe);
            Rotation r = RotationUtils.calcRotationFromVec3d(player.getEyePosition(), target.add(0, player.getEyeHeight(), 0),
                    ctx.playerRotations());
            // Turn now: every tick in lava hurts, so don't wait for a smooth look.
            player.setYRot(r.getYaw());
            player.setXRot(0);
            baritone.getLookBehavior().updateTarget(new Rotation(r.getYaw(), 0), true);
        }
        forceMove(true);
        return pause();
    }

    /** Pours the water bucket at the feet (scooped up again later), or walks into the nearest water. */
    private PathingCommand extinguish(LocalPlayer player) {
        int slot = InventoryOps.toHotbar(ctx, s -> s.is(Items.WATER_BUCKET));
        if (slot >= 0 && player.onGround()) {
            player.getInventory().setSelectedSlot(slot);
            player.setXRot(90);
            baritone.getLookBehavior().updateTarget(new Rotation(player.getYRot(), 90), true);
            if (++lookTicks >= 2) {
                ctx.minecraft().gameMode.useItem(player, InteractionHand.MAIN_HAND);
                if (player.getMainHandItem().is(Items.BUCKET)) clutchWater = player.blockPosition();
                lookTicks = 0;
            }
            return pause();
        }
        BlockPos water = nearestWater(player);
        if (water == null) return pause();
        return new PathingCommand(new GoalBlock(water), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
    }

    private BlockPos nearestSafeSpot(LocalPlayer player) {
        Level level = ctx.world();
        BlockPos feet = player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    if (!safeToStand(level, pos)) continue;
                    double dist = pos.distSqr(feet);
                    if (dist > 0 && dist < bestDist) {
                        bestDist = dist;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private static boolean safeToStand(Level level, BlockPos pos) {
        BlockState floor = level.getBlockState(pos.below());
        if (floor.getCollisionShape(level, pos.below()).isEmpty() || floor.getFluidState().is(FluidTags.LAVA)) return false;
        for (BlockPos p : new BlockPos[]{pos, pos.above()}) {
            BlockState state = level.getBlockState(p);
            if (!state.getCollisionShape(level, p).isEmpty()) return false;
            if (state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE)) return false;
        }
        return true;
    }

    /** Looks straight down with the water bucket, and empties it just before the ground. */
    private PathingCommand clutch(LocalPlayer player) {
        int slot = InventoryOps.toHotbar(ctx, s -> s.is(Items.WATER_BUCKET));
        if (slot < 0) return pause();
        player.getInventory().setSelectedSlot(slot);
        baritone.getLookBehavior().updateTarget(new Rotation(player.getYRot(), 90), true);
        double ground = dropBelow(player);
        if (ground <= CLUTCH_HEIGHT && player.getXRot() > 80) {
            BlockPos landing = BlockPos.containing(player.getX(), player.getY() - ground, player.getZ());
            ctx.minecraft().gameMode.useItem(player, InteractionHand.MAIN_HAND);
            if (player.getMainHandItem().is(Items.BUCKET)) {
                clutchWater = landing;
                log.add("water bucket clutch at " + landing.toShortString());
            }
        }
        return pause();
    }

    /** After landing in clutch water: scoop it back up. True while still working on it. */
    private boolean pickUpClutchWater(LocalPlayer player) {
        Level level = ctx.world();
        if (!level.getFluidState(clutchWater).is(FluidTags.WATER) || clutchWater.distSqr(player.blockPosition()) > 16) {
            clutchWater = null;
            return false;
        }
        if (!player.onGround() && !player.isInWater()) return true;
        int slot = InventoryOps.toHotbar(ctx, s -> s.is(Items.BUCKET));
        if (slot < 0) {
            clutchWater = null;
            return false;
        }
        player.getInventory().setSelectedSlot(slot);
        lookAt(Vec3.atCenterOf(clutchWater));
        if (++lookTicks >= 3) {
            ctx.minecraft().gameMode.useItem(player, InteractionHand.MAIN_HAND);
            lookTicks = 0;
            if (player.getMainHandItem().is(Items.WATER_BUCKET) || !level.getFluidState(clutchWater).is(FluidTags.WATER)) clutchWater = null;
        }
        return clutchWater != null;
    }

    /** Faces the nearest aggressive mob (the likely shooter) and holds the shield up. */
    private PathingCommand shield(LocalPlayer player) {
        ctx.entitiesStream()
                .filter(e -> e instanceof net.minecraft.world.entity.Mob mob && e instanceof Enemy && mob.isAggressive())
                .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)))
                .ifPresent(shooter -> lookAt(shooter.getEyePosition()));
        holdUse();
        return pause();
    }

    private PathingCommand surface() {
        baritone.getLookBehavior().updateTarget(new Rotation(ctx.player().getYRot(), -90), true);
        baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
        forcingMove = true;
        return pause();
    }

    private PathingCommand eat(LocalPlayer player) {
        var eater = baritone.getEatBehavior();
        if (eater.isBusy()) return pause();
        FoodChoice.Food pick = FoodChoice.choose(Foods.held(player), player.getFoodData().getFoodLevel(),
                player.getHealth() <= Baritone.settings().acquireEmergencyHealth.value, Set.of());
        if (pick == null) return pause();
        String why = eater.start(pick.id(), failure -> { });
        if (why != null) log.add("couldn't eat " + ThreatRanking.shortId(pick.id()) + ": " + why);
        return pause();
    }

    // ---------------------------------------------------------------- cover

    /** Stays out of sight (eating if hurt and able), or goes to the nearest hidden spot. */
    private PathingCommand cover(LocalPlayer player) {
        releaseUse();
        List<Entity> shooters = shooters(player);
        if (shooters.isEmpty() || hidden(ctx.world(), player.position(), shooters)) {
            if (player.getHealth() < player.getMaxHealth() && FoodChoice.choose(Foods.held(player),
                    player.getFoodData().getFoodLevel(), false, Set.of()) != null) return eat(player);
            return pause();
        }
        if (coverSpot == null) return pause();
        return new PathingCommand(new GoalBlock(coverSpot), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
    }

    /** Ranged mobs aiming at something within {@link ThreatRanking#RANGED_RADIUS}. */
    private List<Entity> shooters(LocalPlayer player) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : ctx.entitiesStream().toList()) {
            if (!(e instanceof net.minecraft.world.entity.Mob mob) || !e.isAlive() || !mob.isAggressive()) continue;
            if (!ThreatRanking.RANGED.contains(typeId(e)) || e.distanceTo(player) > ThreatRanking.RANGED_RADIUS) continue;
            out.add(e);
        }
        return out;
    }

    /** Whether a player standing at {@code feet} is out of every shooter's sight (both eye and chest height). */
    private static boolean hidden(Level level, Vec3 feet, List<Entity> shooters) {
        for (Entity shooter : shooters) {
            Vec3 eye = shooter.getEyePosition();
            for (double height : new double[]{0.9, 1.6}) {
                Vec3 target = feet.add(0, height, 0);
                var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, target,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, shooter));
                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return false;
            }
        }
        return true;
    }

    /** The nearest safe standing spot within {@link #COVER_RADIUS} that no shooter can see, or null. */
    private BlockPos findCover(LocalPlayer player, List<Entity> shooters) {
        Level level = ctx.world();
        BlockPos feet = player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-COVER_RADIUS, -1, -COVER_RADIUS), feet.offset(COVER_RADIUS, 1, COVER_RADIUS))) {
            double dist = pos.distSqr(feet);
            if (dist >= bestDist || dist > COVER_RADIUS * COVER_RADIUS) continue;
            if (!safeToStand(level, pos)) continue;
            if (!hidden(level, Vec3.atBottomCenterOf(pos), shooters)) continue;
            best = pos.immutable();
            bestDist = dist;
        }
        return best;
    }

    // ---------------------------------------------------------------- shelter

    /** Blocks to seal a pit with: common junk, never anything a plan or a station would want. */
    private static final Set<net.minecraft.world.item.Item> SHELTER_BLOCKS = Set.of(Items.DIRT, Items.COARSE_DIRT,
            Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.NETHERRACK, Items.ANDESITE, Items.DIORITE, Items.GRANITE,
            Items.TUFF, Items.STONE, Items.DEEPSLATE, Items.BLACKSTONE, Items.SANDSTONE, Items.END_STONE);
    private static final int SHELTER_DEPTH = 3;
    /** Times a pit may be restarted after being knocked off it before giving up on digging in here. */
    private static final int SHELTER_RESTARTS = 4;

    /** Digs straight down {@link #SHELTER_DEPTH} blocks, seals the top, then waits (eating if it can). */
    private PathingCommand shelter(LocalPlayer player) {
        Level level = ctx.world();
        var gameMode = ctx.minecraft().gameMode;
        if (sheltered(player)) {
            shelterSeal = null;
            shelterDigging = null;
            shelterRestarts = 0;
            if (player.getHealth() < player.getMaxHealth() && FoodChoice.choose(Foods.held(player),
                    player.getFoodData().getFoodLevel(), false, Set.of()) != null) return eat(player);
            return pause();
        }
        releaseUse();
        BlockPos feet = player.blockPosition();
        if (shelterSeal == null) shelterSeal = feet.below();
        int depth = shelterSeal.getY() + 1 - feet.getY();
        boolean offColumn = feet.getX() != shelterSeal.getX() || feet.getZ() != shelterSeal.getZ();
        if (depth < 0 || depth > SHELTER_DEPTH || offColumn) {
            // Knocked sideways (or still sliding) before the pit was deep enough: start again where we stand.
            if (++shelterRestarts > SHELTER_RESTARTS) return giveUpShelter(feet, "kept getting pushed out of the pit");
            shelterSeal = null;
            shelterDigging = null;
            return pause();
        }
        if (depth < SHELTER_DEPTH) {
            BlockPos below = feet.below();
            if (!player.onGround() || !solid(level, below)) return pause();
            selectDigTool(player);
            baritone.getLookBehavior().updateTarget(new Rotation(player.getYRot(), 90), true);
            if (!below.equals(shelterDigging)) {
                gameMode.startDestroyBlock(below, Direction.UP);
                shelterDigging = below;
            } else {
                gameMode.continueDestroyBlock(below, Direction.UP);
            }
            player.swing(InteractionHand.MAIN_HAND);
            return pause();
        }
        if (!level.getBlockState(shelterSeal).canBeReplaced()) return giveUpShelter(feet, "the pit is open to the side");
        int slot = InventoryOps.toHotbar(ctx, stack -> SHELTER_BLOCKS.contains(stack.getItem()));
        if (slot < 0) return giveUpShelter(feet, "nothing to seal it with");
        player.getInventory().setSelectedSlot(slot);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos wall = shelterSeal.relative(side);
            if (!solid(level, wall)) continue;
            Direction face = side.getOpposite();
            Vec3 hit = Vec3.atCenterOf(wall).add(face.getStepX() * 0.5, 0, face.getStepZ() * 0.5);
            lookAt(hit);
            gameMode.useItemOn(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, wall, false));
            player.swing(InteractionHand.MAIN_HAND);
            return pause();
        }
        return giveUpShelter(feet, "no wall to seal against");
    }

    private PathingCommand giveUpShelter(BlockPos feet, String why) {
        log.add("could not dig in: " + why);
        shelterFailedAt = feet;
        shelterRestarts = 0;
        shelterSeal = null;
        shelterDigging = null;
        return pause();
    }

    /** Solid blocks on all sides at feet and head height: nowhere to step back to, whatever is overhead. */
    private boolean boxedIn(LocalPlayer player) {
        Level level = ctx.world();
        BlockPos feet = player.blockPosition();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!solid(level, feet.relative(side)) || !solid(level, feet.above().relative(side))) return false;
        }
        return true;
    }

    /** In a 1x1 pit with solid blocks on all sides at feet and head height, and overhead. */
    private boolean sheltered(LocalPlayer player) {
        Level level = ctx.world();
        BlockPos feet = player.blockPosition();
        BlockPos head = feet.above();
        if (!player.onGround() || !solid(level, head.above())) return false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!solid(level, feet.relative(side)) || !solid(level, head.relative(side))) return false;
        }
        return true;
    }

    /**
     * Whether a pit can be dug here: a pickaxe and a sealing block held, and {@link #SHELTER_DEPTH} diggable solid
     * blocks below with solid walls and a solid floor (no falling into a cave or lava).
     */
    private boolean canShelter(LocalPlayer player) {
        if (!player.onGround() || player.isInWater() || player.isInLava()) return false;
        BlockPos feet = player.blockPosition();
        if (shelterFailedAt != null && shelterFailedAt.closerThan(feet, 3)) return false;
        if (!has(player, stack -> stack.is(ItemTags.PICKAXES))) return false;
        if (!has(player, stack -> SHELTER_BLOCKS.contains(stack.getItem()))) return false;
        Level level = ctx.world();
        for (int down = 1; down <= SHELTER_DEPTH; down++) {
            BlockPos pos = feet.below(down);
            BlockState state = level.getBlockState(pos);
            float hardness = state.getDestroySpeed(level, pos);
            if (!solid(level, pos) || hardness < 0 || hardness > 5 || state.hasBlockEntity()) return false;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (!solid(level, pos.relative(side))) return false;
            }
        }
        return solid(level, feet.below(SHELTER_DEPTH + 1));
    }

    private static boolean solid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    private static boolean daylight(Level level) {
        if (level.dimension() != Level.OVERWORLD) return true;
        long time = level.getOverworldClockTime() % 24000L;
        return time < 12500 || time > 23500;
    }

    private void selectDigTool(LocalPlayer player) {
        int slot = InventoryOps.toHotbar(ctx, s -> s.is(ItemTags.PICKAXES) && best(player, ItemTags.PICKAXES) == s);
        if (slot >= 0) player.getInventory().setSelectedSlot(slot);
    }

    private PathingCommand handBack(Decision d) {
        log.add(d.reason());
        logDirect("Guardian: " + d.reason() + ".");
        releaseInputs();
        engaged = false;
        quiet = 0;
        lastResponse = null;
        lastWhat = null;
        status = "Stopped the job: " + d.threat().what() + " nearby";
        stopPending = true;
        return pause();
    }

    // ---------------------------------------------------------------- helpers

    private void disengage(String why) {
        releaseInputs();
        LocalPlayer player = ctx.player();
        if (lastResponse != null && player != null) {
            log.add(summary(lastResponse, lastWhat) + ", " + hp(healthAtStart) + "→" + hp(player.getHealth()) + " HP"
                    + (why == null ? "" : " (" + why + ")"));
        }
        engaged = false;
        quiet = 0;
        anchor = null;
        lastResponse = null;
        lastWhat = null;
        shelterSeal = null;
        shelterDigging = null;
        shelterRestarts = 0;
        status = "Guarding";
    }

    private static String summary(Response r, String what) {
        String w = ThreatRanking.shortId(what == null ? null : what.replace(" (inside)", ""));
        return switch (r) {
            case ESCAPE_HAZARD -> "escaped " + w;
            case EXTINGUISH -> "put out the fire";
            case WATER_CLUTCH -> "broke " + w;
            case BACK_OFF -> "avoided " + w;
            case FIGHT -> "fought " + w;
            case RETREAT -> "retreated from " + w;
            case SHIELD -> "blocked " + w;
            case SURFACE -> "surfaced for air";
            case EAT -> "ate";
            case HAND_BACK -> "handed back";
            case SHELTER -> "sheltered from " + w;
            case COVER -> "hid from " + w;
        };
    }

    private static String hp(float health) {
        return String.valueOf(Math.round(health));
    }

    private Entity entity(Decision d) {
        return d.threat().mobId() < 0 ? null : ctx.world().getEntity(d.threat().mobId());
    }

    /** The held item of a tag with the most durability left, a rough "best tier" (diamond > iron > stone > wood). */
    private static ItemStack best(LocalPlayer player, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        ItemStack best = null;
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (s.isEmpty() || !s.is(tag)) continue;
            if (best == null || s.getMaxDamage() > best.getMaxDamage()) best = s;
        }
        return best;
    }

    private static boolean has(LocalPlayer player, Predicate<ItemStack> want) {
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) if (!s.isEmpty() && want.test(s)) return true;
        return want.test(player.getOffhandItem());
    }

    private void lookAt(Vec3 point) {
        baritone.getLookBehavior().updateTarget(
                RotationUtils.calcRotationFromVec3d(ctx.playerHead(), point, ctx.playerRotations()), true);
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
        if (player != null && player.isUsingItem() && player.getUseItem().is(Items.SHIELD) && mc.gameMode != null) {
            mc.gameMode.releaseUsingItem(player);
        }
        holdingUse = false;
    }

    private void forceMove(boolean jump) {
        var input = baritone.getInputOverrideHandler();
        input.setInputForceState(Input.MOVE_FORWARD, true);
        input.setInputForceState(Input.SPRINT, true);
        input.setInputForceState(Input.JUMP, jump);
        forcingMove = true;
    }

    private void releaseMove() {
        if (!forcingMove) return;
        var input = baritone.getInputOverrideHandler();
        input.setInputForceState(Input.MOVE_FORWARD, false);
        input.setInputForceState(Input.SPRINT, false);
        input.setInputForceState(Input.JUMP, false);
        forcingMove = false;
    }

    private void releaseInputs() {
        combat.release();
        releaseUse();
        releaseMove();
        lookTicks = 0;
    }

    private static String typeId(Entity e) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static PathingCommand pause() {
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private static PathingCommand defer() {
        return new PathingCommand(null, PathingCommandType.DEFER);
    }
}
