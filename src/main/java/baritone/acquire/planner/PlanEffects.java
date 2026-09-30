package baritone.acquire.planner;

import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Location;
import baritone.acquire.model.Plan;
import baritone.acquire.model.Step;

import java.util.Map;

/**
 * Where a plan leaves you: the inventory once every step has run, and the place you end up. Lets a chain of plans
 * ({@code #beat plan}) plan each phase from the end of the one before.
 */
public final class PlanEffects {

    private PlanEffects() {
    }

    /** {@code start} with every step of {@code plan} applied (a copy; {@code start} is untouched). */
    public static InventorySnapshot inventoryAfter(Plan plan, InventorySnapshot start) {
        InventorySnapshot inv = start.copy();
        for (Step step : plan.steps()) {
            switch (step) {
                case Step.Mine m -> inv.add(m.item(), Math.max(0, m.untilCount() - inv.count(m.item())));
                case Step.Kill k -> inv.add(k.item(), Math.max(0, k.untilCount() - inv.count(k.item())));
                case Step.Barter b -> {
                    inv.take(b.currency(), b.trades());
                    inv.add(b.item(), Math.max(0, b.untilCount() - inv.count(b.item())));
                }
                case Step.CollectEgg e -> inv.add(e.item(), Math.max(0, e.untilCount() - inv.count(e.item())));
                case Step.Craft c -> {
                    CraftSource r = c.recipe();
                    for (int i = 0; i < r.ingredients().size(); i++) {
                        Ingredient ing = r.ingredients().get(i);
                        inv.take(c.inputs().get(i), ing.count() * c.times());
                    }
                    inv.add(r.output(), c.times() * r.outputCount());
                }
                case Step.Smelt s -> {
                    inv.take(s.input(), s.times());
                    inv.take(s.fuel(), s.fuelCount());
                    inv.add(s.recipe().output(), s.times() * s.recipe().outputCount());
                }
                case Step.PlaceStation p -> inv.take(p.station(), Math.min(1, inv.count(p.station())));
                case Step.RetrieveStation r -> inv.add(r.station(), 1);
                case Step.Travel t -> {
                    for (Map.Entry<String, Integer> use : t.consumes().entrySet()) inv.take(use.getKey(), use.getValue());
                }
                case Step.Locate l -> {
                }
                case Step.SlayDragon d -> {
                }
            }
        }
        return inv;
    }

    /** Where you are after {@code plan}, starting at {@code start}. */
    public static Location locationAfter(Plan plan, Location start) {
        Location at = start;
        for (Step step : plan.steps()) {
            if (step instanceof Step.Travel t) at = t.to();
            else if (step instanceof Step.Locate l) at = l.site();
        }
        return at;
    }
}
