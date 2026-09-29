package baritone.acquire.planner;

/**
 * @param allowPlaceStations may place a crafting table or furnace when none is nearby (else it must find one)
 * @param allowKill          may plan kill steps at all
 * @param maxDepth           recursion limit for sub-goals
 * @param maxSteps           give up (with a "missing" reason) past this many steps
 * @param gearCheckpoints    before the first trip to the Nether or the End, get the gear for it (armour, a shield and a
 *                           sword; a bow, arrows and blocks)
 */
public record PlannerOptions(boolean allowPlaceStations, boolean allowKill, int maxDepth, int maxSteps, boolean gearUp,
                             boolean gearCheckpoints) {
    public PlannerOptions(boolean allowPlaceStations, boolean allowKill, int maxDepth, int maxSteps) {
        this(allowPlaceStations, allowKill, maxDepth, maxSteps, true);
    }

    public PlannerOptions(boolean allowPlaceStations, boolean allowKill, int maxDepth, int maxSteps, boolean gearUp) {
        this(allowPlaceStations, allowKill, maxDepth, maxSteps, gearUp, true);
    }

    public static final PlannerOptions DEFAULT = new PlannerOptions(true, true, 24, 400, true, true);
}
