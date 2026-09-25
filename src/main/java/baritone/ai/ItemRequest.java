/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The arguments of the {@code acquire} and {@code plan_item} tools. Parsed leniently, because
 * models send counts as numbers, as strings, inside the item text ("3 iron_ingot"), or not at all.
 * Exactly one of {@code item} and {@code error} is meaningful: a request with an error is refused.
 */
record ItemRequest(String item, int count, String error) {

    /** A full inventory of one stackable item. Anything larger is almost certainly a mistake. */
    static final int MAX_COUNT = 64 * 36;
    static final int MAX_ITEM_CHARS = 80;

    private static final Pattern LEADING_COUNT = Pattern.compile("^(\\d{1,5})\\s*[x×]?\\s+(.+)$");
    private static final Pattern TRAILING_COUNT = Pattern.compile("^(.+?)\\s+[x×]\\s*(\\d{1,5})$");

    static ItemRequest parse(LlmClient.ToolCall call) {
        String item = call.string("item", "");
        item = item == null ? "" : item.replaceAll("\\s+", " ").trim();

        // A count written into the item text: "3 iron_ingot", "3x torch", "torch x3".
        Integer embedded = null;
        Matcher leading = LEADING_COUNT.matcher(item);
        Matcher trailing = TRAILING_COUNT.matcher(item);
        if (leading.matches()) {
            embedded = Integer.parseInt(leading.group(1));
            item = leading.group(2).trim();
        } else if (trailing.matches()) {
            embedded = Integer.parseInt(trailing.group(2));
            item = trailing.group(1).trim();
        }

        if (item.isEmpty()) {
            return error("No item given. Pass the item name, e.g. {\"item\": \"iron_pickaxe\", \"count\": 1}.");
        }
        if (item.length() > MAX_ITEM_CHARS) {
            return error("That item name is too long. Use the item id, e.g. \"diamond_pickaxe\".");
        }

        JsonElement raw = call.arguments.get("count");
        int count;
        if (raw == null || raw.isJsonNull()) {
            count = embedded == null ? 1 : embedded;
        } else {
            Integer parsed = wholeNumber(raw);
            if (parsed == null) {
                return error("count must be a whole number, e.g. 3.");
            }
            count = parsed;
        }
        if (count < 1) {
            return error("count must be at least 1.");
        }
        if (count > MAX_COUNT) {
            return error("count must be at most " + MAX_COUNT + " (a full inventory).");
        }
        return new ItemRequest(item, count, null);
    }

    boolean ok() {
        return this.error == null;
    }

    private static ItemRequest error(String message) {
        return new ItemRequest("", 0, message);
    }

    private static Integer wholeNumber(JsonElement element) {
        if (!element.isJsonPrimitive()) {
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        try {
            double value = primitive.isNumber()
                    ? primitive.getAsDouble()
                    : primitive.isString() ? Double.parseDouble(primitive.getAsString().trim()) : Double.NaN;
            if (Double.isNaN(value) || Double.isInfinite(value) || value != Math.rint(value)
                    || Math.abs(value) > Integer.MAX_VALUE) {
                return null;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
