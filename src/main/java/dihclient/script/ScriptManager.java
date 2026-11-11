package dihclient.script;

import dihclient.DihClientAddon;
import dihclient.modules.PackHideState;
import dihclient.script.lang.Checker;
import dihclient.script.lang.CoreLibrary;
import dihclient.script.lang.Interpreter;
import dihclient.script.lang.Library;
import dihclient.script.lang.Parser;
import dihclient.script.lang.Program;
import dihclient.script.lang.ScriptException;
import dihclient.script.lang.ScriptHost;
import dihclient.util.DihClientMessaging;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Owns DihScript files ({@code <config>/dihclient/scripts/*.dscript}) and the scripts currently
 * running. Each running script gets its own daemon thread; {@link #onClientTick()} drives
 * {@code wait()}.
 */
public final class ScriptManager {
    public static final String EXTENSION = ".dscript";
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private static final Object TICK_LOCK = new Object();
    private static volatile long tick;
    private static final Map<String, Run> RUNNING = new ConcurrentHashMap<>();
    private static volatile Library library;
    private static volatile long revision;

    private ScriptManager() {
    }

    // ---------------------------------------------------------------- files

    public static File directory() {
        File dir = new File(DihClientAddon.FOLDER, "scripts");
        if (!dir.isDirectory()) {
            dir.mkdirs();
            writeQuietly(dir.toPath().resolve("gold_miner" + EXTENSION), ExampleScripts.GOLD_MINER);
            writeQuietly(dir.toPath().resolve("hello" + EXTENSION), ExampleScripts.HELLO);
        }
        return dir;
    }

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    /** Script names (without extension), sorted. */
    public static List<String> names() {
        File[] files = directory().listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(EXTENSION));
        List<String> out = new ArrayList<>();
        if (files != null) {
            for (File f : files) out.add(f.getName().substring(0, f.getName().length() - EXTENSION.length()));
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Resolves a user-typed name to the stored spelling, or null if no such script. */
    public static String resolve(String name) {
        if (name == null) return null;
        for (String n : names()) if (n.equalsIgnoreCase(name.trim())) return n;
        return null;
    }

    public static boolean exists(String name) {
        return resolve(name) != null;
    }

    public static String read(String name) throws IOException {
        String stored = resolve(name);
        if (stored == null) throw new IOException("No script named '" + name + "'");
        return Files.readString(path(stored), StandardCharsets.UTF_8);
    }

    public static void write(String name, String source) throws IOException {
        if (!isValidName(name)) throw new IOException("Script names may only use letters, digits, _ and - (max 32)");
        String stored = resolve(name);
        Files.writeString(path(stored == null ? name : stored), source == null ? "" : source, StandardCharsets.UTF_8);
        revision++;
    }

    public static boolean delete(String name) throws IOException {
        String stored = resolve(name);
        if (stored == null) return false;
        stop(stored);
        boolean deleted = Files.deleteIfExists(path(stored));
        revision++;
        return deleted;
    }

    /** Bumped whenever scripts are created, saved, deleted, started or stopped. */
    public static long revision() {
        return revision;
    }

    private static Path path(String name) {
        return directory().toPath().resolve(name + EXTENSION);
    }

    private static void writeQuietly(Path p, String text) {
        try {
            Files.writeString(p, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            DihClientAddon.LOG.warn("[Scripts] could not write {}", p, e);
        }
    }

    // ---------------------------------------------------------------- checking

    public static Library library() {
        Library lib = library;
        if (lib == null) {
            lib = new Library().include(CoreLibrary.create()).include(GameLibrary.create());
            library = lib;
        }
        return lib;
    }

    /** Parses and statically checks {@code source}; empty when it is valid. */
    public static List<ScriptException> check(String source) {
        return Checker.check(source, library());
    }

    // ---------------------------------------------------------------- running

    /** Starts a script. Returns null on success, or a message explaining why it didn't start. */
    public static String run(String name) {
        if (PackHideState.isHardLocked()) return "Scripts are disabled while panic mode is on.";
        String stored = resolve(name);
        if (stored == null) return "No script named '" + name + "'. Use .scripts list to see them.";
        String key = key(stored);
        if (RUNNING.containsKey(key)) return "'" + stored + "' is already running. Stop it first with .scripts stop " + stored;

        String source;
        try {
            source = read(stored);
        } catch (IOException e) {
            return "Could not read '" + stored + "': " + e.getMessage();
        }
        Program program;
        try {
            program = Parser.parse(source);
        } catch (ScriptException e) {
            return "Syntax error in '" + stored + "' at " + e.describe();
        }
        List<ScriptException> problems = Checker.check(program, library());
        if (!problems.isEmpty()) {
            return "'" + stored + "' has " + problems.size() + " problem(s); first at " + problems.get(0).describe();
        }

        Run run = new Run(stored, program);
        RUNNING.put(key, run);
        revision++;
        run.thread.start();
        return null;
    }

    /** Asks a script to stop; a second call while it is cleaning up interrupts it. */
    public static boolean stop(String name) {
        Run run = RUNNING.get(key(name == null ? "" : name));
        if (run == null) return false;
        run.requestStop();
        return true;
    }

    public static int stopAll() {
        int n = 0;
        for (Run run : List.copyOf(RUNNING.values())) {
            run.requestStop();
            n++;
        }
        return n;
    }

    public static boolean isRunning(String name) {
        return name != null && RUNNING.containsKey(key(name));
    }

    public static List<String> runningNames() {
        List<String> out = new ArrayList<>();
        for (Run r : RUNNING.values()) out.add(r.name);
        Collections.sort(out);
        return out;
    }

    /** Short live status, e.g. "running 2m 05s · line 14 in mineGold()", or null if not running. */
    public static String status(String name) {
        Run run = RUNNING.get(key(name == null ? "" : name));
        if (run == null) return null;
        long secs = (System.currentTimeMillis() - run.startedAt) / 1000;
        String uptime = secs >= 60 ? (secs / 60) + "m " + String.format(Locale.ROOT, "%02d", secs % 60) + "s" : secs + "s";
        String where = run.interpreter.currentLine() > 0
            ? " · line " + run.interpreter.currentLine()
              + (run.interpreter.currentFunction().isEmpty() ? "" : " in " + run.interpreter.currentFunction() + "()")
            : "";
        return (run.cancelled ? "stopping" : "running") + " " + uptime + where;
    }

    /** Called once per client tick (from the mod's END_CLIENT_TICK hook). */
    public static void onClientTick() {
        if (RUNNING.isEmpty()) {
            tick++;
            return;
        }
        if (PackHideState.isHardLocked()) stopAll();
        synchronized (TICK_LOCK) {
            tick++;
            TICK_LOCK.notifyAll();
        }
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) DihClientMessaging.sendPrefixed(text);
        else mc.execute(() -> DihClientMessaging.sendPrefixed(text));
    }

    /** One running script. Implements the interpreter's host: real ticks, chat output, cancellation. */
    private static final class Run implements ScriptHost {
        final String name;
        final Thread thread;
        final Interpreter interpreter;
        final long startedAt = System.currentTimeMillis();
        volatile boolean cancelled;
        private volatile boolean stopRequestedTwice;

        Run(String name, Program program) {
            this.name = name;
            this.interpreter = new Interpreter(program, library(), this);
            this.thread = new Thread(this::execute, "Dih-Script-" + name);
            this.thread.setDaemon(true);
        }

        void requestStop() {
            if (cancelled) {
                stopRequestedTwice = true;
                thread.interrupt();
                return;
            }
            cancelled = true;
            revision++;
            thread.interrupt();
        }

        private void execute() {
            message("§aScript §f" + name + "§a started.");
            String outcome = null;
            try {
                interpreter.run();
            } catch (ScriptException e) {
                outcome = "§cScript §f" + name + "§c error at " + e.describe();
            } catch (Throwable t) {
                DihClientAddon.LOG.warn("[Scripts] '{}' crashed", name, t);
                outcome = "§cScript §f" + name + "§c crashed: " + t;
            }
            try {
                Thread.interrupted(); // clear a stop interrupt so onStop() can still wait
                stopHandlerRunning = true;
                if (!stopRequestedTwice) interpreter.runStopHandler();
            } catch (ScriptException e) {
                if (outcome == null) outcome = "§cScript §f" + name + "§c error in onStop() at " + e.describe();
            } catch (Throwable t) {
                DihClientAddon.LOG.warn("[Scripts] '{}' onStop crashed", name, t);
            } finally {
                GameLibrary.releaseUse();
                RUNNING.remove(key(name), this);
                revision++;
            }
            if (outcome == null) outcome = cancelled ? "§eScript §f" + name + "§e stopped." : "§aScript §f" + name + "§a finished.";
            message(outcome);
        }

        /**
         * Waits on the shared tick clock. A stop request ends the wait — except while onStop() is
         * running, where only a second stop request (a hard kill) does.
         */
        @Override
        public void waitTicks(int ticks) {
            long target = tick + Math.max(0, ticks);
            synchronized (TICK_LOCK) {
                while (tick < target) {
                    if (shouldAbortWait()) throw new Interpreter.StopSignal();
                    try {
                        TICK_LOCK.wait(250);
                    } catch (InterruptedException e) {
                        if (shouldAbortWait()) throw new Interpreter.StopSignal();
                    }
                }
            }
        }

        private boolean shouldAbortWait() {
            return stopRequestedTwice || (cancelled && !stopHandlerRunning);
        }

        /** The interpreter ignores this inside onStop(); hard kills still land through waitTicks(). */
        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        volatile boolean stopHandlerRunning;

        @Override
        public void print(String text) {
            message("§d[" + name + "]§r " + text);
        }
    }
}
