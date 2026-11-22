package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;

public class CommandsCommand extends Command {
    public CommandsCommand() { super("commands", "List all available commands.", "cmds"); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> list(""));
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("search", StringArgumentType.greedyString())
            .executes(ctx -> list(StringArgumentType.getString(ctx, "search"))));
    }

    private static int list(String search) {
            String prefix = DihCommands.effectivePrefix();
            var commands = DihCommands.all().stream().filter(command -> matches(command, search))
                .sorted(Comparator.comparing(Command::name)).toList();
            DihClientMessaging.sendPrefixed("§e" + commands.size() + " commands (prefix §f" + prefix + "§e)"
                + (search.isBlank() ? ":" : " matching §f" + search + "§e:"));
            StringBuilder line = new StringBuilder();
            for (Command c : commands) {
                if (!search.isBlank()) {
                    DihClientMessaging.sendPrefixed("§b" + prefix + c.name() + "§7 — " + c.description());
                    continue;
                }
                if (line.length() > 0) line.append("§7, ");
                line.append("§f").append(c.name());
                if (line.length() > 200) { DihClientMessaging.sendPrefixed(line.toString()); line.setLength(0); }
            }
            if (line.length() > 0) DihClientMessaging.sendPrefixed(line.toString());
            DihClientMessaging.sendPrefixed("§7Type §f" + prefix + "help <command>§7 for details.");
            if (search.isBlank()) DihClientMessaging.sendPrefixed("§7Find a feature with §f" + prefix + "commands <search>§7; automation uses this same prefix.");
            return SUCCESS;
    }

    static boolean matches(Command command, String search) {
        String value = search.strip().toLowerCase(Locale.ROOT);
        return command.name().toLowerCase(Locale.ROOT).contains(value)
            || command.description().toLowerCase(Locale.ROOT).contains(value)
            || Arrays.stream(command.aliases()).anyMatch(alias -> alias.toLowerCase(Locale.ROOT).contains(value));
    }
}
