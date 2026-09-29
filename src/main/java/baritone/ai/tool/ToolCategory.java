package baritone.ai.tool;

import java.util.Locale;

/**
 * What a tool is about. The model always sees {@link #JOB} tools (and any tool flagged {@code job}); the rest it loads
 * a category at a time with {@code load_tools}, so the definitions it pays for stay small.
 */
public enum ToolCategory {
    JOB("whole tasks that finish by themselves"),
    MOVEMENT("walking, following, swimming, flying"),
    MINING("digging blocks and ores"),
    BUILDING("placing blocks and structures"),
    CRAFTING("crafting, smelting, brewing, enchanting"),
    INVENTORY("equipping, sorting, chests, eating"),
    COMBAT("fighting, shields, retreating"),
    TRADING("villager offers, trades, rerolls"),
    FARMING("crops, animals, fishing"),
    NETHER_END("portals, fortresses, strongholds, the dragon"),
    WORLD("waypoints, the seed map, structures, time and weather"),
    CLIENT("modules, settings, macros, servers, worlds"),
    KNOWLEDGE("recipes, loot, mobs and blocks from game data"),
    WEB("searching and reading the web"),
    CHAT("talking in chat"),
    RAW("every # and . command as-is; use the purpose-built tools first");

    private final String blurb;

    ToolCategory(String blurb) {
        this.blurb = blurb;
    }

    /** The name the model and {@code .tools} use: {@code nether_end}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String blurb() {
        return this.blurb;
    }

    /** The category for an id, ignoring case, spaces and dashes; null when there is none. */
    public static ToolCategory byId(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (ToolCategory category : values()) {
            if (category.name().equals(key)) {
                return category;
            }
        }
        return null;
    }
}
