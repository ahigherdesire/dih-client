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

package baritone.utils.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A schematic read from a file, whatever its format: block states, block entity NBT, bounds and metadata.
 * <p>
 * Positions are relative to the schematic's minimum corner. Cells outside every region of a multi-region file (and
 * structure voids of vanilla structure files) are not part of the schematic: {@link #inSchematic} is false there, so
 * the builder leaves those blocks alone, and they read as air.
 */
public class LoadedSchematic extends StaticSchematic {

    /**
     * The most cells one schematic may hold. Every cell keeps a reference, so this bounds the memory a file can take.
     */
    public static final long MAX_VOLUME = 48L * 1024 * 1024;

    /**
     * A named part of the schematic. Litematica files can hold several; every other format has one.
     */
    public record Region(String name, BlockPos min, Vec3i size) {}

    protected String name = "";
    protected String author = "";
    protected String description = "";
    protected String format = "";
    protected int dataVersion;

    private final Map<BlockPos, CompoundTag> blockEntities = new LinkedHashMap<>();
    private final List<Region> regions = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    /**
     * Which cells belong to the schematic, or null when all of them do.
     */
    private BitSet present;

    /**
     * Sizes the schematic and fills it with air.
     *
     * @param sparse whether cells start out absent and only become part of the schematic once set
     */
    protected final void allocate(int x, int y, int z, boolean sparse) {
        if (x < 0 || y < 0 || z < 0) {
            throw new IllegalArgumentException("negative size " + x + "x" + y + "x" + z);
        }
        long volume = (long) x * y * z;
        if (volume > MAX_VOLUME) {
            throw new IllegalArgumentException("too large: " + x + "x" + y + "x" + z + " is " + volume
                + " blocks, the limit is " + MAX_VOLUME);
        }
        this.x = x;
        this.y = y;
        this.z = z;
        BlockState air = Blocks.AIR.defaultBlockState();
        this.states = new BlockState[x][z][y];
        for (BlockState[][] plane : this.states) {
            for (BlockState[] column : plane) {
                Arrays.fill(column, air);
            }
        }
        this.present = sparse ? new BitSet((int) volume) : null;
    }

    protected final void setBlock(int x, int y, int z, BlockState state) {
        this.states[x][z][y] = state;
        if (this.present != null) {
            this.present.set(index(x, y, z));
        }
    }

    protected final void putBlockEntity(BlockPos pos, CompoundTag tag) {
        this.blockEntities.put(pos.immutable(), tag);
    }

    protected final void addRegion(String name, BlockPos min, Vec3i size) {
        this.regions.add(new Region(name, min.immutable(), size));
    }

    protected final void warn(String warning) {
        if (!this.warnings.contains(warning)) {
            this.warnings.add(warning);
        }
    }

    private int index(int x, int y, int z) {
        return (y * this.z + z) * this.x + x;
    }

    /**
     * Uses {@code fallback} as the name when the file itself names nothing.
     */
    public LoadedSchematic named(String fallback) {
        if (this.name == null || this.name.isBlank()) {
            this.name = fallback;
        }
        return this;
    }

    public String name() {
        return this.name;
    }

    public String author() {
        return this.author;
    }

    public String description() {
        return this.description;
    }

    /**
     * @return the file format, with its version, e.g. {@code "Litematica v7"}
     */
    public String format() {
        return this.format;
    }

    /**
     * @return the Minecraft data version the file was saved with, or 0 if it records none
     */
    public int dataVersion() {
        return this.dataVersion;
    }

    /**
     * @return block entity NBT by position; each tag holds its {@code id} and no position
     */
    public Map<BlockPos, CompoundTag> blockEntities() {
        return Collections.unmodifiableMap(this.blockEntities);
    }

    public CompoundTag blockEntity(int x, int y, int z) {
        return this.blockEntities.get(new BlockPos(x, y, z));
    }

    public List<Region> regions() {
        return Collections.unmodifiableList(this.regions);
    }

    /**
     * @return problems found while loading that did not stop it, such as unknown blocks read as air
     */
    public List<String> warnings() {
        return Collections.unmodifiableList(this.warnings);
    }

    /**
     * @return whether the position lies inside the bounds and belongs to the schematic
     */
    public boolean contains(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= this.x || y >= this.y || z >= this.z) {
            return false;
        }
        return this.present == null || this.present.get(index(x, y, z));
    }

    @Override
    public boolean inSchematic(int x, int y, int z, BlockState currentState) {
        return contains(x, y, z);
    }

    /**
     * @return how many cells of the schematic hold something other than air
     */
    public int blockCount() {
        int count = 0;
        for (int x = 0; x < this.x; x++) {
            for (int z = 0; z < this.z; z++) {
                for (int y = 0; y < this.y; y++) {
                    if (!this.states[x][z][y].isAir() && contains(x, y, z)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
