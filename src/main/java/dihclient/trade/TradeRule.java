package dihclient.trade;

import java.util.Locale;

/**
 * Which offers to buy. Parsed from short text so it fits a module setting, a macro field and a chat command:
 * <ul>
 *   <li>{@code mending}, {@code unbreaking3}, {@code sharpness 4-5}: an enchanted book with that enchantment, at
 *       least (or between) those levels</li>
 *   <li>{@code book}: any enchanted book</li>
 *   <li>{@code slot 2}: the third trade in the list (0-based)</li>
 *   <li>{@code item:bread} or {@code minecraft:bread}: offers whose result is that item</li>
 *   <li>{@code any} or {@code *}: every offer</li>
 * </ul>
 *
 * @param maxPrice   highest emerald price to pay per purchase, 0 for no limit
 * @param profession only villagers with this profession (e.g. {@code librarian}), or null for any
 */
public record TradeRule(Kind kind, String itemId, String enchantment, int minLevel, int maxLevel, int slot,
                        int maxPrice, String profession) {

    public enum Kind { ANY, ITEM, BOOK, SLOT }

    public static TradeRule any() {
        return new TradeRule(Kind.ANY, null, null, 0, 0, -1, 0, null);
    }

    public TradeRule withMaxPrice(int price) {
        return new TradeRule(kind, itemId, enchantment, minLevel, maxLevel, slot, Math.max(0, price), profession);
    }

    public TradeRule withProfession(String name) {
        String p = name == null || name.isBlank() ? null : namespaced(name.trim().toLowerCase(Locale.ROOT));
        return new TradeRule(kind, itemId, enchantment, minLevel, maxLevel, slot, maxPrice, p);
    }

    /** Parses the rule text; throws {@link IllegalArgumentException} with a readable message when it can't. */
    public static TradeRule parse(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty() || t.equals("any") || t.equals("*")) return any();
        if (t.equals("book") || t.equals("books")) return new TradeRule(Kind.BOOK, null, null, 0, 0, -1, 0, null);
        if (t.startsWith("slot")) {
            String n = t.substring(4).trim();
            try {
                int slot = Integer.parseInt(n);
                if (slot < 0) throw new NumberFormatException();
                return new TradeRule(Kind.SLOT, null, null, 0, 0, slot, 0, null);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("\"" + text.trim() + "\": slot needs a number from 0, e.g. slot 2");
            }
        }
        if (t.startsWith("item:")) return new TradeRule(Kind.ITEM, namespaced(t.substring(5).trim()), null, 0, 0, -1, 0, null);
        if (t.startsWith("minecraft:") && !t.contains(" ")) return new TradeRule(Kind.ITEM, t, null, 0, 0, -1, 0, null);
        return parseBook(t, text.trim());
    }

    /** {@code unbreaking3}, {@code unbreaking 3}, {@code sharpness 4-5}, {@code mending}. */
    private static TradeRule parseBook(String t, String original) {
        String name = t;
        String levels = "";
        int split = t.indexOf(' ');
        if (split > 0) {
            name = t.substring(0, split);
            levels = t.substring(split + 1).trim();
        } else {
            int digit = firstDigit(t);
            if (digit > 0) {
                name = t.substring(0, digit);
                levels = t.substring(digit);
            }
        }
        if (!name.matches("[a-z0-9_:.]+")) throw new IllegalArgumentException("\"" + original + "\" isn't a trade rule");
        int min = 1;
        int max = 0;
        if (!levels.isEmpty()) {
            try {
                if (levels.contains("-")) {
                    String[] parts = levels.split("-", 2);
                    min = Integer.parseInt(parts[0].trim());
                    max = Integer.parseInt(parts[1].trim());
                } else {
                    min = Integer.parseInt(levels);
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("\"" + original + "\": levels look like 3 or 2-4");
            }
            if (min < 1 || max != 0 && max < min) throw new IllegalArgumentException("\"" + original + "\": levels look like 3 or 2-4");
        }
        return new TradeRule(Kind.BOOK, null, namespaced(name), min, max, -1, 0, null);
    }

    private static int firstDigit(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isDigit(s.charAt(i))) return i;
        return -1;
    }

    static String namespaced(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    /** Whether the offer is one this rule wants, ignoring price and stock (see {@link TradeBudget}). */
    public boolean matches(TradeOffer offer, String villagerProfession) {
        if (profession != null && !profession.equals(villagerProfession == null ? null : namespaced(villagerProfession))) return false;
        return switch (kind) {
            case ANY -> true;
            case SLOT -> offer.index() == slot;
            case ITEM -> itemId.equals(offer.resultId());
            case BOOK -> {
                if (!offer.isBook()) yield false;
                if (enchantment == null) yield true;
                Integer level = offer.enchantments().get(enchantment);
                yield level != null && level >= minLevel && (maxLevel == 0 || level <= maxLevel);
            }
        };
    }

    /** "mending 1+", "unbreaking 3", "slot 2", "bread", "any". */
    public String describe() {
        String what = switch (kind) {
            case ANY -> "any offer";
            case SLOT -> "slot " + slot;
            case ITEM -> TradeOffer.shortId(itemId);
            case BOOK -> enchantment == null ? "any book"
                    : TradeOffer.shortId(enchantment) + " " + (maxLevel == 0 ? minLevel + (minLevel > 1 ? "+" : "")
                    : minLevel == maxLevel ? String.valueOf(minLevel) : minLevel + "-" + maxLevel);
        };
        return what + (maxPrice > 0 ? " ≤" + maxPrice + "e" : "");
    }
}
