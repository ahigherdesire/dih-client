/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent store for player sighting data.
 *
 * <p>{@link baritone.behavior.ThreatsBehavior} calls {@link #updatePlayer} every
 * tick for each visible player entity. Records are written to
 * {@code baritone/player_memory.json} at most once every 15 seconds.
 */
public final class PlayerMemory {

    private PlayerMemory() {}

    // ── In-memory store ───────────────────────────────────────────────────────

    /** Keyed by UUID string. Insertion-ordered so iteration is consistent. */
    private static final Map<String, PlayerRecord> RECORDS = new LinkedHashMap<>();
    private static boolean loaded = false;

    /** Max sightings kept in history per player (older ones are dropped). */
    private static final int MAX_HISTORY = 20;

    // ── Throttled save ────────────────────────────────────────────────────────

    private static volatile long lastSaveScheduledAt = 0L;
    private static final long   SAVE_INTERVAL_MS     = 15_000L;

    // ── Public write API ──────────────────────────────────────────────────────

    /**
     * Called by {@link baritone.behavior.ThreatsBehavior} every tick for each
     * visible player. Updates or creates the sighting record; throttles disk writes.
     *
     * @param uuid player UUID
     * @param name display name
     * @param pos  current block position
     * @param dim  dimension id string (e.g. {@code "minecraft:overworld"})
     */
    public static void updatePlayer(UUID uuid, String name, BlockPos pos, String dim) {
        String world = WorldScope.currentWorldKey();
        String key = playerKey(world, uuid.toString());
        synchronized (RECORDS) {
            ensureLoaded();
            PlayerRecord rec = RECORDS.get(key);
            if (rec == null) {
                rec = new PlayerRecord(uuid.toString(), name, world, new ArrayList<>());
                RECORDS.put(key, rec);
            }
            rec.name = name; // update name (could change on some servers)

            Sighting sight = new Sighting(pos.getX(), pos.getY(), pos.getZ(), dim,
                    System.currentTimeMillis());
            rec.history.add(0, sight); // most recent first
            if (rec.history.size() > MAX_HISTORY) {
                rec.history.subList(MAX_HISTORY, rec.history.size()).clear();
            }
        }
        scheduleThrottledSave();
    }

    // ── Public read API ───────────────────────────────────────────────────────

    private static String playerKey(String world, String uuid) {
        return world + "|" + uuid;
    }

    /** All recorded players in the current world/server, most recently updated first. */
    public static List<PlayerRecord> allPlayers() {
        String world = WorldScope.currentWorldKey();
        synchronized (RECORDS) {
            ensureLoaded();
            List<PlayerRecord> list = new ArrayList<>();
            for (PlayerRecord rec : RECORDS.values()) {
                if (world.equals(rec.world)) list.add(rec);
            }
            list.sort((a, b) -> {
                long ta = a.history.isEmpty() ? 0 : a.history.get(0).timestamp;
                long tb = b.history.isEmpty() ? 0 : b.history.get(0).timestamp;
                return Long.compare(tb, ta); // descending
            });
            return list;
        }
    }

    /** Find a player by name (case-insensitive partial match) in the current world/server. */
    public static PlayerRecord findByName(String query) {
        String q = query.toLowerCase();
        String world = WorldScope.currentWorldKey();
        synchronized (RECORDS) {
            ensureLoaded();
            for (PlayerRecord rec : RECORDS.values()) {
                if (world.equals(rec.world) && rec.name.toLowerCase().contains(q)) return rec;
            }
        }
        return null;
    }

    /** Find a player by exact UUID string in the current world/server. */
    public static PlayerRecord findByUUID(String uuid) {
        synchronized (RECORDS) {
            ensureLoaded();
            return RECORDS.get(playerKey(WorldScope.currentWorldKey(), uuid));
        }
    }

    /** Total players recorded in the current world/server. */
    public static int size() {
        String world = WorldScope.currentWorldKey();
        synchronized (RECORDS) {
            ensureLoaded();
            int n = 0;
            for (PlayerRecord rec : RECORDS.values()) {
                if (world.equals(rec.world)) n++;
            }
            return n;
        }
    }

    /** Remove a player record by name (case-insensitive) in the current world/server. */
    public static boolean forget(String name) {
        String q = name.toLowerCase();
        String world = WorldScope.currentWorldKey();
        String found = null;
        synchronized (RECORDS) {
            ensureLoaded();
            for (Map.Entry<String, PlayerRecord> e : RECORDS.entrySet()) {
                PlayerRecord rec = e.getValue();
                if (world.equals(rec.world) && rec.name.toLowerCase().contains(q)) { found = e.getKey(); break; }
            }
            if (found != null) RECORDS.remove(found);
        }
        if (found != null) { scheduleThrottledSave(); return true; }
        return false;
    }

    /** Wipe the current world/server's player records. */
    public static void clear() {
        String world = WorldScope.currentWorldKey();
        synchronized (RECORDS) {
            ensureLoaded();
            RECORDS.values().removeIf(rec -> world.equals(rec.world));
        }
        scheduleThrottledSave();
    }

    // ── Data model ────────────────────────────────────────────────────────────

    /** One sighting (position snapshot at a point in time). */
    public static final class Sighting {
        public final int    x, y, z;
        public final String dim;
        public final long   timestamp;

        Sighting(int x, int y, int z, String dim, long timestamp) {
            this.x = x; this.y = y; this.z = z;
            this.dim = dim;
            this.timestamp = timestamp;
        }

        public String dimShort() {
            if (dim == null) return "?";
            if (dim.contains("overworld")) return "overworld";
            if (dim.contains("nether"))    return "nether";
            if (dim.contains("end"))       return "end";
            int c = dim.lastIndexOf(':');
            return c < 0 ? dim : dim.substring(c + 1);
        }
    }

    /** All data known about one player. */
    public static final class PlayerRecord {
        public final  String        uuid;
        public        String        name;
        /** World/server scope key — see {@link WorldScope#currentWorldKey()}. */
        public final  String        world;
        /** Sightings newest-first; max {@link #MAX_HISTORY} entries. */
        public final  List<Sighting> history;

        PlayerRecord(String uuid, String name, String world, List<Sighting> history) {
            this.uuid    = uuid;
            this.name    = name;
            this.world   = world == null ? "unknown" : world;
            this.history = history;
        }

        /** Most recent sighting, or {@code null} if history is empty. */
        public Sighting latest() {
            return history.isEmpty() ? null : history.get(0);
        }
    }

    // ── Lazy load ─────────────────────────────────────────────────────────────

    private static void ensureLoaded() {
        if (!loaded) { loaded = true; loadFromDisk(); }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path dataFile() {
        File gameDir = Minecraft.getInstance().gameDirectory;
        return new File(gameDir, "baritone/player_memory.json").toPath();
    }

    private static void scheduleThrottledSave() {
        long now = System.currentTimeMillis();
        if (now - lastSaveScheduledAt < SAVE_INTERVAL_MS) return;
        lastSaveScheduledAt = now;
        Thread t = new Thread(() -> {
            try { saveToDisk(); } catch (Exception ignored) {}
        }, "BaritonePlayerMemorySave");
        t.setDaemon(true);
        t.start();
    }

    private static void loadFromDisk() {
        Path path = dataFile();
        if (!Files.exists(path)) return;
        try {
            String      json = Files.readString(path);
            JsonObject  root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray   arr  = root.getAsJsonArray("players");
            if (arr == null) return;
            for (JsonElement el : arr) {
                try {
                    PlayerRecord rec = parseRecord(el.getAsJsonObject());
                    if (rec != null) RECORDS.put(playerKey(rec.world, rec.uuid), rec);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static PlayerRecord parseRecord(JsonObject o) {
        String uuid = o.get("uuid").getAsString();
        String name = o.get("name").getAsString();
        // Legacy records (pre world-scoping) have no "world" — tag "unknown".
        String world = o.has("world") ? o.get("world").getAsString() : "unknown";
        JsonArray ha = o.getAsJsonArray("history");
        List<Sighting> history = new ArrayList<>();
        if (ha != null) {
            for (JsonElement e : ha) {
                JsonObject s = e.getAsJsonObject();
                history.add(new Sighting(
                        s.get("x").getAsInt(), s.get("y").getAsInt(), s.get("z").getAsInt(),
                        s.has("dim") ? s.get("dim").getAsString() : "?",
                        s.has("ts")  ? s.get("ts").getAsLong()    : 0L));
            }
        }
        return new PlayerRecord(uuid, name, world, history);
    }

    private static synchronized void saveToDisk() {
        Path path = dataFile();
        try { Files.createDirectories(path.getParent()); }
        catch (Exception e) { return; }

        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray arr = new JsonArray();

        List<PlayerRecord> snapshot;
        synchronized (RECORDS) { snapshot = new ArrayList<>(RECORDS.values()); }

        for (PlayerRecord rec : snapshot) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid",  rec.uuid);
            o.addProperty("name",  rec.name);
            o.addProperty("world", rec.world);
            JsonArray ha = new JsonArray();
            for (Sighting s : rec.history) {
                JsonObject so = new JsonObject();
                so.addProperty("x",   s.x);
                so.addProperty("y",   s.y);
                so.addProperty("z",   s.z);
                so.addProperty("dim", s.dim);
                so.addProperty("ts",  s.timestamp);
                ha.add(so);
            }
            o.add("history", ha);
            arr.add(o);
        }
        root.add("players", arr);

        try { Files.writeString(path, GSON.toJson(root)); }
        catch (Exception ignored) {}
    }
}
