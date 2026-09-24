package dihclient.util;

import dihclient.DihClientAddon;
import net.minecraft.util.Util;

import java.util.Locale;

public final class DihLinks {
    /** Where "Website" buttons go. Points at the releases page until the site has a public URL. */
    public static final String WEBSITE = "https://github.com/ahigherdesire/dih-client/releases";
    public static final String SOURCE = "https://github.com/ahigherdesire/dih-client";

    private DihLinks() {
    }

    public static void open(String url) {
        if (!isOpenableUrl(url)) {
            DihClientAddon.LOG.warn("[Dih] Refused to open non-http(s) URL: {}", url);
            return;
        }
        try {
            Util.getPlatform().openUri(url);
        } catch (Throwable ignored) {  }
    }

    public static boolean isOpenableUrl(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:");
    }
}
