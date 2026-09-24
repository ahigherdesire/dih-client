package baritone.command.defaults;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seed parsing must match the world-creation screen exactly, or text seeds map the wrong world. */
final class SeedParseTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void numericSeedsAreUsedAsIs() {
        assertEquals(-4172144997902289642L, ClientStructureFinder.parse("-4172144997902289642").getAsLong());
        assertEquals(42L, ClientStructureFinder.parse("  42 ").getAsLong());
    }

    @Test
    void textSeedsHashLikeVanilla() {
        assertEquals("glacier".hashCode(), ClientStructureFinder.parse("glacier").getAsLong());
        assertEquals("two words".hashCode(), ClientStructureFinder.parse("two words").getAsLong());
    }

    @Test
    void blankIsNotASeed() {
        assertTrue(ClientStructureFinder.parse("   ").isEmpty());
        assertTrue(ClientStructureFinder.parse(null).isEmpty());
    }
}
