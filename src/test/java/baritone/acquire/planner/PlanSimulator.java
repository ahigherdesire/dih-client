package baritone.acquire.planner;

import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Plan;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs a plan against an inventory the way the executor would, step by step, and fails the test as soon
 * as a step could not run: missing inputs, tool, fuel or station, or an untilCount that doesn't match.
 * Written independently of the planner's own replay on purpose.
 */
final class PlanSimulator {
    private static final String TABLE = "minecraft:crafting_table";

    record Result(Map<String, Integer> inventory, Map<String, Integer> mined, Map<String, Integer> crafted) {
        int count(String item) {
            return inventory.getOrDefault(item, 0);
        }

        int mined(String item) {
            return mined.getOrDefault(item, 0);
        }

        int crafted(String item) {
            return crafted.getOrDefault(item, 0);
        }
    }

    private PlanSimulator() {
    }

    static Result simulate(Plan plan, InventorySnapshot start, Knowledge knowledge, WorldView world) {
        Map<String, Integer> inv = new HashMap<>(start.asMap());
        Map<String, Integer> mined = new HashMap<>();
        Map<String, Integer> crafted = new HashMap<>();
        Set<String> placed = new HashSet<>();
        Set<String> setUp = new HashSet<>();
        List<Step> steps = plan.steps();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            String at = "step " + (i + 1) + " (" + step.describe() + ")";
            switch (step) {
                case Step.Mine m -> {
                    ToolReq tool = m.tool();
                    if (tool.required()) {
                        assertTrue(knowledge.toolsOf(tool.type(), tool.minTier()).stream().anyMatch(t -> count(inv, t) > 0),
                                at + ": no " + tool.type() + " of tier " + tool.minTier());
                    }
                    int gained = m.untilCount() - count(inv, m.item());
                    assertTrue(gained > 0, at + ": nothing to mine");
                    assertTrue(m.expectedBlocks() > 0, at);
                    inv.put(m.item(), m.untilCount());
                    mined.merge(m.item(), gained, Integer::sum);
                    setUp.clear();
                }
                case Step.Kill k -> {
                    int gained = k.untilCount() - count(inv, k.item());
                    assertTrue(gained > 0, at + ": nothing to kill for");
                    inv.put(k.item(), k.untilCount());
                    setUp.clear();
                }
                case Step.Craft c -> {
                    CraftSource r = c.recipe();
                    if (r.needsTable()) assertTrue(setUp.contains(TABLE), at + ": no crafting table set up");
                    assertEquals(r.ingredients().size(), c.inputs().size(), at);
                    for (int k = 0; k < r.ingredients().size(); k++) {
                        Ingredient ing = r.ingredients().get(k);
                        assertTrue(ing.accepts(c.inputs().get(k)), at + ": wrong input " + c.inputs().get(k));
                        take(inv, c.inputs().get(k), ing.count() * c.times(), at);
                    }
                    int made = c.times() * r.outputCount();
                    inv.merge(r.output(), made, Integer::sum);
                    crafted.merge(r.output(), made, Integer::sum);
                    assertEquals(c.untilCount(), count(inv, r.output()), at + ": untilCount");
                }
                case Step.Smelt s -> {
                    SmeltSource r = s.recipe();
                    assertTrue(setUp.contains(r.station()), at + ": no " + r.station() + " set up");
                    assertTrue(r.input().accepts(s.input()), at);
                    assertNotEquals(s.input(), s.fuel(), at + ": burns its own input");
                    Integer burn = knowledge.fuels().get(s.fuel());
                    assertNotNull(burn, at + ": " + s.fuel() + " is not a fuel");
                    assertTrue((long) burn * s.fuelCount() >= (long) s.times() * r.cookTicks(), at + ": not enough fuel");
                    take(inv, s.input(), s.times(), at);
                    take(inv, s.fuel(), s.fuelCount(), at);
                    inv.merge(r.output(), s.times() * r.outputCount(), Integer::sum);
                    assertEquals(s.untilCount(), count(inv, r.output()), at + ": untilCount");
                }
                case Step.PlaceStation p -> {
                    if (!placed.contains(p.station()) && !world.stationNearby(p.station())) take(inv, p.station(), 1, at);
                    placed.add(p.station());
                    setUp.add(p.station());
                }
            }
        }
        inv.values().removeIf(v -> v == 0);
        return new Result(inv, mined, crafted);
    }

    private static int count(Map<String, Integer> inv, String item) {
        return inv.getOrDefault(item, 0);
    }

    private static void take(Map<String, Integer> inv, String item, int n, String at) {
        int have = count(inv, item);
        assertTrue(have >= n, at + ": needs " + n + " " + item + ", has " + have);
        inv.put(item, have - n);
    }
}
