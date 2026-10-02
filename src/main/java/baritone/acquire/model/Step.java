package baritone.acquire.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One thing the executor does. Every step names the item it is for and the inventory count of that
 * item the plan expects once the step is done ({@code untilCount}). The executor treats that count as
 * the finish line: if the inventory already has it, the step is skipped; if the step ends short, the
 * executor re-plans from the real inventory.
 */
public sealed interface Step permits Step.Mine, Step.Craft, Step.Smelt, Step.Kill, Step.Barter, Step.PlaceStation,
        Step.RetrieveStation, Step.Travel, Step.Locate, Step.SlayDragon, Step.CollectEgg {

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

    /**
     * Cook {@code times} of {@code input} in {@code recipe.station()}, burning {@code fuelCount} of {@code fuel}: split
     * across {@code furnaces} of them side by side when more than one, the others placed for it and taken back after.
     */
    record Smelt(SmeltSource recipe, int times, String input, String fuel, int fuelCount, int untilCount, int furnaces)
            implements Step {
        public Smelt {
            furnaces = Math.max(1, Math.min(furnaces, times));
        }

        public Smelt(SmeltSource recipe, int times, String input, String fuel, int fuelCount, int untilCount) {
            this(recipe, times, input, fuel, fuelCount, untilCount, 1);
        }

        @Override
        public String item() {
            return recipe.output();
        }

        /** What each furnace cooks, as even as it goes, the larger shares first. */
        public int[] shares() {
            return shares(times, furnaces);
        }

        public static int[] shares(int times, int furnaces) {
            int n = Math.max(1, Math.min(furnaces, Math.max(1, times)));
            int[] out = new int[n];
            for (int i = 0; i < n; i++) out[i] = times / n + (i < times % n ? 1 : 0);
            return out;
        }

        /** Fuel items of {@code burn} ticks each for {@code times} cooks of {@code heat} ticks in one furnace. */
        public static int fuelFor(int times, long heat, int burn) {
            if (times <= 0) return 0;
            return (int) Math.max(1, (times * heat + burn - 1) / burn);
        }

        @Override
        public String describe() {
            return "smelt " + times + " " + shortId(input) + " into " + shortId(recipe.output())
                    + (furnaces > 1 ? " in " + furnaces + " furnaces" : "") + " (fuel: " + fuelCount + " " + shortId(fuel) + ")";
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
     * Throw {@code currency} to {@code entity} ({@code trades} of it at most) and pick up what comes back, until the
     * inventory holds {@code untilCount} of {@code item}. A gold armour piece is worn while bartering with piglins.
     */
    record Barter(String entity, String currency, String item, int untilCount, int trades) implements Step {
        @Override
        public String describe() {
            return "barter ~" + trades + " " + shortId(currency) + " with " + shortId(entity) + "s for " + shortId(item);
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

    /** Break and pick up a station placed by this acquire before leaving the current work area. */
    record RetrieveStation(String station) implements Step {
        @Override public String item() { return station; }
        @Override public int untilCount() { return 0; }
        @Override public String describe() { return "retrieve " + shortId(station); }
    }

    /**
     * Go from {@code from} to {@code to} through a portal, using up {@code consumes} on the way: for the first nether
     * portal 10 obsidian, or the 20 mould blocks of a frame cast from lava (lit with a held flint and steel either
     * way); 12 eyes of ender to fill the end portal.
     */
    record Travel(Location from, Location to, Map<String, Integer> consumes) implements Step {
        public Travel {
            consumes = Collections.unmodifiableMap(new LinkedHashMap<>(consumes));
        }

        @Override public String item() { return ""; }
        @Override public int untilCount() { return 0; }

        @Override
        public String describe() {
            String what = switch (to) {
                case NETHER -> consumes.isEmpty() ? "go through the portal to the Nether"
                        : consumes.containsKey("minecraft:obsidian") ? "build and light a nether portal, then go through"
                        : "cast and light a nether portal from lava buckets, then go through";
                case END -> "fill the end portal" + (consumes.isEmpty() ? "" : " with " + consumes.values().iterator().next() + " ender_eye")
                        + " and jump in";
                default -> "go through the portal to " + to.label();
            };
            return what + " (" + from.label() + " to " + to.label() + ")";
        }
    }

    /** Find {@code site} (from the seed map or {@code #structure} when the seed is known, else by searching) and go there. */
    record Locate(Location site) implements Step {
        @Override public String item() { return ""; }
        @Override public int untilCount() { return 0; }
        @Override public String describe() { return "find " + site.label() + " and go there"; }
    }

    /** Kill the ender dragon (standing in the End). */
    record SlayDragon() implements Step {
        @Override public String item() { return ""; }
        @Override public int untilCount() { return 0; }
        @Override public String describe() { return "kill the ender dragon"; }
    }

    /** Knock the dragon egg off the exit portal and pick it up. */
    record CollectEgg(int untilCount) implements Step {
        public static final String EGG = "minecraft:dragon_egg";

        @Override public String item() { return EGG; }
        @Override public String describe() { return "collect the dragon egg"; }
    }

    static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
