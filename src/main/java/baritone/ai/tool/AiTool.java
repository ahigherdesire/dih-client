package baritone.ai.tool;

import com.google.gson.JsonObject;

import java.util.regex.Pattern;

/**
 * One tool, written once and usable from the AI, {@code .tools}, the AI_TOOL macro step and keybinds. Build it with
 * {@link #builder}; register it in {@link ToolRegistry}.
 */
public final class AiTool {

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    /** Where the handler runs. Game-thread tools are hopped there; worker tools may block (and hop themselves). */
    public enum ThreadMode { GAME_THREAD, WORKER }

    @FunctionalInterface
    public interface Handler {
        ToolResult execute(ToolContext ctx, ToolInput args) throws Exception;
    }

    private final String name;
    private final ToolCategory category;
    private final String summary;
    private final String description;
    private final ToolSchema schema;
    private final boolean dangerous;
    private final boolean job;
    private final ThreadMode threadMode;
    private final Handler handler;

    private AiTool(Builder builder) {
        this.name = builder.name;
        this.category = builder.category;
        this.summary = builder.summary;
        this.description = builder.description == null ? builder.summary : builder.description;
        this.schema = builder.schema;
        this.dangerous = builder.dangerous;
        this.job = builder.job || builder.category == ToolCategory.JOB;
        this.threadMode = builder.threadMode;
        this.handler = builder.handler;
    }

    public static Builder builder(String name, ToolCategory category) {
        return new Builder(name, category);
    }

    public String name() {
        return this.name;
    }

    public ToolCategory category() {
        return this.category;
    }

    /** One line for {@code .tools}, the palette and {@code list_tools}. */
    public String summary() {
        return this.summary;
    }

    /** What the model reads. */
    public String description() {
        return this.description;
    }

    public ToolSchema schema() {
        return this.schema;
    }

    /** Needs a confirmation before it runs. */
    public boolean dangerous() {
        return this.dangerous;
    }

    /** Always visible to the model. */
    public boolean job() {
        return this.job;
    }

    public ThreadMode threadMode() {
        return this.threadMode;
    }

    /** Runs the handler on the calling thread with already-checked arguments. {@link ToolRegistry#call} is the usual door. */
    public ToolResult execute(ToolContext ctx, JsonObject args) throws Exception {
        ToolResult result = this.handler.execute(ctx, new ToolInput(this.schema, args));
        return result == null ? ToolResult.failed(this.name + " returned nothing.") : result;
    }

    /** The OpenAI function definition. */
    public JsonObject definition() {
        JsonObject function = new JsonObject();
        function.addProperty("name", this.name);
        function.addProperty("description", this.description);
        function.add("parameters", this.schema.toJson());
        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }

    public static final class Builder {
        private final String name;
        private final ToolCategory category;
        private String summary;
        private String description;
        private ToolSchema schema = ToolSchema.EMPTY;
        private boolean dangerous;
        private boolean job;
        private ThreadMode threadMode = ThreadMode.WORKER;
        private Handler handler;

        private Builder(String name, ToolCategory category) {
            if (name == null || !NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("tool names are snake_case: " + name);
            }
            if (category == null) {
                throw new IllegalArgumentException(name + " needs a category");
            }
            this.name = name;
            this.category = category;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        /** Longer text for the model; defaults to the summary. */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder schema(ToolSchema schema) {
            this.schema = schema == null ? ToolSchema.EMPTY : schema;
            return this;
        }

        public Builder dangerous() {
            this.dangerous = true;
            return this;
        }

        public Builder job() {
            this.job = true;
            return this;
        }

        public Builder gameThread() {
            this.threadMode = ThreadMode.GAME_THREAD;
            return this;
        }

        public Builder handler(Handler handler) {
            this.handler = handler;
            return this;
        }

        public AiTool build() {
            if (this.summary == null || this.summary.isBlank()) {
                throw new IllegalArgumentException(this.name + " needs a summary");
            }
            if (this.handler == null) {
                throw new IllegalArgumentException(this.name + " needs a handler");
            }
            return new AiTool(this);
        }
    }
}
