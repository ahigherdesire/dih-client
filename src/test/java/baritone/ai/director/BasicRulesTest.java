package baritone.ai.director;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Basic mode's rule table: the objectives it must cover, and nothing it would guess at. */
final class BasicRulesTest {

    private static final Map<String, String> ITEMS = Map.of(
            "oak log", "minecraft:oak_log",
            "diamond pickaxe", "minecraft:diamond_pickaxe",
            "torch", "minecraft:torch",
            "iron ingot", "minecraft:iron_ingot",
            "bread", "minecraft:bread");

    private static final BasicRules RULES = new BasicRules(text -> Optional.ofNullable(ITEMS.get(text)));

    /** "tool key=value, ..." per step, for short assertions. */
    private static List<String> steps(String objective) {
        Plan plan = RULES.planFor(objective);
        return plan == null ? null : plan.steps().stream().map(step -> {
            StringBuilder sb = new StringBuilder(step.tool());
            step.args().entrySet().forEach(e -> sb.append(' ').append(e.getKey()).append('=').append(e.getValue().getAsString()));
            return sb.toString();
        }).toList();
    }

    @Test
    void beatingTheGameIsTheBeatCampaign() {
        for (String objective : List.of("beat the game", "Please beat minecraft.", "beat the game for me", "kill the ender dragon",
                "defeat the dragon", "beat the ender dragon")) {
            assertEquals(List.of("beat_stage action=start"), steps(objective), objective);
        }
    }

    @Test
    void getItemWithOrWithoutACount() {
        assertEquals(List.of("acquire item=minecraft:oak_log count=10"), steps("get 10 oak logs"));
        assertEquals(List.of("acquire item=minecraft:oak_log count=10"), steps("Please gather me 10 oak logs."));
        assertEquals(List.of("acquire item=minecraft:torch count=32"), steps("make 32 torches"));
        assertEquals(List.of("acquire item=minecraft:iron_ingot count=5"), steps("get iron ingot x5"));
        assertEquals(List.of("acquire item=minecraft:diamond_pickaxe count=1"), steps("diamond pickaxe"));
        assertEquals(List.of("acquire item=minecraft:diamond_pickaxe count=1"), steps("get a diamond pickaxe"));
        assertEquals(List.of("acquire item=minecraft:bread count=1"), steps("craft some bread"));
    }

    @Test
    void armourSetsGoPieceByPiece() {
        List<String> iron = List.of("acquire item=minecraft:iron_helmet count=1", "acquire item=minecraft:iron_chestplate count=1",
                "acquire item=minecraft:iron_leggings count=1", "acquire item=minecraft:iron_boots count=1");
        assertEquals(iron, steps("iron armor"));
        assertEquals(iron, steps("get full iron armour"));
        assertEquals(List.of("acquire item=minecraft:diamond_helmet count=1", "acquire item=minecraft:diamond_chestplate count=1",
                "acquire item=minecraft:diamond_leggings count=1", "acquire item=minecraft:diamond_boots count=1"),
                steps("diamond armor"));
    }

    @Test
    void placesPlayersAndFarms() {
        assertEquals(List.of("goto_structure structure=village"), steps("go to a village"));
        assertEquals(List.of("goto_structure structure=village"), steps("find the nearest village"));
        assertEquals(List.of("goto_structure structure=stronghold"), steps("take me to a stronghold"));
        assertEquals(List.of("follow_player player=Steve_2"), steps("follow Steve_2"));
        assertEquals(List.of("farm"), steps("farm wheat"));
        assertEquals(List.of("farm"), steps("farm"));
    }

    @Test
    void anythingElseIsLeftToTheSmartAi() {
        assertNull(steps("write me a poem"));
        assertNull(steps("get 10 unobtainium"));
        assertNull(steps("go to the moon"));
        assertNull(steps("beat the game in five minutes"), "only the plain phrasings map to #beat");
        assertNull(steps(""));
    }
}
