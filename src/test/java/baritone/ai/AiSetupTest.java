package baritone.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The setup screen's pieces: presets and prices, the plain-words Test, and keys never shown. */
final class AiSetupTest {

    private static final String KEY = "sk-test-0123456789abcdefWXYZ";

    /** A server that answers every chat with {@code status} and {@code body}. */
    private static HttpServer server(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            try (OutputStream stream = exchange.getResponseBody()) {
                stream.write(out);
            }
        });
        server.start();
        return server;
    }

    private static AiConfig config(String url, String model, String key) {
        AiConfig config = new AiConfig();
        config.baseUrl = url;
        config.model = model;
        config.apiKey = key;
        return config;
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @Test
    void everyPresetHasAUrlAndCloudOnesHaveModels() {
        List<AiProviders.Provider> providers = AiProviders.all();
        assertEquals(List.of("openrouter", "openai", "anthropic", "gemini", "groq", "deepseek", "qwen", "ollama", "lmstudio", "custom"),
                providers.stream().map(AiProviders.Provider::id).toList());
        for (AiProviders.Provider provider : providers) {
            if (provider.id().equals("custom")) continue;
            assertTrue(provider.baseUrl().startsWith("http"), provider.id());
            assertFalse(provider.source().isEmpty(), "where the preset was checked: " + provider.id());
            if (!provider.local()) {
                assertTrue(provider.needsKey(), provider.id());
                assertTrue(provider.models().size() >= 2, provider.id());
            }
        }
        assertEquals("ollama", AiProviders.forUrl(providers, "http://LOCALHOST:11434/v1/").id());
        assertFalse(AiProviders.forUrl(providers, "http://localhost:11434/v1").needsKey());
        assertNull(AiProviders.forUrl(providers, "https://example.com/v1"));
    }

    @Test
    void theDefaultUrlIsAPreset() {
        assertEquals("qwen", AiProviders.forUrl(new AiConfig().baseUrl).id(), "the setup screen shows Qwen, not Custom");
    }

    @Test
    void aKeyThatSaysWhoseItIsNamesItsProvider() {
        List<AiProviders.Provider> providers = AiProviders.all();
        assertEquals("groq", AiProviders.forKey(providers, "gsk_abc123").id());
        assertEquals("openrouter", AiProviders.forKey(providers, "sk-or-v1-abc").id(), "longer prefix than OpenAI's sk-");
        assertEquals("anthropic", AiProviders.forKey(providers, "sk-ant-api03-abc").id());
        assertEquals("openai", AiProviders.forKey(providers, "  sk-proj-abc").id());
        assertEquals("gemini", AiProviders.forKey(providers, "AIzaSyAbc").id());
        assertNull(AiProviders.forKey(providers, "sk-0123456789abcdef"), "plain sk- keys: OpenAI, DeepSeek, Qwen...");
        assertNull(AiProviders.forKey(providers, ""));
        assertEquals("groq", AiProviders.named(providers, "Groq").id());
        assertEquals("anthropic", AiProviders.named(providers, "anthropic (claude)").id());
        assertNull(AiProviders.named(providers, "nope"));
    }

    /** From a beta report: a Groq key saved with the default Qwen URL and model, and only "HTTP 401" back. */
    @Test
    void aGroqKeySentToQwenSaysSo() {
        List<AiProviders.Provider> providers = AiProviders.all();
        String qwen = new AiConfig().baseUrl;
        assertEquals("groq", AiProviders.keyMismatch(providers, "gsk_abc", qwen).id());
        assertNull(AiProviders.keyMismatch(providers, "gsk_abc", "https://api.groq.com/openai/v1/"));
        assertNull(AiProviders.keyMismatch(providers, "sk-plain", qwen), "a key that doesn't say whose it is");
        assertEquals("that's a Groq key, but requests go to dashscope-intl.aliyuncs.com: run #ai provider groq (or .ai setup)",
                AiProviders.refusedKeyReason(providers, "gsk_abc", qwen));
        assertTrue(AiProviders.refusedKeyReason(providers, "sk-plain", qwen).startsWith("Qwen keys only work in the region"),
                "a Qwen-looking key refused by Qwen: the region");
        assertEquals("", AiProviders.refusedKeyReason(providers, "sk-plain", "https://api.deepseek.com"));

        AiConfig groqKey = config(qwen, "qwen-plus", "gsk_abc");
        assertEquals("Key rejected: that's a Groq key, but requests go to dashscope-intl.aliyuncs.com: run #ai provider groq"
                + " (or .ai setup).", ConnectionCheck.explain(401, "Incorrect API key provided", groqKey, AiProviders.forUrl(qwen)));
        assertTrue(LlmClient.hint(401, groqKey).contains("#ai provider groq"));
        assertEquals(" (the key was refused: check #ai key)", LlmClient.hint(401, config("https://api.deepseek.com", "m", "sk-x")));
    }

    @Test
    void costComesFromThePriceTable() {
        List<AiProviders.Provider> providers = AiProviders.all();
        assertEquals("$0.03", AiProviders.cost(providers, "https://api.anthropic.com/v1/", "claude-sonnet-5-5", 10_000, 1_000));
        assertEquals("$0.0020", AiProviders.cost(providers, "https://api.openai.com/v1", "gpt-6-luna", 15_000, 1_000));
        assertEquals("free (local)", AiProviders.cost(providers, "http://localhost:11434/v1", "qwen3:8b", 50_000, 9_000));
        assertNull(AiProviders.cost(providers, "https://api.openai.com/v1", "some-new-model", 1, 1), "unknown price: tokens only");
        assertNull(AiProviders.cost(providers, "https://example.com/v1", "m", 1, 1));
    }

    @Test
    void keysAreMaskedWhereverTheyWouldShow() {
        assertEquals("sk-t…WXYZ", Redact.mask(KEY));
        String text = Redact.text("bad key " + KEY + " and another sk-or-abcdefghijklmnop1234 here", KEY);
        assertFalse(text.contains(KEY), text);
        assertFalse(text.contains("abcdefghijklmnop1234"), text);
        assertTrue(text.contains("sk-t…WXYZ"), text);
        assertEquals("no keys here", Redact.text("no keys here", ""));
    }

    @Test
    void anErrorThatEchoesTheKeyDoesNotShowIt() throws IOException {
        HttpServer server = server(401, "{\"error\":{\"message\":\"Incorrect API key provided: " + KEY + "\"}}");
        try {
            AiConfig config = config(url(server), "m", KEY);
            IOException failed = assertThrows(IOException.class,
                    () -> new LlmClient(config, 1).chat(new com.google.gson.JsonArray(), null));
            assertFalse(failed.getMessage().contains(KEY), failed.getMessage());
            ConnectionCheck.Result result = ConnectionCheck.run(config);
            assertFalse(result.ok());
            assertEquals("Key rejected.", result.message());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void theTestSaysWhatIsWrongInPlainWords() throws IOException {
        HttpServer missingModel = server(404, "{\"error\":{\"message\":\"The model `gpt-9` does not exist\",\"code\":\"model_not_found\"}}");
        try {
            assertEquals("Model not found: gpt-9.", ConnectionCheck.run(config(url(missingModel), "gpt-9", KEY)).message());
        } finally {
            missingModel.stop(0);
        }
        HttpServer badRequest = server(400, "{\"error\":{\"message\":\"models/gemini-x is not found for API version v1beta\"}}");
        try {
            assertEquals("Model not found: gemini-x.", ConnectionCheck.run(config(url(badRequest), "gemini-x", KEY)).message());
        } finally {
            badRequest.stop(0);
        }

        int port;
        try (ServerSocket free = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = free.getLocalPort();
        }
        String down = ConnectionCheck.run(config("http://127.0.0.1:" + port + "/v1", "m", KEY)).message();
        assertEquals("Can't reach 127.0.0.1:" + port + ".", down);
        String ollama = ConnectionCheck.explain(LlmClient.RequestFailed.NETWORK, "could not send request: Connection refused: connect",
                config("http://localhost:11434/v1", "qwen3:8b", ""), AiProviders.forUrl("http://localhost:11434/v1"));
        assertEquals("Can't reach localhost:11434: is Ollama running?", ollama);

        assertEquals("Add a key first (get one at https://platform.openai.com/api-keys).",
                ConnectionCheck.run(config("https://api.openai.com/v1", "gpt-6-luna", "")).message());
    }

    @Test
    void aWorkingModelThatCallsTheToolPasses() throws IOException {
        HttpServer ok = server(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"c\","
                + "\"function\":{\"name\":\"ping\",\"arguments\":\"{}\"}}]}}]}");
        try {
            ConnectionCheck.Result result = ConnectionCheck.run(config(url(ok), "m", KEY));
            assertTrue(result.ok(), result.message());
            assertTrue(result.message().startsWith("Works ("), result.message());
        } finally {
            ok.stop(0);
        }
        HttpServer prose = server(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"pong\"}}]}");
        try {
            ConnectionCheck.Result result = ConnectionCheck.run(config(url(prose), "m", KEY));
            assertTrue(result.ok());
            assertTrue(result.message().contains("pick a model with tool calling"), result.message());
        } finally {
            prose.stop(0);
        }
        assertNotNull(AiProviders.all());
    }
}
