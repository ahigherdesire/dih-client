package baritone.ai.director;

/** Drives one director on its own thread until the run is over. */
public final class DirectorRunner {

    private final Director director;
    private final Thread thread;

    private DirectorRunner(Director director) {
        this.director = director;
        this.thread = new Thread(this::loop, "DIH-director");
        this.thread.setDaemon(true);
    }

    /** Starts {@code objective} and returns the running run. */
    public static DirectorRunner start(Director director, String objective) {
        DirectorRunner runner = new DirectorRunner(director);
        director.start(objective);
        runner.thread.start();
        return runner;
    }

    public Director director() {
        return this.director;
    }

    public boolean isActive() {
        return this.director.state().status().isActive();
    }

    private void loop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                RunStatus status = this.director.state().status();
                if (status.isOver()) return;
                if (status != RunStatus.PAUSED) this.director.step();
                Thread.sleep(status == RunStatus.WAITING || status == RunStatus.PAUSED ? 250 : 50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            this.director.stop("The run crashed: " + t);
        }
    }
}
