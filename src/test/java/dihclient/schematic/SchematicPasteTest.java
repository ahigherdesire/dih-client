package dihclient.schematic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SchematicPasteTest {

    private static SchematicPaste.Cell cell(int x, int y, int z, String state, int pass) {
        return new SchematicPaste.Cell(x, y, z, state, pass);
    }

    @Test
    void aRunOfOneStateAlongXIsOneFill() {
        List<String> commands = SchematicPaste.commands(List.of(
            cell(2, 64, 0, "minecraft:stone", SchematicPaste.PASS_SOLID),
            cell(0, 64, 0, "minecraft:stone", SchematicPaste.PASS_SOLID),
            cell(1, 64, 0, "minecraft:stone", SchematicPaste.PASS_SOLID)));
        assertEquals(List.of("fill 0 64 0 2 64 0 minecraft:stone"), commands);
    }

    @Test
    void aGapOrAnotherStateBreaksTheRun() {
        List<String> commands = SchematicPaste.commands(List.of(
            cell(0, 64, 0, "minecraft:stone", SchematicPaste.PASS_SOLID),
            cell(1, 64, 0, "minecraft:dirt", SchematicPaste.PASS_SOLID),
            cell(3, 64, 0, "minecraft:dirt", SchematicPaste.PASS_SOLID)));
        assertEquals(List.of(
            "setblock 0 64 0 minecraft:stone",
            "setblock 1 64 0 minecraft:dirt",
            "setblock 3 64 0 minecraft:dirt"), commands);
    }

    @Test
    void airGoesFirstThenSolidsThenWhatHangsOnThemEachBottomUp() {
        List<String> commands = SchematicPaste.commands(List.of(
            cell(0, 65, 1, "minecraft:wall_torch[facing=south]", SchematicPaste.PASS_ATTACHED),
            cell(0, 65, 0, "minecraft:stone", SchematicPaste.PASS_SOLID),
            cell(0, 64, 0, "minecraft:stone", SchematicPaste.PASS_SOLID),
            cell(5, 70, 5, "minecraft:air", SchematicPaste.PASS_AIR)));
        assertEquals(List.of(
            "setblock 5 70 5 minecraft:air",
            "setblock 0 64 0 minecraft:stone",
            "setblock 0 65 0 minecraft:stone",
            "setblock 0 65 1 minecraft:wall_torch[facing=south]"), commands);
    }

    @Test
    void modesParseFromTheirWords() {
        assertEquals(SchematicBuildMode.LEGIT, SchematicBuildMode.parse("Legit"));
        assertEquals(SchematicBuildMode.LEGIT, SchematicBuildMode.parse("grim"));
        assertEquals(SchematicBuildMode.FAST, SchematicBuildMode.parse("blatant"));
        assertEquals(SchematicBuildMode.CREATIVE, SchematicBuildMode.parse("paste"));
        assertEquals(SchematicBuildMode.CREATIVE, SchematicBuildMode.parse(SchematicBuildMode.CREATIVE.label()));
        assertNull(SchematicBuildMode.parse("sideways"));
    }
}
