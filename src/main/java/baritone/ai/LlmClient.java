/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A tiny OpenAI-compatible chat client built on {@link HttpURLConnection} and Gson — both
 * already on the classpath, so the mod gains no new dependencies.
 *
 * <p>Works against anything that implements {@code POST /chat/completions} with tool calling:
 * DashScope (qwen), OpenRouter, a local Ollama, vLLM, OpenAI itself.
 *
 * <p>The newer {@code java.net.http.HttpClient} would be nicer, but it builds an NIO
 * {@link java.nio.channels.Selector}, and on some Windows setups (including the one this fork
 * is developed on — see the toolchain note in build.gradle) opening a selector fails outright
 * with "Unable to establish loopback connection". Blocking sockets have no such problem.
 *
 * <p>Every method here blocks. Call it from the AI worker thread, never from the game thread.
 */
public final class LlmClient {

    private static final Gson GSON = new Gson();
    private static final List<Integer> RETRYABLE = Arrays.asList(408, 409, 425, 429, 500, 502, 503, 504);
    private static final int MAX_ATTEMPTS = 3;

    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 90_000;

    private final AiConfig config;

    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();
    private final AtomicLong calls = new AtomicLong();

    public LlmClient(AiConfig config) {
        this.config = config;
    }

    public long getPromptTokens() {
        return this.promptTokens.get();
    }

    public long getCompletionTokens() {
        return this.completionTokens.get();
    }

    public long getCalls() {
        return this.calls.get();
    }

    /** One request/response round trip. */
    public Reply chat(JsonArray messages, JsonArray tools) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", this.config.model);
        body.add("messages", messages);
        body.addProperty("temperature", 0.5);
        if (tools != null && !tools.isEmpty()) {
            body.add("tools", tools);
            body.addProperty("tool_choice", "auto");
        }
        mergeExtraBody(body);

        IOException last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                sleep(700L * (1L << (attempt - 1)));
            }
            try {
                return parse(post(body));
            } catch (RetryableException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("LLM request failed") : last;
    }

    private void mergeExtraBody(JsonObject body) {
        String extra = this.config.extraBody;
        if (extra == null || extra.isBlank()) {
            return;
        }
        try {
            JsonObject parsed = JsonParser.parseString(extra).getAsJsonObject();
            for (String key : parsed.keySet()) {
                body.add(key, parsed.get(key));
            }
        } catch (Exception e) {
            System.err.println("[DIH] extraBody is not a JSON object, ignoring: " + e.getMessage());
        }
    }

    private String post(JsonObject body) throws IOException {
        String url = this.config.baseUrl.replaceAll("/+$", "") + "/chat/completions";
        byte[] payload = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);

        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        } catch (IllegalArgumentException e) {
            throw new IOException("bad base URL \"" + this.config.baseUrl + "\"");
        }

        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + this.config.resolveKey());
            connection.setFixedLengthStreamingMode(payload.length);

            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            } catch (IOException e) {
                throw new RetryableException("could not send request: " + e.getMessage());
            }

            int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException e) {
                throw new RetryableException("no response: " + e.getMessage());
            }

            String responseBody = read(status / 100 == 2 ? connection.getInputStream() : connection.getErrorStream());
            if (status / 100 != 2) {
                String snippet = responseBody.length() > 400 ? responseBody.substring(0, 400) : responseBody;
                String message = "model returned HTTP " + status + ": " + snippet;
                if (RETRYABLE.contains(status)) {
                    throw new RetryableException(message);
                }
                throw new IOException(message);
            }
            return responseBody;
        } finally {
            connection.disconnect();
        }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RetryableException("could not read response: " + e.getMessage());
        }
    }

    private Reply parse(String json) throws IOException {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("model returned malformed JSON");
        }

        this.calls.incrementAndGet();
        if (root.has("usage") && root.get("usage").isJsonObject()) {
            JsonObject usage = root.getAsJsonObject("usage");
            if (usage.has("prompt_tokens")) this.promptTokens.addAndGet(usage.get("prompt_tokens").getAsLong());
            if (usage.has("completion_tokens")) this.completionTokens.addAndGet(usage.get("completion_tokens").getAsLong());
        }

        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IOException("model returned no choices");
        }
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null) {
            throw new IOException("model returned no message");
        }

        String content = "";
        JsonElement contentElement = message.get("content");
        if (contentElement != null && contentElement.isJsonPrimitive()) {
            content = contentElement.getAsString().trim();
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        JsonArray rawCalls = message.getAsJsonArray("tool_calls");
        if (rawCalls != null) {
            for (JsonElement element : rawCalls) {
                JsonObject call = element.getAsJsonObject();
                JsonObject function = call.getAsJsonObject("function");
                if (function == null) {
                    continue;
                }
                String id = call.has("id") ? call.get("id").getAsString() : "call_" + toolCalls.size();
                String name = function.has("name") ? function.get("name").getAsString() : "";
                String argsRaw = function.has("arguments") ? function.get("arguments").getAsString() : "{}";
                JsonObject args = new JsonObject();
                try {
                    if (!argsRaw.isBlank()) {
                        args = JsonParser.parseString(argsRaw).getAsJsonObject();
                    }
                } catch (Exception ignored) {
                    // A model that emits broken JSON gets told so by the tool result.
                    args.addProperty("__malformed", argsRaw);
                }
                toolCalls.add(new ToolCall(id, name, args));
            }
        }
        return new Reply(content, toolCalls, message);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A single tool invocation the model asked for. */
    public static final class ToolCall {
        public final String id;
        public final String name;
        public final JsonObject arguments;

        ToolCall(String id, String name, JsonObject arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }

        public String string(String key, String fallback) {
            try {
                JsonElement element = this.arguments.get(key);
                return element == null || element.isJsonNull() ? fallback : element.getAsString();
            } catch (Exception e) {
                return fallback;
            }
        }

        public int integer(String key, int fallback) {
            try {
                JsonElement element = this.arguments.get(key);
                return element == null || element.isJsonNull() ? fallback : element.getAsInt();
            } catch (Exception e) {
                return fallback;
            }
        }
    }

    /** What came back: prose, tool calls, or both. */
    public static final class Reply {
        public final String content;
        public final List<ToolCall> toolCalls;
        /** The assistant message verbatim, to be appended to the transcript. */
        public final JsonObject rawMessage;

        Reply(String content, List<ToolCall> toolCalls, JsonObject rawMessage) {
            this.content = content;
            this.toolCalls = toolCalls;
            this.rawMessage = rawMessage;
        }

        public boolean hasToolCalls() {
            return !this.toolCalls.isEmpty();
        }
    }

    private static final class RetryableException extends IOException {
        RetryableException(String message) {
            super(message);
        }
    }
}
