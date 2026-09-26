package baritone.acquire.exec;

import baritone.acquire.model.Plan;
import baritone.acquire.model.Step;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Where one {@code #acquire} is in its plan: the current step, and how many times it has re-planned.
 * Pure (inventory counts come in as a function) so the skip and re-plan rules are unit-tested.
 */
final class AcquireRun {
    final String goal;
    final int count;
    private Plan plan;
    private int index = -1;
    private int replans;

    AcquireRun(String goal, int count, Plan plan) {
        this.goal = goal;
        this.count = count;
        this.plan = plan;
    }

    Plan plan() {
        return plan;
    }

    /** Index of the current step, -1 before the first, {@code steps().size()} once used up. */
    int index() {
        return index;
    }

    int replans() {
        return replans;
    }

    int stepCount() {
        return plan.steps().size();
    }

    Step current() {
        List<Step> steps = plan.steps();
        return index >= 0 && index < steps.size() ? steps.get(index) : null;
    }

    /**
     * Moves to the next step that still has work and returns its index, or -1 when the plan is used up.
     * A step is skipped when the inventory already holds its {@code untilCount}. A {@link Step.PlaceStation}
     * is skipped when none of the steps it serves has work left.
     */
    int advance(ToIntFunction<String> have) {
        List<Step> steps = plan.steps();
        for (int i = index + 1; i < steps.size(); i++) {
            Step step = steps.get(i);
            boolean work = step instanceof Step.PlaceStation ps ? stationNeeded(i, ps.station(), have) : !met(step, have);
            if (work) {
                index = i;
                return i;
            }
        }
        index = steps.size();
        return -1;
    }

    /** Whether a step after {@code stationIndex} (up to the next set-up of the same station) uses it and has work. */
    boolean stationNeeded(int stationIndex, String station, ToIntFunction<String> have) {
        List<Step> steps = plan.steps();
        for (int j = stationIndex + 1; j < steps.size(); j++) {
            Step step = steps.get(j);
            if (step instanceof Step.PlaceStation ps) {
                if (ps.station().equals(station)) return false;
                continue;
            }
            if (usesStation(step, station) && !met(step, have)) return true;
        }
        return false;
    }

    static boolean usesStation(Step step, String station) {
        if (step instanceof Step.Craft craft) return craft.recipe().needsTable() && station.equals(CRAFTING_TABLE);
        if (step instanceof Step.Smelt smelt) return station.equals(smelt.recipe().station());
        return false;
    }

    static boolean met(Step step, ToIntFunction<String> have) {
        return !(step instanceof Step.PlaceStation) && have.applyAsInt(step.item()) >= step.untilCount();
    }

    boolean goalMet(ToIntFunction<String> have) {
        return have.applyAsInt(goal) >= count;
    }

    /** Swaps in a new plan and counts one re-plan. */
    void replace(Plan next) {
        plan = next;
        index = -1;
        replans++;
    }

    /** Swaps in a new plan without counting a re-plan (after a food detour, which is not a failure). */
    void resume(Plan next) {
        plan = next;
        index = -1;
    }

    /** "acquiring 3 iron_ingot: step 4/9, smelt 3 raw_iron into iron_ingot (...) (2/3)". */
    String status(ToIntFunction<String> have) {
        String head = "acquiring " + count + " " + Step.shortId(goal);
        Step step = current();
        if (step == null) return head;
        String line = head + ": step " + (index + 1) + "/" + stepCount() + ", " + step.describe();
        if (!(step instanceof Step.PlaceStation)) {
            line += " (" + have.applyAsInt(step.item()) + "/" + step.untilCount() + ")";
        }
        return line;
    }

    static final String CRAFTING_TABLE = "minecraft:crafting_table";
}
