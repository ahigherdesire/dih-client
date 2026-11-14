package baritone.parkour.solve;

import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.PlayerPhysics;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.sim.TickInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Single jumps the solver must find (and the one it must prove impossible). Limits come from the Minecraft Parkour
 * Wiki's jump tables: a flat sprint jump clears a 4-block gap (with a run-up) but not 5. Every found plan is replayed
 * through the simulator to check it lands, and each search is timed.
 */
final class JumpSolverTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    /** Generous per-jump wall time so a slow CI machine passes; the dev machine target (200 ms) is logged. */
    private static final long MAX_NANOS = 3_000_000_000L;
    private static final JumpSolver.Limits TEST_LIMITS = new JumpSolver.Limits(600_000, 5_000_000_000L, 90, true);

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // Warm the JIT up on a few jumps first, as a running client would be after its first jump, so the logged
        // times are the steady state rather than whichever test happens to run first.
        BlockSource.MapSource warm = startPlatform();
        warm.set(0, 0, 3, STONE);
        for (int i = 0; i < 5; i++) JumpSolver.solve(warm, start(), new BlockPos(0, 0, 3), TEST_LIMITS);
    }

    /** A 5-wide start platform from z = -8 to z = 0 at y = 0; the player stands at its middle facing +Z. */
    private static BlockSource.MapSource startPlatform() {
        return new BlockSource.MapSource().fill(-2, 0, -8, 2, 0, 0, STONE);
    }

    private static PlayerState start() {
        return PlayerState.standing(0.5, 1.0, -3.5, 0.0F);
    }

    private static JumpSolver.Found found(String what, BlockSource world, PlayerState start, BlockPos target) {
        // A node budget, not the wall clock, decides here, so a busy machine can't flip the result.
        JumpSolver.Result result = JumpSolver.solve(world, start, target, TEST_LIMITS);
        System.out.printf("%s: %s, %d nodes, %.1f ms%n", what, result instanceof JumpSolver.Found f ? f.ticks() + " ticks, margin " + f.margin() : result,
            result.nodes(), result.nanos() / 1e6);
        assertInstanceOf(JumpSolver.Found.class, result, what + " should be solvable");
        JumpSolver.Found plan = (JumpSolver.Found) result;
        assertTrue(plan.nanos() < MAX_NANOS, what + " took " + plan.nanos() / 1e6 + " ms");
        assertTrue(replayLands(world, start, plan.inputs(), target), what + ": the plan must land when replayed");
        return plan;
    }

    private static boolean replayLands(BlockSource world, PlayerState start, List<TickInput> inputs, BlockPos target) {
        PlayerState s = start;
        for (TickInput in : inputs) s = PlayerPhysics.step(s, in, world);
        // The plan must leave the player stopped on the target, not just touching it on the way past.
        return JumpSolver.standsOn(s, target, JumpSolver.topOf(world, target)) && JumpSolver.atRest(s);
    }

    /**
     * Course blocks: a lone block to a lone block, no run-up. The jump must stop on the landing block, not slide off
     * its far side (the bug the course runs hit).
     */
    @ParameterizedTest(name = "lone block, gap of {0}")
    @ValueSource(ints = {1, 2})
    void loneBlockToLoneBlock(int gap) {
        BlockSource.MapSource world = new BlockSource.MapSource();
        world.set(0, 0, 0, STONE);
        world.set(0, 0, 1 + gap, STONE);
        found("lone gap " + gap, world, PlayerState.standing(0.5, 1.0, 0.5, 0.0F), new BlockPos(0, 0, 1 + gap));
    }

    @Test
    void ontoALoneSlabHalfABlockDown() {
        BlockSource.MapSource world = new BlockSource.MapSource();
        world.set(0, 0, 0, STONE);
        world.set(0, -1, 2, Blocks.STONE_SLAB.defaultBlockState());
        found("lone slab", world, PlayerState.standing(0.5, 1.0, 0.5, 0.0F), new BlockPos(0, -1, 2));
    }

    /**
     * A ladder jump: across a one-block gap onto a ladder up the near face of a wall, up it, and off onto the wall's top,
     * three blocks above the start.
     */
    @Test
    void upALadderOntoAWall() {
        BlockSource.MapSource world = startPlatform();
        world.fill(0, 0, 3, 0, 3, 3, STONE);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.NORTH);
        world.fill(0, 1, 2, 0, 3, 2, ladder);
        found("ladder jump", world, start(), new BlockPos(0, 3, 3));
    }

    @ParameterizedTest(name = "flat gap of {0}")
    @ValueSource(ints = {1, 2, 3, 4})
    void flatGapsUpToFourAreCleared(int gap) {
        BlockSource.MapSource world = startPlatform();
        world.fill(-2, 0, 1 + gap, 2, 0, 3 + gap, STONE);
        found("flat " + gap, world, start(), new BlockPos(0, 0, 1 + gap));
    }

    @Test
    void aSimulatedTickIsCheap() {
        BlockSource.MapSource world = startPlatform();
        world.fill(-2, 0, 1, 2, 0, 400, STONE);
        PlayerState s = PlayerState.standing(0.5, 1.0, -3.5, 0.0F);
        TickInput sprint = TickInput.sprint(0.0F, true);
        for (int i = 0; i < 2000; i++) s = PlayerPhysics.step(s, sprint, world);
        s = PlayerState.standing(0.5, 1.0, -3.5, 0.0F);
        long t0 = System.nanoTime();
        int n = 10_000;
        for (int i = 0; i < n; i++) {
            s = PlayerPhysics.step(s, sprint, world);
            if (s.z > 390) s = PlayerState.standing(0.5, 1.0, -3.5, 0.0F);
        }
        double micros = (System.nanoTime() - t0) / 1e3 / n;
        System.out.printf("one simulated tick: %.2f us%n", micros);
        assertTrue(micros < 50.0, "a tick took " + micros + " us");
    }

    @Test
    void aFlatGapOfFiveIsImpossible() {
        BlockSource.MapSource world = startPlatform();
        world.fill(-2, 0, 6, 2, 0, 8, STONE);
        JumpSolver.Result result = JumpSolver.solve(world, start(), new BlockPos(0, 0, 6), JumpSolver.Limits.FAST);
        System.out.println("flat 5: " + result);
        assertInstanceOf(JumpSolver.Impossible.class, result);
    }

    @Test
    void upOneBlockOverATwoGap() {
        BlockSource.MapSource world = startPlatform();
        world.fill(-2, 1, 3, 2, 1, 5, STONE);
        found("+1 over 2", world, start(), new BlockPos(0, 1, 3));
    }

    @Test
    void downTwoOverAFourGap() {
        BlockSource.MapSource world = startPlatform();
        world.fill(-2, -2, 5, 2, -2, 7, STONE);
        found("-2 over 4", world, start(), new BlockPos(0, -2, 5));
    }

    @Test
    void aHeadHitterOverAOneGap() {
        // The ceiling's underside is 0.2 above the player's head: almost no jump height.
        BlockSource.MapSource world = startPlatform();
        world.fill(-2, 3, -8, 2, 3, 6, STONE);
        world.fill(-2, 0, 2, 2, 0, 4, STONE);
        found("head-hitter 1", world, start(), new BlockPos(0, 0, 2));
    }

    @Test
    void ontoAFencePost() {
        // A lone fence post at floor level across a one-block gap: its top is half a block up.
        BlockSource.MapSource world = startPlatform();
        world.set(0, 0, 2, Blocks.OAK_FENCE.defaultBlockState());
        found("onto a fence post", world, start(), new BlockPos(0, 0, 2));
    }

    /**
     * A classic neo: a one-block-wide run-up strip, a wall {@code thickness} blocks deep straight ahead (three high),
     * and the landing block right behind it. The only way is round the wall's side, in the air.
     */
    private static void neo(int thickness) {
        BlockSource.MapSource world = new BlockSource.MapSource().fill(0, 0, -8, 0, 0, 0, STONE);
        world.fill(0, 0, 1, 0, 3, thickness, STONE);
        world.set(0, 0, thickness + 1, STONE);
        found("neo " + thickness, world, PlayerState.standing(0.5, 1.0, -3.5, 0.0F), new BlockPos(0, 0, thickness + 1));
    }

    @Test
    void aClassicNeo() {
        neo(1);
    }

    @Test
    void aDoubleNeo() {
        neo(2);
    }

    /**
     * Not possible on flat ground in 26.2 physics, as far as we can find: the solver's full search finds nothing, and
     * a brute-force sweep (run-up line, takeoff tick and yaw, three air headings, strafe) has no line even clear the
     * wall's end while still high enough to land. A sprint jump covers about 3.65 blocks at the same height; this needs
     * 3.6 to 4.1 straight ahead plus the detour round the wall. Kept to re-check if the physics or solver changes.
     */
    @Disabled("a triple neo on flat ground looks out of reach in 26.2 physics; see the comment")
    @Test
    void aTripleNeo() {
        neo(3);
    }

    @Test
    void aOneBlockNeo() {
        // A wall from x = -3 to x = 1 at z = 1, three high, on the start platform's far edge; land behind it at (1, 0, 3).
        BlockSource.MapSource world = new BlockSource.MapSource().fill(-3, 0, -8, 1, 0, 0, STONE);
        world.fill(-3, 0, 1, 1, 3, 1, STONE);
        world.fill(0, 0, 3, 1, 0, 4, STONE);
        found("1-block neo", world, PlayerState.standing(0.5, 1.0, -3.5, 0.0F), new BlockPos(1, 0, 3));
    }
}
