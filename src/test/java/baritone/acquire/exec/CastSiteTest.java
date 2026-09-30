package baritone.acquire.exec;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CastSiteTest {

    /** A floor at y 63 everywhere unless holed; air above; lava only where put. */
    private static final class Flat implements CastSite.Terrain {
        final Set<BlockPos> holes = new HashSet<>();
        final Set<BlockPos> lava = new HashSet<>();
        final Set<BlockPos> walls = new HashSet<>();

        @Override
        public boolean floor(BlockPos pos) {
            return pos.getY() == 63 && !holes.contains(pos) || walls.contains(pos);
        }

        @Override
        public boolean open(BlockPos pos) {
            return pos.getY() > 63 && !lava.contains(pos) && !walls.contains(pos);
        }

        @Override
        public boolean lava(BlockPos pos) {
            return lava.contains(pos);
        }
    }

    private static final BlockPos FEET = new BlockPos(0, 64, 0);

    @Test
    void threeCellsInARowOnFloorWithTheWaterBetween() {
        CastSite site = CastSite.find(FEET, 6, new Flat());
        assertNotNull(site);
        assertEquals(FEET, site.stand(), "flat ground: cast right where the player is");
        assertEquals(site.stand().relative(site.toward().getOpposite()), site.water());
        assertEquals(site.water().relative(site.toward().getOpposite()), site.cast());
        assertEquals(site.stand(), site.cast().relative(site.toward(), 2));
    }

    @Test
    void neverNextToLava() {
        Flat t = new Flat();
        // A lava pool around the player: every cell within 1 of it is out.
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) t.lava.add(new BlockPos(x, 63, z));
        CastSite site = CastSite.find(FEET, 8, t);
        assertNotNull(site);
        for (BlockPos cell : new BlockPos[]{site.cast(), site.water(), site.stand()}) {
            for (BlockPos l : t.lava) {
                int apart = Math.max(Math.max(Math.abs(cell.getX() - l.getX()), Math.abs(cell.getY() - l.getY())),
                        Math.abs(cell.getZ() - l.getZ()));
                assertTrue(apart > 1, cell + " touches lava at " + l);
            }
        }
    }

    @Test
    void theCastCellNeedsFloorSoTheDropCantFall() {
        Flat t = new Flat();
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) if (x != 0 || z != 0) t.holes.add(new BlockPos(x, 63, z));
        assertNull(CastSite.find(FEET, 6, t), "one floor block is not a row of three");
        t.holes.remove(new BlockPos(1, 63, 0));
        t.holes.remove(new BlockPos(2, 63, 0));
        CastSite site = CastSite.find(FEET, 6, t);
        assertNotNull(site);
        assertTrue(t.floor(site.cast().below()) && t.floor(site.water().below()) && t.floor(site.stand().below()));
    }

    @Test
    void theNearestSiteWins() {
        Flat t = new Flat();
        t.walls.add(new BlockPos(0, 64, 0));
        CastSite site = CastSite.find(FEET, 6, t);
        assertNotNull(site);
        assertFalse(site.stand().equals(FEET), "a wall where the player stands");
        assertEquals(1.0, site.stand().distSqr(FEET), "the next cell over: " + site);
    }
}
