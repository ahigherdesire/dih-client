package baritone.acquire.planner;

import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.BarterSource;
import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Goal;
import baritone.acquire.model.Location;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.KillSource;
import baritone.acquire.model.MineSource;
import baritone.acquire.model.Plan;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Source;
import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;
import baritone.acquire.model.ToolDurability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
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
import static baritone.acquire.planner.PlannerCosts.BARTER_TICKS;
import static baritone.acquire.planner.PlannerCosts.BREAK_TICKS;
import static baritone.acquire.planner.PlannerCosts.CRAFT_TICKS;
import static baritone.acquire.planner.PlannerCosts.DRAGON_TICKS;
import static baritone.acquire.planner.PlannerCosts.FORTRESS_TICKS;
import static baritone.acquire.planner.PlannerCosts.CAST_OBSIDIAN_TICKS;
import static baritone.acquire.planner.PlannerCosts.CAST_PORTAL_TICKS;
import static baritone.acquire.planner.PlannerCosts.PORTAL_TICKS;
import static baritone.acquire.planner.PlannerCosts.STRONGHOLD_TICKS;
import static baritone.acquire.planner.PlannerCosts.KILL_TICKS;
import static baritone.acquire.planner.PlannerCosts.ROAM_KILL_TICKS;
import static baritone.acquire.planner.PlannerCosts.STATION_TICKS;
import static baritone.acquire.planner.PlannerCosts.RETRIEVE_TICKS;
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
 *   <li>Tool uses are budgeted with a digging allowance; spare tools are planned before a long mine.</li>
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
    /**
     * Mobs never hunted for drops. Bosses, golems and group-aggro mobs are dangerous, and villagers,
     * traders, pets and kept animals are things players don't want killed (a cat is a string source,
     * an iron golem the cheapest iron on paper).
     */
    public static final Set<String> NEVER_KILL = Set.of(
            "minecraft:player", "minecraft:iron_golem", "minecraft:snow_golem", "minecraft:wither",
            "minecraft:ender_dragon", "minecraft:warden", "minecraft:elder_guardian", "minecraft:piglin_brute",
            "minecraft:zombified_piglin", "minecraft:villager", "minecraft:wandering_trader", "minecraft:allay",
            "minecraft:cat", "minecraft:ocelot", "minecraft:parrot", "minecraft:wolf", "minecraft:fox",
            "minecraft:axolotl", "minecraft:dolphin", "minecraft:turtle", "minecraft:panda", "minecraft:polar_bear",
            "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:skeleton_horse", "minecraft:zombie_horse",
            "minecraft:llama", "minecraft:trader_llama", "minecraft:camel", "minecraft:sniffer", "minecraft:armadillo",
            "minecraft:bee");

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

    private static final String OBSIDIAN = "minecraft:obsidian";
    private static final String FLINT_AND_STEEL = "minecraft:flint_and_steel";
    private static final String BUCKET = "minecraft:bucket";
    private static final String WATER_BUCKET = "minecraft:water_bucket";
    private static final String LAVA_BUCKET = "minecraft:lava_bucket";
    /** Casting obsidian takes two buckets: one for water, one to carry lava. */
    private static final int OBSIDIAN_BUCKETS = 2;
    private static final String ENDER_EYE = "minecraft:ender_eye";
    private static final String PIGLIN = "minecraft:piglin";
    private static final int PORTAL_OBSIDIAN = 10;
    /**
     * The speedrunner's portal: its frame cast in place from lava, with a mould wall of 20 blocks and four buckets,
     * one for water and three to carry lava: a trip to the pool for every three frame blocks, not every one. Two
     * already held will do (a trip a block): the other two aren't worth a trip for iron of their own.
     */
    private static final int CAST_BUCKETS = 4;
    private static final int CAST_MIN_BUCKETS = 2;
    private static final int CAST_MOULD = 20;
    /** What the mould wall may be made of, the usual first; the runner takes any of these. */
    private static final List<String> MOULD = List.of("minecraft:cobblestone", "minecraft:cobbled_deepslate",
            "minecraft:netherrack", "minecraft:dirt", "minecraft:blackstone");
    /**
     * A pickaxe with wear to spare for a first trip to the Nether: casting digs the site out (up to 30 blocks) and
     * paths through stone to water and lava, and the Nether is dug through on foot: about what a fresh stone pickaxe
     * has. Any pickaxe will do.
     */
    private static final ToolReq PICKAXE = new ToolReq("pickaxe", 1, true);
    private static final int CAST_DIGS = 40;
    private static final int NETHER_DIGS = 48;
    private static final int END_PORTAL_EYES = 12;
    /** Piglins attack a player with no gold on: one of these is worn while bartering, the cheapest made if none is held. */
    private static final List<String> GOLD_ARMOUR = List.of("minecraft:golden_boots", "minecraft:golden_helmet",
            "minecraft:golden_leggings", "minecraft:golden_chestplate");
    /** Where each place leads: portals, and finding the sites. The End has no way out before the dragon. */
    private static final Map<Location, List<Location>> EXITS = Map.of(
            Location.OVERWORLD, List.of(Location.NETHER, Location.STRONGHOLD),
            Location.STRONGHOLD, List.of(Location.OVERWORLD, Location.END),
            Location.NETHER, List.of(Location.OVERWORLD, Location.FORTRESS),
            Location.FORTRESS, List.of(Location.NETHER),
            Location.END, List.of());
    /** The Nether gear checkpoint (M2): armour, a shield and a sword, iron or better. */
    private static final List<Gear> NETHER_GEAR = List.of(
            gear(1, "iron_helmet", "diamond_helmet", "netherite_helmet"),
            gear(1, "iron_chestplate", "diamond_chestplate", "netherite_chestplate"),
            gear(1, "iron_leggings", "diamond_leggings", "netherite_leggings"),
            gear(1, "iron_boots", "diamond_boots", "netherite_boots"),
            gear(1, "shield"),
            gear(1, "iron_sword", "diamond_sword", "netherite_sword"));
    /** The End gear checkpoint (M2): a bow, arrows and blocks to pillar with. */
    private static final List<Gear> END_GEAR = List.of(
            gear(1, "bow"),
            gear(64, "arrow"),
            gear(64, "cobblestone", "cobbled_deepslate", "netherrack", "blackstone"));

    private static Gear gear(int count, String... names) {
        List<String> ids = new ArrayList<>();
        for (String name : names) ids.add("minecraft:" + name);
        return new Gear(List.copyOf(ids), count);
    }

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
        return plan(new Goal.ItemGoal(item, count), inventory, Location.OVERWORLD);
    }

    /** Plans {@code goal} from {@code inventory}, standing in {@code start} (null: the Overworld). */
    public Plan plan(Goal goal, InventorySnapshot inventory, Location start) {
        String label = goal == null ? "null" : goal.label();
        int count = goal == null ? 0 : goal.count();
        try {
            InventorySnapshot inv = inventory == null ? InventorySnapshot.empty() : inventory.copy();
            Location at = start == null ? Location.OVERWORLD : start;
            switch (goal) {
                case null -> {
                    return failed(label, count, "no goal");
                }
                case Goal.ItemGoal item -> {
                    if (item.item() == null || !knowledge.isItem(item.item())) return failed(item.item(), count, "unknown item " + item.item());
                    if (count <= 0 || inv.count(item.item()) >= count) return new Plan(item.item(), count, List.of(), List.of(), 0);
                    if (count > MAX_COUNT) return failed(item.item(), count, "can't plan for more than " + MAX_COUNT + " items");
                }
                case Goal.AtLocation where -> {
                    if (where.location() == at) return new Plan(label, count, List.of(), List.of(), 0);
                }
                case Goal.DragonDead dragon -> {
                }
            }
            return new Search(goal, inv, at).run();
        } catch (RuntimeException | StackOverflowError e) {
            return failed(label, count, "planner error: " + e);
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

    private sealed interface Option permits MineOption, KillOption, BarterOption, CraftOption, SmeltOption {
    }

    /** Every block that drops {@code item} with the same tool requirement, nearest first. */
    private record MineOption(String item, List<String> blocks, ToolReq tool, double dropsPerBlock, double distance,
                              Location location) implements Option {
    }

    private record KillOption(KillSource source, double distance, Location location) implements Option {
    }

    private record BarterOption(BarterSource source, double distance, Location location) implements Option {
    }

    /** Mine sources grouped into one option: the same tool, in the same place. */
    private record MineGroup(ToolReq tool, Location location) {
    }

    /** Gear held before a trip: any one of {@code anyOf}, {@code count} of it; the first is what gets made. */
    private record Gear(List<String> anyOf, int count) {
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
        private final Goal target;
        private final String goal;
        private final InventorySnapshot start;
        private final Location startLocation;
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
        /** Places being travelled to right now; a trip that needs itself would loop. */
        private final Set<Location> travelling = new HashSet<>();
        private int work;

        Search(Goal target, InventorySnapshot start, Location startLocation) {
            this.target = target;
            this.goal = target.label();
            this.start = start;
            this.startLocation = startLocation;
        }

        Plan run() {
            int count = target.count();
            PlanState s = new PlanState(start.copy());
            s.location = startLocation;
            boolean ok = switch (target) {
                case Goal.ItemGoal item -> obtain(s, item.item(), item.count());
                case Goal.DragonDead dragon -> slayDragon(s);
                case Goal.AtLocation where -> travelTo(s, where.location());
            };
            if (ok && options.gearUp() && target instanceof Goal.ItemGoal) addAffordableIronGear(s, count);
            retrieveStations(s);
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
            if (item.equals(Step.CollectEgg.EGG)) return egg(s, need);
            List<Option> usable = new ArrayList<>();
            for (Option o : options(item)) if (usable(o)) usable.add(o);
            if (usable.isEmpty()) return s.fail(noWay.getOrDefault(item, "no known way to get " + item));
            // Cheapest-looking first, so the budget cuts the rest short.
            usable.sort(Comparator.comparingDouble(this::estimateOption));
            return cheapest(s, usable, (t, o) -> switch (o) {
                case MineOption m -> mine(t, m, need);
                case KillOption k -> kill(t, k, need);
                case BarterOption b -> barter(t, b, need);
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
            if (options.gearUp() && undergroundOre(m) && m.location() == Location.OVERWORLD && !hasSword(s)
                    && !obtain(s, "minecraft:stone_sword", 1)) return false;
            // Obsidian nobody has seen is cast: lava carried in one bucket, poured beside water from the other.
            if (m.item().equals(OBSIDIAN) && !seen(OBSIDIAN)) {
                int filled = s.inv.count(WATER_BUCKET) + s.inv.count(LAVA_BUCKET);
                if (filled < OBSIDIAN_BUCKETS && !obtain(s, BUCKET, OBSIDIAN_BUCKETS - filled)) return false;
                // Each block is a lava trip, not just a break.
                s.addCost(need * CAST_OBSIDIAN_TICKS);
            }
            ToolReq tool = m.tool();
            int prev = findEarlier(s, st -> st instanceof Step.Mine e && e.item().equals(m.item())
                    && e.blocks().equals(m.blocks()) && e.tool().equals(tool));
            if (prev >= 0) {
                PlanState.Entry e = s.steps.get(prev);
                Step.Mine old = (Step.Mine) e.step();
                int gained = e.gained() + need;
                int blocks = actions(gained, m.dropsPerBlock());
                int extra = blocks - old.expectedBlocks();
                // A replacement crafted now cannot retroactively supply an earlier mining step, and neither can one
                // crafted since: only merge while the tools that step had cover the extra blocks.
                if (availableToolUses(s, tool) - usesMadeSince(s, prev, tool) >= ToolDurability.budget(extra)) {
                    if (!budgetTool(s, tool, extra)) return false;
                    s.inv.add(m.item(), need);
                    s.steps.set(prev, new PlanState.Entry(
                            new Step.Mine(m.blocks(), m.item(), old.untilCount() + need, tool, blocks), gained));
                    s.addCost(extra * BREAK_TICKS);
                    return true;
                }
            }
            int blocks = actions(need, m.dropsPerBlock());
            if (!budgetTool(s, tool, blocks)) return false;
            if (!travelTo(s, m.location())) return false;
            s.inv.add(m.item(), need);
            s.addCost(travel(m.distance()) + blocks * BREAK_TICKS);
            return emit(s, new Step.Mine(m.blocks(), m.item(), s.inv.count(m.item()), tool, blocks), need, true);
        }

        private boolean seen(String block) {
            double d = world.distanceToBlock(block);
            return !Double.isNaN(d) && !Double.isInfinite(d);
        }

        /** The usable uses of tools of {@code tool}'s type crafted after step {@code index}. */
        private int usesMadeSince(PlanState s, int index, ToolReq tool) {
            if (!gates(tool)) return 0;
            int uses = 0;
            for (int i = index + 1; i < s.steps.size(); i++) {
                if (!(s.steps.get(i).step() instanceof Step.Craft craft)) continue;
                String id = craft.recipe().output();
                if (!tool.type().equals(knowledge.toolType(id))) continue;
                int max = ToolDurability.maxUses(id);
                if (max <= 0) return Integer.MAX_VALUE;
                uses += (max - (int) Math.ceil(max * 0.10)) * craft.times() * craft.recipe().outputCount();
            }
            return uses;
        }

        private boolean undergroundOre(MineOption mine) {
            return mine.blocks().stream().anyMatch(block -> block.endsWith("_ore") || block.contains("deepslate"));
        }

        private boolean hasSword(PlanState s) {
            return List.of("stone_sword", "iron_sword", "diamond_sword", "netherite_sword")
                    .stream().anyMatch(name -> s.inv.count("minecraft:" + name) > 0);
        }

        private void addAffordableIronGear(PlanState s, int goalCount) {
            boolean ironPlanned = s.steps.stream().anyMatch(entry ->
                    entry.step() instanceof Step.Mine mine && mine.item().equals("minecraft:raw_iron")
                    || entry.step() instanceof Step.Smelt smelt && smelt.item().equals("minecraft:iron_ingot"));
            if (!ironPlanned) return;
            double cap = s.cost * 2;
            s.reserve(goal, goalCount);
            try {
                for (String gear : List.of("shield", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots")) {
                    String id = "minecraft:" + gear;
                    if (!knowledge.isItem(id) || s.inv.count(id) > 0) continue;
                    PlanState trial = s.copy();
                    trial.limit = Math.min(trial.limit, cap);
                    try {
                        if (obtain(trial, id, 1) && trial.cost <= cap) s.become(trial);
                    } catch (PlanState.OverBudget ignored) {
                        // Gear is optional when obtaining it would more than double the plan cost.
                    }
                }
            } finally {
                s.release(goal, goalCount);
            }
        }

        private boolean kill(PlanState s, KillOption k, int need) {
            KillSource src = k.source();
            int prev = findEarlier(s, st -> st instanceof Step.Kill e && e.entity().equals(src.entity())
                    && e.item().equals(src.output()));
            if (prev >= 0) {
                s.inv.add(src.output(), need);
                PlanState.Entry e = s.steps.get(prev);
                Step.Kill old = (Step.Kill) e.step();
                int gained = e.gained() + need;
                int kills = actions(gained, src.dropsPerKill());
                s.steps.set(prev, new PlanState.Entry(
                        new Step.Kill(src.entity(), src.output(), old.untilCount() + need, kills), gained));
                s.addCost((kills - old.expectedKills()) * killTicks(k));
                return true;
            }
            if (!travelTo(s, k.location())) return false;
            s.inv.add(src.output(), need);
            int kills = actions(need, src.dropsPerKill());
            s.addCost(travel(k.distance()) + kills * killTicks(k));
            return emit(s, new Step.Kill(src.entity(), src.output(), s.inv.count(src.output()), kills), need, true);
        }

        /** A mob found anywhere (endermen) is thin on the ground: each one is looked for first. */
        private double killTicks(KillOption k) {
            return k.location() == null ? ROAM_KILL_TICKS : KILL_TICKS;
        }

        /**
         * Trades {@code need} of the item for currency: the currency is obtained and held back, a gold piece put on
         * for piglins, then it goes to where they live.
         */
        private boolean barter(PlanState s, BarterOption b, int need) {
            BarterSource src = b.source();
            int trades = actions(need, src.perTrade());
            if (!obtain(s, src.currency(), trades)) return false;
            s.reserve(src.currency(), trades);
            if (src.entity().equals(PIGLIN) && !wearGold(s)) return false;
            if (!travelTo(s, b.location())) return false;
            s.consume(src.currency(), trades);
            s.inv.add(src.output(), need);
            s.addCost(travel(b.distance()) + trades * BARTER_TICKS);
            return emit(s, new Step.Barter(src.entity(), src.currency(), src.output(), s.inv.count(src.output()), trades), need, true);
        }

        /** A gold armour piece to wear, held back: one already held, or the cheapest made. */
        private boolean wearGold(PlanState s) {
            for (String id : GOLD_ARMOUR) {
                if (s.inv.count(id) <= 0) continue;
                if (s.reserved.getOrDefault(id, 0) == 0) s.reserve(id, 1);
                return true;
            }
            String piece = GOLD_ARMOUR.get(0);
            if (!knowledge.isItem(piece) || !obtain(s, piece, 1)) {
                return s.fail("bartering with piglins needs a piece of gold armour to wear: 1 " + Step.shortId(piece));
            }
            s.reserve(piece, 1);
            return true;
        }

        // ---- places: portals, sites, the dragon ----

        /**
         * Gets the player to {@code target} (null: anywhere will do). What every portal on the way needs is obtained
         * first (the eyes before looking for the stronghold), then the route is walked from wherever that left us.
         */
        private boolean travelTo(PlanState s, Location target) {
            if (target == null || s.location == target) return true;
            if (!target.isSite() && s.location.dimension() == target) {
                s.location = target;
                return true;
            }
            if (!travelling.add(target)) return s.fail("getting to " + target.label() + " needs being there first");
            try {
                List<Location> route = route(s.location, target);
                if (route == null) return s.fail(noRoute(s.location, target));
                Location prev = s.location;
                for (Location next : route) {
                    if (prev.dimension() != next && portalNeeded(s, next) && !preparePortal(s, next)) return false;
                    prev = next;
                }
                route = route(s.location, target);
                if (route == null) return s.fail(noRoute(s.location, target));
                Location from = s.location;
                for (Location next : route) {
                    if (!hop(s, from, next)) return false;
                    from = next;
                }
                return true;
            } finally {
                travelling.remove(target);
            }
        }

        private String noRoute(Location from, Location to) {
            return "no way from " + from.label() + " to " + to.label()
                    + (from == Location.END ? " (the End has no way out before the dragon is dead)" : "");
        }

        private boolean portalNeeded(PlanState s, Location next) {
            return (next == Location.NETHER || next == Location.END) && !s.portals.contains(next) && !s.portalReady.containsKey(next)
                    && !world.portalKnown(next);
        }

        /**
         * The portal kit and gear checkpoint for a first trip to {@code to}, obtained and held back. A nether portal is
         * cast from lava or built from obsidian, whichever is cheaper from here: casting unless 10 obsidian are to hand,
         * since mining obsidian takes diamonds.
         */
        private boolean preparePortal(PlanState s, Location to) {
            return cheapest(s, portalKits(s, to), (t, kit) -> prepareKit(t, to, kit)) >= 0;
        }

        private boolean prepareKit(PlanState s, Location to, Map<String, Integer> kit) {
            boolean cast = to == Location.NETHER && !kit.containsKey(OBSIDIAN);
            for (Map.Entry<String, Integer> item : kit.entrySet()) {
                if (!knowledge.isItem(item.getKey()) || !obtain(s, item.getKey(), item.getValue())) {
                    return s.fail("can't reach " + to.label() + ": " + (cast ? "casting the obsidian frame from lava " : "")
                            + "needs " + item.getValue() + " " + Step.shortId(item.getKey()));
                }
                s.reserve(item.getKey(), item.getValue());
            }
            if (cast) s.addCost(CAST_PORTAL_TICKS);
            // Gear last, so it is fresh for the trip (and the End gear comes after the Nether, as the phases go).
            if (!checkpoint(s, to == Location.NETHER ? NETHER_GEAR : END_GEAR, to.label())) return false;
            int digs = (cast ? CAST_DIGS : 0) + (to == Location.NETHER && options.gearCheckpoints() ? NETHER_DIGS : 0);
            if (!budgetTool(s, PICKAXE, digs)) return s.fail("can't reach " + to.label() + ": no pickaxe to dig with");
            s.portalReady.put(to, kit);
            return true;
        }

        /**
         * What a portal can be made from. The Nether's: buckets (filled ones count), mould blocks and a flint and
         * steel to cast the frame; or, with 10 obsidian to hand, those and a flint and steel. Obsidian still to get
         * never makes a frame: seen obsidian says nothing of how much, and the rest is cast a block at a time, a lava
         * trip each. The End's: 12 eyes to fill the end portal.
         */
        private List<Map<String, Integer>> portalKits(PlanState s, Location to) {
            if (to != Location.NETHER) return List.of(Map.of(ENDER_EYE, END_PORTAL_EYES));
            Map<String, Integer> cast = new LinkedHashMap<>();
            int filled = s.available(WATER_BUCKET) + s.available(LAVA_BUCKET);
            int buckets = (filled + s.available(BUCKET) >= CAST_MIN_BUCKETS ? CAST_MIN_BUCKETS : CAST_BUCKETS) - filled;
            if (buckets > 0) cast.put(BUCKET, buckets);
            String mould = MOULD.get(0);
            for (String id : MOULD) {
                if (s.available(id) >= CAST_MOULD) {
                    mould = id;
                    break;
                }
            }
            cast.put(mould, CAST_MOULD);
            cast.put(FLINT_AND_STEEL, 1);
            if (s.available(OBSIDIAN) < PORTAL_OBSIDIAN) return List.of(cast);
            Map<String, Integer> frame = new LinkedHashMap<>();
            frame.put(OBSIDIAN, PORTAL_OBSIDIAN);
            frame.put(FLINT_AND_STEEL, 1);
            return List.of(cast, frame);
        }

        /** One leg: find a site, step out of one, or go through a portal (using up its items the first time). */
        private boolean hop(PlanState s, Location from, Location to) {
            if (to.isSite()) {
                s.addCost(to == Location.FORTRESS ? FORTRESS_TICKS : STRONGHOLD_TICKS);
                if (!emit(s, new Step.Locate(to), 0, true)) return false;
                s.location = to;
                return true;
            }
            if (from.isSite() && from.dimension() == to) {
                s.location = to;
                return true;
            }
            Map<String, Integer> consumes = new LinkedHashMap<>();
            Map<String, Integer> kit = s.portalReady.remove(to);
            if (kit != null) {
                for (Map.Entry<String, Integer> item : kit.entrySet()) {
                    // The flint and steel and the buckets come along; the obsidian, mould blocks or eyes stay behind.
                    if (item.getKey().equals(FLINT_AND_STEEL) || item.getKey().equals(BUCKET)) s.release(item.getKey(), item.getValue());
                    else {
                        s.consume(item.getKey(), item.getValue());
                        consumes.put(item.getKey(), item.getValue());
                    }
                }
                s.portals.add(to);
                // The nether portal leads back the same way.
                if (to == Location.NETHER) s.portals.add(Location.OVERWORLD);
            }
            s.addCost(PORTAL_TICKS);
            if (!emit(s, new Step.Travel(from, to, consumes), 0, true)) return false;
            s.location = to;
            return true;
        }

        /** Before a first trip: hold (or get) each piece of gear; what is held for it stays held back. */
        private boolean checkpoint(PlanState s, List<Gear> gear, String where) {
            if (!options.gearCheckpoints()) return true;
            for (Gear piece : gear) {
                String held = null;
                for (String id : piece.anyOf()) if (held == null && s.available(id) >= piece.count()) held = id;
                String id = held != null ? held : piece.anyOf().get(0);
                if (!knowledge.isItem(id)) continue;
                if (held == null && !obtain(s, id, piece.count())) {
                    return s.fail("the gear for " + where + " needs " + piece.count() + " " + Step.shortId(id));
                }
                s.reserve(id, piece.count());
            }
            return true;
        }

        private boolean slayDragon(PlanState s) {
            if (s.dragonDead) return true;
            if (!travelTo(s, Location.END)) return false;
            s.addCost(DRAGON_TICKS);
            if (!emit(s, new Step.SlayDragon(), 0, true)) return false;
            s.dragonDead = true;
            return true;
        }

        /** The dragon, then the egg off the exit portal. There is only one egg. */
        private boolean egg(PlanState s, int need) {
            if (need > 1) return s.fail("there is only one dragon egg");
            if (!slayDragon(s)) return false;
            s.inv.add(Step.CollectEgg.EGG, 1);
            s.addCost(STATION_TICKS);
            return emit(s, new Step.CollectEgg(s.inv.count(Step.CollectEgg.EGG)), 1, false);
        }

        /** The legs from {@code from} to {@code to}, not counting {@code from}; null when there is no way. */
        private static List<Location> route(Location from, Location to) {
            Map<Location, Location> previous = new EnumMap<>(Location.class);
            Deque<Location> queue = new ArrayDeque<>();
            previous.put(from, from);
            queue.add(from);
            while (!queue.isEmpty()) {
                Location at = queue.poll();
                if (at == to) break;
                for (Location next : EXITS.get(at)) {
                    if (previous.putIfAbsent(next, at) == null) queue.add(next);
                }
            }
            if (!previous.containsKey(to)) return null;
            List<Location> path = new ArrayList<>();
            for (Location at = to; at != from; at = previous.get(at)) path.add(0, at);
            return path;
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

        private int availableToolUses(PlanState s, ToolReq tool) {
            if (!gates(tool)) return Integer.MAX_VALUE;
            int total = 0;
            for (String id : tools(tool.type(), tool.minTier())) {
                if (s.inv.count(id) <= 0) continue;
                int max = ToolDurability.maxUses(id);
                if (max <= 0) return Integer.MAX_VALUE;
                int reserve = (int) Math.ceil(max * 0.10) * s.inv.count(id);
                total += Math.max(0, s.inv.remainingUses(id) - s.usedDurability.getOrDefault(id, 0) - reserve);
            }
            return total;
        }

        /** Plans enough replacement tools before the mine and reserves their estimated uses. */
        private boolean budgetTool(PlanState s, ToolReq tool, int blocks) {
            if (!gates(tool)) return true;
            int needed = ToolDurability.budget(blocks);
            int available = availableToolUses(s, tool);
            if (available < needed && !obtainTool(s, tool.type(), tool.minTier(), needed - available)) return false;
            if (availableToolUses(s, tool) < needed) return s.fail("not enough durability for " + blocks + " blocks");
            for (String id : tools(tool.type(), tool.minTier())) {
                if (s.inv.count(id) <= 0) continue;
                int max = ToolDurability.maxUses(id);
                if (max <= 0) return true;
                int reserve = (int) Math.ceil(max * 0.10) * s.inv.count(id);
                int usable = Math.max(0, s.inv.remainingUses(id) - s.usedDurability.getOrDefault(id, 0) - reserve);
                int spent = Math.min(needed, usable);
                if (spent > 0) s.usedDurability.merge(id, spent, Integer::sum);
                needed -= spent;
                if (needed == 0) return true;
            }
            return needed == 0;
        }

        /** Obtains the cheapest tool capacity of {@code type} with tier >= {@code minTier}. */
        private boolean obtainTool(PlanState s, String type, int minTier, int missingUses) {
            for (ToolNeed n : toolStack)
                if (n.type().equals(type) && n.minTier() <= minTier)
                    return s.fail("needs a " + type + " to make a " + type);
            List<String> candidates = new ArrayList<>();
            for (String t : tools(type, minTier)) if (!stack.contains(t) && ToolDurability.maxUses(t) > 0) candidates.add(t);
            if (candidates.isEmpty())
                return s.fail("no known way to get a " + type + (minTier > 0 ? " of tier " + minTier + "+" : ""));
            toolStack.add(new ToolNeed(type, minTier));
            try {
                return cheapest(s, candidates, (t, id) -> {
                    int max = ToolDurability.maxUses(id);
                    int usable = max - (int) Math.ceil(max * 0.10);
                    int copies = Math.max(1, (missingUses + usable - 1) / usable);
                    return obtain(t, id, t.inv.count(id) + copies);
                }) >= 0;
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
            if (!s.moved && world.stationNearby(station)) {
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
            // Not ready and not pending: retrieved by a move since it was prepared, so it goes down again from the inventory.
            if (s.pending.remove(station) || !s.ready.contains(station)) {
                if (s.inv.count(station) < 1) return s.fail("no " + Step.shortId(station).replace('_', ' ') + " to set up");
                s.consume(station, 1);
                s.ready.add(station);
                s.owned.add(station);
            }
            s.active.add(station);
            s.addCost(STATION_TICKS);
            return emit(s, new Step.PlaceStation(station), 0, false);
        }

        private void retrieveStations(PlanState s) {
            for (String station : List.copyOf(s.owned)) {
                s.steps.add(new PlanState.Entry(new Step.RetrieveStation(station), 0));
                s.inv.add(station, 1);
                s.addCost(RETRIEVE_TICKS);
                s.ready.remove(station);
                s.active.remove(station);
                s.owned.remove(station);
            }
        }

        private boolean emit(PlanState s, Step step, int gained, boolean moves) {
            if (moves) {
                retrieveStations(s);
                s.active.clear();
                s.ready.clear();
                s.moved = true;
            }
            s.steps.add(new PlanState.Entry(step, gained));
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
            Map<MineGroup, List<MineSource>> groups = new LinkedHashMap<>();
            Map<MineGroup, List<MineSource>> rareGroups = new LinkedHashMap<>();
            for (Source src : sources) {
                if (src instanceof SmeltSource sm && item.equals(sm.output()) && FURNACE.equals(PlanReplay.stationOf(sm)))
                    furnaceRecipe = true;
                if (src instanceof MineSource m && minable(item, m))
                    (m.dropsPerBlock() < MIN_DROP_RATE ? rareGroups : groups).computeIfAbsent(groupOf(m), k -> new ArrayList<>()).add(m);
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
                            List<MineSource> group = (isRare ? rareGroups : groups).remove(groupOf(m));
                            if (group != null) (isRare ? rare : out).add(mineOption(item, group));
                        }
                    }
                    case KillSource k -> {
                        if (!options.allowKill()) killOff = true;
                        else if (k.dropsPerKill() > 0 && k.entity() != null && !NEVER_KILL.contains(k.entity()))
                            (k.dropsPerKill() < MIN_DROP_RATE ? rare : out).add(
                                    new KillOption(k, world.distanceToEntity(k.entity()), knowledge.locationOf(k)));
                    }
                    case BarterSource b -> {
                        if (b.perTrade() > 0 && b.entity() != null && b.currency() != null && !b.currency().equals(item))
                            (b.perTrade() < MIN_DROP_RATE ? rare : out).add(
                                    new BarterOption(b, world.distanceToEntity(b.entity()), knowledge.locationOf(b)));
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

        private MineGroup groupOf(MineSource m) {
            return new MineGroup(toolOf(m), knowledge.locationOf(m));
        }

        private MineOption mineOption(String item, List<MineSource> group) {
            Map<String, Double> distance = new HashMap<>();
            for (MineSource m : group) distance.computeIfAbsent(m.block(), this::blockDistance);
            List<MineSource> sorted = new ArrayList<>(group);
            sorted.sort(Comparator.comparingDouble(m -> distance.get(m.block())));
            List<String> blocks = new ArrayList<>();
            for (MineSource m : sorted) if (!blocks.contains(m.block())) blocks.add(m.block());
            MineSource nearest = sorted.get(0);
            return new MineOption(item, blocks, toolOf(nearest), nearest.dropsPerBlock(), distance.get(nearest.block()),
                    knowledge.locationOf(nearest));
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
                case BarterOption b -> !stack.contains(b.source().currency());
                default -> true;
            };
        }

        private boolean hasFreeAlternative(Ingredient ing) {
            for (String a : ing.anyOf()) if (a != null && !stack.contains(a)) return true;
            return false;
        }

        // ---- quick estimates: only order the options, the trial plans decide ----

        /** Rough cost per item from scratch where the plan starts, ignoring tools, stations and fuel. Infinite if nothing makes it. */
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
                case MineOption m -> (travel(m.distance()) + BREAK_TICKS + PlannerCosts.away(startLocation, m.location())) / m.dropsPerBlock();
                case KillOption k -> (travel(k.distance()) + killTicks(k) + PlannerCosts.away(startLocation, k.location())) / k.source().dropsPerKill();
                case BarterOption b -> (travel(b.distance()) + BARTER_TICKS + PlannerCosts.away(startLocation, b.location())
                        + estimateItem(b.source().currency())) / b.source().perTrade();
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
