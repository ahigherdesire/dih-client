package dihclient.commands;

import baritone.api.BaritoneAPI;
import baritone.api.command.ICommand;
import baritone.api.command.manager.ICommandManager;
import dihclient.util.DihClientMessaging;
import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Presents the automation registry through the same dispatcher, help and completion as DIH. */
final class DihAutomationCommands {
    private static final Pattern REFERENCE = Pattern.compile("(?<![\\w/#])(?<!\\]\\()#([A-Za-z][A-Za-z0-9_-]*)");
    private static volatile Set<String> automationNames = Set.of();
    private DihAutomationCommands() { }

    static void install(Predicate<String> reserved, Consumer<Command> register) {
        ICommandManager manager = manager();
        install(manager.getRegistry().entries, reserved, register,
            DihAutomationCommands::execute, DihAutomationCommands::complete);
    }

    static void install(Collection<ICommand> commands, Predicate<String> reserved, Consumer<Command> register,
                        Consumer<String> execute, Function<String, Stream<String>> complete) {
        Set<String> names = new HashSet<>();
        for (ICommand command : commands) names.addAll(command.getNames());
        automationNames = Set.copyOf(names);
        // Always provide a dot-only escape for native name collisions and automation settings.
        if (!reserved.test("baritone")) register.accept(new AutomationCommand("baritone", "Run any automation command or setting.", "",
            execute, complete,
            () -> List.of(DihCommands.effectivePrefix() + "baritone <command> [arguments]",
                "Use this when a native DIH command has the same name; for example "
                    + DihCommands.effectivePrefix() + "baritone set allowPlace false.")));
        for (ICommand command : commands) {
            List<String> available = new ArrayList<>(AutomationCommand.availableNames(command.getNames(), reserved));
            String label = command.getNames().getFirst();
            String qualified = "baritone:" + label;
            if (!reserved.test(qualified)) available.add(qualified);
            if (available.isEmpty()) continue;
            register.accept(new AutomationCommand(available.getFirst(), command.getShortDesc(), label,
                execute, complete,
                () -> command.getLongDesc().stream().map(DihAutomationCommands::helpLine).toList(),
                available.subList(1, available.size()).toArray(String[]::new)));
        }
    }

    private static ICommandManager manager() {
        return BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager();
    }

    private static void execute(String body) {
        if (DihCommands.commandsBlockedByPanic()) return;
        if (dihclient.util.multi.MultiPilot.isActive()) {
            DihClientMessaging.sendPrefixed("§eAutomation commands control this client. Leave bot POV before running them.");
            return;
        }
        String command = body.isBlank() ? "help" : body;
        String[] parts = command.split("\\s+", 2);
        // Preserve Baritone's setting shortcuts within the explicit .baritone entry point.
        if (manager().getCommand(parts[0]) == null) {
            var setting = BaritoneAPI.getSettings().byLowerName.get(parts[0].toLowerCase(Locale.ROOT));
            if (setting != null && !setting.isJavaOnly()) command = "set " + command;
        }
        if (!manager().execute(command)) DihClientMessaging.sendPrefixed("§cUnknown automation command. Use "
            + DihCommands.effectivePrefix() + "commands <search> or " + DihCommands.effectivePrefix() + "help <command>.");
    }

    private static Stream<String> complete(String body) {
        String[] parts = body.split("\\s+", 2);
        if (parts.length > 1 && manager().getCommand(parts[0]) == null) {
            var setting = BaritoneAPI.getSettings().byLowerName.get(parts[0].toLowerCase(Locale.ROOT));
            if (setting != null && !setting.isJavaOnly()) return manager().tabComplete("set " + body);
        }
        Stream<String> commands = manager().tabComplete(body);
        if (parts.length == 1) return Stream.concat(commands, BaritoneAPI.getSettings().allSettings.stream()
            .filter(setting -> !setting.isJavaOnly() && setting.getName().toLowerCase(Locale.ROOT).startsWith(body.toLowerCase(Locale.ROOT)))
            .map(setting -> setting.getName()));
        return commands;
    }

    static String helpLine(String text) {
        return helpLine(text, DihCommands.effectivePrefix(), automationNames, DihAutomationCommands::collides);
    }

    static String helpLine(String text, String prefix, Set<String> names, Predicate<String> collision) {
        if (text.startsWith("> ")) {
            String body = text.substring(2);
            if (body.startsWith("#")) return formatText(body, prefix, names, collision);
            if (body.startsWith(prefix)) return body;
            return commandText(body, prefix, collision);
        }
        return formatText(text, prefix, names, collision);
    }

    static String formatText(String text) {
        if (text == null || text.indexOf('#') < 0) return text;
        return formatText(text, DihCommands.effectivePrefix(), automationNames, DihAutomationCommands::collides);
    }

    static String formatText(String text, String prefix, Set<String> names, Predicate<String> collision) {
        if (text == null || text.indexOf('#') < 0) return text;
        var matcher = REFERENCE.matcher(text);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = names.contains(name.toLowerCase(Locale.ROOT))
                ? commandText(name, prefix, collision) : matcher.group();
            matcher.appendReplacement(output, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output); return output.toString();
    }

    private static boolean collides(String root) {
        Command existing = DihCommands.find(root);
        return existing != null && !(existing instanceof AutomationCommand);
    }

    private static String commandText(String body, String prefix, Predicate<String> collision) {
        String root = body.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return prefix + (collision.test(root) ? "baritone " : "") + body;
    }
}
