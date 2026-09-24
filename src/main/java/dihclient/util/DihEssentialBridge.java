package dihclient.util;

public final class DihEssentialBridge {

    private DihEssentialBridge() {
    }

    public static void disable(DihConfig config) {

    }

    public static void restoreIfOrphaned(DihConfig config) {

        if (config != null && config.essentialHiddenByPanic) {
            config.essentialHiddenByPanic = false;
            config.save();
        }
    }

    public static void restore(DihConfig config) {

        if (config != null && config.essentialHiddenByPanic) {
            config.essentialHiddenByPanic = false;
        }
    }
}
