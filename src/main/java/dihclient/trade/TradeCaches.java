package dihclient.trade;

import dihclient.DihClientAddon;
import dihclient.util.DihWaypoints;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The offer cache for the world or server you're in, saved under {@code trade-offers/} in the DIH folder, one file
 * per world (keyed like waypoints: the server address, or the singleplayer world name).
 */
public final class TradeCaches {

    private static String loadedKey;
    private static OfferCache cache = new OfferCache();

    private TradeCaches() {
    }

    /** The cache for the current world, loading it the first time. */
    public static synchronized OfferCache current() {
        String key = DihWaypoints.scopeKey(Minecraft.getInstance());
        if (!key.equals(loadedKey)) {
            loadedKey = key;
            cache = OfferCache.load(file(key));
        }
        return cache;
    }

    /** Records a villager's offers and saves the cache. */
    public static synchronized void record(VillagerOffers villager) {
        OfferCache c = current();
        c.put(villager);
        try {
            c.save(file(loadedKey));
        } catch (IOException e) {
            // Not fatal: the offers stay cached for this session.
        }
    }

    static Path file(String key) {
        String safe = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        if (safe.isBlank()) safe = "unknown";
        return new File(new File(DihClientAddon.FOLDER, "trade-offers"), safe + ".json").toPath();
    }
}
