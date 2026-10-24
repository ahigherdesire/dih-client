package dihclient.schematic;

import baritone.utils.schematic.LoadedSchematic;
import baritone.utils.schematic.format.SchematicLoadException;
import baritone.utils.schematic.format.SchematicLoader;
import dihclient.DihClientAddon;
import dihclient.util.DihWaypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * The player's schematic placements: which files are placed where, per server or singleplayer world, loaded in the
 * background and saved to {@code config/dih/schematic-placements.json} so they survive relogs. Everything here runs on
 * the client thread except reading schematic files and writing the save file.
 */
public final class Schematics {

    private static final Schematics INSTANCE = new Schematics();
    private static final File STORE = new File(DihClientAddon.FOLDER, "schematic-placements.json");
    private static final long SAVE_DELAY_MS = 500;
    private static final int MAX_PLACEMENTS = 64;
    private static final int MAX_DEPTH = 4;

    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> daemon(r, "dih-schematic-loader"));
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "dih-schematic-saver"));
    private final Map<String, Cached> cache = new HashMap<>();

    private Map<String, SchematicPlacementStore.Scope> stored;
    private String scope;
    private final List<SchematicPlacement> placements = new ArrayList<>();
    private String selected = "";
    private int revision;
    private ScheduledFuture<?> pendingSave;

    private record Cached(long modified, long size, LoadedSchematic schematic) {}

    private Schematics() {}

    public static Schematics get() {
        return INSTANCE;
    }

    private static Thread daemon(Runnable r, String name) {
        Thread thread = new Thread(r, name);
        thread.setDaemon(true);
        return thread;
    }

    /**
     * @return {@code <game dir>/schematics}, where {@code #build} looks too
     */
    public static File folder() {
        return new File(Minecraft.getInstance().gameDirectory, "schematics");
    }

    /**
     * @return the schematic files in the folder and its subfolders, as paths relative to it, sorted
     */
    public static List<String> files() {
        Path root = folder().toPath();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root, MAX_DEPTH)) {
            return walk.filter(Files::isRegularFile)
                .filter(p -> SchematicLoader.EXTENSIONS.contains(extension(p.getFileName().toString())))
                .map(p -> root.relativize(p).toString().replace('\\', '/'))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * @return the file for a path relative to the schematics folder, or null if it would leave the folder
     */
    public static File resolve(String relative) {
        Path root = folder().toPath().toAbsolutePath().normalize();
        Path file = root.resolve(relative).normalize();
        return file.startsWith(root) ? file.toFile() : null;
    }

    // ---- world scope ----

    /**
     * Switches to the placements of the server or world the player is in. Called on join and every tick, so a
     * dimension or server change is picked up without a dedicated hook.
     */
    public void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        String key = DihWaypoints.scopeKey(mc);
        if (!key.equals(this.scope)) {
            enter(key);
        }
    }

    private void enter(String key) {
        leave();
        ensureStored();
        this.scope = key;
        SchematicPlacementStore.Scope saved = this.stored.get(key);
        if (saved != null) {
            this.placements.addAll(saved.placements());
            this.selected = saved.selected();
        }
        for (SchematicPlacement p : this.placements) {
            load(p);
        }
        this.revision++;
    }

    /**
     * Saves and forgets the current world's placements; called when the player leaves.
     */
    public void leave() {
        if (this.scope != null) {
            remember();
            flush();
        }
        this.scope = null;
        this.placements.clear();
        this.selected = "";
        this.revision++;
    }

    private void ensureStored() {
        if (this.stored != null) {
            return;
        }
        String json = "";
        try {
            if (STORE.isFile()) {
                json = Files.readString(STORE.toPath(), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            DihClientAddon.LOG.warn("Could not read schematic placements", e);
        }
        this.stored = new LinkedHashMap<>(SchematicPlacementStore.read(json));
    }

    private void remember() {
        ensureStored();
        if (this.scope != null) {
            this.stored.put(this.scope, new SchematicPlacementStore.Scope(this.selected, this.placements));
        }
    }

    // ---- placements ----

    public List<SchematicPlacement> placements() {
        return List.copyOf(this.placements);
    }

    /**
     * @return the placements in a dimension, for drawing and checking
     */
    public List<SchematicPlacement> placementsIn(String dimension) {
        List<SchematicPlacement> out = new ArrayList<>();
        for (SchematicPlacement p : this.placements) {
            if (p.dimension().isEmpty() || p.dimension().equals(dimension)) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * @return a number that changes whenever a placement is added, removed, selected or changed
     */
    public int revision() {
        int sum = this.revision;
        for (SchematicPlacement p : this.placements) {
            sum = sum * 31 + p.revision();
        }
        return sum;
    }

    public SchematicPlacement selected() {
        for (SchematicPlacement p : this.placements) {
            if (p.id().equals(this.selected)) {
                return p;
            }
        }
        return null;
    }

    public void select(SchematicPlacement placement) {
        this.selected = placement == null ? "" : placement.id();
        this.revision++;
        changed();
    }

    /**
     * @return a placement by its position in {@link #placements()} (1-based) or by the start of its name
     */
    public SchematicPlacement find(String query) {
        if (query == null || query.isBlank()) {
            return selected();
        }
        try {
            int index = Integer.parseInt(query.trim());
            return index >= 1 && index <= this.placements.size() ? this.placements.get(index - 1) : null;
        } catch (NumberFormatException ignored) {
            // a name
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (SchematicPlacement p : this.placements) {
            if (p.name().toLowerCase(Locale.ROOT).startsWith(q) || p.file().toLowerCase(Locale.ROOT).startsWith(q)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Places a file from the schematics folder, selects it and starts loading it.
     *
     * @throws IllegalArgumentException with a message for the player when it cannot be placed
     */
    public SchematicPlacement place(String file, String dimension, BlockPos origin) {
        tick(Minecraft.getInstance());
        if (this.scope == null) {
            throw new IllegalArgumentException("Join a world first");
        }
        File resolved = resolve(file);
        if (resolved == null || !resolved.isFile()) {
            throw new IllegalArgumentException("No schematic \"" + file + "\" in " + folder());
        }
        if (this.placements.size() >= MAX_PLACEMENTS) {
            throw new IllegalArgumentException("At most " + MAX_PLACEMENTS + " placements; remove one first");
        }
        SchematicPlacement placement = SchematicPlacement.create(file.replace('\\', '/'), dimension, origin);
        this.placements.add(placement);
        this.selected = placement.id();
        this.revision++;
        load(placement);
        changed();
        return placement;
    }

    public void remove(SchematicPlacement placement) {
        if (this.placements.remove(placement)) {
            if (placement.id().equals(this.selected)) {
                this.selected = this.placements.isEmpty() ? "" : this.placements.getLast().id();
            }
            this.revision++;
            changed();
        }
    }

    /**
     * Reads the placement's file again, for instance after it was edited.
     */
    public void reload(SchematicPlacement placement) {
        synchronized (this.cache) {
            File file = resolve(placement.file());
            if (file != null) {
                this.cache.remove(file.getAbsolutePath());
            }
        }
        load(placement);
    }

    private void load(SchematicPlacement placement) {
        File file = resolve(placement.file());
        if (file == null) {
            placement.fail(placement.file() + " is outside the schematics folder");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        this.loader.execute(() -> {
            try {
                LoadedSchematic schematic = read(file);
                mc.execute(() -> placement.attach(schematic));
            } catch (SchematicLoadException e) {
                mc.execute(() -> placement.fail(e.getMessage()));
            } catch (RuntimeException | OutOfMemoryError e) {
                mc.execute(() -> placement.fail(file.getName() + " could not be loaded: " + e));
            }
        });
    }

    private LoadedSchematic read(File file) throws SchematicLoadException {
        String key = file.getAbsolutePath();
        long modified = file.lastModified();
        long size = file.length();
        synchronized (this.cache) {
            Cached cached = this.cache.get(key);
            if (cached != null && cached.modified == modified && cached.size == size) {
                return cached.schematic;
            }
        }
        LoadedSchematic schematic = SchematicLoader.load(file);
        synchronized (this.cache) {
            this.cache.put(key, new Cached(modified, size, schematic));
        }
        return schematic;
    }

    // ---- saving ----

    /**
     * Saves soon; call after changing a placement.
     */
    public void changed() {
        if (this.scope == null) {
            return;
        }
        remember();
        String json = SchematicPlacementStore.write(this.stored);
        if (this.pendingSave != null) {
            this.pendingSave.cancel(false);
        }
        this.pendingSave = this.writer.schedule(() -> write(json), SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void flush() {
        if (this.pendingSave != null) {
            this.pendingSave.cancel(false);
            this.pendingSave = null;
        }
        if (this.stored != null) {
            String json = SchematicPlacementStore.write(this.stored);
            this.writer.execute(() -> write(json));
        }
    }

    private static synchronized void write(String json) {
        try {
            File dir = STORE.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            Path tmp = STORE.toPath().resolveSibling(STORE.getName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, STORE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, STORE.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            DihClientAddon.LOG.warn("Could not save schematic placements", e);
        }
    }
}
