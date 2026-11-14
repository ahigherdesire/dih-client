package dihclient.modules;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpearKillModuleTest {
    @Test
    void aFarTargetIsRushedAtFullSpeedOnTheFlat() {
        Vec3 rush = SpearKillModule.rush(new Vec3(0, 64, 0), new Vec3(10, 70, 0), 3.0, 1.5);
        assertEquals(3.0, rush.x, 1.0E-9);
        assertEquals(0.0, rush.y, 1.0E-9);
        assertEquals(0.0, rush.z, 1.0E-9);
    }

    @Test
    void theLastStepStopsShortOfTheTarget() {
        Vec3 rush = SpearKillModule.rush(new Vec3(0, 64, 0), new Vec3(3, 64, 4), 9.0, 1.5);
        assertEquals(3.5, Math.hypot(rush.x, rush.z), 1.0E-9);
        assertEquals(0.6 * 3.5, rush.x, 1.0E-9);
        assertEquals(0.8 * 3.5, rush.z, 1.0E-9);
    }

    @Test
    void aTargetAlreadyInReachIsNotRushed() {
        assertSame(Vec3.ZERO, SpearKillModule.rush(new Vec3(0, 64, 0), new Vec3(1, 64, 0), 3.0, 1.5));
        assertSame(Vec3.ZERO, SpearKillModule.rush(new Vec3(0, 64, 0), new Vec3(0, 64, 0), 3.0, 1.5));
    }

    @Test
    void theChargeDamagesOnlyBetweenItsDelayAndItsDuration() {
        assertFalse(SpearKillModule.damaging(9, 10, 100));
        assertTrue(SpearKillModule.damaging(10, 10, 100));
        assertTrue(SpearKillModule.damaging(110, 10, 100));
        assertFalse(SpearKillModule.damaging(111, 10, 100));
    }

    @Test
    void aWeaponWhoseChargeDealsNoDamageIsNeverRushed() {
        assertFalse(SpearKillModule.damaging(20, 0, -1));
    }
}
