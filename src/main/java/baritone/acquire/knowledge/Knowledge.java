package baritone.acquire.knowledge;

import baritone.acquire.model.Source;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the planner knows about where items come from. All ids are namespaced registry ids
 * ("minecraft:oak_planks"). Implementations are immutable after construction and thread-safe to read.
 */
public interface Knowledge {

    /** Every known way to obtain {@code item}: recipes, blocks that drop it, mobs that drop it. */
    List<Source> sourcesFor(String item);

    /** Tool type of an item ("pickaxe", "axe", "shovel", "hoe", "sword", "shears"), or null if it is not a tool. */
    String toolType(String item);

    /** Tool tier of an item (see {@link baritone.acquire.model.ToolReq}), or 0 if it is not a tool. */
    int toolTier(String item);

    /** Tools of {@code type} with tier >= {@code minTier}, cheapest tier first. */
    List<String> toolsOf(String type, int minTier);

    /** Furnace fuels: item id -> burn time in ticks (coal = 1600, planks = 300, stick = 100). */
    Map<String, Integer> fuels();

    /** Whether {@code item} is a real item id. */
    boolean isItem(String item);

    /**
     * Turns what a user typed into an item id: "iron pick", "Iron Pickaxe", "iron_pickaxe" and
     * "minecraft:iron_pickaxe" all resolve to "minecraft:iron_pickaxe". Empty if nothing matches well.
     */
    Optional<String> resolveItem(String userText);

    /** Up to {@code limit} item ids that look like {@code userText}, best first, for "did you mean". */
    List<String> suggest(String userText, int limit);
}
