package dihclient.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaceFallTest {
    @Test
    void measuresAPlainDropFromItsTop() {
        MaceFall fall = new MaceFall();
        assertTrue(fall.update(80.0D, 0.0D, true));
        assertFalse(fall.update(79.0D, -0.5D, false));
        assertFalse(fall.update(76.0D, -1.0D, false));
        assertEquals(4.0D, fall.distance(76.0D, 4.0D), 1.0E-9);
    }

    @Test
    void windBurstLaunchStartsANewDropAlthoughTheClientFallDistanceKeepsGrowing() {
        MaceFall fall = new MaceFall();
        fall.update(90.0D, 0.0D, true);
        fall.update(80.0D, -1.5D, false);
        assertEquals(10.0D, fall.distance(80.0D, 10.0D), 1.0E-9);

        // The smash throws the player up. The client's fallDistance stays at 10 the whole way.
        assertTrue(fall.update(81.0D, 1.2D, false));
        assertTrue(fall.update(86.0D, 0.4D, false));
        assertEquals(0.0D, fall.distance(86.0D, 10.0D), 1.0E-9);

        // Coming back down: only the new drop counts, so the next smash arms after real falling.
        assertFalse(fall.update(85.5D, -0.1D, false));
        assertEquals(0.5D, fall.distance(85.5D, 10.5D), 1.0E-9);
        assertFalse(fall.update(83.0D, -0.9D, false));
        assertEquals(3.0D, fall.distance(83.0D, 13.0D), 1.0E-9);
    }

    @Test
    void neverReportsMoreThanTheClientCounted() {
        MaceFall fall = new MaceFall();
        fall.update(70.0D, 0.0D, true);
        fall.update(64.0D, -1.0D, false);
        // Fell through water part of the way: the client counted less.
        assertEquals(2.0D, fall.distance(64.0D, 2.0D), 1.0E-9);
    }

    @Test
    void resetForgetsTheDrop() {
        MaceFall fall = new MaceFall();
        fall.update(70.0D, 0.0D, true);
        fall.update(60.0D, -1.0D, false);
        fall.reset();
        assertEquals(0.0D, fall.distance(60.0D, 10.0D), 1.0E-9);
        assertTrue(fall.update(60.0D, -1.0D, false));
    }
}
