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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Vanilla structure files (".nbt"), as saved by structure blocks. Positions the file does not list are structure
 * voids and are not part of the schematic.
 */
public final class StructureSchematic extends LoadedSchematic {

    /**
     * What the game assumes for structure files without a data version.
     */
    private static final int UNVERSIONED = 500;

    public StructureSchematic(CompoundTag file) {
        int current = BlockStateReader.currentDataVersion();
        this.dataVersion = file.getIntOr("DataVersion", UNVERSIONED);
        CompoundTag nbt = this.dataVersion < current
            ? DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), file, this.dataVersion)
            : file;
        this.format = "Structure";

        ListTag size = nbt.getListOrEmpty("size");
        if (size.size() != 3) {
            throw new IllegalArgumentException("it has no size");
        }
        int sx = size.getIntOr(0, 0), sy = size.getIntOr(1, 0), sz = size.getIntOr(2, 0);
        allocate(sx, sy, sz, true);

        ListTag paletteTag = nbt.getList("palette")
            .or(() -> nbt.getListOrEmpty("palettes").getList(0))
            .orElseThrow(() -> new IllegalArgumentException("it has no block palette"));
        BlockStateReader reader = new BlockStateReader(current);
        BlockState[] palette = new BlockState[paletteTag.size()];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = reader.read(paletteTag.getCompoundOrEmpty(i));
        }

        for (Tag entry : nbt.getListOrEmpty("blocks")) {
            if (!(entry instanceof CompoundTag block)) {
                continue;
            }
            ListTag pos = block.getListOrEmpty("pos");
            int state = block.getIntOr("state", -1);
            if (pos.size() != 3 || state < 0 || state >= palette.length) {
                continue;
            }
            int x = pos.getIntOr(0, -1), y = pos.getIntOr(1, -1), z = pos.getIntOr(2, -1);
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz
                || palette[state].is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            setBlock(x, y, z, palette[state]);
            block.getCompound("nbt").ifPresent(tile -> putBlockEntity(new BlockPos(x, y, z), tile.copy()));
        }
        reader.warnings().forEach(this::warn);
    }
}
