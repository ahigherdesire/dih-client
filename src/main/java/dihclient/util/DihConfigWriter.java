package dihclient.util;

import dihclient.DihClientAddon;

final class DihConfigWriter {
    private static final String CONFIG_KEY = "config:" + DihConfig.configFile().getAbsolutePath();

    private DihConfigWriter() {
    }

    static void request(DihConfig config) {
        SaveCoordinator.requestConfigSave(config);
    }

    static void captureAndEnqueue(DihConfig source) {
        final DihConfig snapshot;
        try {
            snapshot = DihConfigSnapshot.copyForPersistence(source);
        } catch (Throwable t) {
            DihClientAddon.LOG.error("Failed to capture Dih config", t);
            return;
        }
        enqueueSnapshot(snapshot);
        DihConfig.onPersistenceSnapshot(snapshot);
    }

    static void enqueueSnapshot(DihConfig snapshot) {
        SaveCoordinator.enqueueLatest(CONFIG_KEY, () -> {
            try {
                DihConfig.writeToDisk(DihConfig.toJson(snapshot));
            } catch (Throwable t) {
                DihClientAddon.LOG.error("Failed to serialize Dih config", t);
            }
        });
    }

    static void capturePendingNow() {
        SaveCoordinator.capturePendingConfigNow();
    }

    static void flushBlocking(long timeoutMs) {
        SaveCoordinator.flushBlocking(timeoutMs);
    }
}
