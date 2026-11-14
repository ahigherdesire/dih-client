package dihclient.modules;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NewChunksModuleTest {
    @Test
    void negativeChunkCoordinatesKeepDistinctKeys() {
        assertNotEquals(NewChunksModule.key(-1, 0), NewChunksModule.key(0, -1));
        long key = NewChunksModule.key(-5, -7);
        assertEquals(-5, (int) (key >> 32));
        assertEquals(-7, (int) key);
    }

    @Test
    void anOldChunkWithNoNewNeighboursKeepsItsColour() {
        assertEquals(0.0D, NewChunksModule.smoothRatio(0, 0, k -> false));
    }

    @Test
    void nearNeighboursCountTwiceAsMuchAsFarOnes() {
        Set<Long> near = Set.of(NewChunksModule.key(1, 0));
        Set<Long> far = Set.of(NewChunksModule.key(2, 0));
        assertEquals(1.0D / 12.0D, NewChunksModule.smoothRatio(0, 0, near::contains), 1.0E-9);
        assertEquals(0.5D / 12.0D, NewChunksModule.smoothRatio(0, 0, far::contains), 1.0E-9);
    }

    @Test
    void surroundedByNewChunksIsFullStrength() {
        assertEquals(1.0D, NewChunksModule.smoothRatio(0, 0, k -> true));
    }
}
