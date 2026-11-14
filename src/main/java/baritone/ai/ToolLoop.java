package baritone.ai;

import baritone.ai.tool.ToolSession;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * One turn with the model: ask, run the tool calls it makes, feed the results back, until it answers in prose or
 * runs out of steps. Every request offers the run's visible tools ({@link ToolSession#definitions}), so a
 * {@code load_tools} call widens the list from the next step on.
 */
final class ToolLoop {

    enum End { ANSWERED, STEP_LIMIT, DISABLED, FAILED }

    /** The brain's side: its transcript, prompt and tool runner. */
    interface Host {
        boolean enabled();

        String systemPrompt();

        /** A snapshot of the conversation so far. */
        List<JsonObject> transcript();

        void append(JsonObject message);

        /** Appends {@code extra} to a tool result already in the transcript. */
        void amend(JsonObject toolResult, String extra);

        /** Events that arrived while tools ran; empty when there are none. */
        String lateEvents();

        void trim();

        void answer(String text);

        void failed(Exception e);

        String runTool(LlmClient.ToolCall call, ToolSession session);
    }

    private ToolLoop() {
    }

    static End run(ChatModel model, ToolSession session, Host host, int maxSteps) {
        int steps = Math.max(1, maxSteps);
        for (int step = 0; step < steps; step++) {
            if (!host.enabled()) {
                return End.DISABLED;
            }
            JsonArray messages = new JsonArray();
            messages.add(AiBrain.message("system", host.systemPrompt()));
            for (JsonObject entry : host.transcript()) {
                messages.add(entry);
            }

            LlmClient.Reply reply;
            try {
                reply = model.chat(messages, session.definitions());
            } catch (Exception e) {
                host.failed(e);
                return End.FAILED;
            }
            host.append(reply.rawMessage);

            if (!reply.hasToolCalls()) {
                if (!reply.content.isEmpty()) {
                    host.answer(reply.content);
                }
                return End.ANSWERED;
            }

            JsonObject lastResult = null;
            for (LlmClient.ToolCall call : reply.toolCalls) {
                lastResult = AiBrain.toolResult(call.id, host.runTool(call, session));
                host.append(lastResult);
            }
            // Events that arrived mid-turn ride along with the last tool result, as long as the model still has a
            // step left to act on them; otherwise they earn a new turn.
            if (lastResult != null && step < steps - 1) {
                String late = host.lateEvents();
                if (!late.isEmpty()) {
                    host.amend(lastResult, late);
                }
            }
            host.trim();
        }
        return End.STEP_LIMIT;
    }
}
