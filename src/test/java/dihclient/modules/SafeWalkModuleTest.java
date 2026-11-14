package dihclient.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SafeWalkModuleTest {

    @Test
    void theSneakRidesOutAProbeFlickerInsteadOfTogglingEveryOtherTick() {

        assertTrue(SafeWalkModule.holdsThroughFlicker(true, 1, 3));
        assertTrue(SafeWalkModule.holdsThroughFlicker(true, 2, 3));

        assertFalse(SafeWalkModule.holdsThroughFlicker(true, 3, 3));
        assertFalse(SafeWalkModule.holdsThroughFlicker(true, 9, 3));

        assertFalse(SafeWalkModule.holdsThroughFlicker(false, 0, 3));
        assertFalse(SafeWalkModule.holdsThroughFlicker(false, 1, 3));
    }

    @Test
    void theProbeNeverReachesLessFarThanOneTickOfTravel() {

        assertTrue(SafeWalkModule.MIN_EDGE_DISTANCE > 0.116D);

        assertEquals(0.15D, SafeWalkModule.safeEdgeDistance(0.05D), 1.0E-9D);
        assertEquals(0.15D, SafeWalkModule.safeEdgeDistance(0.10D), 1.0E-9D);
        assertEquals(0.15D, SafeWalkModule.safeEdgeDistance(0.15D), 1.0E-9D);

        assertEquals(0.30D, SafeWalkModule.safeEdgeDistance(0.30D), 1.0E-9D);
        assertEquals(1.50D, SafeWalkModule.safeEdgeDistance(1.50D), 1.0E-9D);
    }

    @Test
    void minDropDeepensTheProbeOneBlockPerStep() {

        assertEquals(0.55D, SafeWalkModule.dropProbeDepth(1), 1.0E-9D);
        assertEquals(0.55D, SafeWalkModule.dropProbeDepth(0), 1.0E-9D);

        assertEquals(1.55D, SafeWalkModule.dropProbeDepth(2), 1.0E-9D);
        assertEquals(5.55D, SafeWalkModule.dropProbeDepth(6), 1.0E-9D);
    }
}
