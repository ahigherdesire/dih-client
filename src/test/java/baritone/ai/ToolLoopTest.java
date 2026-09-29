package baritone.ai;

import baritone.ai.tool.AiTool;
import baritone.ai.tool.MetaTools;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSession;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The model sees job tools plus the categories it loaded, in a stable order, and nothing else. */
final class ToolLoopTest {

    /** Plays back canned replies and records the tool names each request offered. */
    private static final class FakeModel implements ChatModel {
        final Deque<LlmClient.Reply> replies = new ArrayDeque<>();
        final List<List<String>> offered = new ArrayList<>();
        final List<JsonArray> definitions = new ArrayList<>();

        FakeModel call(String tool, JsonObject args) {
            LlmClient.ToolCall call = new LlmClient.ToolCall("call_" + this.replies.size(), tool, args);
            JsonObject raw = new JsonObject();
            raw.addProperty("role", "assistant");
            this.replies.add(new LlmClient.Reply("", List.of(call), raw));
            return this;
        }

        FakeModel answer(String text) {
            JsonObject raw = new JsonObject();
            raw.addProperty("role", "assistant");
            raw.addProperty("content", text);
            this.replies.add(new LlmClient.Reply(text, List.of(), raw));
            return this;
        }

        @Override
        public LlmClient.Reply chat(JsonArray messages, JsonArray tools) {
            List<String> names = new ArrayList<>();
            for (JsonElement element : tools) {
                names.add(element.getAsJsonObject().getAsJsonObject("function").get("name").getAsString());
            }
            this.offered.add(names);
            this.definitions.add(tools.deepCopy());
            return this.replies.size() > 1 ? this.replies.poll() : this.replies.peek();
        }
    }

    /** Records what the loop did; tools go through the registry like the real brain's. */
    private static final class Host implements ToolLoop.Host {
        final List<JsonObject> transcript = new ArrayList<>();
        final List<String> ran = new ArrayList<>();
        String answer;

        @Override public boolean enabled() { return true; }
        @Override public String systemPrompt() { return "system"; }
        @Override public List<JsonObject> transcript() { return new ArrayList<>(this.transcript); }
        @Override public void append(JsonObject message) { this.transcript.add(message); }
        @Override public void amend(JsonObject toolResult, String extra) { }
        @Override public String lateEvents() { return ""; }
        @Override public void trim() { }
        @Override public void answer(String text) { this.answer = text; }
        @Override public void failed(Exception e) { throw new AssertionError(e); }

        @Override
        public String runTool(LlmClient.ToolCall call, ToolSession session) {
            this.ran.add(call.name);
            return session.registry().call(ToolContext.of(null, ToolContext.Source.AI).withSession(session),
                    call.name, call.arguments).forModel();
        }

        String lastToolResult() {
            for (int i = this.transcript.size() - 1; i >= 0; i--) {
                if ("tool".equals(this.transcript.get(i).get("role").getAsString())) {
                    return this.transcript.get(i).get("content").getAsString();
                }
            }
            return "";
        }
    }

    private static AiTool tool(String name, ToolCategory category, boolean job) {
        AiTool.Builder builder = AiTool.builder(name, category)
                .summary("Does " + name + ".")
                .handler((ctx, args) -> ToolResult.ok(name + " done"));
        return (job ? builder.job() : builder).build();
    }

    private static ToolRegistry registry() {
        ToolRegistry registry = new ToolRegistry()
                .register(tool("look", ToolCategory.JOB, true))
                .register(tool("strike", ToolCategory.COMBAT, false))
                .register(tool("dig", ToolCategory.MINING, false))
                .register(tool("block", ToolCategory.COMBAT, false));
        return MetaTools.register(registry);
    }

    private static JsonObject category(String id) {
        JsonObject args = new JsonObject();
        args.addProperty("category", id);
        return args;
    }

    @Test
    void definitionsGrowAfterLoadToolsAndStayInOrder() {
        FakeModel model = new FakeModel()
                .call("load_tools", category("combat"))
                .call("strike", new JsonObject())
                .answer("done");
        Host host = new Host();

        ToolLoop.End end = ToolLoop.run(model, new ToolSession(registry()), host, 6);

        assertEquals(ToolLoop.End.ANSWERED, end);
        assertEquals("done", host.answer);
        assertEquals(List.of("load_tools", "strike"), host.ran);
        assertEquals(3, model.offered.size());
        assertEquals(List.of("look", "load_tools", "list_tools"), model.offered.get(0));
        assertEquals(List.of("look", "strike", "block", "load_tools", "list_tools"), model.offered.get(1));
        assertEquals(model.definitions.get(1), model.definitions.get(2), "loaded tools stay, byte for byte");
        assertFalse(model.offered.get(2).contains("dig"), "mining was never loaded");
        assertTrue(host.lastToolResult().contains("strike done"));
    }

    @Test
    void aToolFromAnUnloadedCategoryIsRefusedWithDirections() {
        FakeModel model = new FakeModel()
                .call("dig", new JsonObject())
                .answer("ok");
        Host host = new Host();

        ToolLoop.run(model, new ToolSession(registry()), host, 6);

        String result = host.lastToolResult();
        assertTrue(result.contains("load_tools") && result.contains("mining"), result);
    }

    @Test
    void listToolsNamesCategoriesAndTheirTools() {
        FakeModel model = new FakeModel()
                .call("list_tools", new JsonObject())
                .call("list_tools", category("combat"))
                .answer("ok");
        Host host = new Host();
        ToolLoop.run(model, new ToolSession(registry()), host, 6);

        JsonObject overview = host.transcript.get(1);
        String categories = overview.get("content").getAsString();
        assertTrue(categories.contains("combat: 2 tools") && categories.contains("mining: 1 tool"), categories);
        assertFalse(categories.contains("job:"), "job tools are always there");
        String combat = host.lastToolResult();
        assertTrue(combat.contains("strike: Does strike.") && combat.contains("block"), combat);
    }

    @Test
    void stopsAtTheStepLimit() {
        FakeModel model = new FakeModel().call("look", new JsonObject());
        Host host = new Host();
        assertEquals(ToolLoop.End.STEP_LIMIT, ToolLoop.run(model, new ToolSession(registry()), host, 3));
        assertEquals(3, model.offered.size());
    }
}
