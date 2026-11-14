package dihclient.schematic;

import java.util.Locale;

/**
 * How {@code .schem build} builds a placement.
 */
public enum SchematicBuildMode {
    /** Baritone's builder held to what a player can do: short reach, mouse-speed turns, vanilla click delay. Grim-safe. */
    LEGIT("Legit"),
    /** Baritone walks and gathers, and a printer places every block in reach each tick, from further away. Blatant. */
    FAST("Fast"),
    /** Creative only: takes every block from the creative inventory and flies the build, or pastes it with commands when you are op. */
    CREATIVE("Creative");

    private final String label;

    SchematicBuildMode(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    /**
     * @return the mode a word names ("legit", "fast", "creative", "paste", ...), or null
     */
    public static SchematicBuildMode parse(String text) {
        if (text == null) {
            return null;
        }
        return switch (text.strip().toLowerCase(Locale.ROOT)) {
            case "legit", "safe", "grim" -> LEGIT;
            case "fast", "blatant", "rage" -> FAST;
            case "creative", "paste", "dev", "instant" -> CREATIVE;
            default -> null;
        };
    }
}
