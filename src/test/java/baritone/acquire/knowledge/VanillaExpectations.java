package baritone.acquire.knowledge;

import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.KillSource;
import baritone.acquire.model.MineSource;
import baritone.acquire.model.SmeltSource;
import baritone.acquire.model.Source;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Facts about real 26.2 vanilla data that must hold with or without the game's registries. */
final class VanillaExpectations {
    private VanillaExpectations() {
    }

    static <T extends Source> List<T> of(Knowledge k, String item, Class<T> type) {
        return k.sourcesFor(item).stream().filter(type::isInstance).map(type::cast).toList();
    }

    private static MineSource mined(Knowledge k, String item, String block) {
        return of(k, item, MineSource.class).stream().filter(s -> s.block().equals(block) && !s.needsSilkTouch())
                .findFirst().orElseThrow(() -> new AssertionError(block + " does not drop " + item + ": " + k.sourcesFor(item)));
    }

    static void assertCoreFacts(Knowledge k) {
        MineSource cobble = mined(k, "minecraft:cobblestone", "minecraft:stone");
        assertEquals("pickaxe", cobble.tool().type());
        assertEquals(1, cobble.tool().minTier());
        assertTrue(cobble.tool().required());
        assertEquals(1.0, cobble.dropsPerBlock(), 1e-9);

        for (String ore : List.of("minecraft:iron_ore", "minecraft:deepslate_iron_ore")) {
            MineSource raw = mined(k, "minecraft:raw_iron", ore);
            assertEquals("pickaxe", raw.tool().type());
            assertEquals(2, raw.tool().minTier(), ore);
            assertTrue(raw.tool().required());
        }
        MineSource diamond = mined(k, "minecraft:diamond", "minecraft:diamond_ore");
        assertEquals(3, diamond.tool().minTier());
        MineSource obsidian = mined(k, "minecraft:obsidian", "minecraft:obsidian");
        assertEquals(4, obsidian.tool().minTier());

        SmeltSource ingot = of(k, "minecraft:iron_ingot", SmeltSource.class).stream()
                .filter(s -> s.input().anyOf().equals(List.of("minecraft:raw_iron")) && s.station().equals("minecraft:furnace"))
                .findFirst().orElseThrow(() -> new AssertionError("no furnace recipe for raw iron"));
        assertEquals(200, ingot.cookTicks());
        assertEquals(1, ingot.outputCount());

        CraftSource stick = of(k, "minecraft:stick", CraftSource.class).stream()
                .filter(c -> c.recipeId().equals("minecraft:stick")).findFirst().orElseThrow();
        assertEquals(4, stick.outputCount());
        assertFalse(stick.needsTable());
        assertEquals(1, stick.ingredients().size());
        Ingredient planks = stick.ingredients().get(0);
        assertEquals(2, planks.count());
        assertTrue(planks.accepts("minecraft:oak_planks") && planks.accepts("minecraft:birch_planks"), planks.toString());

        assertFalse(of(k, "minecraft:crafting_table", CraftSource.class).get(0).needsTable());
        CraftSource pick = of(k, "minecraft:wooden_pickaxe", CraftSource.class).get(0);
        assertTrue(pick.needsTable());
        assertTrue(pick.ingredients().contains(new Ingredient(List.of("minecraft:stick"), 2)), pick.toString());
        assertTrue(pick.ingredients().stream().anyMatch(i -> i.count() == 3 && i.accepts("minecraft:oak_planks")), pick.toString());

        KillSource spider = of(k, "minecraft:string", KillSource.class).stream()
                .filter(s -> s.entity().equals("minecraft:spider")).findFirst().orElseThrow();
        assertTrue(spider.dropsPerKill() > 0.5 && spider.dropsPerKill() < 2, spider.toString());
        assertFalse(spider.needsPlayerKill());
        KillSource blazeRod = of(k, "minecraft:blaze_rod", KillSource.class).get(0);
        assertTrue(blazeRod.needsPlayerKill());
        assertEquals(0.5, blazeRod.dropsPerKill(), 1e-9);
        assertEquals(1.0, of(k, "minecraft:leather", KillSource.class).stream()
                .filter(s -> s.entity().equals("minecraft:cow")).findFirst().orElseThrow().dropsPerKill(), 1e-9);
        assertTrue(of(k, "minecraft:beef", KillSource.class).stream().anyMatch(s -> s.entity().equals("minecraft:cow")),
                "furnace_smelt is ignored: a cow drops raw beef");

        MineSource flint = mined(k, "minecraft:flint", "minecraft:gravel");
        assertEquals(0.1, flint.dropsPerBlock(), 1e-6);

        MineSource log = mined(k, "minecraft:oak_log", "minecraft:oak_log");
        assertFalse(log.tool().required());
        assertEquals(1.0, log.dropsPerBlock(), 1e-9);
        assertTrue(of(k, "minecraft:stone", MineSource.class).stream().anyMatch(MineSource::needsSilkTouch));

        assertEquals(Optional.of("minecraft:iron_pickaxe"), k.resolveItem("iron pick"));
        assertEquals(Optional.of("minecraft:torch"), k.resolveItem("torches"));
        assertEquals(Optional.of("minecraft:diamond"), k.resolveItem("diamonds"));
        assertEquals(Optional.of("minecraft:cobblestone"), k.resolveItem("Cobblestone"));
        assertEquals(Optional.of("minecraft:iron_block"), k.resolveItem("Block of Iron"));
        assertFalse(k.suggest("iron pikax", 5).isEmpty());
        assertEquals("minecraft:iron_pickaxe", k.suggest("iron pikax", 5).get(0));

        List<String> pickaxes = k.toolsOf("pickaxe", 2);
        assertTrue(List.of("minecraft:stone_pickaxe", "minecraft:copper_pickaxe").contains(pickaxes.get(0)), pickaxes.toString());
        assertFalse(pickaxes.contains("minecraft:wooden_pickaxe") || pickaxes.contains("minecraft:golden_pickaxe"));
        assertEquals("minecraft:wooden_pickaxe", k.toolsOf("pickaxe", 1).get(0));
        assertEquals(2, k.toolTier("minecraft:copper_pickaxe"), "copper sits with stone in the incorrect_for_* tags");
        assertEquals(1, k.toolTier("minecraft:golden_pickaxe"));
        assertEquals(3, k.toolTier("minecraft:iron_pickaxe"));
        assertEquals(4, k.toolTier("minecraft:diamond_pickaxe"));
        assertEquals(5, k.toolTier("minecraft:netherite_pickaxe"));
        assertEquals("axe", k.toolType("minecraft:stone_axe"));
        assertEquals("shears", k.toolType("minecraft:shears"));
        assertEquals(List.of("minecraft:shears"), k.toolsOf("shears", 1));

        assertEquals(1600, k.fuels().get("minecraft:coal"));
        assertEquals(1600, k.fuels().get("minecraft:charcoal"));
        assertEquals(16000, k.fuels().get("minecraft:coal_block"));
        assertEquals(300, k.fuels().get("minecraft:oak_log"));
        assertEquals(300, k.fuels().get("minecraft:spruce_planks"));
        assertEquals(150, k.fuels().get("minecraft:oak_slab"));
        assertEquals(100, k.fuels().get("minecraft:stick"));
        assertEquals(2400, k.fuels().get("minecraft:blaze_rod"));
        assertEquals(20000, k.fuels().get("minecraft:lava_bucket"));
        assertEquals(4001, k.fuels().get("minecraft:dried_kelp_block"));
        assertEquals(50, k.fuels().get("minecraft:bamboo"));
        assertEquals(200, k.fuels().get("minecraft:wooden_pickaxe"));
        assertNull(k.fuels().get("minecraft:crimson_planks"), "nether wood does not burn");

        assertTrue(k.isItem("minecraft:stone"));
        assertTrue(k.isItem("stick"));
        assertFalse(k.isItem("minecraft:not_an_item"));
        assertTrue(k.sourcesFor("minecraft:not_an_item").isEmpty());
    }
}
