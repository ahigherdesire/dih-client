package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.Command;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;

public class ClearCommand extends Command {
    public ClearCommand() { super("clear", "Clear every message from the chat.", "cls"); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui == null || mc.gui.hud == null || mc.gui.hud.getChat() == null) {
                DihClientMessaging.sendPrefixed("§cChat unavailable.");
                return SUCCESS;
            }
            try {

                mc.gui.hud.getChat().clearMessages(false);
                mc.gui.hud.getChat().resetChatScroll();
            } catch (Throwable t) {
                DihClientMessaging.sendPrefixed("§cClear failed: " + t.getMessage());
            }

            return SUCCESS;
        });
    }
}
