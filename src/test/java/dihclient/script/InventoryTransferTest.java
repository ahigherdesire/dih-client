package dihclient.script;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class InventoryTransferTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Unit tests do not load the world's item-component data pack.
        for (String item : java.util.List.of("dirt", "cobblestone", "raw_gold")) {
            net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.Identifier.withDefaultNamespace(item)).orElseThrow()
                .bindComponents(net.minecraft.core.component.DataComponents.COMMON_ITEM_COMPONENTS);
        }
    }

    @Test
    void partialWithdrawalCountsOnlyItemsThatMoved() {
        Inventory inventory = fullInventory();
        inventory.setItem(0, new ItemStack(Items.RAW_GOLD, 63));
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.RAW_GOLD, 64));
        ChestMenu menu = ChestMenu.threeRows(1, inventory, chest);

        assertEquals(1, transfer(menu, "raw_gold", false));
        assertEquals(63, chest.getItem(0).getCount());
        assertEquals(64, inventory.getItem(0).getCount());
    }

    @Test
    void depositSkipsBlockedStackAndMovesLaterStack() {
        Inventory inventory = new Inventory(null, new EntityEquipment());
        inventory.setItem(9, new ItemStack(Items.DIRT, 64));
        inventory.setItem(10, new ItemStack(Items.COBBLESTONE, 16));
        SimpleContainer chest = new SimpleContainer(27);
        for (int i = 0; i < 27; i++) chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 48));
        ChestMenu menu = ChestMenu.threeRows(1, inventory, chest);

        assertEquals(16, transfer(menu, "*", true));
        assertEquals(64, inventory.getItem(9).getCount());
        assertEquals(0, inventory.getItem(10).getCount());
        assertEquals(64, chest.getItem(0).getCount());
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> assertEquals(0, transfer(menu, "*", true)));
    }

    @Test
    void withdrawalSkipsBlockedStackAndMovesLaterStack() {
        Inventory inventory = fullInventory();
        inventory.setItem(0, new ItemStack(Items.RAW_GOLD, 63));
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        chest.setItem(1, new ItemStack(Items.RAW_GOLD, 1));
        ChestMenu menu = ChestMenu.threeRows(1, inventory, chest);

        assertEquals(1, transfer(menu, "*", false));
        assertEquals(64, chest.getItem(0).getCount());
        assertEquals(0, chest.getItem(1).getCount());
        assertEquals(64, inventory.getItem(0).getCount());
    }

    private static Inventory fullInventory() {
        Inventory inventory = new Inventory(null, new EntityEquipment());
        for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.DIRT, 64));
        return inventory;
    }

    private static int transfer(ChestMenu menu, String item, boolean toContainer) {
        return GameLibrary.transferNext(menu, item, toContainer, slot -> {
            menu.quickMoveStack(null, slot);
            return true;
        });
    }
}
