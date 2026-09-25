package baritone.acquire.exec;

import java.util.Locale;

/**
 * Matches the planner's recipe ids against {@code DihCraftingHelper} recipe keys. Local keys are the
 * recipe's registry id ("minecraft:oak_planks"), with "#1", "#2" for extra displays of one recipe;
 * server-sent ones are "synced:&lt;index&gt;". Pure, so it is unit-tested.
 */
final class RecipeKeys {

    private RecipeKeys() {
    }

    /** "minecraft:stick", "stick" and "minecraft:stick#1" all normalise to "minecraft:stick". */
    static String base(String key) {
        if (key == null) return "";
        String k = key.trim().toLowerCase(Locale.ROOT);
        int hash = k.indexOf('#');
        if (hash >= 0) k = k.substring(0, hash);
        if (!k.isEmpty() && !k.contains(":")) k = "minecraft:" + k;
        return k;
    }

    /** 2 for the exact key, 1 for the same recipe under another display index or namespace spelling, 0 otherwise. */
    static int match(String helperKey, String plannerId) {
        if (helperKey == null || plannerId == null) return 0;
        if (helperKey.equalsIgnoreCase(plannerId)) return 2;
        return !base(plannerId).isEmpty() && base(helperKey).equals(base(plannerId)) ? 1 : 0;
    }
}
