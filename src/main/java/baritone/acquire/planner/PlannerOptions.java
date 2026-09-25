package baritone.acquire.planner;

/**
 * @param allowPlaceStations may place a crafting table or furnace when none is nearby (else it must find one)
 * @param allowKill          may plan kill steps at all
 * @param maxDepth           recursion limit for sub-goals
 * @param maxSteps           give up (with a "missing" reason) past this many steps
 */
public record PlannerOptions(boolean allowPlaceStations, boolean allowKill, int maxDepth, int maxSteps) {
    public static final PlannerOptions DEFAULT = new PlannerOptions(true, true, 24, 200);
}
