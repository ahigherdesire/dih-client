package dihclient.modules;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BowAimAssistAndCpsTest {
    // A full-charge arrow: 3 blocks/tick, 0.05 gravity, 0.99 drag.
    private static final double SPEED = 3.0D, GRAVITY = 0.05D, DRAG = 0.99D;

    @Test
    void levelShotWithoutGravityAimsStraight() {
        BowAimAssistModule.Solution solution = BowAimAssistModule.solve(30.0D, 0.0D, 3.0D, 0.0D, 1.0D);
        assertNotNull(solution);
        assertEquals(0.0F, solution.pitch(), 0.01F);
        assertEquals(10.0D, solution.ticks(), 1.0E-6D);
    }

    @Test
    void solvedArcPassesThroughTheTarget() {
        for (double[] target : new double[][] {{20, 0}, {40, 5}, {60, -8}, {35, 12}}) {
            BowAimAssistModule.Solution solution = BowAimAssistModule.solve(target[0], target[1], SPEED, GRAVITY, DRAG);
            assertNotNull(solution, "no solution for " + target[0] + ", " + target[1]);
            double[] at = BowAimAssistModule.heightAt(-solution.pitch(), target[0], SPEED, GRAVITY, DRAG);
            assertNotNull(at);
            assertEquals(target[1], at[0], 0.05D, "arc misses the target at " + target[0]);
        }
    }

    @Test
    void gravityMeansAimingAboveALevelTarget() {
        BowAimAssistModule.Solution solution = BowAimAssistModule.solve(50.0D, 0.0D, SPEED, GRAVITY, DRAG);
        assertNotNull(solution);
        assertTrue(solution.pitch() < 0.0F, "a distant level shot must aim up (negative pitch)");
    }

    @Test
    void targetsOutOfReachHaveNoSolution() {
        assertNull(BowAimAssistModule.solve(500.0D, 0.0D, 1.0D, GRAVITY, DRAG));
        assertNull(BowAimAssistModule.solve(10.0D, 200.0D, SPEED, GRAVITY, DRAG));
    }

    @Test
    void customCpsRangeIsRespectedEachCycle() {
        Random random = new Random(42L);
        for (int[] band : new int[][] {{1, 1}, {3, 4}, {12, 16}, {20, 20}}) {
            for (int i = 0; i < 200; i++) {
                int[] cycle = new int[KillAuraModule.CLICK_CYCLE];
                KillAuraModule.stabilizedFill(cycle, random, band[0], band[1]);
                int clicks = 0;
                for (int tick : cycle) {
                    assertTrue(tick <= 1, "more than one attack in a tick");
                    clicks += tick;
                }
                assertTrue(clicks >= band[0] && clicks <= band[1],
                    clicks + " clicks outside " + band[0] + "-" + band[1]);
            }
        }
    }
}
