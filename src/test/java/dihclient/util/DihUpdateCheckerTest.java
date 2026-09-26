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

    @Test
    void preReleasesComeBeforeTheirRelease() {
        assertTrue(DihUpdateChecker.compareVersions("5.1", "5.1-beta.2") > 0, "a beta user hears about 5.1");
        assertTrue(DihUpdateChecker.compareVersions("5.1-beta.2", "5.0") > 0, "the beta is newer than 5.0");
        assertTrue(DihUpdateChecker.compareVersions("5.0", "5.1-beta.2") < 0, "so 5.0 is no update for it");
        assertTrue(DihUpdateChecker.compareVersions("5.1-beta.10", "5.1-beta.2") > 0);
        assertTrue(DihUpdateChecker.compareVersions("5.1-rc.1", "5.1-beta.9") > 0);
        assertTrue(DihUpdateChecker.compareVersions("5.1.1", "5.1") > 0);
        assertEquals(0, DihUpdateChecker.compareVersions("5.1-beta.2", "5.1-beta.2"));
    }
}
