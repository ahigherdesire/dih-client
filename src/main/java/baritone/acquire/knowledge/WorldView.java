package baritone.acquire.knowledge;

/**
 * What the planner may ask about the world to estimate costs. Implementations read Baritone's world
 * cache and loaded entities; tests use fixed values.
 */
public interface WorldView {

    /** Distance in blocks to the nearest known {@code block}, or {@link Double#POSITIVE_INFINITY} if none is known. */
    double distanceToBlock(String block);

    /** Distance to the nearest loaded entity of type {@code entity}, or {@link Double#POSITIVE_INFINITY}. */
    double distanceToEntity(String entity);

    /** Whether a usable {@code station} block is close enough to walk to instead of placing one. */
    boolean stationNearby(String station);

    /** A world where nothing is known; the planner falls back to default costs. */
    WorldView UNKNOWN = new WorldView() {
        @Override
        public double distanceToBlock(String block) {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public double distanceToEntity(String entity) {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public boolean stationNearby(String station) {
            return false;
        }
    };
}
