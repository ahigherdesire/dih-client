package baritone.acquire.knowledge;

import java.util.List;
import java.util.Set;

/**
 * Facts that live in game code rather than in data files. {@link #NONE} is the data-only fallback
 * used by tests; {@link RuntimeGameFacts} reads the game's registries.
 */
interface GameFacts {

    /** Every item id, or null when unknown (the item list is then derived from the data). */
    Set<String> itemIds();

    /** Whether {@code block} drops nothing without the correct tool, or null when unknown. */
    Boolean requiresCorrectTool(String block);

    /**
     * Block ids whose loot table is {@code table} ("minecraft:blocks/torch" -> torch and wall_torch),
     * or null when unknown (the table's own name is used).
     */
    List<String> blocksWithLootTable(String table);

    /** The item's translation key ("block.minecraft.stone"), or null when unknown. */
    String descriptionId(String item);

    GameFacts NONE = new GameFacts() {
        @Override
        public Set<String> itemIds() {
            return null;
        }

        @Override
        public Boolean requiresCorrectTool(String block) {
            return null;
        }

        @Override
        public List<String> blocksWithLootTable(String table) {
            return null;
        }

        @Override
        public String descriptionId(String item) {
            return null;
        }
    };
}
