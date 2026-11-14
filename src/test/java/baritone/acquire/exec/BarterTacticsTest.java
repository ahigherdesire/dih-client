package baritone.acquire.exec;

import baritone.acquire.exec.BarterTactics.Move;
import baritone.acquire.exec.BarterTactics.Piglin;
import baritone.acquire.exec.BarterTactics.Situation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BarterTacticsTest {

    /** Gold in hand and on, no loot about, one idle piglin two blocks off, nothing thrown lately. */
    private static Situation.Builder ready() {
        return Situation.builder().have(0).want(12).gold(64).wearingGold(true).goldPieceHeld(true)
                .piglins(List.of(new Piglin(1, 2.0, false, 1000))).sinceThrow(1000).goldOnGround(0);
    }

    @Test
    void anIdlePiglinInRangeGetsAnIngot() {
        BarterTactics.Decision d = BarterTactics.decide(ready().build());
        assertEquals(Move.THROW, d.move());
        assertEquals(1, d.piglin());
    }

    @Test
    void enoughHeldIsDone() {
        assertEquals(Move.DONE, BarterTactics.decide(ready().have(12).build()).move());
    }

    @Test
    void goldGoesOnBeforeAnything() {
        assertEquals(Move.WEAR, BarterTactics.decide(ready().wearingGold(false).build()).move());
        assertEquals(Move.NO_ARMOUR, BarterTactics.decide(ready().wearingGold(false).goldPieceHeld(false).build()).move());
    }

    @Test
    void whatTheyThrowBackIsPickedUpFirst() {
        assertEquals(Move.LOOT, BarterTactics.decide(ready().lootDistance(5).build()).move());
        assertEquals(Move.THROW, BarterTactics.decide(ready().lootDistance(BarterTactics.LOOT_RADIUS + 1).build()).move());
    }

    @Test
    void aFarPiglinIsWalkedUpTo() {
        BarterTactics.Decision d = BarterTactics.decide(ready().piglins(List.of(new Piglin(7, 9.0, false, 1000))).build());
        assertEquals(Move.APPROACH, d.move());
        assertEquals(7, d.piglin());
    }

    @Test
    void theNearestIdlePiglinIsPicked() {
        BarterTactics.Decision d = BarterTactics.decide(ready().piglins(List.of(
                new Piglin(1, 1.5, true, 1000), new Piglin(2, 3.0, false, 1000), new Piglin(3, 2.5, false, 1000))).build());
        assertEquals(Move.THROW, d.move());
        assertEquals(3, d.piglin());
    }

    @Test
    void piglinsBusyLookingGoldOverAreWaitedFor() {
        assertEquals(Move.WAIT, BarterTactics.decide(ready().piglins(List.of(new Piglin(1, 2.0, true, 1000))).build()).move());
        // One just thrown to hasn't had time to pick it up.
        assertEquals(Move.WAIT, BarterTactics.decide(ready().piglins(List.of(new Piglin(1, 2.0, false, 20))).build()).move());
    }

    @Test
    void throwsAreSpacedOut() {
        assertEquals(Move.WAIT, BarterTactics.decide(ready().sinceThrow(3).build()).move());
    }

    @Test
    void goldLyingAboutIsLeftForThePiglinsToFetchForAWhile() {
        assertEquals(Move.WAIT, BarterTactics.decide(ready().goldOnGround(1).sinceThrow(40).build()).move());
        assertEquals(Move.THROW, BarterTactics.decide(ready().goldOnGround(1).sinceThrow(BarterTactics.FETCH_TICKS + 1).build()).move());
    }

    @Test
    void noPiglinInSightMeansLookingForThem() {
        assertEquals(Move.EXPLORE, BarterTactics.decide(ready().piglins(List.of()).build()).move());
    }

    @Test
    void outOfGoldWaitsForTheLastTradesThenStops() {
        assertEquals(Move.WAIT, BarterTactics.decide(ready().gold(0).piglins(List.of(new Piglin(1, 2.0, true, 50))).build()).move());
        assertEquals(Move.OUT_OF_GOLD, BarterTactics.decide(ready().gold(0).build()).move());
        assertEquals(Move.OUT_OF_GOLD, BarterTactics.decide(ready().gold(0).piglins(List.of()).build()).move());
        // Its drops still come first.
        assertEquals(Move.LOOT, BarterTactics.decide(ready().gold(0).lootDistance(3).build()).move());
    }
}
