package dihclient.modules;

import org.junit.jupiter.api.Test;

import static dihclient.modules.LogoffSpotModule.Verdict.LEFT_RANGE;
import static dihclient.modules.LogoffSpotModule.Verdict.LOGGED_OFF;
import static dihclient.modules.LogoffSpotModule.Verdict.WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LogoffSpotModuleTest {
    @Test
    void vanishingAndLeavingTheTabListIsALogoff() {
        assertEquals(LOGGED_OFF, LogoffSpotModule.verdict(false, true, true, 0));
    }

    @Test
    void stillInTheTabListWaitsForTheRemovalThenCountsAsOutOfRange() {
        assertEquals(WAIT, LogoffSpotModule.verdict(false, true, false, 0));
        assertEquals(WAIT, LogoffSpotModule.verdict(false, true, false, LogoffSpotModule.TAB_GRACE_TICKS - 1));
        assertEquals(LEFT_RANGE, LogoffSpotModule.verdict(false, true, false, LogoffSpotModule.TAB_GRACE_TICKS));
    }

    @Test
    void aPlayerNeverInTheTabListIsNotTracked() {
        assertEquals(LEFT_RANGE, LogoffSpotModule.verdict(false, false, true, 0));
    }

    @Test
    void anyRemovalModeMarksEveryDisappearance() {
        assertEquals(LOGGED_OFF, LogoffSpotModule.verdict(true, false, false, 0));
    }

    @Test
    void ageReadsInTheLargestWholeUnit() {
        assertEquals("12s", LogoffSpotModule.ago(12_500L));
        assertEquals("5m", LogoffSpotModule.ago(5 * 60_000L + 30_000L));
        assertEquals("2h", LogoffSpotModule.ago(2 * 3_600_000L + 1L));
    }
}
