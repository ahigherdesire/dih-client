package dihclient.schematic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns what a placement still needs into {@code /setblock} and {@code /fill} commands, for creative paste when the
 * player may run them. Runs of one state along X become one {@code /fill}. Air goes first so old blocks are out of the
 * way, then full blocks, then everything that hangs on them (torches, buttons, carpets, ...), each pass bottom to top,
 * so nothing is placed before what holds it.
 */
public final class SchematicPaste {

    /** Air, cleared first. */
    public static final int PASS_AIR = 0;
    /** Full blocks, the frame. */
    public static final int PASS_SOLID = 1;
    /** Everything else, once its support is there. */
    public static final int PASS_ATTACHED = 2;

    /** The most blocks one /fill may cover in vanilla. */
    static final int MAX_FILL = 32768;

    /**
     * One position to set: where, the state as {@code /setblock} reads it, and which pass it goes in.
     */
    public record Cell(int x, int y, int z, String state, int pass) {}

    private SchematicPaste() {}

    public static List<String> commands(List<Cell> cells) {
        List<Cell> sorted = new ArrayList<>(cells);
        sorted.sort(Comparator.comparingInt(Cell::pass).thenComparingInt(Cell::y).thenComparingInt(Cell::z)
            .thenComparingInt(Cell::x));
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            Cell first = sorted.get(i);
            int j = i + 1;
            while (j < sorted.size() && j - i < MAX_FILL) {
                Cell next = sorted.get(j);
                Cell prev = sorted.get(j - 1);
                if (next.pass() != first.pass() || next.y() != first.y() || next.z() != first.z()
                    || next.x() != prev.x() + 1 || !next.state().equals(first.state())) {
                    break;
                }
                j++;
            }
            Cell last = sorted.get(j - 1);
            if (last == first) {
                out.add("setblock " + first.x() + " " + first.y() + " " + first.z() + " " + first.state());
            } else {
                out.add("fill " + first.x() + " " + first.y() + " " + first.z() + " " + last.x() + " " + last.y() + " "
                    + last.z() + " " + first.state());
            }
            i = j;
        }
        return out;
    }
}
