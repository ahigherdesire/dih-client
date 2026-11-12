package dihclient.schematic;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Baritone settings a build mode needs, set for the length of one build and put back when it ends. Nothing is
 * saved, so a crash mid-build leaves the saved settings as they were.
 */
public final class SchematicBuildProfile {

    private static final Map<Settings.Setting<?>, Object> SAVED = new LinkedHashMap<>();
    private static SchematicBuildMode applied;

    private SchematicBuildProfile() {}

    public static synchronized SchematicBuildMode applied() {
        return applied;
    }

    /**
     * Puts back what an earlier build changed, then sets what {@code mode} needs.
     */
    public static synchronized void apply(SchematicBuildMode mode) {
        restore();
        Settings s = BaritoneAPI.getSettings();
        switch (mode) {
            case LEGIT -> {
                // Grim: reach stays well inside the 4.5 survival range, one place per 4 ticks like a held right
                // click, mouse-speed turns snapped to the sensitivity grid, and no placing mid-jump.
                set(s.blockReachDistance, 4.0f);
                set(s.rightClickSpeed, 4);
                set(s.maxRotationPerTick, 32f);
                set(s.blockFreeLook, false);
                set(s.antiCheatCompatibility, true);
                set(s.allowParkour, false);
                set(s.allowParkourPlace, false);
            }
            case FAST -> {
                set(s.blockReachDistance, 5.0f);
                set(s.rightClickSpeed, 1);
                set(s.maxRotationPerTick, 0f);
                set(s.blockFreeLook, true);
            }
            case CREATIVE -> {
                // the printer does it all; Baritone is not used
            }
        }
        applied = mode;
    }

    public static synchronized void restore() {
        for (Map.Entry<Settings.Setting<?>, Object> e : SAVED.entrySet()) {
            put(e.getKey(), e.getValue());
        }
        SAVED.clear();
        applied = null;
    }

    private static <T> void set(Settings.Setting<T> setting, T value) {
        SAVED.putIfAbsent(setting, setting.value);
        setting.value = value;
    }

    @SuppressWarnings("unchecked")
    private static <T> void put(Settings.Setting<T> setting, Object value) {
        setting.value = (T) value;
    }
}
