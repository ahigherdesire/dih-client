package baritone.acquire.exec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalCastTest {

    /** Facing south from 0 64 0: {@code i} is east, {@code k} is south. */
    private static final PortalCast CAST = new PortalCast(new BlockPos(0, 64, 0), Direction.EAST, Direction.SOUTH);

    /** Ground up to y 63, air above; walls, holes, lava and gravel only where put. */
    private static final class Field implements PortalCast.Terrain {
        final Set<BlockPos> solid = new HashSet<>();
        final Set<BlockPos> holes = new HashSet<>();
        final Set<BlockPos> lava = new HashSet<>();
        final Set<BlockPos> gravel = new HashSet<>();

        boolean solid(BlockPos pos) {
            return (pos.getY() <= 63 && !holes.contains(pos) || solid.contains(pos) || gravel.contains(pos)) && !lava.contains(pos);
        }

        @Override
        public boolean floor(BlockPos pos) {
            return solid(pos) && !gravel.contains(pos);
        }

        @Override
        public boolean air(BlockPos pos) {
            return !solid(pos) && !lava.contains(pos);
        }

        @Override
        public boolean diggable(BlockPos pos) {
            return solid(pos);
        }

        @Override
        public boolean placeable(BlockPos pos) {
            return air(pos);
        }

        @Override
        public boolean falls(BlockPos pos) {
            return gravel.contains(pos);
        }

        @Override
        public boolean lava(BlockPos pos) {
            return lava.contains(pos);
        }
    }

    private static BlockPos rel(int i, int j, int k) {
        return CAST.at(i, j, k);
    }

    @Test
    void theTenCellsAreAPortalFrameAroundATwoByThreeOpening() {
        Set<BlockPos> frame = new HashSet<>(CAST.frame());
        Set<BlockPos> want = new HashSet<>(List.of(rel(1, 0, 0), rel(2, 0, 0), rel(1, 4, 0), rel(2, 4, 0)));
        for (int j = 1; j <= 3; j++) {
            want.add(rel(0, j, 0));
            want.add(rel(3, j, 0));
        }
        assertEquals(want, frame);
        assertEquals(10, CAST.frame().size());
        assertEquals(6, CAST.interior().size());
        for (BlockPos pos : CAST.interior()) assertTrue(!frame.contains(pos));
        assertEquals(rel(1, 0, 0), CAST.ignite());
    }

    @Test
    void waterTouchesEachCellFromTheSideOrAboveNeverBelow() {
        for (PortalCast.Cell cell : CAST.cells()) {
            BlockPos d = cell.water().subtract(cell.lava());
            int manhattan = Math.abs(d.getX()) + Math.abs(d.getY()) + Math.abs(d.getZ());
            assertEquals(1, manhattan, "water next to " + cell.lava().toShortString());
            assertNotEquals(-1, d.getY(), "water below lava doesn't set it: " + cell.lava().toShortString());
            assertTrue(!CAST.frame().contains(cell.water()), "the water cell isn't a frame cell");
            assertTrue(CAST.open().contains(cell.water()) && CAST.open().contains(cell.lava()));
        }
    }

    @Test
    void everyPourGoesThroughTheFaceOfAWallOrGroundBlock() {
        Set<BlockPos> solid = new HashSet<>(CAST.wall());
        solid.addAll(CAST.ground());
        for (PortalCast.Cell cell : CAST.cells()) {
            assertTrue(solid.contains(cell.lavaVia()), cell.lava().toShortString());
            assertEquals(cell.lava(), cell.lavaVia().relative(cell.lavaFace()));
            assertTrue(solid.contains(cell.waterVia()), cell.water().toShortString());
            assertEquals(cell.water(), cell.waterVia().relative(cell.waterFace()));
        }
    }

    @Test
    void theTopGoesFirstAndEachSideTopDown() {
        List<BlockPos> order = CAST.frame();
        assertEquals(rel(1, 4, 0), order.get(0));
        assertEquals(rel(2, 4, 0), order.get(1));
        for (int i : new int[]{0, 3}) {
            assertTrue(order.indexOf(rel(i, 3, 0)) < order.indexOf(rel(i, 2, 0)));
            assertTrue(order.indexOf(rel(i, 2, 0)) < order.indexOf(rel(i, 1, 0)));
        }
        // The bottom row last: the look rays down to the ground under it pass nothing cast.
        assertEquals(Set.of(rel(1, 0, 0), rel(2, 0, 0)), Set.of(order.get(8), order.get(9)));
    }

    @Test
    void theWallGoesUpFromTheGround() {
        Set<BlockPos> below = new HashSet<>(CAST.ground());
        for (BlockPos pos : CAST.wall()) {
            assertTrue(below.contains(pos.below()), "nothing under " + pos.toShortString());
            below.add(pos);
        }
        assertEquals(PortalCast.WALL_BLOCKS, CAST.wall().size());
    }

    @Test
    void everyPourIsInReachFromItsStand() {
        for (PortalCast.Cell cell : CAST.cells()) {
            Vec3 eye = Vec3.atBottomCenterOf(cell.stand()).add(0, 1.62, 0);
            for (BlockPos via : List.of(cell.lavaVia(), cell.waterVia())) {
                Direction face = via.equals(cell.lavaVia()) ? cell.lavaFace() : cell.waterFace();
                Vec3 point = Vec3.atCenterOf(via).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                assertTrue(eye.distanceTo(point) < 4.3, via.toShortString() + " is " + eye.distanceTo(point) + " away");
            }
            // The stand is in front of the half it pours.
            assertEquals(2, cell.stand().getZ() - CAST.origin().getZ());
        }
    }

    @Test
    void aFlatFieldTakesTheWholeWallAndNoDigging() {
        PortalCast.Found found = PortalCast.find(new BlockPos(0, 64, 0), 6, 24, Set.of(), new Field());
        assertNotNull(found);
        assertEquals(0, found.digs());
        assertEquals(PortalCast.WALL_BLOCKS, found.places());
        assertEquals(new BlockPos(0, 64, 0), found.cast().stand(false), "flat: right where the player is");
    }

    @Test
    void aCliffIsTheWall() {
        Field t = new Field();
        // A cliff face along z = -3, rising well above the frame, north of the player.
        for (int x = -10; x <= 10; x++) for (int y = 64; y <= 72; y++) for (int z = -12; z <= -3; z++) t.solid.add(new BlockPos(x, y, z));
        PortalCast.Found found = PortalCast.find(new BlockPos(0, 64, 0), 6, 24, Set.of(), t);
        assertNotNull(found);
        assertEquals(0, found.places(), "the cliff is the wall");
        assertEquals(0, found.digs());
        assertEquals(Direction.SOUTH, found.cast().front());
        assertEquals(-3, found.cast().at(0, 0, -1).getZ());
    }

    @Test
    void lavaKeepsItsDistance() {
        Field t = new Field();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            BlockPos pool = new BlockPos(x, 63, z);
            t.holes.add(pool);
            t.lava.add(pool);
        }
        PortalCast.Found found = PortalCast.find(new BlockPos(0, 64, 0), 12, 24, Set.of(), t);
        assertNotNull(found);
        PortalCast cast = found.cast();
        List<BlockPos> used = new java.util.ArrayList<>(cast.open());
        used.addAll(cast.wall());
        used.addAll(cast.ground());
        for (BlockPos pos : used) {
            for (BlockPos l : t.lava) {
                int apart = Math.max(Math.max(Math.abs(pos.getX() - l.getX()), Math.abs(pos.getY() - l.getY())),
                        Math.abs(pos.getZ() - l.getZ()));
                assertTrue(apart > PortalCast.LAVA_CLEARANCE, pos.toShortString() + " is " + apart + " from lava");
            }
        }
    }

    @Test
    void aHillToDigIntoCostsDigsAndNoMould() {
        Field t = new Field();
        // Solid rock everywhere up to y 72: the portal must be dug in.
        for (int x = -12; x <= 12; x++) for (int y = 64; y <= 72; y++) for (int z = -12; z <= 12; z++) t.solid.add(new BlockPos(x, y, z));
        assertNull(PortalCast.find(new BlockPos(0, 64, 0), 3, 24, Set.of(), t), "more digging than allowed");
        PortalCast.Found found = PortalCast.find(new BlockPos(0, 64, 0), 3, 100, Set.of(), t);
        assertNotNull(found);
        assertEquals(found.cast().open().size(), found.digs());
        assertEquals(0, found.places());
    }

    @Test
    void gravelIsNeitherDugNorDugUnder() {
        Field t = new Field();
        // A gravel hill: sites in it would dig gravel, sites below it would dig out from under it.
        for (int x = -12; x <= 12; x++) for (int y = 64; y <= 72; y++) for (int z = -12; z <= 12; z++) t.gravel.add(new BlockPos(x, y, z));
        assertNull(PortalCast.find(new BlockPos(0, 64, 0), 3, 100, Set.of(), t));
    }
}
