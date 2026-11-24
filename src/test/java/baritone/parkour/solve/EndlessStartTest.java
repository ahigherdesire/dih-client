package baritone.parkour.solve;

import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.PlayerState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The first jump of the Endless Procedural Parkour map, as the block dump from the game showed it: a three-wide start
 * strip ending in a gold line at z = 0, a two-block gap, and three pink concrete blocks at z = 3..5 with barriers under
 * them. The solver ran out of time on this in the game; it's a plain two-block gap.
 */
final class EndlessStartTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void theFirstJumpOfEndless() {
        BlockState wall = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockSource.MapSource w = new BlockSource.MapSource();
        w.fill(6, 0, -8, 8, 0, -1, Blocks.STONE.defaultBlockState());
        w.fill(6, 0, 0, 8, 0, 0, Blocks.GOLD_BLOCK.defaultBlockState());
        w.fill(5, 0, -8, 5, 0, 0, wall);
        w.fill(9, 0, -8, 9, 0, 0, wall);
        w.fill(6, -1, -8, 8, -1, 0, Blocks.STONE.defaultBlockState());
        w.fill(6, 0, 3, 8, 0, 5, Blocks.STONE.defaultBlockState());
        w.fill(6, -1, 3, 8, -1, 5, Blocks.BARRIER.defaultBlockState());
        PlayerState start = PlayerState.standing(7.5, 1.0, 0.5, 0.0F);
        JumpSolver.Result r = JumpSolver.solve(w, start, new BlockPos(7, 0, 3), JumpSolver.Limits.THOROUGH);
        System.out.println("endless first jump: " + r);
        assertInstanceOf(JumpSolver.Found.class, r);
    }
}
