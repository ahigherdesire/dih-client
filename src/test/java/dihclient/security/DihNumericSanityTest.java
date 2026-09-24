package dihclient.security;

import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihNumericSanityTest {
    @Test
    void legitimateValuesAreInRange() {
        assertFalse(DihNumericSanity.outOfRange(0.0));
        assertFalse(DihNumericSanity.outOfRange(3.0e7));
        assertFalse(DihNumericSanity.outOfRange(-3.0e7));
        assertFalse(DihNumericSanity.outOfRange(new Vec3(100.5, 64.0, -2000.0)));
        assertFalse(DihNumericSanity.motionOutOfRange(100.0));
        assertFalse(DihNumericSanity.motionOutOfRange(-99.9));
    }

    @Test
    void boundaryValuesAreInRange() {
        assertFalse(DihNumericSanity.outOfRange(DihNumericSanity.SANE_LIMIT));
        assertFalse(DihNumericSanity.outOfRange(-DihNumericSanity.SANE_LIMIT));
        assertFalse(DihNumericSanity.motionOutOfRange(DihNumericSanity.MAX_MOTION_PER_AXIS));
        assertFalse(DihNumericSanity.motionOutOfRange(-DihNumericSanity.MAX_MOTION_PER_AXIS));
    }

    @Test
    void absurdValuesAreOutOfRange() {
        assertTrue(DihNumericSanity.outOfRange(Double.NaN));
        assertTrue(DihNumericSanity.outOfRange(Double.POSITIVE_INFINITY));
        assertTrue(DihNumericSanity.outOfRange(Double.NEGATIVE_INFINITY));
        assertTrue(DihNumericSanity.outOfRange(1.8e38));
        assertTrue(DihNumericSanity.outOfRange(-2.8e38));
        assertTrue(DihNumericSanity.outOfRange(DihNumericSanity.SANE_LIMIT * 1.0001));
        assertTrue(DihNumericSanity.motionOutOfRange(1.0e6));
        assertTrue(DihNumericSanity.motionOutOfRange(Double.NaN));
        assertTrue(DihNumericSanity.motionOutOfRange(Double.POSITIVE_INFINITY));
    }

    @Test
    void vectorsAndChangesAreCheckedComponentWise() {
        assertTrue(DihNumericSanity.outOfRange(null));
        assertTrue(DihNumericSanity.outOfRange(new Vec3(0.0, 1.0e38, 0.0)));
        assertTrue(DihNumericSanity.motionOutOfRange(null));
        assertTrue(DihNumericSanity.motionOutOfRange(new Vec3(0.0, 0.0, 1.0e38)));

        PositionMoveRotation fine = new PositionMoveRotation(new Vec3(64.0, 70.0, -12.0), new Vec3(0.0, 0.4, 0.0), 0.0f, 0.0f);
        assertFalse(DihNumericSanity.positionMoveOutOfRange(fine));

        PositionMoveRotation badPosition = new PositionMoveRotation(new Vec3(1.0e38, 0.0, 0.0), Vec3.ZERO, 0.0f, 0.0f);
        assertTrue(DihNumericSanity.positionMoveOutOfRange(badPosition));

        PositionMoveRotation badDelta = new PositionMoveRotation(Vec3.ZERO, new Vec3(0.0, 1.0e38, 0.0), 0.0f, 0.0f);
        assertTrue(DihNumericSanity.positionMoveOutOfRange(badDelta));

        assertTrue(DihNumericSanity.positionMoveOutOfRange(null));
    }
}
