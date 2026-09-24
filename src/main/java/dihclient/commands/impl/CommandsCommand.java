package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class CommandsCommand extends Command {
    public CommandsCommand() { super("commands", "List all available commands.", "cmds"); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            String prefix = DihCommands.effectivePrefix();
            DihClientMessaging.sendPrefixed("§e" + DihCommands.all().size() + " commands (prefix §f" + prefix + "§e):");
            StringBuilder line = new StringBuilder();
            for (Command c : DihCommands.all()) {
                if (line.length() > 0) line.append("§7, ");
                line.append("§f").append(c.name());
                if (line.length() > 200) { DihClientMessaging.sendPrefixed(line.toString()); line.setLength(0); }
            }
            if (line.length() > 0) DihClientMessaging.sendPrefixed(line.toString());
            DihClientMessaging.sendPrefixed("§7Type §f" + prefix + "help <command>§7 for details.");
            return SUCCESS;
        });
    }
}
