package baritone.parkour.solve;

import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.PlayerPhysics;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.sim.TickInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.Locale;

/**
 * Not pass/fail: whether a triple neo (a wall three deep straight ahead, landing right behind it) can be made with
 * head-hitter momentum, the way players set it up: sprint-jumping under a 2-block ceiling on the run-up, which builds
 * more speed than sprinting. Every tick of such a run-up is a start the solver is asked to finish the neo from.
 */
final class TripleNeoProbeTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final JumpSolver.Limits LIMITS = new JumpSolver.Limits(400_000, 3_000_000_000L, 90, false);

    /** A one-wide strip from z = -10 to 0, the wall at z = 1..3, the landing at z = 4; a ceiling over z = -10..{@code ceilingTo}. */
    private static BlockSource.MapSource course(int ceilingTo, int wall) {
        BlockSource.MapSource w = new BlockSource.MapSource().fill(0, 0, -10, 0, 0, 0, STONE);
        if (ceilingTo >= -10) w.fill(0, 3, -10, 0, 3, ceilingTo, STONE);
        w.fill(0, 0, 1, 0, 3, wall, STONE);
        w.set(0, 0, wall + 1, STONE);
        return w;
    }

    private static int probe(String name, BlockSource world, int wall) {
        BlockPos target = new BlockPos(0, 0, wall + 1);
        PlayerState s = PlayerState.standing(0.5, 1.0, -9.5, 0.0F);
        int starts = 0, found = 0;
        double fastest = 0;
        for (int t = 0; t < 60 && s.z < 0.9; t++) {
            // Head-hitter momentum: hold W and sprint, jump every time the feet touch the ground.
            s = PlayerPhysics.step(s, new TickInput(true, false, false, false, s.onGround, true, false, 0.0F), world);
            fastest = Math.max(fastest, Math.abs(s.vz));
            if (!s.onGround || s.z < -6.0) continue;
            starts++;
            JumpSolver.Result r = JumpSolver.solve(world, s, target, LIMITS);
            if (r instanceof JumpSolver.Found f) {
                found++;
                System.out.printf(Locale.ROOT, "%s: FOUND from z=%.3f vz=%.3f (tick %d), %d ticks, %d nodes%n", name, s.z, s.vz, t, f.ticks(), f.nodes());
            }
        }
        System.out.printf(Locale.ROOT, "%s: %d of %d head-hitter run-up starts make it (top speed %.3f b/t)%n", name, found, starts, fastest);
        return found;
    }

    @Test
    void tripleNeoWithHeadHitterMomentum() {
        probe("triple, no ceiling", course(-11, 3), 3);
        probe("triple, ceiling to z=-1", course(-1, 3), 3);
        probe("triple, ceiling to z=0", course(0, 3), 3);
        probe("double, ceiling to z=-1", course(-1, 2), 2);
    }

    /**
     * Independent of the solver: a sweep straight through the simulator. From every tick of a head-hitter run-up, wait
     * 0-3 ticks, jump, fly out at one yaw for k ticks and back in at another (sprinting, with W, W+A or W+D), and see
     * whether anything lands on the block behind the wall. Finds what the solver's search shape could miss.
     */
    @Test
    void tripleNeoBruteForce() {
        for (int ceilingTo : new int[]{-11, -1, 0}) {
            for (int side : new int[]{1, -1}) {
                BlockSource world = course(ceilingTo, 3);
                BlockPos target = new BlockPos(0, 0, 4);
                double top = JumpSolver.topOf(world, target);
                PlayerState run = PlayerState.standing(0.5, 1.0, -9.5, 0.0F);
                long tried = 0;
                int landed = 0;
                double closest = Double.MAX_VALUE;
                String best = "";
                for (int t = 0; t < 60 && run.z < 0.9; t++) {
                    run = PlayerPhysics.step(run, new TickInput(true, false, false, false, run.onGround, true, false, 0.0F), world);
                    if (!run.onGround || run.z < -6.0) continue;
                    for (int wait = 0; wait <= 3; wait++) {
                        for (float out = 5.0F; out <= 75.0F; out += 5.0F) {
                            for (float back = 0.0F; back <= 120.0F; back += 10.0F) {
                                for (int k = 1; k <= 14; k++) {
                                    for (int keys = 0; keys < 3; keys++) {
                                        tried++;
                                        PlayerState s = run;
                                        for (int i = 0; i < wait; i++) s = PlayerPhysics.step(s, new TickInput(true, false, false, false, false, true, false, 0.0F), world);
                                        boolean left = keys == 1, right = keys == 2;
                                        // Out: yaw towards the side (-x is yaw +90, +x is yaw -90); back: past straight, towards the middle.
                                        float outYaw = side * out, backYaw = -side * back;
                                        boolean ok = false;
                                        for (int i = 0; i < 40; i++) {
                                            float yaw = i < k ? outYaw : backYaw;
                                            s = PlayerPhysics.step(s, new TickInput(true, false, left, right, i == 0, true, false, yaw), world);
                                            if (s.onGround && i > 0) {
                                                ok = JumpSolver.standsOn(s, target, top);
                                                break;
                                            }
                                            if (s.y < -2) break;
                                        }
                                        double d = Math.hypot(s.x - 0.5, s.z - 4.5);
                                        if (ok) {
                                            landed++;
                                            if (landed <= 3) System.out.printf(Locale.ROOT, "brute: LANDS ceiling to %d side %d: start z=%.3f vz=%.3f wait %d out %.0f for %d back %.0f keys %d%n",
                                                ceilingTo, side, run.z, run.vz, wait, outYaw, k, backYaw, keys);
                                        } else if (s.y > 0.9 && d < closest) {
                                            closest = d;
                                            best = String.format(Locale.ROOT, "z=%.3f wait %d out %.0f for %d back %.0f keys %d -> (%.2f, %.2f, %.2f)", run.z, wait, outYaw, k, backYaw, keys, s.x, s.y, s.z);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                System.out.printf(Locale.ROOT, "brute: ceiling to %d side %d: %d of %d landed; nearest miss %.2f from the centre (%s)%n",
                    ceilingTo, side, landed, tried, closest, best);
            }
        }
    }

    /** The same with the solver's camera limits lifted: whether they, not the physics, rule the triple out. */
    @Test
    void tripleNeoWithoutCameraLimits() {
        JumpSolver.cameraLimits(180.0F, 180.0F);
        try {
            probe("free camera: triple, no ceiling", course(-11, 3), 3);
            probe("free camera: triple, ceiling to z=-1", course(-1, 3), 3);
            probe("free camera: triple, ceiling to z=0", course(0, 3), 3);
        } finally {
            JumpSolver.cameraLimits(30.0F, 90.0F);
        }
    }
}
