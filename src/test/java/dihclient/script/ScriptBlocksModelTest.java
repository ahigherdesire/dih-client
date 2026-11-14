package dihclient.script;

import dihclient.script.lang.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScriptBlocksModelTest {
    @Test void editingImportedTextPreservesItsLiteralWhitespace() {
        var model = ScriptBlocksModel.parse("function main() { print(\"a\\tb\\r\\nc\"); }");
        var field = new ScriptEditorModel(false);
        field.setText(model.main.getFirst().value);
        field.documentEnd(false);
        field.insert("!");
        model.main.getFirst().value = field.text();
        assertEquals("a\tb\r\nc!", ScriptBlocksModel.parse(model.source()).main.getFirst().value);
    }
    @Test void nestedBlocksRunAndRoundTrip() {
        var model = new ScriptBlocksModel();
        var repeat = new ScriptBlocksModel.Block(ScriptBlocksModel.Kind.REPEAT, "3");
        repeat.children.add(new ScriptBlocksModel.Block(ScriptBlocksModel.Kind.MESSAGE, "hello"));
        model.main.add(repeat);
        model.cleanup.add(new ScriptBlocksModel.Block(ScriptBlocksModel.Kind.MESSAGE, "done"));
        var restored = ScriptBlocksModel.parse(model.source());
        var output = new java.util.ArrayList<String>();
        var interpreter = new Interpreter(Parser.parse(restored.source()), CoreLibrary.create(), new ScriptHost() {
            public void print(String text) { output.add(text); }
            public void waitTicks(int ticks) { }
            public boolean isCancelled() { return false; }
        });
        interpreter.run();
        interpreter.runStopHandler();
        assertEquals(List.of("hello", "hello", "hello", "done"), output);
    }

    @Test void waypointAndPlaceBlocksUseTheRightFunctions() {
        var model = new ScriptBlocksModel();
        model.main.add(new ScriptBlocksModel.Block(ScriptBlocksModel.Kind.WAYPOINT, "home"));
        model.main.add(new ScriptBlocksModel.Block(ScriptBlocksModel.Kind.GOTO, "chest"));
        String source = model.source();
        assertTrue(source.contains("gotoWaypoint(\"home\");"), source);
        assertTrue(source.contains("goto(\"chest\");"), source);
        var restored = ScriptBlocksModel.parse(source);
        assertEquals(ScriptBlocksModel.Kind.WAYPOINT, restored.main.get(0).kind);
        assertEquals(ScriptBlocksModel.Kind.GOTO, restored.main.get(1).kind);
        // every block's function exists in the real library, so block scripts always pass the checker
        var library = new Library().include(CoreLibrary.create()).include(GameLibrary.create());
        for (var kind : ScriptBlocksModel.Kind.values()) {
            if (kind == ScriptBlocksModel.Kind.REPEAT || kind == ScriptBlocksModel.Kind.FOREVER) continue;
            assertNotNull(library.get(kind.function), kind + " -> " + kind.function);
        }
    }

    @Test void textCannotEscapeIntoExecutableCode() {
        var model = new ScriptBlocksModel();
        String text = "\"\\\n\t); stop(); //";
        model.main.add(new ScriptBlocksModel.Block(ScriptBlocksModel.Kind.MESSAGE, text));
        assertEquals(text, ScriptBlocksModel.parse(model.source()).main.getFirst().value);
        assertEquals(1, Parser.parse(model.source()).functions().get("main").body().body().size());
    }

    @Test void everyDefaultAndRecipePassesTheRealChecker() {
        var library = new Library().include(CoreLibrary.create()).include(GameLibrary.create());
        for (var kind : ScriptBlocksModel.Kind.values()) {
            var model = new ScriptBlocksModel();
            model.main.add(new ScriptBlocksModel.Block(kind, kind.initial));
            assertEquals(List.of(), Checker.check(model.source(), library), kind.name());
            assertEquals(kind, ScriptBlocksModel.parse(model.source()).main.getFirst().kind);
        }
        for (var recipe : ScriptBlocksModel.recipes()) {
            assertEquals(List.of(), Checker.check(recipe.source(), library));
            assertDoesNotThrow(() -> ScriptBlocksModel.parse(recipe.source()));
        }
    }

    @Test void unsupportedCodeIsRejectedInsteadOfDroppingIt() {
        for (String source : List.of(
            "let x = 1; function main() { print(x); }",
            "function main() {} function custom() {}",
            "function main() { if (health() < 5) {} else { stop(); } }",
            "function main(a) {}",
            "function main() { print(playerName()); }",
            "function main() { if (health(5)) { print(\"x\"); } }")) {
            assertThrows(IllegalArgumentException.class, () -> ScriptBlocksModel.parse(source));
        }
    }

    @Test void invalidNumbersCannotBecomeCodeOrUnboundedRepeats() {
        var model = new ScriptBlocksModel();
        var block = new ScriptBlocksModel.Block(ScriptBlocksModel.Kind.REPEAT, "-1");
        model.main.add(block);
        for (String value : List.of("-1", "NaN", "Infinity", "1.5", "2); stop(); repeat(1")) {
            block.value = value;
            assertThrows(IllegalArgumentException.class, model::source);
        }
        block.kind = ScriptBlocksModel.Kind.WAIT;
        block.value = "0.5";
        assertDoesNotThrow(model::source);
    }
}
