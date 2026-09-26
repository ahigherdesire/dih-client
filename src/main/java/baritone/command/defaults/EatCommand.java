package baritone.command.defaults;

import baritone.Baritone;
import baritone.acquire.exec.EatBehavior;
import baritone.acquire.exec.Foods;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.helpers.TabCompleteHelper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #eat}: eat the best healing food now, or a named one. One meal, on request; the eating itself is
 * {@link EatBehavior}, the same code {@code #acquire} heals with.
 */
public class EatCommand extends Command {

    public EatCommand(IBaritone baritone) {
        super(baritone, "eat");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        EatBehavior eater = ((Baritone) baritone).getEatBehavior();
        String rest = args.rawRest().trim();
        if (rest.equalsIgnoreCase("stop") || rest.equalsIgnoreCase("cancel")) {
            if (!eater.isBusy()) throw new CommandInvalidStateException("Not eating.");
            eater.cancel("stopped");
            logDirect("Stopped eating.");
            return;
        }
        try {
            logDirect(eater.eatNow(rest));
        } catch (IllegalArgumentException e) {
            throw new CommandInvalidStateException(e.getMessage());
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.getArgs().size() != 1 || ctx.player() == null) return Stream.empty();
        return new TabCompleteHelper()
                .append(Foods.held(ctx.player()).stream().map(f -> f.id().replace("minecraft:", "")))
                .append("stop")
                .filterPrefix(args.getArgs().getLast().getValue())
                .sortAlphabetically()
                .stream();
    }

    @Override
    public String getShortDesc() {
        return "Eat the best healing food now, or a named one";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The eat command eats one item from your inventory now, and never on its own.",
                "",
                "Without an item it picks one: the food that fills your missing food points with the least",
                "waste, then the most saturation. Harmful food (rotten flesh, spider eyes, raw chicken, ...)",
                "only when you're starving and have nothing else; golden apples only at or below",
                "acquireEmergencyHealth, where they're eaten even on a full food bar.",
                "Natural regeneration needs 18 food, so eating while hurt is how you heal.",
                "",
                "Pathing pauses while you eat. #stop or #eat stop lets go.",
                "",
                "Usage:",
                "> eat - Eat the best food for your health and hunger.",
                "> eat <item> - Eat that item, e.g. #eat bread or #eat golden_apple.",
                "> eat stop - Stop eating."
        );
    }
}
