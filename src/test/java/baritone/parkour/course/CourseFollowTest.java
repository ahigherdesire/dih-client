package baritone.parkour.course;

import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.PlayerPhysics;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.sim.TickInput;
import baritone.parkour.solve.JumpChain;
import baritone.parkour.solve.JumpSolver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whole courses followed headlessly, the way {@code #parkour} follows them: pick the next spot, solve the jump, replay
 * it through the simulator, repeat. The courses are the shapes real maps have and the void test courses don't: turns,
 * a switchback, a floor under the course, a block behind the start.
 */
final class CourseFollowTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
    private static final JumpSolver.Limits LIMITS = new JumpSolver.Limits(600_000, 5_000_000_000L, 90, true);

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    /** What a follow run did. */
    record Run(boolean finished, List<BlockPos> targets, String end, float worstTurn, int backwardTicks, int ticks, int stops) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s after %d jumps, %d ticks, %d stops (%s); worst per-tick turn %.0f deg; %d ticks facing >100 deg off the movement; targets %s",
                finished ? "finished" : "NOT finished", targets.size(), ticks, stops, end, worstTurn, backwardTicks,
                targets.stream().map(BlockPos::toShortString).toList());
        }
    }

    /** The inputs played and how the camera moved, across a whole run. */
    private static final class Tape {
        PlayerState s;
        float lastYaw;
        float worstTurn;
        int backward;
        int ticks;

        Tape(PlayerState start) {
            s = start;
            lastYaw = start.yaw;
        }

        void play(BlockSource world, List<TickInput> inputs) {
            for (TickInput in : inputs) {
                worstTurn = Math.max(worstTurn, Math.abs(net.minecraft.util.Mth.wrapDegrees(in.yaw() - lastYaw)));
                lastYaw = in.yaw();
                double speed = Math.sqrt(s.vx * s.vx + s.vz * s.vz);
                if (speed > 0.05) {
                    float moving = (float) Math.toDegrees(Math.atan2(-s.vx, s.vz));
                    if (Math.abs(net.minecraft.util.Mth.wrapDegrees(in.yaw() - moving)) > 100) backward++;
                }
                s = PlayerPhysics.step(s, in, world);
                ticks++;
            }
        }
    }

    static Run follow(BlockSource.MapSource world, PlayerState start, BlockPos finish) {
        return follow(world, start, finish, false);
    }

    /**
     * Follows from {@code start} until the gold block, a miss, or 40 jumps. Mirrors ParkourProcess's follow loop: with
     * {@code chain}, each jump after the first is solved from the last one's touchdown and taken without stopping.
     */
    static Run follow(BlockSource.MapSource world, PlayerState start, BlockPos finish, boolean chain) {
        CourseFollower follower = new CourseFollower(world, start.yaw);
        Tape tape = new Tape(start);
        List<BlockPos> targets = new ArrayList<>();
        int stops = 0;
        JumpChain.Link link = null;
        for (int jump = 0; jump < 40; jump++) {
            PlayerState s = tape.s;
            BlockPos at = s.support != null ? s.support : BlockPos.containing(s.x, s.y - 0.5, s.z);
            if (at.equals(finish)) return run(true, targets, "on the finish", tape, stops);
            BlockPos next;
            List<TickInput> plan;
            if (link != null) {
                next = link.target();
                plan = link.inputs();
            } else {
                stops++;
                next = follower.next(at);
                if (next == null) return run(false, targets, "nothing ahead from " + at.toShortString(), tape, stops);
                JumpSolver.Result result = JumpSolver.solve(world, s, next, LIMITS);
                if (!(result instanceof JumpSolver.Found found)) {
                    follower.infeasible(at, next);
                    jump--;
                    stops--;
                    continue;
                }
                plan = found.inputs();
            }
            targets.add(next);
            link = chain ? JumpChain.link(world, s, plan, next, follower.peekAfter(next), JumpChain.LIMITS) : null;
            if (link != null) {
                tape.play(world, plan.subList(0, link.index()));
                assertTrue(JumpChain.matches(tape.s, link.from()), "replay drifted from the link");
            } else {
                tape.play(world, plan);
            }
            if (!JumpSolver.standsOn(tape.s, next, JumpSolver.topOf(world, next))) {
                return run(false, targets, "missed " + next.toShortString(), tape, stops);
            }
            follower.landed(next);
        }
        return run(false, targets, "40 jumps", tape, stops);
    }

    private static Run run(boolean finished, List<BlockPos> targets, String end, Tape tape, int stops) {
        return new Run(finished, targets, end, tape.worstTurn, tape.backward, tape.ticks, stops);
    }

    /** Finished on the gold block, never facing back against the movement, no turn past the solver's limit in a tick. */
    private static void check(String course, Run run) {
        System.out.println(course + ": " + run);
        assertTrue(run.finished(), course + ": " + run);
        assertEquals(0, run.backwardTicks(), course + ": looked back. " + run);
        assertTrue(run.worstTurn() <= 30.01F, course + ": turned " + run.worstTurn() + " deg in one tick. " + run);
    }

    /** The same course chained: it must finish too, stop only at the start, and take fewer ticks. */
    private static void checkChained(String course, BlockSource.MapSource world, PlayerState start, BlockPos finish, Run stopping) {
        Run run = follow(world, start, finish, true);
        System.out.println(course + " chained: " + run);
        check(course + " chained", run);
        assertEquals(1, run.stops(), course + " chained: stopped between jumps. " + run);
        assertTrue(run.ticks() < stopping.ticks(), course + " chained took " + run.ticks() + " ticks, stopping took " + stopping.ticks());
    }

    private static PlayerState startOn(int x, int y, int z, float yaw) {
        return PlayerState.standing(x + 0.5, y + 1.0, z + 0.5, yaw);
    }

    /** A start platform 3 wide along -z behind (x, y, z). */
    private static BlockSource.MapSource platform(int x, int y, int z) {
        return new BlockSource.MapSource().fill(x - 1, y, z - 3, x + 1, y, z, STONE);
    }

    @Test
    void straightCourse() {
        BlockSource.MapSource w = platform(0, 0, 0);
        w.set(0, 0, 3, STONE).set(0, 0, 6, STONE).set(0, 0, 9, STONE).set(0, 0, 12, GOLD);
        Run run = follow(w, startOn(0, 0, -1, 0.0F), new BlockPos(0, 0, 12));
        check("straight", run);
        checkChained("straight", w, startOn(0, 0, -1, 0.0F), new BlockPos(0, 0, 12), run);
    }

    @Test
    void rightAngleTurn() {
        BlockSource.MapSource w = platform(0, 0, 0);
        // +z three jumps, then the course turns to -x.
        w.set(0, 0, 3, STONE).set(0, 0, 6, STONE).set(-3, 0, 6, STONE).set(-6, 0, 6, STONE).set(-9, 0, 6, GOLD);
        Run run = follow(w, startOn(0, 0, -1, 0.0F), new BlockPos(-9, 0, 6));
        check("right angle", run);
        checkChained("right angle", w, startOn(0, 0, -1, 0.0F), new BlockPos(-9, 0, 6), run);
    }

    @Test
    void switchback() {
        BlockSource.MapSource w = platform(0, 0, 0);
        // Out along +z, across, and back along -z two rows over (out of reach of the start platform).
        w.set(0, 0, 3, STONE).set(0, 0, 6, STONE).set(3, 0, 6, STONE).set(6, 0, 6, STONE).set(6, 0, 3, STONE)
            .set(6, 0, 0, STONE).set(7, 0, -3, GOLD);
        Run run = follow(w, startOn(0, 0, -1, 0.0F), new BlockPos(7, 0, -3));
        check("switchback", run);
        checkChained("switchback", w, startOn(0, 0, -1, 0.0F), new BlockPos(7, 0, -3), run);
    }

    @Test
    void floorUnderTheCourse() {
        BlockSource.MapSource w = platform(0, 0, 0);
        w.fill(-8, -3, -8, 8, -3, 20, STONE);
        w.set(0, 0, 3, STONE).set(0, 0, 6, STONE).set(0, 0, 9, STONE).set(0, 0, 12, GOLD);
        Run run = follow(w, startOn(0, 0, -1, 0.0F), new BlockPos(0, 0, 12));
        check("floor under", run);
        checkChained("floor under", w, startOn(0, 0, -1, 0.0F), new BlockPos(0, 0, 12), run);
    }

    @Test
    void blockBehindTheStart() {
        BlockSource.MapSource w = new BlockSource.MapSource().set(0, 0, 0, STONE);
        w.set(0, 0, -3, STONE); // decoration behind the player
        w.set(0, 0, 3, STONE).set(0, 0, 6, STONE).set(0, 0, 9, GOLD);
        Run run = follow(w, startOn(0, 0, 0, 0.0F), new BlockPos(0, 0, 9));
        check("block behind", run);
        checkChained("block behind", w, startOn(0, 0, 0, 0.0F), new BlockPos(0, 0, 9), run);
    }

    /**
     * Not a pass/fail test: whether a triple neo (a wall three deep, landing right behind it) can be made when the
     * player arrives already moving, as a chained jump does. Starts on the last block before the wall at a range of
     * speeds and positions and logs what the solver finds.
     */
    @Test
    void tripleNeoWithSpeedProbe() {
        BlockSource.MapSource w = new BlockSource.MapSource().fill(0, 0, -8, 0, 0, 0, STONE);
        w.fill(0, 0, 1, 0, 3, 3, STONE);
        w.set(0, 0, 4, STONE);
        int found = 0;
        for (double v : new double[]{0.28, 0.32, 0.36, 0.40, 0.45}) {
            for (double z : new double[]{-1.5, -0.5, 0.2, 0.6}) {
                PlayerState s = PlayerState.standing(0.5, 1.0, z, 0.0F);
                s.vz = v;
                s.sprinting = true;
                s.support = new BlockPos(0, 0, (int) Math.floor(z));
                JumpSolver.Result r = JumpSolver.solve(w, s, new BlockPos(0, 0, 4), JumpChain.LIMITS);
                if (r instanceof JumpSolver.Found) found++;
                System.out.printf(Locale.ROOT, "triple neo from z=%.1f at %.2f b/t: %s%n", z, v, r instanceof JumpSolver.Found f ? "FOUND, margin " + f.margin() : r);
            }
        }
        System.out.println("triple neo with speed: " + found + " of 20 starts");
    }
}
