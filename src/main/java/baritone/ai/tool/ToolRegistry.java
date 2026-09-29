package baritone.ai.tool;

import baritone.ai.BuiltinTools;
import baritone.ai.catalog.CatalogTools;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Every tool, in registration order. That order is the order the model sees them in, and it never changes during a
 * session, so providers can cache the prompt prefix.
 *
 * <p>{@link #call} is the one way to run a tool: it checks the arguments, the confirmation for dangerous tools and
 * (for the AI) that the tool was loaded, then runs it on the right thread and turns any crash into a result.
 */
public final class ToolRegistry {

    private static volatile ToolRegistry standard;
    /** Tools added from outside Baritone (the client's own, and command adapters), applied to the standard registry. */
    private static final List<Consumer<ToolRegistry>> CONTRIBUTIONS = new ArrayList<>();

    private final Map<String, AiTool> byName = new LinkedHashMap<>();

    /** The client's tools, built once: {@link #withDefaults()} plus every contribution. */
    public static ToolRegistry standard() {
        ToolRegistry registry = standard;
        if (registry == null) {
            synchronized (ToolRegistry.class) {
                registry = standard;
                if (registry == null) {
                    registry = withDefaults();
                    for (Consumer<ToolRegistry> contribution : CONTRIBUTIONS) {
                        contribution.accept(registry);
                    }
                    standard = registry;
                }
            }
        }
        return registry;
    }

    /** A new registry with every tool that needs no game to register: the built-ins, the meta tools and the catalog. */
    public static ToolRegistry withDefaults() {
        ToolRegistry registry = new ToolRegistry();
        BuiltinTools.register(registry);
        MetaTools.register(registry);
        CatalogTools.register(registry);
        return registry;
    }

    /**
     * Adds tools to the standard registry: now if it is already built, else when it is. For code outside Baritone
     * (the client's tools) and tools that need the running game to list (command adapters).
     */
    public static void contribute(Consumer<ToolRegistry> contribution) {
        synchronized (ToolRegistry.class) {
            CONTRIBUTIONS.add(contribution);
            if (standard != null) contribution.accept(standard);
        }
    }

    public synchronized ToolRegistry register(AiTool tool) {
        if (this.byName.containsKey(tool.name())) {
            throw new IllegalArgumentException("duplicate tool name: " + tool.name());
        }
        this.byName.put(tool.name(), tool);
        return this;
    }

    /** The tool with this name, ignoring case, or null. */
    public synchronized AiTool get(String name) {
        return name == null ? null : this.byName.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public synchronized List<AiTool> all() {
        return Collections.unmodifiableList(new ArrayList<>(this.byName.values()));
    }

    public List<AiTool> inCategory(ToolCategory category) {
        return all().stream().filter(tool -> tool.category() == category).toList();
    }

    /** Definitions of the tools {@code include} accepts, always in registration order. */
    public JsonArray definitions(Predicate<AiTool> include) {
        JsonArray array = new JsonArray();
        for (AiTool tool : all()) {
            if (include.test(tool)) {
                array.add(tool.definition());
            }
        }
        return array;
    }

    /** Runs {@code name} with the caller's arguments. Never throws. */
    public ToolResult call(ToolContext ctx, String name, JsonObject args) {
        AiTool tool = get(name);
        if (tool == null) {
            return ToolResult.failed("No such tool: " + name + ".");
        }
        if (args != null && args.has("__malformed")) {
            return ToolResult.failed("Your arguments were not valid JSON. Try again with a proper JSON object.");
        }
        ToolSession session = ctx.session();
        if (ctx.source() == ToolContext.Source.AI && session != null && !session.isVisible(tool)) {
            return ToolResult.failed(tool.name() + " is one of the " + tool.category().id() + " tools. Call load_tools with "
                    + "category \"" + tool.category().id() + "\" first.");
        }
        ToolArgs.Parsed parsed = ToolArgs.validate(tool.schema(), args == null ? new JsonObject() : args);
        if (!parsed.ok()) {
            return ToolResult.failed(parsed.error());
        }
        if (tool.dangerous() && !ctx.isConfirmed()) {
            return ToolResult.failed("needs confirmation").fact("needs_confirmation", true);
        }
        if (tool.threadMode() == AiTool.ThreadMode.GAME_THREAD) {
            return ctx.onGameThread(() -> run(tool, ctx, parsed.args()),
                    ToolResult.failed(tool.name() + " timed out waiting for the game."));
        }
        return run(tool, ctx, parsed.args());
    }

    private static ToolResult run(AiTool tool, ToolContext ctx, JsonObject args) {
        try {
            return tool.execute(ctx, args);
        } catch (IllegalArgumentException e) {
            // Tools throw these for arguments the schema can't express ("give x and z together"): a reason, not a crash.
            return ToolResult.failed(e.getMessage() == null ? "Bad arguments." : e.getMessage());
        } catch (Exception e) {
            return ToolResult.failed("Tool failed: " + e);
        }
    }
}
