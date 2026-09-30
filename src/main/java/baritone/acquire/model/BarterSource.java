package baritone.acquire.model;

/**
 * Throwing one {@code currency} to {@code entity} gives {@code output} back: piglins barter for gold ingots. Each
 * trade gives {@code perTrade} of the output on average, since most trades give something else.
 */
public record BarterSource(String entity, String currency, String output, double perTrade) implements Source {
    @Override
    public double outputPerAction() {
        return perTrade;
    }
}
