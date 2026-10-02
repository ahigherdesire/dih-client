package dihclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DihTextScaleTest {

    @Test
    void valuesSnapToTenthsWithinTheRange() {
        assertEquals(1.0, DihUiScale.normalizeTextScale(1.0));
        assertEquals(1.2, DihUiScale.normalizeTextScale(1.23));
        assertEquals(0.8, DihUiScale.normalizeTextScale(0.5));
        assertEquals(1.5, DihUiScale.normalizeTextScale(3.0));
        assertEquals(1.0, DihUiScale.normalizeTextScale(Double.NaN));
        assertEquals(1.0, DihUiScale.normalizeTextScale(0));
    }

    @Test
    void steppingWrapsAtBothEnds() {
        assertEquals(1.1, DihUiScale.nextTextScale(1.0));
        assertEquals(0.8, DihUiScale.nextTextScale(1.5));
        assertEquals(0.9, DihUiScale.previousTextScale(1.0));
        assertEquals(1.5, DihUiScale.previousTextScale(0.8));
    }

    @Test
    void everyStepIsReachableAndLabelled() {
        double scale = 0.8;
        StringBuilder labels = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            labels.append(DihUiScale.formatTextScale(scale)).append(' ');
            scale = DihUiScale.nextTextScale(scale);
        }
        assertEquals("80% 90% 100% 110% 120% 130% 140% 150% ", labels.toString());
        assertEquals(0.8, scale);
    }
}
