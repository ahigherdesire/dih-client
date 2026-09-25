package baritone.acquire.knowledge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.TreeMap;

/**
 * A discrete distribution over an item stack's count, so loot functions such as
 * {@code limit_count} after a random {@code set_count} give the exact expected value.
 */
final class CountDist {
    private final TreeMap<Integer, Double> p;

    private CountDist(TreeMap<Integer, Double> p) {
        this.p = p;
    }

    static CountDist point(int value) {
        TreeMap<Integer, Double> m = new TreeMap<>();
        m.put(value, 1.0);
        return new CountDist(m);
    }

    /** Uniform over the integers {@code min..max} inclusive, like {@code Mth.nextInt}. */
    static CountDist uniform(int min, int max) {
        if (max <= min) return point(min);
        TreeMap<Integer, Double> m = new TreeMap<>();
        double each = 1.0 / (max - min + 1);
        for (int v = min; v <= max; v++) m.put(v, each);
        return new CountDist(m);
    }

    static CountDist binomial(int n, double chance) {
        if (n <= 0) return point(0);
        double q = Math.max(0, Math.min(1, chance));
        TreeMap<Integer, Double> m = new TreeMap<>();
        for (int k = 0; k <= n; k++) {
            double prob = choose(n, k) * Math.pow(q, k) * Math.pow(1 - q, n - k);
            if (prob > 0) m.put(k, prob);
        }
        return new CountDist(m);
    }

    /** The integer a loot number provider rolls ({@code getInt}): constants and uniform/binomial are exact, others approximate. */
    static CountDist of(JsonElement provider) {
        if (provider == null || provider.isJsonNull()) return point(1);
        if (provider.isJsonPrimitive()) return point(Math.round((float) provider.getAsDouble()));
        if (!provider.isJsonObject()) return point(1);
        JsonObject o = provider.getAsJsonObject();
        String type = o.has("type") ? LootReader.unprefixed(o.get("type").getAsString()) : "uniform";
        return switch (type) {
            case "constant" -> point(Math.round((float) expected(o.get("value"))));
            case "uniform" -> uniform(Math.round((float) expected(o.get("min"))), Math.round((float) expected(o.get("max"))));
            case "binomial" -> binomial(Math.round((float) expected(o.get("n"))), expected(o.get("p")));
            default -> point(Math.round((float) expected(provider)));
        };
    }

    /** Expected float value of a number provider; unknown provider types count as 1. */
    static double expected(JsonElement provider) {
        if (provider == null || provider.isJsonNull()) return 1;
        if (provider.isJsonPrimitive()) return provider.getAsDouble();
        if (!provider.isJsonObject()) return 1;
        JsonObject o = provider.getAsJsonObject();
        String type = o.has("type") ? LootReader.unprefixed(o.get("type").getAsString()) : "uniform";
        return switch (type) {
            case "constant" -> expected(o.get("value"));
            case "uniform" -> (expected(o.get("min")) + expected(o.get("max"))) / 2;
            case "binomial" -> expected(o.get("n")) * expected(o.get("p"));
            default -> 1;
        };
    }

    /** With probability {@code chance} the count becomes {@code other}'s, otherwise it stays. */
    CountDist mix(CountDist other, double chance) {
        if (chance >= 1) return other;
        if (chance <= 0) return this;
        TreeMap<Integer, Double> m = new TreeMap<>();
        p.forEach((v, q) -> m.merge(v, q * (1 - chance), Double::sum));
        other.p.forEach((v, q) -> m.merge(v, q * chance, Double::sum));
        return new CountDist(m);
    }

    /** The sum of two independent counts. */
    CountDist plus(CountDist other) {
        TreeMap<Integer, Double> m = new TreeMap<>();
        for (Map.Entry<Integer, Double> a : p.entrySet()) {
            for (Map.Entry<Integer, Double> b : other.p.entrySet()) {
                m.merge(a.getKey() + b.getKey(), a.getValue() * b.getValue(), Double::sum);
            }
        }
        return new CountDist(m);
    }

    CountDist clamp(int min, int max) {
        TreeMap<Integer, Double> m = new TreeMap<>();
        p.forEach((v, q) -> m.merge(Math.max(min, Math.min(max, v)), q, Double::sum));
        return new CountDist(m);
    }

    double mean() {
        double sum = 0;
        for (Map.Entry<Integer, Double> e : p.entrySet()) sum += e.getKey() * e.getValue();
        return sum;
    }

    /** Expected items dropped: a stack with count 0 or less drops nothing. */
    double expectedDrop() {
        double sum = 0;
        for (Map.Entry<Integer, Double> e : p.entrySet()) if (e.getKey() > 0) sum += e.getKey() * e.getValue();
        return sum;
    }

    private static double choose(int n, int k) {
        double r = 1;
        for (int i = 1; i <= k; i++) r = r * (n - k + i) / i;
        return r;
    }
}
