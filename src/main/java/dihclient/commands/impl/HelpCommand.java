package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Locale;

public class HelpCommand extends Command {
    public HelpCommand() { super("help", "Show usage info for a command (or list all)."); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> { listAll(); return SUCCESS; });
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("command", StringArgumentType.word())
            .suggests((ctx, builder) -> {
                String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
                for (Command command : DihCommands.all()) {
                    if (command.name().toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(command.name());
                    for (String alias : command.aliases()) {
                        if (alias != null && alias.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(alias);
                    }
                }
                return builder.buildFuture();
            })
            .executes(ctx -> {
                String name = StringArgumentType.getString(ctx, "command");
                Command cmd = DihCommands.find(name);
                if (cmd == null) { DihClientMessaging.sendPrefixed("§cNo such command: §f" + name); return SUCCESS; }
                String prefix = DihCommands.effectivePrefix();
                DihClientMessaging.sendPrefixed("§b" + prefix + cmd.name() + " §7" + cmd.description());
                if (cmd.aliases().length > 0) DihClientMessaging.sendPrefixed("§7aliases: §f" + String.join(", ", cmd.aliases()));
                return SUCCESS;
            }));
    }

    private static void listAll() {
        String prefix = DihCommands.effectivePrefix();
        for (Command c : DihCommands.all()) {
            DihClientMessaging.sendPrefixed("§b" + prefix + c.name() + "§7 - " + c.description());
        }
    }
}
