package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.CommandSuggest;
import dihclient.util.DihClientMessaging;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class GiveCommand extends Command {
    public GiveCommand() { super("give", "Give item to self (creative only)."); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            DihClientMessaging.sendPrefixed("§eUsage: " + DihCommands.effectivePrefix() + "give <item> [count]");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<DihCommandSource, String>argument("item", StringArgumentType.word())
            .suggests(CommandSuggest::itemIds)
            .executes(ctx -> give(StringArgumentType.getString(ctx, "item"), 1))
            .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("count", IntegerArgumentType.integer(1, 64))
                .suggests(CommandSuggest::counts)
                .executes(ctx -> give(StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "count")))));
    }

    private static int give(String itemId, int count) {
        Minecraft mc = Minecraft.getInstance();
        boolean pov = dihclient.util.multi.MultiPilot.isActive();
        if (!pov && mc.player == null) { DihClientMessaging.sendPrefixed("§cNot in a world."); return SUCCESS; }
        if (!pov && !mc.player.getAbilities().instabuild) { DihClientMessaging.sendPrefixed("§cCreative mode required."); return SUCCESS; }
        Identifier id = itemId.contains(":") ? Identifier.tryParse(itemId) : Identifier.tryParse("minecraft:" + itemId);
        if (id == null) { DihClientMessaging.sendPrefixed("§cInvalid item id: §f" + itemId); return SUCCESS; }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) { DihClientMessaging.sendPrefixed("§cUnknown item: §f" + id); return SUCCESS; }
        ItemStack stack = new ItemStack(item, count);
        String povResult = dihclient.util.multi.MultiPilot.commandGiveCreative(stack);
        if (povResult != null) {
            DihClientMessaging.sendPrefixed(("Sent".equals(povResult) ? "§a" : "§c")
                + "POV give: §f" + povResult);
            return SUCCESS;
        }
        int slot = mc.player.getInventory().getSelectedSlot();
        int containerSlot = 36 + slot;
        mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket((short) containerSlot, stack));
        mc.player.getInventory().setItem(slot, stack);
        DihClientMessaging.sendPrefixed("§aGave §f" + id + " x" + count);
        return SUCCESS;
    }
}
