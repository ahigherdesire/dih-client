package dihclient.ai;

import baritone.Baritone;
import baritone.ai.AiBrain;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolArgs;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Runs registry tools from typed text: {@code .tools} in chat and the AI_TOOL macro step. */
public final class ToolRunner {

    private ToolRunner() {
    }

    /** The AI brain of the primary Baritone, or null before the first world. */
    public static AiBrain brain() {
        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            return baritone instanceof Baritone b ? b.getAiBehavior().getBrain() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Parses {@code line} against the tool's schema and runs it on the calling thread, which must not be the game
     * thread for tools that wait. Never throws.
     */
    public static ToolResult run(String toolName, String line, ToolContext.Source source, boolean confirmed) {
        AiTool tool = ToolRegistry.standard().get(toolName);
        if (tool == null) {
            return ToolResult.failed(unknown(toolName));
        }
        ToolArgs.Parsed parsed = ToolArgs.parseLine(tool.schema(), line);
        if (!parsed.ok()) {
            return ToolResult.failed(parsed.error());
        }
        return call(tool, parsed.args(), source, confirmed);
    }

    public static ToolResult call(AiTool tool, JsonObject args, ToolContext.Source source, boolean confirmed) {
        ToolContext ctx = ToolContext.of(brain(), source).confirmed(confirmed);
        return ToolRegistry.standard().call(ctx, tool.name(), args);
    }

    /** "No tool named x", with the closest names when some contain what was typed. */
    public static String unknown(String toolName) {
        String typed = toolName == null ? "" : toolName.trim().toLowerCase(Locale.ROOT);
        List<String> close = new ArrayList<>();
        for (AiTool tool : ToolRegistry.standard().all()) {
            if (!typed.isEmpty() && (tool.name().contains(typed) || typed.contains(tool.name()))) {
                close.add(tool.name());
            }
        }
        return "No tool named " + typed + "." + (close.isEmpty() ? "" : " Did you mean " + String.join(", ", close) + "?");
    }
}
