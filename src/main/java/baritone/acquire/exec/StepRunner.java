package baritone.acquire.exec;

import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;

/**
 * Runs one plan step, one game tick at a time. The acquire process owns the runner and calls
 * {@link #tick} while the step is current; the runner answers with what Baritone should do this tick.
 */
interface StepRunner {

    /**
     * @param calcFailed     Baritone could not find a path for the goal this runner asked for last tick
     * @param safeToCancel   pathing may be paused this tick (not mid-jump or mid-bridge)
     */
    Result tick(boolean calcFailed, boolean safeToCancel);

    /** Stops whatever this runner started: the mine process, an open container, a background craft. Idempotent. */
    void cancel();

    /** What a step tick produced. */
    record Result(Kind kind, PathingCommand command, String reason) {

        enum Kind {
            /** Still working; {@code command} is what Baritone should do this tick. */
            RUNNING,
            /** Finished; the process checks {@code untilCount}. */
            DONE,
            /** Did not work out; the process re-plans. */
            FAILED,
            /** Cannot continue at all (full inventory); the process stops the acquire. */
            FATAL
        }

        static Result running(PathingCommand command) {
            return new Result(Kind.RUNNING, command, null);
        }

        /** Stand still (only once it is safe to stop pathing). */
        static Result pause() {
            return running(new PathingCommand(null, PathingCommandType.REQUEST_PAUSE));
        }

        /** Let the next process (the mine process) drive. */
        static Result defer() {
            return running(new PathingCommand(null, PathingCommandType.DEFER));
        }

        static Result done() {
            return new Result(Kind.DONE, null, null);
        }

        static Result failed(String reason) {
            return new Result(Kind.FAILED, null, reason);
        }

        static Result fatal(String reason) {
            return new Result(Kind.FATAL, null, reason);
        }
    }
}
