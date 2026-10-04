package dihclient.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatModulesTest {
    @Test
    void reachAddsOnlyWhatTheRangeLacks() {
        assertEquals(1.5D, ReachModule.bonus(3.0D, 4.5D), 1.0E-9D);
        assertEquals(0.0D, ReachModule.bonus(3.0D, 3.0D), 1.0E-9D);
    }

    @Test
    void reachNeverShortensARangeTheServerGrants() {
        // Creative players get a longer range from the server; a lower setting must not cut it.
        assertEquals(0.0D, ReachModule.bonus(5.0D, 3.5D), 1.0E-9D);
    }

    @Test
    void jumpModeHitsOnlyOnTheWayDown() {
        assertFalse(CriticalsModule.fallingForCrit(true, 0.0D, 0.0D), "on the ground");
        assertFalse(CriticalsModule.fallingForCrit(false, 0.0D, 0.42D), "still rising");
        assertFalse(CriticalsModule.fallingForCrit(false, 0.0D, -0.08D), "past the peak but no fall yet");
        assertTrue(CriticalsModule.fallingForCrit(false, 0.2D, -0.15D), "falling");
    }
}
