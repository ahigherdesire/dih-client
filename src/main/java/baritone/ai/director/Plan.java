package baritone.ai.director;

import java.util.List;

/** Tool calls to run in order. No steps means the objective is done already, or can't be done; the summary says which. */
public record Plan(List<PlanStep> steps, String summary) {

    public Plan {
        steps = List.copyOf(steps);
        summary = summary == null ? "" : summary.trim();
    }
}
