package baritone.command.defaults;

import baritone.Baritone;
import baritone.acquire.exec.EatBehavior;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import net.minecraft.ChatFormatting;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #eat}: eat the best food you carry now, or the one you name. The eating itself is
 * {@link EatBehavior}, which {@code #acquire} also uses.
 */
public class EatCommand extends Command {

    public EatCommand(IBaritone baritone) {
        super(baritone, "eat");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String raw = args.rawRest().trim();
        EatBehavior eater = ((Baritone) baritone).getEatBehavior();
        if (raw.equalsIgnoreCase("stop") || raw.equalsIgnoreCase("cancel")) {
            if (eater.isBusy()) eater.cancel("stopped");
            else logDirect("Not eating.");
            return;
        }
        String id;
        try {
            id = eater.eatNow(raw.isEmpty() ? null : raw, failure -> {
                if (failure == null) logDirect("Done eating.", ChatFormatting.GRAY);
                else logDirect("Stopped eating: " + failure + ".", ChatFormatting.YELLOW);
            });
        } catch (IllegalArgumentException e) {
            throw new CommandInvalidStateException(e.getMessage());
        }
        logDirect("Eating " + (id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id) + ".");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Eat the best food you carry";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The eat command eats one item now. With no item it picks: a golden apple at",
                "acquireEmergencyHealth or below, otherwise the food that wastes the fewest points.",
                "It never picks chorus fruit, pufferfish, spider eyes, poisonous potatoes, rotten flesh",
                "or raw chicken; name one to eat it anyway. Baritone pauses while you eat.",
                "",
                "Usage:",
                "> eat - Eat the best food you carry.",
                "> eat <item> - Eat that, e.g. #eat bread.",
                "> eat stop - Stop eating."
        );
    }
}
