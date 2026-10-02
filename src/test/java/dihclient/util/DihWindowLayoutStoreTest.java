package dihclient.util;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DihWindowLayoutStoreTest {

    private static DihConfig reload(DihConfig config) {
        DihConfig loaded = new Gson().fromJson(new Gson().toJson(config), DihConfig.class);
        loaded.applyRuntimeDefaults();
        return loaded;
    }

    @Test
    void layoutsAndPinsRoundTripThroughTheConfigFile() {
        DihConfig config = new DihConfig();
        assertTrue(DihWindowLayoutStore.write(config, "DihPacketLoggerOverlay", new DihWindowLayout(120, 40, 320, 200, true, false), true));
        assertTrue(DihWindowLayoutStore.write(config, "macro-list", new DihWindowLayout(8, 9, 240, 150, false, true), false));

        Set<String> pinned = new HashSet<>();
        Map<String, DihWindowLayout> layouts = DihWindowLayoutStore.read(reload(config), pinned);

        DihWindowLayout logger = layouts.get("DihPacketLoggerOverlay");
        assertEquals(120, logger.x);
        assertEquals(40, logger.y);
        assertEquals(320, logger.width);
        assertEquals(200, logger.height);
        assertTrue(logger.visible);
        assertFalse(logger.collapsed);
        DihWindowLayout list = layouts.get("macro-list");
        assertTrue(list.collapsed);
        assertFalse(list.visible);
        assertEquals(Set.of("DihPacketLoggerOverlay"), pinned);
    }

    @Test
    void writingTheSameLayoutAgainReportsNoChange() {
        DihConfig config = new DihConfig();
        DihWindowLayout layout = new DihWindowLayout(1, 2, 300, 200, true, false);
        assertTrue(DihWindowLayoutStore.write(config, "w", layout, false));
        assertFalse(DihWindowLayoutStore.write(config, "w", layout, false));
        assertTrue(DihWindowLayoutStore.write(config, "w", layout, true));
    }

    @Test
    void anOldConfigWithoutTheFieldLoadsWithNoLayouts() {
        DihConfig old = new Gson().fromJson("{\"infiniChat\":false,\"overlayScale\":1.25}", DihConfig.class);
        old.applyRuntimeDefaults();

        assertTrue(DihWindowLayoutStore.read(old, new HashSet<>()).isEmpty());
        assertTrue(old.moduleToggleChat);
        assertEquals(1.0, old.uiTextScale);
        assertTrue(old.collapsedMacroFolders.isEmpty());
        assertFalse(old.infiniChat);
    }

    @Test
    void brokenEntriesAreSkipped() {
        DihConfig config = new Gson().fromJson(
            "{\"windowLayouts\":{\"a\":null,\"b\":{\"x\":5,\"y\":5,\"width\":0,\"height\":10},\"c\":{\"x\":5,\"y\":6,\"width\":200,\"height\":100,\"pinned\":true}}}",
            DihConfig.class);
        config.applyRuntimeDefaults();

        Set<String> pinned = new HashSet<>();
        Map<String, DihWindowLayout> layouts = DihWindowLayoutStore.read(config, pinned);

        assertEquals(Set.of("c"), layouts.keySet());
        assertEquals(Set.of("c"), pinned);
    }

    @Test
    void textScaleIsClampedToTheSupportedRange() {
        DihConfig config = new Gson().fromJson("{\"uiTextScale\":4.0}", DihConfig.class);
        config.applyRuntimeDefaults();
        assertEquals(1.5, config.uiTextScale);
        config.uiTextScale = 0.1;
        config.applyRuntimeDefaults();
        assertEquals(0.8, config.uiTextScale);
    }
}
