package dihclient.addons;

import dihclient.addons.bundled.CoordsAddon;
import dihclient.addons.bundled.DeathPointAddon;
import dihclient.addons.bundled.SessionStatsAddon;
import dihclient.api.DihAddon;
import dihclient.util.DihConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Addons shipped inside the client jar. They load through {@link AddonManager} like jar addons, but stay off until
 * the user enables them in the Addons screen ({@link DihConfig#enabledBundledAddonIds}), then restarts.
 */
public final class BundledAddons {
    public record Entry(String id, String name, String version, String authors, String description, int color,
                        Supplier<DihAddon> factory) {}

    public static final List<Entry> ALL = List.of(
        new Entry(CoordsAddon.ID, "Coords HUD", "1.0.0", "DIH",
            "XYZ, facing and Nether coords. Adds .coords", 0xFF67E8F9, CoordsAddon::new),
        new Entry(DeathPointAddon.ID, "Death Point", "1.0.0", "DIH",
            "Shows where you died. Adds .deathpoint", 0xFFEF4444, DeathPointAddon::new),
        new Entry(SessionStatsAddon.ID, "Session Stats", "1.0.0", "DIH",
            "Time played, blocks walked and FPS", 0xFF8BA6FF, SessionStatsAddon::new)
    );

    private BundledAddons() {}

    public static boolean isBundled(String id) {
        for (Entry entry : ALL) {
            if (entry.id().equalsIgnoreCase(id == null ? "" : id.trim())) return true;
        }
        return false;
    }

    public static boolean isEnabled(String id) {
        List<String> enabled = DihConfig.getGlobal().enabledBundledAddonIds;
        if (enabled == null || id == null) return false;
        for (String value : enabled) {
            if (value != null && value.trim().equalsIgnoreCase(id.trim())) return true;
        }
        return false;
    }

    public static void setEnabled(String id, boolean enabled) {
        DihConfig config = DihConfig.getGlobal();
        if (config.enabledBundledAddonIds == null) config.enabledBundledAddonIds = new ArrayList<>();
        String normalized = id.trim();
        config.enabledBundledAddonIds.removeIf(value -> value != null && value.trim().equalsIgnoreCase(normalized));
        if (enabled) config.enabledBundledAddonIds.add(normalized);
        config.save();
    }
}
