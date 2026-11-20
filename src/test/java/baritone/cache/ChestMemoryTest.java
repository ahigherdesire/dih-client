package baritone.cache;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class ChestMemoryTest {
    private static final BlockPos POS = new BlockPos(2, 64, 3);
    private static final String DIM = "minecraft:overworld";
    private static final List<ChestMemory.SlotItem> ITEMS =
        List.of(new ChestMemory.SlotItem("minecraft:iron_ingot", 32, "Iron Ingot"));

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void emptySnapshotRemovesPreviousItemsWithoutTouchingOtherWorlds() {
        var store = new ChestMemory.Store();
        store.put(record("mp:a", DIM, POS, ITEMS));
        store.put(record("mp:b", DIM, POS, ITEMS));
        store.put(record("mp:a", DIM, POS, List.of()));
        assertTrue(store.search("mp:a", "iron").isEmpty());
        assertEquals(32, store.search("mp:b", "iron").getFirst().matchedCount());
    }

    @Test
    void searchesIdsAndNamesWithSpacesAndIndependentOfSystemLocale() {
        var store = new ChestMemory.Store();
        store.put(record("mp:a", DIM, POS, ITEMS));
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(1, store.search("mp:a", "  IRON INGOT  ").size());
            assertEquals(1, store.search("mp:a", "minecraft:iron_ingot").size());
            assertTrue(store.search("mp:a", " ").isEmpty());
            assertTrue(store.search("mp:b", "iron").isEmpty());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void doubleChestSnapshotReplacesRecordsForBothHalves() {
        var store = new ChestMemory.Store();
        BlockPos partner = POS.east();
        store.put(record("mp:a", DIM, partner, ITEMS));
        store.replace(record("mp:a", DIM, POS, ITEMS), partner);
        assertEquals(1, store.search("mp:a", "iron").size());
        store.replace(record("mp:a", DIM, POS, List.of()), partner);
        assertTrue(store.search("mp:a", "iron").isEmpty());
    }

    @Test
    void captureLivesUntilItsMatchingSyncedMenuClosesWithoutTimeExpiry() {
        Object level = new Object(), menu = new Object();
        var capture = new ChestMemory.Capture(level, menu, 7, "mp:a", DIM, POS, null);
        assertNull(capture.snapshot(level, menu, "mp:a", DIM, ITEMS, 1_000));
        capture.synced(6);
        assertNull(capture.snapshot(level, menu, "mp:a", DIM, ITEMS, 2_000));
        capture.synced(7);
        var result = capture.snapshot(level, menu, "mp:a", DIM, ITEMS, 120_000);
        assertNotNull(result); // a two-minute session is valid, unlike the old eight-second expiry
        assertEquals(120_000, result.timestamp);
        assertEquals(ITEMS, result.items);
    }

    @Test
    void captureCannotLeakAcrossMenuLevelServerOrDimensionChanges() {
        Object level = new Object(), menu = new Object();
        var capture = new ChestMemory.Capture(level, menu, 7, "mp:a", DIM, POS, null);
        capture.synced(7);
        assertNull(capture.snapshot(new Object(), menu, "mp:a", DIM, ITEMS, 10));
        assertNull(capture.snapshot(level, new Object(), "mp:a", DIM, ITEMS, 10));
        assertNull(capture.snapshot(level, menu, "mp:b", DIM, ITEMS, 10));
        assertNull(capture.snapshot(level, menu, "mp:a", "minecraft:the_nether", ITEMS, 10));
    }

    @Test
    void openingRequiresARecentInteractionInTheSameLevel() {
        Object level = new Object();
        var click = new ChestMemory.Interaction(level, POS, 1_000);
        assertEquals(POS, click.position(level, 2_000));
        assertNull(click.position(new Object(), 2_000));
        assertNull(click.position(level, 60_000));
        assertNull(click.position(level, 999));
        click.level().clear();
        assertNull(click.position(level, 2_000)); // an old interaction cannot retain a disconnected world
    }

    @Test
    void onlyPhysicalStorageWithTheExpectedMenuAndSizeCanBeRecorded() {
        assertEquals(27, ChestMemory.storageSlotCount(Blocks.CHEST.defaultBlockState(), MenuType.GENERIC_9x3));
        assertEquals(54, ChestMemory.storageSlotCount(Blocks.CHEST.defaultBlockState()
            .setValue(ChestBlock.TYPE, ChestType.LEFT), MenuType.GENERIC_9x6));
        assertEquals(27, ChestMemory.storageSlotCount(Blocks.BARREL.defaultBlockState(), MenuType.GENERIC_9x3));
        assertEquals(5, ChestMemory.storageSlotCount(Blocks.HOPPER.defaultBlockState(), MenuType.HOPPER));
        assertEquals(0, ChestMemory.storageSlotCount(Blocks.CHEST.defaultBlockState(), MenuType.HOPPER));
        assertEquals(0, ChestMemory.storageSlotCount(Blocks.CHEST.defaultBlockState(), MenuType.GENERIC_9x6));
        assertEquals(0, ChestMemory.storageSlotCount(Blocks.ENDER_CHEST.defaultBlockState(), MenuType.GENERIC_9x3));
        assertEquals(0, ChestMemory.storageSlotCount(Blocks.STONE.defaultBlockState(), MenuType.GENERIC_9x3));
        assertEquals(0, ChestMemory.storageSlotCount(Blocks.CRAFTING_TABLE.defaultBlockState(), MenuType.CRAFTING));
    }

    private static ChestMemory.ChestRecord record(String world, String dim, BlockPos pos,
                                                  List<ChestMemory.SlotItem> items) {
        return new ChestMemory.ChestRecord(pos, dim, world, items, 1_000);
    }
}
