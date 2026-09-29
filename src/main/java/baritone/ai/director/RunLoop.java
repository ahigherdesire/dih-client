package baritone.ai.director;

import baritone.ai.tool.ToolResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The run loop both directors share: plan, run the steps in order, wait for jobs, and go back to planning only when a
 * step fails, a job gets stuck or the player says something. Subclasses decide how a plan is made.
 */
abstract class RunLoop implements Director {

    /** Jobs that never end by themselves: the step counts as done once they start, and they keep going. */
    static final Set<String> OPEN_ENDED = Set.of("follow", "farm", "explore", "trade");

    /** A plan couldn't be made; the message is for the player. */
    static final class PlanException extends Exception {
        PlanException(String message) {
            super(message);
        }
    }

    /** What the planner is told. {@code trouble} is null for the first plan. */
    record PlanRequest(String objective, String state, List<String> progress, String trouble, List<String> events) {
    }

    private static final class Step {
        final PlanStep plan;
        StepStatus status = StepStatus.PENDING;
        String job;
        String note = "";

        Step(PlanStep plan) {
            this.plan = plan;
        }
    }

    protected final DirectorHost host;
    protected final DirectorLimits limits;

    private String objective = "";
    private RunStatus status = RunStatus.IDLE;
    private RunStatus resumeTo = RunStatus.RUNNING;
    private final List<Step> steps = new ArrayList<>();
    private int current;
    private String lastReason = "";
    private long startedAt;
    private long stepStartedAt;
    private int planVersion;
    private String trouble;
    private final List<String> events = new ArrayList<>();
    private boolean awaitingConfirm;
    private boolean confirmNext;
    private JsonObject journal = new JsonObject();
    private final ReentrantLock lock = new ReentrantLock();
    private final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();
    private volatile DirectorState published = DirectorState.IDLE;

    RunLoop(DirectorHost host, DirectorLimits limits) {
        this.host = host;
        this.limits = limits;
    }

    /** Makes the next plan. Throws when it can't; the run then pauses with the message. */
    abstract Plan makePlan(PlanRequest request) throws PlanException;

    /** Whether a failure can be planned around; basic mode can't and hands back instead. */
    abstract boolean canReplan();

    abstract boolean basic();

    /** Why no more planning is allowed right now, or null. */
    String budgetExceeded() {
        return null;
    }

    /** The player resumed a paused run. */
    void onResume() {
    }

    int modelCalls() {
        return 0;
    }

    long promptTokens() {
        return 0;
    }

    long completionTokens() {
        return 0;
    }

    /** The first line for the player once the first plan is in. */
    abstract String intro(Plan plan);

    // ---------------------------------------------------------------- control

    @Override
    public void start(String objective) {
        this.lock.lock();
        try {
            begin(objective);
            publish();
        } finally {
            this.lock.unlock();
        }
    }

    private void begin(String objective) {
        this.objective = objective == null ? "" : objective.trim();
        this.status = RunStatus.PLANNING;
        this.startedAt = this.host.now();
        this.steps.clear();
        this.current = 0;
        this.planVersion = 0;
        this.trouble = null;
        this.events.clear();
        this.lastReason = "";
        this.journal = new JsonObject();
        this.journal.addProperty("objective", this.objective);
        this.journal.addProperty("mode", basic() ? "basic" : "smart");
        this.journal.addProperty("started_millis", System.currentTimeMillis());
        this.journal.add("plans", new JsonArray());
        this.journal.add("events", new JsonArray());
        saveJournal("running");
    }

    /** Ends a run before it started: nothing to plan. */
    protected void failAtStart(String objective, String reason) {
        this.lock.lock();
        try {
            begin(objective);
            finish(RunStatus.FAILED, reason);
            publish();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Applies a control now when the run isn't busy, else at the start of its next step: a step can take a while (a
     * model call, a tool), and the player's thread must never wait on it.
     */
    private void control(Runnable action) {
        if (this.lock.tryLock()) {
            try {
                action.run();
                publish();
            } finally {
                this.lock.unlock();
            }
        } else {
            this.pending.add(action);
        }
    }

    @Override
    public void onEvent(String text) {
        control(() -> {
            if (!this.status.isActive() || text == null || text.isBlank()) return;
            String said = "The player said: " + text.trim();
            event(said);
            if (!canReplan()) {
                this.host.report("Basic mode can't change plans mid-run. Stop it with .ai stop and start again.");
                return;
            }
            this.events.add(said);
            if (this.status == RunStatus.WAITING || this.status == RunStatus.RUNNING) {
                this.host.stopJobs();
                resetCurrent();
            }
            this.trouble = said;
            this.status = RunStatus.PLANNING;
        });
    }

    @Override
    public void pause(String why) {
        control(() -> {
            if (!this.status.isActive() || this.status == RunStatus.PAUSED) return;
            this.host.stopJobs();
            pauseHere(why);
        });
    }

    @Override
    public void resume() {
        control(this::resumeHere);
    }

    private void resumeHere() {
        if (this.status != RunStatus.PAUSED) return;
        this.awaitingConfirm = false;
        this.status = this.resumeTo;
        if (this.status == RunStatus.RUNNING) resetCurrent();
        onResume();
        event("resumed");
        this.host.report("Resumed.");
    }

    @Override
    public void stop(String why) {
        control(() -> {
            if (!this.status.isActive()) return;
            this.host.stopJobs();
            finish(RunStatus.STOPPED, why);
        });
    }

    @Override
    public void confirm() {
        control(() -> {
            if (this.status != RunStatus.PAUSED || !this.awaitingConfirm) return;
            this.confirmNext = true;
            resumeHere();
        });
    }

    // ---------------------------------------------------------------- the loop

    @Override
    public void step() {
        this.lock.lock();
        try {
            drainControls();
            stepHere();
            drainControls();
            publish();
        } finally {
            this.lock.unlock();
        }
    }

    private void drainControls() {
        Runnable action;
        while ((action = this.pending.poll()) != null) action.run();
    }

    private void stepHere() {
        if (!this.status.isActive() || this.status == RunStatus.PAUSED) return;
        long now = this.host.now();
        if (now - this.startedAt >= this.limits.maxRunMillis()) {
            this.host.stopJobs();
            finish(RunStatus.STOPPED, "Ran for " + duration(this.limits.maxRunMillis()) + ", the limit for one run.");
            return;
        }
        String autoStop = this.host.autoStop();
        if (autoStop != null) {
            this.host.stopJobs();
            pauseHere(autoStop);
            return;
        }
        for (String guardian : this.host.guardianEvents()) {
            this.events.add("Guardian: " + guardian);
            event("Guardian: " + guardian);
        }
        switch (this.status) {
            case PLANNING -> plan();
            case RUNNING -> runCurrent(now);
            case WAITING -> waitCurrent(now);
            default -> {
            }
        }
    }

    private void plan() {
        String over = budgetExceeded();
        if (over != null) {
            pauseHere(over);
            return;
        }
        PlanRequest request = new PlanRequest(this.objective, this.host.state(), progress(), this.trouble, List.copyOf(this.events));
        Plan plan;
        try {
            plan = makePlan(request);
        } catch (PlanException e) {
            pauseHere(e.getMessage());
            return;
        }
        this.events.clear();
        String why = this.trouble;
        this.trouble = null;
        this.planVersion++;
        recordPlan(plan, why);
        if (plan.steps().isEmpty()) {
            finish(RunStatus.DONE, plan.summary().isEmpty() ? "Nothing to do." : plan.summary());
            return;
        }
        this.steps.clear();
        plan.steps().forEach(step -> this.steps.add(new Step(step)));
        this.current = 0;
        this.status = RunStatus.RUNNING;
        this.host.report(this.planVersion == 1 ? intro(plan) : "New plan: " + checklist(plan));
    }

    private void runCurrent(long now) {
        if (this.current >= this.steps.size()) {
            finish(RunStatus.DONE, "Done: " + this.objective + ".");
            return;
        }
        Step step = this.steps.get(this.current);
        boolean confirmed = this.confirmNext;
        this.confirmNext = false;
        ToolResult result = this.host.runTool(step.plan.tool(), step.plan.args(), confirmed);
        event("step " + (this.current + 1) + " " + step.plan.describe() + ": " + result);
        if (Boolean.TRUE.equals(result.facts().get("needs_confirmation"))) {
            pauseHere(step.plan.tool() + " needs your OK first (" + step.plan.reason() + "). Type .ai confirm to allow it, or .ai stop.");
            this.awaitingConfirm = true;
            return;
        }
        switch (result.status()) {
            case OK -> finishStep(step, result.text());
            case RUNNING -> {
                String job = String.valueOf(result.facts().getOrDefault("job", step.plan.tool()));
                if (OPEN_ENDED.contains(job)) {
                    finishStep(step, "left running: " + firstLine(result.text()));
                    return;
                }
                step.status = StepStatus.RUNNING;
                step.job = job;
                step.note = firstLine(result.text());
                this.stepStartedAt = now;
                this.status = RunStatus.WAITING;
            }
            case FAILED, NEEDS -> trouble(step, result.text(), result.factsJson());
        }
    }

    private void waitCurrent(long now) {
        Step step = this.steps.get(this.current);
        JobStatus job = this.host.poll(step.job);
        switch (job.kind()) {
            case RUNNING -> {
                long limit = "acquire".equals(step.job) ? this.limits.acquireTimeoutMillis() : this.limits.stepTimeoutMillis();
                if (now - this.stepStartedAt > limit) {
                    this.host.stopJobs();
                    trouble(step, "The step took longer than " + duration(limit) + " and was stopped.", new JsonObject());
                }
            }
            case DONE -> {
                event("job " + step.job + " done: " + job.text());
                finishStep(step, job.text());
                this.status = RunStatus.RUNNING;
            }
            case FAILED -> trouble(step, job.text(), new JsonObject());
        }
    }

    private void finishStep(Step step, String text) {
        step.status = StepStatus.DONE;
        step.note = firstLine(text);
        this.current++;
    }

    private void trouble(Step step, String reason, JsonObject facts) {
        step.status = StepStatus.FAILED;
        step.note = firstLine(reason);
        String what = "Step " + (this.current + 1) + " (" + step.plan.describe() + ") failed: " + reason
                + (facts.isEmpty() ? "" : " Facts: " + facts);
        event(what);
        if (!canReplan()) {
            step.status = StepStatus.PENDING;
            this.status = RunStatus.RUNNING;
            pauseHere(what + " Fix it and type .ai resume to try again, or add an AI key for the smart AI.");
            return;
        }
        this.host.report("Step " + (this.current + 1) + " (" + step.plan.tool() + ") failed: " + firstLine(reason) + " Re-planning.");
        this.trouble = what;
        this.status = RunStatus.PLANNING;
    }

    /** Pauses without touching jobs; resuming continues from where the run was. */
    private void pauseHere(String why) {
        this.resumeTo = this.status == RunStatus.WAITING ? RunStatus.RUNNING : this.status;
        this.status = RunStatus.PAUSED;
        this.lastReason = why;
        event("paused: " + why);
        this.host.report("Paused: " + why);
        saveJournal("paused");
    }

    private void finish(RunStatus end, String why) {
        this.status = end;
        this.lastReason = why;
        event(end.id() + ": " + why);
        this.host.report((end == RunStatus.DONE ? "" : end == RunStatus.FAILED ? "Failed: " : "Stopped: ") + why);
        saveJournal(end.id());
    }

    private void resetCurrent() {
        if (this.current < this.steps.size()) {
            Step step = this.steps.get(this.current);
            step.status = StepStatus.PENDING;
            step.job = null;
        }
    }

    // ---------------------------------------------------------------- views

    /** The last published snapshot: never waits for a step to finish. */
    @Override
    public DirectorState state() {
        return this.published;
    }

    private void publish() {
        if (this.status == RunStatus.IDLE) {
            this.published = DirectorState.IDLE;
            return;
        }
        List<DirectorState.StepView> views = new ArrayList<>();
        for (Step step : this.steps) {
            views.add(new DirectorState.StepView(step.plan.tool(), step.plan.args() == null ? "{}" : step.plan.args().toString(),
                    step.plan.reason(), step.status, step.note));
        }
        this.published = new DirectorState(this.objective, basic(), this.status, views, this.current, this.lastReason,
                modelCalls(), promptTokens(), completionTokens(), this.startedAt, this.planVersion);
    }

    /** The plan so far, one line per step, for a re-plan. */
    private List<String> progress() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < this.steps.size(); i++) {
            Step step = this.steps.get(i);
            lines.add((i + 1) + ". [" + step.status.id() + "] " + step.plan.describe() + (step.note.isEmpty() ? "" : " - " + step.note));
        }
        return lines;
    }

    static String checklist(Plan plan) {
        StringBuilder sb = new StringBuilder(plan.summary().isEmpty() ? "" : plan.summary() + " ");
        for (int i = 0; i < plan.steps().size(); i++) {
            sb.append(i == 0 ? "" : ", ").append(i + 1).append(". ").append(plan.steps().get(i).tool());
        }
        return sb.toString().trim();
    }

    static String duration(long millis) {
        long seconds = millis / 1000;
        if (seconds < 120) return seconds + " s";
        return (seconds / 60) + " minutes";
    }

    private static String firstLine(String text) {
        if (text == null) return "";
        int newline = text.indexOf('\n');
        String line = newline < 0 ? text : text.substring(0, newline);
        return line.length() > 160 ? line.substring(0, 160) + "…" : line;
    }

    // ---------------------------------------------------------------- journal

    private void event(String text) {
        JsonObject entry = new JsonObject();
        entry.addProperty("seconds", Math.max(0, (this.host.now() - this.startedAt) / 1000));
        entry.addProperty("text", text);
        this.journal.getAsJsonArray("events").add(entry);
    }

    private void recordPlan(Plan plan, String why) {
        JsonObject entry = new JsonObject();
        entry.addProperty("version", this.planVersion);
        entry.addProperty("summary", plan.summary());
        if (why != null) entry.addProperty("because", why);
        JsonArray list = new JsonArray();
        for (PlanStep step : plan.steps()) {
            JsonObject json = new JsonObject();
            json.addProperty("tool", step.tool());
            json.add("args", step.args() == null ? new JsonObject() : step.args());
            json.addProperty("reason", step.reason());
            list.add(json);
        }
        entry.add("steps", list);
        this.journal.getAsJsonArray("plans").add(entry);
        saveJournal("running");
    }

    private void saveJournal(String outcome) {
        this.journal.addProperty("outcome", outcome);
        this.journal.addProperty("reason", this.lastReason);
        this.journal.addProperty("model_calls", modelCalls());
        this.journal.addProperty("prompt_tokens", promptTokens());
        this.journal.addProperty("completion_tokens", completionTokens());
        this.journal.addProperty("seconds", Math.max(0, (this.host.now() - this.startedAt) / 1000));
        this.host.journal(this.journal);
    }

    /** For tests and the panel: facts as they'd be shown. */
    static String facts(Map<String, Object> facts) {
        return facts.isEmpty() ? "" : facts.toString();
    }
}
