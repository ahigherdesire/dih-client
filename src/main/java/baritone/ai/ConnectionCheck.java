package baritone.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

/**
 * The setup screen's Test button: one short chat with one trivial tool, answered in plain words ("Key rejected",
 * "Model not found", "Can't reach localhost:11434: is Ollama running?").
 */
public final class ConnectionCheck {

    public record Result(boolean ok, long millis, String message) {
    }

    private static final String PING_TOOL = """
            [{"type":"function","function":{"name":"ping","description":"Say you are here.",
              "parameters":{"type":"object","properties":{}}}}]""";

    private ConnectionCheck() {
    }

    /** Blocks for up to the client's timeouts; call it off the game thread. */
    public static Result run(AiConfig candidate) {
        AiProviders.Provider preset = AiProviders.forUrl(candidate.baseUrl);
        boolean needsKey = preset == null || preset.needsKey();
        if (needsKey && !candidate.hasKey()) {
            return new Result(false, 0, "Add a key first"
                    + (preset != null && !preset.keyUrl().isEmpty() ? " (get one at " + preset.keyUrl() + ")" : "") + ".");
        }
        if (candidate.model == null || candidate.model.isBlank()) return new Result(false, 0, "Pick a model first.");
        JsonArray messages = new JsonArray();
        messages.add(AiBrain.message("user", "Call the ping tool."));
        long start = System.nanoTime();
        try {
            LlmClient.Reply reply = new LlmClient(candidate, 1).chat(messages, JsonParser.parseString(PING_TOOL).getAsJsonArray());
            long millis = (System.nanoTime() - start) / 1_000_000;
            String time = String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
            return reply.hasToolCalls()
                    ? new Result(true, millis, "Works (" + time + ").")
                    : new Result(true, millis, "Answers (" + time + "), but didn't call the tool: pick a model with tool calling.");
        } catch (LlmClient.RequestFailed e) {
            return new Result(false, 0, Redact.text(explain(e.status, e.getMessage(), candidate, preset), candidate.resolveKey()));
        } catch (IOException | RuntimeException e) {
            return new Result(false, 0, Redact.text("It answered with something unexpected: " + e.getMessage(), candidate.resolveKey()));
        }
    }

    /** Plain words for a failed request. */
    static String explain(int status, String message, AiConfig candidate, AiProviders.Provider preset) {
        String text = message == null ? "" : message;
        String lower = text.toLowerCase(Locale.ROOT);
        if (status == LlmClient.RequestFailed.BAD_URL) return "That URL isn't valid.";
        if (status == LlmClient.RequestFailed.NETWORK) {
            String host = hostPort(candidate.baseUrl);
            if (lower.contains("refused")) {
                String app = preset != null && preset.local() ? preset.name().replace(" (on this PC)", "") : null;
                return "Can't reach " + host + (app == null ? "." : ": is " + app + " running?");
            }
            if (lower.contains("unknownhost") || lower.contains("unknown host") || lower.contains("nodename")) {
                return "Can't find " + host + ": check the URL and your internet.";
            }
            if (lower.contains("timed out")) return "No answer from " + host + " in time.";
            return "Can't reach " + host + ": " + text;
        }
        boolean aboutModel = lower.contains("model") && (lower.contains("not found") || lower.contains("not_found")
                || lower.contains("does not exist") || lower.contains("invalid") || lower.contains("unknown")
                || lower.contains("not a valid") || lower.contains("no such"));
        if (status == 401) {
            String reason = AiProviders.refusedKeyReason(candidate.resolveKey(), candidate.baseUrl);
            return reason.isEmpty() ? "Key rejected." : "Key rejected: " + reason + ".";
        }
        if (status == 403) return aboutModel ? "This key can't use " + candidate.model + "." : "Key rejected (no access).";
        if (status == 404 && !aboutModel) return "Nothing at that URL: check it (and the model).";
        if (status == 404 || (status == 400 && aboutModel)) return "Model not found: " + candidate.model + ".";
        if (status == 402) return "Out of credit on this account.";
        if (status == 429) return "Too many requests or out of credit; try again in a minute.";
        if (status >= 500) return "The provider had an error (" + status + "); try again later.";
        return "The provider said no (" + status + "): " + text;
    }

    private static String hostPort(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl.trim());
            return uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (RuntimeException e) {
            return baseUrl;
        }
    }
}
