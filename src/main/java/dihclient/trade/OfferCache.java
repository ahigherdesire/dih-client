package dihclient.trade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offers remembered per villager for one world or server, saved as JSON. A villager's entry is replaced each time
 * its trade screen opens. Thread-safe.
 */
public final class OfferCache {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 1;

    private final Map<String, VillagerOffers> byUuid = new LinkedHashMap<>();

    public synchronized void put(VillagerOffers villager) {
        if (villager != null && villager.uuid() != null) byUuid.put(villager.uuid(), villager);
    }

    public synchronized VillagerOffers get(String uuid) {
        return byUuid.get(uuid);
    }

    public synchronized void remove(String uuid) {
        byUuid.remove(uuid);
    }

    public synchronized List<VillagerOffers> all() {
        return new ArrayList<>(byUuid.values());
    }

    public synchronized int size() {
        return byUuid.size();
    }

    public synchronized void clear() {
        byUuid.clear();
    }

    private record File(int version, List<VillagerOffers> villagers) {
    }

    /** Writes the cache atomically (a temp file, then a move). */
    public void save(Path path) throws IOException {
        String json;
        synchronized (this) {
            json = GSON.toJson(new File(VERSION, new ArrayList<>(byUuid.values())));
        }
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** Loads a saved cache; a missing or unreadable file gives an empty cache. */
    public static OfferCache load(Path path) {
        OfferCache cache = new OfferCache();
        if (path == null || !Files.isRegularFile(path)) return cache;
        try {
            File file = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), new TypeToken<File>() { }.getType());
            if (file != null && file.villagers() != null) cache.putAll(file.villagers());
        } catch (IOException | JsonParseException | IllegalStateException e) {
            // A damaged cache is only a convenience lost; start empty.
        }
        return cache;
    }

    private synchronized void putAll(Collection<VillagerOffers> villagers) {
        for (VillagerOffers v : villagers) {
            if (v == null || v.uuid() == null) continue;
            // Gson bypasses the canonical constructor; rebuild so lists and maps are never null.
            List<TradeOffer> offers = new ArrayList<>();
            if (v.offers() != null) {
                for (TradeOffer o : v.offers()) {
                    if (o != null) offers.add(new TradeOffer(o.index(), o.resultId(), o.resultCount(), o.enchantments(),
                            o.costAId(), o.costACount(), o.costBId(), o.costBCount(), o.uses(), o.maxUses()));
                }
            }
            put(new VillagerOffers(v.uuid(), v.profession(), v.level(), v.xp(), v.x(), v.y(), v.z(), offers, v.seenMillis()));
        }
    }
}
