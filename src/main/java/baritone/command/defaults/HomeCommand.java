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
import baritone.api.pathing.goals.GoalBlock;
import baritone.cache.HomeMemory;
import baritone.cache.HomeMemory.HomeRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #home} — save and navigate to named home positions.
 *
 * <h2>Subcommands</h2>
 * <pre>
 *   #home set &lt;name&gt;       save current position as home &lt;name&gt;
 *   #home &lt;name&gt;           path to home &lt;name&gt;
 *   #home list             list all saved homes
 *   #home del &lt;name&gt;       delete home &lt;name&gt;
 * </pre>
 *
 * <p>Names are case-insensitive. Homes are persisted to
 * {@code baritone/homes.json} across sessions.
 *
 * <p>Aliases: {@code #homes}, {@code #sethome} (sethome &lt;name&gt; = home set &lt;name&gt;)
 */
public class HomeCommand extends Command {

    /** Subcommand words — not allowed as home names, they'd be unreachable via #home <name>. */
    private static final java.util.Set<String> RESERVED = java.util.Set.of(
            "set", "save", "add", "list", "ls", "l", "del", "delete", "remove", "rm");

    public HomeCommand(IBaritone baritone) {
        super(baritone, "home", "homes", "sethome");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {

        // ── #sethome <name> ────────────────────────────────────────────────────
        // Treat the entire command as "home set <name>" for convenience.
        if (label.equalsIgnoreCase("sethome")) {
            String name = args.hasAny() ? args.getString() : "default";
            args.requireMax(0);
            doSet(name);
            return;
        }

        // ── #home  (no args) ──────────────────────────────────────────────────
        if (!args.hasAny()) {
            showList();
            return;
        }

        String first = args.getString();

        // ── #home set <name> ──────────────────────────────────────────────────
        if (first.equalsIgnoreCase("set") || first.equalsIgnoreCase("save") || first.equalsIgnoreCase("add")) {
            String name = args.hasAny() ? args.getString() : "default";
            args.requireMax(0);
            doSet(name);
            return;
        }

        // ── #home list ────────────────────────────────────────────────────────
        if (first.equalsIgnoreCase("list") || first.equalsIgnoreCase("ls") || first.equalsIgnoreCase("l")) {
            args.requireMax(0);
            showList();
            return;
        }

        // ── #home del <name> / #home delete <name> ────────────────────────────
        if (first.equalsIgnoreCase("del") || first.equalsIgnoreCase("delete")
                || first.equalsIgnoreCase("remove") || first.equalsIgnoreCase("rm")) {
            if (!args.hasAny()) {
                throw new CommandInvalidStateException("Usage: #home del <name>");
            }
            String name = args.getString();
            args.requireMax(0);
            doDelete(name);
            return;
        }

        // ── #home <name>  ─────────────────────────────────────────────────────
        // First token is the home name — path to it.
        args.requireMax(0);
        doGoto(first);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void doSet(String name) throws CommandInvalidStateException {
        if (RESERVED.contains(name.toLowerCase())) {
            throw new CommandInvalidStateException(
                    "\"" + name + "\" is a reserved word and can't be used as a home name.");
        }
        BlockPos pos = ctx.playerFeet();
        String   dim = currentDim();
        HomeMemory.set(name, pos, dim);
        logDirect("Home \"" + name + "\" set at X=" + pos.getX()
                + " Y=" + pos.getY() + " Z=" + pos.getZ()
                + " [" + shortDim(dim) + "]");
    }

    private void doGoto(String name) throws CommandInvalidStateException {
        HomeRecord rec = HomeMemory.get(name);
        if (rec == null) {
            int total = HomeMemory.size();
            if (total == 0) {
                throw new CommandInvalidStateException(
                        "No home named \"" + name + "\". Use #home set " + name + " to create it.");
            } else {
                throw new CommandInvalidStateException(
                        "No home named \"" + name + "\". Use #home list to see saved homes.");
            }
        }
        // The saved coordinates only make sense in the dimension they were saved
        // in — pathing to overworld coords while in the nether goes somewhere
        // wrong (and possibly dangerous).
        String cur = currentDim();
        if (rec.dim != null && !rec.dim.equals(cur)) {
            throw new CommandInvalidStateException(
                    "Home \"" + rec.name + "\" is in " + rec.dimShort()
                            + " but you are in " + shortDim(cur)
                            + ". Travel there first (try #portal).");
        }
        BlockPos pos = rec.pos;
        logDirect("Going to home \"" + rec.name + "\" — X=" + pos.getX()
                + " Y=" + pos.getY() + " Z=" + pos.getZ()
                + " [" + rec.dimShort() + "]");
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalBlock(pos));
    }

    private void doDelete(String name) {
        boolean ok = HomeMemory.delete(name);
        if (ok) {
            logDirect("Deleted home \"" + name + "\".");
        } else {
            logDirect("No home named \"" + name + "\" found.");
        }
    }

    private void showList() {
        List<HomeRecord> all = HomeMemory.all();
        if (all.isEmpty()) {
            logDirect("No homes saved. Use: #home set <name>");
            return;
        }
        BlockPos origin = ctx.playerFeet();
        logDirect("-- Saved homes (" + all.size() + ") --");
        for (HomeRecord h : all) {
            BlockPos pos  = h.pos;
            int      dx   = pos.getX() - origin.getX();
            int      dz   = pos.getZ() - origin.getZ();
            int      dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
            logDirect(String.format(
                    "  %-12s X=%d Y=%d Z=%d [%s] ~%d blocks away",
                    h.name,
                    pos.getX(), pos.getY(), pos.getZ(),
                    h.dimShort(),
                    dist));
        }
        logDirect("Use #home <name> to navigate, #home del <name> to remove.");
    }

    private static String currentDim() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "minecraft:overworld";
        return mc.level.dimension().identifier().toString();
    }

    private static String shortDim(String dim) {
        if (dim == null) return "?";
        if (dim.contains("overworld")) return "OW";
        if (dim.contains("nether"))    return "NE";
        if (dim.contains("end"))       return "END";
        int colon = dim.lastIndexOf(':');
        return colon < 0 ? dim : dim.substring(colon + 1);
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (!args.hasAny()) return Stream.empty();

        // First token
        if (args.hasExactlyOne()) {
            String prefix = "";
            try { prefix = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String pf = prefix;

            // For sethome, no subcommands — complete home names
            if (label.equalsIgnoreCase("sethome")) {
                return HomeMemory.names().stream().filter(n -> n.startsWith(pf));
            }

            // For #home: offer subcommands + existing home names
            return Stream.concat(
                    Stream.of("set", "list", "del").filter(s -> s.startsWith(pf)),
                    HomeMemory.names().stream().filter(n -> n.startsWith(pf))
            ).distinct();
        }

        // Second token
        if (args.hasAny() && !args.hasExactlyOne()) {
            String first = "";
            try { first = args.getString(); } catch (Exception ignored) {}
            if (first.equalsIgnoreCase("del") || first.equalsIgnoreCase("delete")
                    || first.equalsIgnoreCase("remove") || first.equalsIgnoreCase("rm")) {
                String prefix = "";
                try { prefix = args.peekString().toLowerCase(); } catch (Exception ignored) {}
                final String pf = prefix;
                return HomeMemory.names().stream().filter(n -> n.startsWith(pf));
            }
        }

        return Stream.empty();
    }

    // ── Boilerplate ───────────────────────────────────────────────────────────

    @Override
    public String getShortDesc() {
        return "Save and navigate to named home positions";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Save up to as many named home positions as you want, then path to them by name.",
                "",
                "Homes are persisted across sessions in baritone/homes.json.",
                "",
                "Usage:",
                "> #home set <name>     - save your current position as home <name>",
                "> #home <name>         - path to home <name>",
                "> #home list           - list all saved homes with distances",
                "> #home del <name>     - delete home <name>",
                "",
                "Examples:",
                "> #home set 1          - save current pos as home '1'",
                "> #home 1              - navigate to home '1'",
                "> #home set base       - save current pos as 'base'",
                "> #home base           - navigate to 'base'",
                "",
                "Aliases: #homes, #sethome <name>  (equivalent to #home set <name>)"
        );
    }
}
