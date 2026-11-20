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

package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.cache.ChestMemory;
import baritone.cache.ChestMemory.ChestRecord;
import baritone.cache.ChestMemory.SearchResult;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import dihclient.modules.StorageSearchRenderer;
import dihclient.util.DihStorageSearch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #chest} — search recorded container contents and highlight them.
 *
 * <p>DIH Client silently records the contents of every chest, barrel, shulker
 * box, hopper, dropper, and other container you open. This command lets you
 * search that database and highlight a specific result, without moving the player.
 *
 * <h2>Subcommands</h2>
 * <pre>
 *   #chest &lt;item&gt;            search for chests containing &lt;item&gt; (partial match)
 *   #chest &lt;item&gt; highlight  highlight the best matching chest
 *   #chest &lt;N&gt; highlight     highlight the N'th result from the last search
 *   #chest list              list all recorded containers (most recent first)
 *   #chest stats             show database stats
 *   #chest clear             wipe the entire database
 *   #chest forget &lt;N&gt;       forget the N'th result from the last search
 * </pre>
 *
 * <p>Aliases: {@code #chests}, {@code #findchest}
 */
public class ChestCommand extends Command {

    /** Results from the most recent {@code #chest <item>} search. */
    private List<SearchResult> lastResults = Collections.emptyList();
    private String lastQuery = "";

    public ChestCommand(IBaritone baritone) {
        super(baritone, "chest", "chests", "findchest");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            throw new CommandInvalidStateException(
                    "Usage: #chest <item> | #chest list | #chest stats | #chest clear | #chest <N> highlight");
        }

        String first = args.getString().toLowerCase();

        // ── #chest stats ────────────────────────────────────────────────────
        if (first.equals("stats")) {
            args.requireMax(0);
            int total = ChestMemory.size();
            if (total == 0) {
                logDirect("Chest memory is empty. Open some containers to start recording.");
            } else {
                logDirect("Chest memory: " + total + " container"
                        + (total == 1 ? "" : "s") + " recorded.");
                logDirect("Tip: use #chest <item> to search, #chest list to browse all.");
            }
            return;
        }

        // ── #chest clear ────────────────────────────────────────────────────
        if (first.equals("clear")) {
            args.requireMax(0);
            int prev = ChestMemory.size();
            ChestMemory.clear();
            StorageSearchRenderer.clear();
            lastResults = Collections.emptyList();
            logDirect("Cleared " + prev + " container record"
                    + (prev == 1 ? "" : "s") + " from chest memory.");
            return;
        }

        // ── #chest list ─────────────────────────────────────────────────────
        if (first.equals("list")) {
            int page = 1;
            if (args.hasAny()) {
                try { page = Integer.parseInt(args.getString()); }
                catch (NumberFormatException e) { page = 1; }
            }
            args.requireMax(0);
            showList(page);
            return;
        }

        // ── #chest forget <N> ───────────────────────────────────────────────
        if (first.equals("forget")) {
            if (!args.hasAny()) {
                throw new CommandInvalidStateException(
                        "Usage: #chest forget <N>  (N = result number from last search)");
            }
            int n;
            try { n = Integer.parseInt(args.getString()); }
            catch (NumberFormatException e) {
                throw new CommandInvalidStateException("Expected a number after 'forget'.");
            }
            args.requireMax(0);
            forgetResult(n);
            return;
        }

        // ── #chest <N> goto ─────────────────────────────────────────────────
        // If the first token is a number, treat it as "goto Nth last result".
        try {
            int n = Integer.parseInt(first);
            if (args.hasAny() && isSelection(args.getString())) {
                args.requireMax(0);
                gotoResult(n);
            } else {
                throw new CommandInvalidStateException(
                        "Usage: #chest <N> highlight  (or #chest <item> to search first)");
            }
            return;
        } catch (NumberFormatException ignored) {
            // Not a number — fall through to item search
        }

        // ── #chest <item> [goto] ─────────────────────────────────────────────
        // first = item name fragment; optionally followed by "goto"
        String query = first;
        boolean gotoFirst = false;
        if (args.hasAny()) {
            String second = args.getString().toLowerCase();
            if (isSelection(second)) {
                gotoFirst = true;
            } else {
                // Maybe it's a multi-word item name — append
                query = query + " " + second;
                while (args.hasAny()) {
                    String tok = args.getString().toLowerCase();
                    if (isSelection(tok)) { gotoFirst = true; break; }
                    query = query + " " + tok;
                }
            }
        }
        args.requireMax(0);
        searchAndDisplay(query, gotoFirst);
    }

    // ── Search ──────────────────────────────────────────────────────────────

    private void searchAndDisplay(String query, boolean gotoFirst) throws CommandException {
        lastQuery = query;
        lastResults = ChestMemory.search(query);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            String dimension = mc.level.dimension().identifier().toString();
            lastResults.sort(java.util.Comparator
                .comparingInt((SearchResult hit) -> dimension.equals(hit.record.dimension) ? 0 : 1)
                .thenComparingDouble(hit -> hit.record.pos.distToCenterSqr(mc.player.position())));
            StorageSearchRenderer.search(mc, query);
        }

        if (lastResults.isEmpty()) {
            logDirect("No recorded chests contain \"" + query + "\".");
            logDirect("Open chests and their contents will be remembered automatically.");
            return;
        }

        BlockPos origin = ctx.playerFeet();
        int      dim    = dimensionIndex();
        int      show   = Math.min(lastResults.size(), 10);

        logDirect("══ Chests with \"" + query + "\" — "
                + lastResults.size() + " found, showing top " + show + " ══");

        for (int i = 0; i < show; i++) {
            SearchResult sr  = lastResults.get(i);
            ChestRecord  rec = sr.record;
            BlockPos     pos = rec.pos;

            int    dx   = pos.getX() - origin.getX();
            int    dz   = pos.getZ() - origin.getZ();
            int    dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);

            String matchStr = sr.matchedItems.stream()
                    .sorted((a, b) -> Integer.compare(b.count(), a.count()))
                    .limit(3)
                    .map(s -> s.count() + "× " + s.shortId())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("?");

            logDirect(String.format(
                    " %2d. X=%d Y=%d Z=%d [%s] ~%d away — %s; last seen %s",
                    i + 1,
                    pos.getX(), pos.getY(), pos.getZ(),
                    rec.dimShort(),
                    dist,
                    matchStr, DihStorageSearch.age(rec.timestamp, System.currentTimeMillis())));
        }

        if (lastResults.size() > show) {
            logDirect("  ... " + (lastResults.size() - show) + " more. "
                    + "Use #chest <N> highlight to select a specific result.");
        }

        if (gotoFirst) {
            gotoResult(1);
        } else {
            logDirect("Green outlines show nearby matches (up to 64 within 128 blocks). Contents are remembered snapshots.");
            logDirect("Tip: #chest 1 highlight selects one; .storage clear hides outlines.");
        }
    }

    // ── List all ────────────────────────────────────────────────────────────

    private void showList(int page) {
        List<ChestRecord> all = ChestMemory.allRecords();
        // Most recent first
        List<ChestRecord> sorted = new ArrayList<>(all);
        sorted.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

        if (sorted.isEmpty()) {
            logDirect("Chest memory is empty. Open containers to start recording.");
            return;
        }

        int perPage = 8;
        int pages   = (sorted.size() + perPage - 1) / perPage;
        if (page < 1) page = 1;
        if (page > pages) page = pages;

        int from = (page - 1) * perPage;
        int to   = Math.min(from + perPage, sorted.size());

        logDirect("══ Recorded containers (page " + page + "/" + pages
                + ", " + sorted.size() + " total) ══");

        BlockPos origin = ctx.playerFeet();
        for (int i = from; i < to; i++) {
            ChestRecord rec = sorted.get(i);
            BlockPos    pos = rec.pos;
            int dx   = pos.getX() - origin.getX();
            int dz   = pos.getZ() - origin.getZ();
            int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);

            logDirect(String.format(
                    " %3d. X=%d Y=%d Z=%d [%s] ~%d away — %s",
                    i + 1,
                    pos.getX(), pos.getY(), pos.getZ(),
                    rec.dimShort(),
                    dist,
                    rec.itemSummary(3)));
        }

        if (pages > 1) {
            logDirect("Page " + page + "/" + pages + " — use #chest list <page> for more.");
        }
    }

    // ── Goto ────────────────────────────────────────────────────────────────

    private void gotoResult(int n) throws CommandInvalidStateException {
        if (lastResults.isEmpty()) {
            throw new CommandInvalidStateException(
                    "No search results — run #chest <item> first.");
        }
        if (n < 1 || n > lastResults.size()) {
            throw new CommandInvalidStateException(
                    "Result " + n + " out of range (1 – " + lastResults.size() + ").");
        }
        ChestRecord rec = lastResults.get(n - 1).record;
        BlockPos    pos = rec.pos;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !rec.world.equals(ChestMemory.currentWorldKey())
            || !rec.dimension.equals(mc.level.dimension().identifier().toString())) {
            throw new CommandInvalidStateException("That result is in another world or dimension. Search again here.");
        }
        var highlighted = StorageSearchRenderer.show(mc, lastQuery, pos);
        logDirect("Highlighting chest #" + n + " at X=" + pos.getX()
                + " Y=" + pos.getY() + " Z=" + pos.getZ()
                + " [" + rec.dimShort() + "]");
        if (highlighted.isEmpty()) logDirect("Outside the 128-block highlight radius, or no longer a match. No movement started.");
    }

    // ── Forget ──────────────────────────────────────────────────────────────

    private void forgetResult(int n) throws CommandInvalidStateException {
        if (lastResults.isEmpty()) {
            throw new CommandInvalidStateException(
                    "No search results — run #chest <item> first.");
        }
        if (n < 1 || n > lastResults.size()) {
            throw new CommandInvalidStateException(
                    "Result " + n + " out of range (1 – " + lastResults.size() + ").");
        }
        ChestRecord rec = lastResults.get(n - 1).record;
        if (!rec.world.equals(ChestMemory.currentWorldKey())) {
            throw new CommandInvalidStateException("That result belongs to another world. Search again here.");
        }
        boolean ok = ChestMemory.forgetAt(rec.pos, rec.dimension);
        if (ok) {
            logDirect("Forgot container at X=" + rec.pos.getX()
                    + " Y=" + rec.pos.getY() + " Z=" + rec.pos.getZ() + ".");
            lastResults = new ArrayList<>(lastResults);
            lastResults.remove(n - 1);
        } else {
            logDirect("That container was already removed from the database.");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Legacy goto syntax selects an outline too; storage search never starts movement. */
    private static boolean isSelection(String token) {
        return token.equalsIgnoreCase("highlight") || token.equalsIgnoreCase("goto");
    }

    /** Returns a rough dimension index for sorting same-dim results first. */
    private int dimensionIndex() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        String dim = mc.level.dimension().identifier().toString();
        if (dim.contains("overworld")) return 0;
        if (dim.contains("nether"))    return 1;
        if (dim.contains("end"))       return 2;
        return 3;
    }

    // ── Tab completion ───────────────────────────────────────────────────────

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String p = "";
            try { p = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String pf = p;
            return Stream.of("list", "stats", "clear", "forget")
                    .filter(s -> s.startsWith(pf));
        }
        if (args.hasAny() && !args.hasExactlyOne()) {
            String p = "";
            try { p = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String pf = p;
            return Stream.of("highlight").filter(s -> s.startsWith(pf));
        }
        return Stream.empty();
    }

    // ── Boilerplate ──────────────────────────────────────────────────────────

    @Override
    public String getShortDesc() {
        return "Search recorded chest/container contents";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "DIH Client silently records the contents of every container you open",
                "(chests, barrels, shulker boxes, hoppers, droppers, etc.).",
                "Use this command to search those records and highlight them without walking.",
                "",
                "Usage:",
                "> #chest <item>          - search for chests containing <item>",
                "> #chest <item> highlight - search and highlight the top result",
                "> #chest <N> highlight   - highlight result N from the last search",
                "Legacy goto syntax also highlights; it never starts movement.",
                "> .storage clear        - hide outlines without deleting remembered contents",
                "> #chest list            - browse all recorded containers",
                "> #chest list <page>     - next page of the list",
                "> #chest stats           - how many containers are recorded",
                "> #chest forget <N>      - remove result N from the database",
                "> #chest clear           - wipe the entire chest memory database",
                "",
                "Searching is partial and case-insensitive: 'diamond' matches",
                "'minecraft:diamond', 'minecraft:diamond_sword', etc.",
                "",
                "The database persists to baritone/chest_memory.json.",
                "",
                "Aliases: #chests, #findchest"
        );
    }
}
