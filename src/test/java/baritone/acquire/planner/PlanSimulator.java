package baritone.acquire.planner;

import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.BarterSource;
import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.KillSource;
import baritone.acquire.model.Location;
import baritone.acquire.model.MineSource;
import baritone.acquire.model.Source;
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
    private static final List<String> GOLD_ARMOUR = List.of("minecraft:golden_helmet", "minecraft:golden_chestplate",
            "minecraft:golden_leggings", "minecraft:golden_boots");

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
        return simulate(plan, start, knowledge, world, Location.OVERWORLD);
    }

    /** As above, starting in {@code from}. */
    static Result simulate(Plan plan, InventorySnapshot start, Knowledge knowledge, WorldView world, Location from) {
        Map<String, Integer> inv = new HashMap<>(start.asMap());
        Map<String, Integer> mined = new HashMap<>();
        Map<String, Integer> crafted = new HashMap<>();
        Set<String> placed = new HashSet<>();
        Set<String> setUp = new HashSet<>();
        Set<String> owned = new HashSet<>();
        boolean moved = false;
        boolean dragonDead = false;
        Location here = from;
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
                    assertTrue(canWork(here, mineLocation(knowledge, m)), at + ": mines in " + here);
                    int gained = m.untilCount() - count(inv, m.item());
                    assertTrue(gained > 0, at + ": nothing to mine");
                    assertTrue(m.expectedBlocks() > 0, at);
                    inv.put(m.item(), m.untilCount());
                    mined.merge(m.item(), gained, Integer::sum);
                    setUp.clear();
                    placed.clear();
                    moved = true;
                }
                case Step.Kill k -> {
                    assertTrue(canWork(here, killLocation(knowledge, k)), at + ": kills in " + here);
                    int gained = k.untilCount() - count(inv, k.item());
                    assertTrue(gained > 0, at + ": nothing to kill for");
                    inv.put(k.item(), k.untilCount());
                    setUp.clear();
                    placed.clear();
                    moved = true;
                }
                case Step.Barter b -> {
                    assertTrue(canWork(here, barterLocation(knowledge, b)), at + ": barters in " + here);
                    // Piglins attack a player with no gold on.
                    assertTrue(GOLD_ARMOUR.stream().anyMatch(piece -> count(inv, piece) > 0), at + ": no gold armour to wear");
                    assertTrue(b.trades() > 0, at);
                    take(inv, b.currency(), b.trades(), at);
                    int gained = b.untilCount() - count(inv, b.item());
                    assertTrue(gained > 0, at + ": nothing to barter for");
                    inv.put(b.item(), b.untilCount());
                    setUp.clear();
                    placed.clear();
                    moved = true;
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
                    if (!placed.contains(p.station()) && (moved || !world.stationNearby(p.station()))) {
                        take(inv, p.station(), 1, at);
                        owned.add(p.station());
                    }
                    placed.add(p.station());
                    setUp.add(p.station());
                }
                case Step.Travel t -> {
                    assertTrue(canWork(here, t.from()), at + ": leaves from " + here + ", not " + t.from());
                    if (t.to() == Location.NETHER && !t.consumes().isEmpty()) {
                        assertTrue(count(inv, "minecraft:flint_and_steel") > 0, at + ": nothing to light the portal with");
                        if (!t.consumes().containsKey("minecraft:obsidian")) {
                            int buckets = count(inv, "minecraft:bucket") + count(inv, "minecraft:water_bucket")
                                    + count(inv, "minecraft:lava_bucket");
                            assertTrue(buckets >= 2, at + ": casting the frame takes two buckets, has " + buckets);
                        }
                    }
                    t.consumes().forEach((item, n) -> take(inv, item, n, at));
                    assertTrue(owned.isEmpty(), at + ": leaves placed stations behind");
                    here = t.to();
                    setUp.clear();
                    placed.clear();
                    moved = true;
                }
                case Step.Locate l -> {
                    assertEquals(l.site().dimension(), here.dimension(), at + ": looks in the wrong dimension");
                    assertTrue(owned.isEmpty(), at + ": leaves placed stations behind");
                    here = l.site();
                    setUp.clear();
                    placed.clear();
                    moved = true;
                }
                case Step.SlayDragon d -> {
                    assertEquals(Location.END, here, at + ": the dragon is in the End");
                    dragonDead = true;
                }
                case Step.CollectEgg e -> {
                    assertTrue(dragonDead, at + ": the egg comes after the dragon");
                    inv.merge(e.item(), 1, Integer::sum);
                    assertEquals(e.untilCount(), count(inv, e.item()), at + ": untilCount");
                }
                case Step.RetrieveStation r -> {
                    assertTrue(owned.remove(r.station()), at + ": station was not placed by this plan");
                    inv.merge(r.station(), 1, Integer::sum);
                    placed.remove(r.station());
                    setUp.remove(r.station());
                }
            }
        }
        inv.values().removeIf(v -> v == 0);
        return new Result(inv, mined, crafted);
    }

    /** Standing at {@code here} is fine for work at {@code needed}: the same place, anywhere, or a site's own dimension. */
    private static boolean canWork(Location here, Location needed) {
        return needed == null || needed == here || !needed.isSite() && here.dimension() == needed;
    }

    private static Location mineLocation(Knowledge knowledge, Step.Mine m) {
        for (Source source : knowledge.sourcesFor(m.item()))
            if (source instanceof MineSource mine && m.blocks().contains(mine.block())) return knowledge.locationOf(mine);
        return Location.OVERWORLD;
    }

    private static Location killLocation(Knowledge knowledge, Step.Kill k) {
        for (Source source : knowledge.sourcesFor(k.item()))
            if (source instanceof KillSource kill && kill.entity().equals(k.entity())) return knowledge.locationOf(kill);
        return Location.OVERWORLD;
    }

    private static Location barterLocation(Knowledge knowledge, Step.Barter b) {
        for (Source source : knowledge.sourcesFor(b.item()))
            if (source instanceof BarterSource barter && barter.entity().equals(b.entity())) return knowledge.locationOf(barter);
        return Location.OVERWORLD;
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
