package baritone.ai.catalog;

import baritone.ai.AiConfig;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.CommandTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One raw tool per {@code #} and {@code .} command, named {@code cmd_<name>} and {@code dot_<name>}, taking the
 * arguments as typed. They are the fallback: the model reaches for the purpose-built tools first, which is why these
 * are all in the raw category. Commands that only make sense for a person (menus, key binds, chat clearing), that
 * change settings wholesale, or that the AI deny list names are left out.
 */
public final class CommandAdapters {

    /** A command as the adapters see it. */
    public record Info(String name, List<String> names, String description) {
    }

    /** {@code #} commands left out: menus and renders, settings, cache housekeeping, and what a tool does better. */
    public static final Set<String> BARITONE_SKIP = Set.of("help", "click", "menu", "gc", "render", "reloadall",
            "saveall", "version", "ai", "acquire", "set", "modified", "reset", "explorefilter", "schematica",
            "litematica", "heatmap", "seedmap", "flee", "listentomine");

    /** {@code .} commands left out: menus, binds, chat housekeeping, updating the mod, this tool system itself. */
    public static final Set<String> CLIENT_SKIP = Set.of("help", "commands", "binds", "bind", "clear", "prefix", "gui",
            "tools", "update", "copypos", "setting", "server", "plugins", "matchmaking", "delay", "sync");

    /** {@code .} commands that act on others or can't be undone: they ask first. */
    public static final Set<String> CLIENT_DANGEROUS = Set.of("disconnect", "say", "drop", "packet", "damage", "irc",
            "multi", "send", "toggle", "vclip", "hclip", "tp", "gate", "give");

    private CommandAdapters() {
    }

    /** Adds {@code cmd_<name>} for each {@code #} command. */
    public static void registerBaritone(ToolRegistry registry, List<Info> commands) {
        register(registry, commands, true, BARITONE_SKIP, Set.of());
    }

    /** Adds {@code dot_<name>} for each {@code .} command. */
    public static void registerClient(ToolRegistry registry, List<Info> commands) {
        register(registry, commands, false, CLIENT_SKIP, CLIENT_DANGEROUS);
    }

    static void register(ToolRegistry registry, List<Info> commands, boolean baritone, Set<String> skip, Set<String> dangerous) {
        List<String> denied = new AiConfig().deniedCommands;
        for (Info info : commands) {
            String name = info.name().toLowerCase(Locale.ROOT);
            if (skip.contains(name) || info.names().stream().anyMatch(n -> denied.contains(n.toLowerCase(Locale.ROOT)))) continue;
            String toolName = (baritone ? "cmd_" : "dot_") + name.replaceAll("[^a-z0-9_]", "_");
            if (registry.get(toolName) != null) continue;
            String prefix = baritone ? "#" : ".";
            String description = info.description() == null || info.description().isBlank()
                    ? "Run " + prefix + name + "." : info.description().trim();
            AiTool.Builder builder = AiTool.builder(toolName, ToolCategory.RAW)
                    .summary(prefix + name + ": " + description)
                    .description("Run " + prefix + name + " with arguments as typed after it. " + description
                            + (info.names().size() > 1 ? " Also called " + String.join(", ", info.names().subList(1, info.names().size())) + "." : ""))
                    .schema(ToolSchema.builder()
                            .string("args", "The arguments, as typed after " + prefix + name + " (default: none).")
                            .build())
                    .handler((ctx, args) -> {
                        String refusal = refusal(ctx, name, info.names());
                        if (refusal != null) return ToolResult.failed(refusal);
                        String rest = args.has("args") ? args.string("args").trim() : "";
                        String command = rest.isEmpty() ? name : name + " " + rest;
                        return baritone ? CommandTool.run(ctx, command) : CommandTool.client(ctx, command);
                    });
            if (dangerous.contains(name)) builder.dangerous();
            registry.register(builder.build());
        }
    }

    /** The AI's deny list as it is now (it can change after the tools are registered); people are never limited. */
    private static String refusal(ToolContext ctx, String name, List<String> names) {
        if (ctx.source() != ToolContext.Source.AI) return null;
        AiConfig config = ctx.config() == null ? new AiConfig() : ctx.config();
        return config.allowsCommand(name, names) ? null : "Refused: \"" + name + "\" is on the deny list and cannot be run by the AI.";
    }
}
