package dihclient.util;

import dihclient.DihClientAddon;
import net.minecraft.util.Util;

import java.net.URI;
import java.nio.file.Path;
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
            openUri(url);
        } catch (Throwable ignored) {  }
    }

    /**
     * Opens {@code uri} with the system handler. A malformed one is logged, as vanilla's openUri(String) did
     * (26.3 moved opening links from Util's platform to Blaze3D).
     */
    public static void openUri(String uri) {
        try {
            openUri(URI.create(uri));
        } catch (IllegalArgumentException e) {
            DihClientAddon.LOG.error("[Dih] Couldn't open link {}", uri, e);
        }
    }

    /** Opens {@code uri} with the system handler (Util's platform on 26.2, Blaze3D on 26.3). */
    public static void openUri(URI uri) {
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openUri(uri);
        *///?} else {
        Util.getPlatform().openUri(uri);
        //?}
    }

    /** Opens a file or folder in the system file manager. */
    public static void openPath(Path path) {
        //? if >=26.3 {
        /*com.mojang.blaze3d.Blaze3D.openPath(path);
        *///?} else {
        Util.getPlatform().openPath(path);
        //?}
    }

    public static boolean isOpenableUrl(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:");
    }
}
