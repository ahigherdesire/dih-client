package baritone.acquire;

import java.util.function.Consumer;

/**
 * The {@code #acquire} feature as seen from outside the executor (the {@code #ai} tools, other
 * commands). The executor registers its implementation with {@link #set}; callers use {@link #get}
 * and must handle null (the feature failed to load).
 *
 * <p>Methods that touch the game must be called on the game thread.
 */
public interface AcquireControl {

    /**
     * Starts acquiring. {@code itemText} is anything a user might type ("iron pick", "minecraft:torch").
     * Returns a one-line message saying what it is doing, or throws {@link IllegalArgumentException}
     * with a readable reason (unknown item, no plan).
     */
    String start(String itemText, int count);

    /** Plans without doing anything and returns the numbered steps (or why it can't), one per line. */
    String plan(String itemText, int count);

    /** Stops the current acquire, if any. */
    void stop();

    boolean isActive();

    /** One line: "idle", or "acquiring 3 iron_ingot: step 4/9, smelt 3 raw_iron (2/3)". */
    String status();

    /** Called on the game thread whenever an acquire starts, advances a step, finishes, fails or is stopped. */
    void addListener(Consumer<AcquireEvent> listener);

    record AcquireEvent(Kind kind, String item, int count, String message) {
        public enum Kind { STARTED, STEP, DONE, FAILED, STOPPED }
    }

    static AcquireControl get() {
        return Holder.instance;
    }

    static void set(AcquireControl control) {
        Holder.instance = control;
    }

    final class Holder {
        private static volatile AcquireControl instance;

        private Holder() {
        }
    }
}
