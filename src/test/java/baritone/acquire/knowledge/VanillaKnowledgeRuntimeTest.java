package baritone.acquire.knowledge;

import baritone.acquire.model.MineSource;
import baritone.acquire.model.ToolReq;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/** {@link VanillaKnowledge#get()} in a bootstrapped game: data from the jar, tool rules from the registries. */
final class VanillaKnowledgeRuntimeTest {
    private static Knowledge knowledge;
    private static long loadMillis;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        long start = System.nanoTime();
        knowledge = VanillaKnowledge.get();
        loadMillis = (System.nanoTime() - start) / 1_000_000;
    }

    @Test void coreFacts() {
        VanillaExpectations.assertCoreFacts(knowledge);
    }

    @Test void loadsOnceAndReasonablyFast() {
        assertSame(knowledge, VanillaKnowledge.get());
        System.out.println("VanillaKnowledge.get() took " + loadMillis + " ms: " + ((VanillaKnowledge) knowledge).summary());
        assertTrue(loadMillis < 15_000, "took " + loadMillis + " ms");
    }

    @Test void registryDecidesWhichBlocksNeedTheRightTool() {
        MineSource web = VanillaExpectations.of(knowledge, "minecraft:string", MineSource.class).stream()
                .filter(s -> s.block().equals("minecraft:cobweb")).findFirst().orElseThrow();
        assertEquals(new ToolReq("sword", 1, true), web.tool());
        assertTrue(knowledge.isItem("minecraft:bedrock"), "the item list comes from the registry");

        TreeSet<String> requiredWithoutType = new TreeSet<>();
        for (String item : ((VanillaKnowledge) knowledge).items()) {
            for (MineSource s : VanillaExpectations.of(knowledge, item, MineSource.class)) {
                if (s.tool().required() && s.tool().type() == null) requiredWithoutType.add(s.block());
            }
        }
        System.out.println("blocks that need an unknown tool: " + requiredWithoutType);
        assertTrue(requiredWithoutType.size() < 10, requiredWithoutType.toString());
    }

    @Test void blocksSharingALootTableAreAllSources() {
        assertTrue(VanillaExpectations.of(knowledge, "minecraft:torch", MineSource.class).stream()
                .anyMatch(s -> s.block().equals("minecraft:wall_torch")), knowledge.sourcesFor("minecraft:torch").toString());
    }

    @Test void vanillaPackFallbackReadsTheSameFiles() {
        Map<String, String> pack = VanillaPackData.read();
        assertTrue(VanillaData.looksComplete(pack), "pack fallback found " + pack.size() + " files");
        assertTrue(pack.containsKey("data/minecraft/recipe/stick.json"));
        assertTrue(pack.containsKey("data/minecraft/loot_table/blocks/stone.json"));
    }
}
