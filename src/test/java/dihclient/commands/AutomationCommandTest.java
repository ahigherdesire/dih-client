package dihclient.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutomationCommandTest {
    @Test void everyBackendCommandGetsAUnifiedRootOrCollisionFallback() throws Exception {
        List<baritone.api.command.ICommand> backend = List.of(descriptor("structure", "structures"),
            descriptor("autosleep", "nightowl"), descriptor("seedinput", "seed"), descriptor("set"));
        Set<String> reserved = new HashSet<>(Set.of("set"));
        List<Command> installed = new ArrayList<>();
        List<String> executed = new ArrayList<>();
        DihAutomationCommands.install(backend, reserved::contains, command -> {
            installed.add(command); reserved.add(command.name()); reserved.addAll(Arrays.asList(command.aliases()));
        }, executed::add, text -> Stream.empty());
        assertTrue(reserved.containsAll(Set.of("structure", "structures", "autosleep", "nightowl", "seedinput", "seed", "baritone")));
        assertFalse(installed.stream().anyMatch(command -> command.name().equals("set")), "native settings command must retain its name");
        var dispatcher = new CommandDispatcher<DihCommandSource>();
        for (Command command : installed) {
            for (String name : Stream.concat(Stream.of(command.name()), Arrays.stream(command.aliases())).toList()) {
                var root = LiteralArgumentBuilder.<DihCommandSource>literal(name); command.build(root); dispatcher.register(root);
            }
        }
        dispatcher.execute("structure village", DihCommandSource.INSTANCE);
        dispatcher.execute("nightowl", DihCommandSource.INSTANCE);
        dispatcher.execute("seed -123456789", DihCommandSource.INSTANCE);
        dispatcher.execute("baritone set allowPlace false", DihCommandSource.INSTANCE);
        assertEquals(List.of("structure village", "nightowl", "seed -123456789", "set allowPlace false"), executed);
        assertTrue(installed.stream().anyMatch(command -> command.name().equals("baritone:set")), "colliding automation commands remain discoverable");
    }
    @Test void dotCommandBodiesPreserveArgumentsAndNoArgumentActions() throws Exception {
        List<String> executed = new ArrayList<>();
        var dispatcher = dispatcher(new AutomationCommand("structure", "Find structures", "structure", executed::add,
            text -> Stream.empty(), List::of));
        dispatcher.execute("structure village", DihCommandSource.INSTANCE);
        dispatcher.execute("structure \"large village\" 120 -32", DihCommandSource.INSTANCE);
        assertEquals(List.of("structure village", "structure \"large village\" 120 -32"), executed);
        var sleep = dispatcher(new AutomationCommand("autosleep", "Sleep", "autosleep", executed::add,
            text -> Stream.empty(), List::of));
        sleep.execute("autosleep", DihCommandSource.INSTANCE);
        assertEquals("autosleep", executed.getLast());
    }

    @Test void argumentSuggestionsReplaceOnlyTheCurrentWord() throws Exception {
        List<String> queried = new ArrayList<>();
        var dispatcher = dispatcher(new AutomationCommand("structure", "Find", "structure", text -> {},
            text -> { queried.add(text); return Stream.of("village"); }, List::of));
        var result = dispatcher.getCompletionSuggestions(dispatcher.parse("structure locate vi", DihCommandSource.INSTANCE)).get();
        assertEquals(List.of("structure locate vi"), queried);
        assertEquals("structure locate village", result.getList().getFirst().apply("structure locate vi"));
    }

    @Test void nativeNamesWinWhileUnoccupiedAutomationAliasesRemainAvailable() {
        assertEquals(List.of("wp"), AutomationCommand.availableNames(List.of("waypoints", "wp"), Set.of("waypoints")::contains));
        assertEquals(List.of("structure", "structures"), AutomationCommand.availableNames(
            List.of("structure", "structures", "STRUCTURE"), name -> false));
    }

    @Test void aliasesWithDifferentActionsKeepTheirOwnLabelsAndCompletion() throws Exception {
        List<String> executed = new ArrayList<>(), completed = new ArrayList<>();
        var command = new AutomationCommand("home", "Home", "home", executed::add,
            text -> { completed.add(text); return Stream.empty(); }, List::of, "sethome");
        var dispatcher = dispatcher(command);
        var alias = LiteralArgumentBuilder.<DihCommandSource>literal("sethome"); command.build(alias); dispatcher.register(alias);
        dispatcher.execute("sethome base", DihCommandSource.INSTANCE);
        dispatcher.getCompletionSuggestions(dispatcher.parse("sethome ba", DihCommandSource.INSTANCE)).get();
        assertEquals(List.of("sethome base"), executed);
        assertEquals(List.of("sethome ba"), completed);
    }

    @Test void displayedHelpHasOnePrefixPreservesAnchorsAndExplainsNativeCollisions() {
        Set<String> names = Set.of("home", "chest", "set");
        assertEquals(".home base", DihAutomationCommands.helpLine("> #home base", ".", names, name -> false));
        assertEquals(".home base", DihAutomationCommands.helpLine("> .home base", ".", names, name -> false));
        assertEquals(".baritone set allowPlace false", DihAutomationCommands.helpLine("> set allowPlace false", ".", names, "set"::equals));
        assertEquals("[home](#home) #fff https://example.com/#home .chest", DihAutomationCommands.formatText(
            "[home](#home) #fff https://example.com/#home #chest", ".", names, name -> false));
    }

    private static CommandDispatcher<DihCommandSource> dispatcher(AutomationCommand command) {
        var dispatcher = new CommandDispatcher<DihCommandSource>();
        var root = LiteralArgumentBuilder.<DihCommandSource>literal(command.name());
        command.build(root); dispatcher.register(root); return dispatcher;
    }

    private static baritone.api.command.ICommand descriptor(String... names) {
        return new baritone.api.command.ICommand() {
            public void execute(String label, baritone.api.command.argument.IArgConsumer args) { }
            public Stream<String> tabComplete(String label, baritone.api.command.argument.IArgConsumer args) { return Stream.empty(); }
            public String getShortDesc() { return "Test automation command"; }
            public List<String> getLongDesc() { return List.of("> " + names[0] + " [arguments]"); }
            public List<String> getNames() { return List.of(names); }
        };
    }
}
