package baritone.ai.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ToolConfirmationsTest {

    @BeforeEach
    void clear() {
        ToolConfirmations.clear();
    }

    @Test
    void aConfirmationRunsOnceAndThenIsGone() {
        String id = ToolConfirmations.add("world_delete", "\"New World\"", 1_000);
        ToolConfirmations.Pending pending = ToolConfirmations.take(id, 2_000);
        assertEquals("world_delete", pending.tool());
        assertEquals("\"New World\"", pending.args());
        assertNull(ToolConfirmations.take(id, 2_000), "a second click does nothing");
    }

    @Test
    void confirmationsExpire() {
        String id = ToolConfirmations.add("drop_items", "", 1_000);
        assertNull(ToolConfirmations.take(id, 1_000 + ToolConfirmations.TTL_MILLIS + 1));
    }

    @Test
    void idsAreDistinctAndOnlyTheNewestAreKept() {
        String first = ToolConfirmations.add("a", "", 0);
        assertNotEquals(first, ToolConfirmations.add("a", "", 0));
        for (int i = 0; i < ToolConfirmations.CAPACITY; i++) {
            ToolConfirmations.add("b", "", 0);
        }
        assertNull(ToolConfirmations.take(first, 0), "the oldest fell out");
    }

    @Test
    void clickCommandsRoundTrip() {
        String command = ToolConfirmations.clickCommand("abc123");
        assertEquals("abc123", ToolConfirmations.idFromClick(command));
        assertEquals("abc123", ToolConfirmations.idFromClick("/" + command));
        assertNull(ToolConfirmations.idFromClick("tp @s ~ ~ ~"));
        assertNull(ToolConfirmations.idFromClick(null));
    }
}
