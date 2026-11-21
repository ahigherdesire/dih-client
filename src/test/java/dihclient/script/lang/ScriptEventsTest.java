package dihclient.script.lang;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScriptEventsTest {
    @Test void callbacksRunDuringLongWaitAndNeverReenterThemselves() {
        var output = new ArrayList<String>();
        Interpreter[] interpreter = new Interpreter[1];
        ScriptHost host = new ScriptHost() {
            int ticks;
            public void waitTicks(int n) {
                ticks += n;
                interpreter[0].enqueueEvent("tick", (double) ticks);
            }
            public boolean isCancelled() { return false; }
            public void print(String text) { output.add(text); }
        };
        var lib = CoreLibrary.create();
        lib.define("Events", "on(event, handler)", "", 2, 2,
            a -> { a.interpreter().on(a.string(0), a.string(1)); return true; });
        interpreter[0] = new Interpreter(Parser.parse("""
            let calls = 0;
            function monitor(event) { calls += 1; if (calls == 1) { wait(1); } }
            function main() { on("tick", "monitor"); wait(3); print(calls); }
            """), lib, host);
        interpreter[0].run();
        assertEquals(List.of("3"), output); // Nested waits coalesce tick notifications rather than reentering.
    }

    @Test void unknownCallbackIsRejectedAtRegistration() {
        var host = new InterpreterTest.FakeHost();
        var interpreter = new Interpreter(Parser.parse("function main() {}"), CoreLibrary.create(), host);
        assertThrows(ScriptException.class, () -> interpreter.on("damage", "missing"));
    }

    @Test void monitoringContinuesInsideAnotherCallbacksWait() {
        var output = new ArrayList<String>();
        Interpreter[] current = new Interpreter[1];
        ScriptHost host = new ScriptHost() {
            public void waitTicks(int n) { current[0].enqueueEvent("tick", null); }
            public boolean isCancelled() { return false; }
            public void print(String text) { output.add(text); }
        };
        var lib = CoreLibrary.create();
        lib.define("Events", "on(event, handler)", "", 2, 2, a -> { a.interpreter().on(a.string(0), a.string(1)); return true; });
        lib.define("Events", "fire()", "", 0, 0, a -> { a.interpreter().enqueueEvent("chat", "test"); return true; });
        current[0] = new Interpreter(Parser.parse("""
            let monitored = 0;
            function monitor(event) { monitored += 1; }
            function slowCommand(event) { wait(2); }
            function main() {
                on("tick", "monitor"); on("chat", "slowCommand");
                fire(); wait(1); print(monitored);
            }
            """), lib, host);
        current[0].run();
        assertEquals(List.of("3"), output);
    }

    @Test void deathIsPrioritizedEvenWhenChatQueueIsFull() {
        var output = new ArrayList<String>();
        var lib = CoreLibrary.create();
        lib.define("Events", "on(event, handler)", "", 2, 2, a -> { a.interpreter().on(a.string(0), a.string(1)); return true; });
        lib.define("Events", "flood()", "", 0, 0, a -> {
            for (int i = 0; i < 1100; i++) a.interpreter().enqueueEvent("chat", "chat");
            a.interpreter().enqueueEvent("death", "death"); return true;
        });
        new Interpreter(Parser.parse("""
            function received(event) { print(event); }
            function main() { on("chat", "received"); on("death", "received"); flood(); wait(0); }
            """), lib, new ScriptHost() {
                public void waitTicks(int n) {}
                public boolean isCancelled() { return false; }
                public void print(String s) { output.add(s); }
            }).run();
        assertEquals("death", output.getFirst());
    }
}
