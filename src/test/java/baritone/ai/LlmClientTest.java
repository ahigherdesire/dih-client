package baritone.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The OpenAI-style client against a local server: what it sends, what it parses, when it retries. */
final class LlmClientTest {

    private record Canned(int status, String body) {
    }

    private HttpServer server;
    private final Deque<Canned> script = new ArrayDeque<>();
    private final List<JsonObject> bodies = Collections.synchronizedList(new ArrayList<>());
    private final List<String> auth = Collections.synchronizedList(new ArrayList<>());
    private LlmClient client;

    @BeforeEach
    void start() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/v1/chat/completions", exchange -> {
            this.bodies.add(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject());
            this.auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            Canned canned = this.script.size() > 1 ? this.script.poll() : this.script.peek();
            byte[] out = canned.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(canned.status(), out.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(out);
            }
        });
        this.server.start();
        AiConfig config = new AiConfig();
        config.baseUrl = "http://127.0.0.1:" + this.server.getAddress().getPort() + "/v1/";
        config.model = "test-model";
        config.apiKey = "sk-test";
        this.client = new LlmClient(config);
    }

    @AfterEach
    void stop() {
        this.server.stop(0);
    }

    private static String reply(String message, int prompt, int completion) {
        return "{\"choices\":[{\"message\":" + message + "}],\"usage\":{\"prompt_tokens\":" + prompt
                + ",\"completion_tokens\":" + completion + "}}";
    }

    private static JsonArray userSays(String text) {
        JsonArray messages = new JsonArray();
        messages.add(AiBrain.message("user", text));
        return messages;
    }

    @Test
    void sendsTheModelToolsAndKeyAndParsesToolCalls() throws IOException {
        this.script.add(new Canned(200, reply("""
                {"role":"assistant","content":null,"tool_calls":[
                  {"id":"call_1","type":"function","function":{"name":"goto","arguments":"{\\"target\\":\\"1 2 3\\"}"}},
                  {"id":"call_2","type":"function","function":{"name":"look_around","arguments":""}}]}""", 120, 30)));
        JsonArray tools = new JsonArray();
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tools.add(tool);

        LlmClient.Reply reply = this.client.chat(userSays("go"), tools);

        assertEquals(2, reply.toolCalls.size());
        assertEquals("goto", reply.toolCalls.get(0).name);
        assertEquals("1 2 3", reply.toolCalls.get(0).string("target", ""));
        assertEquals("call_2", reply.toolCalls.get(1).id);
        assertEquals(0, reply.toolCalls.get(1).arguments.size(), "empty arguments are an empty object");
        assertEquals("", reply.content);
        JsonObject sent = this.bodies.get(0);
        assertEquals("test-model", sent.get("model").getAsString());
        assertEquals(1, sent.getAsJsonArray("tools").size());
        assertEquals("go", sent.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("Bearer sk-test", this.auth.get(0));
        assertEquals(120, this.client.promptTokens());
        assertEquals(30, this.client.completionTokens());
        assertEquals(1, this.client.getCalls());
    }

    @Test
    void retriesRateLimitsAndServerErrorsThenSucceeds() throws IOException {
        this.script.add(new Canned(429, "{\"error\":\"slow down\"}"));
        this.script.add(new Canned(503, "busy"));
        this.script.add(new Canned(200, reply("{\"role\":\"assistant\",\"content\":\"  hello  \"}", 5, 2)));
        LlmClient.Reply reply = this.client.chat(userSays("hi"), null);
        assertEquals("hello", reply.content);
        assertEquals(3, this.bodies.size());
        assertTrue(!this.bodies.get(0).has("tools"), "no tools, no tools field");
    }

    @Test
    void givesUpAfterThreeTriesAndNeverRetriesABadRequest() {
        this.script.add(new Canned(502, "bad gateway"));
        IOException gaveUp = assertThrows(IOException.class, () -> this.client.chat(userSays("hi"), null));
        assertTrue(gaveUp.getMessage().contains("502"), gaveUp.getMessage());
        assertEquals(3, this.bodies.size());

        this.bodies.clear();
        this.script.clear();
        this.script.add(new Canned(401, "{\"error\":\"bad key\"}"));
        IOException refused = assertThrows(IOException.class, () -> this.client.chat(userSays("hi"), null));
        assertTrue(refused.getMessage().contains("401") && refused.getMessage().contains("bad key"), refused.getMessage());
        assertTrue(refused.getMessage().contains("check #ai key"), refused.getMessage());
        assertEquals(1, this.bodies.size());
    }

    @Test
    void malformedJsonAndArgumentsAreReported() {
        this.script.add(new Canned(200, reply("""
                {"role":"assistant","tool_calls":[{"id":"c","function":{"name":"goto","arguments":"{target: 1 2"}}]}""", 1, 1)));
        LlmClient.Reply reply = assertDoesNotThrow(() -> this.client.chat(userSays("go"), null));
        assertEquals("{target: 1 2", reply.toolCalls.get(0).arguments.get("__malformed").getAsString());

        this.script.clear();
        this.script.add(new Canned(200, "<html>not json</html>"));
        IOException broken = assertThrows(IOException.class, () -> this.client.chat(userSays("go"), null));
        assertTrue(broken.getMessage().contains("malformed"), broken.getMessage());

        this.script.clear();
        this.script.add(new Canned(200, "{\"choices\":[]}"));
        assertThrows(IOException.class, () -> this.client.chat(userSays("go"), null));
    }

    private static <T> T assertDoesNotThrow(org.junit.jupiter.api.function.ThrowingSupplier<T> supplier) {
        return org.junit.jupiter.api.Assertions.assertDoesNotThrow(supplier);
    }
}
