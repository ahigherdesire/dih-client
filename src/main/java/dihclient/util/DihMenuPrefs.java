package dihclient.util;

import dihclient.modules.PackHideState;

public final class DihMenuPrefs {
    private DihMenuPrefs() {
    }

    public static boolean customMainMenuEnabled() {

        if (DihLiteVariant.enabled()) return false;
        DihConfig config = DihConfig.getGlobal();
        return config == null || config.customMainMenu;
    }

    public static boolean vanillaMenuVisuals() {
        return PackHideState.isActive() || !customMainMenuEnabled();
    }
}
