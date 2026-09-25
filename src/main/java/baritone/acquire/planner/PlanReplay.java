package baritone.acquire.planner;

import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replays drafted steps from the starting inventory the way the executor will run them: fills in every
 * step's {@code untilCount} and checks each step can run (inputs held, tool held, station set up).
 * Also tidies a finished plan ({@link #compact}).
 */
final class PlanReplay {
    static final String CRAFTING_TABLE = "minecraft:crafting_table";
    static final String FURNACE = "minecraft:furnace";

    /** {@code error} is null when every step could run; otherwise {@code steps} is the prefix that could. */
    record Result(List<Step> steps, String error) {
        boolean ok() {
            return error == null;
        }
    }

    private final Knowledge knowledge;
    private final WorldView world;
    private final InventorySnapshot start;

    PlanReplay(Knowledge knowledge, WorldView world, InventorySnapshot start) {
        this.knowledge = knowledge;
        this.world = world;
        this.start = start;
    }

    static String stationOf(SmeltSource recipe) {
        return recipe.station() == null ? FURNACE : recipe.station();
    }

    Result run(List<PlanState.Entry> entries) {
        InventorySnapshot inv = start.copy();
        Set<String> ready = new HashSet<>();
        Set<String> active = new HashSet<>();
        List<Step> out = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            Step step = entries.get(i).step();
            int gained = entries.get(i).gained();
            String error = null;
            switch (step) {
                case Step.Mine m -> {
                    if (gained <= 0) error = "mines nothing";
                    else if (!toolHeld(inv, m.tool())) error = "no " + m.tool().type() + " to mine with";
                    else {
                        inv.add(m.item(), gained);
                        out.add(new Step.Mine(m.blocks(), m.item(), inv.count(m.item()), m.tool(), m.expectedBlocks()));
                        active.clear();
                    }
                }
                case Step.Kill k -> {
                    if (gained <= 0) error = "gains nothing";
                    else {
                        inv.add(k.item(), gained);
                        out.add(new Step.Kill(k.entity(), k.item(), inv.count(k.item()), k.expectedKills()));
                        active.clear();
                    }
                }
                case Step.Craft c -> {
                    CraftSource r = c.recipe();
                    if (r.needsTable() && !active.contains(CRAFTING_TABLE)) error = "no crafting table set up";
                    else if (c.inputs().size() != r.ingredients().size()) error = "inputs don't match the recipe";
                    for (int k = 0; error == null && k < r.ingredients().size(); k++) {
                        Ingredient ing = r.ingredients().get(k);
                        String input = c.inputs().get(k);
                        int total = ing.count() * c.times();
                        if (total <= 0) continue;
                        if (!ing.accepts(input)) error = input + " doesn't fit the recipe";
                        else if (inv.take(input, total) < total) error = "short of " + input;
                    }
                    if (error == null) {
                        inv.add(r.output(), c.times() * r.outputCount());
                        out.add(new Step.Craft(r, c.times(), c.inputs(), inv.count(r.output())));
                    }
                }
                case Step.Smelt sm -> {
                    SmeltSource r = sm.recipe();
                    if (!active.contains(stationOf(r))) error = "no " + stationOf(r) + " set up";
                    else if (!r.input().accepts(sm.input())) error = sm.input() + " doesn't fit the recipe";
                    else if (inv.take(sm.input(), sm.times()) < sm.times()) error = "short of " + sm.input();
                    else if (inv.take(sm.fuel(), sm.fuelCount()) < sm.fuelCount()) error = "short of fuel " + sm.fuel();
                    else {
                        inv.add(r.output(), sm.times() * r.outputCount());
                        out.add(new Step.Smelt(r, sm.times(), sm.input(), sm.fuel(), sm.fuelCount(), inv.count(r.output())));
                    }
                }
                case Step.PlaceStation p -> {
                    String station = p.station();
                    if (!ready.contains(station)) {
                        // First set-up: a nearby one, or the item from the inventory.
                        if (world.stationNearby(station) || inv.take(station, 1) == 1) ready.add(station);
                        else error = "no " + station + " to place";
                    }
                    if (error == null) {
                        active.add(station);
                        out.add(p);
                    }
                }
            }
            if (error != null) return new Result(out, "step " + (i + 1) + " (" + step.describe() + "): " + error);
        }
        return new Result(out, null);
    }

    /**
     * Tidies a plan that replays cleanly: a craft repeated later with the same recipe and inputs merges
     * into the earliest one, and station set-ups nothing uses any more are dropped. Each change is kept
     * only if the plan still replays cleanly. Returns the finished steps.
     */
    List<Step> compact(List<PlanState.Entry> entries) {
        List<PlanState.Entry> cur = new ArrayList<>(entries);
        for (int j = 1; j < cur.size(); j++) {
            if (!(cur.get(j).step() instanceof Step.Craft later)) continue;
            for (int i = 0; i < j; i++) {
                if (!(cur.get(i).step() instanceof Step.Craft earlier) || !earlier.recipe().equals(later.recipe())
                        || !earlier.inputs().equals(later.inputs())) continue;
                List<PlanState.Entry> merged = new ArrayList<>(cur);
                merged.set(i, new PlanState.Entry(
                        new Step.Craft(earlier.recipe(), earlier.times() + later.times(), earlier.inputs(), 0), 0));
                merged.remove(j);
                if (run(merged).ok()) {
                    cur = merged;
                    j--;
                    break;
                }
            }
        }
        for (int i = cur.size() - 1; i >= 0; i--) {
            if (cur.get(i).step() instanceof Step.PlaceStation p && !usedBeforeMoving(cur, i, p.station())) {
                List<PlanState.Entry> without = new ArrayList<>(cur);
                without.remove(i);
                if (run(without).ok()) cur = without;
            }
        }
        return run(cur).steps();
    }

    private static boolean usedBeforeMoving(List<PlanState.Entry> entries, int index, String station) {
        for (int k = index + 1; k < entries.size(); k++) {
            switch (entries.get(k).step()) {
                case Step.Mine m -> {
                    return false;
                }
                case Step.Kill kill -> {
                    return false;
                }
                case Step.Craft c -> {
                    if (c.recipe().needsTable() && CRAFTING_TABLE.equals(station)) return true;
                }
                case Step.Smelt sm -> {
                    if (stationOf(sm.recipe()).equals(station)) return true;
                }
                case Step.PlaceStation p -> {
                }
            }
        }
        return false;
    }

    private boolean toolHeld(InventorySnapshot inv, ToolReq tool) {
        if (tool == null || !tool.required() || tool.type() == null) return true;
        List<String> tools = knowledge.toolsOf(tool.type(), tool.minTier());
        if (tools == null) return false;
        for (String t : tools) if (inv.count(t) > 0) return true;
        return false;
    }
}
