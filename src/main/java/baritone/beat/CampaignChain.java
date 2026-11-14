package baritone.beat;

import baritone.acquire.model.Goal;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Location;
import baritone.acquire.model.Plan;
import baritone.acquire.planner.AcquirePlanner;
import baritone.acquire.planner.PlanEffects;

import java.util.ArrayList;
import java.util.List;

/**
 * The whole {@code #beat} route planned before anything runs: each phase from where the one before leaves you, so
 * {@code #beat plan} can print every step, phase by phase.
 */
public final class CampaignChain {

    /** One phase's plans (the gear phase has one per missing piece). */
    public record PhasePlan(Phase phase, List<Plan> plans) {
        public int steps() {
            return plans.stream().mapToInt(plan -> plan.steps().size()).sum();
        }

        public boolean complete() {
            return plans.stream().allMatch(Plan::complete);
        }
    }

    private CampaignChain() {
    }

    /** Plans {@code from} and every phase after it; stops after the first phase that can't be planned. */
    public static List<PhasePlan> plan(AcquirePlanner planner, InventorySnapshot inventory, Location here, Phase from) {
        List<PhasePlan> chain = new ArrayList<>();
        InventorySnapshot inv = inventory.copy();
        Location at = here;
        for (Phase phase : Phase.values()) {
            if (phase.ordinal() < from.ordinal()) continue;
            List<Plan> plans = new ArrayList<>();
            InventorySnapshot held = inv;
            for (Goal goal : phase.goals(held::count)) {
                Plan plan = planner.plan(goal, inv, at);
                plans.add(plan);
                if (!plan.complete()) break;
                inv = PlanEffects.inventoryAfter(plan, inv);
                at = PlanEffects.locationAfter(plan, at);
            }
            PhasePlan planned = new PhasePlan(phase, plans);
            chain.add(planned);
            if (!planned.complete()) break;
        }
        return chain;
    }

    /** Chat lines: a header per phase, then its numbered steps; the reason where planning stopped. */
    public static List<String> explain(List<PhasePlan> chain) {
        List<String> lines = new ArrayList<>();
        int total = 0;
        for (PhasePlan planned : chain) {
            Phase phase = planned.phase();
            lines.add(phase.number() + ": " + phase.label() + " (" + (planned.steps() == 0 ? "done" : planned.steps() + " steps") + ")");
            int n = 0;
            for (Plan plan : planned.plans()) {
                for (var step : plan.steps()) lines.add("  " + (++n) + ". " + step.describe());
                for (String missing : plan.missing()) lines.add("  can't plan: " + missing);
            }
            total += planned.steps();
        }
        boolean complete = !chain.isEmpty() && chain.get(chain.size() - 1).complete()
                && chain.get(chain.size() - 1).phase() == Phase.DRAGON;
        lines.add(complete ? "The whole route: " + total + " steps." : "The route stops there.");
        return lines;
    }
}
