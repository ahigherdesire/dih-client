package baritone.ai;

import java.util.ArrayDeque;
import java.util.Deque;

/** Requests that arrive while the AI is busy, in order. When full, the oldest gives way to the newest. */
public final class TurnQueue {

    private final int capacity;
    private final Deque<String> waiting = new ArrayDeque<>();

    public TurnQueue(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Queues {@code prompt}; returns the one dropped to make room, or null. */
    public synchronized String offer(String prompt) {
        String dropped = this.waiting.size() >= this.capacity ? this.waiting.pollFirst() : null;
        this.waiting.addLast(prompt);
        return dropped;
    }

    public synchronized String poll() {
        return this.waiting.pollFirst();
    }

    public synchronized int size() {
        return this.waiting.size();
    }

    public synchronized void clear() {
        this.waiting.clear();
    }
}
