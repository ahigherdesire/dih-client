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
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.behavior.ThreatsBehavior;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #threats} — toggle the nearby-player alert watcher.
 *
 * <p>When enabled, DIH Client watches every visible player and fires a chat
 * warning the first time each enters within the configured radius.
 * Regardless of alert state, sightings are always recorded to
 * {@link baritone.cache.PlayerMemory} for use with {@code #players}.
 *
 * <h2>Subcommands</h2>
 * <pre>
 *   #threats              toggle on/off
 *   #threats on           enable
 *   #threats off          disable
 *   #threats &lt;N&gt;          set alert radius to N blocks (default 64)
 *   #threats status       show current state and radius
 * </pre>
 *
 * <p>Aliases: {@code #threat}
 */
public class ThreatsCommand extends Command {

    public ThreatsCommand(IBaritone baritone) {
        super(baritone, "threats", "threat");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            // Toggle
            ThreatsBehavior.threatsEnabled = !ThreatsBehavior.threatsEnabled;
            logDirect("Threat detection " + (ThreatsBehavior.threatsEnabled ? "ON" : "OFF")
                    + " (radius " + ThreatsBehavior.threatsRadius + " blocks).");
            return;
        }

        String sub = args.getString().toLowerCase();

        switch (sub) {
            case "on" -> {
                ThreatsBehavior.threatsEnabled = true;
                logDirect("Threat detection ON (radius " + ThreatsBehavior.threatsRadius + " blocks).");
            }
            case "off" -> {
                ThreatsBehavior.threatsEnabled = false;
                logDirect("Threat detection OFF.");
            }
            case "status" -> {
                logDirect("Threats: " + (ThreatsBehavior.threatsEnabled ? "ON" : "OFF")
                        + " | Radius: " + ThreatsBehavior.threatsRadius + " blocks"
                        + " | Alert cooldown: 30 s"
                        + " | Player memory: always active");
            }
            default -> {
                // Try to parse as a radius number
                int radius;
                try {
                    radius = Integer.parseInt(sub);
                } catch (NumberFormatException e) {
                    throw new CommandInvalidTypeException(args.consumed(), "on/off/status/<radius>", e);
                }
                if (radius < 1 || radius > 2048) {
                    logDirect("Radius must be between 1 and 2048.");
                    return;
                }
                ThreatsBehavior.threatsRadius = radius;
                logDirect("Threat radius set to " + radius + " blocks."
                        + (ThreatsBehavior.threatsEnabled ? "" : " (threats are currently OFF)"));
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String prefix;
            try { prefix = args.peekString().toLowerCase(); } catch (Exception ignored) { prefix = ""; }
            final String p = prefix;
            return Stream.of("on", "off", "status", "64", "128", "256").filter(
                    s -> s.startsWith(p));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Toggle nearby-player alerts (and always-on player memory)";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Watches for nearby players and fires a chat alert when one enters range.",
                "Player sightings are ALWAYS recorded to memory regardless of alert state.",
                "",
                "Usage:",
                "> #threats             — toggle on/off",
                "> #threats on          — enable alerts",
                "> #threats off         — disable alerts",
                "> #threats <N>         — set alert radius to N blocks (default 64)",
                "> #threats status      — show current state and radius",
                "",
                "Alias: #threat"
        );
    }
}
