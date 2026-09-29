package baritone.ai.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * {@code load_tools} and {@code list_tools}: how the model reaches past the job tools. Registered last, so the job
 * tools lead the definitions and loaded categories slot in between.
 */
public final class MetaTools {

    private MetaTools() {
    }

    public static ToolRegistry register(ToolRegistry registry) {
        String[] categories = loadable();
        registry.register(AiTool.builder("load_tools", ToolCategory.JOB)
                .summary("Add a category of tools to this run.")
                .description("Add every tool in one category to your tool list for the rest of this run. "
                        + "Use list_tools first to see what each category holds.")
                .schema(ToolSchema.builder()
                        .enumOf("category", "The category to load.", categories).required()
                        .build())
                .handler((ctx, args) -> load(ctx, ToolCategory.byId(args.string("category"))))
                .build());
        registry.register(AiTool.builder("list_tools", ToolCategory.JOB)
                .summary("List tool categories, or the tools in one.")
                .description("Without a category: every category with how many tools it has. With one: its tools "
                        + "and what each does. Read-only.")
                .schema(ToolSchema.builder()
                        .enumOf("category", "A category to list the tools of.", categories)
                        .build())
                .handler((ctx, args) -> args.has("category")
                        ? listCategory(ctx, ToolCategory.byId(args.string("category")))
                        : overview(ctx))
                .build());
        return registry;
    }

    private static String[] loadable() {
        List<String> ids = new ArrayList<>();
        for (ToolCategory category : ToolCategory.values()) {
            if (category != ToolCategory.JOB) {
                ids.add(category.id());
            }
        }
        return ids.toArray(String[]::new);
    }

    private static ToolRegistry registry(ToolContext ctx) {
        return ctx.session() != null ? ctx.session().registry() : ToolRegistry.standard();
    }

    private static List<AiTool> extra(ToolRegistry registry, ToolCategory category) {
        return registry.inCategory(category).stream().filter(tool -> !tool.job()).toList();
    }

    private static ToolResult load(ToolContext ctx, ToolCategory category) {
        ToolSession session = ctx.session();
        if (session == null) {
            return ToolResult.failed("load_tools is for AI runs; .tools can call any tool directly.");
        }
        List<AiTool> tools = extra(session.registry(), category);
        if (tools.isEmpty()) {
            return ToolResult.ok("There are no " + category.id() + " tools yet.").fact("loaded", 0);
        }
        if (session.load(category) == 0) {
            return ToolResult.ok("The " + category.id() + " tools are already loaded.").fact("loaded", 0);
        }
        StringJoiner names = new StringJoiner(", ");
        tools.forEach(tool -> names.add(tool.name()));
        return ToolResult.ok("Loaded " + tools.size() + " " + category.id() + " tools: " + names
                + ". They are in your tool list from the next step.").fact("loaded", tools.size());
    }

    private static ToolResult overview(ToolContext ctx) {
        ToolRegistry registry = registry(ctx);
        StringBuilder sb = new StringBuilder();
        for (ToolCategory category : ToolCategory.values()) {
            if (category == ToolCategory.JOB) {
                continue;
            }
            int count = extra(registry, category).size();
            if (count == 0) {
                continue;
            }
            sb.append(sb.length() == 0 ? "" : "\n").append(category.id()).append(": ").append(count)
                    .append(count == 1 ? " tool" : " tools").append(" (").append(category.blurb()).append(')');
            if (ctx.session() != null && ctx.session().loaded().contains(category)) {
                sb.append(", loaded");
            }
        }
        if (sb.length() == 0) {
            return ToolResult.ok("Every tool is already in your list.");
        }
        return ToolResult.ok("Tool categories (load one with load_tools):\n" + sb);
    }

    private static ToolResult listCategory(ToolContext ctx, ToolCategory category) {
        List<AiTool> tools = extra(registry(ctx), category);
        if (tools.isEmpty()) {
            return ToolResult.ok("There are no " + category.id() + " tools yet.");
        }
        boolean loaded = ctx.session() == null || ctx.session().loaded().contains(category);
        StringBuilder sb = new StringBuilder(category.id()).append(" tools")
                .append(loaded ? ":" : " (call load_tools to use them):");
        for (AiTool tool : tools) {
            sb.append("\n- ").append(tool.name()).append(": ").append(tool.summary());
            if (tool.dangerous()) {
                sb.append(" (needs the player's confirmation)");
            }
        }
        return ToolResult.ok(sb.toString());
    }
}
