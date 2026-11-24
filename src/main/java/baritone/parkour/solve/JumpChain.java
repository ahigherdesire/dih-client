package baritone.parkour.solve;

import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.PlayerPhysics;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.sim.TickInput;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Jumps run together without stopping in between: the next jump is solved from the moment the current one touches down
 * (still moving), while the current one is in the air. When it's found, the player goes straight on into it at
 * touchdown instead of braking to a stop first; when it isn't, the current jump's plan still stops the player safely.
 */
public final class JumpChain {
    /** How close the real touchdown has to be to the predicted one for the chained plan to hold. */
    public static final double MATCH = 0.005;
    /** The budget for a chained jump: it has to be ready before the current jump lands. */
    public static final JumpSolver.Limits LIMITS = new JumpSolver.Limits(200_000, 250_000_000L, 90, true);

    /**
     * The next jump, ready to take over: after {@code index} inputs of the current plan the player is at {@code from},
     * just touched down on the current target, and {@code inputs} take it on to {@code target}.
     */
    public record Link(int index, PlayerState from, List<TickInput> inputs, BlockPos target) {
    }

    private JumpChain() {
    }

    /**
     * The touchdown on {@code landing} in {@code plan} played from {@code start} (the index after the touching-down
     * input, and the state there), or null when it never touches down on it.
     */
    public static Touchdown touchdown(BlockSource world, PlayerState start, List<TickInput> plan, BlockPos landing) {
        double top = JumpSolver.topOf(world, landing);
        PlayerState s = start;
        boolean airborne = !start.onGround;
        for (int i = 0; i < plan.size(); i++) {
            s = PlayerPhysics.step(s, plan.get(i), world);
            if (!s.onGround) airborne = true;
            else if (airborne && JumpSolver.standsOn(s, landing, top)) return new Touchdown(i + 1, s);
        }
        return null;
    }

    public record Touchdown(int index, PlayerState state) {
    }

    /** The jump on to {@code next} from where {@code plan} touches down on {@code landing}, or null. */
    public static Link link(BlockSource world, PlayerState start, List<TickInput> plan, BlockPos landing, BlockPos next, JumpSolver.Limits limits) {
        return link(world, start, plan, landing, next, null, limits);
    }

    /** How hard the look at the jump after next is: a quick check, made for every landing the search considers. */
    public static final JumpSolver.Limits LOOK_AHEAD = new JumpSolver.Limits(25_000, 40_000_000L, 70, false);

    /**
     * The jump on to {@code next} from where {@code plan} touches down on {@code landing}, landing so the jump on to
     * {@code then} can be made straight on from its touchdown too (looking a jump further ahead keeps the run going
     * instead of stopping on {@code next}). Without such a landing, any landing on {@code next}. Null if there's none.
     */
    public static Link link(BlockSource world, PlayerState start, List<TickInput> plan, BlockPos landing, BlockPos next, BlockPos then,
                            JumpSolver.Limits limits) {
        if (next == null) return null;
        Touchdown at = touchdown(world, start, plan, landing);
        if (at == null) return null;
        JumpSolver.Result result = null;
        if (then != null) {
            result = JumpSolver.solve(world, at.state(), next, limits,
                touch -> JumpSolver.solve(world, touch, then, LOOK_AHEAD) instanceof JumpSolver.Found);
        }
        if (!(result instanceof JumpSolver.Found)) result = JumpSolver.solve(world, at.state(), next, limits);
        return result instanceof JumpSolver.Found found ? new Link(at.index(), at.state(), found.inputs(), next) : null;
    }

    /** Whether the real state is the one a link was solved from: same place, speed and ground contact. */
    public static boolean matches(PlayerState real, PlayerState expected) {
        return real.onGround == expected.onGround && real.sprinting == expected.sprinting
            && Math.abs(real.x - expected.x) < MATCH && Math.abs(real.y - expected.y) < MATCH && Math.abs(real.z - expected.z) < MATCH
            && Math.abs(real.vx - expected.vx) < MATCH && Math.abs(real.vz - expected.vz) < MATCH;
    }
}
