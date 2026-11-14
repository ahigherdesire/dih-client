package baritone.parkour.course;

import baritone.parkour.sim.BlockSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which jumps the course graph offers: a wall top three up is offered only with a ladder up to it. */
final class CourseGraphTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    private static BlockSource.MapSource wall() {
        return new BlockSource.MapSource().fill(-2, 0, -8, 2, 0, 0, STONE).fill(0, 0, 3, 0, 3, 3, STONE);
    }

    @Test
    void aWallTopIsOutOfReachWithoutALadder() {
        CourseGraph graph = new CourseGraph(wall());
        assertFalse(graph.usable(new BlockPos(0, 0, 0), new BlockPos(0, 3, 3)));
    }

    @Test
    void aLadderUpTheWallPutsItsTopInReach() {
        BlockSource.MapSource world = wall();
        world.fill(0, 1, 2, 0, 3, 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        CourseGraph graph = new CourseGraph(world);
        assertTrue(graph.usable(new BlockPos(0, 0, 0), new BlockPos(0, 3, 3)));
        // The ladder itself isn't a spot to stand on.
        assertFalse(graph.standable(new BlockPos(0, 3, 2)));
    }
}
