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
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Sponge schematic format (".schem"), versions 1 to 3, as written by WorldEdit 7 and FAWE.
 *
 * @author Brady
 * @see <a href="https://github.com/SpongePowered/Schematic-Specification">Sponge Schematic Specification</a>
 * @since 12/27/2019
 */
public final class SpongeSchematic extends LoadedSchematic {

    /**
     * Version 1 records no data version; it was written by 1.13 tools.
     */
    private static final int V1_DATA_VERSION = 1519;

    /**
     * @param root the file's root tag; for version 3 the data sits in its "Schematic" child
     */
    public SpongeSchematic(CompoundTag root) {
        CompoundTag nbt = root.getCompound("Schematic").filter(SpongeSchematic::isV3).orElse(root);
        int version = nbt.getIntOr("Version", nbt.getCompound("Blocks").isPresent() ? 3 : 2);
        if (version < 1 || version > 3) {
            throw new IllegalArgumentException("Sponge schematic version " + version + " is not supported (1 to 3 are)");
        }
        int width = size(nbt, "Width");
        int height = size(nbt, "Height");
        int length = size(nbt, "Length");
        this.dataVersion = nbt.getIntOr("DataVersion", V1_DATA_VERSION);
        this.format = "Sponge v" + version;
        CompoundTag metadata = nbt.getCompoundOrEmpty("Metadata");
        this.name = metadata.getStringOr("Name", "");
        this.author = metadata.getStringOr("Author", "");

        CompoundTag blocksTag = version >= 3 ? nbt.getCompoundOrEmpty("Blocks") : nbt;
        CompoundTag paletteTag = blocksTag.getCompound("Palette")
            .orElseThrow(() -> new IllegalArgumentException("it has no block palette"));
        byte[] data = blocksTag.getByteArray(version >= 3 ? "Data" : "BlockData")
            .orElseThrow(() -> new IllegalArgumentException("it has no block data"));

        BlockStateReader reader = new BlockStateReader(this.dataVersion);
        int highest = -1;
        for (String key : paletteTag.keySet()) {
            highest = Math.max(highest, paletteTag.getIntOr(key, -1));
        }
        BlockState[] palette = new BlockState[highest + 1];
        for (String key : paletteTag.keySet()) {
            int index = paletteTag.getIntOr(key, -1);
            if (index >= 0) {
                palette[index] = reader.read(key);
            }
        }

        allocate(width, height, length, false);
        int volume = width * height * length;
        int offset = 0;
        for (int index = 0; index < volume; index++) {
            // VarInts, 7 bits per byte, low bits first
            int value = 0;
            int shift = 0;
            while (true) {
                if (offset >= data.length) {
                    throw new IllegalArgumentException("its block data ends after " + index + " of " + volume + " blocks");
                }
                byte b = data[offset++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 28) {
                    throw new IllegalArgumentException("its block data has a malformed VarInt at byte " + offset);
                }
            }
            if (value < 0 || value >= palette.length || palette[value] == null) {
                throw new IllegalArgumentException("block " + index + " uses palette index " + value
                    + ", which the palette (" + paletteTag.size() + " entries) does not have");
            }
            int y = index / (width * length);
            int z = (index % (width * length)) / width;
            int x = index % width;
            setBlock(x, y, z, palette[value]);
        }

        String entitiesKey = version == 1 ? "TileEntities" : "BlockEntities";
        for (Tag entry : blocksTag.getListOrEmpty(entitiesKey)) {
            if (!(entry instanceof CompoundTag tile)) {
                continue;
            }
            int[] pos = tile.getIntArray("Pos").orElse(new int[0]);
            if (pos.length < 3 || !contains(pos[0], pos[1], pos[2])) {
                continue;
            }
            CompoundTag out;
            if (version >= 3) {
                out = tile.getCompoundOrEmpty("Data").copy();
            } else {
                out = tile.copy();
                out.remove("Pos");
                out.remove("Id");
                out.remove("ContentVersion");
            }
            out.putString("id", tile.getStringOr("Id", ""));
            putBlockEntity(new BlockPos(pos[0], pos[1], pos[2]), reader.blockEntity(out, this.dataVersion));
        }
        reader.warnings().forEach(this::warn);
    }

    private static boolean isV3(CompoundTag tag) {
        return tag.getIntOr("Version", 0) >= 3 || tag.getCompound("Blocks").isPresent();
    }

    private static int size(CompoundTag nbt, String key) {
        int value = nbt.getIntOr(key, -1);
        if (value == -1 && !nbt.contains(key)) {
            throw new IllegalArgumentException("it has no " + key);
        }
        // unsigned shorts
        return value < 0 ? value & 0xFFFF : value;
    }
}
