package dihclient.commands.impl;

import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.commands.args.XCarrySlotArgumentType;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihSharedState;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;

public class XCarryCommand extends Command {
    public XCarryCommand() {
        super("xcarry",
            "Stash the held item into an XCarry slot: craft1..craft5 / helmet / chestplate / leggings / boots / offhand / cursor.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            String prefix = DihCommands.effectivePrefix();
            DihClientMessaging.sendPrefixed("§eUsage: §f" + prefix + "xcarry <slot>");
            DihClientMessaging.sendPrefixed("§7Slots: §fcraft1§7..§fcraft5§7, §fhelmet§7, §fchestplate§7, §fleggings§7, §fboots§7, §foffhand§7, §fcursor§7.");
            return SUCCESS;
        });
        root.then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("slot", XCarrySlotArgumentType.slot())
            .executes(ctx -> stash(XCarrySlotArgumentType.get(ctx, "slot"))));
    }

    private static int stash(int targetSlot) {
        String povResult = dihclient.util.multi.MultiPilot.commandStashXCarry(targetSlot);
        if (povResult != null) {
            DihClientMessaging.sendPrefixed(("Sent".equals(povResult) ? "§a" : "§c")
                + "POV XCarry: §f" + povResult);
            return SUCCESS;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) {
            DihClientMessaging.sendPrefixed("§cNot in a world.");
            return SUCCESS;
        }

        ItemStack heldStack = p.getMainHandItem().copy();
        if (heldStack == null || heldStack.isEmpty()) {
            DihClientMessaging.sendPrefixed("§cHold an item first.");
            return SUCCESS;
        }

        if (p.containerMenu != p.inventoryMenu) {
            DihClientMessaging.sendPrefixed("§cOpen your inventory first (E), then run the command.");
            return SUCCESS;
        }

        AbstractContainerMenu container = p.containerMenu;

        int hotbar = p.getInventory().getSelectedSlot();
        int sourceSlotId = 36 + hotbar;

        DihSharedState shared = DihSharedState.get();
        boolean prevBypass = shared.isXCarryArmorBypass();
        shared.setXCarryArmorBypass(true);
        try {

            mc.gameMode.handleContainerInput(container.containerId, sourceSlotId, 0, ContainerInput.PICKUP, p);
            if (container.getCarried().isEmpty()) {
                DihClientMessaging.sendPrefixed("§cCould not pick up the held item.");
                return SUCCESS;
            }

            String itemName = heldStack.getHoverName().getString();
            String slotName = XCarrySlotArgumentType.displayName(targetSlot);
            LinkedHashSet<Integer> forcedSlots = mergedForcedSlots(shared);
            boolean forcedCursor = shared.isXCarryForced() && shared.isXCarryForcedCarryCursor();

            if (targetSlot == XCarrySlotArgumentType.CURSOR) {

                shared.setXCarryForcedTargets(forcedSlots, true);
                shared.setXCarryForced(true);
                shared.setXCarryActive(true);
                DihClientMessaging.sendPrefixed("§aHolding §f" + itemName + "§a on cursor (XCarry).");
                return SUCCESS;
            }

            if (targetSlot < 0 || targetSlot >= container.slots.size()) {
                DihClientMessaging.sendPrefixed("§cInvalid slot (" + targetSlot + ").");

                mc.gameMode.handleContainerInput(container.containerId, sourceSlotId, 0, ContainerInput.PICKUP, p);
                return SUCCESS;
            }

            mc.gameMode.handleContainerInput(container.containerId, targetSlot, 0, ContainerInput.PICKUP, p);

            boolean placed = container.getCarried().isEmpty()
                    && targetSlot >= 0
                    && targetSlot < container.slots.size()
                    && !container.slots.get(targetSlot).getItem().isEmpty();
            if (!placed && !container.getCarried().isEmpty()) {
                mc.gameMode.handleContainerInput(container.containerId, sourceSlotId, 0, ContainerInput.PICKUP, p);
            }

            if (!placed) {
                DihClientMessaging.sendPrefixed("§cCould not stash into §f" + slotName + "§c.");
                return SUCCESS;
            }

            forcedSlots.add(targetSlot);
            shared.setXCarryForcedTargets(forcedSlots, forcedCursor);
            shared.setXCarryForced(true);
            shared.setXCarryActive(true);
            DihClientMessaging.sendPrefixed("§aStashed §f" + itemName + "§a into §f" + slotName + "§a.");
            return SUCCESS;
        } finally {
            shared.setXCarryArmorBypass(prevBypass);
        }
    }

    private static LinkedHashSet<Integer> mergedForcedSlots(DihSharedState shared) {
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        if (shared != null && shared.isXCarryForced()) {
            Set<Integer> existing = shared.getXCarryForcedSlotMask();
            if (existing != null) {
                for (Integer slot : existing) {
                    if (slot != null && slot >= 0) slots.add(slot);
                }
            }
        }
        return slots;
    }
}
