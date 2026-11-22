package dihclient.script;

import dihclient.script.lang.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ScriptReadCacheTest {
    @Test void hundredInventoryReadsUsePublishedTickWithoutClientThreadRoundTrips() {
        AtomicInteger reads = new AtomicInteger();
        Library game = new Library().define("Inventory", "inventoryItem(slot)", "", 1, 1,
            a -> { reads.incrementAndGet(); return "minecraft:raw_gold"; });
        ScriptReadCache cache = new ScriptReadCache();
        cache.capture(game, List.of(new ScriptReadCache.Call("inventoryItem", List.of(1d))));
        reads.set(0);
        Library library = new Library().include(CoreLibrary.create()).include(game);
        cache.wrap(library, Set.of("inventoryItem"));
        Host host = run(library, """
            function main() {
                for (let i in range(100)) { inventoryItem(1); }
                print(inventoryItem(1));
            }
            """);
        assertEquals(0, reads.get(), "published reads must not enqueue another client task");
        assertEquals(List.of("minecraft:raw_gold"), host.output);
        assertEquals(0, host.ticks);
    }

    @Test void nextPublishedTickReplacesValuesAndScriptCannotMutateSharedLists() {
        List<Object> live = new ArrayList<>(List.of("first"));
        Library game = new Library().define("Player", "playersNearby()", "", 0, 0, a -> live);
        ScriptReadCache cache = new ScriptReadCache();
        var calls = List.of(new ScriptReadCache.Call("playersNearby", List.of()));
        cache.capture(game, calls);
        live.set(0, "second");
        Library library = new Library().include(CoreLibrary.create()).include(game);
        cache.wrap(library, Set.of("playersNearby"));
        assertEquals(List.of("first", "first"), run(library, """
            function main() {
                let p = playersNearby(); print(p[0]); p[0] = "corrupted";
                print(playersNearby()[0]);
            }
            """).output);
        cache.capture(game, calls);
        assertEquals(List.of("second"), run(library,
            "function main() { print(playersNearby()[0]); }").output);
    }

    @Test void capturePreservesNoWorldErrorsWithoutPoisoningOtherReads() {
        Library game = new Library()
            .define("Player", "health()", "", 0, 0, a -> { throw new ScriptException("Not in a world", 0); })
            .define("Player", "inWorld()", "", 0, 0, a -> false);
        ScriptReadCache cache = new ScriptReadCache();
        cache.capture(game, List.of(new ScriptReadCache.Call("health", List.of()),
            new ScriptReadCache.Call("inWorld", List.of())));
        Library library = new Library().include(CoreLibrary.create()).include(game);
        cache.wrap(library, Set.of("health", "inWorld"));
        assertEquals(List.of("false"), run(library, "function main() { print(inWorld()); }").output);
        assertTrue(assertThrows(ScriptException.class,
            () -> run(library, "function main() { health(); }")).getMessage().contains("Not in a world"));
    }

    @Test void unplannedArgumentsFallbackOncePerTickAndActionsAreNeverCached() {
        AtomicInteger reads = new AtomicInteger(), writes = new AtomicInteger();
        Library game = new Library()
            .define("Inventory", "count(item)", "", 1, 1, a -> (double) reads.incrementAndGet())
            .define("Player", "attack()", "", 0, 0, a -> (double) writes.incrementAndGet());
        ScriptReadCache cache = new ScriptReadCache();
        Library library = new Library().include(CoreLibrary.create()).include(game);
        cache.wrap(library, Set.of("count"));
        Host host = run(library, """
            function main() {
                print(count("raw_gold")); print(count("raw_gold"));
                attack(); attack();
            }
            """);
        assertEquals(List.of("1", "1"), host.output);
        assertEquals(2, writes.get());
        cache.capture(game, List.of());
        assertEquals(List.of("2"), run(library, "function main() { print(count(\"raw_gold\")); }").output);
    }

    @Test void aLiveLookupCannotOverwriteANewerTickPublishedWhileItWasWaiting() {
        ScriptReadCache cache = new ScriptReadCache();
        Library next = new Library().define("Inventory", "inventoryItem(slot)", "", 1, 1,
            a -> "minecraft:diamond");
        Library previous = new Library().define("Inventory", "inventoryItem(slot)", "", 1, 1, a -> {
            cache.capture(next, List.of(new ScriptReadCache.Call("inventoryItem", List.of(1d))));
            return "minecraft:raw_gold";
        });
        Library library = new Library().include(CoreLibrary.create()).include(previous);
        cache.wrap(library, Set.of("inventoryItem"));
        assertEquals(List.of("minecraft:raw_gold", "minecraft:diamond"), run(library, """
            function main() { print(inventoryItem(1)); print(inventoryItem(1)); }
            """).output);
    }

    @Test void nullGuiValuesAreCachedAndNestedBatchObjectsAreDetached() {
        AtomicInteger lookups = new AtomicInteger();
        Map<String, Object> stack = new LinkedHashMap<>(); stack.put("count", 64d);
        List<Object> inventory = new ArrayList<>(List.of(stack));
        Library game = new Library()
            .define("GUI", "slotName(slot)", "", 1, 1, a -> { lookups.incrementAndGet(); return null; })
            .define("Inventory", "inventorySnapshot()", "", 0, 0, a -> inventory);
        ScriptReadCache cache = new ScriptReadCache();
        cache.capture(game, List.of(new ScriptReadCache.Call("slotName", List.of(0d)),
            new ScriptReadCache.Call("inventorySnapshot", List.of())));
        stack.put("count", 1d); lookups.set(0);
        Library library = new Library().include(CoreLibrary.create()).include(game);
        cache.wrap(library, Set.of("slotName", "inventorySnapshot"));
        assertEquals(List.of("null", "null", "64"), run(library, """
            function main() {
                print(slotName(0)); print(slotName(0));
                let batch = inventorySnapshot(); batch[0]["count"] = 0;
                print(inventorySnapshot()[0]["count"]);
            }
            """).output);
        assertEquals(0, lookups.get());
    }

    @Test void disconnectDuringCaptureCannotPublishTheDepartingWorldOrSubmitLiveReads() {
        ScriptReadCache cache = new ScriptReadCache();
        AtomicInteger liveReads = new AtomicInteger();
        Library game = new Library()
            .define("Player", "inWorld()", "", 0, 0, a -> {
                cache.disconnected(Set.of("inWorld", "health")); return true;
            })
            .define("Player", "health()", "", 0, 0, a -> { liveReads.incrementAndGet(); return 20d; });
        cache.capture(game, List.of(new ScriptReadCache.Call("inWorld", List.of())));
        Library library = new Library().include(CoreLibrary.create()).include(game);
        cache.wrap(library, Set.of("inWorld", "health"));
        assertEquals(List.of("false"), run(library, "function main() { print(inWorld()); }").output);
        assertThrows(ScriptException.class, () -> run(library, "function main() { health(); }"));
        assertEquals(0, liveReads.get(), "disconnect callbacks must not submit into the departing client");
    }

    @Test void guiWaitCompletionAndFailedMutationRefreshBeforeTheNextRead() {
        ScriptReadCache cache = new ScriptReadCache();
        AtomicInteger size = new AtomicInteger();
        Library game = new Library()
            .define("GUI", "guiSize()", "", 0, 0, a -> (double) size.get())
            .define("GUI", "waitForGui()", "", 0, 0, a -> { size.set(54); return true; })
            .define("GUI", "clickSlot()", "", 0, 0, a -> {
                size.set(0); throw new ScriptException("server closed menu", 0);
            });
        var calls = List.of(new ScriptReadCache.Call("guiSize", List.of()));
        cache.capture(game, calls);
        Library library = new Library().include(CoreLibrary.create()).include(game);
        cache.wrap(library, Set.of("guiSize"));
        cache.wrapActions(library, Set.of("waitForGui", "clickSlot"), () -> cache.capture(game, calls));
        assertEquals(List.of("0", "54"), run(library, """
            function main() { print(guiSize()); waitForGui(); print(guiSize()); }
            """).output);
        assertThrows(ScriptException.class, () -> run(library, "function main() { clickSlot(); }"));
        assertEquals(List.of("0"), run(library, "function main() { print(guiSize()); }").output);
    }

    @Test void disconnectBetweenPlanningAndCaptureAlsoFencesTheDepartingWorld() {
        ScriptReadCache cache = new ScriptReadCache();
        AtomicInteger reads = new AtomicInteger();
        Library game = new Library().define("Player", "inWorld()", "", 0, 0,
            a -> { reads.incrementAndGet(); return true; });
        Object plannedVersion = cache.version();
        cache.disconnected(Set.of("inWorld"));
        cache.capture(game, List.of(new ScriptReadCache.Call("inWorld", List.of())), plannedVersion);
        Library library = new Library().include(CoreLibrary.create()).include(game);
        cache.wrap(library, Set.of("inWorld"));
        assertEquals(List.of("false"), run(library, "function main() { print(inWorld()); }").output);
        assertEquals(0, reads.get());
    }

    @Test void onlyObservedArgumentQueriesAreWarmedAndTheirNumberIsBounded() {
        ScriptReadCache cache = new ScriptReadCache();
        AtomicInteger reads = new AtomicInteger();
        Library game = new Library().define("Inventory", "count(item)", "", 1, 1,
            a -> { a.string(0); reads.incrementAndGet(); return 7d; });
        Library library = new Library().include(CoreLibrary.create()).include(game);
        cache.wrap(library, Set.of("count"));
        assertEquals(List.of("7"), run(library, "function main() { print(count(\"raw_gold\")); }").output);
        cache.capture(game, cache.requested());
        reads.set(0);
        assertEquals(List.of("7"), run(library, "function main() { print(count(\"raw_gold\")); }").output);
        assertEquals(0, reads.get(), "observed arguments are warmed on the next tick");
        run(library, "function main() { for (let i in range(1000)) { count(str(i)); } }");
        assertTrue(cache.requested().size() <= 256, "varying arguments must not grow the tick workload without bound");
    }

    private static Host run(Library library, String source) {
        Host host = new Host();
        new Interpreter(Parser.parse(source), library, host).run();
        return host;
    }
    private static class Host implements ScriptHost {
        final List<String> output = new ArrayList<>();
        int ticks;
        public void print(String message) { output.add(message); }
        public void waitTicks(int ticks) { this.ticks += ticks; }
        public boolean isCancelled() { return false; }
    }
}
