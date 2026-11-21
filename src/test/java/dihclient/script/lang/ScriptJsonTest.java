package dihclient.script.lang;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScriptJsonTest {
    private List<String> run(String source) {
        List<String> output = new ArrayList<>();
        ScriptHost host = new ScriptHost() {
            public void waitTicks(int ticks) {}
            public boolean isCancelled() { return false; }
            public void print(String text) { output.add(text); }
        };
        new Interpreter(Parser.parse(source), CoreLibrary.create(), host).run();
        return output;
    }

    @Test void jsonObjectsAreMutableAndPreserveDiscordIds() {
        assertEquals(List.of("1234567890123456789 3 null", "object 2", "true"), run("""
            function main() {
                let data = jsonParse('{"id":"1234567890123456789","n":2}');
                data["n"] += 1;
                print(data["id"], data["n"], data["missing"]);
                print(type(data), len(data));
                let again = jsonParse(jsonStringify(data));
                print(again["id"] == data["id"] && again["n"] == 3);
            }
            """));
    }

    @Test void invalidJsonDoesNotReplaceLastSettings() {
        ScriptException error = assertThrows(ScriptException.class,
            () -> run("function main() { jsonParse(\"{broken\"); }"));
        assertTrue(error.getMessage().contains("JSON"), error.getMessage());
        assertEquals(List.of("7"), run("""
            function main() {
                let settings = jsonParse('{"delay":7}');
                settings = jsonParse("{broken", settings);
                print(settings["delay"]);
            }
            """));
    }
}
