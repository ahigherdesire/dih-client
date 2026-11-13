package dihclient.util;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DihRegionHighlightTest {
    @Test
    void normalizesEachAxisAndIncludesBothCornerBlocks() {
        AABB box = DihRegionHighlight.bounds(10, -5, 30, -2, 7, 4);
        assertEquals(-2.0, box.minX);
        assertEquals(-5.0, box.minY);
        assertEquals(4.0, box.minZ);
        assertEquals(11.0, box.maxX);
        assertEquals(8.0, box.maxY);
        assertEquals(31.0, box.maxZ);
    }

    @Test
    void identicalCornersHighlightOneWholeBlock() {
        AABB box = DihRegionHighlight.bounds(-3, 64, 5, -3, 64, 5);
        assertEquals(1.0, box.getXsize());
        assertEquals(1.0, box.getYsize());
        assertEquals(1.0, box.getZsize());
    }

    @Test
    void inclusiveMaximumDoesNotOverflowIntegerCoordinates() {
        AABB box = DihRegionHighlight.bounds(0, 0, 0,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(2147483648.0, box.maxX);
        assertEquals(2147483648.0, box.maxY);
        assertEquals(2147483648.0, box.maxZ);
    }
}
