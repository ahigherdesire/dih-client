package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.args.KeyArgumentType;
import dihclient.modules.DihModule;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class BindCommand extends Command {
    public BindCommand() { super("bind", "Bind a key to a command. Usage: bind <key> <command…> | bind clear <key>"); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            String prefix = DihCommands.effectivePrefix();
            DihClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "bind <key> <command>");
            DihClientMessaging.sendPrefixed("§7Example: §f" + prefix + "bind G " + prefix + "macro myMacro");
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("clear")
            .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("key", KeyArgumentType.key())
                .executes(ctx -> {
                    int key = KeyArgumentType.get(ctx, "key");
                    DihModule.get().clearCommandBind(key);
                    DihClientMessaging.sendPrefixed("§eCleared bind for §f" + KeyArgumentType.keyName(key));
                    return SUCCESS;
                })));
        root.then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("key", KeyArgumentType.key())
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("command", StringArgumentType.greedyString())
                .executes(ctx -> {
                    int key = KeyArgumentType.get(ctx, "key");
                    String cmd = StringArgumentType.getString(ctx, "command");
                    DihModule.get().setCommandBind(key, cmd);
                    DihClientMessaging.sendPrefixed("§aBound §f" + KeyArgumentType.keyName(key) + "§a → §f" + cmd);
                    return SUCCESS;
                })));
    }
}
