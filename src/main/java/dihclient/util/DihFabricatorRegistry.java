package dihclient.util;

import org.jetbrains.annotations.Nullable;

public class DihFabricatorRegistry {
    private static volatile DihFabricatorOverlay activeOverlay = null;

    public static void setActiveOverlay(@Nullable DihFabricatorOverlay overlay) {
        activeOverlay = overlay;
    }

    @Nullable
    public static DihFabricatorOverlay getActiveOverlay() {
        return activeOverlay;
    }
}
