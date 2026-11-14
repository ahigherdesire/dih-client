package baritone.ai.tool;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What a tool did. {@code text} is one line a player can read; {@code facts} is a small key → value map the director
 * can reason over, e.g. {@code {"count":8,"item":"diamond","y":-58}}. {@link Status#RUNNING} means an async job
 * started; its end arrives later as an event.
 */
public final class ToolResult {

    public enum Status {
        OK, FAILED, NEEDS, RUNNING;

        /** {@code ok}, {@code failed}, … as macros and the model see it. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Status status;
    private final String text;
    private final Map<String, Object> facts = new LinkedHashMap<>();

    private ToolResult(Status status, String text) {
        this.status = status;
        this.text = text == null ? "" : text;
    }

    public static ToolResult ok(String text) {
        return new ToolResult(Status.OK, text);
    }

    public static ToolResult failed(String reason) {
        return new ToolResult(Status.FAILED, reason);
    }

    /** Couldn't go on without {@code count} of {@code item}. */
    public static ToolResult needs(String item, int count) {
        return new ToolResult(Status.NEEDS, "Needs " + count + " " + item + ".").fact("item", item).fact("count", count);
    }

    /** An async job started; {@code jobId} names it in the completion event. */
    public static ToolResult running(String jobId, String text) {
        return new ToolResult(Status.RUNNING, text).fact("job", jobId);
    }

    /** Adds a fact and returns this result. */
    public ToolResult fact(String key, Object value) {
        this.facts.put(key, value);
        return this;
    }

    public Status status() {
        return this.status;
    }

    public boolean ok() {
        return this.status == Status.OK || this.status == Status.RUNNING;
    }

    public String text() {
        return this.text;
    }

    public Map<String, Object> facts() {
        return Collections.unmodifiableMap(this.facts);
    }

    public JsonObject factsJson() {
        JsonObject json = new JsonObject();
        this.facts.forEach((key, value) -> {
            if (value instanceof Number n) {
                json.addProperty(key, n);
            } else if (value instanceof Boolean b) {
                json.addProperty(key, b);
            } else {
                json.addProperty(key, String.valueOf(value));
            }
        });
        return json;
    }

    /** The tool message the model reads: the text, then the facts as JSON when there are any. */
    public String forModel() {
        return this.facts.isEmpty() ? this.text : this.text + "\nFacts: " + factsJson();
    }

    @Override
    public String toString() {
        return this.status.id() + ": " + forModel();
    }
}
