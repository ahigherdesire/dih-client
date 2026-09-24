package dihclient.util.oresim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DihOreSimSeedStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void valuesAreIsolatedByTheExactWaypointScope() {
        DihOreSimSeedStore store = fresh("scopes.json");

        assertEquals("101", store.value("play.example.com", "101"));
        store.put("second.example.com", "202");

        assertEquals("101", store.value("play.example.com", "ignored"));
        assertEquals("202", store.value("second.example.com", "ignored"));
        assertEquals("", store.value("third.example.com", "must-not-leak"));
        store.flushForTest();
    }

    @Test
    void unknownScopeNeitherMigratesNorWrites() {
        DihOreSimSeedStore store = fresh("unknown.json");

        assertEquals("", store.value("unknown", "55"));
        store.put("unknown", "66");

        assertEquals("55", store.value("Known World", "55"),
            "the first known scope still owns the one-time legacy migration");
        assertEquals("", store.value("Another World", "66"));
        store.flushForTest();
    }

    @Test
    void legacyGlobalValueMigratesOnlyToTheFirstKnownScopeAndStaysCompletedAfterReload() {
        Path file = temporaryDirectory.resolve("migration.json");
        DihOreSimSeedStore first = DihOreSimSeedStore.loadForTest(file.toFile());

        assertEquals("-9223372036854775808", first.value("First World", "-9223372036854775808"));
        assertEquals("", first.value("Second World", "1234"));
        first.flushForTest();

        DihOreSimSeedStore reloaded = DihOreSimSeedStore.loadForTest(file.toFile());
        assertEquals("-9223372036854775808", reloaded.value("First World", "changed-global"));
        assertEquals("", reloaded.value("Third World", "changed-global"));
    }

    @Test
    void clearingOneScopeIsDurableAndDoesNotTouchAnother() {
        Path file = temporaryDirectory.resolve("clear.json");
        DihOreSimSeedStore store = DihOreSimSeedStore.loadForTest(file.toFile());
        store.put("server-a", "1");
        store.put("server-b", "2");
        store.put("server-a", "   ");
        store.flushForTest();

        DihOreSimSeedStore reloaded = DihOreSimSeedStore.loadForTest(file.toFile());
        assertEquals("", reloaded.value("server-a", "legacy-is-already-disabled"));
        assertEquals("2", reloaded.value("server-b", "legacy-is-already-disabled"));
    }

    @Test
    void mapOnlyPreReleaseFormatLoadsWithoutReactivatingGlobalFallback() throws Exception {
        Path file = temporaryDirectory.resolve("map-only.json");
        Files.writeString(file, "{\"server-a\":\"77\"}");

        DihOreSimSeedStore store = DihOreSimSeedStore.loadForTest(file.toFile());
        assertEquals("77", store.value("server-a", "999"));
        assertEquals("", store.value("server-b", "999"));
    }

    private DihOreSimSeedStore fresh(String name) {
        return DihOreSimSeedStore.loadForTest(temporaryDirectory.resolve(name).toFile());
    }
}
