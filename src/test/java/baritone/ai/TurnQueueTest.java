package baritone.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Requests that arrive while the AI is busy wait their turn instead of being dropped. */
final class TurnQueueTest {

    @Test
    void waitsInOrderAndDropsTheOldestWhenFull() {
        TurnQueue queue = new TurnQueue(2);
        assertNull(queue.offer("first"));
        assertNull(queue.offer("second"));
        assertEquals("first", queue.offer("third"), "full: the oldest goes");
        assertEquals(2, queue.size());
        assertEquals("second", queue.poll());
        assertEquals("third", queue.poll());
        assertNull(queue.poll());
        queue.offer("x");
        queue.clear();
        assertEquals(0, queue.size());
    }
}
