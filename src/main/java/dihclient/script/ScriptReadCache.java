package dihclient.script;

import dihclient.script.lang.Library;
import dihclient.script.lang.ScriptException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Publishes read-only script observations from the client tick. */
final class ScriptReadCache {
    private static final int MAX_WATCHED_QUERIES = 256;
    private final Set<Call> requested = ConcurrentHashMap.newKeySet();
    record Call(String name, List<Object> arguments) {
        Call { arguments = Collections.unmodifiableList(new ArrayList<>(arguments)); }
    }
    private record Value(Object result, String error, int line) {
        static Value success(Object value) { return new Value(copy(value), null, 0); }
        Object get() {
            if (error != null) throw new ScriptException(error, line);
            return copy(result);
        }
    }
    // Publish complete frames; never expose a partly captured inventory to another worker.
    private record Frame(Map<Call, Value> values, Map<String, Value> overrides) { }
    private volatile Frame frame = new Frame(new ConcurrentHashMap<>(), Map.of());

    /** No game objects or client submissions: safe even from a Netty disconnect callback. */
    synchronized void disconnected(Set<String> names) {
        Map<String, Value> overrides = new HashMap<>();
        for (String name : names) {
            if (name.equals("moduleEnabled")) continue; // Optional mod state does not require a world.
            Value value = switch (name) {
                case "inWorld", "alive", "guiOpen", "baritoneBusy" -> Value.success(false);
                case "guiTitle" -> Value.success("");
                case "guiSize", "slotCount" -> Value.success(0d);
                case "slotItem", "slotName" -> Value.success(null);
                case "slotLore" -> Value.success(List.of());
                case "inventorySlot" -> Value.success(-1d);
                default -> new Value(null, "Not in a world", 0);
            };
            overrides.put(name, value);
        }
        frame = new Frame(new ConcurrentHashMap<>(), Map.copyOf(overrides));
    }

    void wrapActions(Library library, Set<String> names, Runnable refresh) {
        for (String name : names) {
            Library.Entry entry = library.get(name);
            if (entry == null) continue;
            library.define(entry.category(), entry.signature(), entry.description(), entry.minArgs(), entry.maxArgs(), a -> {
                try { return entry.fn().call(a); }
                finally { refresh.run(); }
            });
        }
    }

    Object version() { return frame; }
    Collection<Call> requested() { return List.copyOf(requested); }
    private synchronized void watch(Call call) {
        if (requested.size() < MAX_WATCHED_QUERIES) requested.add(call);
    }

    void capture(Library source, Collection<Call> calls) { capture(source, calls, frame); }

    void capture(Library source, Collection<Call> calls, Object previous) {
        Map<Call, Value> next = new ConcurrentHashMap<>();
        for (Call call : calls) {
            if (frame != previous) return;
            Library.Entry entry = source.get(call.name());
            if (entry == null) continue;
            try {
                Object result = entry.fn().call(new Library.Args(entry, call.arguments(), null, 0));
                next.put(call, Value.success(result));
            } catch (ScriptException error) {
                next.put(call, new Value(null, error.getMessage(), error.line()));
            }
        }
        synchronized (this) {
            // An asynchronous disconnect must win over a capture of its departing world.
            if (frame == previous) frame = new Frame(next, Map.of());
        }
    }

    void wrap(Library library, Set<String> names) {
        for (String name : names) {
            Library.Entry entry = library.get(name);
            if (entry == null) continue;
            library.define(entry.category(), entry.signature(), entry.description(), entry.minArgs(), entry.maxArgs(), a -> {
                Frame current = frame;
                Call call = new Call(name, a.all());
                Value value = current.overrides().get(name);
                if (value == null) value = current.values().get(call);
                if (value == null) {
                    // Unusual query arguments still use the original validation and game-thread reader.
                    value = Value.success(entry.fn().call(a));
                    // Only observe successful queries actually used by scripts; do not eagerly
                    // rescan every possible item/radius/enchantment on every client tick.
                    watch(call);
                    // A client tick/action may have published a newer frame during that read.
                    if (frame == current) current.values().putIfAbsent(call, value);
                }
                return value.get();
            });
        }
    }

    private static Object copy(Object value) {
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) result.add(copy(item));
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put((String) key, copy(item)));
            return result;
        }
        return value;
    }
}
