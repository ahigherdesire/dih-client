package baritone.acquire.planner;

import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Plan;

/**
 * Turns "N of X" plus the current inventory into an ordered list of steps.
 *
 * <p>SCAFFOLD: the planner agent replaces the body of {@link #plan}. Keep the constructor and the
 * signature. Pure logic, no Minecraft classes, so it runs in plain unit tests.
 */
public final class AcquirePlanner {
    private final Knowledge knowledge;
    private final WorldView world;
    private final PlannerOptions options;

    public AcquirePlanner(Knowledge knowledge, WorldView world, PlannerOptions options) {
        this.knowledge = knowledge;
        this.world = world;
        this.options = options;
    }

    /** Never throws for unknown or impossible items; reports them in {@link Plan#missing()}. Does not modify {@code inventory}. */
    public Plan plan(String item, int count, InventorySnapshot inventory) {
        throw new UnsupportedOperationException("AcquirePlanner is not implemented yet");
    }
}
