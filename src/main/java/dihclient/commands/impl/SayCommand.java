package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

public class SayCommand extends Command {
    public SayCommand() { super("say", "Send a chat message."); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            DihClientMessaging.sendPrefixed("§eUsage: " + DihCommands.effectivePrefix() + "say <message>");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("message", StringArgumentType.greedyString())
            .executes(ctx -> {
                String msg = StringArgumentType.getString(ctx, "message");
                var conn = Minecraft.getInstance().getConnection();
                if (conn == null) { DihClientMessaging.sendPrefixed("§cNot connected."); return SUCCESS; }
                if (msg.startsWith("/")) conn.sendCommand(msg.substring(1));

                else DihCommands.sendPlainChat(conn, msg);
                return SUCCESS;
            }));
    }
}
