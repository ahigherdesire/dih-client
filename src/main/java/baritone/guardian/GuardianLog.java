package baritone.guardian;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The last {@link #CAPACITY} things the Guardian did, oldest first, each with a short reason such as
 * "fought zombie, 20→14 HP". Read by UIs and the AI director (to explain what happened while a job ran);
 * safe to read from any thread.
 */
public final class GuardianLog {

    public static final int CAPACITY = 50;

    /** @param millis wall-clock time the event was logged */
    public record Event(long millis, String text) {
    }

    private final Deque<Event> events = new ArrayDeque<>();

    synchronized void add(String text) {
        if (events.size() >= CAPACITY) events.removeFirst();
        events.addLast(new Event(System.currentTimeMillis(), text));
    }

    /** A copy of the events, oldest first. */
    public synchronized List<Event> recent() {
        return new ArrayList<>(events);
    }

    /** The newest event, or null. */
    public synchronized Event last() {
        return events.peekLast();
    }

    /** Forgets every event (a new session, or a test). */
    public synchronized void clear() {
        events.clear();
    }
}
