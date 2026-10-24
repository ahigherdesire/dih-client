/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils.schematic.format.defaults;

import baritone.utils.schematic.LoadedSchematic;
import baritone.utils.schematic.format.BlockStateReader;
import baritone.utils.schematic.format.LegacyBlockNames;
import baritone.utils.schematic.format.LegacyStateFixer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * The MCEdit format (".schematic"), as written by MCEdit, Schematica and WorldEdit before 1.13: pre-1.13 numeric
 * block ids and data values, which are mapped to today's block states.
 *
 * @author Brady
 * @since 12/27/2019
 */
public final class MCEditSchematic extends LoadedSchematic {

    public MCEditSchematic(CompoundTag schematic) {
        String materials = schematic.getStringOr("Materials", "Alpha");
        if (!materials.equals("Alpha")) {
            throw new IllegalArgumentException("its block ids are from \"" + materials
                + "\" (only \"Alpha\", Java Edition, is supported)");
        }
        int width = size(schematic, "Width");
        int height = size(schematic, "Height");
        int length = size(schematic, "Length");
        byte[] blocks = schematic.getByteArray("Blocks")
            .orElseThrow(() -> new IllegalArgumentException("it has no Blocks array"));
        byte[] metadata = schematic.getByteArray("Data").orElse(new byte[0]);
        allocate(width, height, length, false);
        int volume = width * height * length;
        if (blocks.length < volume) {
            throw new IllegalArgumentException("its Blocks array holds " + blocks.length + " ids, but "
                + width + "x" + height + "x" + length + " needs " + volume);
        }

        // The high 4 bits of ids above 255. "AddBlocks" packs two per byte, the first block in the high nibble
        // (MCEdit, Schematica); Schematica's older "Add" keeps one per byte.
        byte[] additional = null;
        if (schematic.getByteArray("AddBlocks").isPresent()) {
            byte[] addBlocks = schematic.getByteArray("AddBlocks").get();
            additional = new byte[addBlocks.length * 2];
            for (int i = 0; i < addBlocks.length; i++) {
                additional[i * 2] = (byte) ((addBlocks[i] >> 4) & 0xF);
                additional[i * 2 + 1] = (byte) (addBlocks[i] & 0xF);
            }
        } else if (schematic.getByteArray("Add").isPresent()) {
            additional = schematic.getByteArray("Add").get();
        }

        BlockStateReader reader = new BlockStateReader(BlockStateReader.DEFAULT_LEGACY);
        int[] remap = remap(schematic, reader);
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int index = (y * length + z) * width + x;
                    int id = blocks[index] & 0xFF;
                    if (additional != null && index < additional.length) {
                        id |= (additional[index] & 0xF) << 8;
                    }
                    if (remap != null && id < remap.length && remap[id] != Integer.MIN_VALUE) {
                        id = remap[id];
                    }
                    int meta = index < metadata.length ? metadata[index] & 0xF : 0;
                    setBlock(x, y, z, id < 0 ? Blocks.AIR.defaultBlockState() : reader.legacy(id, meta));
                }
            }
        }

        // Tile entities, as saved: "x", "y", "z" and "id", or Sponge-style "Pos" and "Id" in some converters' files
        Map<BlockPos, CompoundTag> tiles = new HashMap<>();
        for (Tag entry : schematic.getListOrEmpty("TileEntities")) {
            if (!(entry instanceof CompoundTag tile)) {
                continue;
            }
            int[] pos = tile.getIntArray("Pos").orElse(new int[]{tile.getIntOr("x", -1), tile.getIntOr("y", -1), tile.getIntOr("z", -1)});
            if (pos.length < 3 || !contains(pos[0], pos[1], pos[2])) {
                continue;
            }
            CompoundTag data = tile.copy();
            for (String key : new String[]{"x", "y", "z", "Pos", "Id"}) {
                data.remove(key);
            }
            if (!data.contains("id")) {
                data.putString("id", tile.getStringOr("Id", ""));
            }
            tiles.put(new BlockPos(pos[0], pos[1], pos[2]), data);
        }
        LegacyStateFixer.fix(this.states, tiles);

        // Ids like "Chest" are from before 1.11, namespaced ones from 1.11 or 1.12, unless the file records the
        // version it was written for. Tiles of blocks that no longer have one (flower pots, note blocks) are dropped.
        int recorded = schematic.getIntOr("DataVersion", 0);
        tiles.forEach((pos, data) -> {
            if (getDirect(pos.getX(), pos.getY(), pos.getZ()).hasBlockEntity()) {
                int from = recorded > 0 ? recorded
                    : data.getStringOr("id", "").indexOf(':') >= 0 ? BlockStateReader.DEFAULT_LEGACY : BlockStateReader.OLDEST;
                putBlockEntity(pos, reader.blockEntity(data, from));
            }
        });

        this.format = remap != null ? "Schematica" : "MCEdit";
        this.dataVersion = recorded;
        if (schematic.getCompound("Palette").isPresent()) {
            warn("This .schematic also carries a modern block palette, but no per-block palette data, so its blocks "
                + "were read from the classic 1.12 ids. Blocks added after 1.12 may be missing; a .schem or "
                + ".litematic export of the same build keeps them.");
        }
        reader.warnings().forEach(this::warn);
    }

    /**
     * @return old id to 1.12 id for files that record their own id table (Schematica's "SchematicaMapping",
     * MCEdit-Unified's "BlockIDs"), {@link Integer#MIN_VALUE} where the file keeps the vanilla id, or null if the
     * file has no table. Ids of blocks 1.12 never had map to -1.
     */
    private static int[] remap(CompoundTag schematic, BlockStateReader reader) {
        int[] remap = new int[4096];
        Arrays.fill(remap, Integer.MIN_VALUE);
        boolean any = false;
        CompoundTag schematica = schematic.getCompoundOrEmpty("SchematicaMapping");
        for (String name : schematica.keySet()) {
            int fileId = schematica.getIntOr(name, -1);
            if (fileId >= 0 && fileId < remap.length) {
                remap[fileId] = vanillaId(name, reader);
                any = true;
            }
        }
        CompoundTag unified = schematic.getCompoundOrEmpty("BlockIDs");
        for (String key : unified.keySet()) {
            try {
                int fileId = Integer.parseInt(key);
                if (fileId >= 0 && fileId < remap.length) {
                    remap[fileId] = vanillaId(unified.getStringOr(key, ""), reader);
                    any = true;
                }
            } catch (NumberFormatException ignored) {
                // not an id entry
            }
        }
        return any ? remap : null;
    }

    private static int vanillaId(String name, BlockStateReader reader) {
        int id = LegacyBlockNames.id(name);
        if (id < 0) {
            reader.unknown(name);
        }
        return id;
    }

    private static int size(CompoundTag schematic, String key) {
        int value = schematic.getIntOr(key, -1);
        if (value == -1 && !schematic.contains(key)) {
            throw new IllegalArgumentException("it has no " + key);
        }
        // stored as a signed short
        return value < 0 ? value & 0xFFFF : value;
    }
}
