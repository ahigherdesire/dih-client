package baritone.acquire.exec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AcquireArgsTest {

    @Test
    void itemAloneMeansOne() {
        AcquireArgs a = AcquireArgs.parse("diamond_pickaxe");
        assertEquals(AcquireArgs.Mode.ACQUIRE, a.mode());
        assertEquals("diamond_pickaxe", a.item());
        assertEquals(1, a.count());
    }

    @Test
    void countEitherSide() {
        assertEquals(new AcquireArgs(AcquireArgs.Mode.ACQUIRE, "torch", 64), AcquireArgs.parse("64 torch"));
        assertEquals(new AcquireArgs(AcquireArgs.Mode.ACQUIRE, "torch", 64), AcquireArgs.parse("torch 64"));
        assertEquals(new AcquireArgs(AcquireArgs.Mode.ACQUIRE, "torch", 16), AcquireArgs.parse("  x16   torch "));
        assertEquals(new AcquireArgs(AcquireArgs.Mode.ACQUIRE, "torch", 16), AcquireArgs.parse("torch 16x"));
    }

    @Test
    void multiWordItemsKeepTheirWords() {
        assertEquals(new AcquireArgs(AcquireArgs.Mode.ACQUIRE, "iron pick", 1), AcquireArgs.parse("iron pick"));
        assertEquals(new AcquireArgs(AcquireArgs.Mode.ACQUIRE, "iron pick", 3), AcquireArgs.parse("3 iron   pick"));
        assertEquals(new AcquireArgs(AcquireArgs.Mode.ACQUIRE, "Iron Pickaxe", 2), AcquireArgs.parse("Iron Pickaxe 2"));
    }

    @Test
    void planTakesTheSameItemSyntax() {
        assertEquals(new AcquireArgs(AcquireArgs.Mode.PLAN, "iron_pickaxe", 1), AcquireArgs.parse("plan iron_pickaxe"));
        assertEquals(new AcquireArgs(AcquireArgs.Mode.PLAN, "torch", 32), AcquireArgs.parse("PLAN 32 torch"));
        assertThrows(IllegalArgumentException.class, () -> AcquireArgs.parse("plan"));
    }

    @Test
    void statusAndStop() {
        assertEquals(AcquireArgs.Mode.STATUS, AcquireArgs.parse("").mode());
        assertEquals(AcquireArgs.Mode.STATUS, AcquireArgs.parse(null).mode());
        assertEquals(AcquireArgs.Mode.STATUS, AcquireArgs.parse("status").mode());
        assertEquals(AcquireArgs.Mode.STOP, AcquireArgs.parse("stop").mode());
        assertEquals(AcquireArgs.Mode.STOP, AcquireArgs.parse("Cancel").mode());
        assertNull(AcquireArgs.parse("stop").item());
    }

    @Test
    void badCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> AcquireArgs.parse("0 torch"));
        assertThrows(IllegalArgumentException.class, () -> AcquireArgs.parse("torch -3"));
        assertThrows(IllegalArgumentException.class, () -> AcquireArgs.parse("99999 torch"));
        assertThrows(IllegalArgumentException.class, () -> AcquireArgs.parse("64"));
    }

    @Test
    void countWordParsing() {
        assertEquals(64, AcquireArgs.parseCount("64"));
        assertEquals(64, AcquireArgs.parseCount("64x"));
        assertEquals(64, AcquireArgs.parseCount("X64"));
        assertNull(AcquireArgs.parseCount("x"));
        assertNull(AcquireArgs.parseCount("x64x"));
        assertNull(AcquireArgs.parseCount("torch"));
    }
}
