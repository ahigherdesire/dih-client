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
import baritone.util.JourneyMapHelper;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reports location information without starting any navigation.
 *
 * No argument  → prints the player's current coordinates and dimension.
 * With argument → finds the nearest named structure and prints its coordinates,
 *                 distance, and compass direction — but does NOT path there.
 *                 Use {@code #structure <name>} to also navigate.
 */
public class WhereCommand extends Command {

    public WhereCommand(IBaritone baritone) {
        super(baritone, "where");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);

        if (!args.hasAny()) {
            // No argument — report the player's own position
            BlockPos pos = ctx.playerFeet();
            // ResourceKey.toString() → "ResourceKey[minecraft:dimension_type / minecraft:overworld]"
            // Strip everything up to " / " to get just "minecraft:overworld]", then drop the "]".
            String dim = "unknown";
            if (ctx.world() != null) {
                String raw = ctx.world().dimension().toString();
                int sep = raw.indexOf(" / ");
                dim = sep >= 0 ? raw.substring(sep + 3, raw.length() - 1) : raw;
            }
            logDirect("You are at  X=" + pos.getX()
                    + "  Y=" + pos.getY()
                    + "  Z=" + pos.getZ()
                    + "  (" + dim + ")");
            return;
        }

        // With argument — locate a structure
        String input = args.getString().toLowerCase();
        String query = StructureCommand.ALIASES.getOrDefault(input, "tag:" + input);

        final BlockPos origin = ctx.playerFeet();

        // ── Singleplayer path ────────────────────────────────────────────────
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            ServerLevel serverLevel = server.getLevel(ctx.world().dimension());
            if (serverLevel == null) {
                throw new CommandInvalidStateException(
                    "Current dimension is not available on the integrated server.");
            }
            // Dispatch resolveStructures + the search to the server thread.
            // Both may touch serverLevel internals that are only safe on that thread.
            logDirect("Searching for nearest '" + input + "'...");
            server.execute(() -> {
                HolderSet<Structure> holderSet = StructureCommand.resolveStructures(query, serverLevel);
                if (holderSet == null) {
                    Minecraft.getInstance().execute(() ->
                        logDirect("Unknown structure: '" + input + "'. Check #help structure for a list."));
                    return;
                }
                int holderCount = 0;
                for (Holder<Structure> h : holderSet) holderCount++;
                if (holderCount == 0) {
                    Minecraft.getInstance().execute(() ->
                        logDirect("Structure tag resolved but contains 0 variants for '" + input
                            + "' in this MC version — cannot search."));
                    return;
                }

                Pair<BlockPos, Holder<Structure>> found;
                try {
                    found = serverLevel.getChunkSource().getGenerator()
                        .findNearestMapStructure(serverLevel, holderSet, origin, 100, false);
                } catch (Throwable t) {
                    final String msg = t.getClass().getSimpleName() + ": " + t.getMessage();
                    Minecraft.getInstance().execute(() ->
                        logDirect("Search failed: " + msg));
                    return;
                }
                final BlockPos result = found == null ? null : found.getFirst();
                final String variantName;
                if (found == null) {
                    variantName = null;
                } else {
                    variantName = found.getSecond().unwrapKey()
                        .map(k -> k.identifier().getPath()).orElse("?");
                }
                Minecraft.getInstance().execute(() -> {
                    if (result == null) {
                        logDirect("No '" + input + "' found within range.");
                        return;
                    }
                    int dx2 = result.getX() - origin.getX();
                    int dz2 = result.getZ() - origin.getZ();
                    int dist = (int) Math.sqrt(dx2 * dx2 + dz2 * dz2);
                    String dir = compassDirection(origin, result);
                    logDirect(variantName + ":"
                        + "  X=" + result.getX()
                        + "  Z=" + result.getZ()
                        + "  |  " + dir
                        + "  |  ~" + dist + " blocks"
                        + "  |  (use #structure " + input + " to go there)");
                    JourneyMapHelper.addWaypoint(
                        input + " (" + variantName + ")", result, JourneyMapHelper.COLOR_STRUCTURE);
                });
            });
            return;
        }

        // ── Multiplayer path ─────────────────────────────────────────────────
        if (!ClientStructureFinder.hasSeed()) {
            throw new CommandInvalidStateException(
                "You are on multiplayer. Enter your world seed first:  #seedinput <seed>");
        }
        final long seed = ClientStructureFinder.getSeed();
        final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = ctx.world().dimension();
        logDirect("Searching for nearest '" + input + "' from seed " + seed + "...");
        Thread t = new Thread(() -> {
            SeedStructureScanner.Found found;
            try {
                found = StructureCommand.findNearestSeeded(query, seed, dim, origin.getX(), origin.getZ());
            } catch (Throwable e) {
                String msg = e instanceof IllegalStateException ? e.getMessage()
                    : "Search failed: " + e.getClass().getSimpleName();
                Minecraft.getInstance().execute(() -> logDirect(msg));
                return;
            }
            Minecraft.getInstance().execute(() -> {
                if (found == null) {
                    logDirect("No '" + input + "' within 8192 blocks for this seed (biome-validated).");
                    return;
                }
                BlockPos result = new BlockPos(found.x(), found.y(), found.z());
                int dx = result.getX() - origin.getX();
                int dz = result.getZ() - origin.getZ();
                int dist = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
                String dir = compassDirection(origin, result);
                logDirect(found.id() + " (seed, biome-validated):"
                    + "  X=" + result.getX()
                    + "  Z=" + result.getZ()
                    + "  |  " + dir
                    + "  |  ~" + dist + " blocks"
                    + "  |  (use #structure " + input + " to go there)");
            });
        }, "BaritoneWhereSeedSearch");
        t.setDaemon(true);
        t.start();
    }

    /** Returns a compass direction string with an arrow, e.g. "NE ↗". */
    private static String compassDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) return "here";
        // atan2(dx, -dz) gives clockwise angle from north.
        // Minecraft: +X = East, +Z = South, -Z = North.
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) angle += 360;
        String[] dirs = { "N ↑", "NE ↗", "E →", "SE ↘", "S ↓", "SW ↙", "W ←", "NW ↖" };
        int idx = (int) Math.round(angle / 45.0) % 8;
        return dirs[idx];
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String prefix = "";
            try { prefix = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String p = prefix;
            return StructureCommand.ALIASES.keySet().stream()
                .filter(k -> k.startsWith(p))
                .sorted();
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Show your position or find a structure's coordinates";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "The where command reports location without starting navigation.",
            "",
            "Usage:",
            "> where               - print your current X Y Z and dimension",
            "> where <structure>   - find the nearest structure and show its coordinates,",
            "                        distance, and compass direction (does NOT path there)",
            "",
            "All structure names accepted by #structure also work here.",
            "To navigate after finding a location, use:  #structure <name>"
        );
    }
}
