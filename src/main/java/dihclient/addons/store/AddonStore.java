package dihclient.addons.store;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dihclient.DihClientAddon;
import dihclient.addons.AddonJanitor;
import dihclient.addons.AddonManager;
import dihclient.addons.AddonUninstaller;
import dihclient.api.ApiVersion;
import com.mojang.blaze3d.platform.NativeImage;
import dihclient.util.DihBackgroundTasks;
import dihclient.util.DihHttp;
import dihclient.util.DihImageCodec;
import dihclient.platform.DihLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The in-game addon browser's backend. Reads the reviewed registry from GitHub (the only host contacted), and installs
 * a listing by downloading its release jar into the mods folder after checking size, SHA-256 and fabric.mod.json.
 * New jars load on the next start; nothing is loaded into the running game.
 *
 * <p>The last list and the card images are cached under {@code config/dih/addon-store}, so the browser shows them at
 * once and refreshes in the background.
 */
public final class AddonStore {
    public static final String REGISTRY_URL = "https://raw.githubusercontent.com/ahigherdesire/dih-client/main/website/addons.json";
    public static final String SUBMIT_URL = "https://github.com/ahigherdesire/dih-client/blob/main/registry/CONTRIBUTING.md";
    /** Dev runs only: {@code -Ddih.dev.addonRegistry=<url>} points the store at a local copy for testing. */
    private static final String DEV_REGISTRY_PROPERTY = "dih.dev.addonRegistry";
    private static final int MAX_MOD_JSON_BYTES = 1024 * 1024;
    private static final long LIST_MAX_AGE_MS = 10 * 60 * 1000L;
    private static final long IMAGE_MAX_AGE_MS = 6 * 60 * 60 * 1000L;
    private static final Path CACHE = DihClientAddon.FOLDER.toPath().resolve("addon-store");

    public enum State { IDLE, LOADING, READY, FAILED }

    public enum Status {
        AVAILABLE("Get"),
        UPDATE("Update"),
        INSTALLED("Installed"),
        RESTART("Restart"),
        REMOVED("Removed"),
        INSTALLING("Installing"),
        WRONG_MINECRAFT("Other MC"),
        FABRIC_ONLY("Fabric only"),
        NEEDS_NEWER_DIH("Needs update"),
        FAILED("Failed");

        private final String label;

        Status(String label) { this.label = label; }

        public String label() { return label; }

        public boolean installable() { return this == AVAILABLE || this == UPDATE || this == FAILED || this == REMOVED; }
    }

    private static volatile State state = State.IDLE;
    private static volatile List<AddonStoreEntry> entries = List.of();
    private static volatile int generation;
    private static volatile long fetchedAt;
    private static volatile boolean cacheRead;
    private static final Set<String> BUSY = ConcurrentHashMap.newKeySet();
    private static final Set<String> PENDING_RESTART = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> MESSAGES = new ConcurrentHashMap<>();

    private AddonStore() {}

    public static State state() { return state; }

    public static List<AddonStoreEntry> entries() { return entries; }

    /** Bumped whenever the list or an install status changes, so the screen knows to refresh. */
    public static int generation() { return generation; }

    public static String message(String id) { return MESSAGES.getOrDefault(id, ""); }

    /** True while a fetch is running and a saved or earlier list is already on screen. */
    public static boolean updating() {
        return state == State.LOADING && !entries.isEmpty();
    }

    /** Fetch the list unless one is loading or a recent fetch succeeded. Called when the Addons screens open. */
    public static void refreshIfStale() {
        if (state == State.LOADING) return;
        if (state == State.READY && System.currentTimeMillis() - fetchedAt < LIST_MAX_AGE_MS) return;
        refresh();
    }

    public static void refresh() {
        if (state == State.LOADING) return;
        state = State.LOADING;
        generation++;
        DihBackgroundTasks.runTracked("DIH-AddonStore-Fetch", () -> {
            if (!cacheRead) {
                cacheRead = true;
                List<AddonStoreEntry> saved = readCachedList();
                if (saved != null && entries.isEmpty()) {
                    entries = saved;
                    generation++;
                }
            }
            long started = System.nanoTime();
            JsonObject root = DihHttp.getJson(registryUrl(), null);
            List<AddonStoreEntry> parsed = root == null ? null : parseList(root);
            long ms = (System.nanoTime() - started) / 1_000_000L;
            if (parsed == null) {
                DihClientAddon.LOG.warn("[AddonStore] Could not load the addon list after {} ms", ms);
                state = State.FAILED;
                generation++;
                return;
            }
            DihClientAddon.LOG.info("[AddonStore] Loaded {} listing(s) in {} ms", parsed.size(), ms);
            entries = parsed;
            fetchedAt = System.currentTimeMillis();
            state = State.READY;
            generation++;
            writeCache(listCacheFile(), root.toString().getBytes(StandardCharsets.UTF_8));
        });
    }

    /** The valid listings in a registry document, or null if it is not one. */
    private static List<AddonStoreEntry> parseList(JsonObject root) {
        if (!root.has("addons") || !root.get("addons").isJsonArray()) return null;
        List<AddonStoreEntry> parsed = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("addons")) {
            AddonStoreEntry entry = element.isJsonObject() ? AddonStoreEntry.parse(element.getAsJsonObject()) : null;
            if (entry == null) continue;
            List<String> problems = new ArrayList<>(entry.problems());
            if (devRegistry()) problems.removeIf(problem -> problem.startsWith("image")); // local test images
            if (problems.isEmpty()) {
                parsed.add(entry);
            } else {
                DihClientAddon.LOG.warn("[AddonStore] Skipping listing '{}': {}", entry.id(), problems);
            }
        }
        return List.copyOf(parsed);
    }

    private static Path listCacheFile() {
        return CACHE.resolve(devRegistry() ? "registry-dev.json" : "registry.json");
    }

    /** The list saved by the last successful fetch, re-validated like a fresh one; null if there is none. */
    private static List<AddonStoreEntry> readCachedList() {
        try {
            Path file = listCacheFile();
            if (!Files.isRegularFile(file)) return null;
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return root.isJsonObject() ? parseList(root.getAsJsonObject()) : null;
        } catch (Exception e) {
            DihClientAddon.LOG.warn("[AddonStore] Ignoring the saved addon list", e);
            return null;
        }
    }

    private static void writeCache(Path file, byte[] body) {
        try {
            Files.createDirectories(file.getParent());
            Path part = file.resolveSibling(file.getFileName() + ".part");
            Files.write(part, body);
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            DihClientAddon.LOG.warn("[AddonStore] Could not save {}", file.getFileName(), e);
        }
    }

    private static boolean devRegistry() {
        return DihLoader.isDevelopment()
            && System.getProperty(DEV_REGISTRY_PROPERTY, "").startsWith("http://localhost:");
    }

    private static String registryUrl() {
        return devRegistry() ? System.getProperty(DEV_REGISTRY_PROPERTY) : REGISTRY_URL;
    }

    public static Status status(AddonStoreEntry entry) {
        if (BUSY.contains(entry.id())) return Status.INSTALLING;
        if (PENDING_RESTART.contains(entry.id())) return Status.RESTART;
        if (AddonUninstaller.isRemoved(entry.id())) return Status.REMOVED;
        Optional<String> installed = DihLoader.modVersion(entry.id());
        if (installed.isPresent() && installed.get().equals(entry.version())) return Status.INSTALLED;
        if (!"fabric".equals(DihLoader.loaderName())) return Status.FABRIC_ONLY;
        if (entry.fileFor(gameVersion()) == null) return Status.WRONG_MINECRAFT;
        if (entry.apiVersion() > ApiVersion.CURRENT) return Status.NEEDS_NEWER_DIH;
        if (MESSAGES.containsKey(entry.id()) && !MESSAGES.get(entry.id()).isBlank()) return Status.FAILED;
        return installed.isPresent() ? Status.UPDATE : Status.AVAILABLE;
    }

    /** A loaded card image: its texture id and pixel size. */
    public record Image(Identifier id, int width, int height) {}

    private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_IMAGE_SIDE = 2048;
    private static final Map<String, Image> IMAGES = new ConcurrentHashMap<>();
    private static final Set<String> IMAGE_REQUESTED = ConcurrentHashMap.newKeySet();

    /**
     * The card image for {@code entry}, or null while it loads, if it failed, or if the listing has none. The first
     * call shows the cached copy at once if there is one, and downloads (size-capped) when there is none or it is
     * older than six hours; decoding happens off-thread and the texture is registered on the render thread.
     */
    public static Image image(AddonStoreEntry entry) {
        if (entry.image().isEmpty()) return null;
        String key = entry.id() + "|" + entry.image();
        Image ready = IMAGES.get(key);
        if (ready != null || !IMAGE_REQUESTED.add(key)) return ready;
        DihBackgroundTasks.runTracked("DIH-AddonStore-Image", () -> {
            Path file = CACHE.resolve("images").resolve(sha1(entry.image()) + ".img");
            byte[] cached = null;
            boolean fresh = false;
            try {
                if (Files.isRegularFile(file) && Files.size(file) <= MAX_IMAGE_BYTES) {
                    cached = Files.readAllBytes(file);
                    fresh = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() < IMAGE_MAX_AGE_MS;
                }
            } catch (IOException ignored) {
            }
            if (cached != null && !showImage(entry, key, cached)) {
                cached = null;
                fresh = false;
            }
            if (fresh) return;
            byte[] body = DihHttp.getBytes(entry.image(), MAX_IMAGE_BYTES);
            if (body == null) return;
            if (Arrays.equals(body, cached)) {
                writeCache(file, body); // unchanged; just mark it fresh
            } else if (showImage(entry, key, body)) {
                writeCache(file, body);
            }
        });
        return null;
    }

    /** Decode and register one card image; false if the bytes are not a usable image. */
    private static boolean showImage(AddonStoreEntry entry, String key, byte[] body) {
        NativeImage decoded = DihImageCodec.decode(body);
        if (decoded == null) return false;
        if (decoded.getWidth() > MAX_IMAGE_SIDE || decoded.getHeight() > MAX_IMAGE_SIDE) {
            decoded.close();
            return false;
        }
        Minecraft.getInstance().execute(() -> {
            Identifier id = Identifier.fromNamespaceAndPath("dihclient", "addon_store/" + entry.id());
            // Registering over an existing id closes the old texture.
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "DIH addon " + entry.id(), decoded));
            IMAGES.put(key, new Image(id, decoded.getWidth(), decoded.getHeight()));
            generation++;
        });
        return true;
    }

    private static String sha1(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    public static String gameVersion() {
        return DihLoader.modVersion("minecraft").orElse("");
    }

    public static void install(AddonStoreEntry entry) {
        if (!entry.problems().isEmpty() || !status(entry).installable()) return;
        if (!BUSY.add(entry.id())) return;
        MESSAGES.remove(entry.id());
        generation++;
        DihBackgroundTasks.runTracked("DIH-AddonStore-Install", () -> {
            try {
                installNow(entry);
                PENDING_RESTART.add(entry.id());
                AddonUninstaller.clearRemoved(entry.id());
            } catch (InstallException e) {
                MESSAGES.put(entry.id(), e.getMessage());
            } catch (Throwable t) {
                MESSAGES.put(entry.id(), "Install failed: " + t.getClass().getSimpleName());
                DihClientAddon.LOG.error("[AddonStore] Installing '{}' failed", entry.id(), t);
            } finally {
                BUSY.remove(entry.id());
                generation++;
            }
        });
    }

    /** Installed (loaded, or downloaded this session) and its jar is in the mods folder. */
    public static boolean canUninstall(AddonStoreEntry entry) {
        if (BUSY.contains(entry.id())) return false;
        if (PENDING_RESTART.contains(entry.id())) {
            return Files.isRegularFile(pendingJar(entry)) || AddonJanitor.isScheduled(pendingPart(entry));
        }
        return AddonUninstaller.canUninstall(entry.id());
    }

    /** Remove the addon's jar from mods; it stays loaded until the restart. Errors show in the dialog. */
    public static void uninstall(AddonStoreEntry entry) {
        if (!canUninstall(entry)) return;
        MESSAGES.remove(entry.id());
        try {
            if (PENDING_RESTART.remove(entry.id())) {
                // Downloaded this session: the new jar is not loaded, so it can go now.
                if (AddonJanitor.cancel(pendingPart(entry))) {
                    Files.deleteIfExists(pendingPart(entry));
                } else {
                    AddonUninstaller.removeJar(pendingJar(entry));
                }
                // An update replaced a loaded version: that version is gone from mods too.
                if (DihLoader.isModLoaded(entry.id())) AddonUninstaller.markRemoved(entry.id());
            } else {
                AddonUninstaller.uninstall(entry.id());
            }
        } catch (AddonUninstaller.UninstallException e) {
            MESSAGES.put(entry.id(), e.getMessage());
        } catch (IOException e) {
            MESSAGES.put(entry.id(), "Could not remove the download: " + e.getMessage());
        }
        generation++;
    }

    private static Path pendingJar(AddonStoreEntry entry) {
        return AddonManager.modsFolder().toPath().toAbsolutePath().normalize().resolve(entry.fileName());
    }

    private static Path pendingPart(AddonStoreEntry entry) {
        return pendingJar(entry).resolveSibling(entry.fileName() + ".part");
    }

    private static void installNow(AddonStoreEntry entry) throws Exception {
        AddonStoreEntry.AddonFile file = entry.fileFor(gameVersion());
        if (file == null) throw new InstallException("No build for Minecraft " + gameVersion() + ".");
        Path mods = AddonManager.modsFolder().toPath().toAbsolutePath().normalize();
        Files.createDirectories(mods);

        List<Path> oldJars = new ArrayList<>();
        // An addon uninstalled this session is still loaded, but its jar is already gone from mods.
        if (DihLoader.isModLoaded(entry.id()) && !AddonUninstaller.isRemoved(entry.id())) {
            oldJars = AddonUninstaller.jarsInMods(entry.id());
            if (oldJars.isEmpty()) {
                throw new InstallException("Already installed outside the mods folder; remove it there first.");
            }
        }

        byte[] body = DihHttp.getBytes(file.jar(), AddonStoreEntry.MAX_JAR_BYTES);
        if (body == null) throw new InstallException("Download failed. Check your internet connection.");
        if (body.length != file.size()) throw new InstallException("Size does not match the listing; not installed.");
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        if (!sha.equals(file.sha256())) throw new InstallException("Checksum does not match the listing; not installed.");
        checkModJson(body, entry.id());

        Path target = mods.resolve(entry.fileName()).normalize();
        if (!mods.equals(target.getParent())) throw new InstallException("Bad file name.");
        Path part = mods.resolve(entry.fileName() + ".part");
        Files.write(part, body);

        // Two jars with the same mod id stop Fabric from starting, so the new jar only becomes a .jar once the old one
        // is gone. A loaded jar is locked on Windows; then both steps run when Minecraft closes (AddonJanitor).
        boolean deferred = false;
        for (Path old : oldJars) {
            if (old.equals(target)) continue;
            try {
                if (!AddonUninstaller.removeJar(old)) deferred = true;
            } catch (AddonUninstaller.UninstallException e) {
                Files.deleteIfExists(part);
                throw new InstallException(e.getMessage());
            }
        }
        if (!deferred && !AddonJanitor.isScheduled(target)) {
            try {
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                DihClientAddon.LOG.info("[AddonStore] Installed '{}' {} (MC {}) to {}", entry.id(), entry.version(), file.minecraft(), target);
                return;
            } catch (IOException e) {
                // target is a locked jar with the same name
            }
        }
        AddonJanitor.scheduleMove(part, target);
        DihClientAddon.LOG.info("[AddonStore] Downloaded '{}' {}; it replaces the old jar when Minecraft closes", entry.id(), entry.version());
    }

    /** The jar must be a Fabric mod with the listed id and a {@code dih} entrypoint. */
    private static void checkModJson(byte[] jar, String id) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!"fabric.mod.json".equals(e.getName())) continue;
                byte[] raw = zip.readNBytes(MAX_MOD_JSON_BYTES);
                JsonObject meta = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
                boolean idMatches = meta.has("id") && id.equals(meta.get("id").getAsString());
                boolean isAddon = meta.has("entrypoints") && meta.getAsJsonObject("entrypoints").has("dih");
                if (idMatches && isAddon) return;
                throw new InstallException("The jar does not match the listing; not installed.");
            }
        }
        throw new InstallException("Not a Fabric mod jar; not installed.");
    }

    private static final class InstallException extends Exception {
        InstallException(String message) { super(message); }
    }
}
