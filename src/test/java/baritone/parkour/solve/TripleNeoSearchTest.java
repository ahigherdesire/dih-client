package baritone.parkour.solve;

import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.PlayerPhysics;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.sim.TickInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Not pass/fail: a wider hunt for a triple neo than {@link TripleNeoProbeTest}. Players do it with a run-up that isn't
 * straight down the middle of the strip: head-hitter momentum, a line aimed at the edge in front of the neo, and a curve
 * in. This searches over the whole shape (start line, run-up yaw, where to jump, the yaw on the jump tick, a curving air
 * yaw, strafing) with random restarts and hill climbing, straight through the simulator.
 */
final class TripleNeoSearchTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockPos TARGET = new BlockPos(0, 0, 4);

    /** Strip x = 0 (and {@code wide}: x = 1 too, behind z = 0) from z = -8 to 0, ceiling at y = 3 over z <= {@code ceilingTo}. */
    static BlockSource.MapSource layout(int ceilingTo, boolean wide) {
        BlockSource.MapSource w = new BlockSource.MapSource().fill(0, 0, -8, 0, 0, 0, STONE);
        if (wide) w.fill(1, 0, -8, 1, 0, -1, STONE);
        if (ceilingTo >= -8) w.fill(0, 3, -8, wide ? 1 : 0, 3, ceilingTo, STONE);
        w.fill(0, 0, 1, 0, 3, 3, STONE);
        w.set(0, 0, 4, STONE);
        return w;
    }

    record Shape(double x0, float runYaw, double jumpAt, float jumpYaw, float air0, float air1, int blend, int strafe) {
        static Shape of(double[] p) {
            return new Shape(p[0], (float) p[1], p[2], (float) p[3], (float) p[4], (float) p[5],
                (int) Math.round(Math.max(1, Math.min(11, p[6]))), (int) Math.round(Math.max(-1, Math.min(1, p[7]))));
        }
    }

    static final double[] LO = {-0.29, -30, -1.5, -100, -100, -60, 1, -1.4};
    static final double[] HI = {1.29, 30, 0.7, 40, 60, 140, 11, 1.4};

    /** Lower is better; 0 means it lands on the target. */
    static double score(BlockSource world, int ceilingTo, double[] p, StringBuilder trace) {
        Shape s = Shape.of(p);
        double top = JumpSolver.topOf(world, TARGET);
        PlayerState st = PlayerState.standing(s.x0(), 1.0, -7.5, s.runYaw());
        int t = 0;
        // Run-up: sprint, jumping whenever on the ground under the ceiling (head-hitter), until the jump point.
        while (true) {
            if (t++ > 80) return 100;
            boolean under = st.z - 0.3 < ceilingTo + 1;
            if (st.onGround && !under && st.z >= s.jumpAt()) break;
            st = PlayerPhysics.step(st, new TickInput(true, false, false, false, st.onGround && under, true, false, s.runYaw()), world);
            if (st.y < 0.99) return 50 + Math.max(0, 1 - st.z);
            if (st.z > 0.71 && st.x < 1.3 && st.x > -0.3 && st.y < 1.1) return 40; // ran into the wall
        }
        if (trace != null) trace.append(String.format(Locale.ROOT, "jump from (%.3f, %.3f) v=(%.3f, %.3f)%n", st.x, st.z, st.vx, st.vz));
        // Jump tick, then the air.
        boolean left = s.strafe() > 0, right = s.strafe() < 0;
        for (int i = 0; i < 40; i++) {
            float yaw = i == 0 ? s.jumpYaw() : s.air0() + (s.air1() - s.air0()) * Math.min(1.0F, (i - 1) / (float) s.blend());
            st = PlayerPhysics.step(st, new TickInput(true, false, i > 0 && left, i > 0 && right, i == 0, true, false, yaw), world);
            if (trace != null) trace.append(String.format(Locale.ROOT, "  t%d yaw %.1f -> (%.3f, %.3f, %.3f) v=(%.3f, %.3f)%s%n", i, yaw, st.x, st.y, st.z, st.vx, st.vz, st.horizontalCollision ? " HIT" : ""));
            if (i > 0 && st.onGround) {
                if (JumpSolver.standsOn(st, TARGET, top)) return 0;
                break;
            }
            if (st.y < 1.0 && st.vy < 0) break;
        }
        return Math.max(0, 4.3 - st.z) + Math.max(0, st.x - 1.29) + Math.max(0, -0.29 - st.x) + (st.y < 0.9 ? 0.5 : 0);
    }

    static double[] random(Random r) {
        double[] p = new double[LO.length];
        for (int i = 0; i < p.length; i++) p[i] = LO[i] + r.nextDouble() * (HI[i] - LO[i]);
        return p;
    }

    static double[] clamp(double[] p) {
        for (int i = 0; i < p.length; i++) p[i] = Math.max(LO[i], Math.min(HI[i], p[i]));
        return p;
    }

    /** The search the bot uses, from where it would stand: at rest on the back block's centre, facing the wall. */
    @Test
    void runUpSearchFindsTheTripleNeo() {
        for (int ceilingTo : new int[]{-2, -1, -3, -11}) {
            BlockSource world = layout(ceilingTo, false);
            PlayerState start = PlayerState.standing(0.5, 1.0, -7.5, 0.0F);
            JumpSolver.Result r = RunUpSearch.solve(world, start, TARGET, 20_000_000_000L, 1);
            System.out.printf(Locale.ROOT, "run-up search, ceiling to %d: %s%n", ceilingTo, r instanceof JumpSolver.Found f
                ? "FOUND " + f.ticks() + " ticks, " + f.nodes() + " sims, " + f.nanos() / 1_000_000 + " ms" : r);
            if (r instanceof JumpSolver.Found f) {
                PlayerState s = start;
                for (TickInput in : f.inputs()) s = PlayerPhysics.step(s, in, world);
                System.out.printf(Locale.ROOT, "  replayed: ends at (%.3f, %.3f, %.3f) on target %s, at rest %s%n", s.x, s.y, s.z,
                    JumpSolver.standsOn(s, TARGET, 1.0), JumpSolver.atRest(s));
                if (ceilingTo == -2) org.junit.jupiter.api.Assertions.assertTrue(JumpSolver.standsOn(s, TARGET, 1.0) && JumpSolver.atRest(s));
            } else if (ceilingTo == -2) {
                org.junit.jupiter.api.Assertions.fail("no triple neo found with the head-hitter to z = -2");
            }
        }
    }

    @Test
    void hunt() {
        for (int ceilingTo : new int[]{-11, -1, -2, -3}) {
            for (boolean wide : new boolean[]{false, true}) {
                BlockSource world = layout(ceilingTo, wide);
                Random r = new Random(ceilingTo * 31L + (wide ? 7 : 0));
                double bestScore = Double.MAX_VALUE;
                double[] best = null;
                for (int restart = 0; restart < 40 && bestScore > 0; restart++) {
                    double[] p = null;
                    double ps = Double.MAX_VALUE;
                    for (int k = 0; k < 400; k++) {
                        double[] q = random(r);
                        double qs = score(world, ceilingTo, q, null);
                        if (qs < ps) {
                            ps = qs;
                            p = q;
                        }
                    }
                    double sigma = 0.25;
                    for (int it = 0; it < 6000 && ps > 0; it++) {
                        double[] q = p.clone();
                        for (int i = 0; i < q.length; i++) q[i] += r.nextGaussian() * sigma * (HI[i] - LO[i]) * (r.nextInt(3) == 0 ? 1 : 0.1);
                        clamp(q);
                        double qs = score(world, ceilingTo, q, null);
                        if (qs <= ps) {
                            ps = qs;
                            p = q;
                        }
                        if (it % 1000 == 999) sigma *= 0.6;
                    }
                    if (ps < bestScore) {
                        bestScore = ps;
                        best = p;
                    }
                }
                System.out.printf(Locale.ROOT, "neo hunt ceiling to %d wide %s: best %.4f %s%n", ceilingTo, wide, bestScore, Shape.of(best));
                StringBuilder trace = new StringBuilder();
                score(world, ceilingTo, best, trace);
                System.out.print(trace);
            }
        }
        System.out.println(Arrays.toString(LO));
    }
}
