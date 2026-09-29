package baritone.ai.director;

import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The run loop with a scripted model and host: the model plans once and is called again only when something breaks. */
final class DirectorTest {

    private static final ToolRegistry TOOLS = ToolRegistry.withDefaults();
    private static final DirectorLimits LIMITS = new DirectorLimits(20, 90 * 60_000L, 10 * 60_000L, 30 * 60_000L);

    private static final String GOTO_THEN_LOOK = """
            {"summary":"walk there and look","steps":[
              {"tool":"goto","args":{"target":"1 2 3"},"reason":"get there"},
              {"tool":"look_around","args":{},"reason":"check"}]}""";

    private static LlmDirector llm(FakeLlm model, FakeHost host, DirectorLimits limits) {
        return new LlmDirector(model, TOOLS, host, limits, "- the base is at 0 64 0");
    }

    /** Steps until the run ends or waits for the player, a quarter second of game time per step. */
    private static DirectorState drive(Director director, FakeHost host) {
        for (int i = 0; i < 400; i++) {
            RunStatus status = director.state().status();
            if (status.isOver() || status == RunStatus.PAUSED) break;
            director.step();
            host.now += 250;
        }
        return director.state();
    }

    @Test
    void aPlanRunsWithoutMoreModelCalls() {
        FakeLlm model = new FakeLlm().plan(GOTO_THEN_LOOK);
        FakeHost host = new FakeHost()
                .tool("goto", ToolResult.running("goto", "Ran #goto 1 2 3."))
                .job("goto", JobStatus.running(), JobStatus.running(), JobStatus.done("arrived"));
        LlmDirector director = llm(model, host, LIMITS);
        director.start("go to 1 2 3 and look around");
        DirectorState state = drive(director, host);

        assertEquals(RunStatus.DONE, state.status(), state.lastReason());
        assertEquals(1, model.calls());
        assertEquals(List.of("goto", "look_around"), host.ran);
        assertEquals("1 2 3", host.ranArgs.get(0).get("target").getAsString());
        assertEquals(List.of(StepStatus.DONE, StepStatus.DONE), state.steps().stream().map(DirectorState.StepView::status).toList());
        assertEquals(1, state.modelCalls());
        assertEquals(1000, state.promptTokens());
        assertEquals(100, state.completionTokens());
        assertFalse(state.basic());
        assertTrue(model.lastMessage(0).contains("go to 1 2 3 and look around"), "the objective is in the request");
        assertTrue(model.lastMessage(0).contains("\"health\":20"), "so is the structured state");
    }

    @Test
    void aFailedStepTriggersExactlyOneReplan() {
        FakeLlm model = new FakeLlm()
                .plan("""
                        {"summary":"pick","steps":[{"tool":"acquire","args":{"item":"iron_pickaxe"},"reason":"need it"}]}""")
                .plan("""
                        {"summary":"table first","steps":[
                          {"tool":"acquire","args":{"item":"crafting_table"},"reason":"the pickaxe needs one"},
                          {"tool":"acquire","args":{"item":"iron_pickaxe"},"reason":"now it can be made"}]}""");
        FakeHost host = new FakeHost().tool("acquire",
                ToolResult.failed("No crafting table and no planks.").fact("item", "iron_pickaxe"),
                ToolResult.running("acquire", "Started"));
        LlmDirector director = llm(model, host, LIMITS);
        director.start("get an iron pickaxe");
        DirectorState state = drive(director, host);

        assertEquals(RunStatus.DONE, state.status(), state.lastReason());
        assertEquals(2, model.calls(), "one plan, one re-plan");
        String replan = model.lastMessage(1);
        assertTrue(replan.contains("No crafting table and no planks."), replan);
        assertTrue(replan.contains("acquire"), "names the step that failed");
        assertTrue(replan.contains("\"item\":\"iron_pickaxe\""), "and its facts: " + replan);
        assertEquals(List.of("acquire", "acquire", "acquire"), host.ran);
        assertEquals(2, state.planVersion());

        JsonObject journal = host.lastJournal();
        assertEquals("get an iron pickaxe", journal.get("objective").getAsString());
        assertEquals(2, journal.getAsJsonArray("plans").size());
        assertEquals("done", journal.get("outcome").getAsString());
        assertEquals(2, journal.get("model_calls").getAsInt());
        assertTrue(journal.getAsJsonArray("events").toString().contains("No crafting table"), journal.toString());
    }

    @Test
    void theCallBudgetPausesTheRunAndResumeAllowsMore() {
        FakeLlm model = new FakeLlm().plan("""
                {"summary":"try","steps":[{"tool":"acquire","args":{"item":"diamond"},"reason":"try"}]}""");
        FakeHost host = new FakeHost().tool("acquire", ToolResult.failed("Too dark to find any."));
        LlmDirector director = llm(model, host, new DirectorLimits(3, LIMITS.maxRunMillis(), LIMITS.stepTimeoutMillis(),
                LIMITS.acquireTimeoutMillis()));
        director.start("get a diamond");
        DirectorState state = drive(director, host);

        assertEquals(RunStatus.PAUSED, state.status());
        assertEquals(3, model.calls());
        assertTrue(state.lastReason().contains("3 model calls"), state.lastReason());

        director.resume();
        director.step();
        assertEquals(4, model.calls(), "resuming allows another round of calls");
    }

    @Test
    void aJobThatFailsOrTakesTooLongIsReplanned() {
        FakeLlm model = new FakeLlm().plan(GOTO_THEN_LOOK).plan(GOTO_THEN_LOOK);
        FakeHost host = new FakeHost()
                .tool("goto", ToolResult.running("goto", "Ran #goto."))
                .job("goto", JobStatus.failed("No path to 1 2 3."), JobStatus.done("arrived"));
        LlmDirector director = llm(model, host, LIMITS);
        director.start("go to 1 2 3");
        assertEquals(RunStatus.DONE, drive(director, host).status());
        assertTrue(model.lastMessage(1).contains("No path to 1 2 3."), model.lastMessage(1));

        FakeLlm slow = new FakeLlm().plan(GOTO_THEN_LOOK).answer("I can't get there.");
        FakeHost stuck = new FakeHost()
                .tool("goto", ToolResult.running("goto", "Ran #goto."))
                .job("goto", JobStatus.running());
        LlmDirector timed = llm(slow, stuck, new DirectorLimits(20, LIMITS.maxRunMillis(), 5_000, 5_000));
        timed.start("go to 1 2 3");
        DirectorState state = drive(timed, stuck);
        assertEquals(2, slow.calls());
        assertTrue(slow.lastMessage(1).contains("took longer than"), slow.lastMessage(1));
        assertEquals(RunStatus.PAUSED, state.status(), "prose instead of a plan hands back to the player");
        assertEquals("I can't get there.", state.lastReason());
        assertTrue(stuck.stops > 0, "the stuck job was stopped");
    }

    @Test
    void aDangerousStepWaitsForThePlayer() {
        FakeLlm model = new FakeLlm().plan("""
                {"summary":"ask","steps":[{"tool":"whisper","args":{"player":"Alex","message":"hi"},"reason":"asked to"}]}""");
        FakeHost host = new FakeHost().tool("whisper",
                ToolResult.failed("needs confirmation").fact("needs_confirmation", true), ToolResult.ok("Whispered."));
        LlmDirector director = llm(model, host, LIMITS);
        director.start("say hi to Alex");
        DirectorState state = drive(director, host);
        assertEquals(RunStatus.PAUSED, state.status());
        assertTrue(state.lastReason().contains("whisper") && state.lastReason().contains(".ai confirm"), state.lastReason());
        assertEquals(1, model.calls(), "waiting for the player is not a failure");
        assertTrue(state.awaitingConfirm(), "the panel shows confirm / deny");
        assertEquals(List.of("whisper: asked to"), host.confirmPrompts, "a prompt for when the panel is closed");

        director.confirm();
        DirectorState done = drive(director, host);
        assertEquals(RunStatus.DONE, done.status());
        assertFalse(done.awaitingConfirm());
        assertEquals(List.of(false, true), host.ranConfirmed);
    }

    @Test
    void autoStopPausesAndTheTimeLimitStops() {
        FakeLlm model = new FakeLlm().plan(GOTO_THEN_LOOK);
        FakeHost host = new FakeHost()
                .tool("goto", ToolResult.running("goto", "Ran #goto."))
                .job("goto", JobStatus.running());
        LlmDirector director = llm(model, host, LIMITS);
        director.start("go");
        director.step();
        director.step();
        host.autoStop = "Steve came within 12 blocks";
        DirectorState state = drive(director, host);
        assertEquals(RunStatus.PAUSED, state.status());
        assertEquals("Steve came within 12 blocks", state.lastReason());
        assertEquals(1, host.stops);
        assertTrue(host.reports.stream().anyMatch(line -> line.contains("Steve came within 12 blocks")), host.reports.toString());

        host.autoStop = null;
        director.resume();
        host.now += LIMITS.maxRunMillis();
        state = drive(director, host);
        assertEquals(RunStatus.STOPPED, state.status());
        assertTrue(state.lastReason().contains("90 minutes"), state.lastReason());
        assertEquals("stopped", host.lastJournal().get("outcome").getAsString());
    }

    @Test
    void playerInputAndGuardianEventsReachTheNextPlan() {
        FakeLlm model = new FakeLlm().plan(GOTO_THEN_LOOK).plan(GOTO_THEN_LOOK);
        FakeHost host = new FakeHost()
                .tool("goto", ToolResult.running("goto", "Ran #goto."))
                .job("goto", JobStatus.running(), JobStatus.running(), JobStatus.running(), JobStatus.done("arrived"));
        LlmDirector director = llm(model, host, LIMITS);
        director.start("go to 1 2 3");
        director.step();
        director.step();
        host.guardian.add("zombie 3 blocks away: fighting");
        director.onEvent("also grab some torches");
        drive(director, host);
        assertEquals(2, model.calls());
        String replan = model.lastMessage(1);
        assertTrue(replan.contains("also grab some torches"), replan);
        assertTrue(replan.contains("zombie 3 blocks away: fighting"), replan);
    }

    @Test
    void badPlansAreSentBackAndDescribeToolsAnswersFromTheRegistry() {
        FakeLlm model = new FakeLlm()
                .call("describe_tools", "{\"tools\":\"goto\"}")
                .plan("{\"summary\":\"fly\",\"steps\":[{\"tool\":\"fly\",\"args\":{},\"reason\":\"fast\"}]}")
                .plan("{\"summary\":\"no target\",\"steps\":[{\"tool\":\"goto\",\"args\":{},\"reason\":\"go\"}]}")
                .plan(GOTO_THEN_LOOK);
        FakeHost host = new FakeHost();
        LlmDirector director = llm(model, host, LIMITS);
        director.start("go to 1 2 3");
        assertEquals(RunStatus.DONE, drive(director, host).status());
        assertEquals(4, model.calls());
        assertTrue(model.lastMessage(1).contains("\"target\""), "describe_tools gives the schema: " + model.lastMessage(1));
        assertTrue(model.lastMessage(2).contains("fly") && model.lastMessage(2).contains("no tool"), model.lastMessage(2));
        assertTrue(model.lastMessage(3).contains("target"), model.lastMessage(3));
        assertEquals(List.of("goto", "look_around"), host.ran);
    }

    @Test
    void anEmptyPlanMeansDoneOrImpossible() {
        FakeLlm done = new FakeLlm().plan("{\"summary\":\"You already have one.\",\"steps\":[]}");
        FakeHost host = new FakeHost();
        LlmDirector director = llm(done, host, LIMITS);
        director.start("get a bed");
        DirectorState state = drive(director, host);
        assertEquals(RunStatus.DONE, state.status());
        assertEquals("You already have one.", state.lastReason());
        assertTrue(host.ran.isEmpty());
    }

    @Test
    void thePromptAndToolListStayTheSameAcrossCalls() {
        FakeLlm model = new FakeLlm().plan(GOTO_THEN_LOOK).plan(GOTO_THEN_LOOK);
        FakeHost host = new FakeHost()
                .tool("goto", ToolResult.failed("blocked"), ToolResult.running("goto", "ok"));
        LlmDirector director = llm(model, host, LIMITS);
        director.start("go");
        drive(director, host);
        assertEquals(2, model.calls());
        assertEquals(model.message(0, 0), model.message(1, 0), "the system prompt is fixed for the run");
        assertEquals(model.tools.get(0), model.tools.get(1), "and so are the tools offered");
        String system = model.message(0, 0);
        assertTrue(system.contains("the base is at 0 64 0"), "memory is in the system prompt");
        assertTrue(system.contains("goto(") && system.contains("acquire("), "job tools are listed with their parameters");
        assertFalse(system.contains("\"health\""), "changing facts stay out of the system prompt");
        assertFalse(system.contains("cmd_"), "adapters aren't listed one by one");
    }

    @Test
    void basicModeRunsRuleTablePlansWithoutAModel() {
        BasicRules rules = new BasicRules(text -> Map.of("oak logs", "minecraft:oak_log", "oak log", "minecraft:oak_log")
                .entrySet().stream().filter(e -> e.getKey().equals(text)).map(Map.Entry::getValue).findFirst());
        FakeHost host = new FakeHost().tool("acquire", ToolResult.running("acquire", "Started"));
        BasicDirector director = new BasicDirector(rules, host, LIMITS);
        director.start("get 10 oak logs");
        DirectorState state = drive(director, host);
        assertEquals(RunStatus.DONE, state.status(), state.lastReason());
        assertTrue(state.basic());
        assertEquals(0, state.modelCalls());
        assertEquals(List.of("acquire"), host.ran);
        assertEquals("minecraft:oak_log", host.ranArgs.get(0).get("item").getAsString());
        assertEquals(10, host.ranArgs.get(0).get("count").getAsInt());
        assertTrue(host.reports.get(0).startsWith("Basic mode"), host.reports.toString());

        BasicDirector unknown = new BasicDirector(rules, new FakeHost(), LIMITS);
        unknown.start("write me a poem");
        assertEquals(RunStatus.FAILED, unknown.state().status());
        assertEquals(BasicRules.UNKNOWN, unknown.state().lastReason());

        FakeHost failing = new FakeHost().tool("acquire", ToolResult.failed("No oak trees nearby."));
        BasicDirector stuck = new BasicDirector(rules, failing, LIMITS);
        stuck.start("get 10 oak logs");
        DirectorState paused = drive(stuck, failing);
        assertEquals(RunStatus.PAUSED, paused.status(), "basic mode can't reflect: it hands back");
        assertTrue(paused.lastReason().contains("No oak trees nearby."), paused.lastReason());
    }

    @Test
    void stopEndsTheRunAndStopsTheJob() {
        FakeLlm model = new FakeLlm().plan(GOTO_THEN_LOOK);
        FakeHost host = new FakeHost().tool("goto", ToolResult.running("goto", "ok")).job("goto", JobStatus.running());
        LlmDirector director = llm(model, host, LIMITS);
        director.start("go");
        director.step();
        director.step();
        director.stop("you said stop");
        assertEquals(RunStatus.STOPPED, director.state().status());
        assertEquals(1, host.stops);
        director.step();
        assertEquals(List.of("goto"), host.ran, "a stopped run does nothing more");
    }
}
