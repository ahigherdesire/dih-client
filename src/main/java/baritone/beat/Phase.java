package baritone.beat;

import baritone.acquire.model.Goal;
import baritone.acquire.model.Location;

import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * The steps of beating the game, in order. Each is one or more planner goals: gear, then reaching the Nether, blaze
 * rods, pearls, home to the Overworld through the same portal, eyes, the stronghold, the End and the dragon.
 */
public enum Phase {
    GEAR("gear"),
    PORTAL("the Nether"),
    BLAZE_RODS("blaze rods"),
    PEARLS("pearls"),
    HOME("home"),
    EYES("eyes"),
    STRONGHOLD("the stronghold"),
    END("the End"),
    DRAGON("the dragon");

    /** Blaze rods to get: 6 make the 12 powder 12 eyes need, plus one spare. */
    public static final int BLAZE_RODS_WANTED = 7;
    /** Pearls to get: 12 for the eyes, and 4 spare for eyes that break when thrown to find the stronghold. */
    public static final int PEARLS_WANTED = 16;
    public static final int EYES_WANTED = 12;

    /** The Nether gear: each piece is any one of its ids; the first is the one to make. */
    public static final List<List<String>> GEAR_PIECES = List.of(
            ids("iron_helmet", "diamond_helmet", "netherite_helmet"),
            ids("iron_chestplate", "diamond_chestplate", "netherite_chestplate"),
            ids("iron_leggings", "diamond_leggings", "netherite_leggings"),
            ids("iron_boots", "diamond_boots", "netherite_boots"),
            ids("shield"),
            ids("iron_sword", "diamond_sword", "netherite_sword"));

    private final String label;

    Phase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "Phase 3/9". */
    public String number() {
        return "Phase " + (ordinal() + 1) + "/" + values().length;
    }

    /**
     * The goals left in this phase given what is held: the missing gear pieces one by one, or the phase's single
     * goal. Empty when an item phase is already met (the location phases always have their goal).
     */
    public List<Goal> goals(ToIntFunction<String> have) {
        return switch (this) {
            case GEAR -> GEAR_PIECES.stream().filter(piece -> held(piece, have) == null)
                    .map(piece -> (Goal) new Goal.ItemGoal(piece.get(0), 1)).toList();
            case PORTAL -> List.of(new Goal.AtLocation(Location.NETHER));
            case BLAZE_RODS -> item("minecraft:blaze_rod", BLAZE_RODS_WANTED, have);
            case PEARLS -> item("minecraft:ender_pearl", PEARLS_WANTED, have);
            case HOME -> List.of(new Goal.AtLocation(Location.OVERWORLD));
            case EYES -> item("minecraft:ender_eye", EYES_WANTED, have);
            case STRONGHOLD -> List.of(new Goal.AtLocation(Location.STRONGHOLD));
            case END -> List.of(new Goal.AtLocation(Location.END));
            case DRAGON -> List.of(new Goal.DragonDead());
        };
    }

    /** "blaze rods 4/7", "gear 3/6", or just the label for a place. */
    public String progress(ToIntFunction<String> have) {
        return switch (this) {
            case GEAR -> label + " " + GEAR_PIECES.stream().filter(piece -> held(piece, have) != null).count() + "/" + GEAR_PIECES.size();
            case BLAZE_RODS -> label + " " + Math.min(have.applyAsInt("minecraft:blaze_rod"), BLAZE_RODS_WANTED) + "/" + BLAZE_RODS_WANTED;
            case PEARLS -> label + " " + Math.min(have.applyAsInt("minecraft:ender_pearl"), PEARLS_WANTED) + "/" + PEARLS_WANTED;
            case EYES -> label + " " + Math.min(have.applyAsInt("minecraft:ender_eye"), EYES_WANTED) + "/" + EYES_WANTED;
            default -> label;
        };
    }

    public static Phase byId(String id) {
        for (Phase phase : values()) if (phase.id().equalsIgnoreCase(id == null ? "" : id.trim())) return phase;
        return null;
    }

    private static List<Goal> item(String id, int count, ToIntFunction<String> have) {
        return have.applyAsInt(id) >= count ? List.of() : List.of(new Goal.ItemGoal(id, count));
    }

    /** The held id of a gear piece, or null. */
    private static String held(List<String> piece, ToIntFunction<String> have) {
        for (String id : piece) if (have.applyAsInt(id) > 0) return id;
        return null;
    }

    private static List<String> ids(String... names) {
        return java.util.Arrays.stream(names).map(name -> "minecraft:" + name).toList();
    }
}
