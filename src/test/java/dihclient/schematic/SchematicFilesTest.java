package dihclient.schematic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SchematicFilesTest {

    @TempDir
    Path dir;

    @Test
    void onlySchematicFilesCount() throws Exception {
        Path house = Files.writeString(dir.resolve("House.LITEMATIC"), "x");
        Path pack = Files.writeString(dir.resolve("pack.zip"), "x");
        Path folder = Files.createDirectory(dir.resolve("tower.schem"));
        assertTrue(SchematicFiles.isSchematic(house));
        assertFalse(SchematicFiles.isSchematic(pack));
        assertFalse(SchematicFiles.isSchematic(folder));
        assertFalse(SchematicFiles.isSchematic(dir.resolve("missing.nbt")));
    }

    @Test
    void importCopiesKeepsSameBytesAndNeverOverwrites() throws Exception {
        Path downloads = Files.createDirectory(dir.resolve("Downloads"));
        Path schematics = dir.resolve("schematics");
        Path house = Files.writeString(downloads.resolve("house.litematic"), "first");

        assertEquals("house.litematic", SchematicFiles.importInto(schematics, house));
        assertArrayEquals(Files.readAllBytes(house), Files.readAllBytes(schematics.resolve("house.litematic")));
        // the same file again is the same schematic
        assertEquals("house.litematic", SchematicFiles.importInto(schematics, house));

        Files.writeString(house, "second");
        assertEquals("house (2).litematic", SchematicFiles.importInto(schematics, house));
        assertEquals("first", Files.readString(schematics.resolve("house.litematic")));
        assertEquals("house (2).litematic", SchematicFiles.importInto(schematics, house));
    }

    @Test
    void aFileAlreadyInTheFolderStaysWhereItIs() throws Exception {
        Path schematics = Files.createDirectories(dir.resolve("schematics/builds"));
        Path tower = Files.writeString(schematics.resolve("tower.schem"), "x");
        assertEquals("builds/tower.schem", SchematicFiles.importInto(dir.resolve("schematics"), tower));
        try (var files = Files.list(schematics)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void newestComeFirst() throws Exception {
        Path old = Files.writeString(dir.resolve("old.schematic"), "x");
        Path mid = Files.writeString(dir.resolve("mid.nbt"), "x");
        Path recent = Files.writeString(dir.resolve("new.litematic"), "x");
        Files.writeString(dir.resolve("notes.txt"), "x");
        Files.setLastModifiedTime(old, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(mid, FileTime.fromMillis(2_000));
        Files.setLastModifiedTime(recent, FileTime.fromMillis(3_000));

        assertEquals(List.of(recent, mid, old), SchematicFiles.newest(dir, 10));
        assertEquals(List.of(recent), SchematicFiles.newest(dir, 1));
        assertEquals(List.of(), SchematicFiles.newest(dir.resolve("missing"), 10));
    }
}
