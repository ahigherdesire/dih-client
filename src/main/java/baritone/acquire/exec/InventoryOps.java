package baritone.acquire.exec;

import baritone.api.utils.IPlayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/** The few inventory clicks the executor makes. All of them run on the game thread. */
final class InventoryOps {

    private InventoryOps() {
    }

    /** Menu slot of main-inventory index {@code invIndex} in the player's own inventory menu. */
    static int inventoryMenuSlot(int invIndex) {
        return invIndex < 9 ? invIndex + 36 : invIndex;
    }

    /** Whether no container is open, so clicks go to the player's inventory menu. */
    static boolean inventoryMenuOpen(Player player) {
        return player.containerMenu == player.inventoryMenu;
    }

    /**
     * Puts a stack matching {@code want} on the hotbar and returns its hotbar index, or -1 if there is
     * none (or it is in the main inventory while a container is open). Prefers an empty hotbar slot,
     * then one holding no tool, so the pickaxe Baritone mines with is not shuffled away.
     */
    static int toHotbar(IPlayerContext ctx, Predicate<ItemStack> want) {
        Player player = ctx.player();
        List<ItemStack> main = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < 9; i++) {
            if (!main.get(i).isEmpty() && want.test(main.get(i))) return i;
        }
        int from = -1;
        for (int i = 9; i < InventoryReader.MAIN_SIZE; i++) {
            if (!main.get(i).isEmpty() && want.test(main.get(i))) {
                from = i;
                break;
            }
        }
        if (from < 0 || !inventoryMenuOpen(player)) return -1;
        int dest = -1;
        for (int i = 0; i < 9 && dest < 0; i++) {
            if (main.get(i).isEmpty()) dest = i;
        }
        for (int i = 8; i >= 0 && dest < 0; i--) {
            if (i != player.getInventory().getSelectedSlot() && !main.get(i).has(DataComponents.TOOL)) dest = i;
        }
        if (dest < 0) dest = 7;
        ctx.playerController().windowClick(player.inventoryMenu.containerId, from, dest, ContainerInput.SWAP, player);
        return dest;
    }

    /** Throws the whole stack at main-inventory index {@code invIndex}. Only with no container open. */
    static boolean throwStack(IPlayerContext ctx, int invIndex) {
        Player player = ctx.player();
        if (!inventoryMenuOpen(player)) return false;
        ctx.playerController().windowClick(player.inventoryMenu.containerId, inventoryMenuSlot(invIndex), 1, ContainerInput.THROW, player);
        return true;
    }

    /** Menu slot showing main-inventory index {@code invIndex} in {@code menu}, or -1. */
    static int menuSlotOf(AbstractContainerMenu menu, Inventory inventory, int invIndex) {
        for (Slot slot : menu.slots) {
            if (slot.container == inventory && slot.getContainerSlot() == invIndex) return slot.index;
        }
        return -1;
    }

    static void click(IPlayerContext ctx, int containerId, ClickPlan.Click click) {
        ctx.playerController().windowClick(containerId, click.slot(), click.button(), ContainerInput.PICKUP, ctx.player());
    }

    static void quickMove(IPlayerContext ctx, int containerId, int slot) {
        ctx.playerController().windowClick(containerId, slot, 0, ContainerInput.QUICK_MOVE, ctx.player());
    }
}
