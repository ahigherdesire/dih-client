package dihclient.util;

import net.minecraft.client.player.LocalPlayer;

public final class DihKillAuraRotation {

    public static final float TURN_SPEED = 72.0f;

    public static final float WIND_DOWN_MAX_YAW_STEP = 37.0f;
    public static final float WIND_DOWN_MAX_PITCH_STEP = 37.0f;
    public static final float RESET_THRESHOLD = 2.0f;
    public static final int TICKS_UNTIL_RESET = 5;

    public static final int PRIORITY_BED_DEFENDER = 30;
    public static final int PRIORITY_SURROUND = 25;
    public static final int PRIORITY_ANCHOR_AURA = 20;
    public static final int PRIORITY_CRYSTAL_AURA = 18;
    public static final int PRIORITY_AUTO_TRAP = 15;
    public static final int PRIORITY_KILL_AURA = 10;
    public static final int PRIORITY_AUTO_FARM = 5;

    public static final String OWNER_BED_DEFENDER = "bed-defender";
    public static final String OWNER_SURROUND = "surround";
    public static final String OWNER_CRYSTAL_AURA = "crystal-aura";
    public static final String OWNER_ANCHOR_AURA = "anchor-aura";
    public static final String OWNER_AUTO_TRAP = "auto-trap";
    public static final String OWNER_KILL_AURA = "kill-aura";
    public static final String OWNER_AUTO_FARM = "auto-farm";

    private static final DihHumanRotation.Stream STREAM = new DihHumanRotation.Stream();
    private static DihRotationUtil.Rotation currentRotation = null;
    private static DihRotationUtil.Rotation targetRotation = null;

    private static String owner = null;

    private static String tickWinner = null;
    private static int tickWinnerPriority = Integer.MIN_VALUE;
    private static int arbitrationTick = Integer.MIN_VALUE;

    private static int streamStepTick = Integer.MIN_VALUE;

    private static final float PIN_HOLD_MAX_DEGREES = 0.05F;

    private static int resetTicks = 0;

    private static final int WIND_DOWN_MAX_TICKS = 27;
    private static int windDownTicks = 0;

    private static boolean windingDown = false;

    private DihKillAuraRotation() {
    }

    public static void setTarget(DihRotationUtil.Rotation rotation) {
        setTarget(OWNER_KILL_AURA, PRIORITY_KILL_AURA, rotation);
    }

    public static void setTarget(String ownerId, int priority, DihRotationUtil.Rotation rotation) {
        if (ownerId == null || rotation == null) return;
        int tick = DihSharedState.get().getClientTickCounter();
        if (tick != arbitrationTick) {
            arbitrationTick = tick;
            tickWinner = null;
            tickWinnerPriority = Integer.MIN_VALUE;
        }
        if (tickWinner != null && priority <= tickWinnerPriority) return;
        tickWinner = ownerId;
        tickWinnerPriority = priority;
        owner = ownerId;
        targetRotation = rotation;
        resetTicks = TICKS_UNTIL_RESET;
        windingDown = false;
    }

    public static String currentOwner() {
        return owner;
    }

    public static void reset() {
        currentRotation = null;
        targetRotation = null;
        owner = null;
        tickWinner = null;
        tickWinnerPriority = Integer.MIN_VALUE;
        arbitrationTick = Integer.MIN_VALUE;
        streamStepTick = Integer.MIN_VALUE;
        resetTicks = 0;
        windDownTicks = 0;
        windingDown = false;
        DihHumanRotation.clear(STREAM);
    }

    public static void beginWindDown(String ownerId) {
        if (owner != null && !owner.equals(ownerId)) return;
        targetRotation = null;
        resetTicks = 0;

        windingDown = currentRotation != null;
    }

    public static boolean isWindingDown() {
        return windingDown && currentRotation != null;
    }

    public static boolean hasCurrentRotation() {
        return currentRotation != null;
    }

    public static DihRotationUtil.Rotation getCurrentRotation() {
        return currentRotation;
    }

    public static void update(String ownerId, LocalPlayer player) {
        update(ownerId, player, TURN_SPEED, TURN_SPEED);
    }

    public static void update(String ownerId, LocalPlayer player, boolean pinQuiet) {
        update(ownerId, player, TURN_SPEED, TURN_SPEED, DihHumanRotation.MotionProfile.STANDARD, pinQuiet);
    }

    public static void update(String ownerId, LocalPlayer player, float maxYawStep, float maxPitchStep) {
        update(ownerId, player, maxYawStep, maxPitchStep, DihHumanRotation.MotionProfile.STANDARD);
    }

    public static void update(String ownerId, LocalPlayer player, float maxYawStep, float maxPitchStep,
                              DihHumanRotation.MotionProfile profile) {
        update(ownerId, player, maxYawStep, maxPitchStep, profile, false);
    }

    private static void update(String ownerId, LocalPlayer player, float maxYawStep, float maxPitchStep,
                               DihHumanRotation.MotionProfile profile, boolean pinQuiet) {
        if (player == null) {
            reset();
            return;
        }
        if (owner != null && !owner.equals(ownerId)) return;
        int tick = DihSharedState.get().getClientTickCounter();

        if (tick == streamStepTick) return;
        streamStepTick = tick;

        DihRotationUtil.Rotation playerRotation = DihRotationUtil.playerRotation(player);
        if (targetRotation == null || resetTicks <= 0) {
            targetRotation = null;
            if (currentRotation == null) {
                windDownTicks = 0;
                windingDown = false;
                return;
            }

            if (windDownTicks <= 0) windDownTicks = WIND_DOWN_MAX_TICKS;

            DihRotationUtil.Rotation next = DihHumanRotation.step(
                STREAM, playerRotation, WIND_DOWN_MAX_YAW_STEP, WIND_DOWN_MAX_PITCH_STEP,
                DihRotationUtil.sensitivityGcd(), false);
            windDownTicks--;

            if (DihRotationUtil.rotationAngleTo(next, playerRotation) <= RESET_THRESHOLD
                || windDownTicks <= 0) {

                float fixedYaw = currentRotation.yaw()
                    + DihRotationUtil.angleDifference(player.getYRot(), currentRotation.yaw());
                player.setYRot(fixedYaw);
                player.yBob = fixedYaw;
                player.yBobO = fixedYaw;
                currentRotation = null;
                owner = null;
                windDownTicks = 0;
                windingDown = false;
                DihHumanRotation.clear(STREAM);
            } else {
                currentRotation = next;
            }
            return;
        }

        if (!DihHumanRotation.isInitialized(STREAM)) {

            DihRotationUtil.Rotation seedFrom = currentRotation;
            if (seedFrom == null) {
                DihServerRotationView.WireSnapshot wire = DihServerRotationView.snapshot();
                seedFrom = wire.initialized()
                    ? new DihRotationUtil.Rotation(wire.currentYaw(), wire.currentPitch())
                    : playerRotation;
            }
            DihHumanRotation.seed(STREAM, seedFrom);
        }

        if (pinQuiet && currentRotation != null
            && DihRotationUtil.rotationAngleTo(currentRotation, targetRotation)
                <= PIN_HOLD_MAX_DEGREES) {

            resetTicks--;
            return;
        }
        currentRotation = DihHumanRotation.step(
            STREAM, targetRotation, maxYawStep, maxPitchStep, DihRotationUtil.sensitivityGcd(),
            false, profile);

        resetTicks--;
    }
}
