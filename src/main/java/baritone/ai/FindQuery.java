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

package baritone.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The pure half of the {@code find} tool: guessing ids from loose text and describing where
 * something is. Kept apart from {@link WorldFinder} so it loads without the game or Baritone.
 */
final class FindQuery {

    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private FindQuery() {}

    /**
     * Ids to try, best first: the text as an id, then without a plural "s"/"es", then as an ore
     * ("diamonds" → diamond → diamond_ore). Empty for blank input.
     */
    static List<String> candidates(String raw) {
        if (raw == null) {
            return List.of();
        }
        String id = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (id.isEmpty()) {
            return List.of();
        }
        String namespace = "";
        String path = id;
        int colon = id.indexOf(':');
        if (colon >= 0) {
            namespace = id.substring(0, colon + 1);
            path = id.substring(colon + 1);
        }
        Set<String> paths = new LinkedHashSet<>();
        paths.add(path);
        if (path.endsWith("ies") && path.length() > 4) {
            paths.add(path.substring(0, path.length() - 3) + "y");
        }
        if (path.endsWith("es") && path.length() > 3) {
            paths.add(path.substring(0, path.length() - 2));
        }
        if (path.endsWith("s") && path.length() > 2) {
            paths.add(path.substring(0, path.length() - 1));
        }
        for (String base : new ArrayList<>(paths)) {
            if (!base.endsWith("_ore")) {
                paths.add(base + "_ore");
            }
        }
        List<String> out = new ArrayList<>();
        for (String candidate : paths) {
            out.add(namespace + candidate);
        }
        return out;
    }

    /** The deepslate twin of an ore and vice versa ("iron_ore" ↔ "deepslate_iron_ore"). */
    static List<String> oreVariants(String path) {
        if (!path.endsWith("_ore")) {
            return List.of();
        }
        return List.of(path.startsWith("deepslate_") ? path.substring("deepslate_".length()) : "deepslate_" + path);
    }

    /** 8-point compass heading for an offset. Minecraft's north is -Z and east is +X. */
    static String compass(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return "here";
        }
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int index = (int) Math.round(degrees / 45.0);
        return COMPASS[Math.floorMod(index, 8)];
    }

    /** "14m NE, 9 below". Height is mentioned from 2 blocks up. */
    static String relative(int dx, int dy, int dz) {
        String heading = compass(dx, dz);
        if (heading.equals("here")) {
            return dy == 0 ? "right here" : Math.abs(dy) + "m straight " + (dy > 0 ? "up" : "down");
        }
        long distance = Math.round(Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz));
        StringBuilder sb = new StringBuilder();
        sb.append(distance).append("m ").append(heading);
        if (Math.abs(dy) >= 2) {
            sb.append(", ").append(Math.abs(dy)).append(dy > 0 ? " above" : " below");
        }
        return sb.toString();
    }
}
