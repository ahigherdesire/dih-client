package baritone.parkour.solve;

import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.PlayerPhysics;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.sim.TickInput;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Finds the per-tick inputs that take the player from a start state to standing on a target block, by weighted
 * best-first search over {@link PlayerPhysics}.
 *
 * <p>Search steps are short macros, each a way of moving relative to the target that is re-aimed every tick: a
 * heading (the direction to the target plus an offset: straight, 20° or 45° off, 90° to go round a wall) and keys
 * (forward, or the 45° strafes, whose facing is turned so the movement still follows the heading). On the ground a
 * step may jump now, run on and jump at the edge, or back up for a run-up; in the air a step holds for two ticks.
 * Neos, strafes, head-hitters and momentum jumps all fall out of this. Airborne states that can no longer reach the
 * target, or that drop below its top, are cut.
 *
 * <p>A plan is only returned when it is robust: it still lands with the start nudged by ±{@link #NUDGE} blocks and
 * every yaw by ±1°. Pure function of its inputs; safe on any thread.
 */
public final class JumpSolver {
    /** How far the start may be off for a plan to still count (the executor's drift). */
    public static final double NUDGE = 0.03;

    /** The fewest extra nodes searched for a sturdier plan once a fragile one is found. */
    private static final int GRACE_STEPS = 2_000;
    /** The most landings scored for robustness once a fragile one is found (each one replays the plan 18 times). */
    private static final int GRACE_LANDINGS = 8;

    public record Limits(int maxSteps, long maxNanos, int maxTicks, boolean robust) {
        public static final Limits FAST = new Limits(60_000, 60_000_000L, 70, true);
        public static final Limits THOROUGH = new Limits(600_000, 600_000_000L, 90, true);
    }

    public sealed interface Result permits Found, Impossible {
        int nodes();

        long nanos();
    }

    /** A plan: the inputs, one per tick, until the landing tick; the margin is the share of nudged replays that land. */
    public record Found(List<TickInput> inputs, double margin, int nodes, long nanos) implements Result {
        public int ticks() {
            return inputs.size();
        }
    }

    public record Impossible(String reason, int nodes, long nanos) implements Result {
    }

    private static final float[] GROUND_OFFSETS = {0.0F, 20.0F, -20.0F, 45.0F, -45.0F, 90.0F, -90.0F};
    private static final float[] AIR_OFFSETS = {0.0F, 20.0F, -20.0F, 45.0F, -45.0F, 90.0F, -90.0F, 135.0F, -135.0F};
    /** Keys and how far the facing turns from the heading so the movement still goes along it. */
    private enum Keys {
        FORWARD("W R", 0.0F), LEFT("WA R", 45.0F), RIGHT("WD R", -45.0F), WALK("W", 0.0F), BRAKE("S", 0.0F), NONE("", 0.0F);

        final String keys;
        final float turn;

        Keys(String keys, float turn) {
            this.keys = keys;
            this.turn = turn;
        }
    }

    private enum Kind { STEP, JUMP, EDGE_JUMP, BACK_UP, AIR, CLIMB }

    /** One search step: a way of moving, held for some ticks, re-aimed at the target every tick. */
    private record Macro(Kind kind, Keys keys, float offset, boolean straight) {
        Macro(Kind kind, Keys keys, float offset) {
            this(kind, keys, offset, false);
        }
    }

    private static final List<Macro> GROUND = new ArrayList<>();
    private static final List<Macro> AIR = new ArrayList<>();
    /** On a ladder: the air controls, plus climbing (jump held) with each of them. */
    private static final List<Macro> CLIMBING = new ArrayList<>();
    /** The run-ups the line search tries: sprinting and walking, along the field's aim and straight at the target. */
    private static final List<Macro> LINES = List.of(
        new Macro(Kind.STEP, Keys.FORWARD, 0.0F), new Macro(Kind.STEP, Keys.FORWARD, 0.0F, true),
        new Macro(Kind.STEP, Keys.WALK, 0.0F), new Macro(Kind.STEP, Keys.WALK, 0.0F, true));
    /** The air controls a takeoff is flown out with before the tree search takes over. */
    private static final List<Macro> ROLLOUTS = new ArrayList<>();

    static {
        for (float offset : GROUND_OFFSETS) {
            if (Math.abs(offset) <= 20.0F) GROUND.add(new Macro(Kind.STEP, Keys.FORWARD, offset));
            for (Kind kind : new Kind[]{Kind.JUMP, Kind.EDGE_JUMP}) GROUND.add(new Macro(kind, Keys.FORWARD, offset));
        }
        for (Keys keys : new Keys[]{Keys.LEFT, Keys.RIGHT}) {
            for (float offset : new float[]{0.0F, 20.0F, -20.0F}) {
                for (Kind kind : new Kind[]{Kind.JUMP, Kind.EDGE_JUMP}) GROUND.add(new Macro(kind, keys, offset));
            }
        }
        for (Kind kind : new Kind[]{Kind.STEP, Kind.JUMP, Kind.EDGE_JUMP}) GROUND.add(new Macro(kind, Keys.WALK, 0.0F));
        for (Kind kind : new Kind[]{Kind.STEP, Kind.JUMP, Kind.EDGE_JUMP}) GROUND.add(new Macro(kind, Keys.FORWARD, 0.0F, true));
        GROUND.add(new Macro(Kind.STEP, Keys.NONE, 0.0F));
        GROUND.add(new Macro(Kind.JUMP, Keys.NONE, 0.0F));
        GROUND.add(new Macro(Kind.BACK_UP, Keys.NONE, 0.0F));
        for (float offset : AIR_OFFSETS) AIR.add(new Macro(Kind.AIR, Keys.FORWARD, offset));
        for (Keys keys : new Keys[]{Keys.LEFT, Keys.RIGHT, Keys.BRAKE, Keys.NONE}) AIR.add(new Macro(Kind.AIR, keys, 0.0F));
        CLIMBING.addAll(AIR);
        for (Keys keys : new Keys[]{Keys.FORWARD, Keys.WALK, Keys.NONE}) {
            for (float offset : new float[]{0.0F, 45.0F, -45.0F}) CLIMBING.add(new Macro(Kind.CLIMB, keys, offset));
        }
        // Sprinting on for a long jump, strafing for the last bit of reach, letting go, walking or braking for a short one.
        for (Keys keys : new Keys[]{Keys.FORWARD, Keys.LEFT, Keys.RIGHT, Keys.NONE, Keys.WALK, Keys.BRAKE}) ROLLOUTS.add(new Macro(Kind.AIR, keys, 0.0F));
    }

    private JumpSolver() {
    }

    public static Result solve(BlockSource world, PlayerState start, BlockPos target, Limits limits) {
        return new Search(world, start, target, limits, System.nanoTime()).run();
    }

    /** Whether {@code s} stands on top of {@code target}: on the ground at its top, centre over it (0.3 overhang allowed). */
    public static boolean standsOn(PlayerState s, BlockPos target, double targetTop) {
        if (!s.onGround || Math.abs(s.y - targetTop) > 1.0E-6) return false;
        return s.x > target.getX() - 0.3 && s.x < target.getX() + 1.3 && s.z > target.getZ() - 0.3 && s.z < target.getZ() + 1.3;
    }

    /**
     * Whether replaying {@code inputs} from {@code start} ends at rest on {@code target}, never touching down on
     * anything else on the way (after the inputs run out, no keys are held until the player stops).
     */
    public static boolean lands(BlockSource world, PlayerState start, List<TickInput> inputs, BlockPos target, BlockPos startSupport) {
        PlayerState s = start;
        double top = topOf(world, target);
        float lastYaw = inputs.isEmpty() ? start.yaw : inputs.getLast().yaw();
        for (int i = 0; i < inputs.size() + SETTLE_TICKS; i++) {
            TickInput in = i < inputs.size() ? inputs.get(i) : TickInput.none(lastYaw);
            boolean wasOnGround = s.onGround;
            s = PlayerPhysics.step(s, in, world);
            boolean on = standsOn(s, target, top);
            if (on && i >= inputs.size() - 1 && atRest(s)) return true;
            // Touching down anywhere else is a miss, unless it's back on the start platform (a hop in the run-up).
            // Level with the target's top is fine too: the player can walk on to it from there.
            if (!on && s.onGround && !wasOnGround && Math.abs(s.y - top) > 1.0E-6
                && (s.support == null || s.support.getY() != startSupport.getY())) return false;
            if (s.y < Math.min(start.y, top) - 3.0) return false;
        }
        return false;
    }

    /** The most ticks the player is given to stop after landing. */
    private static final int SETTLE_TICKS = 25;

    /** Stopped, as the client sees it: on the ground, and the horizontal speed small enough to be zeroed. */
    static boolean atRest(PlayerState s) {
        return s.onGround && Math.abs(s.vx) < 0.003 && Math.abs(s.vz) < 0.003;
    }

    /**
     * The inputs that stop the player on {@code target} after touching down on it (backing against the slide while it's
     * fast, then no keys), or null when even that slides off. A landing on a one-block course is only half the jump.
     */
    static List<TickInput> settle(BlockSource world, PlayerState landed, BlockPos target, double top) {
        PlayerState s = landed;
        List<TickInput> out = new ArrayList<>();
        for (int i = 0; i < SETTLE_TICKS; i++) {
            if (atRest(s)) return out;
            double speed = Math.sqrt(s.vx * s.vx + s.vz * s.vz);
            float yaw = speed > 1.0E-6 ? (float) Math.toDegrees(Math.atan2(-s.vx, s.vz)) : s.yaw;
            // Back against the slide only while that slows it; once backing would reverse it, let friction finish.
            TickInput in = new TickInput(false, true, false, false, false, false, false, yaw);
            PlayerState braked = PlayerPhysics.step(s, in, world);
            if (braked.vx * s.vx + braked.vz * s.vz <= 0.0) {
                in = TickInput.none(yaw);
                braked = PlayerPhysics.step(s, in, world);
            }
            s = braked;
            out.add(in);
            if (!standsOn(s, target, top)) return null;
        }
        return atRest(s) ? out : null;
    }

    private static final class Node implements Comparable<Node> {
        final PlayerState state;
        final Node parent;
        final List<TickInput> inputs;
        final int ticks;
        final double f;
        /** Whether the player has been off the ground since the start (after that, only the target may be landed on). */
        final boolean leftGround;

        Node(PlayerState state, Node parent, List<TickInput> inputs, int ticks, double f, boolean leftGround) {
            this.state = state;
            this.parent = parent;
            this.inputs = inputs;
            this.ticks = ticks;
            this.f = f;
            this.leftGround = leftGround;
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(f, o.f);
        }

        List<TickInput> plan() {
            List<List<TickInput>> parts = new ArrayList<>();
            for (Node n = this; n.parent != null; n = n.parent) parts.add(n.inputs);
            Collections.reverse(parts);
            List<TickInput> out = new ArrayList<>();
            for (List<TickInput> part : parts) out.addAll(part);
            return out;
        }
    }

    private static final class Search {
        final BlockSource world;
        final PlayerState start;
        final BlockPos target;
        final Limits limits;
        final long t0;
        final double targetX;
        final double targetZ;
        final double targetTop;
        final BlockPos startSupport;
        final double startTop;
        final float straightYaw;
        /** The lowest climbable block between the start and the target (its y), or MAX_VALUE when there's none. */
        final double climbFloor;
        final LongOpenHashSet seen = new LongOpenHashSet();
        final DistanceField field;
        int steps;

        Search(BlockSource world, PlayerState start, BlockPos target, Limits limits, long t0) {
            this.world = world;
            this.start = start;
            this.target = target;
            this.limits = limits;
            this.t0 = t0;
            this.targetX = target.getX() + 0.5;
            this.targetZ = target.getZ() + 0.5;
            this.targetTop = topOf(world, target);
            this.startSupport = start.support != null ? start.support : BlockPos.containing(start.x, start.y - 0.5, start.z);
            this.startTop = start.y;
            this.straightYaw = (float) Math.toDegrees(Math.atan2(-(targetX - start.x), targetZ - start.z));
            this.climbFloor = climbFloor(world, start, target);
            double margin = 6.0;
            this.field = new DistanceField(world,
                Math.min(start.x, targetX) - margin, Math.min(start.z, targetZ) - margin,
                Math.max(start.x, targetX) + margin, Math.max(start.z, targetZ) + margin,
                Math.min(start.y, targetTop) + 0.61, Math.max(start.y, targetTop) + 1.79,
                targetX, targetZ, target);
        }

        Result run() {
            Node root = new Node(start, null, List.of(), 0, h(start), false);
            Found quick = lineSearch(root);
            if (quick != null) return quick;
            PriorityQueue<Node> open = new PriorityQueue<>();
            open.add(root);
            while (!open.isEmpty()) {
                if (steps >= limits.maxSteps() || steps >= stopAt || System.nanoTime() - t0 > limits.maxNanos()) break;
                Node node = open.poll();
                List<Macro> macros = node.state.onGround ? GROUND : PlayerPhysics.climbing(node.state, world) ? CLIMBING : AIR;
                for (Macro macro : macros) {
                    Node child = apply(node, macro);
                    if (child == null) continue;
                    PlayerState s = child.state;
                    if (standsOn(s, target, targetTop)) {
                        Found robust = accept(child);
                        if (robust != null) return robust;
                        continue;
                    }
                    if (node.state.onGround && !s.onGround) {
                        // Just took off: try flying the rest of the way along the field first.
                        Node landed = rollout(child);
                        if (landed != null) {
                            Found robust = accept(landed);
                            if (robust != null) return robust;
                        }
                    }
                    if (!seen.add(key(s))) continue;
                    open.add(child);
                }
            }
            long nanos = System.nanoTime() - t0;
            if (fragile != null) return new Found(fragile.inputs(), fragile.margin(), steps, nanos);
            String why = open.isEmpty() ? "no inputs reach it" : steps >= limits.maxSteps() ? "search budget spent" : "time budget spent";
            return new Impossible(why, steps, nanos);
        }

        /** The y of the lowest ladder, vine or other climbable block in the box round the start and the target. */
        static double climbFloor(BlockSource world, PlayerState start, BlockPos target) {
            int x0 = Mth.floor(Math.min(start.x, target.getX())) - 3, x1 = Mth.floor(Math.max(start.x, target.getX())) + 3;
            int z0 = Mth.floor(Math.min(start.z, target.getZ())) - 3, z1 = Mth.floor(Math.max(start.z, target.getZ())) + 3;
            int y0 = Mth.floor(Math.min(start.y, target.getY())) - 2, y1 = Math.max(Mth.floor(start.y), target.getY()) + 1;
            double floor = Double.MAX_VALUE;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = y0; y <= y1 && floor == Double.MAX_VALUE; y++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        if (PlayerPhysics.climbable(world.get(pos.set(x, y, z)))) floor = y;
                    }
                }
            }
            return floor;
        }

        /** The sturdiest fragile plan so far. */
        Found fragile;
        /** Once a fragile plan exists, look for a sturdier one for as many nodes again (and at least GRACE_STEPS more). */
        int stopAt = Integer.MAX_VALUE;
        int graceLandings;

        /** A plan that lands on the target: returned when it's robust, otherwise kept if it's the sturdiest yet. */
        Found accept(Node landed) {
            List<TickInput> stop = settle(world, landed.state, target, targetTop);
            if (stop == null) return null;
            List<TickInput> plan = new ArrayList<>(landed.plan());
            plan.addAll(stop);
            double margin = limits.robust() ? margin(plan, fragile == null ? -1.0 : fragile.margin()) : 1.0;
            if (margin >= 1.0) return new Found(plan, margin, steps, System.nanoTime() - t0);
            if (fragile == null) stopAt = steps + Math.max(steps, GRACE_STEPS);
            else if (++graceLandings >= GRACE_LANDINGS) stopAt = steps;
            if (fragile == null || margin > fragile.margin()) fragile = new Found(plan, margin, steps, System.nanoTime() - t0);
            return null;
        }

        /**
         * From a takeoff, holds each of a few air controls along the field's aim until touching down; the first that
         * lands on the target, or null. The field hugs wall corners, so this finds most neos without a tree search.
         */
        Node rollout(Node takeoff) {
            for (Macro macro : ROLLOUTS) {
                PlayerState s = takeoff.state;
                List<TickInput> inputs = new ArrayList<>(24);
                int ticks = takeoff.ticks;
                while (true) {
                    s = step(s, input(s, macro, false), inputs);
                    ticks++;
                    if (standsOn(s, target, targetTop)) {
                        airborneNow = false;
                        return new Node(s, takeoff, inputs, ticks, ticks, true);
                    }
                    if (dead(s, ticks, true)) break;
                }
            }
            airborneNow = false;
            return null;
        }

        /**
         * Before the tree search: run on along each run-up line, and on every tick try jumping there and flying the
         * rollouts. Most plain jumps (gaps, ups, drops) are one run-up and one jump, so this finds them in a few
         * thousand ticks; the tree search would otherwise dive into the air subtrees of jumps that overshoot.
         */
        Found lineSearch(Node root) {
            for (Macro run : LINES) {
                Node node = root;
                for (int i = 0; i < 24 && node != null && node.state.onGround && steps < stopAt; i++) {
                    Node jumped = apply(node, new Macro(Kind.JUMP, run.keys(), run.offset(), run.straight()));
                    if (jumped != null && !jumped.state.onGround) {
                        Node landed = rollout(jumped);
                        if (landed != null) {
                            Found robust = accept(landed);
                            if (robust != null) return robust;
                        }
                    } else if (jumped != null && standsOn(jumped.state, target, targetTop)) {
                        Found robust = accept(jumped);
                        if (robust != null) return robust;
                    }
                    node = apply(node, run);
                    if (node != null && standsOn(node.state, target, targetTop)) {
                        Found robust = accept(node);
                        if (robust != null) return robust;
                        break;
                    }
                }
            }
            // A fragile plan from here still counts; the tree search may find a sturdier one.
            return null;
        }

        /** Plays {@code macro} from {@code node}; null when the result is a dead end. */
        Node apply(Node node, Macro macro) {
            airborneNow = false;
            PlayerState s = node.state;
            List<TickInput> inputs = new ArrayList<>(4);
            switch (macro.kind()) {
                case STEP, JUMP -> {
                    TickInput in = input(s, macro, macro.kind() == Kind.JUMP);
                    s = step(s, in, inputs);
                }
                case BACK_UP -> {
                    for (int i = 0; i < 2; i++) {
                        float aim = aimYaw(s);
                        s = step(s, new TickInput(false, true, false, false, false, false, false, aim), inputs);
                    }
                }
                case EDGE_JUMP -> {
                    // Run on (re-aimed) until the next tick would leave the ground, and jump on that tick.
                    for (int i = 0; i < 20; i++) {
                        TickInput run = input(s, macro, false);
                        PlayerState next = PlayerPhysics.step(s, run, world);
                        steps++;
                        if (!next.onGround || next.horizontalCollision) {
                            s = step(s, input(s, macro, true), inputs);
                            break;
                        }
                        if (standsOn(next, target, targetTop)) return null; // walking there is a STEP's job
                        inputs.add(run);
                        s = next;
                        if (i == 19) return null;
                    }
                }
                case AIR, CLIMB -> {
                    // Holding jump on a ladder climbs it whether or not the player is pressed against the wall.
                    for (int i = 0; i < 3 && !(i > 0 && s.onGround); i++) s = step(s, input(s, macro, macro.kind() == Kind.CLIMB), inputs);
                }
            }
            int ticks = node.ticks + inputs.size();
            boolean leftGround = node.leftGround || airborneNow || !s.onGround;
            airborneNow = false;
            if (dead(s, ticks, leftGround)) return null;
            return new Node(s, node, inputs, ticks, ticks + 2.0 * h(s), leftGround);
        }

        PlayerState step(PlayerState s, TickInput in, List<TickInput> inputs) {
            steps++;
            inputs.add(in);
            PlayerState next = PlayerPhysics.step(s, in, world);
            if (!next.onGround) airborneNow = true;
            return next;
        }

        /** Set by {@link #step} when a tick ends in the air; read and reset by {@link #apply}. */
        boolean airborneNow;

        TickInput input(PlayerState s, Macro macro, boolean jump) {
            float heading = macro.straight() ? straightYaw(s) : aimYaw(s);
            float yaw = Mth.wrapDegrees(heading + macro.offset() + macro.keys().turn);
            Keys k = macro.keys();
            boolean forward = k == Keys.FORWARD || k == Keys.LEFT || k == Keys.RIGHT || k == Keys.WALK;
            boolean sprint = k == Keys.FORWARD || k == Keys.LEFT || k == Keys.RIGHT;
            return new TickInput(forward, k == Keys.BRAKE, k == Keys.LEFT, k == Keys.RIGHT, jump, sprint, false, yaw);
        }

        boolean dead(PlayerState s, int ticks, boolean leftGround) {
            if (ticks > limits.maxTicks()) return true;
            if (s.onGround) {
                if (standsOn(s, target, targetTop)) return false;
                // Back on the ground after being in the air is a miss unless it's level with the target's top (the
                // top of a ladder, say, a step short of it); before that, the run-up stays at the start platform's height.
                if (leftGround) return Math.abs(s.y - targetTop) > 1.0E-6;
                return s.support == null || s.support.getY() != startSupport.getY();
            }
            // On a ladder the player can still climb to the target, so none of the falling checks apply.
            if (PlayerPhysics.climbing(s, world)) return s.y < Math.min(startTop, targetTop) - 3.0;
            // With a ladder about, the player only has to catch the ladder: measure the fall against its foot instead.
            if (climbFloor != Double.MAX_VALUE) return s.vy <= 0.0 && s.y < Math.min(climbFloor, targetTop) - 1.0E-4;
            // In the air: below the target's top and still falling can't land on it.
            if (s.y < targetTop - 1.0E-4 && s.vy <= 0.0) return true;
            // Past the target and still moving away fast: air control can't bring it back in time.
            double ax = targetX - s.x, az = targetZ - s.z;
            if (ax * ax + az * az > 1.44 && ax * s.vx + az * s.vz < 0 && s.vx * s.vx + s.vz * s.vz > 0.0225) return true;
            if (s.y < Math.min(startTop, targetTop) - 3.0) return true;
            // Out of reach: further than the most the player can still cover before dropping below the target's top.
            int airTicks = ticksAbove(s);
            double dx = targetX - s.x, dz = targetZ - s.z;
            double need = Math.sqrt(dx * dx + dz * dz) - 0.8;
            return need > maxAirTravel(Math.sqrt(s.vx * s.vx + s.vz * s.vz), airTicks) + 0.15;
        }

        /** The furthest horizontal travel in {@code ticks} air ticks starting at speed {@code v}, all of it aimed one way. */
        static double maxAirTravel(double v, int ticks) {
            double d = 0.0;
            // Each tick the air acceleration (sprinting, with the diagonal boost) is added before the move.
            for (int i = 0; i < ticks; i++) {
                v += 0.0266;
                d += v;
                v *= 0.91;
            }
            return d;
        }

        /** Ticks until a free fall from {@code s} drops below the target's top. */
        int ticksAbove(PlayerState s) {
            double y = s.y, vy = s.vy;
            int t = 0;
            while (t < 60) {
                y += vy;
                if (y < targetTop - 1.0E-4 && vy < 0) break;
                vy = (vy - 0.08) * 0.98;
                t++;
            }
            return t + 1;
        }

        /** Ticks left, optimistically: the distance round walls at sprint-jump speed. */
        double h(PlayerState s) {
            return Math.max(0.0, field.distance(s.x, s.z, targetX, targetZ) - 0.8) / 0.33;
        }

        /** The heading of the shortest path round walls, at the furthest point in sight up to a few blocks on. */
        float aimYaw(PlayerState s) {
            return field.aimYaw(s.x, s.z, targetX, targetZ, 4.0);
        }

        /** The heading from the start straight at the target, whatever is in the way. */
        float straightYaw(PlayerState s) {
            return straightYaw;
        }

        long key(PlayerState s) {
            long h = 1469598103934665603L;
            h = mix(h, Math.round(s.x * 32));
            h = mix(h, Math.round(s.y * 32));
            h = mix(h, Math.round(s.z * 32));
            h = mix(h, Math.round(s.vx * 40));
            h = mix(h, Math.round(s.vy * 20));
            h = mix(h, Math.round(s.vz * 40));
            h = mix(h, (s.onGround ? 1 : 0) | (s.sprinting ? 2 : 0) | (s.noJumpDelay > 0 ? 4 : 0));
            return h;
        }

        static long mix(long h, long v) {
            h ^= v;
            h *= 1099511628211L;
            return h ^ (h >>> 29);
        }

        /**
         * The share of nudged replays (start ±NUDGE in x and z, yaw ±1°) that still land; stops early, with a low
         * answer, once the share can no longer beat {@code beat}.
         */
        double margin(List<TickInput> plan, double beat) {
            int passed = 0, total = 0, all = 18;
            for (double ox : new double[]{-NUDGE, 0.0, NUDGE}) {
                for (double oz : new double[]{-NUDGE, 0.0, NUDGE}) {
                    for (float oy : new float[]{-1.0F, 1.0F}) {
                        PlayerState nudged = start.copy();
                        nudged.x += ox;
                        nudged.z += oz;
                        List<TickInput> turned = new ArrayList<>(plan.size());
                        for (TickInput in : plan) turned.add(in.withYaw(in.yaw() + oy));
                        total++;
                        if (lands(world, nudged, turned, target, startSupport)) passed++;
                        if ((passed + all - total) / (double) all <= beat) return passed / (double) all;
                    }
                }
            }
            return passed / (double) all;
        }
    }

    /** The y of the top of {@code pos}'s collision shape (1.0 above its base for a full block). */
    public static double topOf(BlockSource world, BlockPos pos) {
        VoxelShape shape = world.get(pos).getCollisionShape(EmptyBlockGetter.INSTANCE, pos);
        return shape.isEmpty() ? pos.getY() : pos.getY() + shape.max(Direction.Axis.Y);
    }
}
