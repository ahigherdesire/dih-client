package baritone.ai.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * For tools that work by running the mod's own commands: typed arguments in, exact command strings out. Runs them
 * through {@link ToolContext#commands()}, so a test can check the strings without a game.
 */
public final class CommandTool {

    private CommandTool() {
    }

    /** Runs {@code #} commands in order, stopping at the first that fails. */
    public static ToolResult run(ToolContext ctx, String... commands) {
        return exec(ctx, true, null, commands);
    }

    /** Runs {@code #} commands that start a job: the result is {@code running(job)}. */
    public static ToolResult start(ToolContext ctx, String job, String... commands) {
        return exec(ctx, true, job, commands);
    }

    /** Runs {@code .} commands in order, stopping at the first that fails. */
    public static ToolResult client(ToolContext ctx, String... commands) {
        return exec(ctx, false, null, commands);
    }

    private static ToolResult exec(ToolContext ctx, boolean baritone, String job, String... commands) {
        CommandRunner runner = ctx.commands();
        String prefix = baritone ? "#" : ".";
        List<String> printed = new ArrayList<>();
        List<String> ran = new ArrayList<>();
        for (String command : commands) {
            CommandRunner.Outcome outcome = baritone ? runner.baritone(command) : runner.client(command);
            if (!outcome.handled()) {
                return ToolResult.failed(prefix + firstWord(command) + " is not available here.")
                        .fact("command", prefix + command);
            }
            if (outcome.error()) {
                return ToolResult.failed(prefix + command + " failed" + (outcome.output().isEmpty() ? "." : ": " + outcome.output()))
                        .fact("command", prefix + command);
            }
            ran.add(prefix + command);
            if (!outcome.output().isEmpty()) printed.add(outcome.output());
        }
        String text = "Ran " + String.join(", ", ran) + "." + (printed.isEmpty() ? "" : " It printed: " + String.join(" | ", printed));
        ToolResult result = job == null ? ToolResult.ok(text) : ToolResult.running(job, text);
        return result.fact("command", ran.isEmpty() ? "" : ran.get(ran.size() - 1));
    }

    /** A block or item id as commands take it: lowercase, spaces to underscores, no "minecraft:" prefix. */
    public static String id(String text) {
        String id = text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    /** A whole number as a command argument (no ".0"). */
    public static String num(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private static String firstWord(String command) {
        String trimmed = command == null ? "" : command.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }
}
