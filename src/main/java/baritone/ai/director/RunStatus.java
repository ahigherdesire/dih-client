package baritone.ai.director;

import java.util.Locale;

/** Where a run is. PLANNING, RUNNING and WAITING move on by themselves; PAUSED waits for the player. */
public enum RunStatus {
    IDLE, PLANNING, RUNNING, WAITING, PAUSED, DONE, FAILED, STOPPED;

    public boolean isOver() {
        return this == DONE || this == FAILED || this == STOPPED;
    }

    /** Started and not over: paused runs count, they can resume. */
    public boolean isActive() {
        return this != IDLE && !isOver();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
