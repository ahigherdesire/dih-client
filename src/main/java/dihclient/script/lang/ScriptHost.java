package dihclient.script.lang;

/**
 * What the interpreter needs from whoever runs it. The in-game runner waits on real client ticks;
 * tests use a fake that just counts.
 */
public interface ScriptHost {
    /**
     * Blocks the script for {@code ticks} game ticks (20 per second). Implementations throw
     * {@link Interpreter.StopSignal} if the script is stopped while waiting.
     */
    void waitTicks(int ticks);

    /** True once someone asked this script to stop. */
    boolean isCancelled();

    /** Shows a line of script output to the user. */
    void print(String message);
}
