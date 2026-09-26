package baritone.acquire.knowledge;

import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.KillSource;
import baritone.acquire.model.MineSource;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Source;
import baritone.acquire.model.ToolReq;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@link Knowledge} built from the vanilla data that ships in the Minecraft jar: recipes, block and
 * mob loot tables, item and block tags, and the English names. It reads the jar on the classpath
 * ({@link VanillaData}), never the integrated server, so it works the same on multiplayer; server-side
 * custom recipes and loot are not seen. Where the answer lives in game code rather than data (which
 * blocks need the right tool, which blocks share a loot table) it asks the built-in registries through
 * {@link RuntimeGameFacts}, and falls back to data-only rules in unit tests.
 *
 * <p>Ids: recipe ids are the recipe file's id ("minecraft:stick", "minecraft:iron_ingot_from_smelting_raw_iron").
 * {@link MineSource#block()} is a block id and {@link KillSource#entity()} an entity type id; outputs are item ids.
 */
public final class VanillaKnowledge implements Knowledge {
    private static final Logger LOGGER = LoggerFactory.getLogger("Baritone/Acquire");

    private static final String NEEDS_STONE = "minecraft:needs_stone_tool";
    private static final String NEEDS_IRON = "minecraft:needs_iron_tool";
    private static final String NEEDS_DIAMOND = "minecraft:needs_diamond_tool";
    private static final List<String> MINEABLE = List.of("pickaxe", "axe", "shovel", "hoe");
    /** Item tag -> tool type. Shears have no tag and are added by id. */
    private static final Map<String, String> TOOL_TAGS = Map.of(
            "minecraft:pickaxes", "pickaxe", "minecraft:axes", "axe", "minecraft:shovels", "shovel",
            "minecraft:hoes", "hoe", "minecraft:swords", "sword");
    private static final String SHEARS = "minecraft:shears";
    private static final ToolReq SHEARS_REQ = new ToolReq("shears", 1, true);
    /** Tier per material when the incorrect_for_* tags are missing. */
    private static final Map<String, Integer> FALLBACK_TIER = Map.of(
            "wooden", 1, "golden", 1, "stone", 2, "copper", 2, "iron", 3, "diamond", 4, "netherite", 5);
    /** Cheapest material first: gold is tier 1 but needs an iron pickaxe to mine, so it sorts after copper. */
    private static final List<String> MATERIAL_COST = List.of("wooden", "stone", "copper", "golden", "iron", "diamond", "netherite");

    /**
     * Vanilla furnace fuels, copied from {@code FuelValues.vanillaBurnTimes} (26.2) with its 200-tick
     * smelt time. The game builds that table from item tags bound to a level, so it is not reachable
     * before joining a world; this copy resolves the same tags from the data files instead. Applied in
     * order (later entries win); {@code #minecraft:non_flammable_wood} is removed at the end.
     */
    private static final List<Map.Entry<String, Integer>> FUEL_TABLE = List.of(
            Map.entry("minecraft:lava_bucket", 20000), Map.entry("minecraft:coal_block", 16000),
            Map.entry("minecraft:blaze_rod", 2400), Map.entry("minecraft:coal", 1600), Map.entry("minecraft:charcoal", 1600),
            Map.entry("#minecraft:logs", 300), Map.entry("#minecraft:bamboo_blocks", 300), Map.entry("#minecraft:planks", 300),
            Map.entry("minecraft:bamboo_mosaic", 300), Map.entry("#minecraft:wooden_stairs", 300),
            Map.entry("minecraft:bamboo_mosaic_stairs", 300), Map.entry("#minecraft:wooden_slabs", 150),
            Map.entry("minecraft:bamboo_mosaic_slab", 150), Map.entry("#minecraft:wooden_trapdoors", 300),
            Map.entry("#minecraft:wooden_pressure_plates", 300), Map.entry("#minecraft:wooden_shelves", 300),
            Map.entry("#minecraft:wooden_fences", 300), Map.entry("#minecraft:fence_gates", 300),
            Map.entry("minecraft:note_block", 300), Map.entry("minecraft:bookshelf", 300),
            Map.entry("minecraft:chiseled_bookshelf", 300), Map.entry("minecraft:lectern", 300),
            Map.entry("minecraft:jukebox", 300), Map.entry("minecraft:chest", 300), Map.entry("minecraft:trapped_chest", 300),
            Map.entry("minecraft:crafting_table", 300), Map.entry("minecraft:daylight_detector", 300),
            Map.entry("#minecraft:banners", 300), Map.entry("minecraft:bow", 300), Map.entry("minecraft:fishing_rod", 300),
            Map.entry("minecraft:ladder", 300), Map.entry("#minecraft:signs", 200), Map.entry("#minecraft:hanging_signs", 800),
            Map.entry("minecraft:wooden_shovel", 200), Map.entry("minecraft:wooden_sword", 200),
            Map.entry("minecraft:wooden_spear", 200), Map.entry("minecraft:wooden_hoe", 200),
            Map.entry("minecraft:wooden_axe", 200), Map.entry("minecraft:wooden_pickaxe", 200),
            Map.entry("#minecraft:wooden_doors", 200), Map.entry("#minecraft:boats", 1200), Map.entry("#minecraft:wool", 100),
            Map.entry("#minecraft:wooden_buttons", 100), Map.entry("minecraft:stick", 100), Map.entry("#minecraft:saplings", 100),
            Map.entry("minecraft:bowl", 100), Map.entry("#minecraft:wool_carpets", 67), Map.entry("minecraft:dried_kelp_block", 4001),
            Map.entry("minecraft:crossbow", 300), Map.entry("minecraft:bamboo", 50), Map.entry("minecraft:dead_bush", 100),
            Map.entry("minecraft:short_dry_grass", 100), Map.entry("minecraft:tall_dry_grass", 100),
            Map.entry("minecraft:scaffolding", 50), Map.entry("minecraft:loom", 300), Map.entry("minecraft:barrel", 300),
            Map.entry("minecraft:cartography_table", 300), Map.entry("minecraft:fletching_table", 300),
            Map.entry("minecraft:smithing_table", 300), Map.entry("minecraft:composter", 300), Map.entry("minecraft:azalea", 100),
            Map.entry("minecraft:flowering_azalea", 100), Map.entry("minecraft:mangrove_roots", 300),
            Map.entry("minecraft:leaf_litter", 100));
    private static final String NON_FLAMMABLE_WOOD = "minecraft:non_flammable_wood";

    private static Knowledge instance;

    private final Map<String, List<Source>> sources;
    private final Map<String, String> toolTypes;
    private final Map<String, Integer> toolTiers;
    private final Map<String, List<String>> toolsByType;
    private final Map<String, Integer> fuels;
    private final Set<String> items;
    private final ItemNames names;
    private final String summary;

    private VanillaKnowledge(Builder b) {
        Map<String, List<Source>> frozen = new HashMap<>();
        b.sources.forEach((item, list) -> frozen.put(item, List.copyOf(list)));
        this.sources = Map.copyOf(frozen);
        this.toolTypes = Map.copyOf(b.toolTypes);
        this.toolTiers = Map.copyOf(b.toolTiers);
        Map<String, List<String>> byType = new HashMap<>();
        b.toolTypes.forEach((item, type) -> byType.computeIfAbsent(type, t -> new ArrayList<>()).add(item));
        byType.values().forEach(list -> list.sort(Comparator.comparingInt(VanillaKnowledge::materialCost)
                .thenComparingInt(b.toolTiers::get).thenComparing(Comparator.naturalOrder())));
        Map<String, List<String>> frozenTools = new HashMap<>();
        byType.forEach((type, list) -> frozenTools.put(type, List.copyOf(list)));
        this.toolsByType = Map.copyOf(frozenTools);
        this.fuels = Collections.unmodifiableMap(new LinkedHashMap<>(b.fuels));
        this.items = Set.copyOf(b.items);
        this.names = new ItemNames(this.items, b.displayNames);
        this.summary = b.summary();
    }

    /**
     * Loads (once, then cached) and returns the knowledge base. Safe to call from any thread; the first
     * call takes about a second (see {@link #preload()}).
     *
     * @throws IllegalStateException if the vanilla data cannot be found (not cached, the next call retries)
     */
    public static synchronized Knowledge get() {
        if (instance == null) instance = load();
        return instance;
    }

    /** Starts loading on a background thread so a later {@link #get()} returns at once. */
    public static void preload() {
        Thread thread = new Thread(() -> {
            try {
                get();
            } catch (RuntimeException e) {
                LOGGER.warn("Acquire knowledge failed to load", e);
            }
        }, "Acquire knowledge loader");
        thread.setDaemon(true);
        thread.start();
    }

    private static VanillaKnowledge load() {
        long start = System.nanoTime();
        GameFacts facts = RuntimeGameFacts.tryCreate();
        Map<String, String> files = VanillaData.fromClasspath();
        if (!VanillaData.looksComplete(files)) {
            try {
                files = new HashMap<>(VanillaPackData.read());
                String lang = VanillaData.resourceText(VanillaData.LANG);
                if (lang != null) files.put(VanillaData.LANG, lang);
            } catch (Throwable t) {
                LOGGER.warn("Acquire knowledge: vanilla data pack fallback failed", t);
            }
        }
        if (!VanillaData.looksComplete(files)) {
            throw new IllegalStateException("Vanilla recipes and loot tables were not found on the classpath");
        }
        VanillaKnowledge knowledge = fromData(files, facts);
        LOGGER.info("Acquire knowledge loaded in {} ms: {}", (System.nanoTime() - start) / 1_000_000, knowledge.summary);
        return knowledge;
    }

    /** Builds from data files (jar path such as "data/minecraft/recipe/stick.json" -> JSON text) with data-only fallbacks. */
    static VanillaKnowledge fromData(Map<String, String> files) {
        return fromData(files, GameFacts.NONE);
    }

    static VanillaKnowledge fromData(Map<String, String> files, GameFacts facts) {
        return new Builder(files, facts).build();
    }

    /** Every known item id. */
    Set<String> items() {
        return items;
    }

    /** Counts for logs: "1234 recipes (...skipped), 1100 block tables, ...". */
    String summary() {
        return summary;
    }

    @Override
    public List<Source> sourcesFor(String item) {
        return item == null ? List.of() : sources.getOrDefault(id(item), List.of());
    }

    @Override
    public String toolType(String item) {
        return item == null ? null : toolTypes.get(id(item));
    }

    @Override
    public int toolTier(String item) {
        return item == null ? 0 : toolTiers.getOrDefault(id(item), 0);
    }

    @Override
    public List<String> toolsOf(String type, int minTier) {
        List<String> all = toolsByType.getOrDefault(type, List.of());
        List<String> out = new ArrayList<>(all.size());
        for (String tool : all) if (toolTiers.get(tool) >= minTier) out.add(tool);
        return List.copyOf(out);
    }

    @Override
    public Map<String, Integer> fuels() {
        return fuels;
    }

    @Override
    public boolean isItem(String item) {
        return item != null && items.contains(id(item));
    }

    @Override
    public Optional<String> resolveItem(String userText) {
        return names.resolve(userText);
    }

    @Override
    public List<String> suggest(String userText, int limit) {
        return names.suggest(userText, limit);
    }

    /** "stone" -> "minecraft:stone"; namespaced ids are kept. */
    static String id(String s) {
        String t = s.trim();
        return t.indexOf(':') >= 0 ? t : "minecraft:" + t;
    }

    /** "data/minecraft/recipe/stick.json" with dir "recipe/" -> "minecraft:stick"; null if the path is not under that dir. */
    static String idFromPath(String path, String dir) {
        if (!path.startsWith("data/") || !path.endsWith(".json")) return null;
        int slash = path.indexOf('/', 5);
        if (slash < 0) return null;
        String rest = path.substring(slash + 1);
        if (!rest.startsWith(dir)) return null;
        return path.substring(5, slash) + ":" + rest.substring(dir.length(), rest.length() - ".json".length());
    }

    private static int materialCost(String tool) {
        String path = tool.substring(tool.indexOf(':') + 1);
        for (int i = 0; i < MATERIAL_COST.size(); i++) if (path.startsWith(MATERIAL_COST.get(i) + "_")) return i;
        return MATERIAL_COST.size();
    }

    /** Parses the files once and derives everything; discarded after construction. */
    private static final class Builder {
        final GameFacts facts;
        final Map<String, JsonObject> recipes = new TreeMap<>();
        final Map<String, JsonObject> lootTables = new HashMap<>();
        final Map<String, JsonObject> tagFiles = new HashMap<>();
        final Map<String, JsonObject> predicates = new HashMap<>();
        Map<String, String> lang = Map.of();
        Tags itemTags;
        Tags blockTags;

        final Map<String, List<Source>> sources = new HashMap<>();
        final Map<String, String> toolTypes = new HashMap<>();
        final Map<String, Integer> toolTiers = new HashMap<>();
        final Map<String, Integer> fuels = new LinkedHashMap<>();
        final Set<String> items = new HashSet<>();
        final Map<String, String> displayNames = new HashMap<>();
        Map<String, Integer> skippedRecipes = Map.of();
        int crafts, smelts, blockTables, mineSources, mobTables, killSources;

        Builder(Map<String, String> files, GameFacts facts) {
            this.facts = facts;
            for (Map.Entry<String, String> file : files.entrySet()) {
                String path = file.getKey();
                try {
                    JsonElement json = JsonParser.parseString(file.getValue());
                    if (!json.isJsonObject()) continue;
                    if (path.equals(VanillaData.LANG)) {
                        Map<String, String> l = new HashMap<>();
                        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                            if (e.getValue().isJsonPrimitive()) l.put(e.getKey(), e.getValue().getAsString());
                        }
                        lang = l;
                    } else if (idFromPath(path, "recipe/") != null) {
                        recipes.put(idFromPath(path, "recipe/"), json.getAsJsonObject());
                    } else if (idFromPath(path, "loot_table/") != null) {
                        lootTables.put(idFromPath(path, "loot_table/"), json.getAsJsonObject());
                    } else if (idFromPath(path, "predicate/") != null) {
                        predicates.put(idFromPath(path, "predicate/"), json.getAsJsonObject());
                    } else if (idFromPath(path, "tags/") != null) {
                        tagFiles.put(path, json.getAsJsonObject());
                    }
                } catch (RuntimeException e) {
                    LOGGER.debug("Acquire knowledge: skipping unreadable {}", path, e);
                }
            }
            lootTables.replaceAll((id, table) -> LootFormat.normalize(table, predicates));
            itemTags = Tags.parse(tagFiles, "item");
            blockTags = Tags.parse(tagFiles, "block");
        }

        VanillaKnowledge build() {
            readTools();
            readRecipes();
            LootReader loot = new LootReader(lootTables, itemTags);
            readBlockDrops(loot);
            readMobDrops(loot);
            readItems();
            readFuels();
            return new VanillaKnowledge(this);
        }

        private void add(Source source) {
            sources.computeIfAbsent(source.output(), k -> new ArrayList<>()).add(source);
        }

        private void readTools() {
            TOOL_TAGS.forEach((tag, type) -> {
                if (!itemTags.has(tag)) return;
                for (String item : itemTags.get(tag)) {
                    toolTypes.put(item, type);
                    toolTiers.put(item, tierOf(item, type));
                }
            });
            toolTypes.put(SHEARS, "shears");
            toolTiers.put(SHEARS, 1);
        }

        /** Tier from the material's incorrect_for_*_tool tag against the needs_*_tool tags (copper sits with stone). */
        private int tierOf(String item, String type) {
            String path = item.substring(item.indexOf(':') + 1);
            String material = path.endsWith("_" + type) ? path.substring(0, path.length() - type.length() - 1) : path;
            String tagMaterial = material.equals("golden") ? "gold" : material;
            String incorrect = "minecraft:incorrect_for_" + tagMaterial + "_tool";
            if (!blockTags.has(incorrect)) return FALLBACK_TIER.getOrDefault(material, 1);
            Set<String> blocked = Set.copyOf(blockTags.get(incorrect));
            if (coversTag(blocked, NEEDS_STONE)) return 1;
            if (coversTag(blocked, NEEDS_IRON)) return 2;
            if (coversTag(blocked, NEEDS_DIAMOND)) return 3;
            return material.equals("netherite") ? 5 : 4;
        }

        private boolean coversTag(Set<String> blocked, String needsTag) {
            List<String> needs = blockTags.get(needsTag);
            return !needs.isEmpty() && blocked.containsAll(needs);
        }

        ToolReq toolReq(String block) {
            String type = null;
            for (String t : MINEABLE) {
                if (blockTags.contains("minecraft:mineable/" + t, block)) {
                    type = t;
                    break;
                }
            }
            int tier = blockTags.contains(NEEDS_DIAMOND, block) ? 4
                    : blockTags.contains(NEEDS_IRON, block) ? 3
                    : blockTags.contains(NEEDS_STONE, block) ? 2 : 0;
            Boolean known = facts.requiresCorrectTool(block);
            boolean required = known != null ? known : "pickaxe".equals(type);
            if (type == null && required && (block.equals("minecraft:cobweb") || blockTags.contains("minecraft:sword_efficient", block))) {
                type = "sword";
            }
            if (type == null) return required ? new ToolReq(null, tier, true) : ToolReq.NONE;
            return new ToolReq(type, Math.max(1, tier), required);
        }

        /** The cheapest tool that satisfies {@code req}, used as "the tool in hand" when evaluating loot. */
        private String toolFor(ToolReq req) {
            if (req.type() == null) return null;
            if (req.type().equals("shears")) return SHEARS;
            String best = null;
            int bestTier = Integer.MAX_VALUE;
            int bestCost = Integer.MAX_VALUE;
            for (Map.Entry<String, String> e : toolTypes.entrySet()) {
                if (!e.getValue().equals(req.type())) continue;
                int tier = toolTiers.get(e.getKey());
                int cost = materialCost(e.getKey());
                if (tier < Math.max(1, req.minTier())) continue;
                if (cost < bestCost || (cost == bestCost && tier < bestTier)) {
                    best = e.getKey();
                    bestTier = tier;
                    bestCost = cost;
                }
            }
            return best;
        }

        private void readRecipes() {
            RecipeReader reader = new RecipeReader(itemTags);
            recipes.forEach((id, json) -> {
                Source source = reader.read(id, json);
                if (source == null) return;
                add(source);
                if (source instanceof CraftSource) crafts++;
                else smelts++;
            });
            skippedRecipes = reader.skipped();
        }

        private void readBlockDrops(LootReader loot) {
            List<String> ids = new ArrayList<>(lootTables.keySet());
            ids.sort(null);
            for (String table : ids) {
                String block = directChild(table, "blocks/");
                if (block == null) continue;
                blockTables++;
                List<String> blocks = facts.blocksWithLootTable(table);
                if (blocks == null || blocks.isEmpty()) blocks = List.of(block);
                String tool = toolFor(toolReq(block));
                Map<String, Double> normal = loot.expectedDrops(table, new LootReader.Scenario(tool, false, true));
                Map<String, Double> silk = loot.expectedDrops(table, new LootReader.Scenario(tool, true, true));
                Map<String, Double> shears = loot.expectedDrops(table, new LootReader.Scenario(SHEARS, false, true));
                for (String b : blocks) {
                    ToolReq req = toolReq(b);
                    normal.forEach((item, n) -> mine(new MineSource(b, item, n, req, false)));
                    silk.forEach((item, n) -> {
                        if (!normal.containsKey(item)) mine(new MineSource(b, item, n, req, true));
                    });
                    shears.forEach((item, n) -> {
                        if (!normal.containsKey(item)) mine(new MineSource(b, item, n, SHEARS_REQ, false));
                    });
                }
            }
        }

        private void mine(MineSource source) {
            add(source);
            mineSources++;
        }

        private void readMobDrops(LootReader loot) {
            List<String> ids = new ArrayList<>(lootTables.keySet());
            ids.sort(null);
            for (String table : ids) {
                String entity = directChild(table, "entities/");
                if (entity == null) continue;
                mobTables++;
                Map<String, Double> byPlayer = loot.expectedDrops(table, new LootReader.Scenario(null, false, true));
                Map<String, Double> otherwise = loot.expectedDrops(table, new LootReader.Scenario(null, false, false));
                byPlayer.forEach((item, n) -> {
                    add(new KillSource(entity, item, n, otherwise.getOrDefault(item, 0.0) <= 1e-9));
                    killSources++;
                });
            }
        }

        /** "minecraft:blocks/stone" with "blocks/" -> "minecraft:stone"; null for other folders and nested tables. */
        private static String directChild(String table, String folder) {
            int colon = table.indexOf(':');
            String path = table.substring(colon + 1);
            if (!path.startsWith(folder)) return null;
            String name = path.substring(folder.length());
            return name.isEmpty() || name.indexOf('/') >= 0 ? null : table.substring(0, colon + 1) + name;
        }

        private void readItems() {
            Set<String> known = facts.itemIds();
            if (known != null) {
                items.addAll(known);
            } else {
                // Data-only: every item the data mentions, plus every "item.minecraft.<path>" name.
                for (String key : lang.keySet()) {
                    if (!key.startsWith("item.")) continue;
                    String[] parts = key.split("\\.");
                    if (parts.length == 3 && parts[1].equals("minecraft")) items.add("minecraft:" + parts[2]);
                }
                for (List<Source> list : sources.values()) {
                    for (Source s : list) {
                        items.add(s.output());
                        if (s instanceof CraftSource c) for (Ingredient i : c.ingredients()) items.addAll(i.anyOf());
                        if (s instanceof SmeltSource sm) items.addAll(sm.input().anyOf());
                    }
                }
                items.addAll(toolTypes.keySet());
                items.remove("minecraft:air");
            }
            for (String item : items) {
                String name = null;
                String key = facts.descriptionId(item);
                if (key != null) name = lang.get(key);
                if (name == null) {
                    String ns = item.substring(0, item.indexOf(':'));
                    String path = item.substring(item.indexOf(':') + 1);
                    name = lang.getOrDefault("item." + ns + "." + path, lang.get("block." + ns + "." + path));
                }
                if (name != null) displayNames.put(item, name);
            }
        }

        private void readFuels() {
            Set<String> known = facts.itemIds();
            for (Map.Entry<String, Integer> fuel : FUEL_TABLE) {
                for (String item : itemTags.resolve(fuel.getKey())) {
                    if (known == null || known.contains(item)) fuels.put(item, fuel.getValue());
                }
            }
            if (itemTags.has(NON_FLAMMABLE_WOOD)) itemTags.get(NON_FLAMMABLE_WOOD).forEach(fuels::remove);
        }

        String summary() {
            return crafts + " crafting + " + smelts + " cooking recipes (skipped " + skippedRecipes + "), "
                    + mineSources + " block drops from " + blockTables + " block tables, "
                    + killSources + " mob drops from " + mobTables + " mob tables, "
                    + items.size() + " items, " + toolTypes.size() + " tools, " + fuels.size() + " fuels";
        }
    }
}
