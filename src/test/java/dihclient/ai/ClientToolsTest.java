package dihclient.ai;

import baritone.ai.tool.ToolResult;
import dihclient.trade.TradeOffer;
import dihclient.trade.TradeRule;
import dihclient.trade.VillagerOffers;
import dihclient.util.DihMacro;
import dihclient.util.macro.AiToolAction;
import dihclient.util.macro.MacroActionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** macro_write's step building and read_offers' listing. */
final class ClientToolsTest {

    @Test
    void macroStepsBuildFromJson() {
        MacroSteps.Built built = MacroSteps.build("wood", """
                [{"type":"ai_tool","fields":{"tool":"find","args":"oak_log","allowDangerous":true}},
                 {"type":"AI TOOL","fields":{"tool":"goto","args":"oak_log"}}]""");
        assertNull(built.error(), built.error());
        DihMacro macro = built.macro();
        assertEquals("wood", macro.name);
        assertEquals(2, macro.actions.size());
        AiToolAction first = assertInstanceOf(AiToolAction.class, macro.actions.get(0));
        assertEquals("find", first.tool);
        assertEquals("oak_log", first.args);
        assertTrue(first.allowDangerous);
        assertEquals(MacroActionType.AI_TOOL, macro.actions.get(1).getType());
    }

    @Test
    void macroStepMistakesSayWhichStepAndWhy() {
        assertEquals("steps isn't valid JSON: ", MacroSteps.build("m", "[{").error().substring(0, 24));
        assertEquals("steps must be a JSON array of {\"type\":..., \"fields\":{...}}.", MacroSteps.build("m", "{}").error());
        assertEquals("A macro needs at least one step.", MacroSteps.build("m", "[]").error());
        assertEquals("Step 1: no step type FLY.", MacroSteps.build("m", "[{\"type\":\"fly\"}]").error());
        assertEquals("Step 2: missing \"type\".", MacroSteps.build("m", "[{\"type\":\"ai_tool\"},{}]").error());
        assertEquals("Step 1: AI_TOOL has no field \"speed\".",
                MacroSteps.build("m", "[{\"type\":\"ai_tool\",\"fields\":{\"speed\":3}}]").error());
        assertEquals("Step 1: allowDangerous must be true or false.",
                MacroSteps.build("m", "[{\"type\":\"ai_tool\",\"fields\":{\"allowDangerous\":\"yes\"}}]").error());
    }

    private static TradeOffer book(int index, String enchantment, int level, int price) {
        return new TradeOffer(index, "minecraft:enchanted_book", 1, Map.of("minecraft:" + enchantment, level),
                TradeOffer.EMERALD, price, "minecraft:book", 1, 0, 12);
    }

    @Test
    void readOffersListsMatchingOffersNearestFirst() {
        VillagerOffers far = new VillagerOffers("a", "minecraft:librarian", 1, 0, 100, 64, 0,
                List.of(book(0, "mending", 1, 20)), 0);
        VillagerOffers near = new VillagerOffers("b", "minecraft:librarian", 2, 30, 5, 64, 0,
                List.of(book(0, "mending", 1, 12), book(1, "unbreaking", 3, 9)), 0);
        VillagerOffers none = new VillagerOffers("c", "minecraft:librarian", 1, 0, 1, 64, 0,
                List.of(book(0, "protection", 2, 10)), 0);
        ToolResult result = ClientTools.describeOffers(List.of(far, near, none), TradeRule.parse("mending"), 0, 64, 0);
        String[] lines = result.text().split("\n");
        assertEquals(2, lines.length, result.text());
        assertEquals("librarian at 5 64 0: " + book(0, "mending", 1, 12).describe(), lines[0]);
        assertEquals("librarian at 100 64 0 (rerollable): " + book(0, "mending", 1, 20).describe(), lines[1]);
        assertEquals(2, result.facts().get("villagers"));

        ToolResult nothing = ClientTools.describeOffers(List.of(none), TradeRule.parse("mending"), 0, 64, 0);
        assertTrue(nothing.text().startsWith("No remembered offer matches."), nothing.text());
    }
}
