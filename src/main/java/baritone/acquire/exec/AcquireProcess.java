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
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.Settings;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
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
 * <p>No surprise behaviour: it sends no server commands, never eats, teleports or respawns for you,
 * and only drops items when {@code acquireDropJunk} is on.
 */
public final class AcquireProcess extends BaritoneProcessHelper implements AcquireControl {

    private static final int TICKS_PER_SECOND = 20;

    private final List<Consumer<AcquireEvent>> listeners = new CopyOnWriteArrayList<>();
    private final StationFinder stations;
    private Knowledge knowledge;
    private ExecContext exec;

    private AcquireRun run;
    private StepRunner runner;
    private int stepTicks;
    private boolean waitingForRespawn;
    private LocalPlayer lastPlayer;

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
            if (waitingForRespawn) return current.status(this::have) + " (waiting for you to respawn)";
            return current.status(this::have);
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
        String item = resolve(k, itemText);
        Plan plan = newPlan(k, item, count);
        if (plan.alreadyDone()) return "You already have " + count + " " + Step.shortId(item) + ".";
        if (!plan.complete()) {
            throw new IllegalArgumentException("Can't get " + count + " " + Step.shortId(item) + ": " + String.join("; ", plan.missing()));
        }
        if (run != null) finish(AcquireEvent.Kind.STOPPED, "Stopped acquiring " + run.count + " " + Step.shortId(run.goal) + ".");
        // Starting a task replaces whatever Baritone was doing, like every other Baritone command.
        if (baritone.getPathingControlManager().mostRecentInControl().isPresent()) baritone.getPathingBehavior().cancelEverything();

        stations.newRun();
        exec = new ExecContext(baritone, k, stations, this::neededItems);
        run = new AcquireRun(item, count, plan);
        runner = null;
        waitingForRespawn = false;
        lastPlayer = ctx.player();
        String message = "Acquiring " + count + " " + Step.shortId(item) + ": " + plan.steps().size() + " steps. #acquire status for progress, #stop to cancel.";
        logDirect(message);
        fire(AcquireEvent.Kind.STARTED, message);
        return message;
    }

    private String plan0(String itemText, int count) {
        if (count < 1) throw new IllegalArgumentException("The count must be at least 1.");
        if (ctx.player() == null || ctx.world() == null) throw new IllegalArgumentException("Join a world first.");
        Knowledge k = knowledge();
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
     * The knowledge base, loaded on first use (about half a second). A failed load is not kept, so the
     * next {@code #acquire} tries again.
     * TODO(integration): call {@code VanillaKnowledge.preload()} on world join (DihClientMod's JOIN hook)
     * once the knowledge branch is merged; the scaffold here has no such method yet.
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
            if (!replan(died ? "you died" : "you changed worlds")) return null;
        }
        lastPlayer = player;

        // Several steps can finish in one tick (skips, instant checks); the guard bounds a buggy loop.
        for (int guard = 0; guard < 16; guard++) {
            if (runner == null && !startNextStep()) return run == null ? null : pause();
            if (++stepTicks > Baritone.settings().acquireStepTimeoutSeconds.value * TICKS_PER_SECOND) {
                Step step = run.current();
                cancelRunner();
                if (!replan("step " + (run.index() + 1) + " (" + step.describe() + ") timed out")) return null;
                continue;
            }
            StepRunner.Result result = runner.tick(calcFailed && guard == 0, safeToCancel);
            switch (result.kind()) {
                case RUNNING -> {
                    return result.command();
                }
                case DONE -> {
                    Step step = run.current();
                    runner = null;
                    if (!(step instanceof Step.PlaceStation) && have(step.item()) < step.untilCount()) {
                        if (!replan(step.describe() + " ended with " + have(step.item()) + "/" + step.untilCount())) return null;
                    }
                }
                case FAILED -> {
                    cancelRunner();
                    if (!replan(result.reason())) return null;
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

    /** Starts the next step with work left. False when the run ended (done or failed) or needs another tick. */
    private boolean startNextStep() {
        int index = run.advance(this::have);
        if (index < 0) {
            if (run.goalMet(this::have)) {
                finish(AcquireEvent.Kind.DONE, "Done: you have " + have(run.goal) + " " + Step.shortId(run.goal) + ".");
                return false;
            }
            return replan("the plan ran out with " + have(run.goal) + "/" + run.count + " " + Step.shortId(run.goal)) && startNextStep();
        }
        Step step = run.current();
        runner = createRunner(step);
        stepTicks = 0;
        String line = "Step " + (index + 1) + "/" + run.stepCount() + ": " + step.describe();
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
     * Plans again from the real inventory. False (and the run is over) when the re-plan budget is used
     * up or no complete plan exists any more.
     */
    private boolean replan(String reason) {
        int max = Baritone.settings().acquireMaxReplans.value;
        if (run.replans() >= max) {
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
        run.replace(plan);
        runner = null;
        logDirect("Re-planning (" + reason + "): " + plan.steps().size() + " steps.", ChatFormatting.YELLOW);
        return true;
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
     * Ends the run: stops the runner and anything it started, logs, and tells listeners. The process is
     * inactive as soon as this returns (cancelEverything checks that); if it is ever called off the game
     * thread, the cleanup and the event move to the game thread.
     */
    private void finish(AcquireEvent.Kind kind, String message) {
        AcquireRun ended = run;
        StepRunner r = runner;
        runner = null;
        run = null;
        waitingForRespawn = false;
        Runnable after = () -> {
            if (r != null) r.cancel();
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

    private Set<String> neededItems() {
        AcquireRun current = run;
        return current == null ? Set.of() : JunkPolicy.neededItems(current.goal, current.plan().steps(), Math.max(0, current.index()));
    }

    private int have(String item) {
        return InventoryReader.count(ctx.player(), item);
    }

    private static PathingCommand pause() {
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
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
