package dihclient.addons;

import dihclient.DihClientAddon;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Finishes addon file changes after Minecraft closes. Windows keeps a loaded mod's jar locked, so an uninstall or
 * update writes its steps to a plan file and starts this class as a small separate Java process, which waits for the
 * game to exit and then deletes or moves the files. If it cannot finish (killed, still locked), the next start runs it
 * again.
 *
 * <p>{@link #main} runs outside Minecraft and must use only {@code java.base}.
 */
public final class AddonJanitor {
    private static final String DELETE = "delete";
    private static final String MOVE = "move";
    private static boolean launched;

    private AddonJanitor() {}

    private static Path plan() {
        return DihClientAddon.FOLDER.toPath().resolve("addon-store").resolve("pending-file-ops.txt");
    }

    /** Delete {@code file} once the game has closed. */
    public static synchronized void scheduleDelete(Path file) {
        append(DELETE + "\t" + file.toAbsolutePath().normalize());
    }

    /** Move {@code from} to {@code to} once the game has closed and every earlier step succeeded. */
    public static synchronized void scheduleMove(Path from, Path to) {
        append(MOVE + "\t" + from.toAbsolutePath().normalize() + "\t" + to.toAbsolutePath().normalize());
    }

    /** Drop scheduled steps that touch {@code file}; true if any did. */
    public static synchronized boolean cancel(Path file) {
        String needle = "\t" + file.toAbsolutePath().normalize();
        List<String> kept = new ArrayList<>();
        boolean removed = false;
        for (String line : read(plan())) {
            if (line.contains(needle)) removed = true;
            else kept.add(line);
        }
        if (removed) write(plan(), kept);
        return removed;
    }

    /** True if a scheduled step will delete or replace {@code file}. */
    public static synchronized boolean isScheduled(Path file) {
        String needle = "\t" + file.toAbsolutePath().normalize();
        for (String line : read(plan())) {
            if (line.contains(needle)) return true;
        }
        return false;
    }

    /** At startup: if the last session left steps undone, start the helper again for this session. */
    public static synchronized boolean resumeIfPending() {
        if (read(plan()).isEmpty()) return false;
        launch();
        return true;
    }

    private static void append(String line) {
        try {
            Files.createDirectories(plan().getParent());
            Files.writeString(plan(), line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            DihClientAddon.LOG.error("[Addons] Could not save the pending file change", e);
            return;
        }
        launch();
    }

    /** Starts the helper once per session; it reads the plan only after the game exits. */
    private static void launch() {
        if (launched) return;
        try {
            List<String> classpath = classpath();
            if (classpath.isEmpty()) throw new IOException("DIH jar not found");
            List<String> command = List.of(javaBinary(), "-Xmx32m", "-XX:+UseSerialGC",
                "-cp", String.join(File.pathSeparator, classpath), AddonJanitor.class.getName(),
                Long.toString(ProcessHandle.current().pid()), plan().toString());
            ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(plan().resolveSibling("file-ops.log").toFile()));
            builder.environment().remove("JAVA_TOOL_OPTIONS");
            builder.environment().remove("_JAVA_OPTIONS");
            builder.start();
            launched = true;
            DihClientAddon.LOG.info("[Addons] Pending addon file changes finish when Minecraft closes (helper classpath {})", classpath);
        } catch (Exception e) {
            DihClientAddon.LOG.error("[Addons] Could not start the cleanup helper; changes finish on a later start", e);
        }
    }

    /** The jar or class folder this class was loaded from. */
    private static List<String> classpath() {
        List<String> out = new ArrayList<>();
        String suffix = AddonJanitor.class.getName().replace('.', '/') + ".class";
        try {
            URL url = AddonJanitor.class.getResource("AddonJanitor.class");
            String s = url == null ? "" : url.toString();
            if (s.startsWith("jar:file:") && s.contains("!/")) {
                out.add(Path.of(URI.create(s.substring(4, s.indexOf("!/")))).toString());
            } else if (s.startsWith("file:") && s.endsWith(suffix)) {
                out.add(Path.of(URI.create(s.substring(0, s.length() - suffix.length()))).toString());
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** {@code javaw} on Windows so no console window opens. */
    private static String javaBinary() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        for (String name : new String[] {"javaw.exe", "java.exe", "java"}) {
            if (Files.isRegularFile(bin.resolve(name))) return bin.resolve(name).toString();
        }
        return ProcessHandle.current().info().command().orElse("java");
    }

    // ---- helper process ----

    public static void main(String[] args) throws Exception {
        if (args.length != 2) return;
        long pid = Long.parseLong(args[0]);
        Path plan = Path.of(args[1]);
        log("waiting for Minecraft (pid " + pid + ") to close");
        ProcessHandle.of(pid).ifPresent(game -> {
            try {
                game.onExit().get();
            } catch (Exception e) {
                while (game.isAlive()) sleep(1000);
            }
        });
        // Steps are re-read after exit, so changes made late in the session are included.
        for (int attempt = 0; attempt < 30; attempt++) {
            sleep(attempt == 0 ? 500 : 1000);
            if (run(plan)) {
                log("done");
                return;
            }
        }
        log("gave up for now; the next start retries");
    }

    /** Runs the steps in order and stops at the first that fails; true once none are left. */
    static boolean run(Path plan) {
        List<String> steps = read(plan);
        int done = 0;
        for (String step : steps) {
            String[] parts = step.split("\t");
            try {
                if (DELETE.equals(parts[0]) && parts.length == 2) {
                    Files.deleteIfExists(Path.of(parts[1]));
                    log("deleted " + parts[1]);
                } else if (MOVE.equals(parts[0]) && parts.length == 3) {
                    if (Files.exists(Path.of(parts[1]))) {
                        Files.move(Path.of(parts[1]), Path.of(parts[2]), StandardCopyOption.REPLACE_EXISTING);
                        log("moved " + parts[1] + " -> " + parts[2]);
                    }
                }
                done++;
            } catch (IOException e) {
                break;
            }
        }
        List<String> left = steps.subList(done, steps.size());
        if (left.isEmpty()) {
            try {
                Files.deleteIfExists(plan);
            } catch (IOException ignored) {
            }
            return true;
        }
        write(plan, left);
        return false;
    }

    private static List<String> read(Path plan) {
        try {
            if (!Files.isRegularFile(plan)) return List.of();
            List<String> out = new ArrayList<>();
            for (String line : Files.readAllLines(plan, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) out.add(line);
            }
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }

    private static void write(Path plan, List<String> lines) {
        try {
            if (lines.isEmpty()) Files.deleteIfExists(plan);
            else Files.write(plan, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void log(String message) {
        System.out.println(Instant.now() + " " + message);
    }

    private static void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
