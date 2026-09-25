package baritone.acquire.knowledge;

import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.KillSource;
import baritone.acquire.model.MineSource;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Source;
import baritone.acquire.model.ToolReq;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The recipe and loot parsers end to end on small hand-written data files (no Minecraft classes). */
final class VanillaKnowledgeFixtureTest {
    private static final String SILK = """
            {"condition":"minecraft:match_tool","predicate":{"predicates":{"minecraft:enchantments":[{"enchantments":"minecraft:silk_touch","levels":{"min":1}}]}}}""";

    private static Map<String, String> base() {
        Map<String, String> f = new HashMap<>();
        f.put("data/minecraft/tags/item/planks.json", """
                {"values":["minecraft:oak_planks","minecraft:birch_planks"]}""");
        f.put("data/minecraft/tags/item/wooden_tool_materials.json", """
                {"values":["#minecraft:planks"]}""");
        f.put("data/minecraft/tags/item/pickaxes.json", """
                {"values":["minecraft:wooden_pickaxe","minecraft:stone_pickaxe","minecraft:iron_pickaxe"]}""");
        f.put("data/minecraft/tags/block/mineable/pickaxe.json", """
                {"values":["minecraft:stone","minecraft:iron_ore"]}""");
        f.put("data/minecraft/tags/block/mineable/shovel.json", """
                {"values":["minecraft:gravel"]}""");
        f.put("data/minecraft/tags/block/mineable/axe.json", """
                {"values":["#minecraft:logs"]}""");
        f.put("data/minecraft/tags/block/logs.json", """
                {"values":["minecraft:oak_log"]}""");
        f.put("data/minecraft/tags/block/needs_stone_tool.json", """
                {"values":["minecraft:iron_ore"]}""");
        f.put("data/minecraft/tags/block/needs_iron_tool.json", """
                {"values":["minecraft:diamond_ore"]}""");
        f.put("data/minecraft/tags/block/incorrect_for_wooden_tool.json", """
                {"values":["#minecraft:needs_iron_tool","#minecraft:needs_stone_tool"]}""");
        f.put("data/minecraft/tags/block/incorrect_for_stone_tool.json", """
                {"values":["#minecraft:needs_iron_tool"]}""");
        return f;
    }

    private static <T extends Source> List<T> of(VanillaKnowledge k, String item, Class<T> type) {
        return k.sourcesFor(item).stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test void silkTouchAlternativeBecomesASilkOnlySource() {
        Map<String, String> f = base();
        f.put("data/minecraft/loot_table/blocks/stone.json", """
                {"type":"minecraft:block","pools":[{"rolls":1.0,"entries":[{"type":"minecraft:alternatives","children":[
                  {"type":"minecraft:item","name":"minecraft:stone","conditions":[%s]},
                  {"type":"minecraft:item","name":"minecraft:cobblestone","conditions":[{"condition":"minecraft:survives_explosion"}]}]}]}]}
                """.formatted(SILK));
        VanillaKnowledge k = VanillaKnowledge.fromData(f);

        List<MineSource> cobble = of(k, "minecraft:cobblestone", MineSource.class);
        assertEquals(1, cobble.size());
        assertEquals("minecraft:stone", cobble.get(0).block());
        assertEquals(1.0, cobble.get(0).dropsPerBlock(), 1e-9);
        assertFalse(cobble.get(0).needsSilkTouch());
        assertEquals(new ToolReq("pickaxe", 1, true), cobble.get(0).tool());

        List<MineSource> stone = of(k, "minecraft:stone", MineSource.class);
        assertEquals(1, stone.size());
        assertTrue(stone.get(0).needsSilkTouch());
    }

    @Test void setCountUniformUsesItsMean() {
        Map<String, String> f = base();
        f.put("data/minecraft/loot_table/blocks/melon.json", """
                {"type":"minecraft:block","pools":[{"rolls":1.0,"entries":[{"type":"minecraft:item","name":"minecraft:melon_slice",
                  "functions":[{"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":3.0,"max":7.0}},
                               {"function":"minecraft:explosion_decay"}]}]}]}""");
        VanillaKnowledge k = VanillaKnowledge.fromData(f);
        MineSource melon = of(k, "minecraft:melon_slice", MineSource.class).get(0);
        assertEquals(5.0, melon.dropsPerBlock(), 1e-9);
        assertEquals(ToolReq.NONE, melon.tool());
    }

    @Test void tableBonusUsesTheUnenchantedChance() {
        Map<String, String> f = base();
        f.put("data/minecraft/loot_table/blocks/gravel.json", """
                {"type":"minecraft:block","pools":[{"rolls":1.0,"entries":[{"type":"minecraft:alternatives","children":[
                  {"type":"minecraft:item","name":"minecraft:gravel","conditions":[%s]},
                  {"type":"minecraft:alternatives","conditions":[{"condition":"minecraft:survives_explosion"}],"children":[
                    {"type":"minecraft:item","name":"minecraft:flint","conditions":[{"condition":"minecraft:table_bonus",
                      "enchantment":"minecraft:fortune","chances":[0.1,0.14285715,0.25,1.0]}]},
                    {"type":"minecraft:item","name":"minecraft:gravel"}]}]}]}]}""".formatted(SILK));
        VanillaKnowledge k = VanillaKnowledge.fromData(f);
        MineSource flint = of(k, "minecraft:flint", MineSource.class).get(0);
        assertEquals(0.1, flint.dropsPerBlock(), 1e-6);
        assertEquals(new ToolReq("shovel", 1, false), flint.tool());
        MineSource gravel = of(k, "minecraft:gravel", MineSource.class).get(0);
        assertEquals(0.9, gravel.dropsPerBlock(), 1e-6);
        assertFalse(gravel.needsSilkTouch());
    }

    @Test void killedByPlayerMarksTheDrop() {
        Map<String, String> f = base();
        f.put("data/minecraft/loot_table/entities/blaze.json", """
                {"type":"minecraft:entity","pools":[{"rolls":1.0,"conditions":[{"condition":"minecraft:killed_by_player"}],
                  "entries":[{"type":"minecraft:item","name":"minecraft:blaze_rod","functions":[
                    {"function":"minecraft:set_count","count":{"type":"minecraft:uniform","min":0.0,"max":1.0}},
                    {"function":"minecraft:enchanted_count_increase","enchantment":"minecraft:looting","count":{"type":"minecraft:uniform","min":0.0,"max":1.0}}]}]},
                 {"rolls":1.0,"entries":[{"type":"minecraft:item","name":"minecraft:gunpowder"}]}]}""");
        VanillaKnowledge k = VanillaKnowledge.fromData(f);
        KillSource rod = of(k, "minecraft:blaze_rod", KillSource.class).get(0);
        assertEquals("minecraft:blaze", rod.entity());
        assertEquals(0.5, rod.dropsPerKill(), 1e-9);
        assertTrue(rod.needsPlayerKill());
        assertFalse(of(k, "minecraft:gunpowder", KillSource.class).get(0).needsPlayerKill());
    }

    @Test void shapedRecipeMergesSlotsAndExpandsNestedTags() {
        Map<String, String> f = base();
        f.put("data/minecraft/recipe/wooden_pickaxe.json", """
                {"type":"minecraft:crafting_shaped","key":{"#":"minecraft:stick","X":"#minecraft:wooden_tool_materials"},
                 "pattern":["XXX"," # "," # "],"result":{"id":"minecraft:wooden_pickaxe"}}""");
        f.put("data/minecraft/recipe/stick.json", """
                {"type":"minecraft:crafting_shaped","key":{"#":"#minecraft:planks"},"pattern":["#","#"],
                 "result":{"count":4,"id":"minecraft:stick"}}""");
        VanillaKnowledge k = VanillaKnowledge.fromData(f);

        CraftSource pick = of(k, "minecraft:wooden_pickaxe", CraftSource.class).get(0);
        assertEquals("minecraft:wooden_pickaxe", pick.recipeId());
        assertEquals(List.of(
                new Ingredient(List.of("minecraft:oak_planks", "minecraft:birch_planks"), 3),
                new Ingredient(List.of("minecraft:stick"), 2)), pick.ingredients());
        assertTrue(pick.needsTable());
        assertEquals(1, pick.outputCount());

        CraftSource stick = of(k, "minecraft:stick", CraftSource.class).get(0);
        assertEquals(4, stick.outputCount());
        assertEquals(List.of(new Ingredient(List.of("minecraft:oak_planks", "minecraft:birch_planks"), 2)), stick.ingredients());
        assertFalse(stick.needsTable());
    }

    @Test void shapelessAndCookingRecipes() {
        Map<String, String> f = base();
        f.put("data/minecraft/recipe/book.json", """
                {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:paper","minecraft:paper","minecraft:paper","minecraft:leather"],
                 "result":{"id":"minecraft:book"}}""");
        f.put("data/minecraft/recipe/big.json", """
                {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:a","minecraft:a","minecraft:a","minecraft:a",["minecraft:b","minecraft:c"]],
                 "result":{"id":"minecraft:big","count":2}}""");
        f.put("data/minecraft/recipe/iron_ingot_from_smelting_raw_iron.json", """
                {"type":"minecraft:smelting","ingredient":"minecraft:raw_iron","result":{"id":"minecraft:iron_ingot"}}""");
        f.put("data/minecraft/recipe/iron_ingot_from_blasting_raw_iron.json", """
                {"type":"minecraft:blasting","ingredient":"minecraft:raw_iron","result":{"id":"minecraft:iron_ingot"}}""");
        f.put("data/minecraft/recipe/slow.json", """
                {"type":"minecraft:smoking","cookingtime":300,"ingredient":["minecraft:beef","minecraft:porkchop"],"result":{"id":"minecraft:slow"}}""");
        f.put("data/minecraft/recipe/stone_slab_from_stonecutting.json", """
                {"type":"minecraft:stonecutting","ingredient":"minecraft:stone","result":{"id":"minecraft:stone_slab","count":2}}""");
        VanillaKnowledge k = VanillaKnowledge.fromData(f);

        CraftSource book = of(k, "minecraft:book", CraftSource.class).get(0);
        assertEquals(List.of(new Ingredient(List.of("minecraft:paper"), 3), new Ingredient(List.of("minecraft:leather"), 1)), book.ingredients());
        assertFalse(book.needsTable());
        CraftSource big = of(k, "minecraft:big", CraftSource.class).get(0);
        assertTrue(big.needsTable(), "five shapeless ingredients do not fit the 2x2 grid");
        assertEquals(2, big.outputCount());

        List<SmeltSource> ingot = of(k, "minecraft:iron_ingot", SmeltSource.class);
        assertEquals(2, ingot.size());
        SmeltSource furnace = ingot.stream().filter(s -> s.station().equals("minecraft:furnace")).findFirst().orElseThrow();
        assertEquals(200, furnace.cookTicks());
        assertEquals(new Ingredient(List.of("minecraft:raw_iron"), 1), furnace.input());
        assertEquals("minecraft:iron_ingot_from_smelting_raw_iron", furnace.recipeId());
        SmeltSource blast = ingot.stream().filter(s -> s.station().equals("minecraft:blast_furnace")).findFirst().orElseThrow();
        assertEquals(100, blast.cookTicks());

        SmeltSource slow = of(k, "minecraft:slow", SmeltSource.class).get(0);
        assertEquals("minecraft:smoker", slow.station());
        assertEquals(300, slow.cookTicks());
        assertEquals(List.of("minecraft:beef", "minecraft:porkchop"), slow.input().anyOf());

        assertTrue(k.sourcesFor("minecraft:stone_slab").isEmpty(), "stonecutting is skipped");
        assertTrue(k.summary().contains("stonecutting=1"), k.summary());
    }

    @Test void patternIsShrunkBeforeMeasuringTheGrid() {
        assertEquals(List.of("#", "#"), RecipeReader.shrink(List.of("   ", " # ", " # ")));
        assertEquals(List.of("##", "##"), RecipeReader.shrink(List.of("## ", "## ")));
        Map<String, String> f = base();
        f.put("data/minecraft/recipe/padded.json", """
                {"type":"minecraft:crafting_shaped","key":{"#":"minecraft:stick"},"pattern":["   "," # "," # "],"result":{"id":"minecraft:padded"}}""");
        assertFalse(of(VanillaKnowledge.fromData(f), "minecraft:padded", CraftSource.class).get(0).needsTable());
    }

    @Test void toolTiersAndBlockRequirementsComeFromTags() {
        Map<String, String> f = base();
        f.put("data/minecraft/loot_table/blocks/iron_ore.json", """
                {"type":"minecraft:block","pools":[{"rolls":1.0,"entries":[{"type":"minecraft:item","name":"minecraft:raw_iron"}]}]}""");
        f.put("data/minecraft/loot_table/blocks/oak_log.json", """
                {"type":"minecraft:block","pools":[{"rolls":1.0,"entries":[{"type":"minecraft:item","name":"minecraft:oak_log"}]}]}""");
        VanillaKnowledge k = VanillaKnowledge.fromData(f);
        assertEquals(new ToolReq("pickaxe", 2, true), of(k, "minecraft:raw_iron", MineSource.class).get(0).tool());
        assertEquals(new ToolReq("axe", 1, false), of(k, "minecraft:oak_log", MineSource.class).get(0).tool());
        assertEquals(1, k.toolTier("minecraft:wooden_pickaxe"));
        assertEquals(2, k.toolTier("minecraft:stone_pickaxe"));
        assertEquals("pickaxe", k.toolType("minecraft:iron_pickaxe"));
        assertEquals(List.of("minecraft:stone_pickaxe", "minecraft:iron_pickaxe"), k.toolsOf("pickaxe", 2));
        assertNull(k.toolType("minecraft:stick"));
        assertEquals(0, k.toolTier("minecraft:stick"));
    }
}
