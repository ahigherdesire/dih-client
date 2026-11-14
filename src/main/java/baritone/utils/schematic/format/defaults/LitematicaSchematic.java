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
import baritone.utils.schematic.format.LegacyStateFixer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Litematica format (".litematic"), versions 1 to 7. Every region is read, each at its own position, into one
 * schematic whose origin is the lowest corner of all of them. Cells between regions are not part of the schematic.
 * Where regions overlap, a block wins over air and otherwise the region later in name order wins.
 * <p>
 * Based on EmersonDove's work
 * <a href="https://github.com/cabaletta/baritone/pull/2544">...</a>, and on Litematica's own reader
 * ({@code fi.dy.masa.litematica.schematic.LitematicaSchematic#readSubRegionsFromNBT}, LGPLv3) for the region bounds,
 * the bits per entry, the tile entity layouts of versions 1 and 2+, and the 1.12 default data version.
 *
 * @author rycbar
 * @since 22.09.2022
 */
public final class LitematicaSchematic extends LoadedSchematic {

    /**
     * The newest version this reader knows, the one Litematica writes for 1.20.5 and later.
     */
    public static final int MAX_VERSION = 7;
    /**
     * Versions before this were written by 1.12 Litematica, with pre-flattening block states.
     */
    private static final int FIRST_1_13_VERSION = 5;

    private record Region(String name, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, CompoundTag tag) {}

    /**
     * @param nbt the decompressed file
     */
    public LitematicaSchematic(CompoundTag nbt) {
        int version = nbt.getIntOr("Version", -1);
        if (version == -1) {
            throw new IllegalArgumentException("it records no Litematica version");
        }
        if (version < 1 || version > MAX_VERSION) {
            throw new IllegalArgumentException("Litematica schematic version " + version + " is not supported (1 to "
                + MAX_VERSION + " are)");
        }
        this.dataVersion = nbt.getIntOr("MinecraftDataVersion", BlockStateReader.DEFAULT_LEGACY);
        int statesVersion = version < FIRST_1_13_VERSION
            ? Math.min(this.dataVersion, BlockStateReader.DEFAULT_LEGACY) : this.dataVersion;
        this.format = "Litematica v" + version;
        CompoundTag metadata = nbt.getCompoundOrEmpty("Metadata");
        this.name = metadata.getStringOr("Name", "");
        this.author = metadata.getStringOr("Author", "");
        this.description = metadata.getStringOr("Description", "");

        List<Region> regions = regions(nbt.getCompoundOrEmpty("Regions"));
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("it has no regions");
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Region r : regions) {
            minX = Math.min(minX, r.minX);
            minY = Math.min(minY, r.minY);
            minZ = Math.min(minZ, r.minZ);
            maxX = Math.max(maxX, r.minX + r.sizeX - 1);
            maxY = Math.max(maxY, r.minY + r.sizeY - 1);
            maxZ = Math.max(maxZ, r.minZ + r.sizeZ - 1);
        }
        allocate(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, regions.size() > 1);

        BlockStateReader reader = new BlockStateReader(statesVersion);
        Map<BlockPos, CompoundTag> tiles = new HashMap<>();
        for (Region r : regions) {
            int offX = r.minX - minX, offY = r.minY - minY, offZ = r.minZ - minZ;
            readBlocks(r, offX, offY, offZ, reader);
            readTileEntities(r, version, offX, offY, offZ, tiles);
            addRegion(r.name, new BlockPos(offX, offY, offZ), new Vec3i(r.sizeX, r.sizeY, r.sizeZ));
        }
        boolean legacy = version < FIRST_1_13_VERSION;
        if (legacy) {
            LegacyStateFixer.fix(this.states, tiles);
        }
        tiles.forEach((pos, data) -> {
            if (!legacy || getDirect(pos.getX(), pos.getY(), pos.getZ()).hasBlockEntity()) {
                putBlockEntity(pos, reader.blockEntity(data, this.dataVersion));
            }
        });
        reader.warnings().forEach(this::warn);
    }

    private static List<Region> regions(CompoundTag regionsTag) {
        List<Region> regions = new ArrayList<>();
        for (String name : regionsTag.keySet()) {
            if (!(regionsTag.get(name) instanceof CompoundTag tag)) {
                continue;
            }
            CompoundTag pos = tag.getCompoundOrEmpty("Position");
            CompoundTag size = tag.getCompoundOrEmpty("Size");
            int[] min = new int[3];
            int[] abs = new int[3];
            String[] axes = {"x", "y", "z"};
            for (int i = 0; i < 3; i++) {
                int p = pos.getIntOr(axes[i], 0);
                int s = size.getIntOr(axes[i], 0);
                if (s == 0) {
                    throw new IllegalArgumentException("region '" + name + "' has no size along " + axes[i]);
                }
                // a negative size extends from the position towards lower coordinates
                min[i] = s > 0 ? p : p + s + 1;
                abs[i] = Math.abs(s);
            }
            regions.add(new Region(name, min[0], min[1], min[2], abs[0], abs[1], abs[2], tag));
        }
        regions.sort(Comparator.comparing(Region::name));
        return regions;
    }

    private void readBlocks(Region r, int offX, int offY, int offZ, BlockStateReader reader) {
        ListTag paletteTag = r.tag.getListOrEmpty("BlockStatePalette");
        if (paletteTag.isEmpty()) {
            throw new IllegalArgumentException("region '" + r.name + "' has no block palette");
        }
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = reader.read(paletteTag.getCompoundOrEmpty(i));
        }
        int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.length - 1));
        long volume = (long) r.sizeX * r.sizeY * r.sizeZ;
        long[] packed = r.tag.getLongArray("BlockStates").orElse(new long[0]);
        long needed = (volume * bits + 63) / 64;
        if (packed.length < needed) {
            throw new IllegalArgumentException("region '" + r.name + "' stores " + packed.length
                + " longs of block states, but its size needs " + needed);
        }
        LitematicaBitArray array = new LitematicaBitArray(bits, volume, packed);
        long index = 0;
        for (int y = 0; y < r.sizeY; y++) {
            for (int z = 0; z < r.sizeZ; z++) {
                for (int x = 0; x < r.sizeX; x++) {
                    int value = array.getAt(index++);
                    if (value >= palette.length) {
                        throw new IllegalArgumentException("region '" + r.name + "' uses palette index " + value
                            + ", but its palette has " + palette.length + " entries");
                    }
                    BlockState state = palette[value];
                    int sx = offX + x, sy = offY + y, sz = offZ + z;
                    if (state.isAir() && contains(sx, sy, sz) && !getDirect(sx, sy, sz).isAir()) {
                        continue;
                    }
                    setBlock(sx, sy, sz, state);
                }
            }
        }
    }

    private static void readTileEntities(Region r, int version, int offX, int offY, int offZ, Map<BlockPos, CompoundTag> tiles) {
        for (Tag entry : r.tag.getListOrEmpty("TileEntities")) {
            if (!(entry instanceof CompoundTag tile)) {
                continue;
            }
            // version 1 wraps the data in "TileNBT", with the position outside it
            CompoundTag data = (version == 1 ? tile.getCompoundOrEmpty("TileNBT") : tile).copy();
            int x = tile.getIntOr("x", -1);
            int y = tile.getIntOr("y", -1);
            int z = tile.getIntOr("z", -1);
            if (data.isEmpty() || x < 0 || y < 0 || z < 0 || x >= r.sizeX || y >= r.sizeY || z >= r.sizeZ) {
                continue;
            }
            data.remove("x");
            data.remove("y");
            data.remove("z");
            tiles.put(new BlockPos(offX + x, offY + y, offZ + z), data);
        }
    }

    /**
     * @author maruohon
     * Class from the Litematica mod by maruohon
     * Usage under LGPLv3 with the permission of the author.
     * <a href="https://github.com/maruohon/litematica">...</a>
     */
    private static class LitematicaBitArray {
        /**
         * The long array that is used to store the data for this BitArray.
         */
        private final long[] longArray;
        /**
         * Number of bits a single entry takes up
         */
        private final int bitsPerEntry;
        /**
         * The maximum value for a single entry. This also works as a bitmask for a single entry.
         * For instance, if bitsPerEntry were 5, this value would be 31 (ie, {@code 0b00011111}).
         */
        private final long maxEntryValue;
        /**
         * Number of entries in this array (<b>not</b> the length of the long array that internally backs this array)
         */
        private final long arraySize;

        public LitematicaBitArray(int bitsPerEntryIn, long arraySizeIn, long[] longArrayIn) {
            if (bitsPerEntryIn < 1 || bitsPerEntryIn > 32) {
                throw new IllegalArgumentException("bits per entry " + bitsPerEntryIn + " is outside 1 to 32");
            }
            this.arraySize = arraySizeIn;
            this.bitsPerEntry = bitsPerEntryIn;
            this.maxEntryValue = (1L << bitsPerEntryIn) - 1L;
            this.longArray = longArrayIn;
        }

        public int getAt(long index) {
            long startOffset = index * (long) this.bitsPerEntry;
            int startArrIndex = (int) (startOffset >> 6); // startOffset / 64
            int endArrIndex = (int) (((index + 1L) * (long) this.bitsPerEntry - 1L) >> 6);
            int startBitOffset = (int) (startOffset & 0x3F); // startOffset % 64

            if (startArrIndex == endArrIndex) {
                return (int) (this.longArray[startArrIndex] >>> startBitOffset & this.maxEntryValue);
            } else {
                int endOffset = 64 - startBitOffset;
                return (int) ((this.longArray[startArrIndex] >>> startBitOffset | this.longArray[endArrIndex] << endOffset) & this.maxEntryValue);
            }
        }

        public long size() {
            return this.arraySize;
        }
    }
}
