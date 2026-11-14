package baritone.ai.director;

import java.util.Locale;

public enum StepStatus {
    PENDING, RUNNING, DONE, FAILED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
