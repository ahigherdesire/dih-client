package dihclient.util.oresim;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DihOreSimOreTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void everyPickerBlockResolvesAndMapsToAFamily() {
        Set<String> seen = new LinkedHashSet<>();
        for (String id : DihOreSimOre.ORE_SIM_BLOCK_IDS) {
            assertTrue(seen.add(id), "duplicate entry " + id);
            Identifier parsed = Identifier.tryParse(id);
            assertNotNull(parsed, "unparseable id " + id);
            Block block = BuiltInRegistries.BLOCK.getOptional(parsed).orElse(null);
            assertNotNull(block, "unknown block " + id);
            assertTrue(DihOreSimOre.isOreSimBlock(block), id + " rejected by the picker filter");
            assertNotNull(DihOreSimOre.familyOf(id), id + " maps to no ore family");
        }
    }

    @Test
    void everyOreFamilyIsReachableFromThePickerList() {
        for (DihOreSimOre.Kind kind : DihOreSimOre.Kind.values()) {
            boolean reachable = false;
            for (String id : DihOreSimOre.ORE_SIM_BLOCK_IDS) {
                if (DihOreSimOre.familyOf(id) == kind) {
                    reachable = true;
                    break;
                }
            }
            assertTrue(reachable, kind.id + " has no block in the picker list");
        }
    }

    @Test
    void rawOreBlocksArePickerTargetsWithTheCorrectFamilies() {
        assertPickerFamily("minecraft:raw_iron_block", DihOreSimOre.Kind.IRON);
        assertPickerFamily("minecraft:raw_copper_block", DihOreSimOre.Kind.COPPER);
    }

    @Test
    void blocksOutsideTheListAreRejectedByThePicker() {

        for (String id : new String[]{"minecraft:chest", "minecraft:stone", "minecraft:copper_block",
            "minecraft:diamond_block", "minecraft:spawner"}) {
            Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(id)).orElse(null);
            assertNotNull(block, "unknown block " + id);
            assertTrue(!DihOreSimOre.isOreSimBlock(block), id + " should not be offered in OreSim");
        }
    }

    private static void assertPickerFamily(String id, DihOreSimOre.Kind expected) {
        Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.tryParse(id)).orElse(null);
        assertNotNull(block, "unknown block " + id);
        assertTrue(DihOreSimOre.isOreSimBlock(block), id + " rejected by the picker filter");
        assertEquals(expected, DihOreSimOre.familyOf(id), id + " maps to the wrong ore family");
    }
}
