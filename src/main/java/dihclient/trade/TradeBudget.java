package dihclient.trade;

/**
 * Spending limits for a trading session.
 *
 * @param budget      emeralds this session may spend in total, 0 for no limit
 * @param reserve     never let the emeralds held drop below this
 * @param perVisitCap purchases per villager visit, 0 for no limit
 */
public record TradeBudget(int budget, int reserve, int perVisitCap) {

    public static final TradeBudget UNLIMITED = new TradeBudget(0, 0, 0);

    public enum Verdict {
        BUY,
        OUT_OF_STOCK,
        TOO_EXPENSIVE,
        BUDGET_SPENT,
        BELOW_RESERVE,
        VISIT_CAP,
        NOT_ENOUGH_EMERALDS;

        /** A few words for the status line. */
        public String describe() {
            return switch (this) {
                case BUY -> "buying";
                case OUT_OF_STOCK -> "out of stock";
                case TOO_EXPENSIVE -> "over the max price";
                case BUDGET_SPENT -> "emerald budget spent";
                case BELOW_RESERVE -> "would go below the emerald reserve";
                case VISIT_CAP -> "per-visit cap reached";
                case NOT_ENOUGH_EMERALDS -> "not enough emeralds";
            };
        }
    }

    public TradeBudget {
        budget = Math.max(0, budget);
        reserve = Math.max(0, reserve);
        perVisitCap = Math.max(0, perVisitCap);
    }

    /**
     * Whether to buy {@code offer} once more.
     *
     * @param held            emeralds in the inventory now
     * @param spent           emeralds spent this session so far
     * @param boughtThisVisit purchases from this villager on this visit so far
     */
    public Verdict check(TradeOffer offer, TradeRule rule, int held, int spent, int boughtThisVisit) {
        if (offer.outOfStock()) return Verdict.OUT_OF_STOCK;
        int price = offer.emeraldPrice();
        if (rule != null && rule.maxPrice() > 0 && price > rule.maxPrice()) return Verdict.TOO_EXPENSIVE;
        if (perVisitCap > 0 && boughtThisVisit >= perVisitCap) return Verdict.VISIT_CAP;
        if (budget > 0 && spent + price > budget) return Verdict.BUDGET_SPENT;
        if (held < price) return Verdict.NOT_ENOUGH_EMERALDS;
        if (held - price < reserve) return Verdict.BELOW_RESERVE;
        return Verdict.BUY;
    }
}
