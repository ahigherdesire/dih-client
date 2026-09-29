package baritone.ai.director;

import baritone.ai.tool.ToolResult;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Scripted tools and jobs, a hand-driven clock, and a record of what the director did. */
final class FakeHost implements DirectorHost {

    /** Results per tool, in order; the last one repeats. A tool with no script answers ok. */
    final Map<String, Deque<ToolResult>> results = new HashMap<>();
    /** Job states per job id, in order; the last one repeats. A job with no script is done at once. */
    final Map<String, Deque<JobStatus>> jobs = new HashMap<>();
    final List<String> ran = new ArrayList<>();
    final List<JsonObject> ranArgs = new ArrayList<>();
    final List<Boolean> ranConfirmed = new ArrayList<>();
    final List<String> reports = new ArrayList<>();
    final List<String> guardian = new ArrayList<>();
    final List<JsonObject> journals = new ArrayList<>();
    String autoStop;
    long now = 1_000_000L;
    int stops;

    FakeHost tool(String name, ToolResult... script) {
        this.results.computeIfAbsent(name, k -> new ArrayDeque<>()).addAll(List.of(script));
        return this;
    }

    FakeHost job(String id, JobStatus... script) {
        this.jobs.computeIfAbsent(id, k -> new ArrayDeque<>()).addAll(List.of(script));
        return this;
    }

    private static <T> T next(Deque<T> script) {
        return script.size() > 1 ? script.poll() : script.peek();
    }

    @Override
    public ToolResult runTool(String tool, JsonObject args, boolean confirmed) {
        this.ran.add(tool);
        this.ranArgs.add(args.deepCopy());
        this.ranConfirmed.add(confirmed);
        Deque<ToolResult> script = this.results.get(tool);
        return script == null ? ToolResult.ok(tool + " ok") : next(script);
    }

    @Override
    public JobStatus poll(String job) {
        Deque<JobStatus> script = this.jobs.get(job);
        return script == null ? JobStatus.done(job + " finished") : next(script);
    }

    @Override
    public String state() {
        return "{\"health\":20,\"inventory\":{}}";
    }

    @Override
    public String autoStop() {
        return this.autoStop;
    }

    @Override
    public List<String> guardianEvents() {
        List<String> out = new ArrayList<>(this.guardian);
        this.guardian.clear();
        return out;
    }

    @Override
    public long now() {
        return this.now;
    }

    @Override
    public void report(String line) {
        this.reports.add(line);
    }

    @Override
    public void stopJobs() {
        this.stops++;
    }

    @Override
    public void journal(JsonObject journal) {
        this.journals.add(journal.deepCopy());
    }

    JsonObject lastJournal() {
        return this.journals.get(this.journals.size() - 1);
    }
}
