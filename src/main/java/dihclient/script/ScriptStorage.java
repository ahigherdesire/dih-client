package dihclient.script;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/** Scoped files and renewable leases shared safely by independent client processes. */
public final class ScriptStorage {
    private static final String INTERNAL = ".dih-script-internal";
    private static final Map<Path, ReentrantLock> JVM_GUARDS = new ConcurrentHashMap<>();
    private final Path instance;
    private final Path shared;
    private final LongSupplier clock;

    public ScriptStorage(Path instance, Path shared) {
        this(instance, shared, System::currentTimeMillis);
    }

    ScriptStorage(Path instance, Path shared, LongSupplier clock) {
        this.instance = instance.toAbsolutePath().normalize();
        this.shared = shared == null ? null : shared.toAbsolutePath().normalize();
        this.clock = clock;
    }

    public Session session() { return new Session(); }

    private Path root(String scope) throws IOException {
        Path result = switch (scope) {
            case "instance" -> instance;
            case "shared" -> shared;
            default -> throw new IOException("scope must be instance or shared");
        };
        if (result == null) throw new IOException("Shared script folder is not configured");
        rejectLinks(result);
        Files.createDirectories(result);
        return result;
    }

    private static void rejectLinks(Path path) throws IOException {
        for (Path p = path; p != null; p = p.getParent()) {
            if (Files.isSymbolicLink(p)) throw new IOException("Symbolic links are not allowed in script storage");
            // Windows junctions are reported as other rather than symbolic links.
            if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)
                && Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS).isOther()) throw new IOException("Reparse points are not allowed in script storage");
        }
    }

    private Path resolve(String scope, String text) throws IOException {
        Path base = root(scope);
        final Path relative;
        try { relative = Path.of(text); }
        catch (RuntimeException e) { throw new IOException("Invalid script storage path"); }
        Path result = base.resolve(relative).normalize();
        if (relative.isAbsolute() || !result.startsWith(base)) throw new IOException("Path escapes script storage");
        for (Path part : base.relativize(result)) {
            if (part.toString().equalsIgnoreCase(INTERNAL)) throw new IOException("Reserved script storage path");
        }
        rejectLinks(result);
        return result;
    }

    @FunctionalInterface private interface IoTask<T> { T run() throws IOException; }

    private <T> T guarded(String scope, IoTask<T> action) throws IOException {
        Path base = root(scope);
        Path internal = base.resolve(INTERNAL);
        rejectLinks(internal);
        Files.createDirectories(internal);
        Path guard = internal.resolve("guard");
        rejectLinks(guard);
        ReentrantLock local = JVM_GUARDS.computeIfAbsent(base, p -> new ReentrantLock());
        try {
            if (!local.tryLock(2, java.util.concurrent.TimeUnit.SECONDS)) throw new IOException("Script storage is busy; retry later");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException("Script storage operation interrupted");
        }
        try (FileChannel channel = FileChannel.open(guard, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while (true) {
                var lock = channel.tryLock();
                if (lock != null) {
                    try (lock) { return action.run(); }
                }
                if (System.nanoTime() >= deadline) throw new IOException("Script storage is busy; retry later");
                try { Thread.sleep(10); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); throw new IOException("Script storage operation interrupted");
                }
            }
        } finally { local.unlock(); }
    }

    private static String revision(Path file) throws IOException {
        if (!Files.exists(file)) return "missing";
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    private static void replace(Path target, String text) throws IOException {
        rejectLinks(target);
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".dih-write-", ".tmp");
        try {
            Files.writeString(temporary, text);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { throw new IOException("Storage filesystem does not support atomic replacement", e); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private Path leaseFile(String scope, String name) throws IOException {
        if (name == null || name.isBlank() || name.length() > 256) throw new IOException("Lock name must contain 1–256 characters");
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            Path file = root(scope).resolve(INTERNAL).resolve("lease-" + digest);
            rejectLinks(file);
            return file;
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    private record Lease(String token, long expires, long duration) { }
    private record Owned(String scope, String name) { }

    private static Lease readLease(Path file) throws IOException {
        if (!Files.exists(file)) return null;
        Properties props = new Properties();
        try {
            props.load(new StringReader(Files.readString(file)));
            String token = props.getProperty("token");
            long expiry = Long.parseLong(props.getProperty("expires"));
            long duration = Long.parseLong(props.getProperty("duration"));
            if (token == null || token.isBlank() || duration <= 0) throw new IllegalArgumentException();
            return new Lease(token, expiry, duration);
        } catch (RuntimeException e) { throw new IOException("Malformed lock record; refusing to replace it", e); }
    }

    private static void writeLease(Path file, Lease lease) throws IOException {
        Properties props = new Properties();
        props.setProperty("token", lease.token);
        props.setProperty("expires", Long.toString(lease.expires));
        props.setProperty("duration", Long.toString(lease.duration));
        StringWriter text = new StringWriter();
        props.store(text, "DIH script lease");
        replace(file, text.toString());
    }

    /** One script's observed revisions and opaque lock handles; never shared with another script. */
    public final class Session {
        private final Map<Path, String> observed = new ConcurrentHashMap<>();
        private final Map<String, Owned> owned = new ConcurrentHashMap<>();
        private volatile boolean closed;

        public String readText(String scope, String path) throws IOException {
            return guarded(scope, () -> {
                Path file = resolve(scope, path);
                String text = Files.exists(file) ? Files.readString(file) : null;
                observed.put(file, revision(file));
                return text;
            });
        }

        public boolean writeTextAtomic(String scope, String path, String text) throws IOException {
            return guarded(scope, () -> {
                Path file = resolve(scope, path);
                String current = revision(file);
                String expected = observed.get(file);
                if (expected == null ? !current.equals("missing") : !expected.equals(current)) {
                    throw new IOException("Concurrent edit conflict; read the file again before writing");
                }
                replace(file, text);
                observed.put(file, revision(file));
                return true;
            });
        }

        public boolean appendText(String scope, String path, String text) throws IOException {
            return guarded(scope, () -> {
                Path file = resolve(scope, path);
                String previous = Files.exists(file) ? Files.readString(file) : "";
                replace(file, previous + text);
                observed.put(file, revision(file));
                return true;
            });
        }

        public List<String> listFiles(String scope, String directory) throws IOException {
            return guarded(scope, () -> {
                Path folder = resolve(scope, directory);
                if (!Files.exists(folder)) return List.of();
                try (var files = Files.list(folder)) {
                    return files.filter(p -> !p.getFileName().toString().equalsIgnoreCase(INTERNAL))
                        .filter(p -> !p.getFileName().toString().startsWith(".dih-write-"))
                        .map(p -> p.getFileName().toString()).sorted().toList();
                }
            });
        }

        public String tryLock(String scope, String name, long milliseconds) throws IOException {
            if (milliseconds <= 0 || milliseconds > 86_400_000) throw new IOException("Lease must be 1–86400000 milliseconds");
            return guarded(scope, () -> {
                if (closed) throw new IOException("Script storage session is closed");
                Path file = leaseFile(scope, name);
                Lease current = readLease(file);
                long now = clock.getAsLong();
                if (current != null && current.expires > now) return null;
                String token = UUID.randomUUID().toString();
                writeLease(file, new Lease(token, Math.addExact(now, milliseconds), milliseconds));
                owned.put(token, new Owned(scope, name));
                if (closed) {
                    // Cleanup may race an already running acquire after interpreter cancellation.
                    Files.deleteIfExists(file);
                    owned.remove(token);
                    throw new IOException("Script storage session is closed");
                }
                return token;
            });
        }

        public boolean renewLock(String handle) throws IOException {
            Owned item = owned.get(handle);
            if (item == null) return false;
            return guarded(item.scope, () -> {
                Path file = leaseFile(item.scope, item.name);
                Lease current = readLease(file);
                long now = clock.getAsLong();
                if (current == null || !current.token.equals(handle) || current.expires <= now) return false;
                writeLease(file, new Lease(handle, Math.addExact(now, current.duration), current.duration));
                return true;
            });
        }

        public boolean releaseLock(String handle) throws IOException {
            Owned item = owned.get(handle);
            if (item == null) return false;
            boolean result = guarded(item.scope, () -> {
                Path file = leaseFile(item.scope, item.name);
                Lease current = readLease(file);
                if (current == null || !current.token.equals(handle)) return false;
                Files.delete(file);
                return true;
            });
            owned.remove(handle);
            return result;
        }

        public Map<String, Object> lockOwner(String scope, String name) throws IOException {
            return guarded(scope, () -> {
                Lease current = readLease(leaseFile(scope, name));
                if (current == null || current.expires <= clock.getAsLong()) return null;
                return Map.of("token", current.token, "expiresAt", (double) current.expires, "leaseMilliseconds", (double) current.duration);
            });
        }

        public void releaseAll() {
            close();
            for (String token : new ArrayList<>(owned.keySet())) {
                try { releaseLock(token); } catch (IOException ignored) { /* A lease still expires if its filesystem is unavailable. */ }
            }
        }

        void close() { closed = true; }
    }
}
