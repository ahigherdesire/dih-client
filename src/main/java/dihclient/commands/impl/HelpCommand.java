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
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("command", StringArgumentType.greedyString())
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
                String name = StringArgumentType.getString(ctx, "command").trim();
                if (name.startsWith("baritone ")) name = "baritone:" + name.substring("baritone ".length()).trim();
                Command cmd = DihCommands.find(name);
                if (cmd == null) { DihClientMessaging.sendPrefixed("§cNo such command: §f" + name); return SUCCESS; }
                String prefix = DihCommands.effectivePrefix();
                DihClientMessaging.sendPrefixed("§b" + prefix + cmd.name() + " §7" + cmd.description());
                if (cmd.aliases().length > 0) DihClientMessaging.sendPrefixed("§7aliases: §f" + String.join(", ", cmd.aliases()));
                for (String line : cmd.helpLines()) DihClientMessaging.sendPrefixed("§7" + line);
                Command automation = DihCommands.find("baritone:" + name.toLowerCase(Locale.ROOT));
                if (automation != null && automation != cmd) DihClientMessaging.sendPrefixed("§7Automation version: §f"
                    + prefix + "baritone " + name + "§7. Details: §f" + prefix + "help baritone:" + name);
                return SUCCESS;
            }));
    }

    private static void listAll() {
        String prefix = DihCommands.effectivePrefix();
        DihClientMessaging.sendPrefixed("§bDIH commands§7 — one prefix for the client and automation.");
        DihClientMessaging.sendPrefixed("§f" + prefix + "commands [search]§7 — browse commands or find a feature.");
        DihClientMessaging.sendPrefixed("§f" + prefix + "help <command>§7 — detailed usage and aliases.");
        DihClientMessaging.sendPrefixed("§7Examples: §f" + prefix + "structure§7, §f" + prefix + "autosleep§7, §f" + prefix + "seedinput§7.");
        DihClientMessaging.sendPrefixed("§f" + prefix + "baritone <command>§7 — automation commands whose names overlap DIH commands.");
    }
}
