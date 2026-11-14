package baritone.acquire.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The planner's answer.
 *
 * @param goal    item id
 * @param count   how many of it the user asked for (total in inventory)
 * @param steps   in execution order; empty when the inventory already has enough
 * @param missing human-readable reasons the plan is incomplete ("no known source for minecraft:elytra");
 *                empty when the plan is complete
 * @param cost    the planner's estimate in rough ticks; only used to compare alternatives
 */
public record Plan(String goal, int count, List<Step> steps, List<String> missing, double cost) {
    public Plan {
        steps = List.copyOf(steps);
        missing = List.copyOf(missing);
    }

    public boolean complete() {
        return missing.isEmpty();
    }

    public boolean alreadyDone() {
        return complete() && steps.isEmpty();
    }

    /** Numbered lines for chat: "1. mine 3 oak_log for oak_log". */
    public List<String> explain() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) lines.add((i + 1) + ". " + steps.get(i).describe());
        for (String m : missing) lines.add("missing: " + m);
        return lines;
    }
}
