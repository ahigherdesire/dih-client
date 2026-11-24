package baritone.parkour.solve;

import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.PlayerPhysics;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.sim.TickInput;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Jumps that need a run-up the tree search can't find: a triple neo, say, made with head-hitter momentum off the back
 * of a strip, a line aimed at the corner in front of the wall, running off that corner and jumping a tick late, and a
 * curve back in behind the wall. The whole move is a handful of numbers (run-up line, how long to hop, where to jump,
 * the facing on the jump tick, a curving facing in the air), searched with random restarts and hill climbing straight
 * through the simulator. The camera never turns faster than the solver's own limit.
 */
public final class RunUpSearch {
    private static final float MAX_TURN = 30.0F;
    private static final int RESTART_SAMPLES = 300;
    private static final int CLIMB_STEPS = 5_000;

    private RunUpSearch() {
    }

    /** A plan from {@code start} (at rest) that ends at rest on {@code target}, or why there's none in the time given. */
    public static JumpSolver.Result solve(BlockSource world, PlayerState start, BlockPos target, long maxNanos, long seed) {
        long t0 = System.nanoTime();
        Run run = new Run(world, start, target);
        Random r = new Random(seed);
        double best = Double.MAX_VALUE;
        int sims = 0;
        while (System.nanoTime() - t0 < maxNanos) {
            double[] p = null;
            double ps = Double.MAX_VALUE;
            for (int k = 0; k < RESTART_SAMPLES; k++) {
                double[] q = run.random(r);
                double qs = run.score(q);
                sims++;
                if (qs < ps) {
                    ps = qs;
                    p = q;
                }
            }
            double sigma = 0.25;
            for (int it = 0; it < CLIMB_STEPS && ps > 0; it++) {
                if ((it & 255) == 0 && System.nanoTime() - t0 > maxNanos) break;
                double[] q = p.clone();
                for (int i = 0; i < q.length; i++) {
                    q[i] += r.nextGaussian() * sigma * (run.hi[i] - run.lo[i]) * (r.nextInt(3) == 0 ? 1.0 : 0.1);
                }
                run.clamp(q);
                double qs = run.score(q);
                sims++;
                if (qs <= ps) {
                    ps = qs;
                    p = q;
                }
                if (it % 1000 == 999) sigma *= 0.6;
            }
            best = Math.min(best, ps);
            if (ps == 0) {
                List<TickInput> plan = run.plan(p);
                if (plan != null) return new JumpSolver.Found(plan, 0.0, sims, System.nanoTime() - t0);
            }
        }
        return new JumpSolver.Impossible(String.format(Locale.ROOT, "no run-up found (nearest miss %.2f blocks)", best), sims, System.nanoTime() - t0);
    }

    /** One start and target; scores shapes of the move. */
    private static final class Run {
        final BlockSource world;
        final PlayerState start;
        final BlockPos target;
        final double top;
        /** Heading from the start to the target (yaw), its unit vector, and the distance along it. */
        final float heading;
        final double ux, uz, distance;
        // 0 run-up yaw off the heading, 1 hop while closer than this along, 2 jump once this far along,
        // 3 jump-tick yaw off the heading, 4 and 5 air yaw off the heading (start, end), 6 ticks to turn between them,
        // 7 a first leg's yaw off the heading (to start the run-up line from the side of the strip), 8 how far along it ends
        final double[] lo, hi;

        Run(BlockSource world, PlayerState start, BlockPos target) {
            this.world = world;
            this.start = start;
            this.target = target;
            this.top = JumpSolver.topOf(world, target);
            double dx = target.getX() + 0.5 - start.x, dz = target.getZ() + 0.5 - start.z;
            distance = Math.sqrt(dx * dx + dz * dz);
            ux = dx / distance;
            uz = dz / distance;
            heading = (float) Math.toDegrees(Math.atan2(-dx, dz));
            lo = new double[]{-35, 0, Math.max(0, distance - 8), -60, -90, -90, 1, -90, 0};
            hi = new double[]{35, distance, Math.max(0.5, distance - 1), 60, 90, 90, 12, 90, 3};
        }

        double[] random(Random r) {
            double[] p = new double[lo.length];
            for (int i = 0; i < p.length; i++) p[i] = lo[i] + r.nextDouble() * (hi[i] - lo[i]);
            return p;
        }

        void clamp(double[] p) {
            for (int i = 0; i < p.length; i++) p[i] = Math.max(lo[i], Math.min(hi[i], p[i]));
        }

        double along(PlayerState s) {
            return (s.x - start.x) * ux + (s.z - start.z) * uz;
        }

        /** How far outside the box the feet have to be in to stand on the target. */
        double outside(PlayerState s) {
            double ex = Math.max(0, Math.max(target.getX() - 0.3 - s.x, s.x - (target.getX() + 1.3)));
            double ez = Math.max(0, Math.max(target.getZ() - 0.3 - s.z, s.z - (target.getZ() + 1.3)));
            return ex + ez;
        }

        double score(double[] p) {
            return simulate(p, null);
        }

        /** The plan for a shape that lands, with the ticks that stop the player there; null if it can't be stopped. */
        List<TickInput> plan(double[] p) {
            List<TickInput> out = new ArrayList<>();
            if (simulate(p, out) != 0) return null;
            return out;
        }

        /** Plays a shape out: 0 if it ends at rest on the target, else how far it misses by (in blocks, roughly). */
        double simulate(double[] p, List<TickInput> record) {
            float runYaw = heading + (float) p[0], jumpYaw = heading + (float) p[3];
            float air0 = heading + (float) p[4], air1 = heading + (float) p[5];
            double hopUntil = p[1] < 0.5 ? 0 : p[1], jumpAt = p[2];
            int blend = (int) Math.round(p[6]);
            float preYaw = heading + (float) p[7];
            double preUntil = p[8] < 0.2 ? 0 : p[8];
            PlayerState s = start;
            float yaw = start.yaw;
            int sinceJump = -1;
            for (int t = 0; t < 150; t++) {
                double a = along(s);
                boolean jump;
                float want;
                if (sinceJump < 0) {
                    if (s.onGround && a >= jumpAt && a >= hopUntil) {
                        sinceJump = 0;
                        want = jumpYaw;
                        jump = true;
                    } else {
                        want = a < preUntil && t < 40 ? preYaw : runYaw;
                        jump = s.onGround && a < hopUntil;
                    }
                } else {
                    sinceJump++;
                    want = air0 + (air1 - air0) * Math.min(1.0F, (sinceJump - 1) / (float) blend);
                    jump = false;
                }
                float turn = net.minecraft.util.Mth.wrapDegrees(want - yaw);
                yaw += net.minecraft.util.Mth.clamp(turn, -MAX_TURN, MAX_TURN);
                TickInput in = new TickInput(true, false, false, false, jump, true, false, yaw);
                if (record != null) record.add(in);
                s = PlayerPhysics.step(s, in, world);
                if (JumpSolver.standsOn(s, target, top)) {
                    if (record == null) return settles(s) ? 0 : 0.05;
                    List<TickInput> stop = JumpSolver.settle(world, s, target, top);
                    if (stop == null) return 0.05;
                    record.addAll(stop);
                    return 0;
                }
                if (sinceJump >= 1 && s.onGround) return outside(s) + 0.2;
                if (sinceJump >= 1 && s.vy < 0 && s.y < top) return outside(s) + (top - s.y);
                if (s.y < Math.min(start.y, top) - 2) return 5 + Math.max(0, distance - a);
                if (sinceJump < 0 && t > 110) return 10 + Math.max(0, distance - a);
            }
            return 10;
        }

        boolean settles(PlayerState landed) {
            return JumpSolver.settle(world, landed, target, top) != null;
        }
    }
}
