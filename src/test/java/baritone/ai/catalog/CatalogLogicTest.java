package baritone.ai.catalog;

import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.Plan;
import baritone.acquire.model.Step;
import baritone.acquire.model.ToolReq;
import baritone.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure parts of tools that need a world to run: gear_up, craft, inventory, set_guardian, whisper. */
final class CatalogLogicTest {

    @Test
    void gearUpGetsWhatIsMissingPickaxeFirst() {
        assertEquals(List.of("minecraft:iron_pickaxe", "minecraft:iron_sword", "minecraft:iron_axe", "minecraft:iron_helmet",
                        "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots"),
                JobTools.missingGear("iron", true, id -> false));
        Set<String> held = Set.of("minecraft:diamond_pickaxe", "minecraft:iron_sword", "minecraft:stone_axe",
                "minecraft:iron_helmet", "minecraft:netherite_boots");
        assertEquals(List.of("minecraft:iron_axe", "minecraft:iron_chestplate", "minecraft:iron_leggings"),
                JobTools.missingGear("iron", true, held::contains), "better tiers count, worse ones don't");
        assertEquals(List.of(), JobTools.missingGear("stone", true, held::contains), "the iron sword covers stone");
        assertEquals(List.of("minecraft:stone_sword", "minecraft:stone_axe"),
                JobTools.missingGear("stone", true, "minecraft:stone_pickaxe"::equals), "no stone armour exists");
        assertEquals(List.of("minecraft:iron_axe"), JobTools.missingGear("iron", false, held::contains));
        assertThrows(IllegalArgumentException.class, () -> JobTools.missingGear("gold", true, id -> false));
    }

    @Test
    void gotoTakesCoordinatesALevelOrABlock() {
        assertEquals("goto 1 2 3", JobTools.gotoCommand(" 1 2 3 "));
        assertEquals("goto 5 -7", JobTools.gotoCommand("5 -7"));
        assertEquals("goto crafting_table", JobTools.gotoCommand("minecraft:Crafting_Table"));
        assertThrows(IllegalArgumentException.class, () -> JobTools.gotoCommand("1 2 3 4"));
        assertThrows(IllegalArgumentException.class, () -> JobTools.gotoCommand(""));
        assertThrows(IllegalArgumentException.class, () -> JobTools.gotoCommand("1 north"));
    }

    @Test
    void inventoryCountsItemsAndShowsDurability() {
        ToolResult result = InventoryTools.summarize(List.of(
                        new InventoryTools.Stack("cobblestone", 64, 0, 0),
                        new InventoryTools.Stack("iron_pickaxe", 1, 50, 250),
                        new InventoryTools.Stack("cobblestone", 12, 0, 0)),
                List.of(new InventoryTools.Stack("iron_helmet", 1, 0, 165)), 30);
        assertEquals("Carrying: 76 cobblestone, iron_pickaxe (200/250). Wearing: iron_helmet (165/165). Free slots: 30.",
                result.text());
        assertEquals(Map.of("cobblestone", 76), result.facts().get("items"));
        assertEquals(30, result.facts().get("free_slots"));
        assertEquals("Carrying: nothing. Wearing: nothing. Free slots: 36.",
                InventoryTools.summarize(List.of(), List.of(), 36).text());
    }

    @Test
    void guardianSettingsReadBack() {
        ToolResult result = CombatTools.describe(true, 12, false);
        assertEquals("Guardian on, fleeing at 12 health or below.", result.text());
        assertEquals(12, result.facts().get("flee_health"));
        assertTrue(CombatTools.describe(false, 10, true).text().endsWith("handing back when a stranger comes near."));
    }

    @Test
    void craftingAloneNeverGathers() {
        CraftSource planks = new CraftSource("oak_planks", "minecraft:oak_planks", 4,
                List.of(new Ingredient(List.of("minecraft:oak_log"), 1)), false);
        Plan craftOnly = new Plan("minecraft:oak_planks", 8, List.of(
                new Step.Craft(planks, 2, List.of("minecraft:oak_log"), 8)), List.of(), 2);
        assertNull(CraftingTools.blocked(craftOnly), "only crafting: go ahead");

        Plan mines = new Plan("minecraft:oak_planks", 8, List.of(
                new Step.Mine(List.of("minecraft:oak_log"), "minecraft:oak_log", 2, ToolReq.NONE, 2),
                new Step.Craft(planks, 2, List.of("minecraft:oak_log"), 8)), List.of(), 5);
        ToolResult needs = CraftingTools.blocked(mines);
        assertEquals(ToolResult.Status.NEEDS, needs.status());
        assertEquals("Needs 2 oak_log.", needs.text());
        assertEquals("mine", needs.facts().get("gather"));

        Plan missing = new Plan("minecraft:elytra", 1, List.of(), List.of("no known source for minecraft:elytra"), 0);
        assertEquals("Can't make elytra: no known source for minecraft:elytra.", CraftingTools.blocked(missing).text());
        Plan done = new Plan("minecraft:oak_planks", 8, List.of(), List.of(), 0);
        assertEquals("Already have 8 oak_planks.", CraftingTools.blocked(done).text());
    }

    @Test
    void whispersAreOneLineAndNeverCommands() {
        assertEquals("hi there", ChatTools.message(" hi\nthere "));
        assertEquals("x".repeat(ChatTools.MAX_MESSAGE), ChatTools.message("x".repeat(500)));
        assertEquals("red", ChatTools.message("§cred"));
        assertThrows(IllegalArgumentException.class, () -> ChatTools.message("/op me"));
        assertThrows(IllegalArgumentException.class, () -> ChatTools.message("   "));
    }
}
