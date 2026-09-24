package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.Command;
import dihclient.commands.args.MenuSlotArgumentType;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihInventoryHelper;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;

public final class ClickSlotCommand extends Command {
    public ClickSlotCommand() {
        super("click-slot", "Click a GUI/player slot with Item Click actions.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(context -> {
            DihClientMessaging.sendPrefixed(
                "§eUsage: §f" + DihCommands.effectivePrefix() + "click-slot <slot> [click] [times]");
            return SUCCESS;
        });

        RequiredArgumentBuilder<DihCommandSource, Integer> slot = RequiredArgumentBuilder
            .<DihCommandSource, Integer>argument("slot", MenuSlotArgumentType.slot())
            .executes(context -> click(context, ItemClickCommandSupport.ClickSpec.click(
                "Left", ContainerInput.PICKUP, 0), 1));
        ItemClickCommandSupport.attachModes(slot, ClickSlotCommand::click);
        root.then(slot);
    }

    private static int click(
        com.mojang.brigadier.context.CommandContext<DihCommandSource> context,
        ItemClickCommandSupport.ClickSpec spec,
        int times
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.containerMenu == null) {
            DihClientMessaging.sendPrefixed("§cNo active inventory or GUI.");
            return SUCCESS;
        }

        int visibleSlot = MenuSlotArgumentType.get(context, "slot");
        int handlerSlot = DihInventoryHelper.toHandlerSlot(mc, visibleSlot);
        return ItemClickCommandSupport.clickHandlerSlot(
            handlerSlot, spec, times, MenuSlotArgumentType.displayToken(visibleSlot));
    }
}
