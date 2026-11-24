package dihclient.security;

import dihclient.DihClientAddon;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;

/**
 * Keeps font files from untrusted packs away from the native FreeType library (minecraft2shell).
 *
 * <p>A "ttf" font provider hands the raw bytes of a pack file to FreeType's {@code FT_New_Memory_Face}, so a crafted
 * font in a server resource pack reaches native code with no validation. Server and world packs are written by
 * whoever runs the server or made the map, so their font files are refused here, before FreeType ever sees them.
 * The font provider then fails to load and vanilla skips it, falling back to the remaining providers. Fonts from
 * the game itself, mods and packs the player installed keep working.
 *
 * <p>This is always on: it does not depend on the DihProtector toggles, which an external protector mod or a
 * disabled protector would turn off.
 */
public final class DihFontFileGuard {
    private DihFontFileGuard() {
    }

    /** Opens the font file at {@code id} the way vanilla does, unless the winning copy comes from an untrusted pack. */
    public static InputStream open(ResourceManager manager, Identifier id) throws IOException {
        Resource resource = manager.getResourceOrThrow(id);
        PackResources source = resource.source();
        if (isUntrusted(source)) {
            String pack = resource.sourcePackId();
            DihClientAddon.LOG.warn("[DihProtector] Blocked TrueType font {} from untrusted pack {} (minecraft2shell guard).",
                id, pack);
            throw new IOException("Font file " + id + " from untrusted pack " + pack + " was blocked");
        }
        return resource.open();
    }

    static boolean isUntrusted(PackResources source) {
        if (source == null) return false;
        PackLocationInfo location = null;
        try {
            location = source.location();
        } catch (Throwable ignored) {
        }
        if (location != null) {
            PackSource packSource = location.source();
            if (packSource == PackSource.SERVER || packSource == PackSource.WORLD) return true;
        }
        // DownloadedPackSource names its packs "server/<hash>/<uuid>"; catch them even if a wrapper hides the source.
        String id = null;
        try {
            id = source.packId();
        } catch (Throwable ignored) {
        }
        return id != null && (id.startsWith("server/") || id.equals("server"));
    }
}
