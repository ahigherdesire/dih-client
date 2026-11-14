package baritone.ai.director;

/** When a run hands control back by itself: a stranger close by, low health with nothing to eat, or the player moving. */
public final class AutoStop {

    public static final double STRANGER_RADIUS = 12;
    public static final float LOW_HEALTH = 6;
    public static final long TAKEOVER_MILLIS = 1000;

    /** {@code stranger} is the nearest player who isn't trusted or a friend, or null. */
    public record Vitals(float health, boolean hasFood, String stranger, double strangerDistance, long movementHeldMillis) {
    }

    private AutoStop() {
    }

    /** Why to stop, or null to carry on. */
    public static String check(Vitals v) {
        if (v.stranger() != null && v.strangerDistance() < STRANGER_RADIUS) {
            return v.stranger() + " came within " + (int) STRANGER_RADIUS + " blocks";
        }
        if (v.health() <= LOW_HEALTH && !v.hasFood()) {
            return "Low health (" + Math.round(v.health()) + "/20) and no food";
        }
        if (v.movementHeldMillis() > TAKEOVER_MILLIS) {
            return "You took control";
        }
        return null;
    }
}
