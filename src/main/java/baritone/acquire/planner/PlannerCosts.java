package baritone.acquire.planner;

import baritone.acquire.model.Location;

/**
 * The planner's rough costs in ticks, in one place so they are easy to tune. They only compare
 * alternatives; nobody should read them as a time estimate.
 */
public final class PlannerCosts {
    /** Walking, per block of distance. */
    public static final double TICKS_PER_BLOCK = 5;
    /** Distance assumed when the world view knows no block or entity of the type: still plannable, but penalised. */
    public static final double UNKNOWN_DISTANCE = 128;
    /**
     * Distance assumed for an unknown block that is usually player-placed (crafting_table, wall_torch, wool),
     * so making the item wins unless the world view knows one is close.
     */
    public static final double UNKNOWN_PLACED_DISTANCE = 512;
    /** Breaking one block. */
    public static final double BREAK_TICKS = 30;
    /** One crafting action. */
    public static final double CRAFT_TICKS = 10;
    /** One kill, not counting the walk to the first mob. */
    public static final double KILL_TICKS = 100;
    /** Setting up (or walking back to) a crafting table or furnace. */
    public static final double STATION_TICKS = 20;
    /** Break and pick up a station already placed by this acquire. */
    public static final double RETRIEVE_TICKS = 12;
    // Smelting costs the recipe's cookTicks per item.
    /** Going through a portal once it is there (walking to it included). */
    public static final double PORTAL_TICKS = 2400;
    /** Finding a nether fortress and getting there. */
    public static final double FORTRESS_TICKS = 12000;
    /** Finding the stronghold's portal room and getting there. */
    public static final double STRONGHOLD_TICKS = 16000;
    /** The dragon fight. */
    public static final double DRAGON_TICKS = 12000;

    private PlannerCosts() {
    }

    /** A rough extra cost for a source away from the Overworld, so options there sort after Overworld ones. */
    public static double away(Location location) {
        if (location == null || location == Location.OVERWORLD) return 0;
        return switch (location) {
            case NETHER -> PORTAL_TICKS;
            case FORTRESS -> PORTAL_TICKS + FORTRESS_TICKS;
            case STRONGHOLD -> STRONGHOLD_TICKS;
            default -> STRONGHOLD_TICKS + PORTAL_TICKS;
        };
    }

    /** Ticks to walk {@code distance} blocks. Unknown (infinite or NaN) distances count as {@link #UNKNOWN_DISTANCE}. */
    public static double travel(double distance) {
        if (Double.isNaN(distance) || Double.isInfinite(distance)) distance = UNKNOWN_DISTANCE;
        return Math.max(0, distance) * TICKS_PER_BLOCK;
    }
}
