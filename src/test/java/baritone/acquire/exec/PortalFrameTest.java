package baritone.acquire.exec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalFrameTest {

    private static final BlockPos GROUND = new BlockPos(10, 63, -4);

    @Test
    void tenObsidianAndFourCornerBlocks() {
        List<PortalFrame.Placement> order = PortalFrame.placements(GROUND);
        assertEquals(10, order.stream().filter(p -> p.obsidian()).count());
        assertEquals(4, order.stream().filter(p -> !p.obsidian()).count());
    }

    @Test
    void everyBlockIsPlacedAgainstGroundOrABlockAlreadyPlaced() {
        Set<BlockPos> solid = new HashSet<>();
        for (int dx = 0; dx < 4; dx++) solid.add(GROUND.east(dx));
        for (PortalFrame.Placement p : PortalFrame.placements(GROUND)) {
            BlockPos support = p.pos().relative(p.against());
            assertTrue(solid.contains(support), p + " has nothing to be placed against");
            assertFalse(solid.contains(p.pos()), p + " is already filled");
            solid.add(p.pos());
        }
    }

    @Test
    void theFrameIsFourWideFiveTallWithAnOpenTwoByThreeInside() {
        Set<BlockPos> placed = new HashSet<>();
        for (PortalFrame.Placement p : PortalFrame.placements(GROUND)) placed.add(p.pos());
        for (BlockPos inside : PortalFrame.interior(GROUND)) assertFalse(placed.contains(inside), inside + " must stay open");
        assertEquals(6, PortalFrame.interior(GROUND).size());
        for (BlockPos inside : PortalFrame.interior(GROUND)) {
            int dx = inside.getX() - GROUND.getX();
            int dy = inside.getY() - GROUND.getY();
            assertTrue(dx >= 1 && dx <= 2 && dy >= 2 && dy <= 4 && inside.getZ() == GROUND.getZ(), inside.toString());
        }
        // Obsidian all round the opening: below, above and both sides of it.
        for (int dx = 1; dx <= 2; dx++) {
            assertTrue(obsidianAt(GROUND.offset(dx, 1, 0)) && obsidianAt(GROUND.offset(dx, 5, 0)));
        }
        for (int dy = 2; dy <= 4; dy++) {
            assertTrue(obsidianAt(GROUND.offset(0, dy, 0)) && obsidianAt(GROUND.offset(3, dy, 0)));
        }
    }

    @Test
    void theFlameGoesOnTopOfABottomBlockAndTheStandingSpotFacesTheOpening() {
        assertEquals(GROUND.offset(1, 1, 0), PortalFrame.ignite(GROUND));
        assertEquals(GROUND.offset(1, 1, 2), PortalFrame.stand(GROUND));
        assertEquals(Direction.DOWN, PortalFrame.placements(GROUND).get(0).against());
    }

    @Test
    void theSiteNeedsGroundUnderTheFrameAndTheWayInAndAirAbove() {
        Set<BlockPos> needSolid = new HashSet<>(PortalFrame.ground(GROUND));
        Set<BlockPos> needAir = new HashSet<>(PortalFrame.clearance(GROUND));
        assertEquals(12, needSolid.size(), "4 under the frame, 4 under each of the two rows in front");
        for (PortalFrame.Placement p : PortalFrame.placements(GROUND)) assertTrue(needAir.contains(p.pos()), p.toString());
        assertTrue(needAir.containsAll(PortalFrame.interior(GROUND)));
        assertTrue(needAir.contains(PortalFrame.stand(GROUND)) && needAir.contains(PortalFrame.stand(GROUND).above()));
        needSolid.retainAll(needAir);
        assertTrue(needSolid.isEmpty(), "nothing is both: " + needSolid);
    }

    private static boolean obsidianAt(BlockPos pos) {
        return PortalFrame.placements(GROUND).stream().anyMatch(p -> p.obsidian() && p.pos().equals(pos));
    }
}
