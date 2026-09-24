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

import baritone.api.IBaritone;
import baritone.api.cache.ICachedWorld;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.utils.BetterBlockPos;
import baritone.util.JourneyMapHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Scores 512×512-block grid cells by the density of player-indicator blocks in
 * Baritone's chunk cache, then lists the hottest zones.
 *
 * <p>Different from {@link BasesCommand} which uses DBSCAN to find tight base
 * footprints. {@code #heatmap} uses a fixed grid to find broad activity zones —
 * useful for spotting heavily-travelled corridors, nether highway hubs, or the
 * general area of a base before narrowing down with {@code #bases}.
 *
 * <p>Includes nether portals, hoppers, and observers as "player was here" signals
 * in addition to the base-quality indicators used by {@code #bases}.
 *
 * <p>Pure cache reader — no packets, no world scan, no anticheat surface.
 *
 * <p>Subcommands:
 * <pre>
 *   #heatmap              top 10 hottest zones in current dimension
 *   #heatmap &lt;N&gt;          top N zones
 *   #heatmap &lt;N&gt; goto     path to the N'th hotspot via GoalXZ
 * </pre>
 */
public class HeatmapCommand extends Command {

    /**
     * Each grid cell covers CELL_SIZE × CELL_SIZE blocks in X/Z.
     * 32 = one chunk — gives per-chunk precision on the JourneyMap overlay.
     */
    private static final int CELL_SIZE = 32;

    /** Max cells drawn on the JM overlay. Capped to keep rendering fast. */
    private static final int MAX_OVERLAY_CELLS = 1000;

    /**
     * Block weights.
     * Higher weight = stronger signal of player activity.
     * Only blocks present in {@code CachedChunk.BLOCKS_TO_KEEP_TRACK_OF} are
     * queryable via {@code getLocationsOf} — anything else returns empty.
     */
    private static final Map<String, Integer> INDICATORS = new LinkedHashMap<>();
    static {
        // ── High-value base blocks ────────────────────────────────────────────
        INDICATORS.put("beacon",           50);
        INDICATORS.put("ender_chest",      30);
        INDICATORS.put("enchanting_table", 25);
        INDICATORS.put("brewing_stand",    15);
        INDICATORS.put("anvil",            15);

        // Shulker boxes — all 16 tracked colours
        for (String c : Arrays.asList("white", "orange", "magenta", "light_blue",
                "yellow", "lime", "pink", "gray", "light_gray", "cyan",
                "purple", "blue", "brown", "green", "red", "black")) {
            INDICATORS.put(c + "_shulker_box", 15);
        }

        // ── "Player was here / built here" signals ────────────────────────────
        INDICATORS.put("hopper",           10); // automation = someone built a farm/sorter
        INDICATORS.put("observer",          8); // redstone = someone built a contraption
        INDICATORS.put("nether_portal",     8); // transit hub or base entrance
        INDICATORS.put("jukebox",           6); // decorative = player cares about this place

        // ── Storage / crafting ────────────────────────────────────────────────
        INDICATORS.put("trapped_chest",     6);
        INDICATORS.put("chest",             3);
        INDICATORS.put("furnace",           3);

        // ── Beds ──────────────────────────────────────────────────────────────
        for (String c : Arrays.asList("white", "orange", "magenta", "light_blue",
                "yellow", "lime", "pink", "gray", "light_gray", "cyan",
                "purple", "blue", "brown", "green", "red", "black")) {
            INDICATORS.put(c + "_bed", 5);
        }

        // ── Very high-confidence signals ──────────────────────────────────────
        INDICATORS.put("dragon_egg",       100); // end spawn area, guaranteed
        INDICATORS.put("player_head",       20); // a player was killed here
        INDICATORS.put("player_wall_head",  20);
    }

    public HeatmapCommand(IBaritone baritone) {
        super(baritone, "heatmap", "activity");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (ctx.world() == null || ctx.worldData() == null) {
            throw new CommandInvalidStateException("No world loaded.");
        }

        int topN = 10;
        int gotoIndex = -1;

        if (args.hasAny()) {
            String first = args.getString().toLowerCase();
            try {
                topN = Integer.parseInt(first);
                if (topN < 1) throw new CommandInvalidStateException("N must be ≥ 1.");
            } catch (NumberFormatException e) {
                throw new CommandInvalidStateException(
                        "Unknown argument '" + first + "'. Usage: #heatmap [N] [goto]");
            }
            if (args.hasAny()) {
                String second = args.getString().toLowerCase();
                if (second.equals("goto")) {
                    gotoIndex = topN;
                    topN = Math.max(topN, 10);
                } else {
                    throw new CommandInvalidStateException(
                            "Unknown argument '" + second + "'. Did you mean 'goto'?");
                }
            }
        }
        args.requireMax(0);

        // Capture on the game thread before handing off.
        final BetterBlockPos origin   = ctx.playerFeet();
        final ICachedWorld   cache    = ctx.worldData().getCachedWorld();
        final int            finalTopN     = topN;
        final int            finalGotoIndex = gotoIndex;

        logDirect("Scanning cache for activity hotspots...");

        Thread worker = new Thread(() -> {

            // ── Pull every indicator from the cache, bucket into 512×512 cells ──
            // Math.floorDiv handles negative coordinates correctly
            // (Java's / truncates toward zero; -513 / 512 = -1 but -1 / 512 = 0,
            //  which puts -1 and -512 in the same cell — wrong. floorDiv fixes this.)
            Map<String, CellData> cells = new HashMap<>();

            for (Map.Entry<String, Integer> entry : INDICATORS.entrySet()) {
                String blockName = entry.getKey();
                int    weight    = entry.getValue();
                ArrayList<BlockPos> positions = cache.getLocationsOf(
                        blockName, Integer.MAX_VALUE, origin.x, origin.z, Integer.MAX_VALUE);
                for (BlockPos p : positions) {
                    int    cellX = Math.floorDiv(p.getX(), CELL_SIZE);
                    int    cellZ = Math.floorDiv(p.getZ(), CELL_SIZE);
                    String key   = cellX + "," + cellZ;
                    cells.computeIfAbsent(key, k -> new CellData(cellX, cellZ))
                         .add(blockName, weight);
                }
            }

            if (cells.isEmpty()) {
                Minecraft.getInstance().execute(() ->
                        logDirect("No tracked blocks found in cache for this dimension. "
                                + "Explore more chunks (elytra works well), then retry."));
                return;
            }

            // ── Sort by score ──────────────────────────────────────────────────
            List<CellData> sorted = new ArrayList<>(cells.values());
            sorted.sort(Comparator.comparingInt((CellData c) -> -c.score));

            int showCount = Math.min(finalTopN, sorted.size());
            List<String> lines = new ArrayList<>();
            lines.add("══ Activity heatmap — " + cells.size()
                    + " active zones, top " + showCount + " shown │ toggle: fire-charge button on the JourneyMap toolbar ══");

            for (int i = 0; i < showCount; i++) {
                CellData cell = sorted.get(i);
                int cx   = cell.centerX();
                int cz   = cell.centerZ();
                int dx   = cx - origin.x;
                int dz   = cz - origin.z;
                int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
                lines.add(String.format(
                        "%2d. score %5d │ X=%7d Z=%7d │ %3d blocks │ ~%6d away │ %s",
                        i + 1, cell.score, cx, cz, cell.totalCount, dist,
                        cell.topTypes(3)));
            }

            // Build overlay list for JM: ALL cells (up to MAX_OVERLAY_CELLS), not just top-N.
            // More cells = better heatmap coverage. Capped for rendering performance.
            int overlayCount = Math.min(sorted.size(), MAX_OVERLAY_CELLS);
            List<int[]> jmCells = new ArrayList<>(overlayCount);
            for (int i = 0; i < overlayCount; i++) {
                CellData cell = sorted.get(i);
                jmCells.add(new int[]{cell.centerX(), cell.centerZ(), cell.score});
            }
            final int jmMaxScore = sorted.isEmpty() ? 1 : sorted.get(0).score;

            final CellData gotoTarget = (finalGotoIndex > 0 && finalGotoIndex <= sorted.size())
                    ? sorted.get(finalGotoIndex - 1) : null;
            final int totalZones = sorted.size();

            Minecraft.getInstance().execute(() -> {
                for (String line : lines) logDirect(line);

                // Visual heatmap on JourneyMap — colored rectangles, blue→yellow→red by score
                JourneyMapHelper.showHeatmap(jmCells, jmMaxScore);

                // Also drop a pin waypoint at each hotspot centre (visible on minimap too)
                for (int i = 0; i < showCount; i++) {
                    CellData cell = sorted.get(i);
                    JourneyMapHelper.addWaypoint(
                            "Hotspot #" + (i + 1) + " (score " + cell.score + ")",
                            new BlockPos(cell.centerX(), 64, cell.centerZ()),
                            JourneyMapHelper.COLOR_BASE);
                }

                if (finalGotoIndex > 0) {
                    if (gotoTarget == null) {
                        logDirect("Only " + totalZones + " zone"
                                + (totalZones == 1 ? "" : "s") + " found. Cannot goto #"
                                + finalGotoIndex + ".");
                    } else {
                        int cx = gotoTarget.centerX();
                        int cz = gotoTarget.centerZ();
                        logDirect("→ Pathing to hotspot #" + finalGotoIndex
                                + " at X=" + cx + " Z=" + cz);
                        baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(cx, cz));
                    }
                }
            });

        }, "BaritoneHeatmap");
        worker.setDaemon(true);
        worker.start();
    }

    // ── Cell accumulator ────────────────────────────────────────────────────────

    private static final class CellData {
        final int cellX;
        final int cellZ;
        int score;
        int totalCount;
        final Map<String, Integer> typeCounts = new HashMap<>();

        CellData(int cellX, int cellZ) {
            this.cellX = cellX;
            this.cellZ = cellZ;
        }

        void add(String blockName, int weight) {
            score += weight;
            totalCount++;
            typeCounts.merge(canonicalGroup(blockName), 1, Integer::sum);
        }

        /** World-space center of this cell. */
        int centerX() { return cellX * CELL_SIZE + CELL_SIZE / 2; }
        int centerZ() { return cellZ * CELL_SIZE + CELL_SIZE / 2; }

        /** Top-N block type groups as a readable string. */
        String topTypes(int limit) {
            return typeCounts.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(limit)
                    .map(e -> e.getValue() + "× " + e.getKey())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("?");
        }
    }

    /** Collapse dyed variants to a readable group name. */
    private static String canonicalGroup(String name) {
        if (name.endsWith("_shulker_box")) return "shulker";
        if (name.endsWith("_bed"))         return "bed";
        if (name.equals("player_head") || name.equals("player_wall_head")) return "player_head";
        return name;
    }

    // ── Boilerplate ──────────────────────────────────────────────────────────────

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String p = "";
            try { p = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String pf = p;
            return Stream.of("5", "10", "20").filter(s -> s.startsWith(pf));
        }
        if (args.hasAny() && !args.hasExactlyOne()) {
            String p = "";
            try { p = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String pf = p;
            return Stream.of("goto").filter(s -> s.startsWith(pf));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Show player-activity hotspots from the chunk cache";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Scores 512×512-block grid cells by how many player-indicator blocks",
                "Baritone's cache holds in that area. High score = lots of activity.",
                "",
                "Unlike #bases (tight DBSCAN clusters = single base footprint),",
                "#heatmap shows broad zones — useful for finding heavily-travelled",
                "corridors, nether highway hubs, or the general area of a base",
                "before narrowing down with #bases.",
                "",
                "Extra signals vs #bases: nether portals, hoppers, observers,",
                "player heads (a player was killed there), jukeboxes.",
                "",
                "Pure cache reader — no packets, no anticheat surface.",
                "Only shows areas you've already explored.",
                "",
                "Usage:",
                "> #heatmap           - top 10 hottest zones",
                "> #heatmap <N>       - top N zones",
                "> #heatmap <N> goto  - path to the N'th hotspot",
                "",
                "Aliases: #activity"
        );
    }
}
