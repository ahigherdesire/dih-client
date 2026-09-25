package baritone.command.defaults;

import baritone.Baritone;
import baritone.acquire.exec.AcquireArgs;
import baritone.acquire.exec.AcquireProcess;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.helpers.TabCompleteHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code #acquire}: get an item from whatever you have now, by mining, crafting, smelting and killing
 * mobs as needed. The work happens in {@link AcquireProcess}; this parses the chat input.
 */
public class AcquireCommand extends Command {

    private static final List<String> SUBCOMMANDS = List.of("plan", "status", "stop", "food");

    public AcquireCommand(IBaritone baritone) {
        super(baritone, "acquire", "aquire");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        AcquireArgs parsed;
        try {
            parsed = AcquireArgs.parse(args.rawRest());
        } catch (IllegalArgumentException e) {
            throw new CommandInvalidStateException(e.getMessage());
        }
        AcquireProcess acquire = ((Baritone) baritone).getAcquireProcess();
        switch (parsed.mode()) {
            case STATUS -> logDirect(acquire.isActive() ? acquire.status() : "Not acquiring anything. Try #acquire iron_pickaxe or #help acquire.");
            case STOP -> {
                if (acquire.isActive()) acquire.stop();
                else logDirect("Not acquiring anything.");
            }
            case PLAN -> {
                try {
                    for (String line : acquire.plan(parsed.item(), parsed.count()).split("\n")) logDirect(line);
                } catch (IllegalArgumentException e) {
                    throw new CommandInvalidStateException(e.getMessage());
                }
            }
            case ACQUIRE -> {
                try {
                    acquire.start(parsed.item(), parsed.count()); // logs its own start line
                } catch (IllegalArgumentException e) {
                    throw new CommandInvalidStateException(e.getMessage());
                }
            }
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        int words = args.getArgs().size();
        if (words == 0) return Stream.empty();
        String last = args.getArgs().getLast().getValue();
        TabCompleteHelper helper = new TabCompleteHelper();
        if (words == 1) helper.append(SUBCOMMANDS.stream());
        return helper.append(itemIds())
                .filterPrefix(last)
                .sortAlphabetically()
                .stream();
    }

    /** Item ids as typed: "iron_pickaxe" for vanilla items, the full id for modded ones. */
    private static Stream<String> itemIds() {
        return BuiltInRegistries.ITEM.keySet().stream()
                .map(Identifier::toString)
                .map(id -> id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id)
                .filter(id -> !id.equals("air"));
    }

    @Override
    public String getShortDesc() {
        return "Get an item: mine, craft, smelt and hunt for it";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The acquire command gets an item for you from whatever you have now. It works out the",
                "whole chain (punch trees, planks, crafting table, wooden pickaxe, cobblestone, ...) and",
                "runs it: mining with Baritone, crafting in your inventory or at a crafting table,",
                "smelting in a furnace and killing mobs for their drops.",
                "",
                "It counts what you already carry, uses a crafting table or furnace within",
                "acquireStationRadius blocks, and otherwise places its own (acquirePlaceStations).",
                "Progress goes to chat. When a step comes up short, fails, a tool breaks or you die, it",
                "re-plans from your real inventory (at most acquireMaxReplans times).",
                "",
                "Health comes first (acquireHeal): while it runs it eats when you're hurt and below 18 food,",
                "or at 6 food, eats a golden apple at acquireEmergencyHealth (backing off from a fight first),",
                "and with no safe food and health at acquireHealHealth or below it gets food before going on.",
                "It never runs server commands, teleports or respawns for you. It only drops items",
                "when acquireDropJunk is on; otherwise a full inventory stops it with a message.",
                "#stop, #cancel and Flee stop it like any other Baritone task, #pause pauses it.",
                "",
                "Items can be typed loosely: \"iron pick\", \"Iron Pickaxe\", iron_pickaxe, minecraft:iron_pickaxe.",
                "",
                "Usage:",
                "> acquire <item> [count] - Get the item, e.g. #acquire stone_pickaxe or #acquire torch 64.",
                "> acquire <count> <item> - Same, count first: #acquire 64 torch.",
                "> acquire food [count] - The cheapest food: enough for 10-20 food points, or count items of it.",
                "> acquire plan <item> [count] - Print the steps without doing anything.",
                "> acquire status - Show the current step and progress.",
                "> acquire stop - Stop acquiring (so does #stop).",
                "",
                "Settings: acquirePlaceStations, acquireKillMobs, acquireStationRadius, acquireMaxReplans,",
                "acquireStepTimeoutSeconds, acquireDropJunk, acquireHeal, acquireHealHealth,",
                "acquireEmergencyHealth (see #set)."
        );
    }
}
