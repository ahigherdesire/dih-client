package baritone.acquire.planner;

import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.KillSource;
import baritone.acquire.model.MineSource;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Source;
import baritone.acquire.model.ToolReq;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Hand-written vanilla-like sources for planner tests: the wood, stone and iron tiers, torches, string, flint,
 * plus the awkward shapes of the real data (placed blocks dropping themselves, very rare drops, lava as fuel).
 */
final class FakeKnowledge implements Knowledge {
    static final String LOG = "minecraft:oak_log";
    static final String PLANKS = "minecraft:oak_planks";
    static final String STICK = "minecraft:stick";
    static final String TABLE = "minecraft:crafting_table";
    static final String WOODEN_PICKAXE = "minecraft:wooden_pickaxe";
    static final String COBBLESTONE = "minecraft:cobblestone";
    static final String STONE_PICKAXE = "minecraft:stone_pickaxe";
    static final String FURNACE = "minecraft:furnace";
    static final String RAW_IRON = "minecraft:raw_iron";
    static final String IRON_INGOT = "minecraft:iron_ingot";
    static final String IRON_BLOCK = "minecraft:iron_block";
    static final String COAL = "minecraft:coal";
    static final String IRON_PICKAXE = "minecraft:iron_pickaxe";
    static final String TORCH = "minecraft:torch";
    static final String STRING = "minecraft:string";
    static final String FLINT = "minecraft:flint";
    static final String BEDROCK = "minecraft:bedrock";
    static final String APPLE = "minecraft:apple";
    static final String LAVA_BUCKET = "minecraft:lava_bucket";
    static final String WALL_TORCH = "minecraft:wall_torch";

    private static final ToolReq PICK_1 = new ToolReq("pickaxe", 1, true);
    private static final ToolReq PICK_2 = new ToolReq("pickaxe", 2, true);

    private final Map<String, List<Source>> sources = new LinkedHashMap<>();
    private final Map<String, Integer> pickaxes = new LinkedHashMap<>();
    private final Map<String, Integer> fuels = new LinkedHashMap<>();
    private final Set<String> items = new HashSet<>();

    FakeKnowledge() {
        mine(LOG, "minecraft:oak_log", 1.0, new ToolReq("axe", 0, false));
        craft(PLANKS, 4, false, ing(LOG, 1));
        craft(STICK, 4, false, ing(PLANKS, 2));
        craft(TABLE, 1, false, ing(PLANKS, 4));
        craft(WOODEN_PICKAXE, 1, true, ing(PLANKS, 3), ing(STICK, 2));
        mine(COBBLESTONE, "minecraft:stone", 1.0, PICK_1);
        craft(STONE_PICKAXE, 1, true, ing(COBBLESTONE, 3), ing(STICK, 2));
        craft(FURNACE, 1, true, ing(COBBLESTONE, 8));
        mine(RAW_IRON, "minecraft:iron_ore", 1.0, PICK_2);
        mine(RAW_IRON, "minecraft:deepslate_iron_ore", 1.0, PICK_2);
        add(new SmeltSource("minecraft:iron_ingot_from_smelting_raw_iron", IRON_INGOT, 1, ing(RAW_IRON, 1),
                "minecraft:furnace", 200));
        craft(IRON_INGOT, 9, false, ing(IRON_BLOCK, 1));
        craft(IRON_BLOCK, 1, true, ing(IRON_INGOT, 9));
        mine(COAL, "minecraft:coal_ore", 1.0, PICK_1);
        craft(IRON_PICKAXE, 1, true, ing(IRON_INGOT, 3), ing(STICK, 2));
        craft(TORCH, 4, false, ing(COAL, 1), ing(STICK, 1));
        add(new KillSource("minecraft:spider", STRING, 1.0, false));
        mine(FLINT, "minecraft:gravel", 0.1, new ToolReq("shovel", 0, false));

        // Shapes the real data has: placed blocks that drop themselves (or their item), very rare drops.
        mine(TABLE, "minecraft:crafting_table", 1.0, new ToolReq("axe", 0, false));
        mine(FURNACE, "minecraft:furnace", 1.0, PICK_1);
        mine(TORCH, "minecraft:torch", 1.0, ToolReq.NONE);
        mine(TORCH, WALL_TORCH, 1.0, ToolReq.NONE);
        add(new KillSource("minecraft:zombie", IRON_INGOT, 0.0083, true));
        // On the never-kill list: the planner must not use these even though they look cheap.
        add(new KillSource("minecraft:iron_golem", IRON_INGOT, 4.0, false));
        add(new KillSource("minecraft:cat", STRING, 1.0, false));
        mine(APPLE, "minecraft:oak_leaves", 0.005, new ToolReq("hoe", 0, false));

        pickaxes.put(WOODEN_PICKAXE, 1);
        pickaxes.put(STONE_PICKAXE, 2);
        pickaxes.put(IRON_PICKAXE, 3);

        fuels.put(COAL, 1600);
        fuels.put(PLANKS, 300);
        fuels.put(LOG, 300);
        fuels.put(STICK, 100);
        fuels.put(LAVA_BUCKET, 20000);

        items.add(BEDROCK);
        items.add(LAVA_BUCKET);
    }

    private static Ingredient ing(String item, int count) {
        return new Ingredient(List.of(item), count);
    }

    private void mine(String output, String block, double drops, ToolReq tool) {
        add(new MineSource(block, output, drops, tool, false));
        if (!block.equals(WALL_TORCH)) items.add(block); // wall_torch is a block without an item
    }

    private void craft(String output, int count, boolean table, Ingredient... ingredients) {
        String id = output + (sources.containsKey(output) ? "_alt" + sources.get(output).size() : "");
        add(new CraftSource(id, output, count, List.of(ingredients), table));
    }

    private void add(Source source) {
        sources.computeIfAbsent(source.output(), k -> new ArrayList<>()).add(source);
        items.add(source.output());
    }

    @Override
    public List<Source> sourcesFor(String item) {
        return sources.getOrDefault(item, List.of());
    }

    @Override
    public String toolType(String item) {
        return pickaxes.containsKey(item) ? "pickaxe" : null;
    }

    @Override
    public int toolTier(String item) {
        return pickaxes.getOrDefault(item, 0);
    }

    @Override
    public List<String> toolsOf(String type, int minTier) {
        if (!"pickaxe".equals(type)) return List.of();
        List<String> out = new ArrayList<>();
        pickaxes.forEach((item, tier) -> {
            if (tier >= minTier) out.add(item);
        });
        return out;
    }

    @Override
    public Map<String, Integer> fuels() {
        return fuels;
    }

    @Override
    public boolean isItem(String item) {
        return items.contains(item);
    }

    @Override
    public Optional<String> resolveItem(String userText) {
        String id = userText.contains(":") ? userText : "minecraft:" + userText;
        return items.contains(id) ? Optional.of(id) : Optional.empty();
    }

    @Override
    public List<String> suggest(String userText, int limit) {
        return List.of();
    }
}
