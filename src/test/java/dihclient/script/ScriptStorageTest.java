package dihclient.script;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ScriptStorageTest {
    @TempDir Path folder;

    @Test void staleReadCannotOverwriteConcurrentEdit() throws Exception {
        var store = new ScriptStorage(folder, null);
        var a = store.session();
        var b = store.session();
        assertNull(a.readText("instance", "settings.json"));
        a.writeTextAtomic("instance", "settings.json", "first");
        assertEquals("first", b.readText("instance", "settings.json"));
        a.writeTextAtomic("instance", "settings.json", "second");
        assertThrows(java.io.IOException.class, () -> b.writeTextAtomic("instance", "settings.json", "lost"));
        assertEquals("second", a.readText("instance", "settings.json"));
    }

    @Test void existingFilesRequireAnObservedRevision() throws Exception {
        Files.writeString(folder.resolve("settings.json"), "saved");
        var session = new ScriptStorage(folder, null).session();
        assertThrows(java.io.IOException.class, () -> session.writeTextAtomic("instance", "settings.json", "lost"));
        assertEquals("saved", Files.readString(folder.resolve("settings.json")));
    }

    @Test void expiredOwnerCannotReleaseOrRenewReplacement() throws Exception {
        var time = new AtomicLong(1000);
        var store = new ScriptStorage(folder, null, time::get);
        var a = store.session();
        var b = new ScriptStorage(folder, null, time::get).session();
        String old = a.tryLock("instance", "sword", 180000);
        assertNotNull(old);
        assertNull(b.tryLock("instance", "sword", 20000));
        time.set(181001);
        String current = b.tryLock("instance", "sword", 20000);
        assertNotNull(current);
        assertFalse(a.releaseLock(old));
        assertFalse(a.renewLock(old));
        assertEquals(current, b.lockOwner("instance", "sword").get("token"));
        assertTrue(b.renewLock(current));
        assertTrue(b.releaseLock(current));
        assertNull(a.lockOwner("instance", "sword"));
    }

    @Test void appendPreservesOtherSessionsAndRefreshesObservedRevision() throws Exception {
        var store = new ScriptStorage(folder, null);
        var a = store.session();
        var b = store.session();
        a.appendText("instance", "events/log.txt", "one\n");
        b.appendText("instance", "events/log.txt", "two\n");
        assertEquals("one\ntwo\n", a.readText("instance", "events/log.txt"));
        assertEquals(java.util.List.of("log.txt"), a.listFiles("instance", "events"));
        assertEquals(java.util.List.of("events"), a.listFiles("instance", ""));
    }

    @Test void unsafePathsAndMissingSharedScopeFail() throws Exception {
        var a = new ScriptStorage(folder, null).session();
        assertThrows(java.io.IOException.class, () -> a.readText("instance", "../escape"));
        assertThrows(java.io.IOException.class, () -> a.readText("instance", folder.resolve("absolute").toString()));
        assertThrows(java.io.IOException.class, () -> a.readText("instance", ".dih-script-internal/guard"));
        assertThrows(java.io.IOException.class, () -> a.readText("shared", "file"));
        assertThrows(java.io.IOException.class, () -> a.tryLock("instance", "x", 0));
    }

    @Test void anotherJavaProcessCannotAcquireAnActiveLease() throws Exception {
        String classes = Path.of(ScriptStorage.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        String tests = Path.of(ProcessProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        String executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process child = new ProcessBuilder(executable, "-cp", classes + java.io.File.pathSeparator + tests,
            ProcessProbe.class.getName(), folder.toString()).redirectErrorStream(true).start();
        assertTrue(child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
        String token = new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, child.exitValue(), token);
        assertTrue(token.matches("[0-9a-f-]{36}"), token);
        var session = new ScriptStorage(folder, null).session();
        assertNull(session.tryLock("instance", "process", 20000));
        assertEquals(token, session.lockOwner("instance", "process").get("token"));
    }

    @Test void stoppedSessionCannotAcquireALeaseAfterCleanup() throws Exception {
        var session = new ScriptStorage(folder, null).session();
        session.close();
        assertThrows(java.io.IOException.class, () -> session.tryLock("instance", "sword", 180000));
        assertNull(new ScriptStorage(folder, null).session().lockOwner("instance", "sword"));
    }

    public static final class ProcessProbe {
        public static void main(String[] args) throws Exception {
            System.out.println(new ScriptStorage(Path.of(args[0]), null).session().tryLock("instance", "process", 180000));
        }
    }
}
