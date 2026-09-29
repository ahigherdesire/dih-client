package dihclient.commands.impl;

import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolArgs;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolConfirmations;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dihclient.ai.ToolRunner;
import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.util.DihClientMessaging;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@code .tools <tool> [args]}: runs any AI tool by hand. Arguments are positional in the tool's order or
 * {@code key=value}; {@code .tools} lists them and {@code .tools help <tool>} explains one. Dangerous tools print a
 * [confirm] link instead of running.
 */
public class ToolsCommand extends Command {

    /** Tools can wait or walk for seconds; they run here, in order, and report back on the game thread. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "DIH-tools");
        thread.setDaemon(true);
        return thread;
    });

    public ToolsCommand() {
        super("tools", "Run an AI tool: tools <tool> [args], tools help <tool>.", "tool");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> list());
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("help")
            .executes(ctx -> list())
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("tool", StringArgumentType.word())
                .suggests(ToolsCommand::suggestTools)
                .executes(ctx -> help(StringArgumentType.getString(ctx, "tool")))));
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("tool", StringArgumentType.word())
            .suggests(ToolsCommand::suggestTools)
            .executes(ctx -> run(StringArgumentType.getString(ctx, "tool"), ""))
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("args", StringArgumentType.greedyString())
                .suggests(ToolsCommand::suggestArgs)
                .executes(ctx -> run(StringArgumentType.getString(ctx, "tool"), StringArgumentType.getString(ctx, "args")))));
    }

    private static CompletableFuture<Suggestions> suggestTools(CommandContext<DihCommandSource> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (AiTool tool : ToolRegistry.standard().all()) {
            if (tool.name().startsWith(typed)) {
                builder.suggest(tool.name(), Component.literal(tool.summary()));
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestArgs(CommandContext<DihCommandSource> ctx, SuggestionsBuilder builder) {
        AiTool tool;
        try {
            tool = ToolRegistry.standard().get(StringArgumentType.getString(ctx, "tool"));
        } catch (IllegalArgumentException e) {
            tool = null;
        }
        if (tool == null) {
            return builder.buildFuture();
        }
        String typed = builder.getRemaining();
        SuggestionsBuilder at = builder.createOffset(builder.getStart() + ToolArgs.completionStart(typed));
        for (String value : ToolArgs.suggestions(tool.schema(), typed)) {
            at.suggest(value);
        }
        return at.buildFuture();
    }

    private static int list() {
        List<AiTool> all = ToolRegistry.standard().all();
        DihClientMessaging.sendPrefixed("§f" + all.size() + " tools§7. Run one with §f" + prefix() + "tools <tool> [args]§7, "
            + "or §f" + prefix() + "tools help <tool>§7 for its arguments.");
        for (ToolCategory category : ToolCategory.values()) {
            List<String> names = all.stream().filter(tool -> tool.category() == category).map(AiTool::name).toList();
            if (!names.isEmpty()) {
                DihClientMessaging.sendPrefixed("§f" + category.id() + "§7: " + String.join(", ", names));
            }
        }
        return SUCCESS;
    }

    private static int help(String name) {
        AiTool tool = ToolRegistry.standard().get(name);
        if (tool == null) {
            DihClientMessaging.sendPrefixed("§c" + ToolRunner.unknown(name));
            return SUCCESS;
        }
        DihClientMessaging.sendPrefixed("§f" + tool.name() + "§7 (" + tool.category().id() + "): " + tool.summary()
            + (tool.dangerous() ? " §eAsks for confirmation." : ""));
        DihClientMessaging.sendPrefixed("§7Usage: §f" + prefix() + "tools " + tool.schema().usage(tool.name()));
        for (ToolSchema.Param param : tool.schema().params()) {
            DihClientMessaging.sendPrefixed("§7  " + param.describe());
        }
        return SUCCESS;
    }

    private static int run(String name, String line) {
        AiTool tool = ToolRegistry.standard().get(name);
        if (tool == null) {
            DihClientMessaging.sendPrefixed("§c" + ToolRunner.unknown(name) + " §7" + prefix() + "tools lists them.");
            return SUCCESS;
        }
        ToolArgs.Parsed parsed = ToolArgs.parseLine(tool.schema(), line);
        if (!parsed.ok()) {
            DihClientMessaging.sendPrefixed("§c" + parsed.error());
            DihClientMessaging.sendPrefixed("§7Usage: §f" + prefix() + "tools " + tool.schema().usage(tool.name()));
            return SUCCESS;
        }
        if (tool.dangerous()) {
            askToConfirm(tool, line);
            return SUCCESS;
        }
        submit(tool, parsed.args(), false, line);
        return SUCCESS;
    }

    private static void askToConfirm(AiTool tool, String line) {
        String id = ToolConfirmations.add(tool.name(), line, System.currentTimeMillis());
        MutableComponent confirm = Component.literal("[confirm]").withStyle(style -> style
            .withColor(ChatFormatting.GREEN)
            .withUnderlined(true)
            .withClickEvent(new ClickEvent.RunCommand(ToolConfirmations.clickCommand(id)))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal("Runs it once, within a minute"))));
        DihClientMessaging.send(Component.empty()
            .append(DihClientMessaging.themedTag("DIH"))
            .append(DihClientMessaging.themedBody("§e" + tool.name() + " can't be undone. §f" + prefix() + "tools "
                + tool.name() + (line == null || line.isBlank() ? "" : " " + line.trim()) + " "))
            .append(confirm));
    }

    /** A [confirm] click; true when the click was ours (the caller then keeps it from the server). */
    public static boolean handleConfirmClick(String command) {
        String id = ToolConfirmations.idFromClick(command);
        if (id == null) {
            return false;
        }
        ToolConfirmations.Pending pending = ToolConfirmations.take(id, System.currentTimeMillis());
        AiTool tool = pending == null ? null : ToolRegistry.standard().get(pending.tool());
        ToolArgs.Parsed parsed = tool == null ? null : ToolArgs.parseLine(tool.schema(), pending.args());
        if (parsed == null || !parsed.ok()) {
            DihClientMessaging.sendPrefixed("§cThat confirmation expired or was already used.");
            return true;
        }
        submit(tool, parsed.args(), true, pending.args());
        return true;
    }

    private static void submit(AiTool tool, JsonObject args, boolean confirmed, String line) {
        WORKER.submit(() -> {
            ToolResult result;
            try {
                result = ToolRunner.call(tool, args, ToolContext.Source.CHAT, confirmed);
            } catch (Throwable t) {
                result = ToolResult.failed("Tool failed: " + t);
            }
            ToolResult done = result;
            // Some tools are dangerous only for some arguments (toggling a combat module): they ask after the fact.
            boolean ask = !confirmed && Boolean.TRUE.equals(done.facts().get("needs_confirmation"));
            Minecraft.getInstance().execute(() -> {
                if (ask) askToConfirm(tool, line);
                else print(tool.name(), done);
            });
        });
    }

    static void print(String name, ToolResult result) {
        String color = switch (result.status()) {
            case OK -> "§a";
            case RUNNING -> "§b";
            case NEEDS -> "§e";
            case FAILED -> "§c";
        };
        String[] lines = result.text().split("\n");
        DihClientMessaging.sendPrefixed("§7" + name + " " + color + result.status().id() + "§7: §f" + lines[0]);
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                DihClientMessaging.sendPrefixed("§7  " + lines[i]);
            }
        }
        if (!result.facts().isEmpty()) {
            DihClientMessaging.sendPrefixed("§8  " + result.factsJson());
        }
    }

    private static String prefix() {
        return DihCommands.effectivePrefix();
    }
}
