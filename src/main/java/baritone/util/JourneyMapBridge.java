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

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalXZ;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.display.Displayable;
import journeymap.api.v2.client.display.IOverlayListener;
import journeymap.api.v2.client.display.MarkerOverlay;
import journeymap.api.v2.client.display.Overlay;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.fullscreen.ModPopupMenu;
import journeymap.api.v2.client.model.MapImage;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.Context;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The actual JourneyMap v2 API calls. Only reached after JourneyMap has initialised
 * {@link DihJourneyMapPlugin}, so every JourneyMap class referenced here is guaranteed present.
 *
 * <p>Do NOT reference this class from anywhere except {@link JourneyMapHelper} and the plugin.
 *
 * <p>Layers are tracked as lists of the {@link Displayable}s we showed and removed one by one, so
 * the heatmap and the seed map are independent (JourneyMap's {@code removeAll(modId)} would clear
 * both).
 */
final class JourneyMapBridge {

    /** Must equal our Fabric mod id; JourneyMap keys plugins and overlays by it. */
    static final String MOD_ID = "dih";

    private static final Logger LOG = LoggerFactory.getLogger("Dih/JourneyMap");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private static volatile IClientAPI api;

    private JourneyMapBridge() {}

    static void initialize(IClientAPI clientApi) {
        api = clientApi;
        subscribeUi();
        LOG.info("JourneyMap integration active (API {}).", IClientAPI.API_VERSION);
    }

    /** Logs each failure kind once; a broken JM call must never spam the log or crash the game. */
    static void warn(String what, Throwable t) {
        if (WARNED.add(what + ":" + t.getClass().getName())) {
            LOG.warn("JourneyMap call '{}' failed", what, t);
        }
    }

    // ── Layer state ──────────────────────────────────────────────────────────

    private static final List<Displayable> heatmapShown = new ArrayList<>();
    private static final List<Displayable> structuresShown = new ArrayList<>();

    private static volatile boolean heatmapVisible = false;
    private static volatile List<int[]> cachedCells = null;
    private static volatile int cachedMaxScore = 0;
    private static volatile ResourceKey<Level> cachedCellsDim = null;

    private static volatile boolean structuresVisible = false;
    /** Families hidden from the seed-map layer via the right-click menu (no rescan needed). */
    private static final Set<String> hiddenFamilies = ConcurrentHashMap.newKeySet();
    private static volatile List<JourneyMapHelper.StructureMarker> cachedStructures = null;
    private static volatile ResourceKey<Level> cachedStructuresDim = null;

    // ── Fullscreen UI: popup menu + toolbar toggles ──────────────────────────

    private static void subscribeUi() {
        FullscreenEventRegistry.FULLSCREEN_POPUP_MENU_EVENT.subscribe(MOD_ID, event -> {
            ModPopupMenu menu = event.getPopupMenu();
            menu.addMenuItem("Baritone: go here", pos -> gotoXZ(pos.getX(), pos.getZ()));
            menu.addMenuItem("Seed map around here", pos ->
                runCommand("seedmap at " + pos.getX() + " " + pos.getZ()));
            addLayerMenu(menu);
        });

        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(MOD_ID, event -> {
            var buttons = event.getThemeButtonDisplay();
            buttons.addThemeToggleButton("Seed map structures",
                Identifier.withDefaultNamespace("textures/item/filled_map.png"),
                structuresVisible,
                button -> {
                    button.toggle();
                    structuresVisible = Boolean.TRUE.equals(button.getToggled());
                    redrawStructures();
                });
            buttons.addThemeToggleButton("Activity heatmap",
                Identifier.withDefaultNamespace("textures/item/fire_charge.png"),
                heatmapVisible,
                button -> {
                    button.toggle();
                    heatmapVisible = Boolean.TRUE.equals(button.getToggled());
                    redrawHeatmap();
                });
        });
    }

    /** "Seed map layers ▸" submenu: one show/hide entry per structure family currently mapped. */
    private static void addLayerMenu(ModPopupMenu menu) {
        List<JourneyMapHelper.StructureMarker> markers = cachedStructures;
        if (markers == null || markers.isEmpty()) return;
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        for (JourneyMapHelper.StructureMarker m : markers) counts.merge(m.family(), 1, Integer::sum);
        ModPopupMenu layers = menu.createSubItemList("Seed map layers");
        for (var e : counts.entrySet()) {
            String family = e.getKey();
            boolean hidden = hiddenFamilies.contains(family);
            layers.addMenuItem((hidden ? "Show " : "Hide ") + family + " (" + e.getValue() + ")", pos -> {
                if (!hiddenFamilies.remove(family)) hiddenFamilies.add(family);
                structuresVisible = true;
                redrawStructures();
            });
        }
        if (!hiddenFamilies.isEmpty()) {
            layers.addMenuItem("Show all", pos -> {
                hiddenFamilies.clear();
                structuresVisible = true;
                redrawStructures();
            });
        }
    }

    private static void gotoXZ(int x, int z) {
        IBaritone bar = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (bar != null) bar.getCustomGoalProcess().setGoalAndPath(new GoalXZ(x, z));
    }

    private static void runCommand(String command) {
        IBaritone bar = BaritoneAPI.getProvider().getPrimaryBaritone();
        if (bar != null) bar.getCommandManager().execute(command);
    }

    // ── Waypoints ────────────────────────────────────────────────────────────

    static void addWaypoint(String name, BlockPos pos, int color) {
        IClientAPI jm = api;
        Minecraft mc = Minecraft.getInstance();
        if (jm == null || mc.level == null) return;
        BlockPos at = new BlockPos(pos.getX(), pos.getY() > mc.level.getMinY() ? pos.getY() : 64, pos.getZ());
        for (Waypoint existing : jm.getWaypoints(MOD_ID)) {
            if (name.equals(existing.getName()) && existing.getX() == at.getX() && existing.getZ() == at.getZ()) {
                return; // already saved (maybe in an earlier session)
            }
        }
        Waypoint wp = WaypointFactory.createWaypoint(MOD_ID, at, name, mc.level.dimension(), true);
        wp.setColor(color & 0xFFFFFF);
        jm.addWaypoint(MOD_ID, wp);
    }

    // ── Heatmap ──────────────────────────────────────────────────────────────

    static void showHeatmap(List<int[]> cells, int maxScore) {
        Minecraft mc = Minecraft.getInstance();
        cachedCells = cells;
        cachedMaxScore = maxScore;
        cachedCellsDim = mc.level == null ? null : mc.level.dimension();
        heatmapVisible = true;
        redrawHeatmap();
    }

    static void clearHeatmap() {
        heatmapVisible = false;
        redrawHeatmap();
    }

    private static void redrawHeatmap() {
        removeAll(heatmapShown);
        List<int[]> cells = cachedCells;
        ResourceKey<Level> dim = cachedCellsDim;
        int maxScore = cachedMaxScore;
        if (!heatmapVisible || cells == null || dim == null || maxScore <= 0) return;

        for (int[] cell : cells) {
            int cx = cell[0], cz = cell[1], score = cell[2];
            int rgb = heatColor(Math.min(1f, (float) score / maxScore));
            PolygonOverlay overlay = new PolygonOverlay(MOD_ID, dim,
                new ShapeProperties()
                    .setFillColor(rgb).setFillOpacity(0.42f)
                    .setStrokeColor(rgb).setStrokeOpacity(0.85f).setStrokeWidth(1f),
                square(cx, cz, 16));
            overlay.setTitle("Activity score " + score + "  (X " + cx + ", Z " + cz + ")");
            overlay.setOverlayGroupName("Dih heatmap");
            overlay.setDisplayOrder(800);
            show(overlay, heatmapShown);
        }
    }

    /** Blue (t=0) → yellow (t=0.5) → red (t=1), packed 0xRRGGBB. */
    private static int heatColor(float t) {
        int r, g, b;
        if (t < 0.5f) {
            float u = t * 2f;
            r = (int) (255 * u);
            g = (int) (255 * u);
            b = (int) (255 * (1f - u));
        } else {
            float u = (t - 0.5f) * 2f;
            r = 255;
            g = (int) (255 * (1f - u));
            b = 0;
        }
        return (r << 16) | (g << 8) | b;
    }

    // ── Seed map ─────────────────────────────────────────────────────────────

    static void showStructures(List<JourneyMapHelper.StructureMarker> markers, ResourceKey<Level> dim) {
        cachedStructures = markers;
        cachedStructuresDim = dim;
        structuresVisible = true;
        redrawStructures();
    }

    static void clearStructures() {
        structuresVisible = false;
        redrawStructures();
    }

    static void reset() {
        structuresVisible = false;
        heatmapVisible = false;
        hiddenFamilies.clear();
        cachedStructures = null;
        cachedStructuresDim = null;
        cachedCells = null;
        cachedCellsDim = null;
        removeAll(structuresShown);
        removeAll(heatmapShown);
    }

    private static void redrawStructures() {
        removeAll(structuresShown);
        List<JourneyMapHelper.StructureMarker> markers = cachedStructures;
        ResourceKey<Level> dim = cachedStructuresDim;
        if (!structuresVisible || markers == null || dim == null) return;

        for (JourneyMapHelper.StructureMarker m : markers) {
            if (hiddenFamilies.contains(m.family())) continue;
            String title = m.name() + "\nX " + m.x() + "  Y " + m.y() + "  Z " + m.z()
                + "\nRight-click → Baritone: go to";
            IOverlayListener listener = new StructureListener(m);

            // Footprint: the structure's real generated bounding box (villages sprawl ~100 blocks).
            int w = m.maxX() - m.minX(), d = m.maxZ() - m.minZ();
            if (w >= 12 && d >= 12 && w <= 512 && d <= 512) {
                PolygonOverlay footprint = new PolygonOverlay(MOD_ID, dim,
                    new ShapeProperties()
                        .setFillColor(m.rgb()).setFillOpacity(0.16f)
                        .setStrokeColor(m.rgb()).setStrokeOpacity(0.75f).setStrokeWidth(1.5f),
                    new MapPolygon(
                        new BlockPos(m.minX(), m.y(), m.minZ()),
                        new BlockPos(m.maxX() + 1, m.y(), m.minZ()),
                        new BlockPos(m.maxX() + 1, m.y(), m.maxZ() + 1),
                        new BlockPos(m.minX(), m.y(), m.maxZ() + 1)));
                footprint.setTitle(title);
                footprint.setOverlayGroupName("Dih seed map");
                footprint.setDisplayOrder(900);
                footprint.setActiveUIs(Context.UI.Fullscreen);
                footprint.setOverlayListener(listener);
                show(footprint, structuresShown);
            }

            // Icon: fixed screen size like mcseedmap, vanilla explorer-map / item textures.
            int size = m.landmark() ? 20 : 15;
            MapImage icon = new MapImage(Identifier.withDefaultNamespace(m.icon()), m.iconTex(), m.iconTex());
            icon.setDisplayWidth(size);
            icon.setDisplayHeight(size);
            icon.centerAnchors();
            MarkerOverlay marker = new MarkerOverlay(MOD_ID, new BlockPos(m.x(), m.y(), m.z()), icon);
            marker.setDimension(dim);
            marker.setTitle(title);
            marker.setOverlayGroupName("Dih seed map");
            marker.setDisplayOrder(1000);
            marker.setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap);
            marker.setOverlayListener(listener);
            if (m.landmark()) {
                marker.setLabel(m.name());
                marker.setTextProperties(new TextProperties()
                    .setScale(1.0f)
                    .setColor(0xFFFFFF)
                    .setBackgroundColor(0x000000)
                    .setBackgroundOpacity(0.55f)
                    .setOffsetY(14)
                    .setActiveUIs(Context.UI.Fullscreen));
            }
            show(marker, structuresShown);
        }
    }

    /** Right-click a structure on the fullscreen map → path to it. */
    private record StructureListener(JourneyMapHelper.StructureMarker marker) implements IOverlayListener {
        @Override
        public void onOverlayMenuPopup(UIState state, Point2D.Double mousePos, BlockPos blockPos, ModPopupMenu menu) {
            menu.addMenuItem("Baritone: go to " + marker.name(), pos -> gotoXZ(marker.x(), marker.z()));
            menu.addMenuItem("Add waypoint: " + marker.name(), pos ->
                addWaypoint(marker.name(), new BlockPos(marker.x(), marker.y(), marker.z()), marker.rgb()));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static MapPolygon square(int cx, int cz, int half) {
        return new MapPolygon(
            new BlockPos(cx - half, 64, cz - half),
            new BlockPos(cx + half, 64, cz - half),
            new BlockPos(cx + half, 64, cz + half),
            new BlockPos(cx - half, 64, cz + half));
    }

    private static void show(Overlay overlay, List<Displayable> layer) {
        IClientAPI jm = api;
        if (jm == null) return;
        try {
            jm.show(overlay);
            layer.add(overlay);
        } catch (Throwable t) {
            warn("show", t);
        }
    }

    private static void removeAll(List<Displayable> layer) {
        IClientAPI jm = api;
        if (jm == null) {
            layer.clear();
            return;
        }
        for (Displayable d : layer) {
            try {
                jm.remove(d);
            } catch (Throwable t) {
                warn("remove", t);
            }
        }
        layer.clear();
    }
}
