package dihclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DihFarmActionWatchdogTest {
    @Test
    void protectedCellEventuallyYieldsEvenWhenOtherCellsAreVisited() {
        DihFarmActionWatchdog<String> watch = new DihFarmActionWatchdog<>();
        for (int tick = 0; tick < 40; tick++) {
            assertTrue(watch.allow(1, "mature", tick, 40));
            assertTrue(watch.allow(2, "different", tick, 80));
        }
        assertFalse(watch.allow(1, "mature", 40, 40));
    }

    @Test
    void retriesInsideOneTickDoNotSpendTheBudgetTwice() {
        DihFarmActionWatchdog<String> watch = new DihFarmActionWatchdog<>();
        for (int retry = 0; retry < 100; retry++) assertTrue(watch.allow(1, "mature", 7, 1));
        assertFalse(watch.allow(1, "mature", 8, 1));
    }

    @Test
    void growthAndLaterRevisitGetFreshBudgets() {
        DihFarmActionWatchdog<String> watch = new DihFarmActionWatchdog<>();
        assertTrue(watch.allow(1, "age0", 0, 1));
        assertTrue(watch.allow(1, "age1", 1, 1));
        assertTrue(watch.allow(1, "age1", 30, 1));
        watch.clear();
        assertTrue(watch.allow(1, "age1", 31, 1));
    }

    @Test
    void clientTickWrapKeepsTheAttemptBudget() {
        var watch = new DihFarmActionWatchdog<String>();
        assertTrue(watch.allow(1, "crop", Integer.MAX_VALUE, 1));
        assertFalse(watch.allow(1, "crop", Integer.MIN_VALUE, 1));
    }
}
