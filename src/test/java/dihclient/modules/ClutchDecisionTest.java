package dihclient.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClutchDecisionTest {
    @Test
    void blocksMethodAlwaysClutchesWithBlocks() {
        assertEquals(ClutchDecision.Plan.BLOCKS,
            ClutchDecision.plan(ClutchDecision.BLOCKS, false, true, true, true));
    }

    @Test
    void waterMlgsWhenOverworldGroundIsReachableAndTheFallIsBig() {
        assertEquals(ClutchDecision.Plan.WATER,
            ClutchDecision.plan(ClutchDecision.WATER, false, true, true, true));
        assertEquals(ClutchDecision.Plan.WATER,
            ClutchDecision.plan(ClutchDecision.AUTO, false, true, true, true));
    }

    @Test
    void waterFallsBackToBlocksInTheNetherOrWithNoBucket() {
        assertEquals(ClutchDecision.Plan.BLOCKS,
            ClutchDecision.plan(ClutchDecision.WATER, true, true, true, true));
        assertEquals(ClutchDecision.Plan.BLOCKS,
            ClutchDecision.plan(ClutchDecision.AUTO, true, true, true, true));
        assertEquals(ClutchDecision.Plan.BLOCKS,
            ClutchDecision.plan(ClutchDecision.WATER, false, false, true, true));
    }

    @Test
    void strictWaterWaitsForGroundWhileAutoKeepsBlocksAsASafetyNet() {
        // Ground not in reach yet.
        assertEquals(ClutchDecision.Plan.WAIT,
            ClutchDecision.plan(ClutchDecision.WATER, false, true, true, false));
        assertEquals(ClutchDecision.Plan.BLOCKS,
            ClutchDecision.plan(ClutchDecision.AUTO, false, true, true, false));
    }

    @Test
    void doesNotMlgAShortFallEvenWithGroundInReach() {
        assertEquals(ClutchDecision.Plan.WAIT,
            ClutchDecision.plan(ClutchDecision.WATER, false, true, false, true));
        assertEquals(ClutchDecision.Plan.BLOCKS,
            ClutchDecision.plan(ClutchDecision.AUTO, false, true, false, true));
    }
}
