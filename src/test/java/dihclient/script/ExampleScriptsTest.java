package dihclient.script;

import dihclient.script.lang.Checker;
import dihclient.script.lang.CoreLibrary;
import dihclient.script.lang.Library;
import dihclient.script.lang.ScriptException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The bundled examples and the New-script template must pass the static checker. */
class ExampleScriptsTest {
    private static final Library LIBRARY = new Library().include(CoreLibrary.create()).include(GameLibrary.create());

    private static void assertClean(String source) {
        List<ScriptException> problems = Checker.check(source, LIBRARY);
        assertEquals(List.of(), problems.stream().map(ScriptException::describe).toList());
    }

    @Test
    void goldMinerChecksClean() {
        assertClean(ExampleScripts.GOLD_MINER);
    }

    @Test
    void helloChecksClean() {
        assertClean(ExampleScripts.HELLO);
    }

    @Test
    void checkerCatchesUnknownFunctionsAndArity() {
        List<ScriptException> problems = Checker.check("""
            function main() {
                minee("gold");
                health(1);
                break;
            }
            """, LIBRARY);
        assertEquals(3, problems.size());
        assertEquals(2, problems.get(0).line());
    }
}
