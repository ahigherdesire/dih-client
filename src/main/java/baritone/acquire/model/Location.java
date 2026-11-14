package baritone.acquire.model;

import java.util.Locale;

/**
 * Where a source is, and where the player is while a plan runs. The three dimensions, plus sites inside them that
 * must be found before they can be used (a fortress in the Nether, the stronghold in the Overworld).
 */
public enum Location {
    OVERWORLD(null),
    NETHER(null),
    END(null),
    FORTRESS(NETHER),
    STRONGHOLD(OVERWORLD);

    private final Location parent;

    Location(Location parent) {
        this.parent = parent;
    }

    /** The dimension this is in: itself for a dimension, the parent for a site. */
    public Location dimension() {
        return parent == null ? this : parent;
    }

    public boolean isSite() {
        return parent != null;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "the Nether", "a nether fortress": for chat lines. */
    public String label() {
        return switch (this) {
            case OVERWORLD -> "the Overworld";
            case NETHER -> "the Nether";
            case END -> "the End";
            case FORTRESS -> "a nether fortress";
            case STRONGHOLD -> "the stronghold";
        };
    }

    /** The location named {@code id} ("nether", "FORTRESS"), or null. */
    public static Location byId(String id) {
        if (id == null) return null;
        for (Location location : values()) if (location.id().equalsIgnoreCase(id.trim())) return location;
        return null;
    }
}
