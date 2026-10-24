package baritone.utils.schematic.format;

import baritone.utils.schematic.LoadedSchematic;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every format DIH reads, from fixture files written here with known blocks at known positions.
 */
final class SchematicLoaderTest {

    private static int current;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        current = SharedConstants.getCurrentVersion().dataVersion().version();
    }

    // ---- MCEdit / Schematica .schematic ----

    @Test
    void mcEditMapsLegacyIdsAndDataToModernStates() throws IOException {
        // 3 wide (x), 2 high (y), 2 long (z)
        int[][] cells = new int[12][];
        // y=0
        cells[index(0, 0, 0, 3, 2)] = new int[]{1, 0};    // stone
        cells[index(1, 0, 0, 3, 2)] = new int[]{35, 14};  // red wool
        cells[index(2, 0, 0, 3, 2)] = new int[]{17, 5};   // spruce log along x
        cells[index(0, 0, 1, 3, 2)] = new int[]{53, 2};   // oak stairs facing south
        cells[index(1, 0, 1, 3, 2)] = new int[]{54, 3};   // chest facing south
        cells[index(2, 0, 1, 3, 2)] = new int[]{208, 0};  // grass path, renamed since
        // y=1
        cells[index(0, 1, 0, 3, 2)] = new int[]{31, 1};   // tall grass, now short_grass
        cells[index(1, 1, 0, 3, 2)] = new int[]{44, 8};   // top stone slab
        cells[index(2, 1, 0, 3, 2)] = new int[]{0, 0};
        cells[index(0, 1, 1, 3, 2)] = new int[]{95, 11};  // blue stained glass
        cells[index(1, 1, 1, 3, 2)] = new int[]{64, 8};   // upper half of an oak door
        cells[index(2, 1, 1, 3, 2)] = new int[]{2, 0};    // grass block

        CompoundTag nbt = mcEdit(3, 2, 2, cells);
        ListTag tiles = new ListTag();
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "Chest");
        chest.putInt("x", 1);
        chest.putInt("y", 0);
        chest.putInt("z", 1);
        chest.put("Items", new ListTag());
        tiles.add(chest);
        nbt.put("TileEntities", tiles);

        LoadedSchematic s = load(nbt, "house.schematic");

        assertSize(s, 3, 2, 2);
        assertState("minecraft:stone", s, 0, 0, 0);
        assertState("minecraft:red_wool", s, 1, 0, 0);
        assertState("minecraft:spruce_log[axis=x]", s, 2, 0, 0);
        assertState("minecraft:oak_stairs[facing=south,half=bottom]", s, 0, 0, 1);
        assertState("minecraft:chest[facing=south]", s, 1, 0, 1);
        assertState("minecraft:dirt_path", s, 2, 0, 1);
        assertState("minecraft:short_grass", s, 0, 1, 0);
        assertState("minecraft:smooth_stone_slab[type=top]", s, 1, 1, 0);
        assertState("minecraft:air", s, 2, 1, 0);
        assertState("minecraft:blue_stained_glass", s, 0, 1, 1);
        assertEquals("minecraft:oak_door", id(s.getDirect(1, 1, 1)));
        assertEquals(DoubleBlockHalf.UPPER, s.getDirect(1, 1, 1).getValue(BlockStateProperties.DOUBLE_BLOCK_HALF));
        assertState("minecraft:grass_block", s, 2, 1, 1);

        assertEquals(1, s.blockEntities().size());
        CompoundTag tile = s.blockEntity(1, 0, 1);
        assertNotNull(tile);
        assertEquals("minecraft:chest", tile.getStringOr("id", ""));
        assertFalse(tile.contains("x"));
        assertEquals("house", s.name());
        assertEquals(11, s.blockCount());
        assertTrue(s.format().startsWith("MCEdit"));
    }

    @Test
    void mcEditCompletesStatesThatLivedInTileEntitiesAndNeighbours() throws IOException {
        // 4 wide, 2 high, 3 long
        int[][] cells = new int[24][];
        Arrays.fill(cells, new int[]{0, 0});
        cells[index(0, 0, 0, 4, 3)] = new int[]{26, 0};   // bed foot facing south, colour in its tile
        cells[index(1, 0, 0, 4, 3)] = new int[]{176, 4};  // standing banner, rotation 4, colour in its tile
        cells[index(2, 0, 0, 4, 3)] = new int[]{140, 0};  // flower pot, plant in its tile
        cells[index(3, 0, 0, 4, 3)] = new int[]{144, 1};  // floor skull, type and rotation in its tile
        cells[index(0, 0, 1, 4, 3)] = new int[]{64, 1};   // lower oak door, facing south
        cells[index(0, 1, 1, 4, 3)] = new int[]{64, 9};   // upper half: right hinge
        cells[index(1, 0, 1, 4, 3)] = new int[]{85, 0};   // fence
        cells[index(2, 0, 1, 4, 3)] = new int[]{85, 0};   // fence
        cells[index(3, 0, 1, 4, 3)] = new int[]{1, 0};    // stone
        cells[index(0, 0, 2, 4, 3)] = new int[]{2, 0};    // grass under snow
        cells[index(0, 1, 2, 4, 3)] = new int[]{78, 0};   // snow layer
        cells[index(1, 0, 2, 4, 3)] = new int[]{18, 4};   // oak leaves that never decay
        cells[index(2, 0, 2, 4, 3)] = new int[]{18, 0};   // oak leaves that decay
        cells[index(3, 0, 2, 4, 3)] = new int[]{25, 0};   // note block, note in its tile
        CompoundTag nbt = mcEdit(4, 2, 3, cells);
        ListTag tiles = new ListTag();
        tiles.add(tile("minecraft:bed", 0, 0, 0, t -> t.putInt("color", 11)));
        tiles.add(tile("minecraft:banner", 1, 0, 0, t -> t.putInt("Base", 1)));
        tiles.add(tile("minecraft:flower_pot", 2, 0, 0, t -> {
            t.putString("Item", "minecraft:red_flower");
            t.putInt("Data", 1);
        }));
        tiles.add(tile("minecraft:skull", 3, 0, 0, t -> {
            t.putByte("SkullType", (byte) 4);
            t.putByte("Rot", (byte) 6);
        }));
        tiles.add(tile("minecraft:noteblock", 3, 0, 2, t -> {
            t.putByte("note", (byte) 12);
            t.putBoolean("powered", false);
        }));
        nbt.put("TileEntities", tiles);

        LoadedSchematic s = load(nbt, "legacy.schematic");

        assertState("minecraft:blue_bed[facing=south,part=foot]", s, 0, 0, 0);
        assertState("minecraft:red_banner[rotation=4]", s, 1, 0, 0);
        assertState("minecraft:potted_blue_orchid", s, 2, 0, 0);
        assertState("minecraft:creeper_head[rotation=6]", s, 3, 0, 0);
        assertState("minecraft:oak_door[facing=south,half=lower,hinge=right]", s, 0, 0, 1);
        assertState("minecraft:oak_door[facing=south,half=upper,hinge=right]", s, 0, 1, 1);
        assertState("minecraft:oak_fence[east=true]", s, 1, 0, 1);
        assertState("minecraft:oak_fence[east=true,west=true]", s, 2, 0, 1);
        assertState("minecraft:grass_block[snowy=true]", s, 0, 0, 2);
        assertState("minecraft:oak_leaves[persistent=true]", s, 1, 0, 2);
        assertState("minecraft:oak_leaves[persistent=false]", s, 2, 0, 2);
        assertState("minecraft:note_block[note=12]", s, 3, 0, 2);
        // a tile is kept only where the block still has a block entity: not for pots, note blocks or (26.2) beds
        int[][] tilePositions = {{0, 0, 0}, {1, 0, 0}, {2, 0, 0}, {3, 0, 0}, {3, 0, 2}};
        for (int[] p : tilePositions) {
            assertEquals(s.getDirect(p[0], p[1], p[2]).hasBlockEntity(), s.blockEntity(p[0], p[1], p[2]) != null,
                "tile at " + Arrays.toString(p));
        }
        assertNotNull(s.blockEntity(1, 0, 0));
        assertNotNull(s.blockEntity(3, 0, 0));
        assertFalse(s.blockEntities().containsKey(new BlockPos(2, 0, 0)));
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test
    void mcEditReadsSpongeStyleTileEntities() throws IOException {
        CompoundTag nbt = mcEdit(1, 1, 1, new int[][]{{54, 2}});
        ListTag tiles = new ListTag();
        CompoundTag chest = new CompoundTag();
        chest.putIntArray("Pos", new int[]{0, 0, 0});
        chest.putString("Id", "minecraft:chest");
        chest.put("Items", new ListTag());
        tiles.add(chest);
        nbt.put("TileEntities", tiles);
        nbt.putInt("DataVersion", 4438);
        LoadedSchematic s = load(nbt, "converted.schematic");
        assertEquals("minecraft:chest", s.blockEntity(0, 0, 0).getStringOr("id", ""));
        assertFalse(s.blockEntity(0, 0, 0).contains("Pos"));
    }

    @Test
    void mcEditAddBlocksBeyondTheLegacyRangeLoadAsAirWithAWarning() throws IOException {
        int[][] cells = {{1, 0}, {300, 0}};
        CompoundTag nbt = mcEdit(2, 1, 1, cells);
        // AddBlocks holds the high nibble of each id, two per byte; 300 = 0x12C
        nbt.putByteArray("AddBlocks", new byte[]{0x01});
        LoadedSchematic s = load(nbt, "modded.schematic");
        assertState("minecraft:stone", s, 0, 0, 0);
        assertState("minecraft:air", s, 1, 0, 0);
        assertTrue(s.warnings().stream().anyMatch(w -> w.contains("300")), s.warnings().toString());
    }

    @Test
    void schematicaMappingRenumbersIdsByName() throws IOException {
        // Schematica saved stone under id 5 and red wool's block under id 7
        int[][] cells = {{5, 0}, {7, 14}};
        CompoundTag nbt = mcEdit(2, 1, 1, cells);
        CompoundTag mapping = new CompoundTag();
        mapping.putShort("minecraft:stone", (short) 5);
        mapping.putShort("minecraft:wool", (short) 7);
        nbt.put("SchematicaMapping", mapping);
        LoadedSchematic s = load(nbt, "schematica.schematic");
        assertState("minecraft:stone", s, 0, 0, 0);
        assertState("minecraft:red_wool", s, 1, 0, 0);
    }

    @Test
    void mcEditSchematicWithASpongePaletteButNoBlockDataUsesTheLegacyArrays() throws IOException {
        // Some converters write a modern Palette next to the classic arrays without any per-block palette data
        CompoundTag nbt = mcEdit(1, 1, 1, new int[][]{{3, 0}});
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:pale_moss_block", 0);
        nbt.put("Palette", palette);
        nbt.putInt("Version", 2);
        nbt.putInt("DataVersion", 4438);
        LoadedSchematic s = load(nbt, "hybrid.schematic");
        assertState("minecraft:dirt", s, 0, 0, 0);
    }

    // ---- Sponge .schem ----

    @Test
    void spongeV1ReadsTileEntitiesAndItsPalette() throws IOException {
        CompoundTag nbt = spongeBase(2, 1, 1, Map.of("minecraft:air", 0, "minecraft:oak_sign[rotation=4]", 1), new int[]{1, 0});
        nbt.putInt("Version", 1);
        ListTag tiles = new ListTag();
        CompoundTag sign = new CompoundTag();
        sign.putIntArray("Pos", new int[]{0, 0, 0});
        sign.putString("Id", "minecraft:sign");
        sign.putInt("ContentVersion", 1);
        tiles.add(sign);
        nbt.put("TileEntities", tiles);
        LoadedSchematic s = load(nbt, "v1.schem");
        assertState("minecraft:oak_sign[rotation=4]", s, 0, 0, 0);
        assertState("minecraft:air", s, 1, 0, 0);
        CompoundTag tile = s.blockEntity(0, 0, 0);
        assertNotNull(tile);
        assertEquals("minecraft:sign", tile.getStringOr("id", ""));
        assertFalse(tile.contains("Pos"));
        assertTrue(s.format().contains("Sponge v1"), s.format());
    }

    @Test
    void spongeV2DecodesMultiByteVarIntsAndUpgradesOldPalettes() throws IOException {
        // 130 palette entries, so index 129 takes two VarInt bytes
        List<String> names = defaultStates(128);
        Map<String, Integer> palette = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            palette.put(names.get(i), i);
        }
        palette.put("minecraft:grass", 128);            // short_grass since 23w45a
        palette.put("minecraft:chest[facing=west]", 129);
        CompoundTag nbt = spongeBase(3, 1, 1, palette, new int[]{129, 128, 5});
        nbt.putInt("Version", 2);
        nbt.putInt("DataVersion", 3465); // 1.20.1
        ListTag entities = new ListTag();
        CompoundTag chest = new CompoundTag();
        chest.putIntArray("Pos", new int[]{0, 0, 0});
        chest.putString("Id", "minecraft:chest");
        chest.put("Items", new ListTag());
        entities.add(chest);
        nbt.put("BlockEntities", entities);
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", "Varint");
        meta.putString("Author", "tester");
        nbt.put("Metadata", meta);

        LoadedSchematic s = load(nbt, "v2.schem");
        assertSize(s, 3, 1, 1);
        assertState("minecraft:chest[facing=west]", s, 0, 0, 0);
        assertState("minecraft:short_grass", s, 1, 0, 0);
        assertEquals(names.get(5), BlockStateParser.serialize(s.getDirect(2, 0, 0)));
        assertEquals("minecraft:chest", s.blockEntity(0, 0, 0).getStringOr("id", ""));
        assertEquals("Varint", s.name());
        assertEquals("tester", s.author());
        assertEquals(3465, s.dataVersion());
    }

    @Test
    void spongeV3ReadsTheNestedSchematicTag() throws IOException {
        CompoundTag inner = new CompoundTag();
        inner.putInt("Version", 3);
        inner.putInt("DataVersion", current);
        inner.putShort("Width", (short) 2);
        inner.putShort("Height", (short) 2);
        inner.putShort("Length", (short) 1);
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", "Tower");
        meta.putString("Author", "me");
        inner.put("Metadata", meta);
        CompoundTag blocks = new CompoundTag();
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:stone", 1);
        palette.putInt("minecraft:oak_log[axis=z]", 2);
        palette.putInt("minecraft:chest", 3);
        blocks.put("Palette", palette);
        // index = (y * length + z) * width + x
        blocks.putByteArray("Data", varInts(1, 2, 3, 0));
        ListTag entities = new ListTag();
        CompoundTag chest = new CompoundTag();
        chest.putIntArray("Pos", new int[]{0, 1, 0});
        chest.putString("Id", "minecraft:chest");
        CompoundTag data = new CompoundTag();
        data.put("Items", new ListTag());
        data.putString("CustomName", "\"Loot\"");
        chest.put("Data", data);
        entities.add(chest);
        blocks.put("BlockEntities", entities);
        inner.put("Blocks", blocks);
        CompoundTag root = new CompoundTag();
        root.put("Schematic", inner);

        LoadedSchematic s = load(root, "v3.schem");
        assertSize(s, 2, 2, 1);
        assertState("minecraft:stone", s, 0, 0, 0);
        assertState("minecraft:oak_log[axis=z]", s, 1, 0, 0);
        assertState("minecraft:chest", s, 0, 1, 0);
        assertState("minecraft:air", s, 1, 1, 0);
        CompoundTag tile = s.blockEntity(0, 1, 0);
        assertEquals("minecraft:chest", tile.getStringOr("id", ""));
        assertTrue(tile.contains("Items"));
        assertEquals("Tower", s.name());
        assertTrue(s.format().contains("Sponge v3"), s.format());
    }

    @Test
    void aSpongeFileNamedSchematicLoadsAsSponge() throws IOException {
        // WorldEdit 7 and FAWE wrote Sponge data under the old extension
        CompoundTag nbt = spongeBase(1, 1, 1, Map.of("minecraft:diamond_block", 0), new int[]{0});
        nbt.putInt("Version", 2);
        nbt.putInt("DataVersion", current);
        LoadedSchematic s = load(nbt, "worldedit7.schematic");
        assertState("minecraft:diamond_block", s, 0, 0, 0);
    }

    // ---- Litematica .litematic ----

    @Test
    void litematicReadsEveryRegionWithNegativeSizesAndPackedStates() throws IOException {
        CompoundTag regions = new CompoundTag();
        // Base: (0,0,0) .. (1,0,0)
        regions.put("Base", region(new Vec3i(0, 0, 0), new Vec3i(2, 1, 1),
            List.of("minecraft:air", "minecraft:stone", "minecraft:dirt"), new int[]{1, 2}, List.of()));
        // Tower: position (5,0,0), size -2 on x and -1 on y, so it spans x 4..5, y 0
        CompoundTag chestTile = new CompoundTag();
        chestTile.putString("id", "minecraft:chest");
        chestTile.putInt("x", 0);
        chestTile.putInt("y", 0);
        chestTile.putInt("z", 0);
        chestTile.put("Items", new ListTag());
        regions.put("Tower", region(new Vec3i(5, 0, 0), new Vec3i(-2, -1, 1),
            List.of("minecraft:air", "minecraft:chest[facing=north]", "minecraft:glass"), new int[]{1, 2}, List.of(chestTile)));
        // Wide: 30 states at 5 bits each, so entries straddle the 64-bit words
        List<String> wide = defaultStates(30);
        int[] wideCells = new int[30];
        for (int i = 0; i < 30; i++) {
            wideCells[i] = 29 - i;
        }
        regions.put("Wide", region(new Vec3i(0, 1, 0), new Vec3i(30, 1, 1), wide, wideCells, List.of()));

        CompoundTag root = litematic(7, current, regions);
        LoadedSchematic s = load(root, "regions.litematic");

        assertSize(s, 30, 2, 1);
        assertState("minecraft:stone", s, 0, 0, 0);
        assertState("minecraft:dirt", s, 1, 0, 0);
        assertState("minecraft:chest[facing=north]", s, 4, 0, 0);
        assertState("minecraft:glass", s, 5, 0, 0);
        for (int x = 0; x < 30; x++) {
            assertEquals(wide.get(29 - x), BlockStateParser.serialize(s.getDirect(x, 1, 0)), "wide x=" + x);
        }
        // the gaps between regions are not part of the schematic
        assertFalse(s.contains(2, 0, 0));
        assertFalse(s.contains(3, 0, 0));
        assertFalse(s.contains(6, 0, 0));
        assertTrue(s.contains(1, 0, 0));
        assertTrue(s.contains(10, 1, 0));
        assertFalse(s.inSchematic(3, 0, 0, null));

        assertEquals(1, s.blockEntities().size());
        assertEquals("minecraft:chest", s.blockEntity(4, 0, 0).getStringOr("id", ""));
        assertEquals(3, s.regions().size());
        LoadedSchematic.Region tower = s.regions().stream().filter(r -> r.name().equals("Tower")).findFirst().orElseThrow();
        assertEquals(new BlockPos(4, 0, 0), tower.min());
        assertEquals(new Vec3i(2, 1, 1), tower.size());
        assertEquals("Regions", s.name());
        assertEquals("tester", s.author());
    }

    @Test
    void litematicRegionOffsetsAreRelativeToTheLowestCorner() throws IOException {
        CompoundTag regions = new CompoundTag();
        regions.put("A", region(new Vec3i(-3, 10, 2), new Vec3i(1, 1, -2),
            List.of("minecraft:air", "minecraft:gold_block", "minecraft:iron_block"), new int[]{1, 2}, List.of()));
        LoadedSchematic s = load(litematic(6, 3700, regions), "neg.litematic");
        assertSize(s, 1, 1, 2);
        // z runs from 1 to 2; index 0 is the lowest z
        assertState("minecraft:gold_block", s, 0, 0, 0);
        assertState("minecraft:iron_block", s, 0, 0, 1);
    }

    @Test
    void litematicV5UpgradesBlocksRenamedSince() throws IOException {
        CompoundTag regions = new CompoundTag();
        regions.put("R", region(new Vec3i(0, 0, 0), new Vec3i(2, 1, 1),
            List.of("minecraft:air", "minecraft:grass", "minecraft:grass_path"), new int[]{1, 2}, List.of()));
        LoadedSchematic s = load(litematic(5, 2586, regions), "old.litematic");
        assertState("minecraft:short_grass", s, 0, 0, 0);
        assertState("minecraft:dirt_path", s, 1, 0, 0);
    }

    @Test
    void litematicV4WithA112PaletteIsFlattened() throws IOException {
        ListTag palette = new ListTag();
        palette.add(stateTag("minecraft:air", Map.of()));
        palette.add(stateTag("minecraft:wool", Map.of("color", "red")));
        palette.add(stateTag("minecraft:log", Map.of("axis", "x", "variant", "spruce")));
        CompoundTag region = new CompoundTag();
        region.put("Position", vec(new Vec3i(0, 0, 0)));
        region.put("Size", vec(new Vec3i(2, 1, 1)));
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", pack(new int[]{1, 2}, 2));
        region.put("TileEntities", new ListTag());
        CompoundTag regions = new CompoundTag();
        regions.put("Old", region);
        CompoundTag root = litematic(4, 0, regions);
        root.remove("MinecraftDataVersion");
        LoadedSchematic s = load(root, "112.litematic");
        assertState("minecraft:red_wool", s, 0, 0, 0);
        assertState("minecraft:spruce_log[axis=x]", s, 1, 0, 0);
    }

    @Test
    void litematicFromANewerVersionKeepsWhatItKnows() throws IOException {
        CompoundTag regions = new CompoundTag();
        regions.put("R", region(new Vec3i(0, 0, 0), new Vec3i(2, 1, 1),
            List.of("minecraft:air", "minecraft:stone", "futuremod:shiny_block"), new int[]{1, 2}, List.of()));
        LoadedSchematic s = load(litematic(7, current + 500, regions), "future.litematic");
        assertState("minecraft:stone", s, 0, 0, 0);
        assertState("minecraft:air", s, 1, 0, 0);
        assertTrue(s.warnings().stream().anyMatch(w -> w.contains("futuremod:shiny_block")), s.warnings().toString());
    }

    @Test
    void statesInThe263FieldLayoutLoad() throws IOException {
        // 26.3 writes states as {id, properties} instead of {Name, Properties}
        ListTag palette = new ListTag();
        CompoundTag air = new CompoundTag();
        air.putString("id", "minecraft:air");
        palette.add(air);
        CompoundTag log = new CompoundTag();
        log.putString("id", "minecraft:birch_log");
        CompoundTag props = new CompoundTag();
        props.putString("axis", "z");
        log.put("properties", props);
        palette.add(log);
        CompoundTag region = new CompoundTag();
        region.put("Position", vec(new Vec3i(0, 0, 0)));
        region.put("Size", vec(new Vec3i(1, 1, 1)));
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", pack(new int[]{1}, 2));
        CompoundTag regions = new CompoundTag();
        regions.put("R", region);
        LoadedSchematic s = load(litematic(7, current, regions), "new.litematic");
        assertState("minecraft:birch_log[axis=z]", s, 0, 0, 0);
    }

    @Test
    void anUnknownPropertyValueIsAWarningNotACrash() throws IOException {
        CompoundTag regions = new CompoundTag();
        regions.put("R", region(new Vec3i(0, 0, 0), new Vec3i(1, 1, 1),
            List.of("minecraft:air", "minecraft:oak_stairs[facing=sideways]"), new int[]{1}, List.of()));
        LoadedSchematic s = load(litematic(7, current, regions), "odd.litematic");
        assertEquals("minecraft:oak_stairs", id(s.getDirect(0, 0, 0)));
        assertFalse(s.warnings().isEmpty());
    }

    // ---- vanilla structure .nbt ----

    @Test
    void vanillaStructureReadsPaletteBlocksAndNbt() throws IOException {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("DataVersion", current);
        nbt.put("size", intList(3, 1, 2));
        ListTag palette = new ListTag();
        palette.add(stateTag("minecraft:stone", Map.of()));
        palette.add(stateTag("minecraft:chest", Map.of("facing", "east")));
        palette.add(stateTag("minecraft:air", Map.of()));
        nbt.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(structureBlock(0, 0, 0, 0, null));
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.put("Items", new ListTag());
        blocks.add(structureBlock(1, 0, 0, 1, chest));
        blocks.add(structureBlock(0, 0, 1, 2, null));
        nbt.put("blocks", blocks);
        nbt.put("entities", new ListTag());

        LoadedSchematic s = load(nbt, "hut.nbt");
        assertSize(s, 3, 1, 2);
        assertState("minecraft:stone", s, 0, 0, 0);
        assertState("minecraft:chest[facing=east]", s, 1, 0, 0);
        assertState("minecraft:air", s, 0, 0, 1);
        assertTrue(s.contains(0, 0, 1));
        // structure voids are left out of the block list
        assertFalse(s.contains(2, 0, 0));
        assertFalse(s.contains(1, 0, 1));
        assertEquals("minecraft:chest", s.blockEntity(1, 0, 0).getStringOr("id", ""));
    }

    @Test
    void oldVanillaStructuresAreDataFixed() throws IOException {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("DataVersion", 1343); // 1.12.2
        nbt.put("size", intList(1, 1, 1));
        ListTag palette = new ListTag();
        palette.add(stateTag("minecraft:wool", Map.of("color", "red")));
        nbt.put("palette", palette);
        ListTag blocks = new ListTag();
        blocks.add(structureBlock(0, 0, 0, 0, null));
        nbt.put("blocks", blocks);
        nbt.put("entities", new ListTag());
        LoadedSchematic s = load(nbt, "old.nbt");
        assertState("minecraft:red_wool", s, 0, 0, 0);
    }

    // ---- bad files ----

    @Test
    void garbageIsAClearError() {
        SchematicLoadException e = assertThrows(SchematicLoadException.class,
            () -> SchematicLoader.load(new ByteArrayInputStream("hello, not nbt".getBytes(StandardCharsets.UTF_8)), "junk.schematic"));
        assertTrue(e.getMessage().contains("NBT"), e.getMessage());
    }

    @Test
    void anEmptyFileIsAClearError() {
        SchematicLoadException e = assertThrows(SchematicLoadException.class,
            () -> SchematicLoader.load(new ByteArrayInputStream(new byte[0]), "empty.litematic"));
        assertTrue(e.getMessage().contains("empty"), e.getMessage());
    }

    @Test
    void uncompressedNbtLoadsToo() throws IOException {
        CompoundTag nbt = spongeBase(1, 1, 1, Map.of("minecraft:stone", 0), new int[]{0});
        nbt.putInt("Version", 2);
        nbt.putInt("DataVersion", current);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.write(nbt, new DataOutputStream(out));
        LoadedSchematic s = SchematicLoader.load(new ByteArrayInputStream(out.toByteArray()), "plain.schem");
        assertState("minecraft:stone", s, 0, 0, 0);
    }

    @Test
    void unknownLayoutsAreRejectedWithTheirTags() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("Foo", 1);
        SchematicLoadException e = assertThrows(SchematicLoadException.class, () -> load(nbt, "what.schem"));
        assertTrue(e.getMessage().contains("Foo"), e.getMessage());
    }

    @Test
    void unsupportedLitematicVersionsSaySo() {
        CompoundTag root = litematic(99, current, new CompoundTag());
        SchematicLoadException e = assertThrows(SchematicLoadException.class, () -> load(root, "v99.litematic"));
        assertTrue(e.getMessage().contains("99"), e.getMessage());
    }

    @Test
    void truncatedLitematicStatesNameTheRegion() {
        CompoundTag regions = new CompoundTag();
        CompoundTag region = region(new Vec3i(0, 0, 0), new Vec3i(40, 1, 1),
            List.of("minecraft:air", "minecraft:stone"), new int[40], List.of());
        region.putLongArray("BlockStates", new long[1]);
        regions.put("Broken", region);
        SchematicLoadException e = assertThrows(SchematicLoadException.class, () -> load(litematic(7, current, regions), "short.litematic"));
        assertTrue(e.getMessage().contains("Broken"), e.getMessage());
    }

    @Test
    void spongeIndicesOutsideThePaletteAreAClearError() {
        CompoundTag nbt = spongeBase(2, 1, 1, Map.of("minecraft:stone", 0), new int[]{0, 7});
        nbt.putInt("Version", 2);
        nbt.putInt("DataVersion", current);
        SchematicLoadException e = assertThrows(SchematicLoadException.class, () -> load(nbt, "bad.schem"));
        assertTrue(e.getMessage().contains("palette"), e.getMessage());
    }

    @Test
    void missingBlockDataIsAClearError() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("Version", 2);
        nbt.putInt("DataVersion", current);
        nbt.putShort("Width", (short) 1);
        nbt.putShort("Height", (short) 1);
        nbt.putShort("Length", (short) 1);
        nbt.put("Palette", new CompoundTag());
        SchematicLoadException e = assertThrows(SchematicLoadException.class, () -> load(nbt, "nodata.schem"));
        assertNotNull(e.getMessage());
        assertFalse(e.getMessage().isBlank());
    }

    @Test
    void hugeSizesAreRefusedBeforeAllocating() {
        CompoundTag nbt = spongeBase(1, 1, 1, Map.of("minecraft:stone", 0), new int[]{0});
        nbt.putShort("Width", (short) 30000);
        nbt.putShort("Height", (short) 3000);
        nbt.putShort("Length", (short) 30000);
        nbt.putInt("Version", 2);
        nbt.putInt("DataVersion", current);
        SchematicLoadException e = assertThrows(SchematicLoadException.class, () -> load(nbt, "huge.schem"));
        assertTrue(e.getMessage().contains("large"), e.getMessage());
    }

    // ---- fixture builders ----

    private static LoadedSchematic load(CompoundTag root, String fileName) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, out);
        return SchematicLoader.load(new ByteArrayInputStream(out.toByteArray()), fileName);
    }

    private static int index(int x, int y, int z, int width, int length) {
        return (y * length + z) * width + x;
    }

    /**
     * @param cells {id, data} per cell in MCEdit order
     */
    private static CompoundTag mcEdit(int width, int height, int length, int[][] cells) {
        CompoundTag nbt = new CompoundTag();
        nbt.putShort("Width", (short) width);
        nbt.putShort("Height", (short) height);
        nbt.putShort("Length", (short) length);
        nbt.putString("Materials", "Alpha");
        byte[] blocks = new byte[cells.length];
        byte[] data = new byte[cells.length];
        for (int i = 0; i < cells.length; i++) {
            blocks[i] = (byte) cells[i][0];
            data[i] = (byte) cells[i][1];
        }
        nbt.putByteArray("Blocks", blocks);
        nbt.putByteArray("Data", data);
        nbt.put("Entities", new ListTag());
        nbt.put("TileEntities", new ListTag());
        return nbt;
    }

    private static CompoundTag tile(String id, int x, int y, int z, Consumer<CompoundTag> data) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        data.accept(tag);
        return tag;
    }

    private static CompoundTag spongeBase(int width, int height, int length, Map<String, Integer> palette, int[] cells) {
        CompoundTag nbt = new CompoundTag();
        nbt.putShort("Width", (short) width);
        nbt.putShort("Height", (short) height);
        nbt.putShort("Length", (short) length);
        CompoundTag paletteTag = new CompoundTag();
        palette.forEach(paletteTag::putInt);
        nbt.put("Palette", paletteTag);
        nbt.putInt("PaletteMax", palette.size());
        nbt.putByteArray("BlockData", varInts(cells));
        return nbt;
    }

    private static byte[] varInts(int... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int v : values) {
            while ((v & ~0x7F) != 0) {
                out.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            out.write(v);
        }
        return out.toByteArray();
    }

    private static CompoundTag litematic(int version, int dataVersion, CompoundTag regions) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", version);
        root.putInt("SubVersion", 1);
        root.putInt("MinecraftDataVersion", dataVersion);
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", "Regions");
        meta.putString("Author", "tester");
        meta.putString("Description", "");
        meta.putInt("RegionCount", regions.size());
        root.put("Metadata", meta);
        root.put("Regions", regions);
        return root;
    }

    private static CompoundTag region(Vec3i pos, Vec3i size, List<String> palette, int[] cells, List<CompoundTag> tiles) {
        CompoundTag region = new CompoundTag();
        region.put("Position", vec(pos));
        region.put("Size", vec(size));
        ListTag paletteTag = new ListTag();
        for (String spec : palette) {
            paletteTag.add(stateTag(spec));
        }
        region.put("BlockStatePalette", paletteTag);
        int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.size() - 1));
        region.putLongArray("BlockStates", pack(cells, bits));
        ListTag tileList = new ListTag();
        tileList.addAll(tiles);
        region.put("TileEntities", tileList);
        region.put("Entities", new ListTag());
        region.put("PendingBlockTicks", new ListTag());
        return region;
    }

    /**
     * Packs values the way Litematica's bit array does: densely, an entry may span two longs.
     */
    private static long[] pack(int[] values, int bits) {
        long[] longs = new long[(int) ((values.length * (long) bits + 63) / 64)];
        for (int i = 0; i < values.length; i++) {
            long start = (long) i * bits;
            int word = (int) (start >> 6);
            int offset = (int) (start & 63);
            longs[word] |= ((long) values[i]) << offset;
            if (offset + bits > 64) {
                longs[word + 1] |= ((long) values[i]) >>> (64 - offset);
            }
        }
        return longs;
    }

    private static CompoundTag vec(Vec3i v) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", v.getX());
        tag.putInt("y", v.getY());
        tag.putInt("z", v.getZ());
        return tag;
    }

    private static CompoundTag stateTag(String spec) {
        int bracket = spec.indexOf('[');
        if (bracket < 0) {
            return stateTag(spec, Map.of());
        }
        Map<String, String> props = new LinkedHashMap<>();
        for (String pair : spec.substring(bracket + 1, spec.length() - 1).split(",")) {
            String[] kv = pair.split("=");
            props.put(kv[0], kv[1]);
        }
        return stateTag(spec.substring(0, bracket), props);
    }

    private static CompoundTag stateTag(String name, Map<String, String> properties) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        if (!properties.isEmpty()) {
            CompoundTag props = new CompoundTag();
            properties.forEach(props::putString);
            tag.put("Properties", props);
        }
        return tag;
    }

    private static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int v : values) {
            list.add(IntTag.valueOf(v));
        }
        return list;
    }

    private static CompoundTag structureBlock(int x, int y, int z, int state, CompoundTag nbt) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", intList(x, y, z));
        tag.putInt("state", state);
        if (nbt != null) {
            tag.put("nbt", nbt);
        }
        return tag;
    }

    /**
     * Distinct default block states, serialized, skipping air so a palette of them has one air at most.
     */
    private static List<String> defaultStates(int count) {
        List<String> out = new ArrayList<>();
        out.add("minecraft:air");
        for (var block : BuiltInRegistries.BLOCK) {
            if (out.size() == count) {
                break;
            }
            BlockState state = block.defaultBlockState();
            if (!state.isAir()) {
                out.add(BlockStateParser.serialize(state));
            }
        }
        return out;
    }

    private static String id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static void assertState(String expected, LoadedSchematic s, int x, int y, int z) {
        BlockState want;
        try {
            want = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, expected, false).blockState();
        } catch (Exception e) {
            throw new AssertionError("bad expected state " + expected, e);
        }
        BlockState got = s.getDirect(x, y, z);
        assertEquals(BlockStateParser.serialize(want), BlockStateParser.serialize(got), "at " + x + "," + y + "," + z);
    }

    private static void assertSize(LoadedSchematic s, int x, int y, int z) {
        assertEquals(x, s.widthX(), "width");
        assertEquals(y, s.heightY(), "height");
        assertEquals(z, s.lengthZ(), "length");
    }
}
