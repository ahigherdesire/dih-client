package baritone.acquire.exec;

import java.util.Comparator;
import java.util.List;

/**
 * What {@link BarterRunner} does next, from what it sees. Pure logic, no Minecraft classes. A gold armour piece goes on
 * first (piglins attack a player wearing none), what the piglins threw back is picked up, then an ingot goes to the
 * nearest piglin that isn't looking one over already, one throw at a time, so no gold lies about unclaimed.
 */
final class BarterTactics {

    enum Move {
        /** The inventory holds the step's count. */
        DONE,
        /** Put the gold armour piece on. */
        WEAR,
        /** No gold armour to put on: bartering would start a fight. */
        NO_ARMOUR,
        /** Walk over what was thrown back. */
        LOOT,
        /** Walk up to {@link Decision#piglin()}. */
        APPROACH,
        /** Throw {@link Decision#piglin()} an ingot. */
        THROW,
        WAIT,
        /** No piglin in view: go looking. */
        EXPLORE,
        /** No gold left and no trade still going. */
        OUT_OF_GOLD
    }

    /**
     * A piglin in view: {@code admiring} while it looks an ingot over (it holds it in its off hand), and the ticks
     * since it was last thrown one.
     */
    record Piglin(int id, double distance, boolean admiring, int sinceThrown) {
    }

    /** The move, and the piglin it is about (-1 for none). */
    record Decision(Move move, int piglin) {
    }

    /** How close to a piglin an ingot is thrown from: it lands a block or two ahead. */
    static final double THROW_RANGE = 3.5;
    /** How far off the wanted drops are walked to. */
    static final double LOOT_RADIUS = 12;
    /** Ticks between two throws. */
    static final int THROW_GAP = 10;
    /**
     * Ticks a piglin gets to walk over and pick up an ingot thrown to it before it counts as free again, and before
     * gold still lying about stops holding the next throw back.
     */
    static final int FETCH_TICKS = 100;

    /**
     * @param lootDistance how far the nearest dropped item the step wants is (infinite for none)
     * @param goldOnGround thrown ingots still lying near the player
     */
    record Situation(int have, int want, int gold, boolean wearingGold, boolean goldPieceHeld, double lootDistance,
                     List<Piglin> piglins, int sinceThrow, int goldOnGround) {

        static Builder builder() {
            return new Builder();
        }

        static final class Builder {
            private int have;
            private int want;
            private int gold;
            private boolean wearingGold;
            private boolean goldPieceHeld;
            private double lootDistance = Double.POSITIVE_INFINITY;
            private List<Piglin> piglins = List.of();
            private int sinceThrow = Integer.MAX_VALUE;
            private int goldOnGround;

            Builder have(int v) { have = v; return this; }
            Builder want(int v) { want = v; return this; }
            Builder gold(int v) { gold = v; return this; }
            Builder wearingGold(boolean v) { wearingGold = v; return this; }
            Builder goldPieceHeld(boolean v) { goldPieceHeld = v; return this; }
            Builder lootDistance(double v) { lootDistance = v; return this; }
            Builder piglins(List<Piglin> v) { piglins = List.copyOf(v); return this; }
            Builder sinceThrow(int v) { sinceThrow = v; return this; }
            Builder goldOnGround(int v) { goldOnGround = v; return this; }

            Situation build() {
                return new Situation(have, want, gold, wearingGold, goldPieceHeld, lootDistance, piglins, sinceThrow, goldOnGround);
            }
        }
    }

    private BarterTactics() {
    }

    static Decision decide(Situation s) {
        if (s.have() >= s.want()) return of(Move.DONE);
        if (!s.wearingGold()) return of(s.goldPieceHeld() ? Move.WEAR : Move.NO_ARMOUR);
        if (s.lootDistance() <= LOOT_RADIUS) return of(Move.LOOT);
        if (s.gold() <= 0) {
            boolean trading = s.piglins().stream().anyMatch(p -> p.admiring() || p.sinceThrown() < FETCH_TICKS);
            return of(trading ? Move.WAIT : Move.OUT_OF_GOLD);
        }
        if (s.piglins().isEmpty()) return of(Move.EXPLORE);
        Piglin free = s.piglins().stream().filter(p -> !p.admiring() && p.sinceThrown() >= FETCH_TICKS)
                .min(Comparator.comparingDouble(Piglin::distance)).orElse(null);
        if (free == null) return of(Move.WAIT);
        if (free.distance() > THROW_RANGE) return new Decision(Move.APPROACH, free.id());
        if (s.sinceThrow() < THROW_GAP || s.goldOnGround() > 0 && s.sinceThrow() <= FETCH_TICKS) return of(Move.WAIT);
        return new Decision(Move.THROW, free.id());
    }

    private static Decision of(Move move) {
        return new Decision(move, -1);
    }
}
