package dihclient.script;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryCapacityTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bindStackSize("dirt", 64);
        bindStackSize("ender_pearl", 16);
        bindStackSize("diamond_pickaxe", 1);
    }

    private static void bindStackSize(String item, int size) {
        // Unit tests do not load the world's item-component data pack.
        BuiltInRegistries.ITEM.get(Identifier.withDefaultNamespace(item)).orElseThrow()
            .bindComponents(DataComponentMap.builder().addAll(DataComponents.COMMON_ITEM_COMPONENTS)
                .set(DataComponents.MAX_STACK_SIZE, size).build());
    }

    @Test
    void emptySlotsRespectTheRequestedItemsStackLimit() {
        Inventory inventory = oneEmptySlot();
        assertEquals(64, GameLibrary.roomFor(inventory, "dirt"));
        assertEquals(16, GameLibrary.roomFor(inventory, "ender_pearl"));
        assertEquals(1, GameLibrary.roomFor(inventory, "diamond_pickaxe"));
    }

    @Test
    void partialStacksAndEmptySlotsBothCount() {
        Inventory inventory = oneEmptySlot();
        inventory.setItem(0, new ItemStack(Items.ENDER_PEARL, 9));
        assertEquals(23, GameLibrary.roomFor(inventory, "ender_pearl"));
        assertEquals(23, GameLibrary.roomFor(inventory, "minecraft:ender_pearl"));
    }

    @Test
    void noItemCountsEmptySlotsInsteadOfItems() {
        assertEquals(1, GameLibrary.roomFor(oneEmptySlot(), null));
        assertEquals(36, GameLibrary.roomFor(new Inventory(null, new EntityEquipment()), null));
    }

    @Test
    void fullInventoryHasNoRoomForAnotherTool() {
        Inventory inventory = oneEmptySlot();
        inventory.setItem(35, new ItemStack(Items.DIAMOND_PICKAXE));
        assertEquals(0, GameLibrary.roomFor(inventory, "diamond_pickaxe"));
    }

    @Test
    void wildcardCapacityDoesNotDependOnTheFirstItemsStackLimit() {
        Inventory inventory = oneEmptySlot();
        inventory.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
        assertEquals(64, GameLibrary.roomFor(inventory, "*"));
    }

    private static Inventory oneEmptySlot() {
        Inventory inventory = new Inventory(null, new EntityEquipment());
        for (int i = 0; i < 35; i++) inventory.setItem(i, new ItemStack(Items.DIRT, 64));
        return inventory;
    }
}
