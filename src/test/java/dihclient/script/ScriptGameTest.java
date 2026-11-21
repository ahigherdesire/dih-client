package dihclient.script;

import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptGameTest {
    @Test void interactingWithChestUsesActualInsetFace() {
        AABB chest = new AABB(1.0 / 16, 0, 1.0 / 16, 15.0 / 16, 14.0 / 16, 15.0 / 16);
        assertEquals(new Vec3(10.5, 64.875, 20.5), ScriptGame.facePoint(new BlockPos(10, 64, 20), chest, Direction.UP));
        assertEquals(new Vec3(10.9375, 64.4375, 20.5), ScriptGame.facePoint(new BlockPos(10, 64, 20), chest, Direction.EAST));
    }
    @Test void supportsOnlyExplicitHands() {
        assertEquals(InteractionHand.OFF_HAND, ScriptGame.hand("offhand"));
        assertEquals(InteractionHand.MAIN_HAND, ScriptGame.hand("mainhand"));
        assertThrows(IllegalArgumentException.class, () -> ScriptGame.hand("bad"));
    }
    @Test void hostileDoesNotMeanAttacking() {
        assertEquals(false, ScriptGame.threatState(true, false, false).get("attacking"));
        assertEquals(false, ScriptGame.threatState(true, true, false).get("attacking"));
        assertEquals(true, ScriptGame.threatState(false, false, true).get("attacking"));
        assertEquals(true, ScriptGame.threatState(false, true, false).get("targeting"));
    }
}
