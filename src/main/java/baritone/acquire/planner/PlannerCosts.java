package baritone.acquire.planner;

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
    // Smelting costs the recipe's cookTicks per item.

    private PlannerCosts() {
    }

    /** Ticks to walk {@code distance} blocks. Unknown (infinite or NaN) distances count as {@link #UNKNOWN_DISTANCE}. */
    public static double travel(double distance) {
        if (Double.isNaN(distance) || Double.isInfinite(distance)) distance = UNKNOWN_DISTANCE;
        return Math.max(0, distance) * TICKS_PER_BLOCK;
    }
}
