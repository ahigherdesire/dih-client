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

package baritone.command.defaults;

import java.util.Locale;

/**
 * How each structure is drawn on the seed map: colour, marker shape, marker size and a readable
 * name. Keyed by structure id path (e.g. {@code village_desert}), so variants share a family look.
 */
public final class StructureStyle {

    public static final int SHAPE_SQUARE = 0;
    public static final int SHAPE_DIAMOND = 1;
    public static final int SHAPE_CIRCLE = 2;
    public static final int SHAPE_TRIANGLE = 3;

    /** Packed 0xRRGGBB. */
    public final int rgb;
    public final int shape;
    /** Marker half-size in blocks — rarer structures get bigger markers. */
    public final int radius;
    /** Rare, high-value structures; eligible for auto-waypoints. */
    public final boolean landmark;
    public final String family;
    /** Vanilla texture path (minecraft namespace) used as the JourneyMap marker icon. */
    public final String icon;
    /** Pixel size of {@link #icon}'s texture (8 for map decorations, 16 for items). */
    public final int iconTex;

    private StructureStyle(String family, int rgb, int shape, int radius, boolean landmark) {
        this(family, rgb, shape, radius, landmark, "textures/map/decorations/target_point.png", 8);
    }

    private StructureStyle(String family, int rgb, int shape, int radius, boolean landmark, String icon, int iconTex) {
        this.family = family;
        this.rgb = rgb;
        this.shape = shape;
        this.radius = radius;
        this.landmark = landmark;
        this.icon = icon;
        this.iconTex = iconTex;
    }

    private StructureStyle icon(String path, int tex) {
        return new StructureStyle(family, rgb, shape, radius, landmark, path, tex);
    }

    private static final String DECO = "textures/map/decorations/";
    private static final String ITEM = "textures/item/";

    public static StructureStyle of(String id) {
        String p = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (p.contains("stronghold"))      return new StructureStyle("Stronghold",      0x7E57C2, SHAPE_DIAMOND, 40, true).icon(ITEM + "ender_eye.png", 16);
        if (p.contains("mansion"))         return new StructureStyle("Woodland Mansion", 0x5D4037, SHAPE_DIAMOND, 40, true).icon(DECO + "woodland_mansion.png", 8);
        if (p.contains("ancient_city"))    return new StructureStyle("Ancient City",    0x1A237E, SHAPE_DIAMOND, 36, true).icon(ITEM + "echo_shard.png", 16);
        if (p.contains("trial_chamber"))   return new StructureStyle("Trial Chambers",  0xE08A2E, SHAPE_DIAMOND, 30, true).icon(DECO + "trial_chambers.png", 8);
        if (p.contains("monument"))        return new StructureStyle("Ocean Monument",  0x00838F, SHAPE_DIAMOND, 30, true).icon(DECO + "ocean_monument.png", 8);
        if (p.contains("end_city"))        return new StructureStyle("End City",        0xC5A3FF, SHAPE_DIAMOND, 28, true).icon(ITEM + "shulker_shell.png", 16);
        if (p.contains("fortress"))        return new StructureStyle("Nether Fortress", 0xB71C1C, SHAPE_DIAMOND, 30, true).icon(ITEM + "nether_brick.png", 16);
        if (p.contains("bastion"))         return new StructureStyle("Bastion Remnant", 0x424242, SHAPE_DIAMOND, 28, true).icon(ITEM + "gold_ingot.png", 16);
        if (p.contains("village")) {
            StructureStyle v = new StructureStyle("Village", 0x43A047, SHAPE_CIRCLE, 26, false);
            if (p.contains("desert"))  return v.icon(DECO + "desert_village.png", 8);
            if (p.contains("savanna")) return v.icon(DECO + "savanna_village.png", 8);
            if (p.contains("snowy"))   return v.icon(DECO + "snowy_village.png", 8);
            if (p.contains("taiga"))   return v.icon(DECO + "taiga_village.png", 8);
            return v.icon(DECO + "plains_village.png", 8);
        }
        if (p.contains("pillager_outpost"))return new StructureStyle("Pillager Outpost",0x9E9E9E, SHAPE_TRIANGLE, 20, false).icon(ITEM + "crossbow_standby.png", 16);
        if (p.contains("desert_pyramid"))  return new StructureStyle("Desert Temple",   0xE0C36A, SHAPE_TRIANGLE, 18, false).icon("textures/block/chiseled_sandstone.png", 16);
        if (p.contains("jungle"))          return new StructureStyle("Jungle Temple",   0x2E7D32, SHAPE_TRIANGLE, 18, false).icon(DECO + "jungle_temple.png", 8);
        if (p.contains("swamp_hut"))       return new StructureStyle("Witch Hut",       0x6D4C41, SHAPE_TRIANGLE, 16, false).icon(DECO + "swamp_hut.png", 8);
        if (p.contains("igloo"))           return new StructureStyle("Igloo",           0xB3E5FC, SHAPE_CIRCLE, 14, false).icon(ITEM + "snowball.png", 16);
        if (p.contains("trail_ruins"))     return new StructureStyle("Trail Ruins",     0xA1887F, SHAPE_CIRCLE, 16, false).icon(ITEM + "brush.png", 16);
        if (p.contains("ruined_portal"))   return new StructureStyle("Ruined Portal",   0x8E24AA, SHAPE_SQUARE, 12, false).icon(ITEM + "flint_and_steel.png", 16);
        // 26.3: one per biome (abandoned_camp_forest, abandoned_camp_taiga, ...).
        if (p.contains("abandoned_camp"))  return new StructureStyle("Abandoned Camp",  0x8D6E63, SHAPE_CIRCLE, 12, false).icon(ITEM + "campfire.png", 16);
        if (p.contains("shipwreck"))       return new StructureStyle("Shipwreck",       0x8D6E63, SHAPE_SQUARE, 12, false).icon(ITEM + "oak_boat.png", 16);
        if (p.contains("ocean_ruin"))      return new StructureStyle("Ocean Ruin",      0x26C6DA, SHAPE_SQUARE, 12, false).icon(ITEM + "prismarine_shard.png", 16);
        if (p.contains("buried_treasure")) return new StructureStyle("Buried Treasure", 0xFFD700, SHAPE_SQUARE, 8, false).icon(DECO + "red_x.png", 8);
        if (p.contains("mineshaft"))       return new StructureStyle("Mineshaft",       0x757575, SHAPE_SQUARE, 10, false).icon(ITEM + "minecart.png", 16);
        if (p.contains("nether_fossil"))   return new StructureStyle("Nether Fossil",   0xD7CCC8, SHAPE_SQUARE, 8, false).icon(ITEM + "bone.png", 16);
        return new StructureStyle(pretty(p), 0xFF00FF, SHAPE_SQUARE, 12, false);
    }

    /** {@code "village_snowy"} → {@code "Village Snowy"}. */
    public static String pretty(String id) {
        if (id == null || id.isEmpty()) return "?";
        String[] parts = id.split("[_/]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
