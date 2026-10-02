package baritone.combat;

import baritone.combat.CombatTactics.Foe;
import baritone.combat.CombatTactics.Move;
import baritone.combat.CombatTactics.Situation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class CombatTacticsTest {

    /** A zombie in reach, cooldown full, standing on flat ground with a sword, shield and bow. */
    private static Situation.Builder zombie() {
        return Situation.builder(Foe.MELEE).distance(2.5).inReach(true).lineOfSight(true).cooldown(1)
                .onGround(true).canJump(true).hasShield(true).hasBow(true).clearRun(true);
    }

    @Test
    void aReadyHitOnTheGroundJumpsForACritical() {
        assertEquals(Move.JUMP, CombatTactics.decide(zombie().build()));
    }

    @Test
    void theHitLandsOnTheWayDown() {
        assertEquals(Move.STRIKE, CombatTactics.decide(zombie().onGround(false).falling(true).build()));
    }

    @Test
    void risingFromTheJumpWaits() {
        assertEquals(Move.WAIT, CombatTactics.decide(zombie().onGround(false).falling(false).build()));
    }

    @Test
    void withNoRoomToJumpItHitsFlat() {
        assertEquals(Move.STRIKE, CombatTactics.decide(zombie().canJump(false).build()));
        assertEquals(Move.STRIKE, CombatTactics.decide(zombie().inWater(true).build()));
    }

    @Test
    void whileTheSwordRechargesTheShieldIsUpAgainstMelee() {
        assertEquals(Move.SHIELD, CombatTactics.decide(zombie().cooldown(0.4f).build()));
        assertEquals(Move.WAIT, CombatTactics.decide(zombie().cooldown(0.4f).hasShield(false).build()));
    }

    @Test
    void outOfReachItClosesIn() {
        assertEquals(Move.CHARGE, CombatTactics.decide(zombie().inReach(false).distance(5).build()));
        assertEquals(Move.APPROACH, CombatTactics.decide(zombie().inReach(false).distance(5).clearRun(false).build()));
    }

    @Test
    void aHissingCreeperIsLeftAlone() {
        Situation.Builder creeper = zombie().foe(Foe.CREEPER).foeCharging(true);
        assertEquals(Move.BACK_OFF, CombatTactics.decide(creeper.distance(2.5).build()));
        assertEquals(Move.BACK_OFF, CombatTactics.decide(creeper.distance(4.5).inReach(false).build()));
    }

    @Test
    void aCreeperIsHitWithoutJumpingThenBackedOffFrom() {
        Situation.Builder creeper = zombie().foe(Foe.CREEPER);
        assertEquals(Move.STRIKE, CombatTactics.decide(creeper.build()), "a sprint hit knocks it back; no jump");
        assertEquals(Move.BACK_OFF, CombatTactics.decide(creeper.cooldown(0.3f).build()));
    }

    @Test
    void aCreeperAtRangeIsShot() {
        Situation.Builder creeper = zombie().foe(Foe.CREEPER).inReach(false).distance(10);
        assertEquals(Move.DRAW, CombatTactics.decide(creeper.build()));
        assertEquals(Move.LOOSE, CombatTactics.decide(creeper.drawTicks(20).aimSolved(true).build()));
        assertEquals(Move.DRAW, CombatTactics.decide(creeper.drawTicks(20).aimSolved(false).build()), "no shot yet: hold");
        assertEquals(Move.CHARGE, CombatTactics.decide(creeper.hasBow(false).drawTicks(0).build()));
    }

    @Test
    void aSkeletonIsChargedButTheShieldGoesUpWhileItDraws() {
        Situation.Builder skeleton = zombie().foe(Foe.RANGED).inReach(false).distance(10);
        assertEquals(Move.CHARGE, CombatTactics.decide(skeleton.build()));
        assertEquals(Move.SHIELD_APPROACH, CombatTactics.decide(skeleton.foeCharging(true).build()));
        assertEquals(Move.CHARGE, CombatTactics.decide(skeleton.foeCharging(true).hasShield(false).build()));
        assertEquals(Move.JUMP, CombatTactics.decide(skeleton.inReach(true).distance(2).foeCharging(false).build()));
    }

    @Test
    void aHoveringBlazeIsShot() {
        Situation.Builder blaze = zombie().foe(Foe.BLAZE).inReach(false).distance(8).heightAbove(4);
        assertEquals(Move.DRAW, CombatTactics.decide(blaze.build()));
        assertEquals(Move.LOOSE, CombatTactics.decide(blaze.drawTicks(21).aimSolved(true).build()));
    }

    @Test
    void aChargingBlazeGetsTheShieldUnlessTheShotIsNearlyReady() {
        Situation.Builder blaze = zombie().foe(Foe.BLAZE).inReach(false).distance(8).heightAbove(4).foeCharging(true);
        assertEquals(Move.SHIELD, CombatTactics.decide(blaze.build()));
        assertEquals(Move.SHIELD, CombatTactics.decide(blaze.drawTicks(5).build()));
        assertEquals(Move.DRAW, CombatTactics.decide(blaze.drawTicks(15).build()), "committed to the shot");
    }

    @Test
    void aBlazeOutOfReachWithNoBowIsWaitedOutBehindTheShield() {
        Situation.Builder blaze = zombie().foe(Foe.BLAZE).inReach(false).distance(5).heightAbove(4).hasBow(false);
        assertEquals(Move.SHIELD, CombatTactics.decide(blaze.build()));
        assertEquals(Move.WAIT, CombatTactics.decide(blaze.hasShield(false).build()));
        assertEquals(Move.JUMP, CombatTactics.decide(blaze.inReach(true).distance(3).build()), "in reach: melee it");
    }

    @Test
    void aBlazeOnTheGroundIsFoughtInMelee() {
        Situation.Builder blaze = zombie().foe(Foe.BLAZE).inReach(false).distance(3.5).heightAbove(0.5);
        assertEquals(Move.CHARGE, CombatTactics.decide(blaze.build()));
    }

    @Test
    void aShotFromAnyMobGetsTheShieldUnlessTheBowIsNearlyDrawnOrACreeperHisses() {
        // A second blaze fires while the first is shot at: the shield, then the bow again.
        Situation.Builder blaze = zombie().foe(Foe.BLAZE).inReach(false).distance(8).heightAbove(4).aimSolved(true);
        assertEquals(Move.DRAW, CombatTactics.decide(blaze.build()));
        assertEquals(Move.SHIELD, CombatTactics.decide(blaze.shotIncoming(true).build()));
        assertEquals(Move.SHIELD, CombatTactics.decide(zombie().shotIncoming(true).build()), "even with a hit ready");
        assertEquals(Move.DRAW, CombatTactics.decide(blaze.drawTicks(CombatTactics.COMMIT_DRAW_TICKS).build()));
        assertEquals(Move.JUMP, CombatTactics.decide(zombie().shotIncoming(true).hasShield(false).build()));
        Situation.Builder creeper = zombie().foe(Foe.CREEPER).foeCharging(true).shotIncoming(true);
        assertEquals(Move.BACK_OFF, CombatTactics.decide(creeper.distance(2.5).build()));
    }

    @Test
    void endermenAreNeverShot() {
        Situation.Builder enderman = zombie().foe(Foe.ENDERMAN).inReach(false).distance(12);
        assertNotEquals(Move.DRAW, CombatTactics.decide(enderman.build()));
        assertEquals(Move.CHARGE, CombatTactics.decide(enderman.build()));
        assertEquals(Move.JUMP, CombatTactics.decide(enderman.inReach(true).distance(2.5).build()));
    }

    @Test
    void theBowNeedsArrowsAndSight() {
        Situation.Builder blaze = zombie().foe(Foe.BLAZE).inReach(false).distance(8).heightAbove(4);
        assertEquals(Move.SHIELD, CombatTactics.decide(blaze.hasBow(false).build()));
        assertEquals(Move.APPROACH, CombatTactics.decide(blaze.lineOfSight(false).clearRun(false).build()));
    }

    @Test
    void aDrawnBowIsLoosedAtAnythingInRangeRatherThanWasted() {
        Situation.Builder zombie = zombie().inReach(false).distance(9);
        assertEquals(Move.LOOSE, CombatTactics.decide(zombie.drawTicks(20).aimSolved(true).build()));
    }
}
