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

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class AutoSleepCommand extends Command {

    public AutoSleepCommand(IBaritone baritone) {
        super(baritone, "autosleep", "nightowl");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);
        if (!args.hasAny()) {
            boolean newVal = !Baritone.settings().autoSleep.value;
            Baritone.settings().autoSleep.value = newVal;
            logDirect("autoSleep is now " + (newVal ? "ON" : "OFF"));
            return;
        }
        String arg = args.getString().toLowerCase();
        switch (arg) {
            case "on", "true", "enable" -> {
                Baritone.settings().autoSleep.value = true;
                logDirect("autoSleep enabled. Will navigate to the nearest cached bed at night.");
            }
            case "off", "false", "disable" -> {
                Baritone.settings().autoSleep.value = false;
                logDirect("autoSleep disabled.");
            }
            case "interrupt" -> {
                Baritone.settings().autoSleepInterruptTasks.value =
                        !Baritone.settings().autoSleepInterruptTasks.value;
                logDirect("autoSleepInterruptTasks is now " +
                        (Baritone.settings().autoSleepInterruptTasks.value ? "ON" : "OFF") +
                        ". " + (Baritone.settings().autoSleepInterruptTasks.value
                            ? "Will yank you out of active tasks to sleep."
                            : "Will only sleep if no other process is running."));
            }
            case "status" -> {
                logDirect("autoSleep:               " + (Baritone.settings().autoSleep.value ? "ON" : "off"));
                logDirect("autoSleepInterruptTasks: " + Baritone.settings().autoSleepInterruptTasks.value);
            }
            default -> throw new CommandInvalidStateException(
                    "Unknown argument '" + arg + "'. Try on/off/interrupt/status.");
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String p = "";
            try { p = args.peekString().toLowerCase(); } catch (Exception ignored) {}
            final String pf = p;
            return Stream.of("on", "off", "interrupt", "status").filter(s -> s.startsWith(pf));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Auto-navigate to the nearest bed at night";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "When night falls (or a thunderstorm), automatically navigate to the nearest",
                "cached bed using Baritone's bed lookup. By default this only activates when",
                "no other process is running.",
                "",
                "Usage:",
                "> autosleep            - toggle on/off",
                "> autosleep on / off   - explicit",
                "> autosleep interrupt  - toggle whether to interrupt active tasks (default off)",
                "> autosleep status     - show current settings",
                "",
                "Aliases: #nightowl",
                "",
                "Note: this navigates to the bed. Use the existing #sleep command for an",
                "explicit one-shot 'go and sleep now' workflow."
        );
    }
}
