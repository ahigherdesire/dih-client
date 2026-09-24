package dihclient.util;

import java.io.InputStream;

public final class DihLiteVariant {
    private static final boolean ENABLED = compute();

    private DihLiteVariant() {
    }

    private static boolean compute() {
        try (InputStream in = DihLiteVariant.class.getResourceAsStream("/dih-lite.marker")) {
            return in != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean enabled() {
        return ENABLED;
    }
}
