package dihclient.script;

import dihclient.DihClientAddon;
import dihclient.script.lang.Interpreter;
import dihclient.script.lang.Library;
import dihclient.script.lang.ScriptException;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/** Persistent and network facilities. No Minecraft objects are used by an I/O operation. */
public final class ScriptServices {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Map<Interpreter, ScriptStorage.Session> SESSIONS = new WeakHashMap<>();
    private static final java.util.Set<Interpreter> CLOSED = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(128), task -> {
            Thread thread = new Thread(task, "Dih-Script-IO"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.AbortPolicy());

    private ScriptServices() { }

    public static Library create() {
        // Resolve loader configuration lazily: the function catalogue is also constructed in plain JVM tests.
        LocalConfiguration config = new LocalConfiguration();
        return create(config::storage, new SecretStore(() -> DihClientAddon.FOLDER.toPath().resolve("script-secrets.properties")));
    }

    static Library create(ScriptStorage storage, Path secretFile) {
        return create(() -> storage, new SecretStore(() -> secretFile));
    }

    private static Library create(Supplier<ScriptStorage> storage, SecretStore secrets) {
        Library lib = new Library();
        lib.define("Storage", "readText(scope, path)", "Read UTF-8 text or null if missing; remembers the revision for conflict-safe writing.", 2, 2,
            a -> io(a, () -> session(a.interpreter(), storage).readText(a.string(0), a.string(1))));
        lib.define("Storage", "writeTextAtomic(scope, path, text)", "Atomically replace a file; fails if it changed since this script read or wrote it. Existing files must be read first.", 3, 3,
            a -> io(a, () -> session(a.interpreter(), storage).writeTextAtomic(a.string(0), a.string(1), a.string(2))));
        lib.define("Storage", "appendText(scope, path, text)", "Append UTF-8 text under a process-safe storage lock.", 3, 3,
            a -> io(a, () -> session(a.interpreter(), storage).appendText(a.string(0), a.string(1), a.string(2))));
        lib.define("Storage", "listFiles(scope, directory)", "Sorted immediate file/directory names; missing directory gives an empty list.", 2, 2,
            a -> io(a, () -> session(a.interpreter(), storage).listFiles(a.string(0), a.string(1))));
        lib.define("Storage", "tryLock(scope, name, leaseMilliseconds)", "Acquire a renewable cross-process lease; opaque handle, or null while owned by another client.", 3, 3,
            a -> io(a, () -> session(a.interpreter(), storage).tryLock(a.string(0), a.string(1), lease(a))));
        lib.define("Storage", "renewLock(handle)", "Renew your unexpired lease using its original duration. False if ownership was lost.", 1, 1,
            a -> io(a, () -> session(a.interpreter(), storage).renewLock(a.string(0))));
        lib.define("Storage", "releaseLock(handle)", "Release only this script's lease. A stale handle cannot remove a replacement lease.", 1, 1,
            a -> io(a, () -> session(a.interpreter(), storage).releaseLock(a.string(0))));
        lib.define("Storage", "lockOwner(scope, name)", "Active lease {token, expiresAt, leaseMilliseconds}, or null.", 2, 2,
            a -> io(a, () -> session(a.interpreter(), storage).lockOwner(a.string(0), a.string(1))));
        lib.define("Network", "httpRequest(method, url, headers, body, timeoutMilliseconds)", "Asynchronous HTTP returning {status, headers, body}; preserves error and rate-limit responses. Headers are an object; default timeout 15000ms.", 2, 5,
            a -> httpRequest(a.interpreter(), a.string(0), a.string(1), a.get(2), a.get(3), a.integer(4, 15000)));
        lib.define("Network", "secret(name)", "Read a local script secret or DIH_SECRET_NAME environment variable; missing returns null.", 1, 1,
            a -> io(a, () -> secrets.get(a.string(0))));
        return lib;
    }

    private static long lease(Library.Args a) {
        double value = a.number(2);
        if (!Double.isFinite(value) || value != Math.rint(value) || value < 1 || value > 86_400_000) {
            throw a.error("leaseMilliseconds must be a whole number from 1 to 86400000");
        }
        return (long) value;
    }

    @FunctionalInterface private interface IoTask { Object run() throws IOException; }

    private static Object io(Library.Args args, IoTask task) {
        final CompletableFuture<Object> result;
        try {
            result = CompletableFuture.supplyAsync(() -> {
                try { return task.run(); }
                catch (IOException e) { throw new CompletionException(e); }
            }, IO);
        } catch (RejectedExecutionException e) { throw args.error("Script I/O is busy; retry later"); }
        try {
            while (!result.isDone()) args.interpreter().waitTicks(1);
            return result.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException io) throw args.error(io.getMessage());
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw args.error("Script I/O failed");
        } finally {
            if (!result.isDone()) result.cancel(true);
        }
    }

    private static synchronized ScriptStorage.Session session(Interpreter it, Supplier<ScriptStorage> storage) {
        if (CLOSED.contains(it)) throw new ScriptException("Script storage session is closed", it.currentLine());
        return SESSIONS.computeIfAbsent(it, ignored -> storage.get().session());
    }

    /** Called at the end of a script. Ownership is checked again on disk during every release. */
    public static void release(Interpreter it) {
        ScriptStorage.Session state;
        synchronized (ScriptServices.class) { CLOSED.add(it); state = SESSIONS.remove(it); }
        if (state != null) {
            state.close();
            try { IO.execute(state::releaseAll); }
            catch (RejectedExecutionException ignored) { /* Process-safe leases expire even when cleanup cannot be scheduled. */ }
        }
    }

    static Map<String, Object> httpRequest(Interpreter it, String method, String url, Object headers, Object body, int timeoutMilliseconds) {
        if (timeoutMilliseconds < 1 || timeoutMilliseconds > 300000) throw new ScriptException("HTTP timeout must be 1–300000 milliseconds", it.currentLine());
        if (body != null && !(body instanceof String)) throw new ScriptException("HTTP body must be a string or null", it.currentLine());
        final HttpRequest request;
        try {
            URI uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMilliseconds));
            if (headers != null) {
                if (!(headers instanceof Map<?, ?> map)) throw new ScriptException("HTTP headers must be an object or null", it.currentLine());
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String name) || !(entry.getValue() instanceof String value)) {
                        throw new ScriptException("HTTP header names and values must be strings", it.currentLine());
                    }
                    builder.header(name, value);
                }
            }
            request = builder.method(method.toUpperCase(java.util.Locale.ROOT), body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString((String) body)).build();
        } catch (IllegalArgumentException e) {
            // Exception messages from the HTTP builder can contain credentials or header values.
            throw new ScriptException("Invalid HTTP method, URL or headers", it.currentLine());
        }
        var response = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            // Cooperative wait: tick/death/clutch callbacks continue on the same interpreter worker.
            while (!response.isDone()) it.waitTicks(1);
            HttpResponse<String> result = response.join();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", (double) result.statusCode());
            out.put("headers", new LinkedHashMap<>(result.headers().map()));
            out.put("body", result.body());
            return out;
        } catch (CompletionException e) {
            throw new ScriptException("HTTP request failed (network error or timeout)", it.currentLine());
        } finally {
            if (!response.isDone()) response.cancel(true);
        }
    }

    /** A malformed edit never replaces the last successfully loaded configuration. */
    private static final class LocalConfiguration {
        private ScriptStorage current;
        private String sharedDirectory;

        synchronized ScriptStorage storage() {
            Path configFolder = DihClientAddon.FOLDER.toPath();
            String candidate = sharedDirectory;
            try {
                Properties settings = readProperties(configFolder.resolve("script-services.properties"));
                String configured = System.getProperty("dih.script.sharedDirectory", settings.getProperty("sharedDirectory", "")).trim();
                if (!configured.isEmpty()) Path.of(configured).toAbsolutePath().normalize();
                candidate = configured;
            } catch (IOException | IllegalArgumentException ignored) { /* Keep the last valid settings. */ }
            if (current == null || !java.util.Objects.equals(candidate, sharedDirectory)) {
                current = new ScriptStorage(configFolder.resolve("script-data"), candidate == null || candidate.isEmpty() ? null : Path.of(candidate));
                sharedDirectory = candidate;
            }
            return current;
        }
    }

    private static Properties readProperties(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(file)) properties.load(new StringReader(Files.readString(file)));
        return properties;
    }

    private static final class SecretStore {
        private final Supplier<Path> file;
        private Properties previous = new Properties();

        SecretStore(Supplier<Path> file) { this.file = file; }

        synchronized String get(String name) {
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) throw new ScriptException("Invalid secret name", 0);
            String environment = System.getenv("DIH_SECRET_" + name.toUpperCase(java.util.Locale.ROOT));
            if (environment != null) return environment;
            try { previous = readProperties(file.get()); }
            catch (IOException | IllegalArgumentException ignored) { /* Retain last valid secrets without logging them. */ }
            return previous.getProperty(name);
        }
    }
}
