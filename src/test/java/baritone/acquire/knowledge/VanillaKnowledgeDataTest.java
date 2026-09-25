package baritone.acquire.knowledge;

import baritone.acquire.model.MineSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The real vanilla data from the Minecraft jar on the test classpath, with data-only fallbacks (no bootstrap). */
final class VanillaKnowledgeDataTest {
    private static Map<String, String> files;
    private static VanillaKnowledge knowledge;

    @BeforeAll
    static void load() {
        files = VanillaData.fromClasspath();
        knowledge = VanillaKnowledge.fromData(files);
    }

    @Test void findsTheVanillaDataOnTheClasspath() {
        assertTrue(files.keySet().stream().filter(p -> p.startsWith("data/minecraft/recipe/")).count() > 1000);
        assertTrue(files.keySet().stream().filter(p -> p.startsWith("data/minecraft/loot_table/blocks/")).count() > 1000);
        assertTrue(files.keySet().stream().filter(p -> p.startsWith("data/minecraft/loot_table/entities/")).count() > 50);
        assertTrue(files.containsKey("data/minecraft/tags/block/mineable/pickaxe.json"));
        assertTrue(files.containsKey(VanillaData.LANG));
    }

    @Test void coreFacts() {
        VanillaExpectations.assertCoreFacts(knowledge);
    }

    @Test void dataOnlyToolRequirementFallsBackToPickaxeTag() {
        MineSource string = VanillaExpectations.of(knowledge, "minecraft:string", MineSource.class).stream()
                .filter(s -> s.block().equals("minecraft:cobweb")).findFirst().orElseThrow();
        assertFalse(string.tool().required(), "without the registry only pickaxe blocks count as needing a tool");
        assertFalse(knowledge.isItem("minecraft:op_block_warning"), "lang keys outside item.minecraft.* are not items");
    }
}
