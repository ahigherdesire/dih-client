package dihclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DihUpdateCheckerTest {
    @Test
    void stripsTheTagPrefix() {
        assertEquals("5.0", DihUpdateChecker.stripTag("v5.0"));
        assertEquals("5.0.1", DihUpdateChecker.stripTag(" V5.0.1 "));
        assertEquals("5.0", DihUpdateChecker.stripTag("5.0"));
    }

    @Test
    void comparesVersionsNumerically() {
        assertTrue(DihUpdateChecker.compareVersions("5.0.1", "5.0") > 0);
        assertTrue(DihUpdateChecker.compareVersions("5.10", "5.9") > 0);
        assertTrue(DihUpdateChecker.compareVersions("4.9", "5.0") < 0);
        assertEquals(0, DihUpdateChecker.compareVersions("5.0", "5.0.0"));
        assertEquals(0, DihUpdateChecker.compareVersions("5.0", "5.0"));
    }
}
