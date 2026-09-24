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
import baritone.api.pathing.goals.GoalRunAway;
import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs the player away from their current position by the requested distance.
 *
 * <p>Uses {@link GoalRunAway}, which finds a position that is at least {@code distance} blocks
 * away (horizontal) from the origin. Useful for retreating from danger, escaping mobs,
 * or just getting out of a tight area quickly.
 *
 * <p>The origin is locked to where the player was standing when the command was typed, so
 * the bot won't keep running forever — it stops once it's far enough away.
 */
public class RunAwayCommand extends Command {

    private static final int DEFAULT_DISTANCE = 32;

    public RunAwayCommand(IBaritone baritone) {
        super(baritone, "runaway", "flee", "escape");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);
        if (ctx.world() == null || ctx.player() == null) {
            throw new CommandInvalidStateException("No world loaded.");
        }

        int distance = DEFAULT_DISTANCE;
        if (args.hasAny()) {
            distance = args.getAs(Integer.class);
            if (distance <= 0) {
                throw new CommandInvalidStateException("Distance must be a positive integer.");
            }
        }

        // Lock the flee origin to where the player is standing right now
        BlockPos origin = ctx.playerFeet();
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalRunAway(distance, origin));
        logDirect("Fleeing " + distance + " blocks away from  X=" + origin.getX()
                + "  Y=" + origin.getY() + "  Z=" + origin.getZ() + ".");
        logDirect("Use  #cancel  to stop.");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Flee from the current position";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Moves the player at least <distance> blocks away from where they are standing.",
                "The escape origin is locked the moment the command is run.",
                "",
                "Useful for retreating from mobs, escaping lava/fire, or getting out of tight",
                "spaces quickly without specifying a destination.",
                "",
                "Usage:",
                "> runaway            - flee 32 blocks away (default)",
                "> runaway <distance> - flee <distance> blocks away",
                "",
                "Aliases: #flee, #escape",
                "",
                "Use #cancel to stop at any time."
        );
    }
}
