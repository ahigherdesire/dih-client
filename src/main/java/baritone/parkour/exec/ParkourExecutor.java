package baritone.parkour.exec;

import baritone.parkour.sim.BlockSource;
import baritone.parkour.sim.PlayerPhysics;
import baritone.parkour.sim.PlayerState;
import baritone.parkour.sim.TickInput;
import baritone.parkour.solve.JumpSolver;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plays a solved jump tick by tick. The player's keys come from a {@link PlanInput} that replaces the keyboard input
 * while it runs, and the yaw is set before every tick, so what the client does is exactly what the plan says.
 *
 * <p>Closed loop: at the start of each tick the real state is compared with the simulator's prediction for it. Off by
 * more than {@link #REPLAN_DRIFT}, a new plan is solved from the real state on a worker thread while the old plan
 * carries on; the new plan takes over on the tick it was solved for. A teleport (the server setting the player back)
 * ends the run as a fall.
 *
 * <p>Call {@link #tick} on the client thread at the start of every client tick, before the player moves.
 */
public final class ParkourExecutor {
    public static final double REPLAN_DRIFT = 0.01;
    /** A jump in position this big between ticks is a teleport, not movement. */
    private static final double TELEPORT = 4.0;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "DIH parkour solver");
        thread.setDaemon(true);
        return thread;
    });

    public enum Status { RUNNING, LANDED, FELL, STOPPED }

    /** What a running jump will do, for the world overlay: the predicted path (feet positions) and the landing block. */
    public record Preview(List<net.minecraft.world.phys.Vec3> path, BlockPos target) {
    }

    private static volatile Preview preview;

    /** The jump being made right now, or null. */
    public static Preview preview() {
        return preview;
    }

    private final BlockPos target;
    private final double targetTop;
    private final BlockSource world;
    private List<TickInput> plan;
    /** Predicted state after each tick of {@link #plan}, from where it was solved. */
    private List<PlayerState> predicted;
    private int index;
    private PlayerState lastReal;
    private PlanInput input;
    private ClientInput original;
    private CompletableFuture<JumpSolver.Result> replan;
    private int replanAt;
    private double maxDrift;
    private String firstDrift = "";
    private int replans;
    private Status status = Status.RUNNING;
    private String detail = "";

    public ParkourExecutor(BlockSource world, PlayerState start, List<TickInput> plan, BlockPos target) {
        this.world = world;
        this.target = target;
        this.targetTop = JumpSolver.topOf(world, target);
        adopt(start, plan, 0);
    }

    private void adopt(PlayerState from, List<TickInput> newPlan, int offset) {
        List<TickInput> merged = new ArrayList<>(plan == null ? List.of() : plan.subList(0, Math.min(offset, plan.size())));
        merged.addAll(newPlan);
        List<PlayerState> states = new ArrayList<>(predicted == null ? List.of() : predicted.subList(0, Math.min(offset, predicted.size())));
        PlayerState s = from;
        for (TickInput in : newPlan) {
            s = PlayerPhysics.step(s, in, world);
            states.add(s);
        }
        plan = merged;
        predicted = states;
        List<net.minecraft.world.phys.Vec3> path = new ArrayList<>(states.size() + 1);
        path.add(new net.minecraft.world.phys.Vec3(from.x, from.y, from.z));
        for (PlayerState state : states) path.add(new net.minecraft.world.phys.Vec3(state.x, state.y, state.z));
        preview = new Preview(List.copyOf(path), target);
    }

    /** Whether the player has touched down on the target (and is now playing the ticks that stop it there). */
    private boolean touchedDown;

    /** Called before each client tick. Returns the run's status after checking the last tick. */
    public Status tick(LocalPlayer player) {
        if (status != Status.RUNNING) return status;
        PlayerState real = stateOf(player);
        if (lastReal != null && Math.abs(real.x - lastReal.x) + Math.abs(real.y - lastReal.y) + Math.abs(real.z - lastReal.z) > TELEPORT) {
            return finish(player, Status.FELL, "set back by the server");
        }
        lastReal = real;

        if (index > 0) {
            // Past the end of the plan (idling until touchdown) there is no prediction left to compare with.
            double drift = 0;
            if (index <= predicted.size() && index <= plan.size()) {
                PlayerState expected = predicted.get(index - 1);
                drift = Math.max(Math.abs(expected.x - real.x), Math.max(Math.abs(expected.y - real.y), Math.abs(expected.z - real.z)));
                maxDrift = Math.max(maxDrift, drift);
                if (drift > REPLAN_DRIFT && firstDrift.isEmpty()) {
                    firstDrift = "tick " + index + "/" + plan.size() + " input " + plan.get(index - 1).compact() + ": predicted " + expected + " real " + real;
                }
            }
            // The plan ends with the ticks that stop the player on the target; the jump is done once they're played.
            boolean on = JumpSolver.standsOn(real, target, targetTop);
            if (on && index >= plan.size()) return finish(player, Status.LANDED, "landed");
            if (on) touchedDown = true;
            if (real.onGround && !on && (touchedDown || index >= plan.size())) {
                return finish(player, Status.FELL, touchedDown ? "slid off the landing" : "landed short");
            }
            if (real.y < target.getY() - 3.0) return finish(player, Status.FELL, "fell");
            if (drift > REPLAN_DRIFT && replan == null && index < plan.size() && !touchedDown) startReplan(real);
        }
        if (replan != null && replan.isDone() && index >= replanAt - 1) takeReplan();
        if (index >= plan.size()) {
            if (index > plan.size() + 6) return finish(player, Status.FELL, "the plan ran out before landing");
            apply(player, TickInput.none(player.getYRot()));
            index++;
            return status;
        }
        apply(player, plan.get(index));
        index++;
        return status;
    }

    /** Solves from where the player will be one tick from now (the next planned input applied to the real state). */
    private void startReplan(PlayerState real) {
        replans++;
        TickInput next = plan.get(index);
        PlayerState from = PlayerPhysics.step(real, next, world);
        replanAt = index + 1;
        replan = CompletableFuture.supplyAsync(() -> JumpSolver.solve(world, from, target, JumpSolver.Limits.FAST), WORKER);
        replanFrom = from;
    }

    private PlayerState replanFrom;

    private void takeReplan() {
        JumpSolver.Result result = replan.getNow(null);
        replan = null;
        if (result instanceof JumpSolver.Found found && index == replanAt) {
            adopt(replanFrom, found.inputs(), replanAt);
        }
    }

    private void apply(LocalPlayer player, TickInput in) {
        if (input == null) {
            input = new PlanInput();
            original = player.input;
            player.input = input;
        }
        input.next = in;
        player.setYRot(in.yaw());
        player.yRotO = in.yaw();
    }

    private Status finish(LocalPlayer player, Status end, String why) {
        status = end;
        detail = why;
        release(player);
        return status;
    }

    /** Gives the keyboard back. Safe to call more than once. */
    public void release(LocalPlayer player) {
        preview = null;
        if (input != null && player != null && player.input == input) player.input = original;
        input = null;
        if (status == Status.RUNNING) status = Status.STOPPED;
    }

    public Status status() {
        return status;
    }

    public String detail() {
        return detail;
    }

    public double maxDrift() {
        return maxDrift;
    }

    /** Where the real run first drifted from the prediction, for diagnosis; empty if it never did. */
    public String firstDrift() {
        return firstDrift;
    }

    public int replans() {
        return replans;
    }

    /** Keys for the next tick, as the client's input reads them. */
    /** Whether {@code input} is a solved jump's input (Baritone's input handler must not swap it out). */
    public static boolean isPlanInput(ClientInput input) {
        return input instanceof PlanInput;
    }

    static final class PlanInput extends ClientInput {
        TickInput next;

        @Override
        public void tick() {
            TickInput in = next;
            if (in == null) {
                keyPresses = Input.EMPTY;
                moveVector = Vec2.ZERO;
                return;
            }
            keyPresses = new Input(in.forward(), in.back(), in.left(), in.right(), in.jump(), in.sneak(), in.sprint());
            float forward = in.forward() == in.back() ? 0.0F : in.forward() ? 1.0F : -1.0F;
            float left = in.left() == in.right() ? 0.0F : in.left() ? 1.0F : -1.0F;
            moveVector = new Vec2(left, forward).normalized();
        }
    }

    // ---------------------------------------------------------------- reading the live player

    /** The simulator's view of the live player. */
    public static PlayerState stateOf(LocalPlayer player) {
        PlayerState s = new PlayerState();
        s.x = player.getX();
        s.y = player.getY();
        s.z = player.getZ();
        s.vx = player.getDeltaMovement().x;
        s.vy = player.getDeltaMovement().y;
        s.vz = player.getDeltaMovement().z;
        s.yaw = player.getYRot();
        s.onGround = player.onGround();
        s.horizontalCollision = player.horizontalCollision;
        s.minorHorizontalCollision = player.minorHorizontalCollision;
        s.verticalCollision = player.verticalCollision;
        s.sprinting = player.isSprinting();
        s.crouching = player.isCrouching();
        s.crouchPose = player.getPose() == net.minecraft.world.entity.Pose.CROUCHING;
        s.fallDistance = player.fallDistance;
        s.support = ((dihclient.mixin.accessor.DihSupportingBlockAccessor) player).dih$getMainSupportingBlockPos().orElse(null);
        s.noJumpDelay = ((dihclient.mixin.accessor.DihJumpDelayAccessor) player).dih$getNoJumpDelay();
        s.onGroundNoBlocks = s.onGround && s.support == null;
        s.prevJump = player.input.keyPresses.jump();
        s.prevSneak = player.input.keyPresses.shift();
        s.prevForwardImpulse = player.input.getMoveVector().y;
        return s;
    }
}
