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
import baritone.behavior.AutoMineBehavior;
import net.minecraft.ChatFormatting;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #start minecmd} — run the unattended mining cycle until {@code #stop}.
 *
 * <p>Each cycle: teleport via the server's RTP menu, mine
 * {@link baritone.api.Settings#autoMineTarget}, flee home when a guard fires,
 * recover health, deposit the loot, repeat.
 *
 * <p>Aliases: {@code #minecmd}, {@code #automine}. {@code #stop} ends the loop.
 */
public class MineCmdCommand extends Command {

    public MineCmdCommand(IBaritone baritone) {
        super(baritone, "start", "minecmd", "automine");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        AutoMineBehavior loop = ((Baritone) baritone).getAutoMineBehavior();

        String sub = args.hasAny() ? args.getString().toLowerCase() : "";
        args.requireMax(0);

        // "#start minecmd" — the arg just names what to start.
        if (sub.equals("stop") || sub.equals("cancel")) {
            if (!loop.isRunning()) {
                logDirect("The mining loop isn't running.");
            } else {
                loop.stop("stopped by command");
            }
            return;
        }
        if (sub.equals("status") || sub.equals("s")) {
            logDirect("Mining loop: " + loop.status(), ChatFormatting.AQUA);
            logDirect("target=" + Baritone.settings().autoMineTarget.value
                    + "  travel=/" + Baritone.settings().autoMineTravelCommand.value
                    + "  steps=" + Baritone.settings().autoMineTravelSteps.value);
            logDirect("Tools: " + ((Baritone) baritone).getAutopilotBehavior().toolReport());
            return;
        }
        if (loop.isRunning()) {
            logDirect("Already running: " + loop.status(), ChatFormatting.YELLOW);
            return;
        }
        loop.start();
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String prefix = "";
            try {
                prefix = args.peekString().toLowerCase();
            } catch (Exception ignored) {
            }
            final String pf = prefix;
            return Stream.of("minecmd", "status", "stop").filter(s -> s.startsWith(pf));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Run the unattended mine/deposit/teleport loop";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Runs a full mining cycle over and over until you type #stop.",
                "",
                "Each cycle:",
                "  1. run the RTP menu command and click through it (heart of the sea -> badlands)",
                "  2. #mine the target block",
                "  3. when a mining guard fires (low health, player nearby, tool worn,",
                "     stacks collected) it runs mineFleeCommand — normally /home",
                "  4. eat and wait for health to come back",
                "  5. path to the nearest chest and shift-click the loot in",
                "  6. back to step 1",
                "",
                "Usage:",
                "> #start minecmd    - start the loop",
                "> #start status     - show the current phase and cycle count",
                "> #stop             - stop it (or #start stop)",
                "",
                "Key settings:",
                "> autoMineTarget          block(s) to mine            (default gold_ore)",
                "> autoMineTravelCommand   menu command                (default rtpmenu)",
                "> autoMineTravelSteps     '>'-separated slot matchers",
                "> autoMineResumeHealth    hp to wait for              (default 18)",
                "> autoMineDeposit         deposit loot each cycle     (default true)",
                "> autoMineKeep            registry paths never deposited",
                "",
                "Verify autoMineTravelSteps with #menu dump while each menu is open.",
                "",
                "Aliases: #minecmd, #automine"
        );
    }
}
