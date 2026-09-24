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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent store for named home positions.
 *
 * <p>Homes are saved to {@code baritone/homes.json} and persist across sessions.
 * Names are case-insensitive and stored in lower-case.
 *
 * <p>All public methods are thread-safe.
 */
public final class HomeMemory {

    private HomeMemory() {}

    // ── Data model ─────────────────────────────────────────────────────────────

    public static final class HomeRecord {
        public final String   name;
        public final BlockPos pos;
        public final String   dim;
        /** World/server scope key — see {@link WorldScope#currentWorldKey()}. */
        public final String   world;

        HomeRecord(String name, BlockPos pos, String dim, String world) {
            this.name  = name;
            this.pos   = pos;
            this.dim   = dim;
            this.world = world == null ? "unknown" : world;
        }

        /** Short dimension label — "overworld", "nether", "end", or the full id. */
        public String dimShort() {
            if (dim == null) return "?";
            if (dim.contains("overworld")) return "OW";
            if (dim.contains("nether"))    return "NE";
            if (dim.contains("end"))       return "END";
            int colon = dim.lastIndexOf(':');
            return colon < 0 ? dim : dim.substring(colon + 1);
        }
    }

    // ── In-memory store ────────────────────────────────────────────────────────

    /** Key is {@code world|lowercase-name} so the same home name in different worlds doesn't collide. */
    private static final Map<String, HomeRecord> HOMES  = new LinkedHashMap<>();
    private static       boolean                 loaded = false;

    private static void ensureLoaded() {
        if (!loaded) {
            loaded = true;
            loadFromDisk();
        }
    }

    private static String homeKey(String world, String name) {
        return world + "|" + name.toLowerCase();
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Save or overwrite a named home at {@code pos} in {@code dim} (in the current world/server). */
    public static void set(String name, BlockPos pos, String dim) {
        String world = WorldScope.currentWorldKey();
        String lname = name.toLowerCase();
        synchronized (HOMES) {
            ensureLoaded();
            HOMES.put(homeKey(world, lname), new HomeRecord(lname, pos, dim, world));
        }
        scheduleSave();
    }

    /** Return the record for {@code name} in the current world/server, or {@code null}. */
    public static HomeRecord get(String name) {
        synchronized (HOMES) {
            ensureLoaded();
            return HOMES.get(homeKey(WorldScope.currentWorldKey(), name));
        }
    }

    /** Snapshot of all saved homes in the current world/server. */
    public static List<HomeRecord> all() {
        String world = WorldScope.currentWorldKey();
        synchronized (HOMES) {
            ensureLoaded();
            List<HomeRecord> out = new ArrayList<>();
            for (HomeRecord rec : HOMES.values()) {
                if (world.equals(rec.world)) out.add(rec);
            }
            return out;
        }
    }

    /** List of all home names in the current world/server. */
    public static List<String> names() {
        String world = WorldScope.currentWorldKey();
        synchronized (HOMES) {
            ensureLoaded();
            List<String> out = new ArrayList<>();
            for (HomeRecord rec : HOMES.values()) {
                if (world.equals(rec.world)) out.add(rec.name);
            }
            return out;
        }
    }

    /** Delete a named home in the current world/server. Returns {@code true} if it existed. */
    public static boolean delete(String name) {
        boolean removed;
        synchronized (HOMES) {
            ensureLoaded();
            removed = HOMES.remove(homeKey(WorldScope.currentWorldKey(), name)) != null;
        }
        if (removed) scheduleSave();
        return removed;
    }

    /** How many homes are saved in the current world/server. */
    public static int size() {
        String world = WorldScope.currentWorldKey();
        synchronized (HOMES) {
            ensureLoaded();
            int n = 0;
            for (HomeRecord rec : HOMES.values()) {
                if (world.equals(rec.world)) n++;
            }
            return n;
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path dataFile() {
        File gameDir = Minecraft.getInstance().gameDirectory;
        return new File(gameDir, "baritone/homes.json").toPath();
    }

    private static void scheduleSave() {
        Thread t = new Thread(() -> {
            try { saveToDisk(); } catch (Exception ignored) {}
        }, "BaritoneHomeMemorySave");
        t.setDaemon(true);
        t.start();
    }

    private static void loadFromDisk() {
        Path path = dataFile();
        if (!Files.exists(path)) return;
        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("homes");
            if (arr == null) return;
            for (JsonElement el : arr) {
                try {
                    JsonObject o    = el.getAsJsonObject();
                    String     name = o.get("name").getAsString();
                    int        x    = o.get("x").getAsInt();
                    int        y    = o.get("y").getAsInt();
                    int        z    = o.get("z").getAsInt();
                    String     dim  = o.has("dim") ? o.get("dim").getAsString() : "minecraft:overworld";
                    // Legacy homes (pre world-scoping) have no "world" — tag "unknown".
                    String     world = o.has("world") ? o.get("world").getAsString() : "unknown";
                    HOMES.put(homeKey(world, name), new HomeRecord(name, new BlockPos(x, y, z), dim, world));
                } catch (Exception ignored) {} // skip malformed entry
            }
        } catch (Exception ignored) {} // corrupt file — start fresh
    }

    private static synchronized void saveToDisk() {
        Path path = dataFile();
        try { Files.createDirectories(path.getParent()); } catch (Exception e) { return; }

        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray arr = new JsonArray();

        List<HomeRecord> snapshot;
        synchronized (HOMES) {
            snapshot = new ArrayList<>(HOMES.values());
        }

        for (HomeRecord h : snapshot) {
            JsonObject o = new JsonObject();
            o.addProperty("name",  h.name);
            o.addProperty("world", h.world);
            o.addProperty("x",    h.pos.getX());
            o.addProperty("y",    h.pos.getY());
            o.addProperty("z",    h.pos.getZ());
            o.addProperty("dim",  h.dim);
            arr.add(o);
        }
        root.add("homes", arr);

        try { Files.writeString(path, GSON.toJson(root)); } catch (Exception ignored) {}
    }
}
