package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.args.KeyArgumentType;
import dihclient.modules.DihModule;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.Map;

public class BindsCommand extends Command {
    public BindsCommand() { super("binds", "List command keybinds (set with `bind`)."); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            Map<Integer, String> binds = DihModule.get().getCommandBinds();
            if (binds.isEmpty()) { DihClientMessaging.sendPrefixed("§eNo command keybinds set."); return SUCCESS; }
            DihClientMessaging.sendPrefixed("§e" + binds.size() + " command keybinds:");
            for (Map.Entry<Integer, String> e : binds.entrySet()) {
                DihClientMessaging.sendPrefixed("§b" + KeyArgumentType.keyName(e.getKey()) + "§7 → §f" + e.getValue());
            }
            return SUCCESS;
        });
    }
}
