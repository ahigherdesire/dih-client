package dihclient.modules;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihVelocitySpoofTest {
    private static final double WALK = DihVelocitySpoof.WALK_REACH;

    @Test
    void eachPacketOfATickReachesTheSquareRootOfItsCountFurther() {
        assertEquals(10.0 - DihVelocitySpoof.MARGIN, DihVelocitySpoof.reach(1, WALK), 1.0E-9);
        assertEquals(20.0 - DihVelocitySpoof.MARGIN, DihVelocitySpoof.reach(4, WALK), 1.0E-9);
        // Past five packets the server stops counting more.
        assertEquals(DihVelocitySpoof.reach(5, WALK), DihVelocitySpoof.reach(9, WALK), 1.0E-9);
    }

    @Test
    void aReturningBurstUnderTenBlocksNeedsNoPads() {
        DihVelocitySpoof.Plan plan = DihVelocitySpoof.returning(9.5, 50.0, WALK);
        assertEquals(0, plan.pads());
        assertEquals(9.5, plan.back(), 1.0E-9);
        assertEquals(0.0, plan.forward(), 1.0E-9);
    }

    @Test
    void aLongerReturningBurstPadsUntilTheStepBackFits() {
        DihVelocitySpoof.Plan plan = DihVelocitySpoof.returning(15.0, 50.0, WALK);
        assertEquals(2, plan.pads());
        assertTrue(plan.back() <= DihVelocitySpoof.reach(plan.pads() + 1, WALK));
        assertEquals(15.0, plan.boost(), 1.0E-9);
    }

    @Test
    void aReturningBurstStopsAtAWallBehind() {
        assertEquals(3.0, DihVelocitySpoof.returning(9.5, 3.0, WALK).boost(), 1.0E-9);
    }

    @Test
    void aShotSplitsItsBoostBetweenTheStepBackAndTheStepAhead() {
        DihVelocitySpoof.Plan plan = DihVelocitySpoof.through(19.0, 50.0, 50.0, WALK);
        assertEquals(0, plan.pads());
        assertEquals(9.5, plan.back(), 1.0E-9);
        assertEquals(9.5, plan.forward(), 1.0E-9);
    }

    @Test
    void theLargestShotUsesEveryPacketTheServerCounts() {
        DihVelocitySpoof.Plan plan = DihVelocitySpoof.through(ArrowDamageModule.MAX_BOOST, 50.0, 50.0, WALK);
        assertEquals(3, plan.pads());
        assertEquals(ArrowDamageModule.MAX_BOOST, plan.boost(), 1.0E-9);
        assertTrue(plan.back() <= DihVelocitySpoof.reach(4, WALK) + 1.0E-9);
        assertTrue(plan.forward() <= DihVelocitySpoof.reach(5, WALK) + 1.0E-9);
    }

    @Test
    void everyStepOfAShotStaysInsideItsAllowance() {
        for (double boost = 1.0; boost <= ArrowDamageModule.MAX_BOOST; boost += 0.5) {
            DihVelocitySpoof.Plan plan = DihVelocitySpoof.through(boost, 50.0, 50.0, WALK);
            assertTrue(plan.back() <= DihVelocitySpoof.reach(plan.pads() + 1, WALK) + 1.0E-9, "back at " + boost);
            assertTrue(plan.forward() <= DihVelocitySpoof.reach(plan.pads() + 2, WALK) + 1.0E-9, "ahead at " + boost);
            assertEquals(boost, plan.boost(), 1.0E-9, "boost " + boost);
        }
    }

    @Test
    void aShotWithAWallAheadPutsMoreOfItsBoostBehind() {
        DihVelocitySpoof.Plan plan = DihVelocitySpoof.through(19.0, 50.0, 2.0, WALK);
        assertEquals(2.0, plan.forward(), 1.0E-9);
        assertEquals(17.0, plan.back(), 1.0E-9);
    }

    @Test
    void aBoxedInShotGivesWhatRoomThereIs() {
        DihVelocitySpoof.Plan plan = DihVelocitySpoof.through(19.0, 1.0, 1.5, WALK);
        assertEquals(2.5, plan.boost(), 1.0E-9);
    }

    @Test
    void glidingEarnsTheBiggerAllowance() {
        assertTrue(DihVelocitySpoof.returning(15.0, 50.0, DihVelocitySpoof.GLIDE_REACH).pads() == 0);
    }

    @Test
    void thePokeStepsAlongTheLevelFacing() {
        Vec3 south = SpearKillModule.flat(0.0F);
        assertEquals(0.0, south.x, 1.0E-9);
        assertEquals(1.0, south.z, 1.0E-9);
        Vec3 west = SpearKillModule.flat(90.0F);
        assertEquals(-1.0, west.x, 1.0E-9);
        assertEquals(0.0, west.z, 1.0E-9);
    }
}
