package baritone.cache;

import dihclient.util.DihStorageSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageSearchTest {
    @Test
    void highlightsOnlyNearbyResultsInThisWorldAndDimensionNearestFirst() {
        var far = hit("mp:a", "minecraft:overworld", 20);
        var near = hit("mp:a", "minecraft:overworld", 2);
        var otherDim = hit("mp:a", "minecraft:the_nether", 1);
        var otherWorld = hit("mp:b", "minecraft:overworld", 1);
        var outside = hit("mp:a", "minecraft:overworld", 200);
        var results = DihStorageSearch.select(List.of(far, otherDim, outside, otherWorld, near),
            "mp:a", "minecraft:overworld", Vec3.ZERO, 128, 64);
        assertEquals(List.of(near, far), results);
    }

    @Test
    void capsHighlightsAfterSortingAndDoesNotModifyTheDatabaseResults() {
        var far = hit("mp:a", "minecraft:overworld", 20);
        var near = hit("mp:a", "minecraft:overworld", 2);
        var source = List.of(far, near);
        assertEquals(List.of(near), DihStorageSearch.select(source, "mp:a", "minecraft:overworld", Vec3.ZERO, 128, 1));
        assertEquals(List.of(far, near), source);
        assertTrue(DihStorageSearch.select(source, "mp:a", "minecraft:overworld", Vec3.ZERO, 128, 0).isEmpty());
    }

    @Test
    void clearingHighlightsPreservesMemoryAndChangingWorldDropsTheQuery() {
        var state = new DihStorageSearch();
        Object level = new Object();
        state.start(level, "iron", null);
        assertEquals("iron", state.query(level));
        state.clear();
        assertNull(state.query(level));
        state.start(level, "iron", null);
        assertNull(state.query(new Object()));
        assertNull(state.query(level)); // does not come back when returning to that level
        state.start(level, "iron", null);
        assertNull(state.query(null));
    }

    @Test
    void selectingOneChestKeepsOnlyThatResultAndRejectsOtherScopes() {
        var selected = hit("mp:a", "minecraft:overworld", 2);
        var other = hit("mp:a", "minecraft:overworld", 20);
        var state = new DihStorageSearch();
        Object level = new Object();
        state.start(level, "iron", other.record.pos);
        assertEquals(other.record.pos, state.selected(level));
        assertEquals(List.of(other), DihStorageSearch.select(List.of(selected, other),
            "mp:a", "minecraft:overworld", Vec3.ZERO, 128, 1, state.selected(level)));
        assertNull(state.selected(new Object()));
    }

    @Test
    void lastSeenAgesDescribeSnapshotsAndHandleUnknownOrFutureTimes() {
        assertEquals("unknown", DihStorageSearch.age(0, 120_000));
        assertEquals("just now", DihStorageSearch.age(130_000, 120_000));
        assertEquals("15s ago", DihStorageSearch.age(105_000, 120_000));
        assertEquals("2m ago", DihStorageSearch.age(1_000, 121_000));
        assertEquals("2h ago", DihStorageSearch.age(1_000, 7_201_000));
        assertEquals("2d ago", DihStorageSearch.age(1_000, 172_801_000));
    }

    private static ChestMemory.SearchResult hit(String world, String dimension, int x) {
        var items = List.of(new ChestMemory.SlotItem("minecraft:iron_ingot", 32, "Iron Ingot"));
        return new ChestMemory.SearchResult(new ChestMemory.ChestRecord(new BlockPos(x, 0, 0),
            dimension, world, items, 1_000), items);
    }
}
