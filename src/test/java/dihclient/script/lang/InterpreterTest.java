package dihclient.script.lang;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterTest {
    /** Records prints and waited ticks; can be told to cancel after N ticks. */
    static final class FakeHost implements ScriptHost {
        final List<String> out = new ArrayList<>();
        int ticks;
        int cancelAfterTicks = Integer.MAX_VALUE;

        @Override
        public void waitTicks(int t) {
            ticks += t;
            if (ticks >= cancelAfterTicks) throw new Interpreter.StopSignal();
        }

        @Override
        public boolean isCancelled() {
            return ticks >= cancelAfterTicks;
        }

        @Override
        public void print(String message) {
            out.add(message);
        }
    }

    private static FakeHost run(String src) {
        FakeHost host = new FakeHost();
        new Interpreter(Parser.parse(src), CoreLibrary.create(), host).run();
        return host;
    }

    @Test
    void arithmeticAndPrinting() {
        FakeHost h = run("""
            function main() {
                let x = 2 + 3 * 4;
                print(x, "items", 7 / 2, 10 % 3);
                print("a" + 1);
            }
            """);
        assertEquals(List.of("14 items 3.5 1", "a1"), h.out);
    }

    @Test
    void controlFlowAndFunctions() {
        FakeHost h = run("""
            const LIMIT = 5;
            let total = 0;

            function square(n) { return n * n; }

            function main() {
                for (let i in range(LIMIT)) {
                    if (i == 1) { continue; }
                    if (i == 4) { break; }
                    total += square(i);
                }
                let n = 0;
                while (n < 3) { n += 1; }
                repeat (2) { n = n * 2; }
                if (n > 100) { print("big"); } else if (n == 12) { print("twelve"); } else { print("other"); }
                print(total);
            }
            """);
        assertEquals(List.of("twelve", "13"), h.out);
    }

    @Test
    void logicShortCircuitsAndLists() {
        FakeHost h = run("""
            function boom() { error("should not run"); }
            function main() {
                let items = [1, 2, "three"];
                push(items, 4);
                items[0] = 10;
                items[1] += 5;
                print(len(items), items[-1], items[0] + items[1], contains(items, "three"));
                print(false && boom(), true || boom(), !null);
            }
            """);
        assertEquals(List.of("4 4 17 true", "false true true"), h.out);
    }

    @Test
    void waitUsesHostTicks() {
        FakeHost h = run("""
            function main() { wait(5); waitSeconds(1); }
            """);
        assertEquals(25, h.ticks);
    }

    @Test
    void cancellationStopsInfiniteLoop() {
        FakeHost host = new FakeHost();
        host.cancelAfterTicks = 3;
        Interpreter it = new Interpreter(Parser.parse("""
            function main() { while (true) { wait(1); } }
            function onStop() { print("cleaned up"); }
            """), CoreLibrary.create(), host);
        it.run();
        it.runStopHandler();
        assertEquals(List.of("cleaned up"), host.out);
    }

    @Test
    void busyLoopYieldsAutomatically() {
        FakeHost host = new FakeHost();
        host.cancelAfterTicks = 2;
        new Interpreter(Parser.parse("function main() { let n = 0; while (true) { n += 1; } }"),
            CoreLibrary.create(), host).run();
        assertTrue(host.ticks >= 2);
    }

    @Test
    void emptyLoopsYieldAndCanBeCancelled() {
        for (String loop : List.of(
            "let n = 0; while ((n += 1) < 50000) {}",
            "repeat (50000) {}",
            "for (let n in range(50000)) {}"
        )) {
            FakeHost host = new FakeHost();
            host.cancelAfterTicks = 2;
            Interpreter it = new Interpreter(Parser.parse("function main() { " + loop
                + " print(\"finished\"); } function onStop() { print(\"cleaned up\"); }"),
                CoreLibrary.create(), host);
            it.run();
            it.runStopHandler();
            assertEquals(2, host.ticks, loop);
            assertEquals(List.of("cleaned up"), host.out, loop);
        }
    }

    @Test
    void stopBuiltinEndsQuietly() {
        FakeHost h = run("function main() { print(1); stop(); print(2); }");
        assertEquals(List.of("1"), h.out);
    }

    @Test
    void syntaxErrorsReportLines() {
        ScriptException e = assertThrows(ScriptException.class, () -> Parser.parse("""
            function main() {
                let x = 1
                print(x);
            }
            """));
        assertEquals(3, e.line());
        assertTrue(e.getMessage().contains("';'"), e.getMessage());

        ScriptException top = assertThrows(ScriptException.class, () -> Parser.parse("print(1);"));
        assertTrue(top.getMessage().contains("top level"));

        ScriptException brace = assertThrows(ScriptException.class, () -> Parser.parse("function main() {\n print(1);\n"));
        assertEquals(1, brace.line());
    }

    @Test
    void runtimeErrorsAreHelpful() {
        ScriptException unknown = assertThrows(ScriptException.class, () -> run("function main() {\n  prnt(1);\n}"));
        assertEquals(2, unknown.line());
        assertTrue(unknown.getMessage().contains("did you mean print()"), unknown.getMessage());

        ScriptException constant = assertThrows(ScriptException.class, () -> run("const A = 1; function main() { A = 2; }"));
        assertTrue(constant.getMessage().contains("const"));

        ScriptException undeclared = assertThrows(ScriptException.class, () -> run("function main() { y = 2; }"));
        assertTrue(undeclared.getMessage().contains("let y"));

        ScriptException noMain = assertThrows(ScriptException.class, () -> run("function other() { }"));
        assertTrue(noMain.getMessage().contains("main()"));

        ScriptException args = assertThrows(ScriptException.class, () -> run("function main() { abs(1, 2); }"));
        assertTrue(args.getMessage().contains("abs(x)"));
    }

    @Test
    void commentsAndStrings() {
        FakeHost h = run("""
            // line comment
            /* block
               comment */
            function main() {
                print('single "quoted"', "tab\\tx");
            }
            """);
        assertEquals(List.of("single \"quoted\" tab\tx"), h.out);
    }

    @Test
    void recursionIsBounded() {
        ScriptException e = assertThrows(ScriptException.class, () -> run("function f() { f(); } function main() { f(); }"));
        assertTrue(e.getMessage().contains("nested calls"));
    }
}
