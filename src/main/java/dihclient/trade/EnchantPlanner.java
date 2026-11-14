package dihclient.trade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code .enchant plan mending unbreaking3}: the cheapest way to buy a set of enchanted books from the villagers
 * whose offers are cached. Pure, so it is unit-tested.
 *
 * <p>Each wanted book goes to the cheapest cached offer at or above the wanted level. On a price tie it prefers a
 * villager already on the route, so the route visits as few villagers as possible. Books nobody offers are listed
 * as missing, with the nearest librarian that can still be rerolled (0 trade XP) as a suggestion.
 */
public final class EnchantPlanner {

    public static final String LIBRARIAN = "minecraft:librarian";

    /** One wanted book. */
    public record Want(String enchantment, int level) {
        public String describe() {
            return TradeOffer.shortId(enchantment) + " " + level;
        }
    }

    /** One villager to visit and what to buy there. */
    public record Stop(VillagerOffers villager, List<TradeOffer> buys, int emeralds) {
    }

    public record Plan(List<Stop> stops, int totalEmeralds, List<Want> missing, VillagerOffers reroll) {
        public boolean complete() {
            return missing.isEmpty();
        }
    }

    private EnchantPlanner() {
    }

    /** Parses {@code mending unbreaking3 "sharpness 5"}-style words; a word with no level means level 1. */
    public static List<Want> parseWants(List<String> words) {
        List<Want> wants = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i).trim().toLowerCase(Locale.ROOT);
            if (word.isEmpty()) continue;
            // "unbreaking 3" given as two words.
            if (i + 1 < words.size() && words.get(i + 1).trim().matches("\\d+") && !Character.isDigit(word.charAt(word.length() - 1))) {
                word = word + words.get(++i).trim();
            }
            TradeRule rule = TradeRule.parse(word);
            if (rule.kind() != TradeRule.Kind.BOOK || rule.enchantment() == null)
                throw new IllegalArgumentException("\"" + words.get(i) + "\" isn't an enchantment");
            wants.add(new Want(rule.enchantment(), Math.max(1, rule.minLevel())));
        }
        if (wants.isEmpty()) throw new IllegalArgumentException("name at least one enchantment, e.g. mending unbreaking3");
        return wants;
    }

    public static Plan plan(List<Want> wants, List<VillagerOffers> villagers, double px, double py, double pz) {
        Map<String, Stop> stops = new LinkedHashMap<>();
        List<Want> missing = new ArrayList<>();
        int total = 0;
        for (Want want : wants) {
            VillagerOffers bestVillager = null;
            TradeOffer bestOffer = null;
            for (VillagerOffers v : villagers) {
                for (TradeOffer o : v.offers()) {
                    if (o.outOfStock() || !o.isBook()) continue;
                    Integer level = o.enchantments().get(want.enchantment());
                    if (level == null || level < want.level()) continue;
                    boolean cheaper = bestOffer == null || o.emeraldPrice() < bestOffer.emeraldPrice();
                    boolean tieOnRoute = bestOffer != null && o.emeraldPrice() == bestOffer.emeraldPrice()
                            && stops.containsKey(v.uuid()) && !stops.containsKey(bestVillager.uuid());
                    if (cheaper || tieOnRoute) {
                        bestOffer = o;
                        bestVillager = v;
                    }
                }
            }
            if (bestOffer == null) {
                missing.add(want);
                continue;
            }
            Stop old = stops.get(bestVillager.uuid());
            List<TradeOffer> buys = new ArrayList<>(old == null ? List.of() : old.buys());
            buys.add(bestOffer);
            int emeralds = (old == null ? 0 : old.emeralds()) + bestOffer.emeraldPrice();
            stops.put(bestVillager.uuid(), new Stop(bestVillager, List.copyOf(buys), emeralds));
            total += bestOffer.emeraldPrice();
        }
        VillagerOffers reroll = null;
        if (!missing.isEmpty()) {
            for (VillagerOffers v : villagers) {
                if (!LIBRARIAN.equals(v.profession()) || !v.rerollable()) continue;
                if (reroll == null || v.distanceSq(px, py, pz) < reroll.distanceSq(px, py, pz)) reroll = v;
            }
        }
        return new Plan(List.copyOf(stops.values()), total, List.copyOf(missing), reroll);
    }

    /** Chat lines for the plan. */
    public static List<String> explain(Plan plan) {
        List<String> lines = new ArrayList<>();
        if (plan.stops().isEmpty() && plan.missing().isEmpty()) lines.add("Nothing to buy.");
        int n = 1;
        for (Stop stop : plan.stops()) {
            VillagerOffers v = stop.villager();
            StringBuilder line = new StringBuilder();
            line.append(n++).append(". villager at ").append(v.x()).append(' ').append(v.y()).append(' ').append(v.z())
                    .append(": ");
            for (int i = 0; i < stop.buys().size(); i++) {
                if (i > 0) line.append(", ");
                line.append(stop.buys().get(i).describe());
            }
            lines.add(line.toString());
        }
        if (!plan.stops().isEmpty()) lines.add("Total: " + plan.totalEmeralds() + " emeralds (plus one book each).");
        if (!plan.missing().isEmpty()) {
            StringBuilder line = new StringBuilder("Not offered by any villager seen yet: ");
            for (int i = 0; i < plan.missing().size(); i++) {
                if (i > 0) line.append(", ");
                line.append(plan.missing().get(i).describe());
            }
            lines.add(line.toString());
            VillagerOffers r = plan.reroll();
            lines.add(r == null
                    ? "Open more librarians' trades, or reroll a librarian nobody has traded with (AutoTrade, Reroll mode)."
                    : "Reroll the librarian at " + r.x() + " " + r.y() + " " + r.z() + " (0 trade XP) with AutoTrade in Reroll mode.");
        }
        return lines;
    }
}
