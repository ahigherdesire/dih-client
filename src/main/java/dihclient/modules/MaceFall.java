package dihclient.modules;

/**
 * How far the player has fallen in the current drop, as the server will count it for a mace smash.
 *
 * <p>The client's own {@code fallDistance} only grows while descending and nothing resets it when a Wind Burst or a
 * wind charge throws the player back up (the server resets its copy after a smash, the client never hears of it).
 * So a chain of smashes looks like one endless fall. This measures each drop from its highest point instead: any
 * upward motion, landing, water, ladder or flight starts a new drop.
 */
final class MaceFall {
    private double topY = Double.NaN;

    /**
     * Feeds one tick. {@code grounded} covers anything that ends a fall (ground, water, climbing, flight, riding).
     * Returns true while the player is not yet falling in a fresh drop (rising, grounded or just started), which is
     * when a smash from the previous drop stops counting.
     */
    boolean update(double y, double velocityY, boolean grounded) {
        boolean restart = grounded || velocityY > 0.0D || Double.isNaN(topY) || y > topY;
        if (restart) topY = y;
        return restart;
    }

    /** Blocks fallen in the current drop, never more than the client's own count. */
    double distance(double y, double clientFallDistance) {
        if (Double.isNaN(topY)) return 0.0D;
        return Math.max(0.0D, Math.min(clientFallDistance, topY - y));
    }

    void reset() {
        topY = Double.NaN;
    }
}
