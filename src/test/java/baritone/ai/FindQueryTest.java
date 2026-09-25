package baritone.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure half of the find tool: name guessing and directions. */
final class FindQueryTest {

    @Test
    void compassFollowsMinecraftAxes() {
        assertEquals("N", FindQuery.compass(0, -10));
        assertEquals("S", FindQuery.compass(0, 10));
        assertEquals("E", FindQuery.compass(10, 0));
        assertEquals("W", FindQuery.compass(-10, 0));
        assertEquals("NE", FindQuery.compass(10, -10));
        assertEquals("SW", FindQuery.compass(-7, 7));
        assertEquals("NW", FindQuery.compass(-10, -9));
        assertEquals("N", FindQuery.compass(1, -10), "mostly north");
        assertEquals("here", FindQuery.compass(0, 0));
    }

    @Test
    void relativeMentionsHeightOnlyWhenItMatters() {
        assertEquals("10m E", FindQuery.relative(10, 1, 0));
        assertEquals("14m NE, 9 below", FindQuery.relative(8, -9, -8));
        assertEquals("5m straight up", FindQuery.relative(0, 5, 0));
        assertEquals("right here", FindQuery.relative(0, 0, 0));
    }

    @Test
    void candidatesTryTheTextThenSingularThenOre() {
        assertEquals("diamond_ore", FindQuery.candidates("diamond_ore").get(0));
        List<String> diamonds = FindQuery.candidates("Diamonds");
        assertEquals("diamonds", diamonds.get(0));
        assertTrue(diamonds.indexOf("diamond") < diamonds.indexOf("diamond_ore"));
        assertTrue(FindQuery.candidates("cows").contains("cow"));
        assertTrue(FindQuery.candidates("torches").contains("torch"));
        assertTrue(FindQuery.candidates("berries").contains("berry"));
        assertEquals("oak_log", FindQuery.candidates("  oak log ").get(0));
        assertEquals("minecraft:cow", FindQuery.candidates("minecraft:cow").get(0));
        assertTrue(FindQuery.candidates("minecraft:cows").contains("minecraft:cow"));
        assertTrue(FindQuery.candidates("   ").isEmpty());
        assertTrue(FindQuery.candidates(null).isEmpty());
    }

    @Test
    void oresComeWithTheirDeepslateTwin() {
        assertEquals(List.of("deepslate_iron_ore"), FindQuery.oreVariants("iron_ore"));
        assertEquals(List.of("iron_ore"), FindQuery.oreVariants("deepslate_iron_ore"));
        assertEquals(List.of(), FindQuery.oreVariants("chest"));
    }
}
