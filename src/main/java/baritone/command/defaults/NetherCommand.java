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
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Converts coordinates between the Overworld and the Nether.
 *
 * <p>X and Z are scaled ×8 / ÷8. Y is the same in both dimensions.
 *
 * <p>Usage:
 * <pre>
 *   #nether                      convert your current X Y Z (dimension auto-detected)
 *   #nether X Y Z                convert given coords (direction auto-detected)
 *   #nether X Z                  same but Y omitted (shown as-is)
 *   #nether overworld X Y Z      explicitly convert overworld → nether
 *   #nether nether   X Y Z      explicitly convert nether → overworld
 * </pre>
 */
public class NetherCommand extends Command {

    private static final int NO_Y = Integer.MIN_VALUE;

    public NetherCommand(IBaritone baritone) {
        super(baritone, "nether", "nc", "coords");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (ctx.world() == null) {
            throw new CommandInvalidStateException("No world loaded.");
        }

        boolean inNether = ctx.world().dimension() == Level.NETHER;

        // ── No arguments: convert current position (all 3 axes) ──────────────
        if (!args.hasAny()) {
            BlockPos pos = ctx.playerFeet();
            showConversion(pos.getX(), pos.getY(), pos.getZ(), inNether, true);
            return;
        }

        // ── Peek at first arg for explicit dimension keyword ──────────────────
        String first = args.peekString().toLowerCase();

        if (first.equals("overworld") || first.equals("ow")) {
            args.getString();
            int[] xyz = parseXYZ(args);
            showConversion(xyz[0], xyz[1], xyz[2], false, false);
            return;
        }
        if (first.equals("nether") || first.equals("nw") || first.equals("hell")) {
            args.getString();
            int[] xyz = parseXYZ(args);
            showConversion(xyz[0], xyz[1], xyz[2], true, false);
            return;
        }

        // ── Numeric args in current dimension ─────────────────────────────────
        int[] xyz = parseXYZ(args);
        showConversion(xyz[0], xyz[1], xyz[2], inNether, false);
    }

    /**
     * Parse X [Y] Z from the argument consumer.
     * Accepts 2 args (X Z, no Y → NO_Y) or 3 args (X Y Z).
     * Returns int[3]: {x, y, z} where y may be NO_Y.
     */
    private int[] parseXYZ(IArgConsumer args) throws CommandException {
        int x = parseInt(args, "X");
        if (!args.hasAny()) {
            throw new CommandInvalidStateException(
                "Too few arguments. Usage:  #nether X Y Z  or  #nether X Z");
        }
        int second = parseInt(args, "Y or Z");
        if (args.hasAny()) {
            // Three numbers: X Y Z
            int z = parseInt(args, "Z");
            return new int[]{x, second, z};
        } else {
            // Two numbers: X Z (no Y)
            return new int[]{x, NO_Y, second};
        }
    }

    /**
     * Formats and logs the conversion result.
     *
     * @param x          source X
     * @param y          source Y (may be NO_Y if not provided)
     * @param z          source Z
     * @param fromNether true  → source is nether, convert to overworld
     *                   false → source is overworld, convert to nether
     * @param isCurrent  true if these are the player's live coordinates
     */
    private void showConversion(int x, int y, int z, boolean fromNether, boolean isCurrent) {
        String srcDim = fromNether ? "Nether"    : "Overworld";
        String dstDim = fromNether ? "Overworld" : "Nether";

        int dstX, dstZ;
        if (fromNether) {
            dstX = x * 8;
            dstZ = z * 8;
        } else {
            dstX = (int) Math.round(x / 8.0);
            dstZ = (int) Math.round(z / 8.0);
        }

        // Y is the same in both dimensions
        String srcY = y == NO_Y ? "?" : String.valueOf(y);
        String dstY = y == NO_Y ? "?" : String.valueOf(y);

        String srcLabel = isCurrent ? "You are at (" + srcDim + ")" : srcDim;
        logDirect(srcLabel + "  X=" + x + "  Y=" + srcY + "  Z=" + z
                + "  →  " + dstDim + "  X=" + dstX + "  Y=" + dstY + "  Z=" + dstZ);

        if (!fromNether && y == NO_Y) {
            logDirect("(Y is the same in both dimensions — Nether Y range is 0–127)");
        } else if (!fromNether) {
            logDirect("(Y stays the same — Nether Y range is 0–127)");
        }
    }

    private int parseInt(IArgConsumer args, String name) throws CommandException {
        if (!args.hasAny()) {
            throw new CommandInvalidStateException(
                "Missing " + name + " coordinate.");
        }
        String raw = args.getString();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new CommandInvalidStateException(
                "Invalid coordinate '" + raw + "' for " + name + ". Must be an integer.");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String prefix = "";
            try { prefix = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String p = prefix;
            return Stream.of("overworld", "nether").filter(s -> s.startsWith(p));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Convert X Y Z coordinates between Overworld and Nether";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "Converts coordinates between the Overworld and the Nether.",
            "X and Z are scaled ÷8 (overworld→nether) or ×8 (nether→overworld).",
            "Y is the same in both dimensions.",
            "",
            "Usage:",
            "> nether                    - convert your current X Y Z (auto-detects dimension)",
            "> nether X Y Z              - convert given coords (auto-detects direction)",
            "> nether X Z                - convert X Z only (Y omitted)",
            "> nether overworld X Y Z    - overworld → nether explicitly",
            "> nether nether   X Y Z    - nether → overworld explicitly",
            "",
            "Aliases: #nc, #coords",
            "",
            "Examples:",
            "  In overworld at 800 64 -400:   #nether  →  Nether X=100 Y=64 Z=-50",
            "  In nether at 100 64 -50:        #nether  →  Overworld X=800 Y=64 Z=-400",
            "  Explicit:  #nether overworld 800 64 -400  →  Nether X=100 Y=64 Z=-50"
        );
    }
}
