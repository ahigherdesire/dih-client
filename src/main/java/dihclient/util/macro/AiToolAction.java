package dihclient.util.macro;

import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolResult;
import dihclient.ai.ToolRunner;
import dihclient.api.macro.ContextMacroAction;
import dihclient.api.macro.MacroExecutionContext;
import net.minecraft.nbt.CompoundTag;

/**
 * AI Tool: runs one registry tool, the same as typing {@code .tools <tool> <args>}. Later steps can read
 * {@code tool_status} ({@code ok}, {@code running}, {@code needs} or {@code failed}) and {@code tool_output} (the
 * result text). Dangerous tools only run when {@link #allowDangerous} is on; otherwise they fail with "needs
 * confirmation".
 */
public class AiToolAction implements ContextMacroAction {
    public String tool = "";
    /** Arguments as typed after the tool name; templates like {@code {x}} are filled in first. */
    public String args = "";
    public boolean allowDangerous = false;
    private boolean enabled = true;

    @Override
    public void run(MacroExecutionContext ctx) {
        String name = resolve(ctx, tool).trim();
        if (name.isEmpty()) {
            publish(ctx, ToolResult.failed("No tool set."));
            return;
        }
        ctx.setStatus("Tool: " + name);
        publish(ctx, ToolRunner.run(name, resolve(ctx, args), ToolContext.Source.MACRO, allowDangerous));
    }

    private static String resolve(MacroExecutionContext ctx, String template) {
        String text = template == null ? "" : template;
        MacroTemplate.Resolution resolved = ctx.resolveTemplate(text);
        return resolved.success() ? resolved.value() : text;
    }

    private static void publish(MacroExecutionContext ctx, ToolResult result) {
        ctx.setVariable("tool_status", MacroValue.text(result.status().id()));
        ctx.setVariable("tool_output", MacroValue.text(result.text()));
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", getType().name());
        tag.putString("tool", tool == null ? "" : tool);
        tag.putString("args", args == null ? "" : args);
        tag.putBoolean("allowDangerous", allowDangerous);
        tag.putBoolean("enabled", enabled);
        return tag;
    }

    @Override
    public void fromTag(CompoundTag tag) {
        tool = tag.getStringOr("tool", "");
        args = tag.getStringOr("args", "");
        allowDangerous = tag.getBooleanOr("allowDangerous", false);
        enabled = tag.getBooleanOr("enabled", true);
    }

    @Override
    public MacroActionType getType() {
        return MacroActionType.AI_TOOL;
    }

    @Override
    public String getDisplayName() {
        String name = tool == null || tool.isBlank() ? "(none)" : tool.trim();
        String typed = args == null || args.isBlank() ? "" : " " + args.trim();
        return "Tool " + name + typed;
    }

    @Override
    public String getIcon() {
        return "T";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
