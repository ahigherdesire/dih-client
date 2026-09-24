package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.modules.ModuleRegistry;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class ModulesCommand extends Command {
    public ModulesCommand() { super("modules", "List installed modules.", "features"); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            java.util.List<String> names = ModuleRegistry.names();
            DihClientMessaging.sendPrefixed("§e" + names.size() + " modules:");
            StringBuilder line = new StringBuilder();
            for (String n : names) {
                if (line.length() > 0) line.append("§7, ");
                line.append("§f").append(n);
                if (line.length() > 200) { DihClientMessaging.sendPrefixed(line.toString()); line.setLength(0); }
            }
            if (line.length() > 0) DihClientMessaging.sendPrefixed(line.toString());
            return SUCCESS;
        });
    }
}
