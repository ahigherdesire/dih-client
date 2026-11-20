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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Persistent store for chest / container contents observed in-game.
 *
 * <p>When the player opens a container the GUI screen hook calls
 * {@link #onContainerOpen(AbstractContainerMenu)} with the menu,
 * then {@link #onContainerClose(AbstractContainerMenu, String)} when the screen
 * closes. Once the server has synced its contents, the snapshot is written to
 * {@code baritone/chest_memory.json}. Empty snapshots remove old records.
 *
 * <p>Lifecycle hooks run on the client thread. Queries and persistence synchronize access to records.
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
    // Bound to the exact menu and level, not to an arbitrary wall-clock timeout.
    private static Capture pending;
    private static Interaction interaction;

    /** Observes Gui.setScreen, which both vanilla container opening and closing actually use. */
    public static void onScreenChange(Minecraft mc, Screen newScreen) {
        Screen old = mc.gui.screen();
        if (old == newScreen) return;
        boolean oldContainer = old instanceof AbstractContainerScreen<?>;
        if (old instanceof AbstractContainerScreen<?> previous && mc.level != null) {
            onContainerClose(previous.getMenu(), mc.level.dimension().identifier().toString());
        }
        if (!oldContainer && newScreen instanceof AbstractContainerScreen<?> next) {
            onContainerOpen(next.getMenu());
        } else {
            // Screen replacement, disconnection or an unrelated GUI ends the association.
            cancelCapture();
        }
    }

    /** Record the actual use target rather than a later, possibly unrelated crosshair position. */
    public static void onBlockInteraction(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        interaction = mc.level == null || pos == null ? null
            : new Interaction(mc.level, pos.immutable(), System.currentTimeMillis());
    }

    record Interaction(WeakReference<Object> level, BlockPos pos, long timestamp) {
        Interaction(Object level, BlockPos pos, long timestamp) {
            this(new WeakReference<>(level), pos, timestamp);
        }

        BlockPos position(Object currentLevel, long now) {
            // This timeout only covers the wait for the server to open a menu, not time spent using it.
            long elapsed = now - timestamp;
            return currentLevel != null && level.get() == currentLevel && elapsed >= 0 && elapsed <= 5_000 ? pos : null;
        }
    }

    /**
     * Called by {@link #onScreenChange} when a container screen
     * opens. Consume the recent block interaction, then validate its physical storage/menu pairing.
     */
    public static void onContainerOpen(AbstractContainerMenu menu) {
        pending = null;
        Minecraft mc = Minecraft.getInstance();
        Interaction clicked = interaction;
        interaction = null;
        BlockPos pos = clicked == null ? null : clicked.position(mc.level, System.currentTimeMillis());
        if (pos == null || mc.level == null || mc.player == null || menu == null
                || menu == mc.player.inventoryMenu || !mc.level.isLoaded(pos)) return;
        BlockState state = mc.level.getBlockState(pos);
        int slots;
        try {
            slots = storageSlotCount(state, menu.getType());
        } catch (UnsupportedOperationException e) {
            return; // inventory has no registered menu type
        }
        if (slots == 0 || menu.slots.size() != slots + 36 || mc.level.getBlockEntity(pos) == null) return;
        BlockPos partner = null;
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            partner = pos.relative(ChestBlock.getConnectedDirection(state));
            if (!mc.level.isLoaded(partner)) return;
            BlockState other = mc.level.getBlockState(partner);
            if (!other.is(state.getBlock()) || other.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                || other.getValue(ChestBlock.FACING) != state.getValue(ChestBlock.FACING)
                || !partner.relative(ChestBlock.getConnectedDirection(other)).equals(pos)) return;
            // A double chest is one record whichever half was clicked. Replace legacy records for both halves.
            if (partner.compareTo(pos) < 0) {
                BlockPos swap = pos;
                pos = partner;
                partner = swap;
            }
        }
        pending = new Capture(mc.level, menu, menu.containerId, currentWorldKey(),
            mc.level.dimension().identifier().toString(), pos.immutable(), partner);
    }

    /** Called only after the server's full contents packet has been applied. */
    public static void onContainerSynced(int containerId) {
        if (pending != null) pending.synced(containerId);
    }

    public static void cancelCapture() {
        pending = null;
        interaction = null;
    }

    /** Expected storage-owned slots; excludes personal ender storage and non-storage menus. */
    public static int storageSlotCount(BlockState state, MenuType<?> type) {
        if (state.getBlock() instanceof ChestBlock) {
            boolean single = state.getValue(ChestBlock.TYPE) == ChestType.SINGLE;
            return type == (single ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6) ? (single ? 27 : 54) : 0;
        }
        if (state.is(Blocks.BARREL) && type == MenuType.GENERIC_9x3) return 27;
        if (state.getBlock() instanceof ShulkerBoxBlock && type == MenuType.SHULKER_BOX) return 27;
        if ((state.is(Blocks.DISPENSER) || state.is(Blocks.DROPPER)) && type == MenuType.GENERIC_3x3) return 9;
        if (state.is(Blocks.HOPPER) && type == MenuType.HOPPER) return 5;
        if (state.is(Blocks.FURNACE) && type == MenuType.FURNACE) return 3;
        if (state.is(Blocks.BLAST_FURNACE) && type == MenuType.BLAST_FURNACE) return 3;
        if (state.is(Blocks.SMOKER) && type == MenuType.SMOKER) return 3;
        if (state.is(Blocks.BREWING_STAND) && type == MenuType.BREWING_STAND) return 5;
        if (state.is(Blocks.CRAFTER) && type == MenuType.CRAFTER_3x3) return 9;
        return 0;
    }

    static final class Capture {
        final Object level, menu;
        final int containerId;
        final String world, dimension;
        final BlockPos pos, partner;
        private boolean synced;

        Capture(Object level, Object menu, int containerId, String world, String dimension, BlockPos pos, BlockPos partner) {
            this.level = level;
            this.menu = menu;
            this.containerId = containerId;
            this.world = world;
            this.dimension = dimension;
            this.pos = pos;
            this.partner = partner;
        }

        void synced(int id) {
            if (id == containerId) synced = true;
        }

        ChestRecord snapshot(Object level, Object menu, String world, String dimension, List<SlotItem> items, long now) {
            if (!synced || this.level != level || this.menu != menu || !this.world.equals(world)
                    || !this.dimension.equals(dimension)) return null;
            return new ChestRecord(pos, dimension, world, items, now);
        }
    }

    /**
     * Called by {@link #onScreenChange} when a container screen
     * closes. Reads the validated storage-owned slots and replaces the previous
     * snapshot, removing the record if the container is now empty.
     *
     * @param menu      the closing container menu
     * @param dimension registry key string like {@code "minecraft:overworld"}
     */
    public static void onContainerClose(AbstractContainerMenu menu, String dimension) {
        Capture capture = pending;
        pending = null;
        if (capture == null || dimension == null || menu == null) return;
        Minecraft mc = Minecraft.getInstance();

        // Collect items from the container's own slots (not the player inv).
        // onContainerOpen has validated the vanilla menu type and exact slot count;
        // the final 36 slots are the player's inventory, never remembered here.
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

        ChestRecord snapshot = capture.snapshot(mc.level, menu, currentWorldKey(), dimension, items,
            System.currentTimeMillis());
        if (snapshot == null) return;
        synchronized (RECORDS) {
            ensureLoaded();
            STORE.replace(snapshot, capture.partner);
        }
        scheduleSave();
    }

    // ── In-memory store ───────────────────────────────────────────────────────

    /** One entry per world, dimension and recorded container position. */
    private static final Store STORE = new Store();
    private static final Map<String, ChestRecord> RECORDS = STORE.records;
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
        String world = currentWorldKey();
        synchronized (RECORDS) {
            ensureLoaded();
            return STORE.search(world, query);
        }
    }

    /** Shared in-memory index. Disk IO and Minecraft lifecycle remain in the facade. */
    static final class Store {
        final Map<String, ChestRecord> records = new LinkedHashMap<>();

        void put(ChestRecord record) {
            String key = recordKey(record.world, record.dimension, record.pos);
            if (record.items.isEmpty()) records.remove(key);
            else records.put(key, record);
        }

        void replace(ChestRecord record, BlockPos partner) {
            if (partner != null) records.remove(recordKey(record.world, record.dimension, partner));
            put(record);
        }

        List<SearchResult> search(String world, String query) {
            String q = normalize(query);
            List<SearchResult> results = new ArrayList<>();
            if (q.isEmpty()) return results;
            for (ChestRecord rec : records.values()) {
                if (!world.equals(rec.world)) continue;
                List<SlotItem> matched = new ArrayList<>();
                for (SlotItem item : rec.items) {
                    if (normalize(item.id).contains(q) || normalize(item.displayName).contains(q)) {
                        matched.add(item);
                    }
                }
                if (!matched.isEmpty()) {
                    results.add(new SearchResult(rec, matched));
                }
            }
            results.sort((a, b) -> Integer.compare(b.matchedCount(), a.matchedCount()));
            return results;
        }

        private static String normalize(String text) {
            return text == null ? "" : text.strip().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
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
            ensureLoaded();
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
