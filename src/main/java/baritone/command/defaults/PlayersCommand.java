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
import baritone.api.pathing.goals.GoalXZ;
import baritone.cache.PlayerMemory;
import baritone.cache.PlayerMemory.PlayerRecord;
import baritone.cache.PlayerMemory.Sighting;
import net.minecraft.client.Minecraft;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #players} — browse and navigate to recorded player sightings.
 *
 * <p>{@link baritone.behavior.ThreatsBehavior} silently records every player
 * entity you see each tick. This command lets you query that database and
 * optionally path to a player's last known position.
 *
 * <h2>Subcommands</h2>
 * <pre>
 *   #players                     list all recorded players (most-recent first)
 *   #players &lt;name&gt;              show a player's sighting history
 *   #players goto &lt;name&gt;         path to that player's last known position
 *   #players forget &lt;name&gt;       remove a player record
 *   #players clear               wipe the entire database
 *   #players stats               database summary
 * </pre>
 *
 * <p>Aliases: {@code #player}, {@code #seen}
 */
public class PlayersCommand extends Command {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    public PlayersCommand(IBaritone baritone) {
        super(baritone, "players", "player", "seen");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        // ── No args → list all ────────────────────────────────────────────────
        if (!args.hasAny()) {
            listAll();
            return;
        }

        String sub = args.getString().toLowerCase();

        switch (sub) {
            case "stats" -> {
                int total = PlayerMemory.size();
                logDirect("Player memory: " + total + " player" + (total == 1 ? "" : "s") + " recorded.");
            }
            case "clear" -> {
                int before = PlayerMemory.size();
                PlayerMemory.clear();
                logDirect("Cleared " + before + " player record" + (before == 1 ? "" : "s") + ".");
            }
            case "forget" -> {
                if (!args.hasAny()) {
                    logDirect("Usage: #players forget <name>");
                    return;
                }
                String name = args.getString();
                if (PlayerMemory.forget(name)) {
                    logDirect("Forgotten: " + name);
                } else {
                    logDirect("No player matching \"" + name + "\" found.");
                }
            }
            case "goto" -> {
                if (!args.hasAny()) {
                    logDirect("Usage: #players goto <name>");
                    return;
                }
                gotoPlayer(args.getString());
            }
            default -> {
                // sub is the first word of the player name; rest of args may be "goto"
                String name = sub; // already consumed
                if (args.hasAny() && args.peekString().equalsIgnoreCase("goto")) {
                    args.getString(); // consume "goto"
                    gotoPlayer(name);
                } else {
                    showPlayerHistory(name);
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void listAll() {
        List<PlayerRecord> all = PlayerMemory.allPlayers();
        if (all.isEmpty()) {
            logDirect("No players recorded yet. Player memory fills automatically as you see others.");
            logDirect("Enable #threats to also receive proximity alerts.");
            return;
        }

        logDirect("══ Known players (" + all.size() + " recorded) ══");
        for (int i = 0; i < all.size(); i++) {
            PlayerRecord rec = all.get(i);
            Sighting s = rec.latest();
            if (s == null) {
                logDirect("  " + (i + 1) + ". " + rec.name + " — no position data");
                continue;
            }
            double dist = distanceTo(s.x, s.z);
            String distStr = dist < 0 ? "" : ("  ~" + (int) dist + " blocks");
            logDirect("  " + (i + 1) + ". " + rec.name
                    + distStr
                    + "  X=" + s.x + " Y=" + s.y + " Z=" + s.z
                    + " [" + s.dimShort() + "]"
                    + "  " + timeAgo(s.timestamp));
        }
        logDirect("#players <name> — history  |  #players goto <name> — navigate");
    }

    private void showPlayerHistory(String query) {
        PlayerRecord rec = PlayerMemory.findByName(query);
        if (rec == null) {
            logDirect("No player matching \"" + query + "\" in memory.");
            return;
        }
        logDirect("══ " + rec.name + " ══");
        logDirect("UUID: " + rec.uuid);
        if (rec.history.isEmpty()) {
            logDirect("  (no sightings)");
            return;
        }
        logDirect("Sighting history (" + rec.history.size() + " entries, newest first):");
        for (int i = 0; i < rec.history.size(); i++) {
            Sighting s = rec.history.get(i);
            logDirect("  " + (i + 1) + ".  X=" + s.x + " Y=" + s.y + " Z=" + s.z
                    + " [" + s.dimShort() + "]  —  " + FMT.format(Instant.ofEpochMilli(s.timestamp)));
        }
    }

    private void gotoPlayer(String query) throws CommandException {
        PlayerRecord rec = PlayerMemory.findByName(query);
        if (rec == null) {
            throw new CommandInvalidStateException("No player matching \"" + query + "\" in memory.");
        }
        Sighting s = rec.latest();
        if (s == null) {
            throw new CommandInvalidStateException("No position recorded for " + rec.name + ".");
        }
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalXZ(s.x, s.z));
        logDirect("Pathing to " + rec.name + "'s last known position: X=" + s.x + " Z=" + s.z
                + " [" + s.dimShort() + "]  (" + timeAgo(s.timestamp) + ")");
        if (!s.dim.equals(currentDim())) {
            logDirect("⚠ Warning: that sighting was in [" + s.dimShort() + "]"
                    + " but you are in [" + currentDimShort() + "].");
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static double distanceTo(int x, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        double dx = mc.player.getX() - x;
        double dz = mc.player.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String currentDim() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "";
        return mc.level.dimension().identifier().toString();
    }

    private static String currentDimShort() {
        String d = currentDim();
        if (d.contains("overworld")) return "overworld";
        if (d.contains("nether"))    return "nether";
        if (d.contains("end"))       return "end";
        int c = d.lastIndexOf(':');
        return c < 0 ? d : d.substring(c + 1);
    }

    /** Human-readable relative time string for a past epoch-millisecond timestamp. */
    static String timeAgo(long ts) {
        long seconds = (System.currentTimeMillis() - ts) / 1000;
        if (seconds < 5)    return "just now";
        if (seconds < 60)   return seconds + "s ago";
        if (seconds < 3600) {
            long m = seconds / 60, s = seconds % 60;
            return m + "m " + s + "s ago";
        }
        if (seconds < 86400) {
            long h = seconds / 3600, m = (seconds % 3600) / 60;
            return h + "h " + m + "m ago";
        }
        long days = seconds / 86400;
        return days + " day" + (days == 1 ? "" : "s") + " ago";
    }

    // ── Tab complete ──────────────────────────────────────────────────────────

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        try {
            if (args.hasExactlyOne()) {
                String prefix = args.peekString().toLowerCase();
                Stream<String> fixed = Stream.of("stats", "clear", "forget", "goto");
                Stream<String> names = PlayerMemory.allPlayers().stream().map(r -> r.name);
                return Stream.concat(fixed, names).filter(s -> s.toLowerCase().startsWith(prefix));
            }
            if (args.has(2)) {
                String first = args.peekString().toLowerCase();
                if (first.equals("goto") || first.equals("forget")) {
                    args.getString(); // consume first
                    String prefix = args.peekString().toLowerCase();
                    return PlayerMemory.allPlayers().stream()
                            .map(r -> r.name)
                            .filter(n -> n.toLowerCase().startsWith(prefix));
                }
                // <name> goto
                boolean nameKnown = PlayerMemory.allPlayers().stream()
                        .anyMatch(n -> n.name.equalsIgnoreCase(first));
                if (nameKnown) return Stream.of("goto");
            }
        } catch (Exception ignored) {}
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Browse and navigate to recorded player sightings";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Queries the player-sighting database that ThreatsBehavior populates every tick.",
                "",
                "Usage:",
                "> #players                — list all known players",
                "> #players <name>         — sighting history for that player",
                "> #players goto <name>    — path to their last known position",
                "> #players forget <name>  — remove from database",
                "> #players clear          — wipe the entire database",
                "> #players stats          — show database summary",
                "",
                "Player data is always recorded even if #threats is OFF.",
                "Aliases: #player, #seen"
        );
    }
}
