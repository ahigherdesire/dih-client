/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Optional JourneyMap integration.
 *
 * <p>This class has NO JourneyMap imports — it is always safe to load even when JourneyMap is
 * absent. All actual JM calls live in {@link JourneyMapBridge}, which is only touched once
 * JourneyMap has initialised {@link DihJourneyMapPlugin} (i.e. JM is installed and its API is
 * live), so a missing or incompatible JourneyMap can never crash the client.
 *
 * <pre>
 *   JourneyMapHelper.addWaypoint("Village", pos, JourneyMapHelper.COLOR_STRUCTURE);
 * </pre>
 */
public final class JourneyMapHelper {

    /** Gold — used for structure waypoints. */
    public static final int COLOR_STRUCTURE = 0xFFAA00;

    /** Purple — used for player base waypoints. */
    public static final int COLOR_BASE      = 0xAA00FF;

    /** Cyan — used for portal waypoints. */
    public static final int COLOR_PORTAL    = 0x00FFEE;

    private static volatile boolean ready = false;

    private JourneyMapHelper() {}

    /** Called by {@link DihJourneyMapPlugin} once JourneyMap hands us its API. */
    static void markReady() {
        ready = true;
    }

    /** {@code true} once JourneyMap is installed and has initialised our plugin. */
    public static boolean isAvailable() {
        return ready;
    }

    /** Short user-facing reason when {@link #isAvailable()} is false. */
    public static String unavailableReason() {
        boolean installed;
        try {
            installed = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("journeymap");
        } catch (Throwable t) {
            installed = false;
        }
        return installed
            ? "JourneyMap is installed but hasn't loaded its API yet (or is an incompatible version)."
            : "JourneyMap isn't installed — add the JourneyMap mod to see this on the map.";
    }

    /**
     * Kept for callers from the old lazy-subscribe design; subscriptions now happen when
     * JourneyMap initialises the plugin, so this is a no-op.
     */
    public static void ensureSubscribed() {
    }

    /**
     * Creates a persistent JourneyMap waypoint in the current dimension. Silently does nothing if
     * JourneyMap is not available. Must be called on the client thread.
     *
     * @param color packed 0xRRGGBB
     */
    public static void addWaypoint(String name, BlockPos pos, int color) {
        if (!ready) return;
        try {
            JourneyMapBridge.addWaypoint(name, pos, color);
        } catch (Throwable t) {
            JourneyMapBridge.warn("addWaypoint", t);
        }
    }

    /**
     * Heatmap overlay: one coloured cell per entry, blue (cold) → red (hot).
     *
     * @param cells    list of {centerX, centerZ, score}
     * @param maxScore score of the hottest cell for colour normalisation
     */
    public static void showHeatmap(List<int[]> cells, int maxScore) {
        if (!ready) return;
        try {
            JourneyMapBridge.showHeatmap(cells, maxScore);
        } catch (Throwable t) {
            JourneyMapBridge.warn("showHeatmap", t);
        }
    }

    /** Hides the heatmap (the [Heat] button can bring it back). */
    public static void clearHeatmap() {
        if (!ready) return;
        try {
            JourneyMapBridge.clearHeatmap();
        } catch (Throwable t) {
            JourneyMapBridge.warn("clearHeatmap", t);
        }
    }

    /**
     * One structure on the seed map. Plain data — no JourneyMap types — so commands can build it
     * whether or not JourneyMap is present.
     *
     * @param icon    vanilla texture path (minecraft namespace) for the map marker
     * @param iconTex pixel size of that texture
     */
    public record StructureMarker(String name, String family, int x, int y, int z,
                                  int minX, int minZ, int maxX, int maxZ,
                                  int rgb, String icon, int iconTex, boolean landmark) {
    }

    /**
     * Draws the seed map (icons + structure footprints) in {@code dim}, replacing any previous one.
     * The heatmap is left alone.
     */
    public static void showStructures(List<StructureMarker> markers, ResourceKey<Level> dim) {
        if (!ready) return;
        try {
            JourneyMapBridge.showStructures(markers, dim);
        } catch (Throwable t) {
            JourneyMapBridge.warn("showStructures", t);
        }
    }

    /**
     * Drops every overlay <i>and</i> its cached data — used when leaving a world, so a previous
     * server's structures or heatmap can never be toggled back on over a different world.
     */
    public static void reset() {
        if (!ready) return;
        try {
            JourneyMapBridge.reset();
        } catch (Throwable t) {
            JourneyMapBridge.warn("reset", t);
        }
    }

    /** Hides the seed map (the [Struct] button can bring it back). */
    public static void clearStructures() {
        if (!ready) return;
        try {
            JourneyMapBridge.clearStructures();
        } catch (Throwable t) {
            JourneyMapBridge.warn("clearStructures", t);
        }
    }
}
