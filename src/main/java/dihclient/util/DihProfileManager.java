package dihclient.util;

import dihclient.DihClientAddon;
import dihclient.modules.DihModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DihProfileManager {
    private static DihProfileManager INSTANCE;

    public static final String DEFAULT_ID = "default";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File dir = new File(DihClientAddon.FOLDER, "profiles");
    private final List<DihProfile> profiles = new ArrayList<>();
    private String activeId = "";
    private volatile boolean dirty = false;
    private volatile long revision = 0;

    private boolean applying = false;

    private static final long MIRROR_DEBOUNCE_MS = 750;
    private volatile boolean mirrorPending = false;
    private long mirrorDueAtMs = 0;

    private DihConfig pendingMirrorSnapshot;

    private volatile boolean loadPending = false;

    private DihProfileManager() {
        loadAll();
        ensureDefault();
        dedupeNames();
        adoptActive();
        DihConfig.afterSave = this::onLiveConfigSaved;
        DihConfig.afterPersistenceSnapshot = this::onLiveConfigSnapshot;
    }

    public static synchronized DihProfileManager get() {
        if (INSTANCE == null) INSTANCE = new DihProfileManager();
        return INSTANCE;
    }

    static void flushPendingMirrorIfInitialized() {
        DihProfileManager current = INSTANCE;
        if (current != null) current.flushMirrorNow();
    }

    public synchronized List<DihProfile> list() {

        List<DihProfile> out = new ArrayList<>(profiles);
        out.sort((a, b) -> {
            boolean da = DEFAULT_ID.equals(a.id), db = DEFAULT_ID.equals(b.id);
            if (da != db) return da ? -1 : 1;
            return Long.compare(a.createdAt, b.createdAt);
        });
        return out;
    }
    public synchronized int count() { return profiles.size(); }
    public static boolean isDefault(DihProfile p) { return p != null && DEFAULT_ID.equals(p.id); }
    public synchronized String activeId() { return activeId; }
    public synchronized boolean isDirty() { return dirty; }
    public long revision() { return revision; }

    public synchronized DihProfile active() {
        return byId(activeId);
    }

    public synchronized DihProfile byId(String id) {
        if (id == null) return null;
        for (DihProfile p : profiles) if (p != null && id.equals(p.id)) return p;
        return null;
    }

    private boolean existsId(String id) { return byId(id) != null; }

    public synchronized boolean nameExists(String name, String excludeId) {
        if (name == null) return false;
        String want = name.strip();
        if (want.isEmpty()) return false;
        for (DihProfile p : profiles) {
            if (p == null || p.displayName == null) continue;
            if (excludeId != null && excludeId.equals(p.id)) continue;
            if (p.displayName.strip().equalsIgnoreCase(want)) return true;
        }
        return false;
    }

    public synchronized DihProfile create(String name) {
        return create(name, true, false, true);
    }

    public synchronized DihProfile create(String name, boolean autoSave, boolean ownMacroLibrary, boolean ownThemeColor) {
        if (nameExists(name, null)) return null;
        DihProfile p = new DihProfile(slug(name), name == null || name.isBlank() ? "Profile" : name.strip());
        p.autoSave = autoSave;
        p.ownMacroLibrary = ownMacroLibrary;
        p.ownThemeColor = ownThemeColor;
        p.createdAt = p.updatedAt = System.currentTimeMillis();
        p.snapshot = snapshotLiveConfig();

        if (ownMacroLibrary) DihMacroManager.writeEmptyLibrary(companionFile(p));
        profiles.add(p);
        writeProfile(p, p.snapshot);
        revision++;
        return p;
    }

    public synchronized DihProfile duplicate(DihProfile src, String name) {
        if (src == null) return create(name);
        if (nameExists(name, null)) return null;
        DihProfile p = new DihProfile(slug(name), name == null || name.isBlank() ? src.displayName + " copy" : name.strip());
        p.autoSave = src.autoSave;
        p.ownMacroLibrary = src.ownMacroLibrary;
        p.ownThemeColor = src.ownThemeColor;
        p.serverPatterns = new ArrayList<>(src.serverPatterns);
        p.createdAt = p.updatedAt = System.currentTimeMillis();
        p.snapshot = src.snapshot.deepCopy();
        profiles.add(p);
        writeProfile(p);
        if (src.ownMacroLibrary) {
            try {
                File from = companionFile(src);
                if (from.exists()) Files.copy(from.toPath(), companionFile(p).toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                DihClientAddon.LOG.warn("Could not copy macro library while duplicating profile", e);
            }
        }
        revision++;
        return p;
    }

    public synchronized boolean rename(DihProfile p, String name) {
        if (p == null || name == null || name.isBlank()) return false;
        if (nameExists(name, p.id)) return false;
        p.displayName = name.strip();
        p.updatedAt = System.currentTimeMillis();
        writeProfile(p);
        revision++;
        return true;
    }

    public synchronized boolean delete(DihProfile p) {
        if (p == null || DEFAULT_ID.equals(p.id) || profiles.size() <= 1) return false;
        boolean wasActive = p.id.equals(activeId);
        profiles.remove(p);
        deleteProfileFiles(p);
        if (wasActive) {
            DihProfile fallback = byId(DEFAULT_ID);
            if (fallback == null) fallback = profiles.get(0);
            load(fallback);
        }

        deleteFile(companionFile(p));
        deleteFile(new File(dir, p.id + ".macros.nbt.bak"));
        revision++;
        return true;
    }

    public synchronized void setAutoSave(DihProfile p, boolean autoSave) {
        if (p == null || p.autoSave == autoSave) return;
        p.autoSave = autoSave;
        p.updatedAt = System.currentTimeMillis();

        if (autoSave && p.id.equals(activeId)) {
            p.snapshot = snapshotLiveConfig();
            dirty = false;
        }
        writeProfile(p, autoSave && p.id.equals(activeId) ? p.snapshot : null);
        revision++;
    }

    public synchronized void setOwnMacroLibrary(DihProfile p, boolean own) {
        if (p == null || p.ownMacroLibrary == own) return;
        p.ownMacroLibrary = own;
        p.updatedAt = System.currentTimeMillis();
        if (own) seedCompanionIfAbsent(p);
        writeProfile(p);
        if (p.id.equals(activeId)) {
            if (own) DihMacroManager.get().switchBackingFile(companionFile(p), false, true);
            else DihMacroManager.get().resetToSharedLibrary();
        }
        revision++;
    }

    public synchronized void setOwnThemeColor(DihProfile p, boolean own) {
        if (p == null || p.ownThemeColor == own) return;
        p.ownThemeColor = own;
        p.updatedAt = System.currentTimeMillis();
        writeProfile(p);

        revision++;
    }

    public synchronized void setServerPatterns(DihProfile p, List<String> patterns) {
        if (p == null) return;
        List<String> clean = new ArrayList<>();
        if (patterns != null) {
            for (String s : patterns) {
                if (s == null) continue;
                String n = s.strip().toLowerCase(Locale.ROOT);
                if (!n.isEmpty() && !clean.contains(n)) clean.add(n);
            }
        }
        p.serverPatterns = clean;
        p.updatedAt = System.currentTimeMillis();
        writeProfile(p);
        revision++;
    }

    public synchronized void save(DihProfile p) {
        if (p == null) return;
        p.snapshot = snapshotLiveConfig();
        p.updatedAt = System.currentTimeMillis();
        writeProfile(p, p.snapshot);
        if (p.id.equals(activeId)) {
            dirty = false;
        }
        revision++;
    }

    public synchronized void load(DihProfile p) {
        if (p == null) return;
        DihConfig.enqueuePendingSaveNow();
        flushMirrorNow();
        applying = true;
        try {
            DihConfig next = p.snapshot.deepCopy();
            next.activeProfileId = p.id;
            if (!p.ownThemeColor) {

                DihConfig liveNow = DihConfig.getGlobal();
                if (liveNow != null) next.themeColors = liveNow.deepCopy().themeColors;
            }
            DihModule.get().applyConfig(next);
            switchMacroLibraryFor(p);
            activeId = p.id;
            dirty = false;
            revision++;
        } finally {
            applying = false;
        }
        DihClientMessaging.sendPrefixed("§aLoaded profile: " + p.displayName);
    }

    public synchronized void beginLoad(DihProfile p) {
        if (p == null || loadPending) return;
        if (p.id.equals(activeId)) return;
        loadPending = true;
        Runnable applyJob = dihclient.gui.DihThemeApplyOverlay.beginJob("Switching Profile", "Applying profile…");
        dihclient.gui.DihThemeApplyOverlay.runAfterShown(() -> runSwitch(p, applyJob));
    }

    private synchronized void runSwitch(DihProfile p, Runnable applyJob) {
        Runnable macroJob = dihclient.gui.DihThemeApplyOverlay.beginJob("Switching Profile", "Loading macros…");
        boolean macroKicked = false;
        try {
            DihConfig.enqueuePendingSaveNow();
            flushMirrorNow();
            applying = true;
            DihConfig next = p.snapshot.deepCopy();
            next.activeProfileId = p.id;
            if (!p.ownThemeColor) {
                DihConfig live = DihConfig.getGlobal();
                if (live != null) next.themeColors = live.deepCopy().themeColors;
            }
            DihModule.get().applyConfig(next);
            activeId = p.id;
            dirty = false;
            revision++;
            DihClientMessaging.sendPrefixed("§aLoaded profile: " + p.displayName);
            final DihProfile fp = p;
            final Runnable mj = macroJob;
            DihBackgroundTasks.runTracked("profile-macros", () -> {
                try { switchMacroLibraryFor(fp); }
                catch (Throwable t) { DihClientAddon.LOG.warn("Profile macro switch failed", t); }
                finally { mj.run(); }
            });
            macroKicked = true;
        } catch (Throwable t) {
            DihClientAddon.LOG.warn("Profile switch failed", t);
        } finally {
            applying = false;
            loadPending = false;
            applyJob.run();
            if (!macroKicked) macroJob.run();
        }
    }

    public synchronized void applyForServerConnect(ServerAddress addr, ServerData data) {
        String address = (data != null && data.ip != null && !data.ip.isBlank())
            ? data.ip
            : (addr != null ? addr.getHost() + ":" + addr.getPort() : "");
        DihProfile match = matchForServer(address);
        if (match == null || match.id.equals(activeId)) return;
        DihProfile current = active();
        if (current != null && !current.autoSave && dirty) {
            save(current);
        }
        load(match);
    }

    public synchronized DihProfile matchForServer(String address) {
        if (address == null || address.isBlank()) return null;
        String addr = address.strip().toLowerCase(Locale.ROOT);
        String host = addr;
        int port = 25565;
        int colon = addr.lastIndexOf(':');
        if (colon > 0 && colon < addr.length() - 1) {
            try { port = Integer.parseInt(addr.substring(colon + 1)); host = addr.substring(0, colon); }
            catch (NumberFormatException ignored) {  }
        }
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);

        DihProfile best = null;
        int bestLen = -1;
        long bestCreated = Long.MAX_VALUE;
        for (DihProfile p : profiles) {
            if (p == null || p.serverPatterns == null) continue;
            for (String pattern : p.serverPatterns) {
                if (!matchesPattern(host, port, pattern)) continue;
                int len = pattern.length();
                if (len > bestLen || (len == bestLen && p.createdAt < bestCreated)) {
                    best = p; bestLen = len; bestCreated = p.createdAt;
                }
            }
        }
        return best;
    }

    private static boolean matchesPattern(String host, int port, String pattern) {
        if (pattern == null) return false;
        String pat = pattern.strip().toLowerCase(Locale.ROOT);
        if (pat.isEmpty()) return false;
        if (pat.endsWith(".")) pat = pat.substring(0, pat.length() - 1);
        if (pat.equals("*")) return true;
        if (pat.startsWith("*.")) {
            String suffix = pat.substring(2);
            return host.equals(suffix) || host.endsWith("." + suffix);
        }
        int colon = pat.lastIndexOf(':');
        if (colon > 0 && colon < pat.length() - 1) {
            try {
                int patPort = Integer.parseInt(pat.substring(colon + 1));
                return host.equals(pat.substring(0, colon)) && port == patPort;
            } catch (NumberFormatException ignored) {  }
        }
        return host.equals(pat);
    }

    private synchronized void onLiveConfigSaved() {
        if (applying) return;
        DihProfile a = active();
        if (a == null) return;
        if (a.autoSave) {

            pendingMirrorSnapshot = null;
            mirrorDueAtMs = System.currentTimeMillis() + MIRROR_DEBOUNCE_MS;
            mirrorPending = true;
        } else {
            dirty = true;
            revision++;
        }
    }

    private synchronized void onLiveConfigSnapshot(DihConfig snapshot) {
        if (applying || snapshot == null || !mirrorPending) return;
        DihProfile a = active();
        if (a == null || !a.autoSave) return;
        pendingMirrorSnapshot = DihConfig.profileSnapshotFromDetached(snapshot);
    }

    public void flushMirrorIfDue() {

        if (!mirrorPending) return;
        synchronized (this) {
            if (mirrorPending && System.currentTimeMillis() >= mirrorDueAtMs) doMirror();
        }
    }

    public synchronized void flushMirrorNow() {
        if (mirrorPending && pendingMirrorSnapshot == null) {

            DihConfigWriter.capturePendingNow();
        }
        if (mirrorPending) doMirror();
    }

    private void doMirror() {
        mirrorPending = false;
        DihProfile a = active();
        if (a == null || !a.autoSave) return;
        DihConfig snap = pendingMirrorSnapshot;
        pendingMirrorSnapshot = null;
        if (snap == null) snap = snapshotLiveConfig();
        a.snapshot = snap;
        a.updatedAt = System.currentTimeMillis();
        writeProfile(a, snap);
        dirty = false;
    }

    private DihConfig snapshotLiveConfig() {
        return DihConfig.getGlobal().snapshotForProfile();
    }

    private File companionFile(DihProfile p) { return new File(dir, p.id + ".macros.nbt"); }

    private void seedCompanionIfAbsent(DihProfile p) {
        File comp = companionFile(p);
        File bak = new File(dir, p.id + ".macros.nbt.bak");
        if (comp.exists() || bak.exists()) return;
        try {
            dir.mkdirs();
            File shared = DihMacroManager.sharedLibraryFile();
            if (shared.exists()) Files.copy(shared.toPath(), comp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            DihClientAddon.LOG.warn("Could not seed profile macro library", e);
        }
    }

    private void switchMacroLibraryFor(DihProfile p) {
        if (p.ownMacroLibrary) {
            seedCompanionIfAbsent(p);
            DihMacroManager.get().switchBackingFile(companionFile(p), false, false);
        } else {
            DihMacroManager.get().resetToSharedLibrary();
        }
    }

    private File profileFile(DihProfile p) { return new File(dir, p.id + ".json"); }

    private String profileWriteKey(String id) {
        return "profile:" + new File(dir, id + ".json").getAbsolutePath();
    }

    private void loadAll() {
        profiles.clear();
        if (!dir.exists()) return;
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json") && !n.endsWith(".tmp"));
        if (files == null) return;
        for (File f : files) {
            DihProfile p = readProfile(f);
            if (p == null) {
                setAsideCorrupt(f);
                File bak = new File(dir, f.getName() + ".bak");
                if (bak.exists()) p = readProfile(bak);
            }
            if (p != null && !p.id.isBlank() && byId(p.id) == null) profiles.add(p);
        }
    }

    private DihProfile readProfile(File f) {
        try (FileReader r = new FileReader(f)) {
            DihProfile p = gson.fromJson(r, DihProfile.class);
            if (p == null) return null;
            p.normalize();
            return p;
        } catch (Throwable t) {
            DihClientAddon.LOG.error("Failed to read profile {}", f.getName(), t);
            return null;
        }
    }

    private void setAsideCorrupt(File f) {
        try {
            File aside = new File(dir, f.getName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(f.toPath(), aside.toPath(), StandardCopyOption.REPLACE_EXISTING);
            DihClientAddon.LOG.error("Profile {} was corrupt; moved it to {}", f.getName(), aside.getName());
        } catch (Throwable t) {
            DihClientAddon.LOG.error("Failed to set aside corrupt profile {}", f.getName(), t);
        }
    }

    private void writeProfile(DihProfile p) {
        writeProfile(p, null);
    }

    private void writeProfile(DihProfile p, DihConfig detachedSnapshot) {
        if (p == null) return;
        DihProfile snapshot = copyProfile(p, detachedSnapshot);
        SaveCoordinator.enqueueLatest(profileWriteKey(snapshot.id), () -> writeProfileSnapshot(snapshot));
    }

    private void writeProfileSnapshot(DihProfile p) {
        dir.mkdirs();
        File target = profileFile(p);
        File tmp = new File(dir, p.id + ".json.tmp");
        File bak = new File(dir, p.id + ".json.bak");
        try (java.io.FileWriter w = new java.io.FileWriter(tmp)) {
            gson.toJson(p, w);
        } catch (Throwable t) {
            DihClientAddon.LOG.error("Failed to write profile {}", p.id, t);
            tmp.delete();
            return;
        }
        try {
            if (target.exists()) Files.copy(target.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            DihClientAddon.LOG.warn("Failed to back up profile {} before save", p.id, t);
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Throwable atomicFailed) {
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable t) {
                DihClientAddon.LOG.error("Failed to swap in profile {}", p.id, t);
            }
        }
    }

    private static DihProfile copyProfile(DihProfile source, DihConfig detachedSnapshot) {
        DihProfile copy = new DihProfile();
        copy.id = source.id == null ? "" : source.id;
        copy.displayName = source.displayName == null ? "" : source.displayName;
        copy.autoSave = source.autoSave;
        copy.ownMacroLibrary = source.ownMacroLibrary;
        copy.ownThemeColor = source.ownThemeColor;
        copy.serverPatterns = source.serverPatterns == null ? new ArrayList<>() : new ArrayList<>(source.serverPatterns);
        copy.createdAt = source.createdAt;
        copy.updatedAt = source.updatedAt;
        copy.schemaVersion = source.schemaVersion;
        copy.snapshot = detachedSnapshot != null
            ? detachedSnapshot
            : (source.snapshot == null ? new DihConfig() : source.snapshot.deepCopy());
        return copy;
    }

    private void deleteProfileFiles(DihProfile profile) {
        if (profile == null) return;
        String id = profile.id;
        SaveCoordinator.enqueueLatest(profileWriteKey(id), () -> {
            deleteFile(new File(dir, id + ".json"));
            deleteFile(new File(dir, id + ".json.bak"));
            deleteFile(new File(dir, id + ".json.tmp"));
        });
    }

    private void deleteFile(File f) {
        try { if (f != null && f.exists()) Files.deleteIfExists(f.toPath()); }
        catch (Throwable ignored) {  }
    }

    private void ensureDefault() {
        if (profiles.isEmpty()) {
            DihProfile def = new DihProfile(DEFAULT_ID, "Default");
            def.autoSave = true;
            def.createdAt = def.updatedAt = System.currentTimeMillis();
            def.snapshot = snapshotLiveConfig();
            profiles.add(def);
            activeId = def.id;
            writeProfile(def);
            return;
        }
        if (byId(DEFAULT_ID) != null) return;

        DihConfig stock = new DihConfig();
        stock.applyRuntimeDefaults();
        DihProfile def = new DihProfile(DEFAULT_ID, "Default");
        def.autoSave = true;
        def.createdAt = def.updatedAt = System.currentTimeMillis();
        def.snapshot = stock.snapshotForProfile();
        profiles.add(def);
        writeProfile(def);
    }

    private void dedupeNames() {
        Set<String> taken = new HashSet<>();
        DihProfile def = byId(DEFAULT_ID);
        if (def != null && def.displayName != null) taken.add(def.displayName.strip().toLowerCase(Locale.ROOT));
        for (DihProfile p : profiles) {
            if (p == null || p == def) continue;
            String base = (p.displayName == null ? "" : p.displayName.strip());
            if (base.isEmpty()) base = "Profile";
            if (taken.add(base.toLowerCase(Locale.ROOT))) continue;
            int n = 2;
            String candidate;
            do { candidate = base + " (" + (n++) + ")"; } while (!taken.add(candidate.toLowerCase(Locale.ROOT)));
            p.displayName = candidate;
            p.updatedAt = System.currentTimeMillis();
            writeProfile(p);
        }
    }

    private void adoptActive() {
        String want = DihConfig.getGlobal().activeProfileId;
        if (want != null && !want.isBlank() && existsId(want)) {
            activeId = want;
        } else if (activeId == null || activeId.isBlank() || !existsId(activeId)) {
            activeId = profiles.isEmpty() ? "" : profiles.get(0).id;
            DihConfig.getGlobal().activeProfileId = activeId;
        }

        DihProfile a = active();
        if (a != null && !a.autoSave) {
            try {
                String live = gson.toJson(snapshotLiveConfig());
                String saved = gson.toJson(a.snapshot);
                if (!live.equals(saved)) dirty = true;
            } catch (Throwable ignored) {  }
        }
    }

    private String slug(String name) {
        String base = DihProfile.sanitizeId(name);
        String candidate = base;
        int n = 2;
        while (existsId(candidate)) candidate = base + "-" + (n++);
        return candidate;
    }
}
