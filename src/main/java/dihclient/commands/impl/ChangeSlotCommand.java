package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.Command;
import dihclient.commands.CommandSuggest;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihInventoryHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;

public final class ChangeSlotCommand extends Command {
    public ChangeSlotCommand() {
        super("change-slot", "Select hotbar slot 1-9.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(context -> {
            DihClientMessaging.sendPrefixed(
                "§eUsage: §f" + DihCommands.effectivePrefix() + "change-slot <1-9>");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument(
                "hotbar-slot", IntegerArgumentType.integer(1, 9))
            .suggests((context, builder) -> CommandSuggest.literals(
                builder, "1", "2", "3", "4", "5", "6", "7", "8", "9"))
            .executes(context -> changeSlot(IntegerArgumentType.getInteger(context, "hotbar-slot"))));
    }

    private static int changeSlot(int oneBasedSlot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            DihClientMessaging.sendPrefixed("§cNot in a world.");
            return SUCCESS;
        }

        DihInventoryHelper.selectHotbarSlot(mc, oneBasedSlot - 1);
        DihClientMessaging.sendPrefixed("§aSelected hotbar slot §f" + oneBasedSlot + "§a.");
        return SUCCESS;
    }
}
