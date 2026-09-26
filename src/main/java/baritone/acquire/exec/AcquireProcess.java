package baritone.acquire.exec;

import baritone.Baritone;
import baritone.acquire.AcquireControl;
import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.knowledge.VanillaKnowledge;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Plan;
import baritone.acquire.model.Step;
import baritone.acquire.planner.AcquirePlanner;
import baritone.acquire.planner.PlannerOptions;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.Settings;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs {@code #acquire}: plans with {@link AcquirePlanner}, then runs the steps one tick at a time and
 * re-plans from the real inventory whenever a step ends short, fails, times out, a tool breaks or the
 * player dies (up to {@code acquireMaxReplans}).
 *
 * <p><b>How it shares control.</b> This is one Baritone process with a priority just above the default,
 * so the control manager asks it first every tick. While a Mine step runs it answers {@code DEFER},
 * which hands that tick to {@link baritone.process.MineProcess} without the manager calling this
 * process's {@link #onLostControl()}; for everything else (walking to stations, crafting, smelting,
 * fighting) it returns its own path or pause command. {@code #stop}, {@code #cancel}, Flee and anything
 * else that calls {@code cancelEverything} reach {@link #onLostControl()}, which stops the step runner,
 * the mine process it started, any screen it opened and any background craft.
 *
 * <p><b>Health comes first</b> ({@code acquireHeal}). Every tick, before the step runs, it eats when
 * {@link HealthPolicy} says so (through {@link EatBehavior}, whose pause process holds mining without
 * cancelling it), backs away from a close attacker first at emergency health during a Kill step, and
 * with no safe food held and health or food low, runs a food detour: the cheapest {@link FoodGoal}
 * plan, then eat, then re-plan the main goal.
 *
 * <p>No surprise behaviour: it sends no server commands, teleports or respawns for you, eats only while
 * it runs and {@code acquireHeal} is on, and only drops items when {@code acquireDropJunk} is on.
 */
public final class AcquireProcess extends BaritoneProcessHelper implements AcquireControl {

    private static final int TICKS_PER_SECOND = 20;
    /** Food detours per acquire before it stops trying. */
    static final int MAX_FOOD_DETOURS = 3;
    /** Consecutive failed meals before it stops trying to eat for the rest of the acquire. */
    private static final int MAX_EAT_FAILURES = 3;
    private static final int EAT_RETRY_TICKS = 5 * TICKS_PER_SECOND;
    /** At emergency health in a fight: back away from an attacker this close, for at most this long. */
    private static final double THREAT_RADIUS = 5;
    private static final int BACK_OFF_TICKS = 2 * TICKS_PER_SECOND;
    private static final int BACK_OFF_DISTANCE = 8;
    /** At emergency health in a fight with nothing to eat, wait this long for regeneration before fighting on. */
    private static final int REGEN_WAIT_TICKS = 20 * TICKS_PER_SECOND;

    private final List<Consumer<AcquireEvent>> listeners = new CopyOnWriteArrayList<>();
    private final StationFinder stations;
    private Knowledge knowledge;
    private ExecContext exec;

    private AcquireRun run;
    private StepRunner runner;
    private int stepTicks;
    private boolean waitingForRespawn;
    private LocalPlayer lastPlayer;

    // Healing (see heal()).
    /** The food detour, run instead of {@link #run} while set. */
    private AcquireRun detour;
    /** The goal is {@code #acquire food}: no detours on top of it. */
    private boolean foodGoal;
    private int detours;
    private boolean noMoreDetours;
    /** Re-plan the main goal with this reason once any meal is done. */
    private String pendingReplan;
    private boolean eatingForUs;
    private int eatFailures;
    private long ticks;
    private long nextEatTick;
    private int backOffTicks;
    private int regenWaitTicks;

    public AcquireProcess(Baritone baritone) {
        super(baritone);
        this.stations = new StationFinder(baritone.getPlayerContext());
    }

    // ---------------------------------------------------------------- AcquireControl

    @Override
    public String start(String itemText, int count) {
        return onGameThread(() -> start0(itemText, count));
    }

    @Override
    public String plan(String itemText, int count) {
        return onGameThread(() -> plan0(itemText, count));
    }

    @Override
    public void stop() {
        onGameThread(() -> {
            if (run != null) finish(AcquireEvent.Kind.STOPPED, "Acquire stopped.");
            return null;
        });
    }

    @Override
    public boolean isActive() {
        return run != null;
    }

    @Override
    public String status() {
        return onGameThread(() -> {
            AcquireRun current = run;
            if (current == null) return "idle";
            String line = current.status(this::have);
            if (detour != null) line += "; first getting food: " + detour.status(this::have);
            if (eatingForUs) line += " (eating)";
            if (waitingForRespawn) line += " (waiting for you to respawn)";
            return line;
        });
    }

    @Override
    public void addListener(Consumer<AcquireEvent> listener) {
        listeners.add(listener);
    }

    private String start0(String itemText, int count) {
        if (count < 1) throw new IllegalArgumentException("The count must be at least 1.");
        if (ctx.player() == null || ctx.world() == null) throw new IllegalArgumentException("Join a world first.");
        Knowledge k = knowledge();
        boolean food = FoodGoal.isFoodWord(itemText);
        String item;
        int want;
        Plan plan;
        if (food) {
            FoodGoal.Choice choice = planFood(count, Set.of());
            item = choice.item();
            want = choice.count();
            plan = choice.plan();
        } else {
            item = resolve(k, itemText);
            want = count;
            plan = newPlan(k, item, count);
            if (plan.alreadyDone()) return "You already have " + count + " " + Step.shortId(item) + ".";
            if (!plan.complete()) {
                throw new IllegalArgumentException("Can't get " + count + " " + Step.shortId(item) + ": " + String.join("; ", plan.missing()));
            }
        }
        if (run != null) finish(AcquireEvent.Kind.STOPPED, "Stopped acquiring " + run.count + " " + Step.shortId(run.goal) + ".");
        // Starting a task replaces whatever Baritone was doing, like every other Baritone command.
        if (baritone.getPathingControlManager().mostRecentInControl().isPresent()) baritone.getPathingBehavior().cancelEverything();

        stations.newRun();
        exec = new ExecContext(baritone, k, stations, this::neededItems);
        run = new AcquireRun(item, want, plan);
        runner = null;
        waitingForRespawn = false;
        lastPlayer = ctx.player();
        resetHealing(food);
        String message = "Acquiring " + want + " " + Step.shortId(item) + (food ? " (the cheapest food)" : "") + ": "
                + plan.steps().size() + " steps. #acquire status for progress, #stop to cancel.";
        logDirect(message);
        fire(AcquireEvent.Kind.STARTED, message);
        return message;
    }

    private String plan0(String itemText, int count) {
        if (count < 1) throw new IllegalArgumentException("The count must be at least 1.");
        if (ctx.player() == null || ctx.world() == null) throw new IllegalArgumentException("Join a world first.");
        Knowledge k = knowledge();
        if (FoodGoal.isFoodWord(itemText)) {
            FoodGoal.Choice choice = planFood(count, Set.of());
            List<String> lines = new ArrayList<>();
            lines.add("Cheapest food: " + choice.extra() + " " + Step.shortId(choice.item()) + " (" + choice.plan().steps().size() + " steps):");
            lines.addAll(choice.plan().explain());
            return String.join("\n", lines);
        }
        String item = resolve(k, itemText);
        Plan plan = newPlan(k, item, count);
        String name = count + " " + Step.shortId(item);
        if (plan.alreadyDone()) return "You already have " + name + ".";
        List<String> lines = new ArrayList<>();
        lines.add(plan.complete()
                ? "Plan for " + name + " (" + plan.steps().size() + " steps):"
                : "Can't fully plan " + name + ":");
        lines.addAll(plan.explain());
        return String.join("\n", lines);
    }

    /**
     * {@code #acquire food [count]}: the cheapest food. A count above 1 is that many of it; otherwise
     * enough for {@link HealthPolicy#detourPoints} food points.
     */
    private FoodGoal.Choice planFood(int count, Set<String> exclude) {
        int points = HealthPolicy.detourPoints(ctx.player().getFoodData().getFoodLevel());
        FoodGoal.Choice choice = chooseFood(points, count > 1 ? count : 0, exclude);
        if (choice == null) {
            throw new IllegalArgumentException("Can't get any food from here: no candidate food has a complete plan. #acquire plan cooked_beef shows why.");
        }
        return choice;
    }

    private FoodGoal.Choice chooseFood(int points, int items, Set<String> exclude) {
        InventorySnapshot inventory = InventoryReader.snapshot(ctx.player());
        BaritoneWorldView world = new BaritoneWorldView(ctx, stations, Baritone.settings().acquireStationRadius.value);
        try {
            return FoodGoal.choose(new AcquirePlanner(knowledge(), world, options()), inventory, points, items, exclude);
        } catch (RuntimeException | LinkageError e) {
            throw new IllegalArgumentException("Couldn't plan food: " + e, e);
        }
    }

    /**
     * The knowledge base, loaded on first use (about half a second). A failed load is not kept, so the
     * next {@code #acquire} tries again. {@code DihClientMod} preloads it on world join.
     */
    private Knowledge knowledge() {
        if (knowledge == null) {
            try {
                knowledge = VanillaKnowledge.get();
            } catch (RuntimeException | LinkageError e) {
                throw new IllegalArgumentException("The item knowledge base failed to load: " + e.getMessage(), e);
            }
        }
        return knowledge;
    }

    private static String resolve(Knowledge k, String itemText) {
        String text = itemText == null ? "" : itemText.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Say which item, e.g. #acquire iron_pickaxe");
        return k.resolveItem(text).orElseThrow(() -> {
            List<String> close = k.suggest(text, 5);
            String hint = close.isEmpty() ? "" : " Did you mean: " + String.join(", ", close.stream().map(Step::shortId).toList()) + "?";
            return new IllegalArgumentException("Unknown item '" + text + "'." + hint);
        });
    }

    private Plan newPlan(Knowledge k, String item, int count) {
        InventorySnapshot inventory = InventoryReader.snapshot(ctx.player());
        BaritoneWorldView world = new BaritoneWorldView(ctx, stations, Baritone.settings().acquireStationRadius.value);
        try {
            return new AcquirePlanner(k, world, options()).plan(item, count, inventory);
        } catch (RuntimeException | LinkageError e) {
            throw new IllegalArgumentException("Couldn't plan " + Step.shortId(item) + ": " + e, e);
        }
    }

    static PlannerOptions options() {
        Settings s = Baritone.settings();
        return new PlannerOptions(s.acquirePlaceStations.value, s.acquireKillMobs.value,
                PlannerOptions.DEFAULT.maxDepth(), PlannerOptions.DEFAULT.maxSteps());
    }

    // ---------------------------------------------------------------- IBaritoneProcess

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        try {
            return tick(calcFailed, isSafeToCancel);
        } catch (RuntimeException | LinkageError e) {
            e.printStackTrace();
            finish(AcquireEvent.Kind.FAILED, "Acquire failed: internal error (" + e + ").");
            return null; // inactive now, so the manager accepts null
        }
    }

    private PathingCommand tick(boolean calcFailed, boolean safeToCancel) {
        ticks++;
        LocalPlayer player = ctx.player();
        if (player.isDeadOrDying()) {
            if (!waitingForRespawn) {
                waitingForRespawn = true;
                cancelRunner();
                logDirect("You died. Acquire re-plans once you respawn (#stop to cancel).");
            }
            return pause();
        }
        if (waitingForRespawn || (lastPlayer != null && player != lastPlayer)) {
            // A new player object means a respawn or a dimension change: everything may have moved.
            boolean died = waitingForRespawn;
            waitingForRespawn = false;
            lastPlayer = player;
            cancelRunner();
            detour = null;
            pendingReplan = null;
            if (!replanMain(died ? "you died" : "you changed worlds")) return null;
        }
        lastPlayer = player;

        // Health comes first: eat, back off, or go and get food before the step runs.
        if (eatingForUs || eater().isBusy()) return pause();
        PathingCommand heal = heal(player);
        if (heal != null) return heal;
        if (pendingReplan != null) {
            String reason = pendingReplan;
            pendingReplan = null;
            if (!replanMain(reason, false)) return null;
        }

        // Several steps can finish in one tick (skips, instant checks); the guard bounds a buggy loop.
        for (int guard = 0; guard < 16; guard++) {
            if (runner == null && !startNextStep()) return idle();
            if (++stepTicks > Baritone.settings().acquireStepTimeoutSeconds.value * TICKS_PER_SECOND) {
                AcquireRun active = active();
                Step step = active.current();
                cancelRunner();
                if (!replan("step " + (active.index() + 1) + " (" + step.describe() + ") timed out")) return idle();
                continue;
            }
            StepRunner.Result result = runner.tick(calcFailed && guard == 0, safeToCancel);
            switch (result.kind()) {
                case RUNNING -> {
                    return result.command();
                }
                case DONE -> {
                    Step step = active().current();
                    runner = null;
                    if (!(step instanceof Step.PlaceStation) && have(step.item()) < step.untilCount()) {
                        if (!replan(step.describe() + " ended with " + have(step.item()) + "/" + step.untilCount())) return idle();
                    }
                }
                case FAILED -> {
                    cancelRunner();
                    if (!replan(result.reason())) return idle();
                }
                case FATAL -> {
                    cancelRunner();
                    finish(AcquireEvent.Kind.FAILED, "Acquire stopped: " + result.reason());
                    return null;
                }
            }
        }
        return pause();
    }

    /** The run whose steps are running: the food detour while there is one, else the main goal. */
    private AcquireRun active() {
        return detour != null ? detour : run;
    }

    /** Starts the next step with work left. False when the run ended (done or failed) or needs another tick. */
    private boolean startNextStep() {
        AcquireRun active = active();
        int index = active.advance(this::have);
        if (index < 0) {
            if (active.goalMet(this::have)) {
                if (active == detour) {
                    endDetour("Got " + have(detour.goal) + " " + Step.shortId(detour.goal) + ".");
                    return false;
                }
                finish(AcquireEvent.Kind.DONE, "Done: you have " + have(run.goal) + " " + Step.shortId(run.goal) + ".");
                return false;
            }
            return replan("the plan ran out with " + have(active.goal) + "/" + active.count + " " + Step.shortId(active.goal)) && startNextStep();
        }
        Step step = active.current();
        runner = createRunner(step);
        stepTicks = 0;
        String line = (active == detour ? "Food step " : "Step ") + (index + 1) + "/" + active.stepCount() + ": " + step.describe();
        logDirect(line);
        fire(AcquireEvent.Kind.STEP, line);
        return true;
    }

    private StepRunner createRunner(Step step) {
        return switch (step) {
            case Step.Mine mine -> new MineRunner(exec, mine);
            case Step.Craft craft -> new CraftRunner(exec, craft);
            case Step.Smelt smelt -> new SmeltRunner(exec, smelt);
            case Step.Kill kill -> new KillRunner(exec, kill);
            case Step.PlaceStation station -> new StationRunner(exec, station);
        };
    }

    /**
     * Plans the running goal again from the real inventory. False when the step loop must stop this tick:
     * the acquire ended (re-plan budget used up, no complete plan), or the food detour was dropped.
     */
    private boolean replan(String reason) {
        return detour != null ? replanDetour(reason) : replanMain(reason);
    }

    /** Re-plans the main goal. False (and the run is over) when the budget is used up or no complete plan exists. */
    private boolean replanMain(String reason) {
        return replanMain(reason, true);
    }

    /**
     * @param counts whether this uses up one of {@code acquireMaxReplans}: not for coming back from a
     *               food detour, which is not a failure
     */
    private boolean replanMain(String reason, boolean counts) {
        int max = Baritone.settings().acquireMaxReplans.value;
        if (counts && run.replans() >= max) {
            finish(AcquireEvent.Kind.FAILED, "Acquire gave up after " + max + " re-plans. Last problem: " + reason + ".");
            return false;
        }
        Plan plan;
        try {
            plan = newPlan(knowledge(), run.goal, run.count);
        } catch (IllegalArgumentException e) {
            finish(AcquireEvent.Kind.FAILED, "Acquire failed: " + reason + ", and " + e.getMessage());
            return false;
        }
        if (plan.alreadyDone()) {
            finish(AcquireEvent.Kind.DONE, "Done: you have " + have(run.goal) + " " + Step.shortId(run.goal) + ".");
            return false;
        }
        if (!plan.complete()) {
            finish(AcquireEvent.Kind.FAILED, "Acquire failed: " + reason + ", and there is no other way: " + String.join("; ", plan.missing()));
            return false;
        }
        if (counts) run.replace(plan);
        else run.resume(plan);
        runner = null;
        logDirect("Re-planning (" + reason + "): " + plan.steps().size() + " steps.", ChatFormatting.YELLOW);
        return true;
    }

    /** Re-plans the food detour on its own budget; drops it (and carries on with the main goal) when that fails. */
    private boolean replanDetour(String reason) {
        int max = Baritone.settings().acquireMaxReplans.value;
        String problem = null;
        Plan plan = null;
        if (detour.replans() >= max) {
            problem = "gave up after " + max + " re-plans";
        } else {
            try {
                plan = newPlan(knowledge(), detour.goal, detour.count);
                if (plan.alreadyDone()) {
                    endDetour("Got " + have(detour.goal) + " " + Step.shortId(detour.goal) + ".");
                    return false;
                }
                if (!plan.complete()) problem = String.join("; ", plan.missing());
            } catch (IllegalArgumentException e) {
                problem = e.getMessage();
            }
        }
        if (problem != null) {
            noMoreDetours = true;
            endDetour("Couldn't get " + Step.shortId(detour.goal) + " (" + reason + "; " + problem + "). Carrying on without food.");
            return false;
        }
        detour.replace(plan);
        runner = null;
        logDirect("Re-planning food (" + reason + "): " + plan.steps().size() + " steps.", ChatFormatting.YELLOW);
        return true;
    }

    // ---------------------------------------------------------------- healing

    /**
     * The health check, before the step runs (only with {@code acquireHeal} on). Returns the command for
     * this tick while it eats or backs off, or null to run the step (which a new food detour replaces).
     * Nothing is eaten with a screen open or mid-craft; at emergency health a container acquire opened
     * (a furnace) is closed first.
     */
    private PathingCommand heal(LocalPlayer player) {
        Settings s = Baritone.settings();
        if (!s.acquireHeal.value) return null;
        float health = player.getHealth();
        int food = player.getFoodData().getFoodLevel();
        int healHealth = s.acquireHealHealth.value;
        HealthPolicy.Need need = HealthPolicy.need(health, player.getAbsorptionAmount(), player.getMaxHealth(), food,
                s.acquireEmergencyHealth.value);
        boolean emergency = need == HealthPolicy.Need.EMERGENCY;
        if (!emergency) {
            backOffTicks = 0;
            regenWaitTicks = 0;
        }
        if (need == HealthPolicy.Need.NONE && health > healHealth && food > HealthPolicy.HUNGRY_FOOD) return null;
        if (runner != null && runner.busy()) return null;
        Minecraft mc = ctx.minecraft();
        if (mc.gui.screen() != null && emergency && player.containerMenu != player.inventoryMenu) player.closeContainer();
        if (mc.gui.screen() != null) return null;

        boolean fighting = runner instanceof KillRunner;
        if (emergency && fighting && backOffTicks < BACK_OFF_TICKS) {
            Entity threat = nearestThreat(player);
            if (threat != null) {
                if (backOffTicks++ == 0) logDirect("Low health (" + HealthPolicy.hearts(health) + "): backing off to eat.", ChatFormatting.YELLOW);
                return new PathingCommand(new GoalRunAway(BACK_OFF_DISTANCE, threat.blockPosition()), PathingCommandType.REVALIDATE_GOAL_AND_PATH);
            }
        }

        List<FoodChoice.Food> held = Foods.held(player);
        Set<String> needed = neededItems();
        if (need != HealthPolicy.Need.NONE && ticks >= nextEatTick) {
            FoodChoice.Food pick = FoodChoice.choose(held, food, emergency, needed);
            if (pick != null) {
                String why = eater().start(pick.id(), this::mealEnded);
                if (why == null) {
                    eatingForUs = true;
                    logDirect("Eating " + Step.shortId(pick.id()) + " (" + HealthPolicy.hearts(health) + ", food " + food + "/20)");
                    return pause();
                }
                mealEnded(why);
            }
        }
        if (detour == null && !foodGoal && !noMoreDetours
                && HealthPolicy.wantsFood(health, food, healHealth, FoodChoice.hasSafeFood(held, needed))) {
            startDetour(health, food, healHealth);
            return null;
        }
        // Fighting at emergency health with nothing to eat now: hold off while it regenerates.
        if (emergency && fighting && food >= HealthPolicy.REGEN_FOOD && regenWaitTicks++ < REGEN_WAIT_TICKS) return pause();
        return null;
    }

    /** Plans the food detour and switches to it, or rules out further detours when there is no food to get. */
    private void startDetour(float health, int food, int healHealth) {
        String why = health <= healHealth ? "Low health (" + HealthPolicy.hearts(health) + ")" : "Hungry (food " + food + "/20)";
        if (detours >= MAX_FOOD_DETOURS) {
            noMoreDetours = true;
            logDirect(why + " with no food, but " + MAX_FOOD_DETOURS + " food runs already; carrying on.", ChatFormatting.YELLOW);
            return;
        }
        // Never fetch a food the main plan itself still needs (it would be eaten out from under it).
        Set<String> exclude = new HashSet<>(JunkPolicy.neededItems(run.goal, run.plan().steps(), Math.max(0, run.index())));
        FoodGoal.Choice choice;
        try {
            choice = chooseFood(HealthPolicy.detourPoints(food), 0, exclude);
        } catch (IllegalArgumentException e) {
            choice = null;
        }
        if (choice == null) {
            noMoreDetours = true;
            logDirect(why + " with no food, and no food can be planned from here; carrying on.", ChatFormatting.YELLOW);
            return;
        }
        cancelRunner();
        detours++;
        detour = new AcquireRun(choice.item(), choice.count(), choice.plan());
        String line = why + ", no food: getting " + choice.extra() + " " + Step.shortId(choice.item()) + " first.";
        logDirect(line, ChatFormatting.YELLOW);
        fire(AcquireEvent.Kind.STEP, line);
    }

    /** Leaves the food detour; the main goal is re-planned once any meal is done. */
    private void endDetour(String message) {
        cancelRunner();
        detour = null;
        logDirect(message, ChatFormatting.GRAY);
        pendingReplan = "back from getting food";
    }

    private void mealEnded(String failure) {
        eatingForUs = false;
        if (run == null) return;
        if (failure == null) {
            eatFailures = 0;
            return;
        }
        nextEatTick = ticks + EAT_RETRY_TICKS;
        if (++eatFailures >= MAX_EAT_FAILURES) {
            nextEatTick = Long.MAX_VALUE;
            logDirect("Couldn't eat " + MAX_EAT_FAILURES + " times (last: " + failure + "); acquire stops trying to eat this run.", ChatFormatting.YELLOW);
        }
    }

    private void resetHealing(boolean food) {
        detour = null;
        foodGoal = food;
        detours = 0;
        noMoreDetours = false;
        pendingReplan = null;
        eatingForUs = false;
        eatFailures = 0;
        nextEatTick = 0;
        backOffTicks = 0;
        regenWaitTicks = 0;
    }

    /** The nearest hostile, or mob targeting the player, within {@link #THREAT_RADIUS}. */
    private Entity nearestThreat(LocalPlayer player) {
        return ctx.entitiesStream()
                .filter(e -> e instanceof LivingEntity living && living.isAlive() && e != player)
                .filter(e -> e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == player)
                .filter(e -> e.distanceToSqr(player) <= THREAT_RADIUS * THREAT_RADIUS)
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
    }

    private EatBehavior eater() {
        return baritone.getEatBehavior();
    }

    @Override
    public void onLostControl() {
        // #stop, #cancel, Flee, a disconnect, or a higher-priority task taking over.
        if (run != null) finish(AcquireEvent.Kind.STOPPED, "Acquire stopped.");
    }

    @Override
    public double priority() {
        // Above MineProcess and CustomGoalProcess (-1) so a DEFER hands them the tick without
        // this process losing control; below the #pause process (0), which halts it.
        return IBaritoneProcess.DEFAULT_PRIORITY + 0.5;
    }

    @Override
    public String displayName0() {
        return run == null ? "Acquire" : "Acquire " + run.count + " " + Step.shortId(run.goal);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Ends the run: stops the runner, a meal it started and anything else it started, logs, and tells
     * listeners. The process is inactive as soon as this returns (cancelEverything checks that); if it is
     * ever called off the game thread, the cleanup and the event move to the game thread.
     */
    private void finish(AcquireEvent.Kind kind, String message) {
        AcquireRun ended = run;
        StepRunner r = runner;
        boolean ourMeal = eatingForUs;
        runner = null;
        run = null;
        detour = null;
        pendingReplan = null;
        eatingForUs = false;
        waitingForRespawn = false;
        Runnable after = () -> {
            if (r != null) r.cancel();
            if (ourMeal) eater().cancel("the acquire ended");
            if (ended == null) return;
            logDirect(message, kind == AcquireEvent.Kind.FAILED ? ChatFormatting.RED
                    : kind == AcquireEvent.Kind.DONE ? ChatFormatting.GREEN : ChatFormatting.GRAY);
            fire(kind, message, ended);
        };
        Minecraft mc = ctx.minecraft();
        if (mc.isSameThread()) after.run();
        else mc.execute(after);
    }

    private void cancelRunner() {
        StepRunner r = runner;
        runner = null;
        if (r != null) r.cancel();
    }

    private void fire(AcquireEvent.Kind kind, String message) {
        fire(kind, message, run);
    }

    private void fire(AcquireEvent.Kind kind, String message, AcquireRun about) {
        AcquireEvent event = new AcquireEvent(kind, about == null ? null : about.goal, about == null ? 0 : about.count, message);
        for (Consumer<AcquireEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    /** What the main plan and the food detour still use: never thrown away as junk, never eaten outside an emergency. */
    private Set<String> neededItems() {
        AcquireRun current = run;
        if (current == null) return Set.of();
        Set<String> needed = JunkPolicy.neededItems(current.goal, current.plan().steps(), Math.max(0, current.index()));
        AcquireRun food = detour;
        if (food != null) needed.addAll(JunkPolicy.neededItems(food.goal, food.plan().steps(), Math.max(0, food.index())));
        return needed;
    }

    private int have(String item) {
        return InventoryReader.count(ctx.player(), item);
    }

    private static PathingCommand pause() {
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    /** After the step loop stopped: nothing once the run is over, else stand still for a tick. */
    private PathingCommand idle() {
        return run == null ? null : pause();
    }

    /** Runs on the game thread, waiting for it when called from elsewhere (the #ai tools). */
    private <T> T onGameThread(Supplier<T> task) {
        Minecraft mc = ctx.minecraft();
        if (mc.isSameThread()) return task.get();
        try {
            return mc.submit(task).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw e;
        }
    }
}
