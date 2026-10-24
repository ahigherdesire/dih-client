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

package baritone.utils.schematic.format;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.BlockStateData;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the block states a schematic file stores into this version's block states.
 * <p>
 * Every state is first run through the game's own data fixer, from the version the file was saved with, so blocks
 * renamed or reshaped since (grass to short_grass, the 1.13 flattening, ...) come out as they are today. Pre-1.13
 * numeric ids go through {@link BlockStateData}, the table the game itself used to convert old worlds. Unknown blocks
 * load as air, and bad property values are dropped, each with a warning rather than an error.
 */
public final class BlockStateReader {

    /**
     * The data version {@link BlockStateData}'s flattened states belong to (the 1.13 flattening).
     */
    public static final int FLATTENING = 1451;
    /**
     * The version assumed for files that record none, as Litematica does: 1.12.
     */
    public static final int DEFAULT_LEGACY = 1139;
    /**
     * The version assumed for block entities with pre-1.11 ids such as {@code Chest}.
     */
    public static final int OLDEST = 99;
    /**
     * From this data version (26.3) a state's fields are "id" and "properties" rather than "Name" and "Properties".
     */
    public static final int FIELDS_RENAMED = 5006;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /**
     * What {@link BlockStateData} names 1.12 skulls.
     */
    private static final String SKULL_PLACEHOLDER = "%%FILTER_ME%%";
    private static final int MAX_LISTED = 8;

    private final int dataVersion;
    private final int current;
    private final Map<CompoundTag, BlockState> cache = new HashMap<>();
    private final BlockState[] legacy = new BlockState[256 << 4];
    private final Set<String> unknown = new LinkedHashSet<>();
    private final Set<String> problems = new LinkedHashSet<>();

    /**
     * @param dataVersion the data version the file's states were saved with
     */
    public BlockStateReader(int dataVersion) {
        this.dataVersion = dataVersion;
        this.current = currentDataVersion();
    }

    public static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    /**
     * @param tag a state as {@code {Name: "minecraft:stone", Properties: {...}}}
     */
    public BlockState read(CompoundTag tag) {
        BlockState state = this.cache.get(tag);
        if (state == null) {
            state = resolve(skull(fix(withFieldsOf(tag, this.dataVersion), this.dataVersion)));
            this.cache.put(tag.copy(), state);
        }
        return state;
    }

    /**
     * @param spec a state as {@code minecraft:oak_stairs[facing=east,half=top]}
     */
    public BlockState read(String spec) {
        return read(parse(spec));
    }

    /**
     * @param id   a pre-1.13 numeric block id, 0 to 255
     * @param meta its data value, 0 to 15
     */
    public BlockState legacy(int id, int meta) {
        if (id < 0 || id > 255) {
            this.unknown.add("legacy id " + id);
            return AIR;
        }
        int key = id << 4 | (meta & 15);
        BlockState state = this.legacy[key];
        if (state == null) {
            if (id != 0 && "minecraft:air".equals(BlockStateData.upgradeBlock(id << 4))) {
                this.unknown.add("legacy id " + id);
                state = AIR;
            } else {
                Tag flattened = BlockStateData.getTag(key).convert(NbtOps.INSTANCE).getValue();
                state = flattened instanceof CompoundTag tag ? resolve(skull(fix(tag, FLATTENING))) : AIR;
            }
            this.legacy[key] = state;
        }
        return state;
    }

    /**
     * Upgrades block entity NBT saved with {@code fromVersion} to this version.
     */
    public CompoundTag blockEntity(CompoundTag tag, int fromVersion) {
        if (fromVersion >= this.current) {
            return tag;
        }
        try {
            Tag fixed = DataFixers.getDataFixer()
                .update(References.BLOCK_ENTITY, new Dynamic<>(NbtOps.INSTANCE, tag), fromVersion, this.current)
                .getValue();
            return fixed instanceof CompoundTag c ? c : tag;
        } catch (RuntimeException e) {
            this.problems.add("block entity " + tag.getStringOr("id", "?") + " kept as saved (" + e.getMessage() + ")");
            return tag;
        }
    }

    /**
     * Notes a block that is not in this version, to be listed in {@link #warnings()}.
     */
    public void unknown(String name) {
        this.unknown.add(name);
    }

    public void problem(String problem) {
        this.problems.add(problem);
    }

    /**
     * @return what was lost while reading, in a few lines
     */
    public List<String> warnings() {
        List<String> out = new ArrayList<>();
        if (!this.unknown.isEmpty()) {
            out.add("Unknown blocks loaded as air: " + list(this.unknown));
        }
        int listed = 0;
        for (String problem : this.problems) {
            if (listed++ == MAX_LISTED) {
                out.add("... and " + (this.problems.size() - MAX_LISTED) + " more problems");
                break;
            }
            out.add(problem);
        }
        return out;
    }

    private static String list(Set<String> items) {
        List<String> shown = new ArrayList<>();
        for (String item : items) {
            if (shown.size() == MAX_LISTED) {
                break;
            }
            shown.add(item);
        }
        String text = String.join(", ", shown);
        return items.size() > MAX_LISTED ? text + " and " + (items.size() - MAX_LISTED) + " more" : text;
    }

    private CompoundTag fix(CompoundTag tag, int fromVersion) {
        if (fromVersion >= this.current) {
            return tag;
        }
        try {
            Tag fixed = DataFixers.getDataFixer()
                .update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, tag), fromVersion, this.current)
                .getValue();
            return fixed instanceof CompoundTag c ? c : tag;
        } catch (RuntimeException e) {
            this.problems.add("could not upgrade " + nameOf(tag) + " (" + e.getMessage() + ")");
            return tag;
        }
    }

    private BlockState resolve(CompoundTag tag) {
        String name = nameOf(tag);
        Identifier id = Identifier.tryParse(name);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            if (name.isEmpty()) {
                this.problems.add("a palette entry names no block");
            } else {
                this.unknown.add(name);
            }
            return AIR;
        }
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        BlockState state = block.defaultBlockState();
        CompoundTag properties = legacyProperties(propertiesOf(tag));
        for (String key : properties.keySet()) {
            Property<?> property = block.getStateDefinition().getProperty(key);
            String value = properties.get(key).asString().orElse("");
            if (property == null) {
                this.problems.add(name + " has no property '" + key + "', ignored it");
                continue;
            }
            state = withValue(state, property, value, name);
        }
        return state;
    }

    /**
     * @return the block name of a state tag in either field layout
     */
    public static String nameOf(CompoundTag tag) {
        return tag.getString("id").orElseGet(() -> tag.getStringOr("Name", ""));
    }

    public static CompoundTag propertiesOf(CompoundTag tag) {
        return tag.getCompound("properties").orElseGet(() -> tag.getCompoundOrEmpty("Properties"));
    }

    /**
     * @return the tag in the field layout the data fixer expects at {@code version}
     */
    private static CompoundTag withFieldsOf(CompoundTag tag, int version) {
        boolean renamed = version >= FIELDS_RENAMED;
        String nameKey = renamed ? "id" : "Name";
        if (tag.contains(nameKey)) {
            return tag;
        }
        CompoundTag out = new CompoundTag();
        out.putString(nameKey, nameOf(tag));
        CompoundTag properties = propertiesOf(tag);
        if (!properties.isEmpty()) {
            out.put(renamed ? "properties" : "Properties", properties.copy());
        }
        return out;
    }

    /**
     * The game's table leaves skulls as a placeholder: their type and rotation were in the tile entity, which
     * {@link LegacyStateFixer} reads. Until then they are skeleton skulls, on the wall when they face sideways.
     */
    private static CompoundTag skull(CompoundTag tag) {
        if (!SKULL_PLACEHOLDER.equals(nameOf(tag))) {
            return tag;
        }
        String facing = propertiesOf(tag).getStringOr("facing", "up");
        CompoundTag out = new CompoundTag();
        if (facing.equals("up") || facing.equals("down")) {
            out.putString("Name", "minecraft:skeleton_skull");
        } else {
            out.putString("Name", "minecraft:skeleton_wall_skull");
            CompoundTag properties = new CompoundTag();
            properties.putString("facing", facing);
            out.put("Properties", properties);
        }
        return out;
    }

    /**
     * 1.12 leaves kept "decayable" and "check_decay", which the game only converts inside chunks (LeavesFix):
     * leaves that do not decay are persistent.
     */
    private static CompoundTag legacyProperties(CompoundTag properties) {
        if (!properties.contains("decayable") && !properties.contains("check_decay")) {
            return properties;
        }
        CompoundTag out = properties.copy();
        out.remove("check_decay");
        if (out.contains("decayable")) {
            out.putString("persistent", String.valueOf("false".equals(out.get("decayable").asString().orElse("true"))));
            out.remove("decayable");
        }
        return out;
    }

    private <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value, String name) {
        Optional<T> parsed = property.getValue(value);
        if (parsed.isEmpty()) {
            this.problems.add(name + " has no " + property.getName() + "=" + value + ", used the default");
            return state;
        }
        return state.setValue(property, parsed.get());
    }

    /**
     * @param spec {@code namespace:block[key=value,...]}
     * @return the same state as a {@code {Name, Properties}} tag
     */
    public static CompoundTag parse(String spec) {
        CompoundTag tag = new CompoundTag();
        String trimmed = spec.trim();
        int open = trimmed.indexOf('[');
        if (open < 0 || !trimmed.endsWith("]")) {
            tag.putString("Name", trimmed);
            return tag;
        }
        tag.putString("Name", trimmed.substring(0, open));
        CompoundTag properties = new CompoundTag();
        String body = trimmed.substring(open + 1, trimmed.length() - 1);
        for (String pair : body.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                properties.putString(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        if (!properties.isEmpty()) {
            tag.put("Properties", properties);
        }
        return tag;
    }
}
