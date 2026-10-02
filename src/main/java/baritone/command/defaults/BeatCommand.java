package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.beat.BeatCampaign;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * {@code #beat}: beat the game, phase by phase (gear, the Nether, blaze rods, pearls, eyes, the stronghold, the End,
 * the dragon). The campaign lives in {@link BeatCampaign}; this parses the chat input.
 */
public class BeatCommand extends Command {

    private static final List<String> SUBCOMMANDS = List.of("plan", "resume", "stop", "status", "restart");

    public BeatCommand(IBaritone baritone) {
        super(baritone, "beat");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String sub = args.hasAny() ? args.getString().toLowerCase(Locale.ROOT) : "";
        BeatCampaign beat = ((Baritone) baritone).getBeatCampaign();
        try {
            String reply = switch (sub) {
                case "" -> beat.start();
                case "plan" -> beat.plan();
                case "resume" -> beat.resume();
                case "stop" -> beat.stop();
                case "status" -> beat.status();
                case "restart" -> beat.restart();
                default -> throw new CommandInvalidStateException("#beat " + sub + "? Try #beat, #beat plan, #beat resume, #beat stop.");
            };
            // start and resume say their own status line
            if (!sub.isEmpty() && !sub.equals("resume")) for (String line : reply.split("\n")) logDirect(line);
        } catch (IllegalArgumentException e) {
            throw new CommandInvalidStateException(e.getMessage());
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) return new TabCompleteHelper().append(SUBCOMMANDS.stream()).filterPrefix(args.getString()).stream();
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Beat the game: gear, the Nether, the End, the dragon";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Beats the game as a campaign of 9 phases: gear, a nether portal, blaze rods, pearls, home to the",
                "Overworld, eyes of ender, the stronghold, the End and the dragon. Progress is saved per world, so it",
                "survives a crash or restart.",
                "Parts that aren't built yet (going through portals, finding structures, the dragon fight) stop the",
                "campaign with a clear message; everything before them runs.",
                "",
                "Usage:",
                "> beat - start, or carry on with this world's saved campaign",
                "> beat plan - print every phase and step before anything runs",
                "> beat resume - continue the saved campaign",
                "> beat stop - stop (the campaign stays saved)",
                "> beat status - the phase and its progress",
                "> beat restart - forget the saved campaign and start over"
        );
    }
}
