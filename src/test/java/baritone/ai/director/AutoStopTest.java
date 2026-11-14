package baritone.ai.director;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** When a run hands control back by itself. */
final class AutoStopTest {

    private static AutoStop.Vitals fine() {
        return new AutoStop.Vitals(20, true, null, Double.POSITIVE_INFINITY, 0);
    }

    @Test
    void aHealthyUndisturbedRunCarriesOn() {
        assertNull(AutoStop.check(fine()));
        assertNull(AutoStop.check(new AutoStop.Vitals(4, true, null, Double.POSITIVE_INFINITY, 0)), "hurt but has food");
        assertNull(AutoStop.check(new AutoStop.Vitals(20, false, null, Double.POSITIVE_INFINITY, 0)), "no food but healthy");
        assertNull(AutoStop.check(new AutoStop.Vitals(20, true, "Alex", 12.5, 0)), "a stranger further than 12 blocks");
        assertNull(AutoStop.check(new AutoStop.Vitals(20, true, null, Double.POSITIVE_INFINITY, 1000)), "a tap of a key");
    }

    @Test
    void strangersLowHealthWithoutFoodAndThePlayerTakingOverStopIt() {
        assertEquals("Alex came within 12 blocks", AutoStop.check(new AutoStop.Vitals(20, true, "Alex", 11.9, 0)));
        assertEquals("Low health (6/20) and no food", AutoStop.check(new AutoStop.Vitals(6, false, null, Double.POSITIVE_INFINITY, 0)));
        assertEquals("You took control", AutoStop.check(new AutoStop.Vitals(20, true, null, Double.POSITIVE_INFINITY, 1001)));
    }
}
