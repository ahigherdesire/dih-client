package dihclient.util;

import dihclient.DihClientAddon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ServerPluginScanCache {
    static final int MAX_ENTRIES = 128;
    static final long MAX_BYTES = 8L * 1024L * 1024L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOG = LogUtils.getLogger();
    private static volatile ServerPluginScanCache instance;

    private final File file;
    private final String persistenceKey;
    private final Object persistenceLock = new Object();
    private final LinkedHashMap<String, DihConfig.PluginScanCacheEntry> entries = new LinkedHashMap<>();

    private final LinkedHashMap<String, Long> serializedSizes = new LinkedHashMap<>();
    private volatile Map<String, DihConfig.PluginScanCacheEntry> publishedView = Map.of();

    private volatile Map<String, DihConfig.PluginScanCacheEntry> pendingLegacyFallback = Map.of();

    private long stateRevision;
    private long persistedRevision = -1L;
    private long pendingLegacyRevision = Long.MAX_VALUE;

    private ServerPluginScanCache(File file) {
        this.file = file;
        this.persistenceKey = "plugin-scans:" + file.getAbsolutePath();
        load();
    }

    static ServerPluginScanCache get() {
        ServerPluginScanCache current = instance;
        if (current != null) return current;
        synchronized (ServerPluginScanCache.class) {
            if (instance == null) {
                instance = new ServerPluginScanCache(new File(DihClientAddon.FOLDER, "server-plugin-scans.json"));
            }
            return instance;
        }
    }

    static ServerPluginScanCache forFile(File file) {
        return new ServerPluginScanCache(file);
    }

    Map<String, DihConfig.PluginScanCacheEntry> sharedView() {
        return publishedView;
    }

    Map<String, DihConfig.PluginScanCacheEntry> pendingLegacyFallback() {
        return pendingLegacyFallback;
    }

    synchronized DihConfig.PluginScanCacheEntry get(String key) {
        return copyEntry(entries.get(key));
    }

    synchronized DihConfig.PluginScanCacheEntry newestForAddress(String normalizedAddress) {
        if (normalizedAddress == null || normalizedAddress.isBlank()) return null;
        DihConfig.PluginScanCacheEntry newest = null;
        for (Map.Entry<String, DihConfig.PluginScanCacheEntry> item : entries.entrySet()) {
            String key = item.getKey();
            if (!key.equals(normalizedAddress) && !key.startsWith(normalizedAddress + "|")) continue;
            if (newest == null || item.getValue().scannedAtMs > newest.scannedAtMs) newest = item.getValue();
        }
        return copyEntry(newest);
    }

    synchronized int size() {
        return entries.size();
    }

    synchronized long revision() {
        return stateRevision;
    }

    synchronized Map<String, DihConfig.PluginScanCacheEntry> snapshot() {
        return copyEntries(entries);
    }

    void put(String key, DihConfig.PluginScanCacheEntry entry) {
        if (key == null || key.isBlank() || entry == null) return;
        DihConfig.PluginScanCacheEntry copied = copyEntry(entry);
        long serializedSize = serializedBytes(key, copied);
        synchronized (this) {
            entries.put(key, copied);
            serializedSizes.put(key, serializedSize);
            trimToBounds();
            stateRevision++;
            publishAndEnqueueWrite();
        }
    }

    synchronized void remove(String key) {
        if (key != null && entries.remove(key) != null) {
            serializedSizes.remove(key);
            stateRevision++;
            publishAndEnqueueWrite();
        }
    }

    synchronized void removeAddress(String normalizedAddress) {
        if (normalizedAddress == null) return;
        boolean changed = entries.keySet().removeIf(
            key -> key.equals(normalizedAddress) || key.startsWith(normalizedAddress + "|"));
        if (changed) {
            serializedSizes.keySet().retainAll(entries.keySet());
            stateRevision++;
            publishAndEnqueueWrite();
        }
    }

    boolean mergeLegacy(Map<String, DihConfig.PluginScanCacheEntry> legacy) {
        if (legacy == null || legacy.isEmpty()) return false;

        LinkedHashMap<String, DihConfig.PluginScanCacheEntry> candidates = new LinkedHashMap<>();
        LinkedHashMap<String, Long> candidateSizes = new LinkedHashMap<>();
        for (Map.Entry<String, DihConfig.PluginScanCacheEntry> item : legacy.entrySet()) {
            if (item.getKey() == null || item.getKey().isBlank() || item.getValue() == null) continue;
            DihConfig.PluginScanCacheEntry copied = copyEntry(item.getValue());
            candidates.put(item.getKey(), copied);
            candidateSizes.put(item.getKey(), serializedBytes(item.getKey(), copied));
        }
        if (candidates.isEmpty()) return false;

        Map<String, DihConfig.PluginScanCacheEntry> snapshot;
        long revision;
        synchronized (this) {

            pendingLegacyFallback = Collections.unmodifiableMap(copyEntries(candidates));
            boolean changed = false;
            for (Map.Entry<String, DihConfig.PluginScanCacheEntry> item : candidates.entrySet()) {
                DihConfig.PluginScanCacheEntry old = entries.get(item.getKey());
                if (old == null || item.getValue().scannedAtMs > old.scannedAtMs) {
                    entries.put(item.getKey(), item.getValue());
                    serializedSizes.put(item.getKey(), candidateSizes.get(item.getKey()));
                    changed = true;
                }
            }
            trimToBounds();
            if (changed) stateRevision++;
            pendingLegacyRevision = stateRevision;
            snapshot = publishSnapshot();
            revision = stateRevision;
        }

        return writeSnapshot(snapshot, revision);
    }

    private void publishAndEnqueueWrite() {
        Map<String, DihConfig.PluginScanCacheEntry> snapshot = publishSnapshot();
        long revision = stateRevision;
        SaveCoordinator.enqueueLatest(persistenceKey, () -> writeSnapshot(snapshot, revision));
    }

    private Map<String, DihConfig.PluginScanCacheEntry> publishSnapshot() {
        Map<String, DihConfig.PluginScanCacheEntry> snapshot = copyEntries(entries);
        publishedView = Collections.unmodifiableMap(snapshot);
        return snapshot;
    }

    private void trimToBounds() {
        if (entries.isEmpty()) return;
        List<Map.Entry<String, DihConfig.PluginScanCacheEntry>> newest = new ArrayList<>(entries.entrySet());
        newest.sort(Comparator
            .<Map.Entry<String, DihConfig.PluginScanCacheEntry>>comparingLong(
                entry -> entry.getValue() == null ? 0L : entry.getValue().scannedAtMs)
            .reversed());

        LinkedHashMap<String, DihConfig.PluginScanCacheEntry> retained = new LinkedHashMap<>();
        long bytes = 32L;
        for (Map.Entry<String, DihConfig.PluginScanCacheEntry> item : newest) {
            if (retained.size() >= MAX_ENTRIES) break;
            Long knownSize = serializedSizes.get(item.getKey());

            long entryBytes = knownSize == null ? MAX_BYTES : knownSize;
            if (!retained.isEmpty() && bytes + entryBytes > MAX_BYTES) break;
            retained.put(item.getKey(), item.getValue());
            bytes += entryBytes;
        }
        entries.clear();
        entries.putAll(retained);
        serializedSizes.keySet().retainAll(retained.keySet());
    }

    private static long serializedBytes(String key, DihConfig.PluginScanCacheEntry entry) {
        return GSON.toJson(Map.of(key, entry)).getBytes(StandardCharsets.UTF_8).length;
    }

    private void load() {
        CacheFile cache = read(file);
        if (cache == null) {
            File backup = backupFile();
            cache = read(backup);
        }
        if (cache == null || cache.entries == null) return;
        entries.putAll(copyEntries(cache.entries));
        for (Map.Entry<String, DihConfig.PluginScanCacheEntry> item : entries.entrySet()) {
            serializedSizes.put(item.getKey(), serializedBytes(item.getKey(), item.getValue()));
        }
        trimToBounds();
        if (!entries.isEmpty()) stateRevision++;
        publishSnapshot();
    }

    private CacheFile read(File source) {
        if (source == null || !source.exists()) return null;
        try (FileReader reader = new FileReader(source)) {
            CacheFile loaded = GSON.fromJson(reader, CacheFile.class);
            return loaded == null ? new CacheFile() : loaded;
        } catch (Throwable t) {
            LOG.warn("Failed to read server plugin cache {}", source.getName(), t);
            return null;
        }
    }

    private boolean writeSnapshot(Map<String, DihConfig.PluginScanCacheEntry> snapshot, long revision) {
        synchronized (this) {
            if (revision < persistedRevision) return true;
        }
        CacheFile value = new CacheFile();
        value.entries = snapshot;
        final String json;
        try {
            json = GSON.toJson(value);
        } catch (Throwable t) {
            LOG.error("Failed to serialize server plugin cache", t);
            return false;
        }
        synchronized (persistenceLock) {
            synchronized (this) {
                if (revision < persistedRevision) return true;
            }
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                LOG.error("Failed to create server plugin cache directory {}", parent);
                return false;
            }
            File tmp = new File(parent, file.getName() + ".tmp");
            try (FileWriter writer = new FileWriter(tmp)) {
                writer.write(json);
            } catch (Throwable t) {
                LOG.error("Failed to write server plugin cache", t);
                tmp.delete();
                return false;
            }
            try {
                if (file.exists()) Files.copy(file.toPath(), backupFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable t) {
                LOG.warn("Failed to back up server plugin cache", t);
            }
            boolean persisted = atomicReplace(tmp, file, "server plugin cache") && file.isFile();
            if (persisted) markPersisted(revision);
            return persisted;
        }
    }

    private synchronized void markPersisted(long revision) {
        persistedRevision = Math.max(persistedRevision, revision);
        if (!pendingLegacyFallback.isEmpty() && revision >= pendingLegacyRevision) {
            pendingLegacyFallback = Map.of();
            pendingLegacyRevision = Long.MAX_VALUE;
        }
    }

    private File backupFile() {
        return new File(file.getParentFile(), file.getName() + ".bak");
    }

    private static boolean atomicReplace(File tmp, File target, String description) {
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Throwable atomicFailed) {
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (Throwable t) {
                LOG.error("Failed to swap in {}", description, t);
                tmp.delete();
                return false;
            }
        }
    }

    private static LinkedHashMap<String, DihConfig.PluginScanCacheEntry> copyEntries(
        Map<String, DihConfig.PluginScanCacheEntry> source) {
        LinkedHashMap<String, DihConfig.PluginScanCacheEntry> copy = new LinkedHashMap<>();
        if (source == null) return copy;
        for (Map.Entry<String, DihConfig.PluginScanCacheEntry> item : source.entrySet()) {
            if (item.getKey() != null && item.getValue() != null) copy.put(item.getKey(), copyEntry(item.getValue()));
        }
        return copy;
    }

    private static DihConfig.PluginScanCacheEntry copyEntry(DihConfig.PluginScanCacheEntry source) {
        if (source == null) return null;
        DihConfig.PluginScanCacheEntry copy = new DihConfig.PluginScanCacheEntry();
        copy.contextSignature = source.contextSignature == null ? "" : source.contextSignature;
        copy.serverName = source.serverName == null ? "" : source.serverName;
        copy.serverAddress = source.serverAddress == null ? "" : source.serverAddress;
        copy.plugins = source.plugins == null ? new ArrayList<>() : new ArrayList<>(source.plugins);
        copy.commands = copyListMap(source.commands);
        copy.evidence = source.evidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.evidence);
        copy.channels = copyListMap(source.channels);
        copy.guis = copyListMap(source.guis);
        copy.confidence = source.confidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.confidence);
        copy.copyEvidence = source.copyEvidence == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.copyEvidence);
        copy.copyCommands = copyListMap(source.copyCommands);
        copy.scanStatus = source.scanStatus == null ? "COMPLETE" : source.scanStatus;
        copy.totalProbes = source.totalProbes;
        copy.answeredProbes = source.answeredProbes;
        copy.retriedProbes = source.retriedProbes;
        copy.failedProbes = source.failedProbes;
        copy.scannedAtMs = source.scannedAtMs;
        return copy;
    }

    private static LinkedHashMap<String, List<String>> copyListMap(Map<String, List<String>> source) {
        LinkedHashMap<String, List<String>> copy = new LinkedHashMap<>();
        if (source == null) return copy;
        for (Map.Entry<String, List<String>> item : source.entrySet()) {
            copy.put(item.getKey(), item.getValue() == null ? new ArrayList<>() : new ArrayList<>(item.getValue()));
        }
        return copy;
    }

    private static final class CacheFile {
        int version = 1;
        Map<String, DihConfig.PluginScanCacheEntry> entries = new LinkedHashMap<>();
    }
}
