package baritone.ai.director;

import baritone.ai.ChatModel;
import baritone.ai.LlmClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A model that plays back scripted replies and records every request, with made-up token counts. */
final class FakeLlm implements ChatModel {

    final Deque<LlmClient.Reply> replies = new ArrayDeque<>();
    final List<JsonArray> requests = new ArrayList<>();
    final List<JsonArray> tools = new ArrayList<>();
    private long prompt;
    private long completion;

    /** Replies with a {@code submit_plan} call; {@code json} is its arguments. */
    FakeLlm plan(String json) {
        return call("submit_plan", json);
    }

    FakeLlm call(String tool, String json) {
        JsonObject args = JsonParser.parseString(json).getAsJsonObject();
        JsonObject raw = new JsonObject();
        raw.addProperty("role", "assistant");
        this.replies.add(new LlmClient.Reply("", List.of(new LlmClient.ToolCall("call_" + this.replies.size(), tool, args)), raw));
        return this;
    }

    FakeLlm answer(String text) {
        JsonObject raw = new JsonObject();
        raw.addProperty("role", "assistant");
        raw.addProperty("content", text);
        this.replies.add(new LlmClient.Reply(text, List.of(), raw));
        return this;
    }

    /** The last scripted reply repeats once the script runs out. */
    @Override
    public LlmClient.Reply chat(JsonArray messages, JsonArray tools) throws IOException {
        this.requests.add(messages.deepCopy());
        this.tools.add(tools == null ? new JsonArray() : tools.deepCopy());
        this.prompt += 1000;
        this.completion += 100;
        if (this.replies.isEmpty()) throw new IOException("the script ran out");
        return this.replies.size() > 1 ? this.replies.poll() : this.replies.peek();
    }

    @Override
    public long promptTokens() {
        return this.prompt;
    }

    @Override
    public long completionTokens() {
        return this.completion;
    }

    int calls() {
        return this.requests.size();
    }

    /** The text of message {@code index} (0 = system) of request {@code call}. */
    String message(int call, int index) {
        return this.requests.get(call).get(index).getAsJsonObject().get("content").getAsString();
    }

    /** The last message of request {@code call}: what changed since the system prompt. */
    String lastMessage(int call) {
        JsonArray request = this.requests.get(call);
        return request.get(request.size() - 1).getAsJsonObject().get("content").getAsString();
    }
}
