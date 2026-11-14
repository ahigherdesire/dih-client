package baritone.ai.director;

/** How a job a step started is going. */
public record JobStatus(Kind kind, String text) {

    public enum Kind { RUNNING, DONE, FAILED }

    public static JobStatus running() {
        return new JobStatus(Kind.RUNNING, "");
    }

    public static JobStatus done(String text) {
        return new JobStatus(Kind.DONE, text == null ? "" : text);
    }

    public static JobStatus failed(String text) {
        return new JobStatus(Kind.FAILED, text == null ? "" : text);
    }
}
