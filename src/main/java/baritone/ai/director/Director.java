package baritone.ai.director;

/**
 * Gets an objective done: a plan of tool calls, run in order by {@link #step}, re-planned only when something breaks.
 * Methods are safe to call from any thread; {@link #step} does the work and may block (tools, model calls).
 */
public interface Director {

    void start(String objective);

    /** The player said something during the run. */
    void onEvent(String text);

    void pause(String why);

    void resume();

    void stop(String why);

    /** Lets the step waiting for the player's OK (a dangerous tool) run. */
    void confirm();

    /** Moves the run on by one step. */
    void step();

    DirectorState state();
}
