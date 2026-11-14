package baritone.acquire.exec;

/**
 * One meal, tick by tick: start eating, hold "use" until the item count drops, and let go on every way
 * out (done, failed, cancelled, death, a screen opening). Pure (the game state comes in as plain values)
 * so the release rules are unit-tested; {@link EatBehavior} does what each {@link Action} says.
 */
final class Meal {

    enum Action {
        /**
         * Don't hold "use" this tick: a screen is open, or the item isn't in use (held with nothing in use,
         * vanilla would start a second use or right-click the block in the crosshair).
         */
        WAIT,
        /** Put the food in hand, start using it and hold "use". */
        START,
        /** Keep holding "use". */
        HOLD,
        /** Let go of "use" (and stop using the item). */
        RELEASE
    }

    private enum Phase { READY, EATING, DONE, FAILED }

    /** How long a screen may stay open before the meal is given up. */
    static final int SCREEN_WAIT_TICKS = 200;
    /** Not using the item for this long while eating means it stopped without eating (full, or refused). */
    static final int IDLE_TICKS = 10;
    /** Extra ticks past the item's eating time before giving up. */
    static final int TIMEOUT_MARGIN = 40;

    final String item;
    private final int timeoutTicks;
    private Phase phase = Phase.READY;
    private int startCount;
    private int ticks;
    private int idle;
    private int screenTicks;
    private String failure;

    /** @param eatTicks how long the item takes to eat (32 for most food) */
    Meal(String item, int eatTicks) {
        this.item = item;
        this.timeoutTicks = Math.max(1, eatTicks) + TIMEOUT_MARGIN;
    }

    /**
     * One game tick.
     *
     * @param count how many of the item the inventory holds
     * @param using whether the player is using an item
     */
    Action tick(boolean dead, boolean screenOpen, int count, boolean using) {
        if (finished()) return Action.RELEASE;
        if (dead) return fail("you died");
        if (screenOpen) {
            if (++screenTicks > SCREEN_WAIT_TICKS) return fail("a screen stayed open");
            boolean wasEating = phase == Phase.EATING;
            phase = Phase.READY; // start over once it closes
            return wasEating ? Action.RELEASE : Action.WAIT;
        }
        screenTicks = 0;
        if (phase == Phase.READY) {
            if (count <= 0) return fail("no " + shortId(item) + " left");
            startCount = count;
            ticks = 0;
            idle = 0;
            phase = Phase.EATING;
            return Action.START;
        }
        if (count < startCount) {
            phase = Phase.DONE;
            return Action.RELEASE;
        }
        if (++ticks > timeoutTicks) return fail("timed out");
        if (!using) {
            // Finished on the server with the count update still in flight, or refused.
            return ++idle > IDLE_TICKS ? fail("couldn't eat it") : Action.WAIT;
        }
        idle = 0;
        return Action.HOLD;
    }

    /** Gives up (a failed start, #stop, a disconnect). */
    Action cancel(String why) {
        if (!finished()) fail(why);
        return Action.RELEASE;
    }

    boolean finished() {
        return phase == Phase.DONE || phase == Phase.FAILED;
    }

    boolean ate() {
        return phase == Phase.DONE;
    }

    /** Why it failed, or null. */
    String failure() {
        return failure;
    }

    private Action fail(String why) {
        phase = Phase.FAILED;
        failure = why;
        return Action.RELEASE;
    }

    static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
