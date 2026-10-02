package baritone.ai;

import com.google.gson.JsonArray;

import java.io.IOException;

/** A chat model with tool calling. {@link LlmClient} is the real one; tests script their own. */
public interface ChatModel {

    LlmClient.Reply chat(JsonArray messages, JsonArray tools) throws IOException;

    /** Prompt tokens used so far, as the provider reported them. */
    default long promptTokens() {
        return 0;
    }

    default long completionTokens() {
        return 0;
    }
}
