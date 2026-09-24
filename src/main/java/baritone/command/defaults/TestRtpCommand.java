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

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.process.MenuClickProcess;
import net.minecraft.ChatFormatting;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #testrtp} — run just the travel leg of the mining loop, once.
 *
 * <p>Sends {@link baritone.api.Settings#autoMineTravelCommand} and clicks
 * through {@link baritone.api.Settings#autoMineTravelSteps}, then stops. Use
 * this to verify the menu matchers before committing to
 * {@code #start minecmd}, which repeats the same leg every cycle.
 *
 * <p>An optional argument overrides the chain for a one-off test:
 * {@code #testrtp item:heart_of_the_sea>badlands}
 */
public class TestRtpCommand extends Command {

    public TestRtpCommand(IBaritone baritone) {
        super(baritone, "testrtp", "rtptest");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String chain = args.hasAny()
                ? args.rawRest().trim()
                : Baritone.settings().autoMineTravelSteps.value.trim();
        String cmd = Baritone.settings().autoMineTravelCommand.value.trim();

        if (cmd.isEmpty()) {
            throw new CommandInvalidStateException("autoMineTravelCommand is empty.");
        }
        if (chain.isEmpty()) {
            throw new CommandInvalidStateException("autoMineTravelSteps is empty.");
        }

        List<MenuClickProcess.Step> steps;
        try {
            steps = MenuClickProcess.parseChain(chain);
        } catch (NumberFormatException e) {
            throw new CommandInvalidStateException("slot: needs a number in \"" + chain + "\"");
        }
        if (steps.isEmpty()) {
            throw new CommandInvalidStateException("No matchers parsed from \"" + chain + "\"");
        }

        logDirect("Testing travel: /" + cmd + " then " + steps, ChatFormatting.AQUA);
        logDirect("(this runs the travel leg once — it does not start the mining loop)");
        ((Baritone) baritone).getMenuClickProcess()
                .start(cmd.startsWith("/") ? cmd.substring(1) : cmd, steps);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Run the RTP menu chain once, without starting the loop";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Sends autoMineTravelCommand and clicks through autoMineTravelSteps once.",
                "",
                "Use this to check your matchers before running #start minecmd, which",
                "repeats this same leg on every cycle.",
                "",
                "Usage:",
                "> #testrtp                              - use the configured settings",
                "> #testrtp item:heart_of_the_sea>badlands - override the chain for one test",
                "",
                "If it fails, open each menu by hand and run #menu dump to see the real",
                "item ids and display names, then set autoMineTravelSteps accordingly.",
                "",
                "Alias: #rtptest"
        );
    }
}
