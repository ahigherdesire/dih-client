package dihclient.schematic;

import net.minecraft.world.phys.Vec3;

/**
 * The math behind moving a placement with the mouse, ported from Autism's {@code AiPlacementControls}: depth along
 * the look ray, the point at that depth, and where the ray meets a height. There is no survival reach limit; world
 * bounds are checked by the caller.
 */
public final class PlacementControls {

    /** The nearest and farthest a placement can be pushed along the look ray. */
    public static final double MIN_DEPTH = 0.5;
    public static final double MAX_DEPTH = 5.999997E7F;

    private PlacementControls() {}

    /**
     * @return {@code current} pushed by a scroll, kept between {@link #MIN_DEPTH} and {@link #MAX_DEPTH}
     */
    public static double depth(double current, double scroll) {
        return Math.max(MIN_DEPTH, Math.min(MAX_DEPTH, current + scroll));
    }

    /**
     * @return the point {@code distance} along the ray
     */
    public static Vec3 along(Vec3 eye, Vec3 direction, double distance) {
        return eye.add(direction.normalize().scale(distance));
    }

    /**
     * @return how far along the ray it reaches height {@code y}, or {@code fallback} if it never does in range
     */
    public static double distanceToHeight(Vec3 eye, Vec3 direction, double y, double fallback) {
        double d = Math.abs(direction.y) > 1.0E-7 ? (y - eye.y) / direction.y : -1.0;
        return Double.isFinite(d) && d >= MIN_DEPTH && d <= MAX_DEPTH ? d : fallback;
    }
}
