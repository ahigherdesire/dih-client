package baritone.utils.schematic.format;

import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.fixes.BlockStateData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LegacyBlockNamesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    /**
     * The game's own flattening table knows each 1.12 name and each id; both must lead to the same block.
     */
    @Test
    void everyNameMatchesTheGamesFlatteningTable() {
        List<String> wrong = new ArrayList<>();
        for (int id = 0; id < LegacyBlockNames.size(); id++) {
            String name = LegacyBlockNames.name(id);
            if (name == null) {
                continue;
            }
            String byName = BlockStateData.upgradeBlock(name);
            String byId = BlockStateData.upgradeBlock(id << 4);
            if (!byName.equals(byId)) {
                wrong.add(id + " " + name + ": by name " + byName + ", by id " + byId);
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    @Test
    void namesAndIdsRoundTrip() {
        assertEquals(35, LegacyBlockNames.id("minecraft:wool"));
        assertEquals(35, LegacyBlockNames.id("wool"));
        assertEquals(1, LegacyBlockNames.id("STONE"));
        assertEquals(-1, LegacyBlockNames.id("minecraft:short_grass"));
        assertEquals("minecraft:grass_path", LegacyBlockNames.name(208));
        assertNull(LegacyBlockNames.name(253));
        assertNull(LegacyBlockNames.name(256));
    }
}
