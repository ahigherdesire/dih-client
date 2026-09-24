package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.CommandSuggest;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

public class DamageCommand extends Command {
    public DamageCommand() { super("damage", "Take damage to self (vanilla server allows via /damage if op)."); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            DihClientMessaging.sendPrefixed("§eUsage: " + DihCommands.effectivePrefix() + "damage <amount>");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<DihCommandSource, Double>argument("amount", DoubleArgumentType.doubleArg(0.1, 1024.0))
            .suggests(CommandSuggest::damage)
            .executes(ctx -> {
                double amt = DoubleArgumentType.getDouble(ctx, "amount");
                var conn = Minecraft.getInstance().getConnection();
                if (conn == null) { DihClientMessaging.sendPrefixed("§cNot connected."); return SUCCESS; }
                conn.sendCommand("damage @s " + amt);
                return SUCCESS;
            }));
    }
}
