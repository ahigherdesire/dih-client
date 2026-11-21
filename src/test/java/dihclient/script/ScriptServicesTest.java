package dihclient.script;

import com.sun.net.httpserver.HttpServer;
import dihclient.script.lang.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ScriptServicesTest {
    @TempDir Path folder;

    @Test void cleanupPreventsLateTasksFromOpeningANewLeaseSession() throws Exception {
        ScriptStorage storage = new ScriptStorage(folder.resolve("data"), null);
        Library library = new Library().include(CoreLibrary.create())
            .include(ScriptServices.create(storage, folder.resolve("secrets")));
        Interpreter interpreter = new Interpreter(Parser.parse("""
            function main() { tryLock("instance", "sword", 180000); }
            """), library, new Host());
        ScriptServices.release(interpreter);
        ScriptException failure = assertThrows(ScriptException.class, interpreter::run);
        assertTrue(failure.getMessage().contains("closed"));
        assertNull(storage.session().lockOwner("instance", "sword"));
    }

    @Test void requestExposesRateLimitStatusHeadersAndBody() throws Exception {
        var waited = new java.util.concurrent.CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try { assertTrue(waited.await(5, java.util.concurrent.TimeUnit.SECONDS)); }
            catch (InterruptedException e) { throw new AssertionError(e); }
            exchange.getResponseHeaders().add("Retry-After", "20");
            byte[] body = "rate limited".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var host = new Host();
            host.waited = waited;
            var interpreter = new Interpreter(Parser.parse("function main() {}"), new Library(), host);
            Map<String, Object> response = ScriptServices.httpRequest(interpreter, "GET",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/", Map.of(), null, 5000);
            assertEquals(429d, response.get("status"));
            assertEquals("rate limited", response.get("body"));
            assertEquals(List.of("20"), ((Map<?, ?>) response.get("headers")).get("retry-after"));
            assertTrue(host.ticks > 0);
        } finally { server.stop(0); }
    }

    @Test void malformedSecretConfigurationRetainsLastValidValues() throws Exception {
        Path secrets = folder.resolve("secrets.properties");
        Files.writeString(secrets, "discord=local-token\n");
        Library lib = new Library().include(CoreLibrary.create()).include(ScriptServices.create(new ScriptStorage(folder.resolve("data"), null), secrets));
        Host first = run(lib, "function main() { print(secret(\"discord\")); }");
        assertEquals(List.of("local-token"), first.out);
        Files.writeString(secrets, "discord=\\uBOGU\n");
        assertEquals(List.of("local-token"), run(lib, "function main() { print(secret(\"discord\")); }").out);
    }

    @Test void waitingForAnotherProcessesStorageLockStillYieldsToMonitoring() throws Exception {
        Path internal = Files.createDirectories(folder.resolve("data/.dih-script-internal"));
        String classes = Path.of(GuardProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        String executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        Process child = new ProcessBuilder(executable, "-cp", classes, GuardProbe.class.getName(), internal.resolve("guard").toString())
            .redirectErrorStream(true).start();
        try {
            try (var reader = child.inputReader()) { assertEquals("locked", reader.readLine()); }
            Host host = new Host();
            host.duringWait = () -> {
                if (host.ticks == 3) {
                    try { child.getOutputStream().write('\n'); child.getOutputStream().flush(); }
                    catch (java.io.IOException e) { throw new AssertionError(e); }
                }
            };
            Library library = new Library().include(CoreLibrary.create())
                .include(ScriptServices.create(new ScriptStorage(folder.resolve("data"), null), folder.resolve("secrets")));
            new Interpreter(Parser.parse("function main() { print(readText(\"instance\", \"missing.txt\")); }"), library, host).run();
            assertEquals(List.of("null"), host.out);
            assertTrue(host.ticks >= 3);
            assertTrue(child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
        } finally { if (child.isAlive()) child.destroyForcibly(); }
    }

    public static final class GuardProbe {
        public static void main(String[] args) throws Exception {
            try (var channel = java.nio.channels.FileChannel.open(Path.of(args[0]), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
                 var ignored = channel.lock()) {
                System.out.println("locked"); System.out.flush();
                System.in.read();
            }
        }
    }

    private static Host run(Library lib, String source) {
        Host host = new Host();
        new Interpreter(Parser.parse(source), lib, host).run();
        return host;
    }

    private static final class Host implements ScriptHost {
        final List<String> out = new ArrayList<>();
        java.util.concurrent.CountDownLatch waited;
        Runnable duringWait;
        int ticks;
        public void waitTicks(int ticks) {
            this.ticks += ticks;
            if (waited != null) waited.countDown();
            if (duringWait != null) duringWait.run();
            try { Thread.sleep(1); } catch (InterruptedException e) { throw new AssertionError(e); }
        }
        public boolean isCancelled() { return false; }
        public void print(String message) { out.add(message); }
    }
}
