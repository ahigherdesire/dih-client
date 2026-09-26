package baritone.acquire.exec;

import baritone.acquire.exec.HealthPolicy.Need;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When to eat and when to fetch food. Health in half-hearts, emergency at 6 like the default setting. */
final class HealthPolicyTest {

    private static final int EMERGENCY = 6;
    private static final int HEAL = 12;

    private static Need need(float health, int food) {
        return HealthPolicy.need(health, 0, 20, food, EMERGENCY);
    }

    @Test
    void hurtEatsOnlyBelowTheRegenerationLine() {
        assertEquals(Need.EAT, need(15, 17), "hurt and below 18: no regeneration until topped up");
        assertEquals(Need.NONE, need(15, 18), "18 food regenerates on its own");
        assertEquals(Need.NONE, need(15, 20));
        assertEquals(Need.NONE, need(20, 17), "full health and not hungry");
    }

    @Test
    void hungryEatsWhateverTheHealth() {
        assertEquals(Need.EAT, need(20, 6));
        assertEquals(Need.EAT, need(20, 0));
        assertEquals(Need.NONE, need(20, 7));
    }

    @Test
    void emergencyAtOrBelowTheThresholdEvenOnAFullBar() {
        assertEquals(Need.EMERGENCY, need(6, 20));
        assertEquals(Need.EMERGENCY, need(1, 3));
        assertEquals(Need.EAT, need(7, 10));
        assertEquals(Need.NONE, need(7, 20));
        // A golden apple's absorption counts, so a second one isn't eaten straight after the first.
        assertEquals(Need.NONE, HealthPolicy.need(5, 4, 20, 20, EMERGENCY));
        assertEquals(Need.EAT, HealthPolicy.need(5, 4, 20, 12, EMERGENCY));
        // A lower max health (a mod, an attribute) is still "full".
        assertEquals(Need.NONE, HealthPolicy.need(6, 0, 6, 20, EMERGENCY));
    }

    @Test
    void deadNeedsNothing() {
        assertEquals(Need.NONE, need(0, 0));
    }

    @Test
    void foodDetourOnlyWithoutSafeFood() {
        assertTrue(HealthPolicy.wantsFood(12, 20, HEAL, false), "at the heal threshold, even on a full bar");
        assertTrue(HealthPolicy.wantsFood(20, 6, HEAL, false), "hungry");
        assertFalse(HealthPolicy.wantsFood(13, 7, HEAL, false));
        assertFalse(HealthPolicy.wantsFood(4, 2, HEAL, true), "food is held: eat it instead");
        assertFalse(HealthPolicy.wantsFood(0, 0, HEAL, false), "dead");
    }

    @Test
    void detourPointsFillTheBarWithASpareMeal() {
        assertEquals(20, HealthPolicy.detourPoints(0));
        assertEquals(20, HealthPolicy.detourPoints(8));
        assertEquals(16, HealthPolicy.detourPoints(12));
        assertEquals(10, HealthPolicy.detourPoints(18));
        assertEquals(10, HealthPolicy.detourPoints(20));
    }

    @Test
    void heartsReadNaturally() {
        assertEquals("6 hearts", HealthPolicy.hearts(12));
        assertEquals("2.5 hearts", HealthPolicy.hearts(5));
        assertEquals("1 heart", HealthPolicy.hearts(2));
        assertEquals("0.5 hearts", HealthPolicy.hearts(1.2F));
    }
}
