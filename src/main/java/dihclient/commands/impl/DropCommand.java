package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.Command;
import dihclient.commands.CommandSuggest;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihDropHelper;
import dihclient.util.DihInventoryHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class DropCommand extends Command {
    public DropCommand() {
        super("drop", "Drop hand, full inventory, or an amount of a held/specific item.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            showUsage();
            return SUCCESS;
        });

        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("hand")
            .executes(ctx -> dropHand()));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("fullinventory")
            .executes(ctx -> dropFullInventory()));
        root.then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument(
                "amount", IntegerArgumentType.integer(1))
            .suggests(CommandSuggest::counts)
            .executes(ctx -> dropHeldAmount(IntegerArgumentType.getInteger(ctx, "amount")))
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument(
                    "item", StringArgumentType.word())
                .suggests(CommandSuggest::itemIds)
                .executes(ctx -> dropItemAmount(
                    IntegerArgumentType.getInteger(ctx, "amount"),
                    StringArgumentType.getString(ctx, "item")))));
    }

    private static void showUsage() {
        String prefix = DihCommands.effectivePrefix();
        DihClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "drop hand");
        DihClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "drop fullinventory");
        DihClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "drop <amount> [item_id]");
    }

    private static int dropHand() {
        Minecraft mc = Minecraft.getInstance();
        if (!ready(mc)) return SUCCESS;

        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty()) {
            DihClientMessaging.sendPrefixed("§cYou are not holding an item.");
            return SUCCESS;
        }

        int amount = held.getCount();
        Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());
        mc.player.drop(true);
        DihClientMessaging.sendPrefixed("§aDropped §f" + amount + "x " + id + "§a.");
        return SUCCESS;
    }

    private static int dropFullInventory() {
        Minecraft mc = Minecraft.getInstance();
        if (!ready(mc)) return SUCCESS;

        int droppedItems = 0;
        int droppedStacks = 0;
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack stack = mc.player.getInventory().getItem(inventorySlot);
            if (stack.isEmpty()) continue;

            int handlerSlot = DihInventoryHelper.toHandlerSlot(mc, inventorySlot);
            if (handlerSlot < 0) continue;

            int stackCount = stack.getCount();
            if (DihDropHelper.dropFromHandlerSlot(mc, handlerSlot, 0) > 0) {
                droppedItems += stackCount;
                droppedStacks++;
            }
        }

        if (droppedStacks == 0) {
            DihClientMessaging.sendPrefixed("§eYour inventory is empty.");
        } else {
            DihClientMessaging.sendPrefixed(
                "§aDropped §f" + droppedItems + " items §7(" + droppedStacks + " stacks§7)§a.");
        }
        return SUCCESS;
    }

    private static int dropHeldAmount(int requested) {
        Minecraft mc = Minecraft.getInstance();
        if (!ready(mc)) return SUCCESS;

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        ItemStack held = mc.player.getInventory().getItem(selectedSlot);
        if (held.isEmpty()) {
            DihClientMessaging.sendPrefixed("§cYou are not holding an item.");
            return SUCCESS;
        }

        Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());
        return dropResolvedItemAmount(mc, requested, id, held.getItem());
    }

    private static int dropItemAmount(int requested, String rawItemId) {
        Minecraft mc = Minecraft.getInstance();
        if (!ready(mc)) return SUCCESS;

        Identifier id = parseItemId(rawItemId);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            DihClientMessaging.sendPrefixed("§cUnknown item id: §f" + rawItemId);
            return SUCCESS;
        }

        return dropResolvedItemAmount(mc, requested, id, item);
    }

    private static int dropResolvedItemAmount(Minecraft mc, int requested, Identifier id, Item item) {
        List<Integer> matchingSlots = matchingInventorySlots(mc, item);
        int available = 0;
        for (int slot : matchingSlots) {
            available += mc.player.getInventory().getItem(slot).getCount();
        }
        if (available == 0) {
            DihClientMessaging.sendPrefixed("§cYou do not have §f" + id + "§c in your inventory.");
            return SUCCESS;
        }

        int remaining = Math.min(requested, available);
        int dropped = 0;
        for (int inventorySlot : matchingSlots) {
            if (remaining <= 0) break;
            ItemStack stack = mc.player.getInventory().getItem(inventorySlot);
            if (stack.isEmpty() || stack.getItem() != item) continue;

            int amount = Math.min(remaining, stack.getCount());
            int handlerSlot = DihInventoryHelper.toHandlerSlot(mc, inventorySlot);
            if (handlerSlot < 0) continue;
            if (DihDropHelper.dropFromHandlerSlot(mc, handlerSlot, amount) == 0) continue;

            dropped += amount;
            remaining -= amount;
        }

        if (dropped == 0) {
            DihClientMessaging.sendPrefixed("§cCould not drop §f" + id + "§c from the current screen.");
        } else {
            reportDrop(id, dropped, requested);
        }
        return SUCCESS;
    }

    private static List<Integer> matchingInventorySlots(Minecraft mc, Item item) {
        List<Integer> slots = new ArrayList<>();
        int selected = mc.player.getInventory().getSelectedSlot();
        addIfMatching(mc, item, selected, slots);
        for (int slot = 0; slot < 9; slot++) {
            if (slot != selected) addIfMatching(mc, item, slot, slots);
        }
        for (int slot = 9; slot < 36; slot++) {
            addIfMatching(mc, item, slot, slots);
        }
        return slots;
    }

    private static void addIfMatching(Minecraft mc, Item item, int slot, List<Integer> slots) {
        ItemStack stack = mc.player.getInventory().getItem(slot);
        if (!stack.isEmpty() && stack.getItem() == item) slots.add(slot);
    }

    private static Identifier parseItemId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return Identifier.tryParse(value.indexOf(':') >= 0 ? value : "minecraft:" + value);
    }

    private static void reportDrop(Identifier id, int dropped, int requested) {
        if (dropped < requested) {
            DihClientMessaging.sendPrefixed(
                "§eDropped §f" + dropped + "/" + requested + "x " + id + "§e (all available).");
        } else {
            DihClientMessaging.sendPrefixed("§aDropped §f" + dropped + "x " + id + "§a.");
        }
    }

    private static boolean ready(Minecraft mc) {
        if (mc.player != null && mc.gameMode != null) return true;
        DihClientMessaging.sendPrefixed("§cNot in a world.");
        return false;
    }
}
