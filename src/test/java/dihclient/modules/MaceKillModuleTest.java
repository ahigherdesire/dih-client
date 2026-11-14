package dihclient.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaceKillModuleTest {
    @Test
    void eachMovePacketInTheTickRaisesTheFallTheServerAllows() {
        assertEquals(10, MaceKillModule.allowedHeight(0));
        assertEquals(14, MaceKillModule.allowedHeight(1));
        assertEquals(20, MaceKillModule.allowedHeight(3));
        assertEquals(22, MaceKillModule.allowedHeight(4));
    }

    @Test
    void pastFivePacketsTheServerCountsFromOneAgainSoMoreSpamGainsNothing() {
        assertEquals(22, MaceKillModule.allowedHeight(9));
    }

    @Test
    void theLiftStopsBelowTheFirstBlockOverhead() {
        assertEquals(6, MaceKillModule.clearHeight(22, up -> up <= 6));
        assertEquals(0, MaceKillModule.clearHeight(22, up -> false));
    }

    @Test
    void theLiftStopsAtTheCapWithClearSky() {
        assertEquals(22, MaceKillModule.clearHeight(22, up -> true));
    }

    @Test
    void aGapAboveARoofIsNotReached() {
        assertEquals(3, MaceKillModule.clearHeight(22, up -> up <= 3 || up >= 8));
    }
}
