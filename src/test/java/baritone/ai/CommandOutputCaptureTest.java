package baritone.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How a command's chat output is condensed into run_command's result. */
final class CommandOutputCaptureTest {

    @Test
    void stripsTheBaritonePrefix() {
        assertEquals("Mining diamond_ore", CommandOutputCapture.stripPrefix("[Baritone] Mining diamond_ore"));
        assertEquals("Mining diamond_ore", CommandOutputCapture.stripPrefix("[B] Mining diamond_ore"));
        assertEquals("[ai] hello", CommandOutputCapture.stripPrefix("[ai] hello"));
        assertEquals("", CommandOutputCapture.stripPrefix(null));
    }

    @Test
    void joinsDedupesAndSkipsBlanks() {
        String summary = CommandOutputCapture.summarize(
                List.of("[Baritone] Invalid argument", "[Baritone] Invalid argument", "  ", "[Baritone] no block found by that id"),
                8, 600);
        assertEquals("Invalid argument | no block found by that id", summary);
        assertEquals("", CommandOutputCapture.summarize(List.of(), 8, 600));
    }

    @Test
    void capsLinesAndLength() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            lines.add("[Baritone] line " + i);
        }
        String summary = CommandOutputCapture.summarize(lines, 3, 600);
        assertEquals("line 0 | line 1 | line 2 (+17 more lines)", summary);

        String longOne = CommandOutputCapture.summarize(List.of("x".repeat(1000)), 8, 50);
        assertEquals(51, longOne.length());
        assertTrue(longOne.endsWith("…"));
    }
}
