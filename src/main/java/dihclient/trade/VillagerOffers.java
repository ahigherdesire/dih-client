package dihclient.trade;

import java.util.List;

/**
 * What one villager offered the last time its trade screen was open.
 *
 * @param uuid       the villager's UUID as text
 * @param profession e.g. {@code minecraft:librarian}
 * @param xp         trade experience; 0 means nobody has traded with it, so its trades can still be rerolled
 * @param seenMillis when the offers were read (wall clock)
 */
public record VillagerOffers(String uuid, String profession, int level, int xp, int x, int y, int z,
                             List<TradeOffer> offers, long seenMillis) {

    public VillagerOffers {
        offers = offers == null ? List.of() : List.copyOf(offers);
    }

    public boolean rerollable() {
        return xp == 0;
    }

    public double distanceSq(double px, double py, double pz) {
        double dx = x + 0.5 - px;
        double dy = y - py;
        double dz = z + 0.5 - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** The cheapest in-stock offer the rule matches, or null. */
    public TradeOffer best(TradeRule rule) {
        TradeOffer best = null;
        for (TradeOffer o : offers) {
            if (o.outOfStock() || !rule.matches(o, profession)) continue;
            if (best == null || o.emeraldPrice() < best.emeraldPrice()) best = o;
        }
        return best;
    }

    /** The label shown above the villager: its best enchanted book, else its cheapest offer. */
    public String label() {
        TradeOffer shown = best(new TradeRule(TradeRule.Kind.BOOK, null, null, 0, 0, -1, 0, null));
        if (shown == null) shown = best(TradeRule.any());
        return shown == null ? null : shown.describe();
    }
}
