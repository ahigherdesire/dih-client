package baritone.ai.tool;

import java.util.List;

/**
 * Runs the mod's own commands for tools. The live runner goes through Baritone's command manager and the DIH
 * dispatcher and reports what the command printed; tests swap in one that records the calls.
 */
public interface CommandRunner {

    /**
     * What a command did.
     *
     * @param handled whether it was a known command
     * @param error   whether it printed an error (in red)
     * @param output  what it printed, condensed to one line, or ""
     */
    record Outcome(boolean handled, boolean error, String output) {
        public static Outcome ok(String output) {
            return new Outcome(true, false, output == null ? "" : output);
        }

        public static Outcome failed(String output) {
            return new Outcome(true, true, output == null ? "" : output);
        }

        public static Outcome unknown() {
            return new Outcome(false, false, "");
        }
    }

    /** Runs a Baritone {@code #} command, given without the prefix. */
    Outcome baritone(String command);

    /** Runs a DIH {@code .} command, given without the prefix. */
    Outcome client(String command);

    /** Every name the {@code #} command called {@code name} goes by (for the deny list), or just {@code name}. */
    default List<String> baritoneNames(String name) {
        return List.of(name);
    }
}
