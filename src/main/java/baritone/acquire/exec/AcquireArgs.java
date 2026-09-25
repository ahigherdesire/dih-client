package baritone.acquire.exec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed {@code #acquire} arguments. Pure (no Minecraft classes) so it is unit-tested.
 *
 * <pre>
 *   #acquire                        status
 *   #acquire status | stop
 *   #acquire iron pick              1 iron pickaxe
 *   #acquire torch 64 / 64 torch    either order, "64x" and "x64" work too
 *   #acquire plan iron_pickaxe 2    dry run
 * </pre>
 *
 * @param item  what the user typed for the item, words joined by one space; null for STATUS and STOP
 * @param count 1 or more for ACQUIRE and PLAN, 0 otherwise
 */
public record AcquireArgs(Mode mode, String item, int count) {

    public enum Mode { ACQUIRE, PLAN, STATUS, STOP }

    /** A full inventory of 64-stacks. Anything above that cannot be held anyway. */
    public static final int MAX_COUNT = 36 * 64;

    private static final Pattern COUNT = Pattern.compile("(?i)^(?:x(-?\\d{1,9})|(-?\\d{1,9})x?)$");

    /** Throws {@link IllegalArgumentException} with a readable message on bad input. */
    public static AcquireArgs parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return new AcquireArgs(Mode.STATUS, null, 0);

        List<String> words = new ArrayList<>(Arrays.asList(text.split("\\s+")));
        String head = words.get(0).toLowerCase(Locale.ROOT);
        if (words.size() == 1 && head.equals("status")) return new AcquireArgs(Mode.STATUS, null, 0);
        if (words.size() == 1 && (head.equals("stop") || head.equals("cancel"))) return new AcquireArgs(Mode.STOP, null, 0);

        Mode mode = Mode.ACQUIRE;
        if (head.equals("plan")) {
            mode = Mode.PLAN;
            words.remove(0);
            if (words.isEmpty()) throw new IllegalArgumentException("Say what to plan, e.g. #acquire plan iron_pickaxe");
        }

        int count = 1;
        Integer lead = parseCount(words.get(0));
        Integer tail = words.size() > 1 ? parseCount(words.get(words.size() - 1)) : null;
        if (words.size() > 1 && lead != null) {
            count = lead;
            words.remove(0);
        } else if (tail != null) {
            count = tail;
            words.remove(words.size() - 1);
        } else if (lead != null) {
            throw new IllegalArgumentException("Which item? e.g. #acquire " + words.get(0) + " torch");
        }
        if (count < 1) throw new IllegalArgumentException("The count must be at least 1.");
        if (count > MAX_COUNT) throw new IllegalArgumentException("At most " + MAX_COUNT + " fit in an inventory.");

        return new AcquireArgs(mode, String.join(" ", words), count);
    }

    /** "64", "64x" or "x64" as a number (possibly zero or negative, so the caller can complain); null if not a count. */
    static Integer parseCount(String word) {
        Matcher m = COUNT.matcher(word);
        if (!m.matches()) return null;
        String digits = m.group(1) != null ? m.group(1) : m.group(2);
        return Integer.parseInt(digits);
    }
}
