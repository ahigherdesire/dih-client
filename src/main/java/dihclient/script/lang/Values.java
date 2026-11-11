package dihclient.script.lang;

import java.util.List;
import java.util.Objects;

/**
 * DihScript's value model. Values are plain Java objects: {@link Double} (all numbers),
 * {@link String}, {@link Boolean}, {@code null} and mutable {@link List} of values.
 */
public final class Values {
    private Values() {
    }

    public static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Double d) return d != 0.0 && !d.isNaN();
        if (v instanceof String s) return !s.isEmpty();
        if (v instanceof List<?> l) return !l.isEmpty();
        return true;
    }

    public static String typeName(Object v) {
        if (v == null) return "null";
        if (v instanceof Double) return "number";
        if (v instanceof String) return "string";
        if (v instanceof Boolean) return "boolean";
        if (v instanceof List<?>) return "list";
        return v.getClass().getSimpleName();
    }

    /** How a value prints: whole numbers without ".0", lists as {@code [a, b]}. */
    public static String str(Object v) {
        if (v == null) return "null";
        if (v instanceof Double d) return formatNumber(d);
        if (v instanceof List<?> l) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(", ");
                Object item = l.get(i);
                sb.append(item instanceof String s ? '"' + s + '"' : str(item));
            }
            return sb.append(']').toString();
        }
        return String.valueOf(v);
    }

    public static String formatNumber(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        return Double.toString(d);
    }

    public static boolean equal(Object a, Object b) {
        if (a instanceof Double x && b instanceof Double y) return x.doubleValue() == y.doubleValue();
        return Objects.equals(a, b);
    }

    public static Double num(double d) {
        return d;
    }
}
