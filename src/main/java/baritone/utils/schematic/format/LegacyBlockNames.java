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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The 1.12 block registry: numeric id to name. Schematica and MCEdit-Unified files map their ids to these names, so a
 * file saved with a remapped id table can be read back with the vanilla ids.
 */
public final class LegacyBlockNames {

    private static final String[] NAMES = {
        "air", "stone", "grass", "dirt", "cobblestone", "planks", "sapling", "bedrock",
        "flowing_water", "water", "flowing_lava", "lava", "sand", "gravel", "gold_ore", "iron_ore",
        "coal_ore", "log", "leaves", "sponge", "glass", "lapis_ore", "lapis_block", "dispenser",
        "sandstone", "noteblock", "bed", "golden_rail", "detector_rail", "sticky_piston", "web", "tallgrass",
        "deadbush", "piston", "piston_head", "wool", "piston_extension", "yellow_flower", "red_flower", "brown_mushroom",
        "red_mushroom", "gold_block", "iron_block", "double_stone_slab", "stone_slab", "brick_block", "tnt", "bookshelf",
        "mossy_cobblestone", "obsidian", "torch", "fire", "mob_spawner", "oak_stairs", "chest", "redstone_wire",
        "diamond_ore", "diamond_block", "crafting_table", "wheat", "farmland", "furnace", "lit_furnace", "standing_sign",
        "wooden_door", "ladder", "rail", "stone_stairs", "wall_sign", "lever", "stone_pressure_plate", "iron_door",
        "wooden_pressure_plate", "redstone_ore", "lit_redstone_ore", "unlit_redstone_torch", "redstone_torch", "stone_button", "snow_layer", "ice",
        "snow", "cactus", "clay", "reeds", "jukebox", "fence", "pumpkin", "netherrack",
        "soul_sand", "glowstone", "portal", "lit_pumpkin", "cake", "unpowered_repeater", "powered_repeater", "stained_glass",
        "trapdoor", "monster_egg", "stonebrick", "brown_mushroom_block", "red_mushroom_block", "iron_bars", "glass_pane", "melon_block",
        "pumpkin_stem", "melon_stem", "vine", "fence_gate", "brick_stairs", "stone_brick_stairs", "mycelium", "waterlily",
        "nether_brick", "nether_brick_fence", "nether_brick_stairs", "nether_wart", "enchanting_table", "brewing_stand", "cauldron", "end_portal",
        "end_portal_frame", "end_stone", "dragon_egg", "redstone_lamp", "lit_redstone_lamp", "double_wooden_slab", "wooden_slab", "cocoa",
        "sandstone_stairs", "emerald_ore", "ender_chest", "tripwire_hook", "tripwire", "emerald_block", "spruce_stairs", "birch_stairs",
        "jungle_stairs", "command_block", "beacon", "cobblestone_wall", "flower_pot", "carrots", "potatoes", "wooden_button",
        "skull", "anvil", "trapped_chest", "light_weighted_pressure_plate", "heavy_weighted_pressure_plate", "unpowered_comparator", "powered_comparator", "daylight_detector",
        "redstone_block", "quartz_ore", "hopper", "quartz_block", "quartz_stairs", "activator_rail", "dropper", "stained_hardened_clay",
        "stained_glass_pane", "leaves2", "log2", "acacia_stairs", "dark_oak_stairs", "slime", "barrier", "iron_trapdoor",
        "prismarine", "sea_lantern", "hay_block", "carpet", "hardened_clay", "coal_block", "packed_ice", "double_plant",
        "standing_banner", "wall_banner", "daylight_detector_inverted", "red_sandstone", "red_sandstone_stairs", "double_stone_slab2", "stone_slab2", "spruce_fence_gate",
        "birch_fence_gate", "jungle_fence_gate", "dark_oak_fence_gate", "acacia_fence_gate", "spruce_fence", "birch_fence", "jungle_fence", "dark_oak_fence",
        "acacia_fence", "spruce_door", "birch_door", "jungle_door", "acacia_door", "dark_oak_door", "end_rod", "chorus_plant",
        "chorus_flower", "purpur_block", "purpur_pillar", "purpur_stairs", "purpur_double_slab", "purpur_slab", "end_bricks", "beetroots",
        "grass_path", "end_gateway", "repeating_command_block", "chain_command_block", "frosted_ice", "magma", "nether_wart_block", "red_nether_brick",
        "bone_block", "structure_void", "observer", "white_shulker_box", "orange_shulker_box", "magenta_shulker_box", "light_blue_shulker_box", "yellow_shulker_box",
        "lime_shulker_box", "pink_shulker_box", "gray_shulker_box", "silver_shulker_box", "cyan_shulker_box", "purple_shulker_box", "blue_shulker_box", "brown_shulker_box",
        "green_shulker_box", "red_shulker_box", "black_shulker_box", "white_glazed_terracotta", "orange_glazed_terracotta", "magenta_glazed_terracotta", "light_blue_glazed_terracotta", "yellow_glazed_terracotta",
        "lime_glazed_terracotta", "pink_glazed_terracotta", "gray_glazed_terracotta", "silver_glazed_terracotta", "cyan_glazed_terracotta", "purple_glazed_terracotta", "blue_glazed_terracotta", "brown_glazed_terracotta",
        "green_glazed_terracotta", "red_glazed_terracotta", "black_glazed_terracotta", "concrete", "concrete_powder", null, null, "structure_block",
    };

    private static final Map<String, Integer> IDS = new HashMap<>();

    static {
        for (int id = 0; id < NAMES.length; id++) {
            if (NAMES[id] != null) {
                IDS.put("minecraft:" + NAMES[id], id);
            }
        }
    }

    private LegacyBlockNames() {}

    /**
     * @return the 1.12 name of a numeric block id, namespaced, or null if 1.12 had no block with that id
     */
    public static String name(int id) {
        return id >= 0 && id < NAMES.length && NAMES[id] != null ? "minecraft:" + NAMES[id] : null;
    }

    /**
     * @param name a 1.12 block name, with or without the {@code minecraft:} namespace
     * @return its numeric id, or -1 if 1.12 had no such block
     */
    public static int id(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (key.indexOf(':') < 0) {
            key = "minecraft:" + key;
        }
        return IDS.getOrDefault(key, -1);
    }

    public static int size() {
        return NAMES.length;
    }
}
