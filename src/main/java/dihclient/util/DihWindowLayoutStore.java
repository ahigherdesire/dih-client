package dihclient.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Converts window layouts to and from {@link DihConfig#windowLayouts}, so positions and pins survive a restart. */
final class DihWindowLayoutStore {
    private DihWindowLayoutStore() {
    }

    /** The saved layouts by window id; ids of pinned windows are added to {@code pinnedOut}. */
    static Map<String, DihWindowLayout> read(DihConfig config, Set<String> pinnedOut) {
        Map<String, DihWindowLayout> layouts = new LinkedHashMap<>();
        if (config == null || config.windowLayouts == null) return layouts;
        for (Map.Entry<String, DihConfig.SavedWindowLayout> entry : config.windowLayouts.entrySet()) {
            DihConfig.SavedWindowLayout saved = entry.getValue();
            if (entry.getKey() == null || entry.getKey().isEmpty() || saved == null) continue;
            if (saved.width <= 0 || saved.height <= 0) continue;
            layouts.put(entry.getKey(), new DihWindowLayout(saved.x, saved.y, saved.width, saved.height, saved.visible, saved.collapsed));
            if (saved.pinned && pinnedOut != null) pinnedOut.add(entry.getKey());
        }
        return layouts;
    }

    /** Stores one window; returns whether anything changed. */
    static boolean write(DihConfig config, String id, DihWindowLayout layout, boolean pinned) {
        if (config == null || id == null || id.isEmpty() || layout == null) return false;
        if (config.windowLayouts == null) config.windowLayouts = new LinkedHashMap<>();
        DihConfig.SavedWindowLayout saved = config.windowLayouts.get(id);
        if (saved != null && saved.x == layout.x && saved.y == layout.y && saved.width == layout.width
            && saved.height == layout.height && saved.visible == layout.visible && saved.collapsed == layout.collapsed
            && saved.pinned == pinned) {
            return false;
        }
        if (saved == null) {
            saved = new DihConfig.SavedWindowLayout();
            config.windowLayouts.put(id, saved);
        }
        saved.x = layout.x;
        saved.y = layout.y;
        saved.width = layout.width;
        saved.height = layout.height;
        saved.visible = layout.visible;
        saved.collapsed = layout.collapsed;
        saved.pinned = pinned;
        return true;
    }
}
