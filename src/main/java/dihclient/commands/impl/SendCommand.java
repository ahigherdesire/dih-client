package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.args.PacketClassArgumentType;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihPacketArgumentBuilder;
import dihclient.util.DihPacketRegistry;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

public class SendCommand extends Command {
    public SendCommand() { super("send", "Send a raw C2S packet by class name with optional field=value arguments."); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            DihClientMessaging.sendPrefixed("§eUsage: §f" + DihCommands.effectivePrefix() + "send <packetName> [field=value ...]");
            DihClientMessaging.sendPrefixed("§7Tip: press Tab after the space to search C2S packet classes.");
            DihClientMessaging.sendPrefixed("§7Example: §f" + DihCommands.effectivePrefix() + "send ServerboundSetCarriedItemPacket slot=0");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("packet", PacketClassArgumentType.packetClass())
            .executes(ctx -> send(PacketClassArgumentType.get(ctx, "packet"), ""))
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("args", StringArgumentType.greedyString())
                .suggests((ctx, builder) -> {
                    Class<? extends Packet<?>> cls = DihPacketRegistry.getPacket(PacketClassArgumentType.get(ctx, "packet"));
                    return DihPacketArgumentBuilder.suggest(cls, builder);
                })
                .executes(ctx -> send(PacketClassArgumentType.get(ctx, "packet"), StringArgumentType.getString(ctx, "args")))));
    }

    private int send(String name, String args) {
        Class<? extends Packet<?>> cls = DihPacketRegistry.getPacket(name);
        if (cls == null) {
            DihClientMessaging.sendPrefixed("§cUnknown packet: §f" + name);
            return SUCCESS;
        }
        if (!DihPacketRegistry.getC2SPackets().contains(cls)) {
            DihClientMessaging.sendPrefixed("§cRefusing to send non-C2S packet: §f" + name);
            return SUCCESS;
        }

        DihPacketArgumentBuilder.PreparedArgs prepared;
        try {
            prepared = DihPacketArgumentBuilder.prepare(args);
        } catch (IllegalArgumentException e) {
            DihClientMessaging.sendPrefixed("§cBad arguments: " + e.getMessage());
            return SUCCESS;
        }
        DihPacketArgumentBuilder.Result result = DihPacketArgumentBuilder.build(cls, prepared.args());
        if (result.help()) {
            sendLines("§e", result.message());
            return SUCCESS;
        }
        if (!result.ok()) {
            sendLines("§c", "Failed to build §f" + name + "§c: " + result.message());
            return SUCCESS;
        }

        if (prepared.dryRun()) {
            DihClientMessaging.sendPrefixed("§eBuilt §f" + name + "§7 (" + result.source() + ") §e- dry run, not sent.");
            return SUCCESS;
        }
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) {
            DihClientMessaging.sendPrefixed("§cNo network connection.");
            return SUCCESS;
        }
        dihclient.util.DihSharedState.get().sendPacketBypassDelay(conn, result.packet());
        DihClientMessaging.sendPrefixed("§aSent §f" + name + "§7 (" + result.source() + ")");
        return SUCCESS;
    }

    private static void sendLines(String color, String message) {
        if (message == null || message.isBlank()) return;
        for (String line : message.split("\\R")) {
            if (!line.isBlank()) DihClientMessaging.sendPrefixed(color + line);
        }
    }
}
