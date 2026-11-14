package baritone.ai.catalog;

import baritone.ai.tool.AiTool;
import baritone.ai.tool.CommandTool;
import baritone.ai.tool.ToolInput;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;

import java.util.List;

/** Tools that turn typed arguments into exact {@code #} command strings. */
final class Cmd {

    /** Checked arguments to commands. Throws {@link IllegalArgumentException} with a reason a player can read. */
    interface Commands {
        List<String> of(ToolInput args);
    }

    private Cmd() {
    }

    /** A tool that runs its commands and reports what they printed. */
    static AiTool run(AiTool.Builder builder, Commands commands) {
        return builder.handler((ctx, args) -> {
            List<String> list;
            try {
                list = commands.of(args);
            } catch (IllegalArgumentException e) {
                return ToolResult.failed(e.getMessage());
            }
            return CommandTool.run(ctx, list.toArray(String[]::new));
        }).build();
    }

    /** A tool whose commands start a job: the result is {@code running(job)}. */
    static AiTool start(AiTool.Builder builder, String job, Commands commands) {
        return builder.handler((ctx, args) -> {
            List<String> list;
            try {
                list = commands.of(args);
            } catch (IllegalArgumentException e) {
                return ToolResult.failed(e.getMessage());
            }
            return CommandTool.start(ctx, job, list.toArray(String[]::new));
        }).build();
    }

    /** "x y z" from integer params {@code x}, {@code y}, {@code z} (or with a suffix, e.g. x1 y1 z1). */
    static String xyz(ToolInput args, String suffix) {
        return args.integer("x" + suffix) + " " + args.integer("y" + suffix) + " " + args.integer("z" + suffix);
    }

    /** Adds required integer corners x1 y1 z1 x2 y2 z2 of a box. */
    static ToolSchema.Builder region(ToolSchema.Builder schema) {
        return schema
                .integer("x1", "First corner X.").required()
                .integer("y1", "First corner Y.").required()
                .integer("z1", "First corner Z.").required()
                .integer("x2", "Opposite corner X.").required()
                .integer("y2", "Opposite corner Y.").required()
                .integer("z2", "Opposite corner Z.").required();
    }

    /** Selects the box from {@link #region} then runs {@code action} on it (a {@code #sel} action). */
    static List<String> onRegion(ToolInput args, String action) {
        return List.of("sel clear", "sel 1 " + xyz(args, "1"), "sel 2 " + xyz(args, "2"), "sel " + action);
    }

    /** A block or item id from a string param, checked for being one word. */
    static String id(ToolInput args, String name) {
        String id = CommandTool.id(args.string(name));
        if (id.isEmpty() || !id.matches("[a-z0-9_:./-]+")) {
            throw new IllegalArgumentException(name + " must be an id like diamond_ore, not \"" + args.string(name) + "\".");
        }
        return id;
    }
}
