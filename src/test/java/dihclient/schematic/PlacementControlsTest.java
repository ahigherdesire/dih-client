package dihclient.schematic;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlacementControlsTest {

    @Test
    void depthStaysWithinItsLimits() {
        assertEquals(7.0, PlacementControls.depth(6.0, 1.0));
        assertEquals(PlacementControls.MIN_DEPTH, PlacementControls.depth(1.0, -5.0));
        // no survival reach limit: far placements are allowed
        assertEquals(129.0, PlacementControls.depth(128.0, 1.0));
        assertEquals(PlacementControls.MAX_DEPTH, PlacementControls.depth(PlacementControls.MAX_DEPTH, 10.0));
    }

    @Test
    void pointsAlongTheRayUseItsDirectionOnly() {
        Vec3 point = PlacementControls.along(new Vec3(1, 2, 3), new Vec3(0, 0, 10), 4);
        assertEquals(new Vec3(1, 2, 7), point);
    }

    @Test
    void theRayMeetsAHeightOrFallsBack() {
        Vec3 eye = new Vec3(0, 70, 0);
        // distances are in units of the ray's own length; this ray drops one block per unit
        Vec3 down = new Vec3(0, -1, 1);
        assertEquals(10.0, PlacementControls.distanceToHeight(eye, down, 60, 6.0), 1e-9);
        // looking up never reaches a height below
        assertEquals(6.0, PlacementControls.distanceToHeight(eye, new Vec3(0, 1, 1), 60, 6.0));
        // level: never
        assertEquals(6.0, PlacementControls.distanceToHeight(eye, new Vec3(1, 0, 0), 60, 6.0));
        // too close
        assertEquals(6.0, PlacementControls.distanceToHeight(eye, down, 69.9, 6.0));
    }
}
