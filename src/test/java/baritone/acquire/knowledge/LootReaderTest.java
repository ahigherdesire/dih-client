package baritone.acquire.knowledge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Expected-value semantics of the loot evaluator on single tables. */
final class LootReaderTest {
    private static final LootReader.Scenario HAND = new LootReader.Scenario(null, false, true);
    private static final LootReader.Scenario SHEARS = new LootReader.Scenario("minecraft:shears", false, true);
    private static final LootReader.Scenario SILK = new LootReader.Scenario("minecraft:iron_pickaxe", true, true);
    private static final LootReader.Scenario MOB_ONLY = new LootReader.Scenario(null, false, false);

    private static Map<String, Double> drops(String json, LootReader.Scenario scenario) {
        return drops(Map.of("minecraft:blocks/test", json), "minecraft:blocks/test", scenario);
    }

    private static Map<String, Double> drops(Map<String, String> tables, String id, LootReader.Scenario scenario) {
        Map<String, JsonObject> parsed = new HashMap<>();
        tables.forEach((k, v) -> parsed.put(k, JsonParser.parseString(v).getAsJsonObject()));
        Tags itemTags = Tags.of(Map.of("minecraft:discs", List.of("minecraft:disc_a", "minecraft:disc_b")));
        return new LootReader(parsed, itemTags).expectedDrops(id, scenario);
    }

    private static String item(String name, String extra) {
        return "{\"type\":\"minecraft:item\",\"name\":\"" + name + "\"" + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private static String pool(String rolls, String entries) {
        return "{\"rolls\":" + rolls + ",\"entries\":[" + entries + "]}";
    }

    private static String table(String... pools) {
        return "{\"type\":\"minecraft:block\",\"pools\":[" + String.join(",", pools) + "]}";
    }

    @Test void rollsAreAveragedAndWeightsSplitThePick() {
        String t = table(pool("{\"type\":\"minecraft:uniform\",\"min\":1,\"max\":3}",
                item("minecraft:a", "\"weight\":3") + "," + item("minecraft:b", "") + ",{\"type\":\"minecraft:empty\"}"));
        Map<String, Double> d = drops(t, HAND);
        assertEquals(2 * 0.6, d.get("minecraft:a"), 1e-9);
        assertEquals(2 * 0.2, d.get("minecraft:b"), 1e-9);
    }

    @Test void randomChanceAndBinomialMultiply() {
        String t = table(pool("1", item("minecraft:a", """
                "conditions":[{"condition":"minecraft:random_chance","chance":0.25}],
                "functions":[{"function":"minecraft:set_count","count":{"type":"minecraft:binomial","n":4,"p":0.5}}]""")));
        assertEquals(0.25 * 2, drops(t, HAND).get("minecraft:a"), 1e-9);
    }

    @Test void limitCountClampsTheDistributionNotTheMean() {
        // Huge mushroom block: uniform -6..2 clamped at 0 gives (1 + 2) / 9, not max(0, -2).
        String t = table(pool("1", item("minecraft:brown_mushroom", """
                "functions":[{"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":-6,"max":2}},
                             {"function":"minecraft:limit_count","limit":{"min":0}}]""")));
        assertEquals(3.0 / 9, drops(t, HAND).get("minecraft:brown_mushroom"), 1e-9);
    }

    @Test void invertedAnyOfSeparatesShearsFromTheHand() {
        String shearsOrSilk = """
                {"condition":"minecraft:any_of","terms":[{"condition":"minecraft:match_tool","predicate":{"items":"minecraft:shears"}},
                  {"condition":"minecraft:match_tool","predicate":{"predicates":{"minecraft:enchantments":[{"enchantments":"minecraft:silk_touch"}]}}}]}""";
        String t = table(
                pool("1", item("minecraft:leaves", "\"conditions\":[" + shearsOrSilk + "]")),
                "{\"rolls\":1,\"conditions\":[{\"condition\":\"minecraft:inverted\",\"term\":" + shearsOrSilk + "}],\"entries\":["
                        + item("minecraft:stick", "\"conditions\":[{\"condition\":\"minecraft:table_bonus\",\"chances\":[0.02,0.1]}]") + "]}");
        assertEquals(Map.of("minecraft:stick", 0.02), round(drops(t, HAND)));
        assertEquals(Map.of("minecraft:leaves", 1.0), round(drops(t, SHEARS)));
        assertEquals(Map.of("minecraft:leaves", 1.0), round(drops(t, SILK)));
    }

    @Test void canonicalBlockStatePicksMatureCropsAndSingleBlocks() {
        String wheat = table(
                pool("1", "{\"type\":\"minecraft:alternatives\",\"children\":["
                        + item("minecraft:wheat", "\"conditions\":[{\"condition\":\"minecraft:block_state_property\",\"block\":\"minecraft:wheat\",\"properties\":{\"age\":\"7\"}}]")
                        + "," + item("minecraft:wheat_seeds", "") + "]}"),
                "{\"rolls\":1,\"conditions\":[{\"condition\":\"minecraft:block_state_property\",\"block\":\"minecraft:wheat\",\"properties\":{\"age\":\"7\"}}],\"entries\":["
                        + item("minecraft:wheat_seeds", "\"functions\":[{\"function\":\"minecraft:apply_bonus\",\"enchantment\":\"minecraft:fortune\","
                        + "\"formula\":\"minecraft:binomial_with_bonus_count\",\"parameters\":{\"extra\":3,\"probability\":0.5}}]") + "]}");
        Map<String, Double> d = drops(wheat, HAND);
        assertEquals(1.0, d.get("minecraft:wheat"), 1e-9);
        assertEquals(1 + 1.5, d.get("minecraft:wheat_seeds"), 1e-9, "the level-0 binomial bonus still rolls its extra tries");

        String slab = table(pool("1", item("minecraft:oak_slab", """
                "functions":[{"function":"minecraft:set_count","count":2,
                  "conditions":[{"condition":"minecraft:block_state_property","block":"minecraft:oak_slab","properties":{"type":"double"}}]}]""")));
        assertEquals(1.0, drops(slab, HAND).get("minecraft:oak_slab"), 1e-9);

        String candle = table(pool("1", item("minecraft:candle", """
                "functions":[{"function":"minecraft:set_count","count":2,"conditions":[{"condition":"minecraft:block_state_property","properties":{"candles":"2"}}]},
                             {"function":"minecraft:set_count","count":3,"conditions":[{"condition":"minecraft:block_state_property","properties":{"candles":"3"}}]}]""")));
        assertEquals(1.0, drops(candle, HAND).get("minecraft:candle"), 1e-9);
    }

    @Test void nestedLootTablesAreFollowed() {
        Map<String, String> tables = Map.of(
                "minecraft:entities/parent", table(pool("1", "{\"type\":\"minecraft:loot_table\",\"value\":\"minecraft:entities/child\"}")),
                "minecraft:entities/child", table(pool("2", item("minecraft:bone", ""))),
                "minecraft:entities/loop", table(pool("1", "{\"type\":\"minecraft:loot_table\",\"value\":\"minecraft:entities/loop\"}")));
        assertEquals(Map.of("minecraft:bone", 2.0), round(drops(tables, "minecraft:entities/parent", HAND)));
        assertTrue(drops(tables, "minecraft:entities/loop", HAND).isEmpty());
    }

    @Test void mobConditionsFollowTheDocumentedRules() {
        String t = table(
                pool("1", item("minecraft:cooked_thing", "\"conditions\":[{\"condition\":\"minecraft:entity_properties\",\"entity\":\"this\",\"predicate\":{\"minecraft:flags\":{\"is_on_fire\":true}}}]")),
                pool("1", item("minecraft:rare", "\"conditions\":[{\"condition\":\"minecraft:random_chance_with_enchanted_bonus\",\"unenchanted_chance\":0.025,\"enchanted_chance\":0.5}]")),
                pool("1", item("minecraft:trophy", "\"conditions\":[{\"condition\":\"minecraft:killed_by_player\"},{\"condition\":\"minecraft:damage_source_properties\",\"predicate\":{}}]")),
                pool("1", "{\"type\":\"minecraft:tag\",\"name\":\"minecraft:discs\",\"expand\":true}"));
        Map<String, Double> d = round(drops(t, HAND));
        assertNull(d.get("minecraft:cooked_thing"));
        assertEquals(0.025, d.get("minecraft:rare"), 1e-9);
        assertNull(d.get("minecraft:trophy"));
        assertEquals(0.5, d.get("minecraft:disc_a"), 1e-9);
        assertEquals(0.5, d.get("minecraft:disc_b"), 1e-9);
    }

    @Test void sheepColoursExcludeEachOther() {
        String color = "{\"type\":\"minecraft:loot_table\",\"value\":\"minecraft:entities/sheep/%1$s\",\"conditions\":[{\"condition\":\"minecraft:entity_properties\","
                + "\"entity\":\"this\",\"predicate\":{\"minecraft:components\":{\"minecraft:sheep/color\":\"%1$s\"},\"minecraft:type_specific/sheep\":{\"sheared\":false}}}]}";
        Map<String, String> tables = Map.of(
                "minecraft:entities/sheep", table(pool("1", "{\"type\":\"minecraft:alternatives\",\"children\":["
                        + color.formatted("white") + "," + color.formatted("black") + "]}")),
                "minecraft:entities/sheep/white", table(pool("1", item("minecraft:white_wool", ""))),
                "minecraft:entities/sheep/black", table(pool("1", item("minecraft:black_wool", ""))));
        Map<String, Double> d = drops(tables, "minecraft:entities/sheep", MOB_ONLY);
        assertEquals(0.81836, d.get("minecraft:white_wool"), 1e-9);
        assertEquals(0.05, d.get("minecraft:black_wool"), 1e-9);
    }

    @Test void unknownTableDropsNothing() {
        assertTrue(drops(Map.of(), "minecraft:blocks/none", HAND).isEmpty());
    }

    private static Map<String, Double> round(Map<String, Double> d) {
        Map<String, Double> out = new HashMap<>();
        d.forEach((k, v) -> out.put(k, Math.round(v * 1e9) / 1e9));
        return out;
    }
}
