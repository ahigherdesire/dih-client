package baritone.ai.catalog;

import baritone.ai.AiConfig;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CommandAdaptersTest {

    private static final List<CommandAdapters.Info> BARITONE = List.of(
            new CommandAdapters.Info("goto", List.of("goto"), "Go to a coordinate or block"),
            new CommandAdapters.Info("set", List.of("set", "setting", "settings"), "View or change settings"),
            new CommandAdapters.Info("reloadall", List.of("reloadall"), "Reloads the cache"),
            new CommandAdapters.Info("help", List.of("help", "?"), ""),
            new CommandAdapters.Info("runaway", List.of("runaway", "flee", "escape"), "Flee from the current position"),
            new CommandAdapters.Info("activate", List.of("activate", "act"), "Denied by any name"));

    private static final List<CommandAdapters.Info> CLIENT = List.of(
            new CommandAdapters.Info("modules", List.of("modules", "features"), "List installed modules."),
            new CommandAdapters.Info("say", List.of("say"), "Send a chat message."),
            new CommandAdapters.Info("bind", List.of("bind"), "Bind a key."));

    private static ToolRegistry registry() {
        ToolRegistry registry = new ToolRegistry();
        CommandAdapters.registerBaritone(registry, BARITONE);
        CommandAdapters.registerClient(registry, CLIENT);
        return registry;
    }

    @Test
    void oneRawToolPerCommandLeavingOutDeniedAndHumanOnlyOnes() {
        ToolRegistry registry = registry();
        assertEquals(List.of("cmd_goto", "cmd_runaway", "dot_modules", "dot_say"),
                registry.all().stream().map(AiTool::name).toList());
        for (AiTool tool : registry.all()) {
            assertEquals(ToolCategory.RAW, tool.category(), tool.name());
            assertFalse(tool.job(), "adapters are never jobs: " + tool.name());
        }
        assertNull(registry.get("cmd_set"), "set is on the deny list, as are its aliases");
        assertNull(registry.get("cmd_activate"), "denied by name");
        assertNull(registry.get("cmd_reloadall"));
        assertNull(registry.get("cmd_help"));
        assertNull(registry.get("dot_bind"), "binding keys is for people");
        assertTrue(registry.get("dot_say").dangerous(), "sending chat asks first");
        assertFalse(registry.get("dot_modules").dangerous());
        assertTrue(registry.get("cmd_runaway").description().contains("Also called flee, escape."));
    }

    @Test
    void runsTheCommandWithItsArguments() {
        RecordingCommands commands = new RecordingCommands();
        ToolContext ctx = ToolContext.of(null, ToolContext.Source.AI).withCommands(commands).withConfig(new AiConfig());
        JsonObject args = new JsonObject();
        args.addProperty("args", "100 64 -200");
        ToolResult result = registry().call(ctx, "cmd_goto", args);
        assertTrue(result.ok(), result.text());
        assertEquals(List.of("goto 100 64 -200"), commands.baritone);
        assertEquals("#goto 100 64 -200", result.facts().get("command"));

        ToolResult modules = registry().call(ctx, "dot_modules", new JsonObject());
        assertTrue(modules.ok());
        assertEquals(List.of("modules"), commands.client);
    }

    @Test
    void theDenyListAsItIsNowStillAppliesToTheAi() {
        RecordingCommands commands = new RecordingCommands();
        AiConfig config = new AiConfig();
        config.deniedCommands.add("escape");
        ToolContext ai = ToolContext.of(null, ToolContext.Source.AI).withCommands(commands).withConfig(config);
        ToolResult refused = registry().call(ai, "cmd_runaway", new JsonObject());
        assertEquals(ToolResult.Status.FAILED, refused.status());
        assertTrue(refused.text().contains("deny list"), refused.text());
        assertTrue(commands.baritone.isEmpty());

        ToolContext player = ToolContext.of(null, ToolContext.Source.CHAT).withCommands(commands).withConfig(config);
        assertTrue(registry().call(player, "cmd_runaway", new JsonObject()).ok(), "people aren't limited by the AI's list");
        assertEquals(List.of("runaway"), commands.baritone);
    }

    @Test
    void registeringTwiceKeepsTheFirst() {
        ToolRegistry registry = registry();
        CommandAdapters.registerBaritone(registry, BARITONE);
        assertNotNull(registry.get("cmd_goto"));
        assertEquals(4, registry.all().size());
    }
}
