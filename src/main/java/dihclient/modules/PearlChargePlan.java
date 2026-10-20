package dihclient.modules;

/**
 * Pure timing logic for PearlCharge: which tick the ender pearl is thrown on and which tick the wind
 * charge is popped on, measured from when the launch is armed. Kept free of Minecraft types so it can
 * be unit tested on its own.
 */
final class PearlChargePlan {
    enum Action {
        PEARL,
        CHARGE
    }

    private final int pearlTick;
    private final int chargeTick;

    PearlChargePlan(boolean pearlFirst, int delayTicks) {
        int gap = Math.max(0, delayTicks);
        this.pearlTick = pearlFirst ? 0 : gap;
        this.chargeTick = pearlFirst ? gap : 0;
    }

    int pearlTick() {
        return pearlTick;
    }

    int chargeTick() {
        return chargeTick;
    }

    /** Whether the given throw fires on this tick (measured from arming). */
    boolean fires(Action action, int tick) {
        return action == Action.PEARL ? tick == pearlTick : tick == chargeTick;
    }

    /** The last tick either throw fires on; the sequence is finished a little after this. */
    int finalTick() {
        return Math.max(pearlTick, chargeTick);
    }
}
