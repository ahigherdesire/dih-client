package baritone.ai.catalog;

import baritone.ai.AiConfig;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.CommandRunner;
import baritone.ai.tool.ToolArgs;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import dihclient.ai.ClientTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every hand-written tool's argument handling and result shape. Tools that work through commands are run against a
 * recorder, so the exact command strings are checked; the rest are tested in their own classes or, when they need a
 * live world, in the client game test.
 */
final class ToolShapeTest {

    private static final ToolRegistry REGISTRY = ToolRegistry.withDefaults();

    /** Tools with their own unit tests (pure logic split out), named here so the coverage check knows. */
    static final Set<String> TESTED_ELSEWHERE = Set.of(
            "recipe_of", "uses_of", "drops_of", "where_from", "item_lookup", "fuels",   // KnowledgeToolsTest
            "web_search", "web_fetch",                                                    // WebToolsTest
            "load_tools", "list_tools",                                                   // ToolRegistryTest, ToolLoopTest
            "wait", "remember", "plan_item", "acquire",                                   // AiToolsDefinitionTest, below
            "macro_write", "read_offers");                                                // MacroStepsTest, ClientToolsTest

    /** Tools that need a live world; their pure parts are unit-tested and ToolsGameTest runs them in a world. */
    static final Set<String> LIVE_ONLY = Set.of(
            "find", "look_around", "say", "gear_up", "craft", "inventory", "equip", "biome_here", "set_guardian",
            "guardian_status", "whisper", "module_list", "module_toggle", "module_setting", "macro_list", "macro_run",
            "macro_stop", "server_connect", "friend_add", "friend_remove", "trade", "enchant_plan");

    static Stream<Arguments> commandTools() {
        return Stream.of(
                // job
                call("goto", "100 64 -200", "goto 100 64 -200"),
                call("goto", "diamond_ore", "goto diamond_ore"),
                call("goto", "-58", "goto -58"),
                call("goto", "~ ~10 ~", "goto ~ ~10 ~"),
                call("goto_structure", "village", "structure village"),
                call("explore", "", "explore"),
                call("explore", "x=10 z=-20", "explore 10 -20"),
                call("beat_stage", "start", "beat"),
                call("beat_stage", "resume", "beat resume"),
                call("beat_stage", "status", "beat status"),
                call("beat_stage", "plan", "beat plan"),
                call("beat_stage", "stop", "beat stop"),
                call("farm", "", "farm"),
                call("farm", "32", "farm 32"),
                call("flee_to_safety", "", "runaway 32"),
                call("stop", "", "stop"),
                call("build_schematic", "house", "build house"),
                call("build_schematic", "house 1 2 3", "build house 1 2 3"),
                call("run_command", "goto 1 2 3", "goto 1 2 3"),
                // movement
                call("follow_player", "Steve", "follow player Steve"),
                call("follow_mob", "cow", "follow entity cow"),
                call("head_forward", "100", "thisway 100", "path"),
                call("go_to_surface", "", "surface"),
                call("elytra_to", "x=100 z=200", "elytra goto 100 200"),
                call("elytra_to", "100 200 70", "elytra goto 100 70 200"),
                call("home_go", "base", "home base"),
                call("home_set", "base", "home set base"),
                call("home_list", "", "home list"),
                call("home_delete", "base", "home del base"),
                call("goto_portal", "", "portal"),
                call("goto_portal", "skip_nearest=true", "portal skip"),
                call("goto_axis", "", "axis", "path"),
                call("pause", "", "pause"),
                call("resume", "", "resume"),
                // mining
                call("mine_block", "iron_ore", "mine iron_ore"),
                call("mine_block", "iron_ore 16", "mine 16 iron_ore"),
                call("mine_block", "block=\"Iron Ore\"", "mine iron_ore"),
                call("tunnel", "", "tunnel"),
                call("tunnel", "2 3 50", "tunnel 2 3 50"),
                call("clear_area", "0 60 0 4 64 4", "sel clear", "sel 1 0 60 0", "sel 2 4 64 4", "sel cleararea"),
                // building
                call("fill_region", "0 60 0 4 64 4 cobblestone", "sel clear", "sel 1 0 60 0", "sel 2 4 64 4", "sel set cobblestone"),
                call("build_walls", "0 60 0 4 64 4 stone", "sel clear", "sel 1 0 60 0", "sel 2 4 64 4", "sel walls stone"),
                call("build_shell", "0 60 0 4 64 4 glass", "sel clear", "sel 1 0 60 0", "sel 2 4 64 4", "sel shell glass"),
                call("build_sphere", "0 60 0 4 64 4 stone", "sel clear", "sel 1 0 60 0", "sel 2 4 64 4", "sel sphere stone"),
                call("build_hollow_sphere", "0 60 0 4 64 4 stone", "sel clear", "sel 1 0 60 0", "sel 2 4 64 4", "sel hsphere stone"),
                call("replace_blocks", "0 60 0 4 64 4 dirt stone", "sel clear", "sel 1 0 60 0", "sel 2 4 64 4", "sel replace dirt stone"),
                call("clear_selection", "", "sel clear"),
                // inventory, farming
                call("eat", "", "eat"),
                call("eat", "bread", "eat bread"),
                call("collect_drops", "", "pickup"),
                call("collect_drops", "\"diamond, iron_ingot\"", "pickup diamond iron_ingot"),
                call("sleep_in_bed", "", "sleep"),
                // world
                call("waypoint_add", "base", "wp save user base"),
                call("waypoint_add", "base 1 2 3", "wp save user base 1 2 3"),
                call("waypoint_list", "", "wp list"),
                call("waypoint_remove", "base", "wp delete base"),
                call("waypoint_goto", "base", "wp goto base"),
                call("nearest_structure", "stronghold", "where stronghold"),
                call("nether_coords", "800 -1600 overworld", "nether overworld 800 64 -1600"),
                call("players_seen", "", "players"),
                call("players_seen", "Steve", "players Steve"),
                call("find_in_chests", "diamond", "chest diamond"));
    }

    private static Arguments call(String tool, String line, String... commands) {
        return Arguments.of(tool, line, List.of(commands));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("commandTools")
    void runsExactlyTheseCommands(String tool, String line, List<String> expected) {
        RecordingCommands commands = new RecordingCommands();
        ToolResult result = callLine(tool, line, commands, ToolContext.Source.CHAT);
        assertEquals(expected, commands.baritone, tool + " " + line);
        assertTrue(result.ok(), result.text());
        assertEquals("#" + expected.get(expected.size() - 1), result.facts().get("command"));
        AiTool definition = REGISTRY.get(tool);
        if (result.status() == ToolResult.Status.RUNNING) {
            assertTrue(definition.job() || result.facts().containsKey("job"), "a running result names its job");
        }
    }

    static Stream<Arguments> badArguments() {
        return Stream.of(
                Arguments.of("goto", "north please", "target must be"),
                Arguments.of("explore", "x=10", "both x and z"),
                Arguments.of("tunnel", "2", "height, width and depth together"),
                Arguments.of("build_schematic", "house 1", "all of x, y and z"),
                Arguments.of("waypoint_add", "base 1 2", "all of x, y and z"),
                Arguments.of("follow_player", "\"bad name!\"", "one word"),
                Arguments.of("mine_block", "\"iron ore; stop\"", "must be an id"),
                Arguments.of("flee_to_safety", "2", "at least 8"),
                Arguments.of("goto_structure", "castle", "must be one of"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("badArguments")
    void badArgumentsFailWithAReasonAndRunNothing(String tool, String line, String reason) {
        RecordingCommands commands = new RecordingCommands();
        ToolResult result = callLine(tool, line, commands, ToolContext.Source.CHAT);
        assertEquals(ToolResult.Status.FAILED, result.status());
        assertTrue(result.text().contains(reason), result.text());
        assertTrue(commands.baritone.isEmpty(), "nothing ran: " + commands.baritone);
    }

    @Test
    void anUnknownCommandOrAnErrorIsAFailure() {
        RecordingCommands commands = new RecordingCommands();
        commands.next = CommandRunner.Outcome.unknown();
        ToolResult unknown = callLine("goto_axis", "", commands, ToolContext.Source.CHAT);
        assertEquals(ToolResult.Status.FAILED, unknown.status());
        assertTrue(unknown.text().contains("#axis is not available"), unknown.text());
        assertEquals(List.of("axis"), commands.baritone, "stops at the first command that fails");

        commands = new RecordingCommands();
        commands.next = CommandRunner.Outcome.failed("No home called base");
        ToolResult error = callLine("home_go", "base", commands, ToolContext.Source.CHAT);
        assertEquals(ToolResult.Status.FAILED, error.status());
        assertTrue(error.text().contains("No home called base"), error.text());
    }

    @Test
    void jobsReportRunningAndWhatThePrinted() {
        RecordingCommands commands = new RecordingCommands();
        commands.next = CommandRunner.Outcome.ok("Mining [iron_ore]");
        ToolResult result = callLine("mine_block", "iron_ore", commands, ToolContext.Source.CHAT);
        assertEquals(ToolResult.Status.RUNNING, result.status());
        assertEquals("mine", result.facts().get("job"));
        assertTrue(result.text().contains("It printed: Mining [iron_ore]"), result.text());
        assertTrue(result.forModel().contains("\"job\":\"mine\""), result.forModel());
    }

    @Test
    void disconnectIsDangerousAndRunsTheClientCommandOnceConfirmed() {
        ToolRegistry registry = ToolRegistry.withDefaults();
        ClientTools.register(registry);
        RecordingCommands commands = new RecordingCommands();
        ToolContext ctx = ToolContext.of(null, ToolContext.Source.CHAT).withCommands(commands);
        ToolResult unconfirmed = registry.call(ctx, "disconnect", new com.google.gson.JsonObject());
        assertEquals(Boolean.TRUE, unconfirmed.facts().get("needs_confirmation"));
        assertTrue(commands.client.isEmpty());
        ToolResult confirmed = registry.call(ctx.confirmed(true), "disconnect", new com.google.gson.JsonObject());
        assertTrue(confirmed.ok(), confirmed.text());
        assertEquals(List.of("disconnect"), commands.client);
    }

    @Test
    void runCommandKeepsTheAiOffTheDenyListAndOffAcquire() {
        RecordingCommands commands = new RecordingCommands();
        ToolResult denied = callLine("run_command", "set allowBreak false", commands, ToolContext.Source.AI);
        assertEquals(ToolResult.Status.FAILED, denied.status());
        assertTrue(denied.text().contains("deny list"), denied.text());
        ToolResult acquire = callLine("run_command", "acquire diamond", commands, ToolContext.Source.AI);
        assertTrue(acquire.text().contains("Use the acquire tool"), acquire.text());
        assertTrue(commands.baritone.isEmpty());
        assertTrue(callLine("run_command", "set allowBreak false", commands, ToolContext.Source.CHAT).ok(), "a player may");
        ToolResult noGame = callLine("acquire", "diamond", commands, ToolContext.Source.CHAT);
        assertEquals("Not in a game.", noGame.text());
    }

    @Test
    void everyHandWrittenToolHasAShapeTest() {
        ToolRegistry registry = ToolRegistry.withDefaults();
        ClientTools.register(registry);
        Set<String> covered = new HashSet<>(TESTED_ELSEWHERE);
        covered.addAll(LIVE_ONLY);
        commandTools().forEach(args -> covered.add((String) args.get()[0]));
        covered.add("disconnect");
        List<String> missing = new ArrayList<>();
        for (AiTool tool : registry.all()) {
            if (!covered.contains(tool.name())) missing.add(tool.name());
        }
        assertTrue(missing.isEmpty(), "tools without a shape test: " + missing);
        for (String name : covered) assertFalse(registry.get(name) == null, "listed but not registered: " + name);
    }

    static ToolResult callLine(String tool, String line, CommandRunner commands, ToolContext.Source source) {
        AiTool definition = REGISTRY.get(tool);
        assertTrue(definition != null, "no tool " + tool);
        ToolArgs.Parsed parsed = ToolArgs.parseLine(definition.schema(), line);
        if (!parsed.ok()) return ToolResult.failed(parsed.error());
        ToolContext ctx = ToolContext.of(null, source).withCommands(commands).withConfig(new AiConfig());
        return REGISTRY.call(ctx, tool, parsed.args());
    }
}
