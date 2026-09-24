package dihclient.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DihFarmReplantMemoryTest {
    @Test
    void aLongSweepRetainsMixedCropsPastTheOldSixteenCellLimit() {
        var memory = new DihFarmReplantMemory<String>(64);
        for (int i = 0; i < 48; i++) assertTrue(memory.remember(i, i % 2 == 0 ? "wheat" : "carrot"));
        assertEquals("wheat", memory.get(0));
        assertEquals("carrot", memory.get(1));
        assertEquals(48, memory.size());
    }

    @Test
    void fullQueuePreservesEveryUnfinishedCell() {
        DihFarmReplantMemory<String> memory = new DihFarmReplantMemory<>(2);
        assertTrue(memory.remember(1L, "wheat"));
        assertTrue(memory.remember(2L, "carrots"));
        assertFalse(memory.remember(3L, "potatoes"));
        assertEquals("wheat", memory.get(1L));
        assertEquals("carrots", memory.get(2L));
        assertNull(memory.get(3L));
    }

    @Test
    void repeatedHarvestDoesNotDuplicateOrReorderDebt() {
        DihFarmReplantMemory<String> memory = new DihFarmReplantMemory<>(2);
        memory.remember(1L, "wheat");
        memory.remember(2L, "carrots");
        assertTrue(memory.remember(1L, "beetroots"));
        assertEquals(2, memory.size());
        assertEquals(List.of("beetroots", "carrots"), List.copyOf(memory.values()));
    }

    @Test
    void completedCellFreesCapacityWithoutForgettingItsNeighbors() {
        DihFarmReplantMemory<String> memory = new DihFarmReplantMemory<>(2);
        memory.remember(Long.MIN_VALUE, "wheat");
        memory.remember(Long.MAX_VALUE, "carrots");
        memory.remove(Long.MIN_VALUE);
        assertTrue(memory.hasRoomFor(0L));
        assertTrue(memory.remember(0L, "potatoes"));
        assertEquals("carrots", memory.get(Long.MAX_VALUE));
        memory.clear();
        assertTrue(memory.isEmpty());
    }
}
