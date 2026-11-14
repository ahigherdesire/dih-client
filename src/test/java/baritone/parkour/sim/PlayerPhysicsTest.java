package baritone.parkour.sim;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The simulator against movement facts every parkour player knows (Minecraft Parkour Wiki, "Movement formulas" and
 * "Jump" pages): walking settles at 0.21585 blocks/tick and sprinting at 0.2806, a standing jump peaks 1.2522 blocks up
 * and lands on flat ground on its 12th tick, sneaking stops at an edge, a ladder is climbed at 0.2 blocks/tick.
 */
final class PlayerPhysicsTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    /** A stone floor at y = 0 from x -50..50, z -50..50; players stand at y = 1. */
    static BlockSource.MapSource floor() {
        return new BlockSource.MapSource().fill(-50, 0, -50, 50, 0, 50, Blocks.STONE.defaultBlockState());
    }

    private static PlayerState run(PlayerState start, TickInput input, int ticks, BlockSource world) {
        PlayerState s = start;
        for (int i = 0; i < ticks; i++) s = PlayerPhysics.step(s, input, world);
        return s;
    }

    @Test
    void standingStillStaysPut() {
        PlayerState s = run(PlayerState.standing(0.5, 1.0, 0.5, 0.0F), TickInput.none(0.0F), 40, floor());
        assertEquals(0.5, s.x, 1e-12);
        assertEquals(1.0, s.y, 1e-12);
        assertTrue(s.onGround);
    }

    @Test
    void walkingSettlesAtTheKnownSpeed() {
        BlockSource world = floor();
        TickInput walk = new TickInput(true, false, false, false, false, false, false, 0.0F);
        PlayerState s = run(PlayerState.standing(0.5, 1.0, -40.0, 0.0F), walk, 60, world);
        PlayerState next = PlayerPhysics.step(s, walk, world);
        // yaw 0 faces +Z
        assertEquals(0.21585, next.z - s.z, 1e-4);
        assertEquals(0.0, next.x - s.x, 1e-9);
    }

    @Test
    void sprintingSettlesAtTheKnownSpeed() {
        BlockSource world = floor();
        TickInput sprint = TickInput.sprint(0.0F, false);
        PlayerState s = run(PlayerState.standing(0.5, 1.0, -40.0, 0.0F), sprint, 60, world);
        PlayerState next = PlayerPhysics.step(s, sprint, world);
        assertTrue(next.sprinting);
        assertEquals(0.2806, next.z - s.z, 1e-4);
    }

    @Test
    void aStandingJumpPeaksAt1Point2522AndLandsOnTick12() {
        BlockSource world = floor();
        PlayerState s = PlayerState.standing(0.5, 1.0, 0.5, 0.0F);
        double peak = 0;
        int landedAt = -1;
        for (int tick = 1; tick <= 20; tick++) {
            s = PlayerPhysics.step(s, TickInput.none(0.0F).withJump(tick == 1), world);
            peak = Math.max(peak, s.y - 1.0);
            if (tick > 1 && s.onGround) {
                landedAt = tick;
                break;
            }
        }
        assertEquals(1.2522, peak, 1e-4);
        assertEquals(12, landedAt);
        assertEquals(1.0, s.y, 1e-9);
    }

    @Test
    void sneakingNeverWalksOffAnEdge() {
        BlockSource world = new BlockSource.MapSource().fill(0, 0, 0, 0, 0, 5, Blocks.STONE.defaultBlockState());
        TickInput sneakSideways = new TickInput(false, false, true, false, false, false, true, 0.0F);
        PlayerState s = run(PlayerState.standing(0.5, 1.0, 2.5, 0.0F), sneakSideways, 80, world);
        assertTrue(s.onGround, "still on the block");
        assertEquals(1.0, s.y, 1e-9);
        // The edge stop leaves the box overhanging by at most 0.3 (half the width).
        assertTrue(s.x <= 1.3 && s.x >= 1.0, "stopped at the edge, x=" + s.x);
    }

    @Test
    void stepsUpAHalfSlabButNotAFullBlock() {
        BlockSource.MapSource world = floor();
        world.fill(-2, 1, 3, 2, 1, 30, Blocks.STONE_SLAB.defaultBlockState());
        TickInput walk = new TickInput(true, false, false, false, false, false, false, 0.0F);
        PlayerState s = run(PlayerState.standing(0.5, 1.0, 0.5, 0.0F), walk, 30, world);
        assertTrue(s.z > 3.5, "walked onto the slab");
        assertEquals(1.5, s.y, 1e-9);

        BlockSource.MapSource wall = floor();
        wall.set(0, 1, 3, Blocks.STONE.defaultBlockState());
        PlayerState blocked = run(PlayerState.standing(0.5, 1.0, 0.5, 0.0F), walk, 30, wall);
        assertEquals(2.7, blocked.z, 1e-6);
        assertTrue(blocked.horizontalCollision);
    }

    @Test
    void aLadderIsClimbedAt0Point2PerTick() {
        BlockSource.MapSource world = floor();
        for (int y = 1; y <= 10; y++) {
            world.set(0, y, 1, Blocks.STONE.defaultBlockState());
            world.set(0, y, 0, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        TickInput into = new TickInput(true, false, false, false, false, false, false, 0.0F);
        PlayerState s = run(PlayerState.standing(0.5, 1.0, 0.5, 0.0F), into, 20, world);
        PlayerState next = PlayerPhysics.step(s, into, world);
        // (0.2 - gravity 0.08) * drag 0.98 = 0.1176 blocks/tick, the known 2.35 m/s.
        assertEquals(0.1176, next.y - s.y, 1e-4, "climbing");
    }

    @Test
    void soulSandSlowsWalking() {
        BlockSource.MapSource world = new BlockSource.MapSource().fill(-50, 0, -50, 50, 0, 50, Blocks.SOUL_SAND.defaultBlockState());
        TickInput walk = new TickInput(true, false, false, false, false, false, false, 0.0F);
        PlayerState s = run(PlayerState.standing(0.5, 0.875, -40.0, 0.0F), walk, 60, world);
        PlayerState next = PlayerPhysics.step(s, walk, world);
        double speed = next.z - s.z;
        // Slower than walking on stone (0.21585).
        assertTrue(speed < 0.2 && speed > 0.05, "soul sand speed " + speed);
        assertFalse(next.sprinting);
    }
}
