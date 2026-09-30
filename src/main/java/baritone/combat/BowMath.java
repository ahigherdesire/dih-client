package baritone.combat;

import net.minecraft.world.phys.Vec3;

/**
 * Where to point a bow so the arrow lands on a target: drop compensation for the arrow's gravity and drag, and lead
 * for a moving target. Pure math, unit-tested against the game's own arrow step (each tick an arrow moves by its
 * velocity, then the velocity is multiplied by {@link #DRAG} and loses {@link #GRAVITY} downwards).
 *
 * <p>Angles here follow Minecraft: yaw 0 faces +Z (south) and 90 faces −X; pitch is positive looking down.
 * {@link #lowArcPitch} alone uses "degrees above the horizon", which is easier to reason about for an arc.
 */
public final class BowMath {

    public static final double GRAVITY = 0.05;
    public static final double DRAG = 0.99;
    /** Arrow speed from a fully drawn bow (blocks per tick). */
    public static final double FULL_SPEED = 3.0;
    /** Ticks of drawing for a full-power shot. */
    public static final int FULL_DRAW_TICKS = 20;

    private static final double LOG_DRAG = Math.log(DRAG);
    private static final double MIN_PITCH = -89.9;
    private static final double MAX_PITCH = 89.9;
    private static final int LEAD_ROUNDS = 8;

    private BowMath() {
    }

    /** Where to look, and how many ticks the arrow will fly. */
    public record Aim(float yaw, float pitch, double ticks) {
    }

    /** The arrow's speed after drawing for {@code drawTicks}, as the bow computes it. */
    public static double speed(int drawTicks) {
        double f = drawTicks / (double) FULL_DRAW_TICKS;
        f = (f * f + f * 2) / 3;
        return Math.min(f, 1) * FULL_SPEED;
    }

    /**
     * The flatter of the two arcs from the origin through a point {@code distance} away horizontally and
     * {@code height} above, in degrees above the horizon; NaN when no arc at this speed reaches it.
     */
    public static double lowArcPitch(double distance, double height, double speed) {
        if (speed <= 0 || distance <= 0) return Double.NaN;
        // Height at the target's distance rises with the pitch up to a peak, then falls (the lob side): find the
        // peak, then bisect the rising side.
        double lo = MIN_PITCH;
        double hi = MAX_PITCH;
        for (int i = 0; i < 100; i++) {
            double a = lo + (hi - lo) / 3;
            double b = hi - (hi - lo) / 3;
            if (heightAt(distance, a, speed) < heightAt(distance, b, speed)) lo = a;
            else hi = b;
        }
        double peak = (lo + hi) / 2;
        if (!(heightAt(distance, peak, speed) >= height)) return Double.NaN;
        lo = MIN_PITCH;
        hi = peak;
        if (heightAt(distance, lo, speed) > height) return Double.NaN;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            if (heightAt(distance, mid, speed) < height) lo = mid;
            else hi = mid;
        }
        return (lo + hi) / 2;
    }

    /**
     * Aims from {@code from} (where the arrow leaves: the eye, 0.1 lower) at {@code target}, which moves by
     * {@code velocity} each tick. Null when the target is out of range.
     */
    public static Aim aim(Vec3 from, Vec3 target, Vec3 velocity, double speed) {
        Vec3 at = target;
        double pitchUp = Double.NaN;
        double ticks = 0;
        for (int round = 0; round < LEAD_ROUNDS; round++) {
            Vec3 d = at.subtract(from);
            double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
            pitchUp = lowArcPitch(horizontal, d.y, speed);
            if (Double.isNaN(pitchUp)) return null;
            ticks = flightTicks(horizontal, pitchUp, speed);
            Vec3 next = target.add(velocity.scale(ticks));
            if (next.distanceToSqr(at) < 1e-6) {
                at = next;
                break;
            }
            at = next;
        }
        Vec3 d = at.subtract(from);
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        return new Aim(yaw, (float) -pitchUp, ticks);
    }

    /** Ticks (fractional) for an arrow at {@code pitchUp} to travel {@code distance} horizontally; +∞ if it never does. */
    public static double flightTicks(double distance, double pitchUp, double speed) {
        double vx = speed * Math.cos(Math.toRadians(pitchUp));
        if (vx <= 0) return Double.POSITIVE_INFINITY;
        // Horizontal distance after n ticks: vx * (1 - DRAG^n) / (1 - DRAG).
        double left = 1 - distance * (1 - DRAG) / vx;
        if (left <= 0) return Double.POSITIVE_INFINITY;
        double n = Math.log(left) / LOG_DRAG;
        int whole = (int) Math.floor(n);
        // Within a tick the arrow moves in a straight line, so the fraction is linear in distance, not in n.
        double before = travelled(vx, whole);
        double step = vx * Math.pow(DRAG, whole);
        return whole + (distance - before) / step;
    }

    /** The arrow's height once it has flown {@code distance} horizontally; −∞ if it never gets that far. */
    static double heightAt(double distance, double pitchUp, double speed) {
        double ticks = flightTicks(distance, pitchUp, speed);
        if (Double.isInfinite(ticks)) return Double.NEGATIVE_INFINITY;
        double vy = speed * Math.sin(Math.toRadians(pitchUp));
        int whole = (int) Math.floor(ticks);
        double y = height(vy, whole);
        double stepY = velocityAfter(vy, whole);
        return y + stepY * (ticks - whole);
    }

    private static double travelled(double vx, int ticks) {
        return vx * (1 - Math.pow(DRAG, ticks)) / (1 - DRAG);
    }

    /** Vertical speed after {@code ticks}: it settles toward −GRAVITY / (1 − DRAG). */
    private static double velocityAfter(double vy, int ticks) {
        double terminal = -GRAVITY / (1 - DRAG);
        return (vy - terminal) * Math.pow(DRAG, ticks) + terminal;
    }

    /** Height after {@code ticks} whole ticks: the sum of the vertical speeds so far. */
    private static double height(double vy, int ticks) {
        double terminal = -GRAVITY / (1 - DRAG);
        return (vy - terminal) * (1 - Math.pow(DRAG, ticks)) / (1 - DRAG) + terminal * ticks;
    }
}
