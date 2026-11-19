package dihclient.util;

import dihclient.util.DihSpawnLight.Darkness;
import dihclient.util.DihSpawnLight.Spot;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihSpawnLightTest {
    private static final int OVERWORLD_LIMIT = 0;
    private static final int MAX = DihSpawnLight.DEFAULT_MAX_SPAWN_BRIGHTNESS;

    @Test
    void anyBlockLightStopsOverworldSpawns() {
        assertEquals(Darkness.NONE, DihSpawnLight.classify(1, 0, OVERWORLD_LIMIT, MAX));
        assertEquals(Darkness.NONE, DihSpawnLight.classify(14, 15, OVERWORLD_LIMIT, MAX));
    }

    @Test
    void aSpotWithNoLightAtAllIsAlwaysDark() {
        assertEquals(Darkness.ALWAYS, DihSpawnLight.classify(0, 0, OVERWORLD_LIMIT, MAX));
    }

    @Test
    void dimSkyLightStillSpawnsDuringTheDay() {
        // Under an overhang the sky light can be 7 or less: too dim for daylight to protect it.
        assertEquals(Darkness.ALWAYS, DihSpawnLight.classify(0, MAX, OVERWORLD_LIMIT, MAX));
    }

    @Test
    void openSkyIsOnlyDarkAtNight() {
        assertEquals(Darkness.NIGHT_ONLY, DihSpawnLight.classify(0, MAX + 1, OVERWORLD_LIMIT, MAX));
        assertEquals(Darkness.NIGHT_ONLY, DihSpawnLight.classify(0, 15, OVERWORLD_LIMIT, MAX));
    }

    @Test
    void aLenientBlockLightLimitStillNeedsTheBrightnessTest() {
        // Nether-style dimension: block light up to 15 passes the limit, but the light test still caps it.
        assertEquals(Darkness.ALWAYS, DihSpawnLight.classify(7, 0, 15, MAX));
        assertEquals(Darkness.NONE, DihSpawnLight.classify(8, 0, 15, MAX));
    }

    @Test
    void offsetsCoverTheWholeBoxExactlyOnce() {
        int h = 3, v = 2;
        int count = DihSpawnLight.cellCount(h, v);
        assertEquals(7 * 7 * 5, count);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int[] o = DihSpawnLight.offset(i, h, v);
            assertTrue(Math.abs(o[0]) <= h && Math.abs(o[1]) <= v && Math.abs(o[2]) <= h, "out of box at " + i);
            assertTrue(seen.add(o[0] + "," + o[1] + "," + o[2]), "visited twice at " + i);
        }
        assertEquals(count, seen.size());
    }

    @Test
    void aColumnIsWalkedTopToBottom() {
        int[] first = DihSpawnLight.offset(0, 4, 3);
        int[] second = DihSpawnLight.offset(1, 4, 3);
        assertEquals(3, first[1]);
        assertEquals(2, second[1]);
        assertEquals(first[0], second[0]);
        assertEquals(first[2], second[2]);
    }

    @Test
    void theCapKeepsTheClosestSpotsNearestFirst() {
        List<Spot> spots = List.of(
            new Spot(10, 64, 0, Darkness.ALWAYS),
            new Spot(1, 64, 0, Darkness.NIGHT_ONLY),
            new Spot(-5, 64, 0, Darkness.ALWAYS),
            new Spot(0, 64, 0, Darkness.ALWAYS));
        List<Spot> kept = DihSpawnLight.nearest(spots, 2, 0.5, 64.0, 0.5);
        assertEquals(List.of(new Spot(0, 64, 0, Darkness.ALWAYS), new Spot(1, 64, 0, Darkness.NIGHT_ONLY)), kept);
    }

    @Test
    void aCapLargerThanTheListKeepsEverything() {
        List<Spot> spots = List.of(new Spot(3, 0, 0, Darkness.ALWAYS), new Spot(1, 0, 0, Darkness.ALWAYS));
        assertEquals(2, DihSpawnLight.nearest(spots, 100, 0, 0, 0).size());
        assertEquals(List.of(), DihSpawnLight.nearest(spots, 0, 0, 0, 0));
    }
}
