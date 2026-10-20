package dihclient.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PearlChargePlanTest {
    @Test
    void pearlFirstThrowsThePearlThenTheChargeAfterTheDelay() {
        PearlChargePlan plan = new PearlChargePlan(true, 1);
        assertEquals(0, plan.pearlTick());
        assertEquals(1, plan.chargeTick());
        assertEquals(1, plan.finalTick());
        assertTrue(plan.fires(PearlChargePlan.Action.PEARL, 0));
        assertFalse(plan.fires(PearlChargePlan.Action.CHARGE, 0));
        assertTrue(plan.fires(PearlChargePlan.Action.CHARGE, 1));
    }

    @Test
    void chargeFirstPopsTheChargeThenThrowsThePearlAfterTheDelay() {
        PearlChargePlan plan = new PearlChargePlan(false, 2);
        assertEquals(0, plan.chargeTick());
        assertEquals(2, plan.pearlTick());
        assertEquals(2, plan.finalTick());
        assertTrue(plan.fires(PearlChargePlan.Action.CHARGE, 0));
        assertTrue(plan.fires(PearlChargePlan.Action.PEARL, 2));
        assertFalse(plan.fires(PearlChargePlan.Action.PEARL, 1));
    }

    @Test
    void zeroDelayFiresBothOnTheSameTick() {
        PearlChargePlan plan = new PearlChargePlan(true, 0);
        assertEquals(0, plan.pearlTick());
        assertEquals(0, plan.chargeTick());
        assertEquals(0, plan.finalTick());
        assertTrue(plan.fires(PearlChargePlan.Action.PEARL, 0));
        assertTrue(plan.fires(PearlChargePlan.Action.CHARGE, 0));
    }

    @Test
    void negativeDelayIsClampedToZero() {
        PearlChargePlan plan = new PearlChargePlan(true, -5);
        assertEquals(0, plan.chargeTick());
        assertTrue(plan.fires(PearlChargePlan.Action.CHARGE, 0));
    }
}
