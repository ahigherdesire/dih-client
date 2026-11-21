package dihclient.script;

import dihclient.script.lang.*;
import dihclient.commands.DihCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Local script supervision and connection lifecycle. Game events never call the interpreter directly. */
public final class ScriptLifecycle {
    private record Command(Interpreter owner, String handler) {}
    private static final Map<String, Command> COMMANDS = new ConcurrentHashMap<>();
    private static final Map<Interpreter, ScriptReconnectGuard> RESUME = new ConcurrentHashMap<>();
    private record ReconnectRequest(Interpreter owner, String token) {}
    private static final ThreadLocal<ReconnectRequest> RECONNECT_REQUEST = new ThreadLocal<>();
    private static Object activeAttempt;
    private static final Set<Interpreter> FOCUS = ConcurrentHashMap.newKeySet();
    private static Boolean previousPause;
    private static ServerData lastServer;
    private static Map<String, Object> lastSnapshot = Map.of();
    private static boolean dead;
    private ScriptLifecycle() {}

    public static Library create() {
        Library lib = new Library();
        lib.define("Events", "on(event, handler)", "Register a named callback for tick, damage, death, join, disconnect, chat or resume.", 2, 2, a -> {
            String event = a.string(0).toLowerCase(Locale.ROOT);
            if (!Set.of("tick", "damage", "death", "join", "disconnect", "chat", "resume").contains(event)) throw a.error("unknown event");
            a.interpreter().on(event, a.string(1)); return true;
        });
        lib.define("Control", "supervise(enabled)", "Keep this script's event worker alive after main returns, including while disconnected.", 1, 1,
            a -> { a.interpreter().supervise(Values.truthy(a.get(0))); return true; });
        lib.define("Control", "startScript(name)", "Explicitly start another script; returns null on success or an error.", 1, 1, a -> ScriptManager.run(a.string(0)));
        lib.define("Control", "stopScript(name)", "Stop a mining script while this supervisor remains running.", 1, 1, a -> ScriptManager.stop(a.string(0)));
        lib.define("Control", "scriptRunning(name)", "Whether the named script is running.", 1, 1, a -> ScriptManager.isRunning(a.string(0)));
        lib.define("Control", "registerClientCommand(name, handler)", "Register a local DIH command using a named function; callback receives the argument text.", 2, 2, a -> {
            String name = a.string(0).toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z][a-z0-9_-]{0,31}")) throw a.error("invalid command name");
            a.interpreter().validateHandler(a.string(1));
            if (DihCommands.find(name) != null) return false;
            Command command = new Command(a.interpreter(), a.string(1));
            Command old = COMMANDS.putIfAbsent(name, command);
            if (old != null && old.owner != a.interpreter()) return false;
            COMMANDS.put(name, command); return true;
        });
        lib.define("Control", "armAutoLogResume(enabled)", "Arm one resume event after an explicit reconnectLastServer; death or an ordinary join disarms it.", 1, 1,
            a -> { state(a.interpreter()).arm(Values.truthy(a.get(0))); return true; });
        lib.define("Player", "disconnect(reason)", "Disconnect with a local reason. Supervisor workers stay alive.", 0, 1, a -> GameLibrary.onClient(() -> {
            var connection = Minecraft.getInstance().getConnection();
            if (connection == null) return false;
            connection.getConnection().disconnect(Component.literal(a.string(0, "Disconnected by script"))); return true;
        }));
        lib.define("Player", "reconnectLastServer()", "Explicitly reconnect to the last multiplayer server; returns false if unavailable.", 0, 0, a -> GameLibrary.onClient(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (lastServer == null || mc.getConnection() != null || !mc.allowsMultiplayer()) return false;
            var guard = state(a.interpreter());
            String token = guard.requestReconnect(lastServer.ip);
            RECONNECT_REQUEST.set(new ReconnectRequest(a.interpreter(), token));
            try {
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(lastServer.ip), lastServer, false, null);
            } finally {
                RECONNECT_REQUEST.remove();
                guard.cancelRequest(token);
            }
            return true;
        }));
        lib.define("Player", "respawn()", "Request normal respawn only while dead.", 0, 0, a -> GameLibrary.onClient(() -> {
            var p = Minecraft.getInstance().player;
            if (p == null || p.isAlive()) return false;
            p.respawn(); return true;
        }));
        lib.define("Control", "setPauseOnLostFocus(enabled)", "Temporarily control pause on lost focus; restore the original setting when all owners stop.", 1, 1, a -> GameLibrary.onClient(() -> {
            var options = Minecraft.getInstance().options;
            if (FOCUS.isEmpty()) previousPause = options.pauseOnLostFocus;
            FOCUS.add(a.interpreter()); options.pauseOnLostFocus = Values.truthy(a.get(0)); return true;
        }));
        return lib;
    }

    private static ScriptReconnectGuard state(Interpreter it) { return RESUME.computeIfAbsent(it, k -> new ScriptReconnectGuard()); }

    public static void connectionStarted(Object attempt, String server) {
        activeAttempt = attempt;
        ReconnectRequest request = RECONNECT_REQUEST.get();
        RESUME.forEach((it, guard) -> guard.connectionStarted(attempt, server,
            request != null && request.owner == it ? request.token : null));
    }

    public static void connectionFailed(Object attempt) {
        RESUME.values().forEach(guard -> guard.failed(attempt));
        if (activeAttempt == attempt) activeAttempt = null;
    }

    public static void connectionFailed() { connectionFailed(activeAttempt); }

    public static boolean dispatchCommand(String body) {
        String[] parts = body.strip().split("\\s+", 2);
        Command command = COMMANDS.get(parts[0].toLowerCase(Locale.ROOT));
        if (command == null) return false;
        command.owner.enqueueCallback(command.handler, parts.length == 2 ? parts[1] : "");
        return true;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            lastSnapshot = ScriptGame.snapshot();
            if (!mc.player.isAlive()) death();
            else dead = false;
        }
        ScriptManager.emit("tick", (double) System.currentTimeMillis());
    }

    public static void joined() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) lastServer = mc.getCurrentServer();
        dead = false;
        lastSnapshot = ScriptGame.snapshot();
        ScriptManager.emit("join", lastSnapshot);
        String server = mc.getCurrentServer() == null ? "" : mc.getCurrentServer().ip;
        RESUME.forEach((it, state) -> { if (state.joined(activeAttempt, server)) it.enqueueEvent("resume", copy(lastSnapshot)); });
        activeAttempt = null;
    }

    public static void disconnected() {
        connectionFailed();
        ScriptManager.emit("disconnect", lastSnapshot);
        // Release gameplay controls on connection loss without ending supervisor workers.
        ScriptManager.releaseGameKeys();
    }

    public static void health(float health) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.player == null || mc.level == null) return;
        if (health < mc.player.getHealth()) {
            lastSnapshot = ScriptGame.snapshot();
            var snapshot = new LinkedHashMap<>(lastSnapshot);
            snapshot.put("damage", (double) (mc.player.getHealth() - health));
            snapshot.put("healthAfter", (double) health);
            ScriptManager.emit("damage", snapshot);
        }
        if (health <= 0) death();
    }

    public static void death() {
        if (!Minecraft.getInstance().isSameThread() || dead) return;
        dead = true;
        RESUME.values().forEach(ScriptReconnectGuard::died);
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().level != null) lastSnapshot = ScriptGame.snapshot();
        ScriptManager.emit("death", lastSnapshot);
        ScriptManager.releaseGameKeys();
    }

    // Copy snapshots per interpreter so scripts cannot mutate each other's evidence.
    static Object copy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put((String) k, copy(v))); return out;
        }
        if (value instanceof List<?> list) { List<Object> out = new ArrayList<>(); list.forEach(v -> out.add(copy(v))); return out; }
        return value;
    }

    public static void release(Interpreter it) {
        COMMANDS.entrySet().removeIf(e -> e.getValue().owner == it);
        RESUME.remove(it);
        Minecraft.getInstance().execute(() -> {
            if (FOCUS.remove(it) && FOCUS.isEmpty() && previousPause != null) {
                Minecraft.getInstance().options.pauseOnLostFocus = previousPause; previousPause = null;
            }
        });
    }
}
