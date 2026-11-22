package dihclient.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

/** A normal DIH command backed by an automation command, with native argument completion. */
final class AutomationCommand extends Command {
    private final String label;
    private final Consumer<String> execute;
    private final Function<String, Stream<String>> complete;
    private final Supplier<List<String>> help;

    AutomationCommand(String name, String description, String label, Consumer<String> execute,
                      Function<String, Stream<String>> complete, Supplier<List<String>> help, String... aliases) {
        super(name, description, aliases);
        this.label = label; this.execute = execute; this.complete = complete; this.help = help;
    }

    @Override public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> { execute.accept(invokedLabel(ctx)); return SUCCESS; });
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("arguments", StringArgumentType.greedyString())
            .suggests((ctx, builder) -> {
                String arguments = builder.getRemaining();
                String actual = invokedLabel(ctx);
                String query = actual.isEmpty() ? arguments : actual + " " + arguments;
                var output = builder.createOffset(builder.getStart() + arguments.lastIndexOf(' ') + 1);
                try (Stream<String> values = complete.apply(query)) { values.distinct().limit(200).forEach(output::suggest); }
                return output.buildFuture();
            })
            .executes(ctx -> {
                String arguments = StringArgumentType.getString(ctx, "arguments");
                String actual = invokedLabel(ctx);
                execute.accept(actual.isEmpty() ? arguments : actual + " " + arguments);
                return SUCCESS;
            }));
    }

    private String invokedLabel(com.mojang.brigadier.context.CommandContext<DihCommandSource> context) {
        if (label.isEmpty()) return "";
        String invoked = context.getNodes().getFirst().getNode().getName();
        return invoked.startsWith("baritone:") ? label : invoked;
    }

    @Override public List<String> helpLines() { return help.get(); }

    static List<String> availableNames(List<String> names, Predicate<String> reserved) {
        return names.stream().map(n -> n.toLowerCase(Locale.ROOT)).distinct().filter(n -> !reserved.test(n)).toList();
    }
}
