package baritone.acquire.planner;

import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.KillSource;
import baritone.acquire.model.MineSource;
import baritone.acquire.model.Plan;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Source;
import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static baritone.acquire.planner.PlanReplay.CRAFTING_TABLE;
import static baritone.acquire.planner.PlanReplay.FURNACE;
import static baritone.acquire.planner.PlannerCosts.BREAK_TICKS;
import static baritone.acquire.planner.PlannerCosts.CRAFT_TICKS;
import static baritone.acquire.planner.PlannerCosts.KILL_TICKS;
import static baritone.acquire.planner.PlannerCosts.STATION_TICKS;
import static baritone.acquire.planner.PlannerCosts.UNKNOWN_DISTANCE;
import static baritone.acquire.planner.PlannerCosts.UNKNOWN_PLACED_DISTANCE;
import static baritone.acquire.planner.PlannerCosts.travel;

/**
 * Turns "N of X" plus the current inventory into an ordered list of steps.
 *
 * <p>It plans recursively against a virtual copy of the inventory: held items count, leftovers from
 * earlier crafts are reused, consumed items disappear. For each item it trial-plans every source,
 * prerequisites included (tool, station, fuel, ingredients), on a copy of that state and keeps the
 * cheapest complete one. Pure logic, no Minecraft classes, so it runs in plain unit tests.
 *
 * <p>What the executor can rely on:
 * <ul>
 *   <li>{@code untilCount} is the inventory count of the step's item right after the step, replayed from
 *       the inventory passed to {@link #plan}. Mine and Kill steps run until that count.</li>
 *   <li>A {@link Step.PlaceStation} comes before a table craft or a smelt unless the same station was
 *       already set up since the last Mine or Kill step. The first one for a station places the station
 *       item (made earlier in the plan or already held), unless {@link WorldView#stationNearby} said one
 *       is there. Later ones for that station need no item: walk back to the one used before.</li>
 *   <li>{@code Craft.inputs} holds one concrete item per ingredient, in recipe order, and the plan
 *       holds enough of each when the craft runs.</li>
 *   <li>A Smelt burns {@code fuelCount} of one fuel item, which is never a tool, a station block, the
 *       smelt input or an item being planned. Held fuel is used when it is cheap to replace; a lava bucket
 *       is never obtained as fuel. Blast furnaces and smokers burn fuel twice as fast.</li>
 *   <li>Tools are never consumed and durability is ignored; if one breaks, re-plan.</li>
 * </ul>
 */
public final class AcquirePlanner {
    /** Station blocks: never burned as fuel. */
    private static final Set<String> STATIONS =
            Set.of(CRAFTING_TABLE, FURNACE, "minecraft:blast_furnace", "minecraft:smoker");
    /** Stations that cook twice as fast and burn fuel twice as fast. */
    private static final Set<String> FAST_STATIONS = Set.of("minecraft:blast_furnace", "minecraft:smoker");
    /** Fuels never obtained just to burn (a lava bucket leaves the bucket behind and needs lava). */
    private static final Set<String> NEVER_OBTAIN_AS_FUEL = Set.of("minecraft:lava_bucket");
    /** Mine and kill sources below this drop rate (zombie -> iron_ingot) only count when nothing else makes the item. */
    private static final double MIN_DROP_RATE = 0.02;

    /** Ingredient alternatives and fuels trial-planned per choice, cheapest-looking first. */
    private static final int MAX_ALTERNATIVES = 6;
    /** Past this many sub-goals, each choice takes its first complete option instead of comparing the rest. */
    private static final int SOFT_WORK = 50_000;
    /** Past this many sub-goals, planning gives up. */
    private static final int HARD_WORK = 400_000;
    private static final int MAX_COUNT = 100_000;
    /** Per-item value of a held fuel nothing can make more of, so it is burned last. */
    private static final double IRREPLACEABLE = 1e6;
    private static final double INF = Double.POSITIVE_INFINITY;

    private final Knowledge knowledge;
    private final WorldView world;
    private final PlannerOptions options;

    public AcquirePlanner(Knowledge knowledge, WorldView world, PlannerOptions options) {
        this.knowledge = knowledge;
        this.world = world == null ? WorldView.UNKNOWN : world;
        this.options = options == null ? PlannerOptions.DEFAULT : options;
    }

    /** Never throws for unknown or impossible items; reports them in {@link Plan#missing()}. Does not modify {@code inventory}. */
    public Plan plan(String item, int count, InventorySnapshot inventory) {
        try {
            if (item == null || !knowledge.isItem(item)) return failed(item, count, "unknown item " + item);
            InventorySnapshot start = inventory == null ? InventorySnapshot.empty() : inventory.copy();
            if (count <= 0 || start.count(item) >= count) return new Plan(item, count, List.of(), List.of(), 0);
            if (count > MAX_COUNT) return failed(item, count, "can't plan for more than " + MAX_COUNT + " items");
            return new Search(item, start).run(count);
        } catch (RuntimeException | StackOverflowError e) {
            return failed(item, count, "planner error: " + e);
        }
    }

    /** The plan as chat lines joined by newlines, or one line when there is nothing to do. */
    public static String explain(Plan plan) {
        if (plan.alreadyDone()) return "already have " + plan.count() + " " + Step.shortId(plan.goal());
        return String.join("\n", plan.explain());
    }

    private static Plan failed(String item, int count, String reason) {
        return new Plan(String.valueOf(item), count, List.of(), List.of(reason), 0);
    }

    // ---- options: the sources of one item, prepared once per plan ----

    private sealed interface Option permits MineOption, KillOption, CraftOption, SmeltOption {
    }

    /** Every block that drops {@code item} with the same tool requirement, nearest first. */
    private record MineOption(String item, List<String> blocks, ToolReq tool, double dropsPerBlock, double distance)
            implements Option {
    }

    private record KillOption(KillSource source, double distance) implements Option {
    }

    private record CraftOption(CraftSource recipe) implements Option {
    }

    private record SmeltOption(SmeltSource recipe) implements Option {
    }

    private record ToolNeed(String type, int minTier) {
    }

    private record Fuel(String item, int count) {
    }

    /** One planning run. The caches and the recursion stack live here, so the planner itself keeps no state. */
    private final class Search {
        private final String goal;
        private final InventorySnapshot start;
        private final Map<String, List<Option>> optionCache = new HashMap<>();
        private final Map<String, String> noWay = new HashMap<>();
        private final Map<String, Double> estimates = new HashMap<>();
        private final Set<String> estimating = new HashSet<>();
        private final Map<String, List<String>> toolCache = new HashMap<>();
        private final Map<String, Boolean> placedCache = new HashMap<>();
        /** Items being planned right now; a source that needs one of them would be a cycle. */
        private final Set<String> stack = new HashSet<>();
        /** Tool needs being planned right now ("a pickaxe of tier 1+"). */
        private final List<ToolNeed> toolStack = new ArrayList<>();
        private int work;

        Search(String goal, InventorySnapshot start) {
            this.goal = goal;
            this.start = start;
        }

        Plan run(int count) {
            PlanState s = new PlanState(start.copy());
            boolean ok = obtain(s, goal, count);
            List<String> missing = new ArrayList<>(s.missing);
            if (!ok && missing.isEmpty()) missing.add("no known way to get " + goal);
            PlanReplay replay = new PlanReplay(knowledge, world, start);
            PlanReplay.Result checked = replay.run(s.steps);
            List<Step> steps;
            if (checked.ok()) {
                steps = replay.compact(s.steps);
            } else {
                missing.add("internal planner error at " + checked.error());
                steps = checked.steps();
            }
            return new Plan(goal, count, steps, missing, s.cost);
        }

        /** Makes at least {@code n} of {@code item} available (held and not reserved for anything else). */
        private boolean obtain(PlanState s, String item, int n) {
            int have = s.available(item);
            if (have >= n) return true;
            if (++work > HARD_WORK) return s.fail("gave up: too many possibilities to plan");
            if (stack.contains(item)) return s.fail(Step.shortId(item) + " would need itself");
            if (stack.size() >= options.maxDepth())
                return s.fail("gave up on " + item + ": more than " + options.maxDepth() + " sub-goals deep");
            // Hold the stock back so sub-goals can't use it up, then make the rest.
            s.reserve(item, have);
            stack.add(item);
            boolean ok;
            try {
                ok = produce(s, item, n - have);
            } finally {
                stack.remove(item);
            }
            s.release(item, have);
            return ok;
        }

        private boolean produce(PlanState s, String item, int need) {
            List<Option> usable = new ArrayList<>();
            for (Option o : options(item)) if (usable(o)) usable.add(o);
            if (usable.isEmpty()) return s.fail(noWay.getOrDefault(item, "no known way to get " + item));
            // Cheapest-looking first, so the budget cuts the rest short.
            usable.sort(Comparator.comparingDouble(this::estimateOption));
            return cheapest(s, usable, (t, o) -> switch (o) {
                case MineOption m -> mine(t, m, need);
                case KillOption k -> kill(t, k, need);
                case CraftOption c -> craft(t, c.recipe(), need);
                case SmeltOption sm -> smelt(t, sm.recipe(), need);
            }) >= 0;
        }

        /**
         * Trial-runs each choice on a copy of the state and adopts the cheapest complete one, returning its
         * index. When none completes it adopts the first failure with the fewest missing reasons and returns -1.
         */
        private <T> int cheapest(PlanState s, List<T> choices, BiPredicate<PlanState, T> attempt) {
            if (choices.size() == 1) return attempt.test(s, choices.get(0)) ? 0 : -1;
            PlanState best = null;
            PlanState bestFailed = null;
            int bestIndex = -1;
            boolean aborted = false;
            for (int i = 0; i < choices.size(); i++) {
                if (best != null && work > SOFT_WORK) break;
                PlanState t = s.copy();
                if (best != null) t.limit = Math.min(t.limit, best.cost);
                boolean ok;
                try {
                    ok = attempt.test(t, choices.get(i));
                } catch (PlanState.OverBudget e) {
                    aborted = true;
                    continue;
                }
                if (ok) {
                    if (best == null || t.cost < best.cost) {
                        best = t;
                        bestIndex = i;
                    }
                } else if (bestFailed == null || t.missing.size() < bestFailed.missing.size()) {
                    bestFailed = t;
                }
            }
            if (best != null) {
                s.become(best);
                return bestIndex;
            }
            if (bestFailed != null) {
                s.become(bestFailed);
                return -1;
            }
            // Every choice went over a budget set further up, where a cheaper complete option exists.
            if (aborted) throw PlanState.OverBudget.INSTANCE;
            return -1;
        }

        // ---- the four ways to get an item ----

        private boolean mine(PlanState s, MineOption m, int need) {
            ToolReq tool = m.tool();
            if (gates(tool) && !holdsTool(s, tool) && !obtainTool(s, tool.type(), tool.minTier())) return false;
            s.inv.add(m.item(), need);
            // Already mining these blocks earlier in the plan: take the extra on that trip instead of walking back.
            int prev = findEarlier(s, st -> st instanceof Step.Mine e && e.item().equals(m.item())
                    && e.blocks().equals(m.blocks()) && e.tool().equals(tool));
            if (prev >= 0) {
                PlanState.Entry e = s.steps.get(prev);
                Step.Mine old = (Step.Mine) e.step();
                int gained = e.gained() + need;
                int blocks = actions(gained, m.dropsPerBlock());
                s.steps.set(prev, new PlanState.Entry(
                        new Step.Mine(m.blocks(), m.item(), old.untilCount() + need, tool, blocks), gained));
                s.addCost((blocks - old.expectedBlocks()) * BREAK_TICKS);
                return true;
            }
            int blocks = actions(need, m.dropsPerBlock());
            s.addCost(travel(m.distance()) + blocks * BREAK_TICKS);
            return emit(s, new Step.Mine(m.blocks(), m.item(), s.inv.count(m.item()), tool, blocks), need, true);
        }

        private boolean kill(PlanState s, KillOption k, int need) {
            KillSource src = k.source();
            s.inv.add(src.output(), need);
            int prev = findEarlier(s, st -> st instanceof Step.Kill e && e.entity().equals(src.entity())
                    && e.item().equals(src.output()));
            if (prev >= 0) {
                PlanState.Entry e = s.steps.get(prev);
                Step.Kill old = (Step.Kill) e.step();
                int gained = e.gained() + need;
                int kills = actions(gained, src.dropsPerKill());
                s.steps.set(prev, new PlanState.Entry(
                        new Step.Kill(src.entity(), src.output(), old.untilCount() + need, kills), gained));
                s.addCost((kills - old.expectedKills()) * KILL_TICKS);
                return true;
            }
            int kills = actions(need, src.dropsPerKill());
            s.addCost(travel(k.distance()) + kills * KILL_TICKS);
            return emit(s, new Step.Kill(src.entity(), src.output(), s.inv.count(src.output()), kills), need, true);
        }

        private boolean craft(PlanState s, CraftSource r, int need) {
            int times = divideUp(need, r.outputCount());
            List<Ingredient> ings = r.ingredients();
            List<String> inputs = new ArrayList<>(ings.size());
            int[] totals = new int[ings.size()];
            for (int k = 0; k < ings.size(); k++) {
                Ingredient ing = ings.get(k);
                totals[k] = ing.count() * times;
                if (totals[k] <= 0) {
                    inputs.add(ing.anyOf().isEmpty() ? "minecraft:air" : ing.anyOf().get(0));
                    continue;
                }
                String chosen = pick(s, ing.anyOf(), totals[k]);
                if (chosen == null) return false;
                s.reserve(chosen, totals[k]);
                inputs.add(chosen);
            }
            if (r.needsTable() && !prepareStation(s, CRAFTING_TABLE)) return false;
            for (int k = 0; k < ings.size(); k++) s.consume(inputs.get(k), totals[k]);
            if (r.needsTable() && !setUpStation(s, CRAFTING_TABLE)) return false;
            s.inv.add(r.output(), times * r.outputCount());
            s.addCost(times * CRAFT_TICKS);
            return emit(s, new Step.Craft(r, times, inputs, s.inv.count(r.output())), 0, false);
        }

        private boolean smelt(PlanState s, SmeltSource r, int need) {
            int times = divideUp(need, r.outputCount());
            String input = pick(s, r.input().anyOf(), times);
            if (input == null) return false;
            s.reserve(input, times);
            String station = PlanReplay.stationOf(r);
            if (!prepareStation(s, station)) return false;
            Fuel fuel = fuel(s, input, times, r.cookTicks(), station);
            if (fuel == null) return false;
            s.reserve(fuel.item(), fuel.count());
            s.consume(input, times);
            s.consume(fuel.item(), fuel.count());
            if (!setUpStation(s, station)) return false;
            s.inv.add(r.output(), times * r.outputCount());
            s.addCost((double) times * Math.max(0, r.cookTicks()));
            return emit(s, new Step.Smelt(r, times, input, fuel.item(), fuel.count(), s.inv.count(r.output())), 0, false);
        }

        // ---- choices inside a source: ingredient item, fuel, tool, station ----

        /** One concrete item for an ingredient: one already held in full if any, else the cheapest to obtain (obtained). */
        private String pick(PlanState s, List<String> anyOf, int total) {
            List<String> alts = new ArrayList<>();
            for (String a : anyOf) if (a != null && !stack.contains(a) && !alts.contains(a)) alts.add(a);
            if (alts.isEmpty()) {
                s.fail("every item that fits " + anyOf + " would need itself");
                return null;
            }
            String held = null;
            for (String a : alts)
                if (s.available(a) >= total && (held == null || s.available(a) > s.available(held))) held = a;
            if (held != null) return held;
            alts.sort(Comparator.comparingDouble(a -> shortfallCost(s, a, total)));
            List<String> tries = alts.size() > MAX_ALTERNATIVES ? alts.subList(0, MAX_ALTERNATIVES) : alts;
            int i = cheapest(s, tries, (t, a) -> obtain(t, a, total));
            return i < 0 ? null : tries.get(i);
        }

        /** Fuel for {@code times} smelts: a held one if enough of it is spare, else the cheapest to obtain (obtained). */
        private Fuel fuel(PlanState s, String input, int times, int cookTicks, String station) {
            long heat = (long) times * Math.max(1, cookTicks) * (FAST_STATIONS.contains(station) ? 2 : 1);
            List<Fuel> candidates = new ArrayList<>();
            Map<String, Integer> all = knowledge.fuels();
            if (all != null) {
                List<String> ids = new ArrayList<>();
                for (String f : all.keySet()) if (f != null) ids.add(f);
                ids.sort(null);
                for (String f : ids) {
                    Integer burn = all.get(f);
                    if (burn == null || burn <= 0 || f.equals(input) || f.equals(goal) || stack.contains(f)
                            || STATIONS.contains(f) || knowledge.toolType(f) != null) continue;
                    candidates.add(new Fuel(f, (int) Math.max(1, (heat + burn - 1) / burn)));
                }
            }
            // Prefer what is already held, burning the cheapest to replace, unless that is worth more than
            // about twice what fresh fuel costs (held wool, chests or a lava bucket stay in the inventory).
            double fresh = INF;
            for (Fuel f : candidates)
                if (!NEVER_OBTAIN_AS_FUEL.contains(f.item())) fresh = Math.min(fresh, f.count() * estimateItem(f.item()));
            Fuel held = null;
            double heldValue = 0;
            for (Fuel f : candidates) {
                if (s.available(f.item()) < f.count()) continue;
                double value = f.count() * Math.min(estimateItem(f.item()), IRREPLACEABLE);
                if (value <= 2 * fresh && (held == null || value < heldValue)) {
                    held = f;
                    heldValue = value;
                }
            }
            if (held != null) return held;
            List<Fuel> tries = new ArrayList<>();
            for (Fuel f : candidates)
                if (!NEVER_OBTAIN_AS_FUEL.contains(f.item()) && shortfallCost(s, f.item(), f.count()) < INF) tries.add(f);
            if (tries.isEmpty()) {
                s.fail("no fuel to smelt " + Step.shortId(input));
                return null;
            }
            tries.sort(Comparator.comparingDouble(f -> shortfallCost(s, f.item(), f.count())));
            if (tries.size() > MAX_ALTERNATIVES) tries = new ArrayList<>(tries.subList(0, MAX_ALTERNATIVES));
            List<Fuel> finalTries = tries;
            int i = cheapest(s, tries, (t, f) -> obtain(t, f.item(), f.count()));
            return i < 0 ? null : finalTries.get(i);
        }

        private boolean gates(ToolReq tool) {
            return tool != null && tool.required() && tool.type() != null;
        }

        private boolean holdsTool(PlanState s, ToolReq tool) {
            for (String t : tools(tool.type(), tool.minTier())) if (s.inv.count(t) > 0) return true;
            return false;
        }

        /** Obtains the cheapest tool of {@code type} with tier >= {@code minTier}. */
        private boolean obtainTool(PlanState s, String type, int minTier) {
            // Needing a tier-k tool while already planning one of tier <= k is a cycle (iron pick for cobblestone).
            for (ToolNeed n : toolStack)
                if (n.type().equals(type) && n.minTier() <= minTier)
                    return s.fail("needs a " + type + " to make a " + type);
            List<String> candidates = new ArrayList<>();
            for (String t : tools(type, minTier)) if (!stack.contains(t)) candidates.add(t);
            if (candidates.isEmpty())
                return s.fail("no known way to get a " + type + (minTier > 0 ? " of tier " + minTier + "+" : ""));
            toolStack.add(new ToolNeed(type, minTier));
            try {
                return cheapest(s, candidates, (t, tool) -> obtain(t, tool, 1)) >= 0;
            } finally {
                toolStack.remove(toolStack.size() - 1);
            }
        }

        private List<String> tools(String type, int minTier) {
            return toolCache.computeIfAbsent(type + '#' + minTier, key -> {
                List<String> t = knowledge.toolsOf(type, minTier);
                return t == null ? List.of() : t;
            });
        }

        /** Makes sure the station can be set up later: nearby, already placed, or its item obtained and held back. */
        private boolean prepareStation(PlanState s, String station) {
            if (s.ready.contains(station) || s.pending.contains(station)) return true;
            if (world.stationNearby(station)) {
                s.ready.add(station);
                return true;
            }
            if (!options.allowPlaceStations())
                return s.fail("no " + Step.shortId(station).replace('_', ' ') + " nearby and placing stations is off");
            if (!obtain(s, station, 1)) return false;
            s.reserve(station, 1);
            s.pending.add(station);
            return true;
        }

        /** Emits a PlaceStation unless the station is still set up from earlier; the first placement uses up the item. */
        private boolean setUpStation(PlanState s, String station) {
            if (s.active.contains(station)) return true;
            if (s.pending.remove(station)) {
                s.consume(station, 1);
                s.ready.add(station);
            }
            s.active.add(station);
            s.addCost(STATION_TICKS);
            return emit(s, new Step.PlaceStation(station), 0, false);
        }

        private boolean emit(PlanState s, Step step, int gained, boolean moves) {
            s.steps.add(new PlanState.Entry(step, gained));
            if (moves) s.active.clear();
            if (s.steps.size() > options.maxSteps())
                return s.fail("the plan needs more than " + options.maxSteps() + " steps");
            return true;
        }

        private int findEarlier(PlanState s, Predicate<Step> match) {
            for (int i = 0; i < s.steps.size(); i++) if (match.test(s.steps.get(i).step())) return i;
            return -1;
        }

        // ---- sources, prepared once per item ----

        private List<Option> options(String item) {
            List<Option> cached = optionCache.get(item);
            if (cached != null) return cached;
            List<Source> sources = knowledge.sourcesFor(item);
            if (sources == null) sources = List.of();
            boolean furnaceRecipe = false;
            // Every block with the same tool requirement becomes one option: any of them will do.
            // Very rare drops are grouped apart and only used when nothing else makes the item.
            Map<ToolReq, List<MineSource>> groups = new LinkedHashMap<>();
            Map<ToolReq, List<MineSource>> rareGroups = new LinkedHashMap<>();
            for (Source src : sources) {
                if (src instanceof SmeltSource sm && item.equals(sm.output()) && FURNACE.equals(PlanReplay.stationOf(sm)))
                    furnaceRecipe = true;
                if (src instanceof MineSource m && minable(item, m))
                    (m.dropsPerBlock() < MIN_DROP_RATE ? rareGroups : groups).computeIfAbsent(toolOf(m), k -> new ArrayList<>()).add(m);
            }
            List<Option> out = new ArrayList<>();
            List<Option> rare = new ArrayList<>();
            boolean killOff = false;
            boolean silkOnly = false;
            for (Source src : sources) {
                if (src == null || !item.equals(src.output())) continue;
                switch (src) {
                    case MineSource m -> {
                        if (m.needsSilkTouch()) silkOnly = true;
                        if (minable(item, m)) {
                            boolean isRare = m.dropsPerBlock() < MIN_DROP_RATE;
                            List<MineSource> group = (isRare ? rareGroups : groups).remove(toolOf(m));
                            if (group != null) (isRare ? rare : out).add(mineOption(item, group));
                        }
                    }
                    case KillSource k -> {
                        if (!options.allowKill()) killOff = true;
                        else if (k.dropsPerKill() > 0 && k.entity() != null)
                            (k.dropsPerKill() < MIN_DROP_RATE ? rare : out).add(new KillOption(k, world.distanceToEntity(k.entity())));
                    }
                    case CraftSource c -> {
                        if (craftable(c)) out.add(new CraftOption(c));
                    }
                    case SmeltSource sm -> {
                        // The plain furnace, unless another station is right there (or there is no furnace recipe).
                        boolean valid = sm.outputCount() > 0 && sm.input() != null && !sm.input().anyOf().isEmpty();
                        String station = PlanReplay.stationOf(sm);
                        if (valid && (FURNACE.equals(station) || !furnaceRecipe || world.stationNearby(station)))
                            out.add(new SmeltOption(sm));
                    }
                }
            }
            if (out.isEmpty()) out = rare;
            if (out.isEmpty()) {
                noWay.put(item, "no known way to get " + item
                        + (killOff ? " (killing mobs is off)" : silkOnly ? " (it only drops with silk touch)" : ""));
            }
            optionCache.put(item, out);
            return out;
        }

        private boolean minable(String item, MineSource m) {
            return item.equals(m.output()) && m.block() != null && !m.needsSilkTouch() && m.dropsPerBlock() > 0;
        }

        private ToolReq toolOf(MineSource m) {
            return m.tool() == null ? ToolReq.NONE : m.tool();
        }

        private MineOption mineOption(String item, List<MineSource> group) {
            Map<String, Double> distance = new HashMap<>();
            for (MineSource m : group) distance.computeIfAbsent(m.block(), this::blockDistance);
            List<MineSource> sorted = new ArrayList<>(group);
            sorted.sort(Comparator.comparingDouble(m -> distance.get(m.block())));
            List<String> blocks = new ArrayList<>();
            for (MineSource m : sorted) if (!blocks.contains(m.block())) blocks.add(m.block());
            MineSource nearest = sorted.get(0);
            return new MineOption(item, blocks, toolOf(nearest), nearest.dropsPerBlock(), distance.get(nearest.block()));
        }

        /**
         * Distance to the nearest known {@code block}. Unknown ones get a default: a much larger one for blocks
         * that are usually player-placed, so a crafting table is crafted rather than hunted for.
         */
        private double blockDistance(String block) {
            double d = world.distanceToBlock(block);
            if (!Double.isNaN(d) && !Double.isInfinite(d)) return Math.max(0, d);
            return placedLooking(block) ? UNKNOWN_PLACED_DISTANCE : UNKNOWN_DISTANCE;
        }

        /** No item form (wall_torch, redstone_wire), or its item is crafted (crafting_table, torch, white_wool). */
        private boolean placedLooking(String block) {
            return placedCache.computeIfAbsent(block, b -> {
                if (!knowledge.isItem(b)) return true;
                List<Source> sources = knowledge.sourcesFor(b);
                if (sources != null) for (Source s : sources) if (s instanceof CraftSource c && b.equals(c.output())) return true;
                return false;
            });
        }

        private boolean craftable(CraftSource c) {
            if (c.outputCount() <= 0 || c.output() == null) return false;
            for (Ingredient ing : c.ingredients()) if (ing == null || (ing.count() > 0 && ing.anyOf().isEmpty())) return false;
            return true;
        }

        /** False for a recipe that needs an item being planned right now (iron_block while planning iron_ingot). */
        private boolean usable(Option o) {
            return switch (o) {
                case CraftOption c -> c.recipe().ingredients().stream().allMatch(i -> i.count() <= 0 || hasFreeAlternative(i));
                case SmeltOption sm -> hasFreeAlternative(sm.recipe().input());
                default -> true;
            };
        }

        private boolean hasFreeAlternative(Ingredient ing) {
            for (String a : ing.anyOf()) if (a != null && !stack.contains(a)) return true;
            return false;
        }

        // ---- quick estimates: only order the options, the trial plans decide ----

        /** Rough cost per item from scratch, ignoring tools, stations and fuel. Infinite if nothing makes it. */
        private double estimateItem(String item) {
            Double known = estimates.get(item);
            if (known != null) return known;
            if (!estimating.add(item)) return INF;
            double best = INF;
            for (Option o : options(item)) best = Math.min(best, estimateOption(o));
            estimating.remove(item);
            estimates.put(item, best);
            return best;
        }

        private double estimateOption(Option o) {
            return switch (o) {
                case MineOption m -> (travel(m.distance()) + BREAK_TICKS) / m.dropsPerBlock();
                case KillOption k -> (travel(k.distance()) + KILL_TICKS) / k.source().dropsPerKill();
                case CraftOption c -> {
                    double sum = CRAFT_TICKS;
                    for (Ingredient ing : c.recipe().ingredients())
                        if (ing.count() > 0) sum += ing.count() * cheapestAlternative(ing);
                    yield sum / c.recipe().outputCount();
                }
                case SmeltOption sm -> (Math.max(0, sm.recipe().cookTicks()) + cheapestAlternative(sm.recipe().input()))
                        / sm.recipe().outputCount();
            };
        }

        private double cheapestAlternative(Ingredient ing) {
            double best = INF;
            for (String a : ing.anyOf()) if (a != null) best = Math.min(best, estimateItem(a));
            return best;
        }

        /** Estimated cost of the part of {@code total} not already available. */
        private double shortfallCost(PlanState s, String item, int total) {
            int shortfall = total - s.available(item);
            return shortfall <= 0 ? 0 : shortfall * estimateItem(item);
        }
    }

    private static int divideUp(int need, int per) {
        return (need + per - 1) / per;
    }

    /** Actions (blocks, kills) expected to yield {@code amount} at {@code perAction} each; at least 1. */
    private static int actions(int amount, double perAction) {
        return Math.max(1, (int) Math.ceil(amount / perAction - 1e-9));
    }
}
