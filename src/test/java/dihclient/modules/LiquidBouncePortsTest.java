package dihclient.modules;

import dihclient.util.DihBreakMath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The small pure pieces of Radar, Zoom, SmartEat, BetterChat and the break maths. */
class LiquidBouncePortsTest {
    @Test
    void radarArrowNarrowsToItsTipAndIsNotchedAtTheTail() {
        float[] tail = RadarModule.rowSpan(0.0F, 10.0F, 4.0F, 2.0F);
        assertArrayEquals(new float[] {4.0F, 4.0F}, tail, 1.0E-6F);
        float[] pastNotch = RadarModule.rowSpan(5.0F, 10.0F, 4.0F, 2.0F);
        assertArrayEquals(new float[] {2.0F, 0.0F}, pastNotch, 1.0E-6F);
        float[] tip = RadarModule.rowSpan(10.0F, 10.0F, 4.0F, 0.0F);
        assertEquals(0.0F, tip[0], 1.0E-6F);
    }

    @Test
    void zoomEasesOutAndFinishesOnTime() {
        assertEquals(0.0F, ZoomModule.eased(0.0F, 100.0F));
        assertEquals(0.75F, ZoomModule.eased(50.0F, 100.0F), 1.0E-6F);
        assertEquals(1.0F, ZoomModule.eased(250.0F, 100.0F));
        assertEquals(1.0F, ZoomModule.eased(0.0F, 0.0F));
    }

    @Test
    void zoomSensitivityTurnsInProportionToTheZoom() {
        double s = 0.5D;
        assertEquals(s, ZoomModule.sensitivityFor(s, 1.0D), 1.0E-9);
        double scaled = ZoomModule.sensitivityFor(s, 0.125D);
        double turn = Math.pow(s * 0.6D + 0.2D, 3.0D);
        double scaledTurn = Math.pow(scaled * 0.6D + 0.2D, 3.0D);
        assertEquals(0.125D, scaledTurn / turn, 1.0E-9);
    }

    private static SmartEatModule.Candidate food(int slot, int nutrition) {
        return new SmartEatModule.Candidate(slot, SmartEatModule.Kind.FOOD, 20, nutrition);
    }

    @Test
    void smartEatPrefersTheMostUrgentThenTheMostFillingThenTheNearest() {
        SmartEatModule.Candidate bread = food(0, 5);
        SmartEatModule.Candidate steak = food(8, 8);
        SmartEatModule.Candidate gapple = new SmartEatModule.Candidate(4, SmartEatModule.Kind.GOLDEN_APPLE, 9, 4);
        assertEquals(steak, SmartEatModule.best(List.of(bread, steak), true, 0));
        assertEquals(gapple, SmartEatModule.best(List.of(bread, steak, gapple), true, 0));
        SmartEatModule.Candidate otherBread = food(2, 5);
        assertEquals(otherBread, SmartEatModule.best(List.of(bread, otherBread), true, 3));
    }

    @Test
    void smartEatLeavesPlainFoodAloneUntilHungry() {
        SmartEatModule.Candidate bread = food(0, 5);
        assertNull(SmartEatModule.best(List.of(bread), false, 0));
        SmartEatModule.Candidate potion = new SmartEatModule.Candidate(1, SmartEatModule.Kind.HEALING_POTION, 8, 0);
        assertEquals(potion, SmartEatModule.best(List.of(bread, potion), false, 0));
    }

    @Test
    void fullWidthShiftsOnlyPrintableAscii() {
        assertEquals("Ｈｉ！", BetterChatModule.fullWidth("Hi!"));
        assertEquals(" é", BetterChatModule.fullWidth(" é"));
    }

    @Test
    void breakProgressScalesWithToolSpeedAndTheCorrectToolDivisor() {
        // Fist on stone (wrong tool, speed 1) to a pickaxe at speed 8 that is the right tool.
        float fist = 1.0F / 1.5F / 100.0F;
        float pick = DihBreakMath.scaled(fist, 1.0F, 8.0F, false, true);
        assertEquals(8.0F / 1.5F / 30.0F, pick, 1.0E-7F);
        assertEquals(fist, DihBreakMath.scaled(fist, 1.0F, 1.0F, false, false), 1.0E-9F);
    }
}
