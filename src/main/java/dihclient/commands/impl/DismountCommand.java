package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;

public class DismountCommand extends Command {
    public DismountCommand() { super("dismount", "Force-dismount from any vehicle."); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.getConnection() == null) { DihClientMessaging.sendPrefixed("§cNot in a world."); return SUCCESS; }
            if (mc.player.getVehicle() == null) { DihClientMessaging.sendPrefixed("§eNot riding anything."); return SUCCESS; }
            try {
                mc.player.removeVehicle();
                DihClientMessaging.sendPrefixed("§aDismounted.");
            } catch (Throwable t) {
                DihClientMessaging.sendPrefixed("§cDismount failed: " + t.getMessage());
            }
            return SUCCESS;
        });
    }
}
