package dihclient.trade;

import java.util.Map;

/**
 * One villager offer as plain data (no Minecraft types), as the server last sent it.
 *
 * @param index        position in the villager's trade list (what the select-trade packet sends)
 * @param enchantments stored enchantments of an enchanted book result, id → level; empty otherwise
 * @param costACount   the price as the server charges it now (demand and discounts included)
 * @param costBId      the second cost item, or null
 */
public record TradeOffer(int index, String resultId, int resultCount, Map<String, Integer> enchantments,
                         String costAId, int costACount, String costBId, int costBCount, int uses, int maxUses) {

    public static final String EMERALD = "minecraft:emerald";
    public static final String ENCHANTED_BOOK = "minecraft:enchanted_book";

    public TradeOffer {
        enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
    }

    public boolean outOfStock() {
        return maxUses > 0 && uses >= maxUses;
    }

    public boolean isBook() {
        return ENCHANTED_BOOK.equals(resultId);
    }

    /** Emeralds one purchase costs (either cost slot may hold emeralds). */
    public int emeraldPrice() {
        return (EMERALD.equals(costAId) ? costACount : 0) + (EMERALD.equals(costBId) ? costBCount : 0);
    }

    /** "mending 1 – 14e", "bread x6 – 1e". */
    public String describe() {
        StringBuilder out = new StringBuilder();
        if (isBook() && !enchantments.isEmpty()) {
            enchantments.forEach((id, level) -> {
                if (!out.isEmpty()) out.append(", ");
                out.append(shortId(id)).append(' ').append(level);
            });
        } else {
            out.append(shortId(resultId));
            if (resultCount > 1) out.append(" x").append(resultCount);
        }
        return out + " – " + emeraldPrice() + "e";
    }

    static String shortId(String id) {
        return id != null && id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : String.valueOf(id);
    }
}
