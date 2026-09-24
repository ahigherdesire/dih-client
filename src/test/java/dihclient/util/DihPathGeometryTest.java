package dihclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihPathGeometryTest {
    @Test
    void shoulderCannotClipAnObstacleWhenCenterAvoidsItsCell() {

        assertFalse(DihPathGeometry.crossesBox(0.5, 0.5, 1.5, 2.0, 1, 0, 2, 1));
        assertTrue(DihPathGeometry.crossesBox(0.5, 0.5, 1.5, 2.0, 0.7, -0.3, 2.3, 1.3));
    }

    @Test
    void corridorClearanceIncludesTheWholeBodyInBothDirections() {
        assertFalse(DihPathGeometry.crossesBox(0.5, -3, 0.5, 3, -1.3, -2.3, 0.3, 2.3));
        assertTrue(DihPathGeometry.crossesBox(0.2, -3, 0.2, 3, -1.3, -2.3, 0.3, 2.3));
        assertTrue(DihPathGeometry.crossesBox(0.2, 3, 0.2, -3, -1.3, -2.3, 0.3, 2.3));
    }

    @Test
    void stationaryBodyAndBoundaryGrazingAreHandledWithoutDivisionByZero() {
        assertTrue(DihPathGeometry.crossesBox(0, 0, 0, 0, -1, -1, 1, 1));
        assertFalse(DihPathGeometry.crossesBox(1, -2, 1, 2, -1, -1, 1, 1));
        assertFalse(DihPathGeometry.crossesBox(2, 2, 2, 2, -1, -1, 1, 1));
    }

    @Test
    void smoothingCannotTurnAPartialFloorClimbIntoAJumpOrTramplingDrop() {
        assertTrue(DihPathGeometry.safeRise(0.9375, 1.5, 0.6));
        assertFalse(DihPathGeometry.safeRise(0.5, 1.5, 0.6));
        assertTrue(DihPathGeometry.safeRise(1.0, 0.9375, 0.6));
        assertFalse(DihPathGeometry.safeRise(1.5, 0.9375, 0.6));
        assertFalse(DihPathGeometry.safeRise(1.0, 0.5, 0.6));
    }

    @Test
    void heuristicIsConsistentForFlatEdgesDropsAndCheapTwoCellGaps() {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                int h = DihPathGeometry.lowerBound(x, z, 0, 0);
                for (int sign : new int[] {-1, 1}) {

                    assertTrue(h <= 10 + DihPathGeometry.lowerBound(x + sign, z, 0, 0));
                    assertTrue(h <= 16 + DihPathGeometry.lowerBound(x, z + sign, 0, 0));
                    assertTrue(h <= 18 + DihPathGeometry.lowerBound(x + sign * 2, z, 0, 0));
                    assertTrue(h <= 18 + DihPathGeometry.lowerBound(x, z + sign * 2, 0, 0));
                }
            }
        }
        assertEquals(0, DihPathGeometry.lowerBound(4, -7, 4, -7));
    }
}
