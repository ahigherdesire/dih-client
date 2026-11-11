package dihclient.script.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Built-ins that don't touch the game: output, timing, math, strings and lists. */
public final class CoreLibrary {
    private CoreLibrary() {
    }

    public static Library create() {
        Library lib = new Library();

        // ---- control
        lib.define("Control", "print(value, ...)", "Show a message in chat (values are joined with spaces).", 0, -1, a -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) sb.append(' ');
                sb.append(Values.str(a.get(i)));
            }
            a.interpreter().host().print(sb.toString());
            return null;
        });
        lib.define("Control", "wait(ticks)", "Pause for a number of game ticks (20 ticks = 1 second).", 1, 1, a -> {
            a.interpreter().waitTicks(Math.max(0, a.integer(0)));
            return null;
        });
        lib.define("Control", "waitSeconds(seconds)", "Pause for a number of seconds (decimals allowed).", 1, 1, a -> {
            a.interpreter().waitTicks((int) Math.round(Math.max(0, a.number(0)) * 20.0));
            return null;
        });
        lib.define("Control", "stop()", "End the script right now (onStop() still runs).", 0, 0, a -> {
            throw new Interpreter.StopSignal();
        });
        lib.define("Control", "error(message)", "Stop the script with an error message.", 1, 1, a -> {
            throw new ScriptException(Values.str(a.get(0)), a.line());
        });
        lib.define("Control", "time()", "Milliseconds since 1970 — use it to measure how long things take.", 0, 0,
            a -> (double) System.currentTimeMillis());

        // ---- math
        lib.define("Math", "abs(x)", "Absolute value.", 1, 1, a -> Math.abs(a.number(0)));
        lib.define("Math", "floor(x)", "Round down.", 1, 1, a -> Math.floor(a.number(0)));
        lib.define("Math", "ceil(x)", "Round up.", 1, 1, a -> Math.ceil(a.number(0)));
        lib.define("Math", "round(x)", "Round to the nearest whole number.", 1, 1, a -> (double) Math.round(a.number(0)));
        lib.define("Math", "sqrt(x)", "Square root.", 1, 1, a -> Math.sqrt(a.number(0)));
        lib.define("Math", "min(a, b, ...)", "Smallest of the numbers.", 1, -1, a -> {
            double m = a.number(0);
            for (int i = 1; i < a.size(); i++) m = Math.min(m, a.number(i));
            return m;
        });
        lib.define("Math", "max(a, b, ...)", "Largest of the numbers.", 1, -1, a -> {
            double m = a.number(0);
            for (int i = 1; i < a.size(); i++) m = Math.max(m, a.number(i));
            return m;
        });
        lib.define("Math", "random(min, max)", "Random whole number from min to max (both included).", 2, 2, a -> {
            int lo = a.integer(0);
            int hi = a.integer(1);
            if (hi < lo) throw a.error("max must be >= min");
            return (double) ThreadLocalRandom.current().nextInt(lo, hi + 1);
        });

        // ---- conversion / strings
        lib.define("Text", "str(value)", "Turn any value into text.", 1, 1, a -> Values.str(a.get(0)));
        lib.define("Text", "num(text)", "Turn text into a number (null if it isn't one).", 1, 1, a -> {
            Object v = a.get(0);
            if (v instanceof Double d) return d;
            try {
                return Double.parseDouble(Values.str(v).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        });
        lib.define("Text", "type(value)", "Type name: number, string, boolean, list or null.", 1, 1, a -> Values.typeName(a.get(0)));
        lib.define("Text", "lower(text)", "Lower-case copy of the text.", 1, 1, a -> a.string(0).toLowerCase(Locale.ROOT));
        lib.define("Text", "upper(text)", "Upper-case copy of the text.", 1, 1, a -> a.string(0).toUpperCase(Locale.ROOT));
        lib.define("Text", "trim(text)", "Text without leading/trailing spaces.", 1, 1, a -> a.string(0).trim());
        lib.define("Text", "startsWith(text, prefix)", "True if the text begins with prefix.", 2, 2, a -> a.string(0).startsWith(a.string(1)));
        lib.define("Text", "endsWith(text, suffix)", "True if the text ends with suffix.", 2, 2, a -> a.string(0).endsWith(a.string(1)));
        lib.define("Text", "replace(text, find, with)", "Replace every occurrence of find.", 3, 3,
            a -> a.string(0).replace(a.string(1), a.string(2)));
        lib.define("Text", "split(text, separator)", "Split text into a list.", 2, 2, a -> {
            String sep = a.string(1);
            List<Object> out = new ArrayList<>();
            if (sep.isEmpty()) {
                for (char c : a.string(0).toCharArray()) out.add(String.valueOf(c));
                return out;
            }
            String s = a.string(0);
            int from = 0;
            while (true) {
                int at = s.indexOf(sep, from);
                if (at < 0) {
                    out.add(s.substring(from));
                    return out;
                }
                out.add(s.substring(from, at));
                from = at + sep.length();
            }
        });
        lib.define("Text", "join(list, separator)", "Join list items into text.", 1, 2, a -> {
            String sep = a.string(1, ", ");
            StringBuilder sb = new StringBuilder();
            List<Object> l = a.list(0);
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(sep);
                sb.append(Values.str(l.get(i)));
            }
            return sb.toString();
        });

        // ---- lists
        lib.define("Lists", "len(value)", "Length of a list or string.", 1, 1, a -> {
            Object v = a.get(0);
            if (v instanceof List<?> l) return (double) l.size();
            if (v instanceof String s) return (double) s.length();
            throw a.error("needs a list or string, got " + Values.typeName(v));
        });
        lib.define("Lists", "contains(listOrText, value)", "True if the list holds value (or the text contains it).", 2, 2, a -> {
            Object c = a.get(0);
            if (c instanceof String s) return s.contains(Values.str(a.get(1)));
            for (Object o : a.list(0)) if (Values.equal(o, a.get(1))) return true;
            return false;
        });
        lib.define("Lists", "push(list, value)", "Add value to the end of the list.", 2, 2, a -> {
            a.list(0).add(a.get(1));
            return null;
        });
        lib.define("Lists", "pop(list)", "Remove and return the last item (null if empty).", 1, 1, a -> {
            List<Object> l = a.list(0);
            return l.isEmpty() ? null : l.remove(l.size() - 1);
        });
        lib.define("Lists", "removeAt(list, index)", "Remove and return the item at index.", 2, 2, a -> {
            List<Object> l = a.list(0);
            int i = a.integer(1);
            if (i < 0) i += l.size();
            if (i < 0 || i >= l.size()) throw a.error("index out of range");
            return l.remove(i);
        });
        lib.define("Lists", "range(start, end)", "List of whole numbers from start up to (not including) end.", 1, 2, a -> {
            int start = a.size() == 1 ? 0 : a.integer(0);
            int end = a.size() == 1 ? a.integer(0) : a.integer(1);
            if (end - start > 1_000_000) throw a.error("range is too large");
            List<Object> out = new ArrayList<>();
            for (int i = start; i < end; i++) out.add((double) i);
            return out;
        });
        return lib;
    }
}
