package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.Command;
import dihclient.util.DihClientMessaging;
import dihclient.util.mm.MatchmakingManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

public class IrcCommand extends Command {
    public IrcCommand() { super("irc", "Send a message to the Matchmaking lobby chat."); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            DihClientMessaging.sendPrefixed("§eUsage: " + DihCommands.effectivePrefix() + "irc <message>");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("message", StringArgumentType.greedyString())
            .executes(ctx -> {
                String msg = StringArgumentType.getString(ctx, "message");
                MatchmakingManager mm = MatchmakingManager.get();
                if (!mm.inLobby()) {
                    DihClientMessaging.sendPrefixed("§cNot in a lobby. Open Matchmaking and join or create one first.");
                    return SUCCESS;
                }

                mm.sendChat(msg);
                return SUCCESS;
            }));
    }
}
