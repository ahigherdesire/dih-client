package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class DisconnectCommand extends Command {
    public DisconnectCommand() { super("disconnect", "Disconnect from the current server."); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null) { DihClientMessaging.sendPrefixed("§cNot connected."); return SUCCESS; }
            try {
                mc.getConnection().getConnection().disconnect(Component.literal("Disconnected via .disconnect"));
            } catch (Throwable t) {
                DihClientMessaging.sendPrefixed("§cDisconnect failed: " + t.getMessage());
            }
            return SUCCESS;
        });
    }
}
