package baritone.beat;

import baritone.Baritone;
import baritone.acquire.AcquireControl;
import baritone.acquire.exec.AcquireProcess;
import baritone.acquire.exec.BaritoneWorldView;
import baritone.acquire.exec.InventoryReader;
import baritone.acquire.knowledge.VanillaKnowledge;
import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.Goal;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.planner.AcquirePlanner;
import baritone.ai.AiBrain;
import baritone.ai.director.JobStatus;
import baritone.ai.director.LiveDirectorHost;
import baritone.api.event.events.TickEvent;
import baritone.api.utils.Helper;
import baritone.behavior.Behavior;

import java.io.IOException;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * {@code #beat}: beats the game as a campaign of {@link Phase}s. Each phase's next goal goes to {@code #acquire}
 * (items, or a place, or the dragon), which runs it under the Guardian like any Baritone job. A finished goal moves
 * the campaign on; a failed or stopped one pauses it with the reason. The campaign is saved to
 * {@code baritone/beat/<world>.json} on every phase change and every 60 seconds, so {@code #beat resume} continues
 * after a crash, a disconnect or a restart.
 *
 * <p>Game thread only, like {@code #acquire}.
 */
public final class BeatCampaign extends Behavior {
    private static final long SAVE_EVERY_TICKS = 20L * 60;
    private static volatile BeatCampaign primary;

    private Campaign campaign;
    private boolean running;
    /** The goal handed to {@code #acquire}, while it runs. */
    private Goal waiting;
    /** How the last run ended, for a director waiting on the "beat" job: null while running or never run. */
    private JobStatus outcome;
    private long ticks;
    private long savedAt;
    private boolean listening;

    public BeatCampaign(Baritone baritone) {
        super(baritone);
        if (primary == null) {
            primary = this;
            LiveDirectorHost.registerJob("beat", new LiveDirectorHost.ClientJob(this::jobStatus,
                    () -> net.minecraft.client.Minecraft.getInstance().execute(this::stop)));
        }
    }

    /** The primary player's campaign, or null before Baritone has loaded. */
    public static BeatCampaign get() {
        return primary;
    }

    // ---------------------------------------------------------------- commands

    /** Every phase from the saved one (or the start), planned from what you hold now. */
    public String plan() {
        requireWorld();
        Campaign saved = current();
        Phase from = saved == null ? Phase.GEAR : saved.phase();
        InventorySnapshot inv = InventoryReader.snapshot(ctx.player());
        AcquirePlanner planner = new AcquirePlanner(VanillaKnowledge.get(), WorldView.UNKNOWN, AcquireProcess.options());
        List<CampaignChain.PhasePlan> chain = CampaignChain.plan(planner, inv, BaritoneWorldView.dimension(ctx.world()), from);
        List<String> lines = new java.util.ArrayList<>();
        lines.add(saved == null ? "#beat, from the start:" : "#beat, from the saved " + saved.phase().number() + ":");
        lines.addAll(CampaignChain.explain(chain));
        return String.join("\n", lines);
    }

    /** Starts beating the game, or carries on with this world's saved campaign. */
    public String start() {
        requireWorld();
        if (running) return "#beat is already running: " + status();
        Campaign saved = current();
        if (saved == null) {
            campaign = new Campaign(AiBrain.currentWorldKey(), System.currentTimeMillis());
            save();
            say("#beat started. " + campaign.statusLine(have()) + ". #beat plan shows the route, #beat stop stops.");
        } else {
            campaign = saved;
            say("#beat continues at " + campaign.statusLine(have()) + ".");
        }
        return runPhase();
    }

    /** Continues the saved campaign for this world. */
    public String resume() {
        requireWorld();
        if (running) return "#beat is already running: " + status();
        Campaign saved = current();
        if (saved == null) return "No saved #beat for this world. #beat starts one.";
        campaign = saved;
        say("#beat resumes at " + campaign.statusLine(have()) + ".");
        return runPhase();
    }

    public String stop() {
        if (!running) return campaign == null ? "#beat isn't running." : "#beat is already stopped at " + campaign.statusLine(have()) + ".";
        running = false;
        Goal was = waiting;
        waiting = null;
        AcquireControl acquire = AcquireControl.get();
        if (was != null && acquire != null && acquire.isActive()) acquire.stop();
        campaign.setProblem("stopped");
        outcome = JobStatus.failed("stopped");
        save();
        return "#beat stopped at " + campaign.statusLine(have()) + ". #beat resume carries on.";
    }

    /** Forgets this world's campaign and starts over. */
    public String restart() {
        requireWorld();
        if (running) stop();
        try {
            store().delete(AiBrain.currentWorldKey());
        } catch (IOException e) {
            return "Couldn't delete the saved #beat: " + e.getMessage();
        }
        campaign = null;
        return start();
    }

    public String status() {
        Campaign shown = campaign != null ? campaign : ctx.player() == null ? null : current();
        if (shown == null) return "No #beat in this world. #beat starts one, #beat plan shows the route.";
        String line = shown.statusLine(have());
        AcquireControl acquire = AcquireControl.get();
        if (running) return line + (waiting == null || acquire == null ? "" : " (" + acquire.status() + ")");
        return line + (shown.problem().isEmpty() ? " (stopped)" : " (stopped: " + shown.problem() + ")");
    }

    public boolean isRunning() {
        return running;
    }

    /** For a director step waiting on {@code beat_stage start}. */
    public JobStatus jobStatus() {
        if (running) return JobStatus.running();
        return outcome == null ? JobStatus.failed("#beat isn't running") : outcome;
    }

    /** The saved campaign file, for tests. */
    public java.nio.file.Path file() {
        return store().file(AiBrain.currentWorldKey());
    }

    /** Drops what is held in memory, as a restart would; the file stays. */
    public void forget() {
        if (running) stop();
        campaign = null;
        outcome = null;
    }

    // ---------------------------------------------------------------- the loop

    /** Hands the current phase's next goal to #acquire, moving past phases that are already met. */
    private String runPhase() {
        listen();
        AcquireControl acquire = AcquireControl.get();
        if (acquire == null) return pause("#acquire didn't load");
        for (int guard = 0; ; guard++) {
            if (guard > Phase.values().length + Phase.GEAR_PIECES.size()) return pause("the phases keep saying they're done");
            Phase phase = campaign.phase();
            List<Goal> goals = phase.goals(have());
            if (goals.isEmpty()) {
                if (!advance()) return "#beat is done.";
                continue;
            }
            Goal goal = goals.get(0);
            String started;
            try {
                started = acquire.startGoal(goal);
            } catch (IllegalArgumentException e) {
                return pause(e.getMessage());
            }
            if (started.startsWith("You already have") || started.startsWith("Already done")) {
                if (phase != Phase.GEAR && !advance()) return "#beat is done.";
                continue;
            }
            waiting = goal;
            running = true;
            outcome = null;
            campaign.setProblem("");
            save();
            String line = campaign.statusLine(have());
            say(line);
            return line;
        }
    }

    /** Next phase (saved); false after the last. */
    private boolean advance() {
        campaign.count(have());
        if (!campaign.next()) {
            running = false;
            waiting = null;
            outcome = JobStatus.done("the dragon is dead");
            save();
            say("#beat: the game is beaten.");
            return false;
        }
        save();
        say(campaign.statusLine(have()));
        return true;
    }

    private String pause(String why) {
        running = false;
        waiting = null;
        String reason = why == null || why.isBlank() ? "it stopped" : why;
        campaign.setProblem(reason);
        outcome = JobStatus.failed(reason);
        save();
        String line = "#beat paused at " + campaign.statusLine(have()) + ": " + reason + " #beat resume tries again.";
        say(line);
        return line;
    }

    private void listen() {
        if (listening) return;
        AcquireControl acquire = AcquireControl.get();
        if (acquire == null) return;
        listening = true;
        acquire.addListener(this::onAcquire);
    }

    private void onAcquire(AcquireControl.AcquireEvent event) {
        if (!running || waiting == null || !waiting.label().equals(event.item())) return;
        switch (event.kind()) {
            case DONE -> {
                Phase phase = campaign.phase();
                Goal done = waiting;
                waiting = null;
                // An item phase is done when its items are held; a place (or the dragon) when its goal finished.
                boolean phaseDone = phase.goals(have()).isEmpty() || !(done instanceof Goal.ItemGoal);
                if (phaseDone && !advance()) return;
                runPhase();
            }
            case FAILED, STOPPED -> pause(event.message());
            default -> {
            }
        }
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN || !running) return;
        if (++ticks - savedAt >= SAVE_EVERY_TICKS) {
            campaign.count(have());
            save();
        }
    }

    // ---------------------------------------------------------------- plumbing

    private Campaign current() {
        if (campaign != null && campaign.world().equals(AiBrain.currentWorldKey())) return campaign;
        return store().load(AiBrain.currentWorldKey()).orElse(null);
    }

    private CampaignStore store() {
        return new CampaignStore(baritone.getDirectory().resolve("beat"));
    }

    private void save() {
        if (campaign == null) return;
        savedAt = ticks;
        try {
            store().save(campaign, System.currentTimeMillis());
        } catch (IOException e) {
            System.err.println("[DIH] could not save the #beat campaign: " + e);
        }
    }

    private ToIntFunction<String> have() {
        InventorySnapshot inv = ctx.player() == null ? InventorySnapshot.empty() : InventoryReader.snapshot(ctx.player());
        return inv::count;
    }

    private void requireWorld() {
        if (ctx.player() == null || ctx.world() == null) throw new IllegalArgumentException("Join a world first.");
    }

    private static void say(String line) {
        Helper.HELPER.logDirect(line);
    }
}
