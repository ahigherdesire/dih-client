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

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.BlockById;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #search} — find a block type in the world and navigate to it.
 *
 * <p>Two data sources are merged:
 * <ol>
 *   <li><b>Live loaded chunks</b> — scans the chunks the client currently has,
 *       so it finds <em>any</em> block, not just cached ores.</li>
 *   <li><b>Persistent block cache</b> — Baritone remembers tracked blocks (ores,
 *       chests, etc.) it has seen before. This is why the search can still surface
 *       ores a server's anti-xray has since <em>re-hidden</em>: the client saw them
 *       once (when close enough for the server to reveal them) and Baritone kept the
 *       coordinates.</li>
 * </ol>
 *
 * <p><b>Anti-xray reality:</b> this cannot reveal blocks the server never sent.
 * Proper anti-xray hides ores by replacing them with stone in the packet — the
 * client never receives them, so there is nothing to find. No client mod can beat
 * that; the cache edge above only helps once you have actually seen a block.
 *
 * <h2>Usage</h2>
 * <pre>
 *   #search &lt;block&gt;         find &lt;block&gt;, list the nearest matches
 *   #search &lt;block&gt; goto    find and path to the nearest match
 *   #search &lt;N&gt; goto        path to result N from the last search
 * </pre>
 *
 * <p>Aliases: {@code #s}, {@code #blocks}
 */
public class SearchCommand extends Command {

    /** How many chunks out to scan live, and the result cap. */
    private static final int SCAN_RADIUS_CHUNKS = 64;
    private static final int MAX_RESULTS         = 1000;

    private List<BlockPos> lastResults = Collections.emptyList();
    private String         lastQuery   = "";

    public SearchCommand(IBaritone baritone) {
        super(baritone, "search", "s", "blocks");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            throw new CommandInvalidStateException(
                    "Usage: #search <block> [goto]   |   #search <N> goto");
        }

        // ── #search <N> goto ────────────────────────────────────────────────
        String peek = args.peekString();
        try {
            int n = Integer.parseInt(peek);
            args.getString(); // consume the number
            if (args.hasAny() && args.getString().equalsIgnoreCase("goto")) {
                args.requireMax(0);
                gotoResult(n);
            } else {
                throw new CommandInvalidStateException("Usage: #search <N> goto");
            }
            return;
        } catch (NumberFormatException notANumber) {
            // first token is a block name — fall through
        }

        // ── #search <block> [goto] ──────────────────────────────────────────
        Block block = args.getDatatypeFor(BlockById.INSTANCE);
        boolean gotoFirst = false;
        if (args.hasAny() && args.getString().equalsIgnoreCase("goto")) {
            gotoFirst = true;
        }
        args.requireMax(0);
        searchAndDisplay(block, gotoFirst);
    }

    private void searchAndDisplay(Block block, boolean gotoFirst) throws CommandException {
        if (ctx.world() == null) {
            throw new CommandInvalidStateException("No world loaded.");
        }
        final String        name   = BuiltInRegistries.BLOCK.getKey(block).getPath();
        final BetterBlockPos origin = ctx.playerFeet();

        // Merge live scan + cache, de-duplicating identical positions.
        LinkedHashSet<BlockPos> merged = new LinkedHashSet<>();

        // 1) live loaded chunks — any block the client currently has
        try {
            merged.addAll(BaritoneAPI.getProvider().getWorldScanner().scanChunkRadius(
                    ctx, Collections.singletonList(block), MAX_RESULTS, 0, SCAN_RADIUS_CHUNKS));
        } catch (Throwable ignored) {}

        // 2) persistent cache — remembers tracked blocks even after re-obfuscation
        try {
            merged.addAll(ctx.worldData().getCachedWorld().getLocationsOf(
                    name, MAX_RESULTS, origin.x, origin.z, 4));
        } catch (Throwable ignored) {}

        List<BlockPos> results = new ArrayList<>(merged);
        results.sort(Comparator.comparingDouble(p -> horizDistSq(origin, p)));

        lastResults = results;
        lastQuery   = name;

        if (results.isEmpty()) {
            logDirect("No '" + name + "' found in loaded chunks or the block cache.");
            logDirect("Explore closer so the client actually receives the blocks — a server's "
                    + "anti-xray can hide ores the client never sees.");
            return;
        }

        int show = Math.min(results.size(), 12);
        logDirect("== " + results.size() + " '" + name + "' found — nearest " + show + " ==");
        for (int i = 0; i < show; i++) {
            BlockPos p    = results.get(i);
            int      dist = (int) Math.sqrt(horizDistSq(origin, p));
            logDirect(String.format(" %2d. X=%d Y=%d Z=%d  ~%d blocks  %s",
                    i + 1, p.getX(), p.getY(), p.getZ(), dist, direction(origin, p)));
        }
        if (results.size() > show) {
            logDirect("  ... " + (results.size() - show) + " more. Use  #search <N> goto  for any of them.");
        }

        if (gotoFirst) {
            gotoResult(1);
        } else {
            logDirect("Tip:  #search " + name + " goto   → path to the nearest one");
        }
    }

    private void gotoResult(int n) throws CommandInvalidStateException {
        if (lastResults.isEmpty()) {
            throw new CommandInvalidStateException("No results — run #search <block> first.");
        }
        if (n < 1 || n > lastResults.size()) {
            throw new CommandInvalidStateException(
                    "Result " + n + " out of range (1 – " + lastResults.size() + ").");
        }
        BlockPos p = lastResults.get(n - 1);
        logDirect("→ Pathing to " + lastQuery + " #" + n
                + " at X=" + p.getX() + " Y=" + p.getY() + " Z=" + p.getZ());
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(p));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static double horizDistSq(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** Compass direction from origin to p (N/NE/E/... or "here"). */
    private static String direction(BlockPos origin, BlockPos p) {
        int dx = p.getX() - origin.getX();
        int dz = p.getZ() - origin.getZ();
        String ns = dz < 0 ? "N" : (dz > 0 ? "S" : "");
        String ew = dx < 0 ? "W" : (dx > 0 ? "E" : "");
        String d  = ns + ew;
        return d.isEmpty() ? "here" : d;
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .append(BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString))
                    .filterPrefixNamespaced(args.getString())
                    .sortAlphabetically()
                    .stream();
        }
        String pk = "";
        try { pk = args.peekString(); } catch (Exception ignored) {}
        final String p = pk.toLowerCase();
        return Stream.of("goto").filter(s -> s.startsWith(p));
    }

    @Override
    public String getShortDesc() {
        return "Search the world for a block and navigate to it";
    }

    @Override
    public List<String> getLongDesc() {
        return java.util.Arrays.asList(
                "Finds a block type in the world by scanning the chunks the client",
                "currently has loaded PLUS Baritone's persistent block cache, then lists",
                "the nearest matches with distance and direction.",
                "",
                "The cache is why previously-seen ores still show up even after a server's",
                "anti-xray re-hides them — but nothing can find blocks the server never sent",
                "in the first place (that is how real anti-xray works).",
                "",
                "Usage:",
                "> #search <block>        - list nearest matches",
                "> #search <block> goto   - list, then path to the nearest",
                "> #search <N> goto       - path to result N from the last search",
                "",
                "Examples:",
                "> #search diamond_ore",
                "> #search chest goto",
                "",
                "Aliases: #s, #blocks"
        );
    }
}
