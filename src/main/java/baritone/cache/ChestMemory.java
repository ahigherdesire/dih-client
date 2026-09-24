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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistent store for chest / container contents observed in-game.
 *
 * <p>When the player opens a container the {@link MixinMinecraft} hooks call
 * {@link #onContainerOpen(BlockPos)} with the BlockPos the player is looking at,
 * then {@link #onContainerClose(AbstractContainerMenu, String)} when the screen
 * closes. If both fire in quick succession and the container has at least one
 * item, the record is written to {@code baritone/chest_memory.json}.
 *
 * <p>All public methods are safe to call from any thread.
 */
public final class ChestMemory {

    private ChestMemory() {}

    // ── World scoping ─────────────────────────────────────────────────────────
    // Records are tagged with a key identifying the current server / singleplayer
    // world, and every query is filtered to that key. Without this, chests from
    // every server and save file land in one global database and show up in worlds
    // they don't belong to. Mirrors how WorldProvider picks per-world cache dirs:
    // singleplayer -> save-folder name, multiplayer -> server address.

    /** Stable identifier for the world/server the player is currently in. */
    public static String currentWorldKey() {
        return WorldScope.currentWorldKey();
    }

    /** Map key including the world scope so identical positions in different worlds don't collide. */
    private static String recordKey(String world, String dimension, BlockPos pos) {
        return world + "|" + dimension + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ── Pending interaction tracking ──────────────────────────────────────────
    // Set when a container screen opens (hitResult at that moment = the block).
    // Cleared when the screen closes or after TTL_MS without a close event.

    private static volatile BlockPos pendingPos       = null;
    private static volatile long     pendingTimestamp = 0L;
    /** Discard the pending pos if the screen hasn't closed within this many ms. */
    private static final long TTL_MS = 8_000L;

    /**
     * Called by {@code MixinMinecraft.onSetScreen} when a container screen
     * opens. {@code pos} is {@code mc.hitResult.getBlockPos()} at that instant —
     * the block the player just right-clicked to open the container.
     */
    public static void onContainerOpen(BlockPos pos) {
        pendingPos       = pos;
        pendingTimestamp = System.currentTimeMillis();
    }

    /**
     * Called by {@code MixinMinecraft.onSetScreen} when a container screen
     * closes. Reads all container-owned slots, filters air/empty, and if there
     * are items and a valid pending BlockPos, writes a record to disk.
     *
     * @param menu      the closing container menu
     * @param dimension registry key string like {@code "minecraft:overworld"}
     */
    public static void onContainerClose(AbstractContainerMenu menu, String dimension) {
        if (dimension == null) return;

        BlockPos pos = pendingPos;
        long     ts  = pendingTimestamp;
        if (pos == null) return;

        // Stale pending pos — the container was opened without a right-click
        // capture (automation, plugin message, etc.).
        if (System.currentTimeMillis() - ts > TTL_MS) {
            pendingPos = null;
            return;
        }
        pendingPos = null;

        // Collect items from the container's own slots (not the player inv).
        // Most menus: total_slots - 36 = container slots (the last 36 are
        // always the player's own inventory). For tiny containers (< 41 slots
        // total) we record all slots.
        List<Slot> allSlots = menu.slots;
        int containerSlotCount = allSlots.size() > 36
                ? allSlots.size() - 36
                : allSlots.size();

        List<SlotItem> items = new ArrayList<>();
        for (int i = 0; i < containerSlotCount; i++) {
            ItemStack stack = allSlots.get(i).getItem();
            if (stack.isEmpty()) continue;

            // Get the registry id — "minecraft:diamond" etc.
            String id = BuiltInRegistries.ITEM
                    .getResourceKey(stack.getItem())
                    .map(rk -> rk.identifier().toString())
                    .orElse("minecraft:air");

            if ("minecraft:air".equals(id)) continue;

            String displayName = stack.getDisplayName().getString();
            items.add(new SlotItem(id, stack.getCount(), displayName));
        }

        if (items.isEmpty()) return; // container was empty — not worth keeping

        String world = currentWorldKey();
        String key = recordKey(world, dimension, pos);
        synchronized (RECORDS) {
            ensureLoaded();
            RECORDS.put(key, new ChestRecord(pos, dimension, world, items,
                    System.currentTimeMillis()));
        }
        scheduleSave();
    }

    // ── In-memory store ───────────────────────────────────────────────────────

    /** Map key: {@code "dim:x,y,z"} — one entry per recorded container position. */
    private static final Map<String, ChestRecord> RECORDS = new LinkedHashMap<>();
    private static boolean loaded = false;

    /** Ensure the on-disk data has been loaded into {@link #RECORDS} (lazy, once). */
    private static void ensureLoaded() {
        if (!loaded) {
            loaded = true;
            loadFromDisk();
        }
    }

    // ── Public query API ──────────────────────────────────────────────────────

    /** How many distinct containers are recorded in the current world/server. */
    public static int size() {
        String world = currentWorldKey();
        synchronized (RECORDS) {
            ensureLoaded();
            int n = 0;
            for (ChestRecord rec : RECORDS.values()) {
                if (world.equals(rec.world)) n++;
            }
            return n;
        }
    }

    /** Snapshot of all recorded chests in the current world/server. */
    public static List<ChestRecord> allRecords() {
        String world = currentWorldKey();
        synchronized (RECORDS) {
            ensureLoaded();
            List<ChestRecord> out = new ArrayList<>();
            for (ChestRecord rec : RECORDS.values()) {
                if (world.equals(rec.world)) out.add(rec);
            }
            return out;
        }
    }

    /**
     * Search for containers whose item list has at least one entry matching
     * {@code query} (partial, case-insensitive match on item id OR display name).
     * Results are sorted by total matched-item count, descending.
     */
    public static List<SearchResult> search(String query) {
        String q = query.toLowerCase();
        String world = currentWorldKey();
        List<SearchResult> results = new ArrayList<>();
        synchronized (RECORDS) {
            ensureLoaded();
            for (ChestRecord rec : RECORDS.values()) {
                if (!world.equals(rec.world)) continue; // only the current world/server
                List<SlotItem> matched = new ArrayList<>();
                for (SlotItem item : rec.items) {
                    if (item.id.toLowerCase().contains(q)
                            || item.displayName.toLowerCase().contains(q)) {
                        matched.add(item);
                    }
                }
                if (!matched.isEmpty()) {
                    results.add(new SearchResult(rec, matched));
                }
            }
        }
        // Sort by total matched quantity (most items first)
        results.sort((a, b) -> {
            int ca = a.matchedItems.stream().mapToInt(s -> s.count).sum();
            int cb = b.matchedItems.stream().mapToInt(s -> s.count).sum();
            return Integer.compare(cb, ca);
        });
        return results;
    }

    /** Remove all records for the current world/server (in memory + on disk). */
    public static void clear() {
        String world = currentWorldKey();
        synchronized (RECORDS) {
            ensureLoaded();
            RECORDS.values().removeIf(rec -> world.equals(rec.world));
        }
        scheduleSave();
    }

    /** Remove the record for a specific position + dimension in the current world/server. */
    public static boolean forgetAt(BlockPos pos, String dim) {
        String key = recordKey(currentWorldKey(), dim, pos);
        boolean removed;
        synchronized (RECORDS) {
            removed = RECORDS.remove(key) != null;
        }
        if (removed) scheduleSave();
        return removed;
    }

    // ── Data model ────────────────────────────────────────────────────────────

    /** One item type in a slot (stack). */
    public record SlotItem(String id, int count, String displayName) {
        /** Registry path only, e.g. {@code "diamond"} from {@code "minecraft:diamond"}. */
        public String shortId() {
            int c = id.lastIndexOf(':');
            return c < 0 ? id : id.substring(c + 1);
        }
    }

    /** Snapshot of a recorded container. */
    public static final class ChestRecord {
        public final BlockPos      pos;
        public final String        dimension;
        /** World/server scope key — see {@link #currentWorldKey()}. */
        public final String        world;
        public final List<SlotItem> items;
        public final long          timestamp;

        ChestRecord(BlockPos pos, String dimension, String world, List<SlotItem> items, long timestamp) {
            this.pos       = pos;
            this.dimension = dimension;
            this.world     = world == null ? "unknown" : world;
            this.items     = Collections.unmodifiableList(new ArrayList<>(items));
            this.timestamp = timestamp;
        }

        /**
         * Compact summary, e.g. {@code "64× diamond, 3× sword, 12× bow"}.
         *
         * @param maxTypes max number of distinct item types to include
         */
        public String itemSummary(int maxTypes) {
            return items.stream()
                    .sorted((a, b) -> Integer.compare(b.count, a.count))
                    .limit(maxTypes)
                    .map(s -> s.count + "× " + s.shortId())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(empty)");
        }

        /** Short dimension label — "overworld", "nether", "end", or the full id. */
        public String dimShort() {
            if (dimension == null) return "?";
            if (dimension.contains("overworld")) return "overworld";
            if (dimension.contains("nether"))    return "nether";
            if (dimension.contains("end"))       return "end";
            int colon = dimension.lastIndexOf(':');
            return colon < 0 ? dimension : dimension.substring(colon + 1);
        }
    }

    /** One search hit: the container record plus only the slots that matched. */
    public static final class SearchResult {
        public final ChestRecord  record;
        public final List<SlotItem> matchedItems;

        SearchResult(ChestRecord record, List<SlotItem> matchedItems) {
            this.record       = record;
            this.matchedItems = matchedItems;
        }

        /** Total quantity of matching items across all matched slots. */
        public int matchedCount() {
            return matchedItems.stream().mapToInt(s -> s.count).sum();
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path dataFile() {
        File gameDir = Minecraft.getInstance().gameDirectory;
        return new File(gameDir, "baritone/chest_memory.json").toPath();
    }

    // Async save — called after every mutation so the file stays fresh.
    private static void scheduleSave() {
        Thread t = new Thread(() -> {
            try { saveToDisk(); } catch (Exception ignored) {}
        }, "BaritoneChestMemorySave");
        t.setDaemon(true);
        t.start();
    }

    private static void loadFromDisk() {
        Path path = dataFile();
        if (!Files.exists(path)) return;
        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("chests");
            if (arr == null) return;
            for (JsonElement el : arr) {
                try {
                    ChestRecord rec = parseRecord(el.getAsJsonObject());
                    if (rec != null) {
                        RECORDS.put(recordKey(rec.world, rec.dimension, rec.pos), rec);
                    }
                } catch (Exception ignored) {} // skip malformed entry
            }
        } catch (Exception ignored) {} // corrupt file — start fresh
    }

    private static ChestRecord parseRecord(JsonObject o) {
        String dim = o.get("dim").getAsString();
        // Legacy records (written before world-scoping) have no "world" field —
        // tag them "unknown" so they don't leak into every world's results.
        String world = o.has("world") ? o.get("world").getAsString() : "unknown";
        int    x   = o.get("x").getAsInt();
        int    y   = o.get("y").getAsInt();
        int    z   = o.get("z").getAsInt();
        long   ts  = o.has("ts") ? o.get("ts").getAsLong() : 0L;
        JsonArray arr = o.getAsJsonArray("items");
        if (arr == null) return null;
        List<SlotItem> items = new ArrayList<>();
        for (JsonElement e : arr) {
            JsonObject io  = e.getAsJsonObject();
            String     id  = io.get("id").getAsString();
            int        cnt = io.get("n").getAsInt();
            String     nm  = io.has("name") ? io.get("name").getAsString() : id;
            items.add(new SlotItem(id, cnt, nm));
        }
        if (items.isEmpty()) return null;
        return new ChestRecord(new BlockPos(x, y, z), dim, world, items, ts);
    }

    private static synchronized void saveToDisk() {
        Path path = dataFile();
        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) { return; }

        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray arr = new JsonArray();

        List<ChestRecord> snapshot;
        synchronized (RECORDS) {
            snapshot = new ArrayList<>(RECORDS.values());
        }

        for (ChestRecord rec : snapshot) {
            JsonObject o = new JsonObject();
            o.addProperty("dim",   rec.dimension);
            o.addProperty("world", rec.world);
            o.addProperty("x",   rec.pos.getX());
            o.addProperty("y",   rec.pos.getY());
            o.addProperty("z",   rec.pos.getZ());
            o.addProperty("ts",  rec.timestamp);
            JsonArray items = new JsonArray();
            for (SlotItem s : rec.items) {
                JsonObject io = new JsonObject();
                io.addProperty("id",   s.id);
                io.addProperty("n",    s.count);
                io.addProperty("name", s.displayName);
                items.add(io);
            }
            o.add("items", items);
            arr.add(o);
        }
        root.add("chests", arr);

        try {
            Files.writeString(path, GSON.toJson(root));
        } catch (Exception ignored) {}
    }
}
