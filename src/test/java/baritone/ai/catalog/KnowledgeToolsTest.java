package baritone.ai.catalog;

import baritone.acquire.knowledge.Knowledge;
import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.KillSource;
import baritone.acquire.model.MineSource;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Source;
import baritone.acquire.model.ToolReq;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class KnowledgeToolsTest {

    /** Iron: ore mined with a stone pickaxe, smelted to ingots, ingots crafted into a pickaxe; zombies drop it. */
    private static final class Fake implements Knowledge {
        final List<Source> all = List.of(
                new MineSource("minecraft:iron_ore", "minecraft:raw_iron", 1, new ToolReq("pickaxe", 2, true), false),
                new SmeltSource("iron_ingot_from_raw", "minecraft:iron_ingot", 1,
                        new Ingredient(List.of("minecraft:raw_iron"), 1), "minecraft:furnace", 200),
                new CraftSource("iron_pickaxe", "minecraft:iron_pickaxe", 1, List.of(
                        new Ingredient(List.of("minecraft:iron_ingot"), 3), new Ingredient(List.of("minecraft:stick"), 2)), true),
                new KillSource("minecraft:zombie", "minecraft:iron_ingot", 0.008, true),
                new KillSource("minecraft:zombie", "minecraft:rotten_flesh", 1.0, false));

        @Override
        public List<Source> sourcesFor(String item) {
            List<Source> out = new ArrayList<>();
            for (Source s : all) if (output(s).equals(item)) out.add(s);
            return out;
        }

        private static String output(Source s) {
            return switch (s) {
                case MineSource m -> m.output();
                case SmeltSource m -> m.output();
                case CraftSource m -> m.output();
                case KillSource m -> m.output();
            };
        }

        @Override
        public List<Source> allSources() {
            return all;
        }

        @Override
        public String toolType(String item) {
            return null;
        }

        @Override
        public int toolTier(String item) {
            return 0;
        }

        @Override
        public List<String> toolsOf(String type, int minTier) {
            return List.of();
        }

        @Override
        public Map<String, Integer> fuels() {
            Map<String, Integer> fuels = new LinkedHashMap<>();
            fuels.put("minecraft:stick", 100);
            fuels.put("minecraft:coal", 1600);
            return fuels;
        }

        @Override
        public boolean isItem(String item) {
            return resolveItem(item).isPresent();
        }

        @Override
        public Optional<String> resolveItem(String userText) {
            String t = userText.trim().toLowerCase().replace(' ', '_');
            if (t.equals("iron") || t.equals("iron_ingot")) return Optional.of("minecraft:iron_ingot");
            if (t.equals("raw_iron")) return Optional.of("minecraft:raw_iron");
            if (t.equals("iron_pickaxe") || t.equals("iron_pick")) return Optional.of("minecraft:iron_pickaxe");
            return Optional.empty();
        }

        @Override
        public List<String> suggest(String userText, int limit) {
            return userText.startsWith("ir") ? List.of("minecraft:iron_ingot", "minecraft:iron_pickaxe") : List.of();
        }
    }

    private static ToolResult call(String tool, String key, String value) {
        ToolRegistry registry = new ToolRegistry();
        KnowledgeTools.register(registry, Fake::new);
        JsonObject args = new JsonObject();
        if (key != null) args.addProperty(key, value);
        return registry.call(ToolContext.of(null, ToolContext.Source.CHAT), tool, args);
    }

    @Test
    void recipeOfListsCraftingAndSmelting() {
        ToolResult pick = call("recipe_of", "item", "iron pick");
        assertTrue(pick.ok());
        assertEquals("iron_pickaxe: craft 1 from 3 iron_ingot, 2 stick (crafting table).", pick.text());
        assertEquals(1, pick.facts().get("recipes"));
        ToolResult ingot = call("recipe_of", "item", "iron");
        assertEquals("iron_ingot: smelt raw_iron in a furnace (10 s).", ingot.text());
        ToolResult raw = call("recipe_of", "item", "raw_iron");
        assertTrue(raw.text().contains("can't be crafted or smelted"), raw.text());
        assertEquals(0, raw.facts().get("recipes"));
    }

    @Test
    void unknownItemsSuggestCloseOnes() {
        ToolResult result = call("recipe_of", "item", "xyz");
        assertEquals(ToolResult.Status.FAILED, result.status());
        assertEquals("No item called \"xyz\".", result.text());
        ToolResult close = call("uses_of", "item", "iro");
        assertEquals("No item called \"iro\". Did you mean iron_ingot, iron_pickaxe?", close.text());
    }

    @Test
    void usesOfReversesTheRecipes() {
        ToolResult result = call("uses_of", "item", "iron");
        assertEquals("iron_ingot makes: iron_pickaxe.", result.text());
        assertEquals(1, result.facts().get("uses"));
        assertEquals("raw_iron makes: iron_ingot (smelted).", call("uses_of", "item", "raw_iron").text());
    }

    @Test
    void dropsOfCoversMobsAndBlocksWithTheToolNeeded() {
        ToolResult zombie = call("drops_of", "thing", "zombie");
        assertEquals("zombie drops 0.01 iron_ingot per kill (player kill); 1.00 rotten_flesh per kill.", zombie.text());
        ToolResult ore = call("drops_of", "thing", "minecraft:iron_ore");
        assertEquals("iron_ore drops 1.00 raw_iron per block. Needs a stone pickaxe or better.", ore.text());
        assertEquals("pickaxe", ore.facts().get("tool"));
        assertEquals("stone", ore.facts().get("min_tier"));
        assertEquals(ToolResult.Status.FAILED, call("drops_of", "thing", "bedrock").status());
    }

    @Test
    void whereFromListsEveryWay() {
        ToolResult result = call("where_from", "item", "iron_ingot");
        assertEquals("iron_ingot: smelt raw_iron; kill zombie.", result.text());
        assertEquals(2, result.facts().get("ways"));
    }

    @Test
    void itemLookupAndFuels() {
        ToolResult found = call("item_lookup", "name", "iron pick");
        assertEquals("\"iron pick\" is iron_pickaxe.", found.text());
        assertEquals("iron_pickaxe", found.facts().get("item"));
        ToolResult fuels = call("fuels", null, null);
        assertEquals("Items smelted per fuel: coal 8; stick 0.5.", fuels.text());
        assertEquals(2, fuels.facts().get("fuels"));
    }
}
