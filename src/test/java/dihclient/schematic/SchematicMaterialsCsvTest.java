package dihclient.schematic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SchematicMaterialsCsvTest {

    @TempDir
    Path dir;

    private static final Map<String, String> NAMES = Map.of(
        "minecraft:oak_planks", "Oak Planks",
        "minecraft:glass", "Glass",
        "minecraft:water", "Water");

    private static String name(String id) {
        return NAMES.getOrDefault(id, id);
    }

    @Test
    void rowsFollowTheReportThenBlocksNoItemPlaces() {
        Map<String, Integer> unplaceable = new LinkedHashMap<>();
        unplaceable.put("minecraft:water", 3);
        SchematicMaterials.Report report = new SchematicMaterials.Report(List.of(
            new SchematicMaterials.Line("minecraft:oak_planks", 64, 10),
            new SchematicMaterials.Line("minecraft:glass", 22, 30)), unplaceable, 89);

        String csv = SchematicMaterialsCsv.csv(report, SchematicMaterialsCsvTest::name);

        assertEquals(String.join("\r\n",
            "item,name,needed,have,missing,note",
            "minecraft:oak_planks,Oak Planks,64,10,54,",
            "minecraft:glass,Glass,22,30,0,",
            "minecraft:water,Water,3,0,3,no item places it",
            ""), csv);
    }

    @Test
    void fieldsAreQuotedAndFormulaSafe() {
        assertEquals("Oak Planks", SchematicMaterialsCsv.field("Oak Planks"));
        assertEquals("\"Planks, Oak\"", SchematicMaterialsCsv.field("Planks, Oak"));
        assertEquals("\"Say \"\"hi\"\"\"", SchematicMaterialsCsv.field("Say \"hi\""));
        assertEquals("'=SUM(A1)", SchematicMaterialsCsv.field("=SUM(A1)"));
        assertEquals("\"'=A1,B1\"", SchematicMaterialsCsv.field("=A1,B1"));
        assertEquals("", SchematicMaterialsCsv.field(null));
    }

    @Test
    void fileNameDropsFoldersExtensionAndBadCharacters() {
        assertEquals("house-materials.csv", SchematicMaterialsCsv.fileName("house.litematic"));
        assertEquals("tower-materials.csv", SchematicMaterialsCsv.fileName("builds/tower.schem"));
        assertEquals("base v2-materials.csv", SchematicMaterialsCsv.fileName("old\\base v2.schematic"));
        assertEquals("what_-materials.csv", SchematicMaterialsCsv.fileName("what?.nbt"));
        assertEquals("schematic-materials.csv", SchematicMaterialsCsv.fileName(""));
    }

    @Test
    void writeCreatesTheFolderAndReplacesTheLastExport() throws Exception {
        Path folder = dir.resolve("schematics").resolve("materials");

        Path first = SchematicMaterialsCsv.write(folder, "house.litematic", "a\r\n");
        Path second = SchematicMaterialsCsv.write(folder, "house.litematic", "b\r\n");

        assertEquals(folder.resolve("house-materials.csv"), first);
        assertEquals(first, second);
        assertEquals("b\r\n", Files.readString(second, StandardCharsets.UTF_8));
    }
}
