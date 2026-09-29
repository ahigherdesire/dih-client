package baritone.acquire;

import baritone.acquire.model.Goal;

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

    /**
     * Starts a goal that may not be an item: being somewhere, or the dragon dead ({@code #beat} phases). Same
     * contract as {@link #start}. The run stops at the first step that has no runner yet.
     */
    default String startGoal(Goal goal) {
        if (goal instanceof Goal.ItemGoal item) return start(item.item(), item.count());
        throw new IllegalArgumentException("#acquire can't do " + goal.label() + " here.");
    }

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
