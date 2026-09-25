package baritone.acquire.model;

import java.util.List;

/**
 * One thing the executor does. Every step names the item it is for and the inventory count of that
 * item the plan expects once the step is done ({@code untilCount}). The executor treats that count as
 * the finish line: if the inventory already has it, the step is skipped; if the step ends short, the
 * executor re-plans from the real inventory.
 */
public sealed interface Step permits Step.Mine, Step.Craft, Step.Smelt, Step.Kill, Step.PlaceStation {

    /** The item this step produces. For {@link PlaceStation} it is the station block's item. */
    String item();

    /** Inventory count of {@link #item()} expected after the step. */
    int untilCount();

    /** Short chat line, e.g. "mine 3 oak_log" or "craft 4 stick". No colour codes. */
    String describe();

    /**
     * Break any of {@code blocks} until the inventory holds {@code untilCount} of {@code item}.
     * {@code blocks} are all blocks that drop the item (stone and cobblestone both give cobblestone).
     */
    record Mine(List<String> blocks, String item, int untilCount, ToolReq tool, int expectedBlocks) implements Step {
        public Mine {
            blocks = List.copyOf(blocks);
        }

        @Override
        public String describe() {
            return "mine " + expectedBlocks + " " + shortId(blocks.get(0)) + (blocks.size() > 1 ? " (or similar)" : "")
                    + " for " + shortId(item);
        }
    }

    /**
     * Run {@code recipe} {@code times} times. {@code inputs} are the exact item ids chosen for each
     * ingredient (same order as {@code recipe.ingredients()}), so the executor does not have to guess
     * which plank type the planner meant.
     */
    record Craft(CraftSource recipe, int times, List<String> inputs, int untilCount) implements Step {
        public Craft {
            inputs = List.copyOf(inputs);
        }

        @Override
        public String item() {
            return recipe.output();
        }

        @Override
        public String describe() {
            return "craft " + (times * recipe.outputCount()) + " " + shortId(recipe.output())
                    + (recipe.needsTable() ? " (crafting table)" : "");
        }
    }

    /** Cook {@code times} of {@code input} in {@code recipe.station()}, burning {@code fuelCount} of {@code fuel}. */
    record Smelt(SmeltSource recipe, int times, String input, String fuel, int fuelCount, int untilCount) implements Step {
        @Override
        public String item() {
            return recipe.output();
        }

        @Override
        public String describe() {
            return "smelt " + times + " " + shortId(input) + " into " + shortId(recipe.output())
                    + " (fuel: " + fuelCount + " " + shortId(fuel) + ")";
        }
    }

    /** Kill {@code entity} until the inventory holds {@code untilCount} of {@code item}. Only this mob type. */
    record Kill(String entity, String item, int untilCount, int expectedKills) implements Step {
        @Override
        public String describe() {
            return "kill ~" + expectedKills + " " + shortId(entity) + " for " + shortId(item);
        }
    }

    /**
     * Make sure a {@code station} block ({@code minecraft:crafting_table}, {@code minecraft:furnace}, ...)
     * is usable nearby: use a known one within range, otherwise place one from the inventory. The
     * planner puts the station item in the plan before this step.
     */
    record PlaceStation(String station) implements Step {
        @Override
        public String item() {
            return station;
        }

        @Override
        public int untilCount() {
            return 0;
        }

        @Override
        public String describe() {
            return "set up a " + shortId(station);
        }
    }

    static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
